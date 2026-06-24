package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
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
    private static final long ACTION_DELAY = 100;

    // ==================== СОСТОЯНИЕ ====================
    private static boolean enabled = false;
    private static BlockPos farmLocation = null;
    private static long lastActionTime = 0;

    private static Thread workerThread;
    private volatile boolean running = true;
    private static boolean wasKeyPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("AppleFarm (full tree chop) loaded. Press Z to toggle.");

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

                    Thread.sleep(10);
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
            return;
        }

        // Если нет дерева — сажаем и удобряем
        if (!isTreeGrown()) {
            if (!isSaplingPlanted()) {
                tryPlantSapling();
            } else {
                tryApplyBoneMeal();
            }
            return;
        }

        // Дерево есть — рубим всё (брёвна топором, листву мотыгой)
        if (System.currentTimeMillis() - lastActionTime < ACTION_DELAY) return;

        BlockPos target = findBestBlock();
        if (target == null) return;

        if (!aimAtBlock(target)) return;

        // Выбираем инструмент
        boolean isLog = mc.world.getBlockState(target).isIn(net.minecraft.block.BlockTags.LOGS);
        if (isLog) {
            switchToBestTool(target, true); // топор
        } else {
            switchToBestTool(target, false); // мотыга
        }

        mc.interactionManager.attackBlock(target, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastActionTime = System.currentTimeMillis();
    }

    // ==================== ПОСАДКА ====================
    private static void tryPlantSapling() {
        if (System.currentTimeMillis() - lastActionTime < ACTION_DELAY) return;

        Item sapling = findSapling();
        if (sapling == Items.AIR) {
            mc.player.sendMessage(Text.of("§cНет саженцев!"), true);
            return;
        }

        BlockPos plantPos = farmLocation.up();
        if (!mc.world.getBlockState(plantPos).isAir()) return;

        int slot = getHotbarSlot(sapling);
        if (slot == -1) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;

        if (!aimAtBlock(plantPos)) {
            mc.player.getInventory().selectedSlot = oldSlot;
            return;
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = oldSlot;
        lastActionTime = System.currentTimeMillis();
    }

    private static void tryApplyBoneMeal() {
        if (System.currentTimeMillis() - lastActionTime < ACTION_DELAY) return;

        int slot = getHotbarSlot(Items.BONE_MEAL);
        if (slot == -1) return;

        BlockPos saplingPos = farmLocation.up();
        if (!mc.world.getBlockState(saplingPos).isIn(net.minecraft.block.BlockTags.SAPLINGS)) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;

        if (!aimAtBlock(saplingPos)) {
            mc.player.getInventory().selectedSlot = oldSlot;
            return;
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = oldSlot;
        lastActionTime = System.currentTimeMillis();
    }

    // ==================== ПОИСК ЦЕЛИ ====================
    private static BlockPos findBestBlock() {
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
                    if (state.isIn(net.minecraft.block.BlockTags.LOGS)) {
                        logs.add(pos);
                    } else if (state.isIn(net.minecraft.block.BlockTags.LEAVES)) {
                        leaves.add(pos);
                    }
                }
            }
        }

        // Сначала рубим брёвна (сверху вниз), потом листву
        if (!logs.isEmpty()) {
            logs.sort((a, b) -> Integer.compare(b.getY(), a.getY())); // сверху вниз
            return logs.get(0);
        }

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
                    if (mc.world.getBlockState(farmLocation.add(x, y, z)).isIn(net.minecraft.block.BlockTags.LOGS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSaplingPlanted() {
        return mc.world.getBlockState(farmLocation.up()).isIn(net.minecraft.block.BlockTags.SAPLINGS);
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

    // ==================== ИНСТРУМЕНТЫ ====================
    private static void switchToBestTool(BlockPos pos, boolean axe) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        var state = mc.world.getBlockState(pos);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            Item item = stack.getItem();

            if (axe) {
                // Для брёвен — только топоры
                if (!(item instanceof net.minecraft.item.AxeItem)) continue;
            } else {
                // Для листвы — только мотыги (или пустая рука, если мотыги нет)
                if (!(item instanceof net.minecraft.item.HoeItem)) continue;
            }

            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        // Если нет мотыги — рубим рукой
        if (bestSlot == -1 && !axe) {
            // ищем пустой слот
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isEmpty()) {
                    bestSlot = i;
                    break;
                }
            }
            return;
        }

        if (bestSlot != -1 && bestSlot != mc.player.getInventory().selectedSlot) {
            mc.player.getInventory().selectedSlot = bestSlot;
        }
    }

    // ==================== ПРИЦЕЛИВАНИЕ ====================
    private static boolean aimAtBlock(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d target = pos.toCenterPos();

        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);

        HitResult hit = mc.player.raycast(REACH, 1.0f, false);
        if (hit instanceof BlockHitResult bhr) {
            return bhr.getBlockPos().equals(pos);
        }
        return false;
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
