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

    // === Настройки ===
    private static final double RANGE = 4.5;
    private static final double MIN_DELAY = 0.690;
    private static final double MAX_DELAY = 0.750;
    private static final boolean SPRINT_RESET = true;
    private static final float SMOOTH_SPEED = 0.15f;

    // === Смещение для обхода ===
    private static final boolean ENABLE_SHIFT = true;
    private static final float SHIFT_DEGREES = 0.5f;
    private static final long SHIFT_DURATION_MS = 3000;
    private static final long RETURN_DURATION_MS = 2000;

    // === Джиттер ===
    private static final float JITTER_RANGE = 0.15f;

    private Thread workerThread;
    private volatile boolean running = true;

    // Целевые углы
    private float targetYaw = 0;
    private float targetPitch = 0;

    // Фаза смещения
    private long shiftCycleStart = System.currentTimeMillis();
    private boolean isShiftPhase = true;

    // === ЗАФИКСИРОВАННАЯ ЦЕЛЬ ===
    private LivingEntity lockedTarget = null;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod KillAura with LOCK TARGET loaded. Press R to toggle.");

        workerThread = new Thread(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            while (running) {
                try {
                    // === Обработка клавиши R ===
                    if (client != null && client.getWindow() != null) {
                        long window = client.getWindow().getHandle();
                        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                            enabled = !enabled;
                            if (!enabled) lockedTarget = null; // сброс цели при выключении
                            LOGGER.info("KillAura: " + (enabled ? "ON" : "OFF"));
                            Thread.sleep(300);
                        }
                    }

                    if (!enabled || client == null || client.player == null || client.world == null) {
                        Thread.sleep(50);
                        continue;
                    }

                    // === Обновление фазы смещения ===
                    long now = System.currentTimeMillis();
                    long elapsed = now - shiftCycleStart;
                    if (isShiftPhase && elapsed >= SHIFT_DURATION_MS) {
                        isShiftPhase = false;
                        shiftCycleStart = now;
                    } else if (!isShiftPhase && elapsed >= RETURN_DURATION_MS) {
                        isShiftPhase = true;
                        shiftCycleStart = now;
                    }

                    // === Логика выбора цели (с фиксацией) ===
                    // Если есть зафиксированная цель – проверяем, жива ли и в радиусе
                    LivingEntity target = null;
                    if (lockedTarget != null && lockedTarget.isAlive() && !lockedTarget.isDead()) {
                        double dist = client.player.distanceTo(lockedTarget);
                        if (dist <= RANGE) {
                            target = lockedTarget;
                        }
                    }

                    // Если цели нет или она ушла – ищем новую ближайшую и фиксируем её
                    if (target == null) {
                        lockedTarget = getTarget(client);
                        target = lockedTarget;
                    }

                    if (target == null) {
                        Thread.sleep(50);
                        continue;
                    }

                    double dist = client.player.distanceTo(target);
                    if (dist > RANGE) {
                        // цель ушла слишком далеко – сбрасываем
                        lockedTarget = null;
                        Thread.sleep(50);
                        continue;
                    }

                    // === Вычисление углов на цель (с джиттером и смещением) ===
                    Vec3d eyePos = client.player.getEyePos();
                    Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);

                    double dx = targetPos.x - eyePos.x;
                    double dy = targetPos.y - eyePos.y;
                    double dz = targetPos.z - eyePos.z;

                    double distance = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
                    float pitch = (float) -MathHelper.atan2(dy, distance) * (180F / (float) Math.PI);

                    // Джиттер
                    float jitterYaw = (random.nextFloat() - 0.5f) * JITTER_RANGE * 2;
                    float jitterPitch = (random.nextFloat() - 0.5f) * JITTER_RANGE * 2;

                    // Смещение вниз/вверх (периодическое)
                    float shift = 0f;
                    if (ENABLE_SHIFT && isShiftPhase) {
                        shift = SHIFT_DEGREES;
                    }

                    float finalYaw = yaw + jitterYaw;
                    float finalPitch = pitch + jitterPitch + shift;

                    targetYaw = finalYaw;
                    targetPitch = finalPitch;

                    // === Плавная ротация и атака ===
                    client.execute(() -> {
                        if (client.player == null) return;

                        float currentYaw = client.player.getYaw();
                        float currentPitch = client.player.getPitch();

                        float newYaw = lerpAngle(currentYaw, targetYaw, SMOOTH_SPEED);
                        float newPitch = lerpAngle(currentPitch, targetPitch, SMOOTH_SPEED);

                        client.player.setYaw(newYaw);
                        client.player.setPitch(newPitch);

                        // Атака
                        long now2 = System.currentTimeMillis();
                        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
                        long delayMs = (long) (delay * 1000);

                        if (now2 - lastAttackTime >= delayMs && target.isAlive()) {
                            if (SPRINT_RESET && client.player.isSprinting()) {
                                client.player.setSprinting(false);
                            }
                            client.interactionManager.attackEntity(client.player, target);
                            client.player.swingHand(client.player.getActiveHand());
                            lastAttackTime = now2;
                        }
                    });

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("KillAura error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private LivingEntity getTarget(MinecraftClient client) {
        Box box = client.player.getBoundingBox().expand(RANGE);
        List<LivingEntity> entities = client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != client.player && e.isAlive() && !e.isDead());
        entities.sort(Comparator.comparingDouble(e -> client.player.distanceTo(e)));
        return entities.isEmpty() ? null : entities.get(0);
    }

    private float lerpAngle(float from, float to, float speed) {
        float diff = to - from;
        diff = (diff % 360 + 540) % 360 - 180;
        float step = diff * speed;
        if (Math.abs(step) > Math.abs(diff)) step = diff;
        return from + step;
    }
}
