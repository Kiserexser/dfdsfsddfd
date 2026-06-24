package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // ==================== НАСТРОЙКИ ====================
    private static float funtimeSnowJumpY = 0.42f; // можно менять через GUI или в коде

    // ==================== СОСТОЯНИЕ ====================
    private static boolean enabled = false;

    // ==================== ПОТОК ====================
    private Thread workerThread;
    private volatile boolean running = true;
    private boolean wasXPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("FuntimeSnow loaded. Press X to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean xPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_X) == GLFW.GLFW_PRESS;

                        if (xPressed && !wasXPressed) {
                            enabled = !enabled;
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendMessage(Text.of("§6FuntimeSnow §7» " + (enabled ? "§aВключён" : "§cВыключен")), true);
                                }
                            });
                            LOGGER.info("FuntimeSnow: " + (enabled ? "ON" : "OFF"));
                            wasXPressed = true;
                        } else if (!xPressed) {
                            wasXPressed = false;
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        mc.execute(SpeedMod::handleFuntimeSnow);
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("FuntimeSnow error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // ==================== ЛОГИКА МОДУЛЯ ====================
    private static void handleFuntimeSnow() {
        if (mc.player == null) return;

        // Проверка: стоит ли игрок на земле
        if (mc.player.isOnGround()) {
            // Устанавливаем вертикальную скорость (прыжок) с высотой из настройки
            mc.player.setVelocity(mc.player.getVelocity().x, funtimeSnowJumpY, mc.player.getVelocity().z);
        }
    }

    // ==================== МЕТОД ДЛЯ ИЗМЕНЕНИЯ НАСТРОЙКИ (опционально) ====================
    public static void setFuntimeSnowJumpY(float value) {
        funtimeSnowJumpY = Math.max(0.1f, Math.min(1.0f, value));
    }
}
