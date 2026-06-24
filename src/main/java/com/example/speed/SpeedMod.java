package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // ==================== НАСТРОЙКИ ====================
    private static final double REACH = 5.0;
    private static final int SCAN_RADIUS = 8;
    private static final int SCAN_HEIGHT = 30;
    private static final long ACTION_DELAY = 200;
    private static final float ROTATION_SPEED = 0.7f;

    // ==================== СОСТОЯНИЕ ====================
    private static boolean enabled = false;
    private static BlockPos farmLocation = null;
    private static long lastActionTime = 0;

    private static float currentYaw = 0;
    private static float currentPitch = 0;

    private static Thread workerThread;
    private volatile static boolean running = true;
    private static boolean wasKeyPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("AppleFarm (fixed) loaded. Press Z to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean zPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;

                        if (zPressed && !wasKeyPressed) {
                            enabled = !enabled;
                            if (enabled) {
                                farmLocation = getTargetBlock();
                                if (farmLocation == null) {
                                    mc.execute(() -> {
                                        if (mc.player != null) {
                                            mc.player.sendMessage(Text.of("§6AppleFarm §7» §cПосмотри на блок земли и нажми Z снова!"), true);
                                        }
                                    });
                                    enabled = false;
                                } else {
                                    currentYaw = mc.player.getYaw();
                                    currentPitch = mc.player.getPitch();
                                    mc.execute(() -> {
                                        if (mc.player != null) {
                                            mc.player.sendMessage(Text.of("§6AppleFarm §7» §aФерма запущена!"), true);
                                        }
                                    });
                                    LOGGER.info("AppleFarm started at " + farmLocation);
                                }
                            } else {
                                mc.execute(() -> {
                                    if (mc.player != null) {
                                        mc.player.sendMessage(Text.of("§6AppleFarm §7» §cВыключена"), true);
                                    }
                                });
                                LOGGER.info("AppleFarm disabled");
                            }
                            wasKeyPressed = true;
                        } else if (!zPressed) {
                            wasKeyPressed = false;
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        mc.execute(SpeedMod::updateFarm);
                    }

                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("AppleFarm error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // ==================== ОСНОВНАЯ ЛОГИКА ====================
    private static void updateFarm() {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.squaredDistanceTo(farmLocation.toCenterPos()) > 100) {
            mc.player.sendMessage(Text.of("§cСлишком далеко от фермы!"), true);
            enabled = false;
            return;
        }

        // Проверяем, есть ли дерево
        if (isTreeGrown()) {
            // Есть дерево - рубим
            BlockPos target = findBestBlockToMine();
            if (target != null) {
                mineBlock(target);
            }
            return;
        }

        // Дерева нет - сажаем и выращиваем
        if (!isSaplingPlanted()) {
            plantSapling();
        } else {
            applyBoneMeal();
        }
    }

    // ==================== ПОСАДКА САЖЕНЦА ====================
    private static void plantSapling() {
        if (System.currentTimeMillis() - lastActionTime < ACTION_DELAY) return;

        Item sapling = findSapling();
        if (sapling == Items.AIR) {
            mc.player.sendMessage(Text.of("§cНет саженцев!"), true);
            enabled = false;
            return;
        }

        BlockPos plantPos = farmLocation.up();
        
        // Проверяем, что место пустое
        if (!mc.world.getBlockState(plantPos).isAir()) {
            // Если там что-то есть, может быть дерево или листва - рубим
            if (mc.world.getBlockState(plantPos).isIn(BlockTags.LOGS) || 
                mc.world.getBlockState(plantPos).isIn(BlockTags.LEAVES)) {
                mineBlock(plantPos);
            }
            return;
        }

        int slot = getHotbarSlot(sapling);
        if (slot == -1) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;

        // ТОЧКА ДЛЯ ПРИЦЕЛИВАНИЯ: 5 пикселей НИЖЕ центра блока
        Vec3d aimPoint = new Vec3d(
            plantPos.getX() + 0.5,
            plantPos.getY() + 0.495, // центр (0.5) - 5 пикселей (0.005) = 0.495
            plantPos.getZ() + 0.5
        );
        
        // Поворачиваемся к этой точке
        smoothRotateToVec(aimPoint);

        // Создаем BlockHitResult для клика по земле
        BlockHitResult hitResult = new BlockHitResult(
            aimPoint,
            Direction.UP,
            plantPos,
            false
        );

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = oldSlot;
        lastActionTime = System.currentTimeMillis();

        LOGGER.info("Саженец посажен на " + plantPos);
    }

    // ==================== ПРИМЕНЕНИЕ КОСТНОЙ МУКИ ====================
    private static void applyBoneMeal() {
        if (System.currentTimeMillis() - lastActionTime < ACTION_DELAY) return;

        int slot = getHotbarSlot(Items.BONE_MEAL);
        if (slot == -1) {
            return;
        }

        BlockPos saplingPos = farmLocation.up();
        if (!mc.world.getBlockState(saplingPos).isIn(BlockTags.SAPLINGS)) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;

        // Смотрим на саженец
        smoothRotate(saplingPos);
        if (!isAimedAt(saplingPos)) {
            mc.player.getInventory().selectedSlot = oldSlot;
            return;
        }

        // Правильное применение костной муки
        Vec3d hitVec = new Vec3d(
            saplingPos.getX() + 0.5,
            saplingPos.getY() + 0.5,
            saplingPos.getZ() + 0.5
        );
        
        BlockHitResult hitResult = new BlockHitResult(
            hitVec,
            Direction.UP,
            saplingPos,
            false
        );

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = oldSlot;
        lastActionTime = System.currentTimeMillis();
    }

    // ==================== РУБКА ДЕРЕВА ====================
    private static void mineBlock(BlockPos target) {
        if (System.currentTimeMillis() - lastActionTime < ACTION_DELAY) return;
        if (mc.player == null || mc.world == null) return;

        // Проверяем, что блок существует
        var state = mc.world.getBlockState(target);
        if (state.isAir()) return;

        // Выбираем правильный инструмент
        boolean isLog = state.isIn(BlockTags.LOGS);
        boolean isLeaf = state.isIn(BlockTags.LEAVES);
        
        if (isLog) {
            switchToAxe(target);
        } else if (isLeaf) {
            switchToHoeOrEmpty(target);
        } else {
            return;
        }

        // Смотрим на блок
        smoothRotate(target);
        if (!isAimedAt(target)) return;

        // Ломаем блок
        mc.interactionManager.attackBlock(target, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastActionTime = System.currentTimeMillis();
    }

    // ==================== ВЫБОР ИНСТРУМЕНТОВ ====================
    private static void switchToAxe(BlockPos pos) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        var state = mc.world.getBlockState(pos);

        // Ищем топор
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) {
                float speed = stack.getMiningSpeedMultiplier(state);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        if (bestSlot != -1 && bestSlot != mc.player.getInventory().selectedSlot) {
            mc.player.getInventory().selectedSlot = bestSlot;
        }
    }

    private static void switchToHoeOrEmpty(BlockPos pos) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        var state = mc.world.getBlockState(pos);

        // Сначала ищем мотыгу
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof HoeItem) {
                float speed = stack.getMiningSpeedMultiplier(state);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        // Если мотыги нет, берем пустой слот или руку
        if (bestSlot == -1) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isEmpty()) {
                    bestSlot = i;
                    break;
                }
            }
        }

        if (bestSlot != -1 && bestSlot != mc.player.getInventory().selectedSlot) {
            mc.player.getInventory().selectedSlot = bestSlot;
        }
    }

    // ==================== ПОИСК БЛОКОВ ДЛЯ РУБКИ ====================
    private static BlockPos findBestBlockToMine() {
        List<BlockPos> logs = new ArrayList<>();
        List<BlockPos> leaves = new ArrayList<>();
        int r = SCAN_RADIUS;
        int h = SCAN_HEIGHT;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 0; y <= h; y++) {
                    BlockPos pos = farmLocation.add(x, y, z);
                    double dist = mc.player.squaredDistanceTo(pos.toCenterPos());
                    if (dist > REACH * REACH) continue;

                    var state = mc.world.getBlockState(pos);
                    if (state.isIn(BlockTags.LOGS)) {
                        logs.add(pos);
                    } else if (state.isIn(BlockTags.LEAVES)) {
                        leaves.add(pos);
                    }
                }
            }
        }

        // Сначала рубим ствол (сверху вниз)
        if (!logs.isEmpty()) {
            logs.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
            return logs.get(0);
        }

        // Потом листву (ближайшую)
        if (!leaves.isEmpty()) {
            leaves.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p.toCenterPos())));
            return leaves.get(0);
        }

        return null;
    }

    // ==================== ПРОВЕРКИ ====================
    private static boolean isTreeGrown() {
        int r = SCAN_RADIUS;
        int h = SCAN_HEIGHT;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 0; y <= h; y++) {
                    if (mc.world.getBlockState(farmLocation.add(x, y, z)).isIn(BlockTags.LOGS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSaplingPlanted() {
        return mc.world.getBlockState(farmLocation.up()).isIn(BlockTags.SAPLINGS);
    }

    // ==================== ПЛАВНАЯ РОТАЦИЯ ====================
    private static void smoothRotate(BlockPos pos) {
        if (mc.player == null) return;
        
        Vec3d eye = mc.player.getEyePos();
        Vec3d target = pos.toCenterPos();

        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));

        if (currentYaw == 0 && currentPitch == 0) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }

        float yawDiff = wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        currentYaw += yawDiff * ROTATION_SPEED;
        currentPitch += pitchDiff * ROTATION_SPEED;
        currentPitch = Math.max(-89f, Math.min(89f, currentPitch));

        mc.player.setYaw(currentYaw);
        mc.player.setPitch(currentPitch);
        mc.player.headYaw = currentYaw;
        mc.player.bodyYaw = currentYaw;
    }

    private static void smoothRotateToVec(Vec3d target) {
        if (mc.player == null) return;
        
        Vec3d eye = mc.player.getEyePos();

        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));

        if (currentYaw == 0 && currentPitch == 0) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }

        float yawDiff = wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        currentYaw += yawDiff * ROTATION_SPEED;
        currentPitch += pitchDiff * ROTATION_SPEED;
        currentPitch = Math.max(-89f, Math.min(89f, currentPitch));

        mc.player.setYaw(currentYaw);
        mc.player.setPitch(currentPitch);
        mc.player.headYaw = currentYaw;
        mc.player.bodyYaw = currentYaw;
    }

    private static float wrapDegrees(float angle) {
        angle %= 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    private static boolean isAimedAt(BlockPos pos) {
        if (mc.player == null) return false;
        HitResult hit = mc.player.raycast(REACH, mc.getTickDelta(), false);
        if (hit instanceof BlockHitResult bhr) {
            return bhr.getBlockPos().equals(pos);
        }
        return false;
    }

    // ==================== ИНВЕНТАРЬ ====================
    private static Item findSapling() {
        Item[] saplings = {
                Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING,
                Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING,
                Items.CHERRY_SAPLING, Items.MANGROVE_PROPAGULE
        };
        for (int i = 0; i < 36; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            for (Item s : saplings) {
                if (item == s) return item;
            }
        }
        return Items.AIR;
    }

    private static int getHotbarSlot(Item target) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == target) return i;
        }
        return -1;
    }

    // ==================== ВСПОМОГАТЕЛЬНОЕ ====================
    private static BlockPos getTargetBlock() {
        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            var state = mc.world.getBlockState(pos);
            if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT) ||
                    state.isOf(Blocks.FARMLAND) || state.isOf(Blocks.PODZOL) ||
                    state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.ROOTED_DIRT)) {
                return pos;
            }
            BlockPos below = pos.down();
            var belowState = mc.world.getBlockState(below);
            if (belowState.isOf(Blocks.GRASS_BLOCK) || belowState.isOf(Blocks.DIRT) ||
                    belowState.isOf(Blocks.FARMLAND) || belowState.isOf(Blocks.PODZOL) ||
                    belowState.isOf(Blocks.COARSE_DIRT) || belowState.isOf(Blocks.ROOTED_DIRT)) {
                return below;
            }
        }
        return null;
    }
}
