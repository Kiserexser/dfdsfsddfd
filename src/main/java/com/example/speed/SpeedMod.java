package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // === НАСТРОЙКИ ===
    private static int keyBind = GLFW.GLFW_KEY_UNKNOWN; // клавиша (можно менять)
    private static float range = 5.0f;                 // радиус
    private static boolean autoWater = true;           // авто-ведро

    // === СОСТОЯНИЕ ===
    private static boolean enabled = false;
    private static boolean wasPressed = false;
    private static final CopyOnWriteArrayList<BlockPos> phantomWater = new CopyOnWriteArrayList<>();

    // === ПОТОК ===
    private Thread workerThread;
    private volatile boolean running = true;

    @Override
    public void onInitialize() {
        LOGGER.info("WaterFake loaded. Press R to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean rPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

                        if (rPressed && !wasPressed) {
                            enabled = !enabled;
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendMessage(Text.of("§6WaterFake §7» " + (enabled ? "§aВключён" : "§cВыключен")), true);
                                }
                            });
                            LOGGER.info("WaterFake: " + (enabled ? "ON" : "OFF"));
                            if (!enabled) {
                                // При выключении убираем всю воду
                                mc.execute(() -> {
                                    for (BlockPos pos : phantomWater) {
                                        if (mc.world != null) {
                                            mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                                        }
                                    }
                                    phantomWater.clear();
                                });
                            }
                            wasPressed = true;
                        } else if (!rPressed) {
                            wasPressed = false;
                        }

                        // === Обработка нажатия ===
                        if (enabled) {
                            boolean keyPressed = isKeyPressed(keyBind);
                            if (keyPressed && !wasPressed) {
                                mc.execute(SpeedMod::tryPlace);
                                wasPressed = true;
                            } else if (!keyPressed) {
                                wasPressed = false;
                            }
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        mc.execute(SpeedMod::updateWater);
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("WaterFake error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private static void updateWater() {
        if (mc.world == null) return;
        // Поддерживаем воду каждые несколько тиков
        for (BlockPos pos : phantomWater) {
            mc.world.setBlockState(pos, Blocks.WATER.getDefaultState(), 3);
        }
    }

    private static void tryPlace() {
        if (mc.player == null || mc.world == null) return;

        // Проверка прицела
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        if (hit.getType() != HitResult.Type.BLOCK) return;

        // Проверка расстояния
        double distSq = mc.player.squaredDistanceTo(hit.getBlockPos().toCenterPos());
        if (distSq > range * range) return;

        BlockPos placePos = hit.getBlockPos().offset(hit.getSide());
        if (!mc.world.getBlockState(placePos).isAir()) return;
        if (phantomWater.contains(placePos)) return;

        // Проверка ведра в руке
        if (!mc.player.getMainHandStack().isOf(Items.WATER_BUCKET)) return;

        // Ставим воду
        mc.world.setBlockState(placePos, Blocks.WATER.getDefaultState(), 3);
        phantomWater.add(placePos);

        // Авто-ведро (меняем местами основную руку и оффхенд)
        if (autoWater) {
            var mainStack = mc.player.getMainHandStack();
            var offStack = mc.player.getOffHandStack();
            if (!offStack.isOf(Items.WATER_BUCKET)) {
                mc.player.getInventory().main.set(mc.player.getInventory().selectedSlot, offStack);
                mc.player.getInventory().offHand.set(0, mainStack);
            }
        }

        // Сообщение
        if (mc.player != null) {
            mc.player.sendMessage(Text.of("§6WaterFake §7» §aВода поставлена!"), true);
        }
    }

    private static boolean isKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return false;
        if (mc.getWindow() == null) return false;
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
    }

    // ==================== МЕТОДЫ ДЛЯ НАСТРОЙКИ ====================
    public static void setKeyBind(int key) {
        keyBind = key;
    }

    public static void setRange(float value) {
        range = Math.max(1.0f, Math.min(10.0f, value));
    }
}
