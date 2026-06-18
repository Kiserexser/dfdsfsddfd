package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");

    private static boolean enabled = false;
    private static final Random random = new Random();
    private long lastAttackTime = 0;

    // === Настройки (можно менять прямо в коде) ===
    private static final double RANGE = 4.5;
    private static final double MIN_DELAY = 0.680;
    private static final double MAX_DELAY = 0.740;
    private static final double JITTER_H = 3.0;    // градусы
    private static final double JITTER_V = 5.0;
    private static final boolean SPRINT_RESET = true;
    private static final float SMOOTH_SPEED = 0.15f; // скорость плавного поворота (0..1)

    private Thread workerThread;
    private volatile boolean running = true;

    // текущие целевые углы (для плавности)
    private float targetYaw = 0;
    private float targetPitch = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod KillAura with smooth rotation and swing loaded. Press R to toggle.");

        workerThread = new Thread(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            while (running) {
                try {
                    // ===== Обработка клавиши R =====
                    if (client != null && client.getWindow() != null) {
                        long window = client.getWindow().getHandle();
                        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                            enabled = !enabled;
                            LOGGER.info("KillAura: " + (enabled ? "ON" : "OFF"));
                            Thread.sleep(300); // дебаунс
                        }
                    }

                    if (!enabled || client == null || client.player == null || client.world == null) {
                        Thread.sleep(50);
                        continue;
                    }

                    // ===== Поиск цели =====
                    LivingEntity target = getTarget(client);
                    if (target == null) {
                        Thread.sleep(50);
                        continue;
                    }

                    double dist = client.player.distanceTo(target);
                    if (dist > RANGE) {
                        Thread.sleep(50);
                        continue;
                    }

                    // ===== Вычисляем углы для наведения (с джиттером) =====
                    Vec3d eyePos = client.player.getEyePos();
                    Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0); // центр тела

                    // Добавляем случайный джиттер (только для цели, чтобы прицел "плавал")
                    double jitterX = (random.nextDouble() - 0.5) * JITTER_H * 0.02;
                    double jitterY = (random.nextDouble() - 0.5) * JITTER_V * 0.02;
                    targetPos = targetPos.add(jitterX, jitterY, jitterX); // небольшое смещение

                    double dx = targetPos.x - eyePos.x;
                    double dy = targetPos.y - eyePos.y;
                    double dz = targetPos.z - eyePos.z;

                    double distance = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
                    float pitch = (float) -MathHelper.atan2(dy, distance) * (180F / (float) Math.PI);

                    // Добавляем джиттер уже к готовым углам (вторичный)
                    float finalYaw = yaw + (float) (JITTER_H * (random.nextDouble() - 0.5) * 2);
                    float finalPitch = pitch + (float) (JITTER_V * (random.nextDouble() - 0.5) * 2);

                    // Запоминаем целевые углы для плавного поворота
                    targetYaw = finalYaw;
                    targetPitch = finalPitch;

                    // ===== Плавная ротация и атака в основном потоке =====
                    client.execute(() -> {
                        if (client.player == null) return;

                        // Интерполяция к целевым углам
                        float currentYaw = client.player.getYaw();
                        float currentPitch = client.player.getPitch();

                        float newYaw = lerpAngle(currentYaw, targetYaw, SMOOTH_SPEED);
                        float newPitch = lerpAngle(currentPitch, targetPitch, SMOOTH_SPEED);

                        client.player.setYaw(newYaw);
                        client.player.setPitch(newPitch);

                        // ===== Атака с задержкой =====
                        long now = System.currentTimeMillis();
                        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
                        long delayMs = (long) (delay * 1000);

                        if (now - lastAttackTime >= delayMs && target.isAlive()) {
                            // Сброс спринта
                            if (SPRINT_RESET && client.player.isSprinting()) {
                                client.player.setSprinting(false);
                            }

                            // Атака с отводкой (swing) – теперь удары видны всем!
                            client.interactionManager.attackEntity(client.player, target);
                            client.player.swingHand(client.player.getActiveHand()); // <-- анимация руки

                            lastAttackTime = now;
                        }
                    });

                    Thread.sleep(10); // проверка каждые 10 мс
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("KillAura thread error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // === Вспомогательные методы ===
    private LivingEntity getTarget(MinecraftClient client) {
        Box box = client.player.getBoundingBox().expand(RANGE);
        List<LivingEntity> entities = client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != client.player && e.isAlive() && !e.isDead());
        entities.sort(Comparator.comparingDouble(e -> client.player.distanceTo(e)));
        return entities.isEmpty() ? null : entities.get(0);
    }

    // Плавная интерполяция углов с учётом перехода через -180/180
    private float lerpAngle(float from, float to, float speed) {
        float diff = to - from;
        diff = (diff % 360 + 540) % 360 - 180;
        float step = diff * speed;
        if (Math.abs(step) > Math.abs(diff)) step = diff;
        return from + step;
    }
}
