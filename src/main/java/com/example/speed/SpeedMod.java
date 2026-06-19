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

    private static boolean enabled = false;
    private static final float SLOW_FACTOR = 1.0f / 1.5f; // 0.666... (уменьшение скорости в 1.5 раза)

    private Thread workerThread;
    private volatile boolean running = true;
    private boolean wasGPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("SlowFall loaded. Press G to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean gPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;

                        if (gPressed && !wasGPressed) {
                            enabled = !enabled;
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendMessage(Text.of("§6SlowFall §7» " + (enabled ? "§aВключён" : "§cВыключен")), true);
                                }
                            });
                            LOGGER.info("SlowFall: " + (enabled ? "ON" : "OFF"));
                            wasGPressed = true;
                        } else if (!gPressed) {
                            wasGPressed = false;
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        applySlowFall();
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("SlowFall error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void applySlowFall() {
        if (mc.player == null) return;

        // Проверяем, падает ли игрок (вертикальная скорость направлена вниз)
        double yVelocity = mc.player.getVelocity().y;
        if (yVelocity < 0) {
            // Замедляем падение в 1.5 раза
            mc.player.setVelocity(mc.player.getVelocity().x, yVelocity * SLOW_FACTOR, mc.player.getVelocity().z);
            // Сбрасываем fallDistance, чтобы не получать урон от падения
            mc.player.fallDistance = 0;
        }
    }
}
