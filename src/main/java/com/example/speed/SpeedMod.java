package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    private static boolean enabled = false;
    private static int ticks = 0;
    private static long lastTeleportTime = 0;

    private static float speedBase = 0.087f;      // базовая скорость
    private static float verticalPull = 0.02f;     // вертикальное замедление
    private static float verticalBoost = 0.016f;   // вертикальное ускорение
    private static float randomMin = 1.1f;
    private static float randomMax = 1.21f;

    private Thread workerThread;
    private volatile boolean running = true;
    private boolean wasGPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("GrimGlide (Fly без фейерверков) loaded. Press G to toggle.");

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
                                    mc.player.sendMessage(Text.of("§6GrimGlide §7» " + (enabled ? "§aВключён" : "§cВыключен")), true);
                                }
                            });
                            LOGGER.info("GrimGlide: " + (enabled ? "ON" : "OFF"));
                            wasGPressed = true;
                        } else if (!gPressed) {
                            wasGPressed = false;
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        mc.execute(SpeedMod::applyGrimGlide);
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("GrimGlide error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private static void applyGrimGlide() {
        if (mc.player == null) return;

        // Работает только на элитрах
        if (!mc.player.isGliding()) {
            return;
        }

        ticks++;

        Vec3d pos = mc.player.getPos();
        float yaw = mc.player.getYaw();

        // Базовое направление движения
        double dx = -Math.sin(Math.toRadians(yaw)) * speedBase;
        double dz = Math.cos(Math.toRadians(yaw)) * speedBase;

        // Случайный множитель (человеческий фактор)
        float randomMultiplier = randomMin + (randomMax - randomMin) * random.nextFloat();
        double motionX = dx * randomMultiplier;
        double motionZ = dz * randomMultiplier;

        // Первое ускорение (с небольшим падением)
        mc.player.setVelocity(
                motionX,
                mc.player.getVelocity().y - verticalPull,
                motionZ
        );

        // Телепорт вперёд раз в 50 мс
        long now = System.currentTimeMillis();
        if (now - lastTeleportTime > 50) {
            mc.player.setPosition(pos.x + dx, pos.y, pos.z + dz);
            lastTeleportTime = now;
        }

        // Второе ускорение (с небольшим подъёмом)
        mc.player.setVelocity(
                motionX,
                mc.player.getVelocity().y + verticalBoost,
                motionZ
        );
    }

    // ==================== МЕТОДЫ ДЛЯ НАСТРОЙКИ (опционально) ====================
    public static void setSpeedBase(float value) {
        speedBase = Math.max(0.01f, Math.min(1.0f, value));
    }

    public static void setVerticalPull(float value) {
        verticalPull = Math.max(0.0f, Math.min(0.2f, value));
    }

    public static void setVerticalBoost(float value) {
        verticalBoost = Math.max(0.0f, Math.min(0.2f, value));
    }
}
