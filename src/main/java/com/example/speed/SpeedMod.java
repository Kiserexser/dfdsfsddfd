package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
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
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // ==================== Настройки ====================
    private static final double RANGE = 4.5;
    private static final double MIN_DELAY = 0.690;
    private static final double MAX_DELAY = 0.750;
    private static final boolean SPRINT_RESET = true;
    private static final boolean CHECK_VISIBILITY = true; // проверка на видимость (raycast)

    // Параметры поворота (для обхода Grim)
    private static final float BASE_SMOOTH_SPEED = 0.12f;      // базовая скорость поворота
    private static final float SPEED_VARIATION = 0.3f;         // ±30% вариации
    private static final float MAX_YAW_CHANGE = 15.0f;         // макс. изменение yaw за тик (градусы)
    private static final float MAX_PITCH_CHANGE = 10.0f;       // макс. изменение pitch за тик

    // Джиттер и смещение (для натуральности)
    private static final float JITTER_RANGE = 0.35f;
    private static final boolean ENABLE_SHIFT = true;
    private static final float SHIFT_DEGREES = 1.5f;
    private static final long SHIFT_DURATION_MS = 3000;
    private static final long RETURN_DURATION_MS = 2000;

    // ==================== Состояния ====================
    private static boolean enabled = false;
    private static final Random random = new Random();
    private static long lastAttackTime = 0;

    private static long shiftCycleStart = System.currentTimeMillis();
    private static boolean isShiftPhase = true;
    private static LivingEntity lockedTarget = null;

    // ==================== Поток и клавиша ====================
    private static boolean wasRPressed = false;
    private static Thread workerThread;
    private static volatile boolean running = true;

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura (Grim/Spooky bypass with normalized angles) loaded. Press R to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean rPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

                        if (rPressed && !wasRPressed) {
                            enabled = !enabled;
                            if (!enabled) lockedTarget = null;
                            LOGGER.info("KillAura: " + (enabled ? "ON" : "OFF"));
                            wasRPressed = true;
                        } else if (!rPressed) {
                            wasRPressed = false;
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        mc.execute(SpeedMod::update);
                    }

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

    private static void update() {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();

        // === Обновление фазы смещения ===
        long elapsedShift = now - shiftCycleStart;
        if (isShiftPhase && elapsedShift >= SHIFT_DURATION_MS) {
            isShiftPhase = false;
            shiftCycleStart = now;
        } else if (!isShiftPhase && elapsedShift >= RETURN_DURATION_MS) {
            isShiftPhase = true;
            shiftCycleStart = now;
        }

        // === Выбор цели ===
        LivingEntity target = null;
        if (lockedTarget != null && lockedTarget.isAlive() && !lockedTarget.isDead()) {
            double dist = mc.player.distanceTo(lockedTarget);
            if (dist <= RANGE) target = lockedTarget;
        }

        if (target == null) {
            lockedTarget = getTarget();
            target = lockedTarget;
        }

        if (target == null) return;

        double dist = mc.player.distanceTo(target);
        if (dist > RANGE) {
            lockedTarget = null;
            return;
        }

        // === Проверка видимости (если включена) ===
        if (CHECK_VISIBILITY && !mc.player.canSee(target)) {
            // цель не видна – пропускаем атаку, но наведение продолжаем
            // (можно также сбросить цель, чтобы не атаковать сквозь стены)
        }

        // === Вычисление целевых углов ===
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);

        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float pitch = (float) -MathHelper.atan2(dy, distance) * (180F / (float) Math.PI);

        // === Нормализация ===
        yaw = normalizeYaw(yaw);
        pitch = normalizePitch(pitch);

        // === Джиттер и смещение ===
        float jitterYaw = (random.nextFloat() - 0.5f) * JITTER_RANGE * 2;
        float jitterPitch = (random.nextFloat() - 0.5f) * JITTER_RANGE * 2;
        float shift = 0f;
        if (ENABLE_SHIFT && isShiftPhase) shift = SHIFT_DEGREES;

        float targetYaw = normalizeYaw(yaw + jitterYaw);
        float targetPitch = normalizePitch(pitch + jitterPitch + shift);

        // === Плавный поворот (с рандомизацией скорости) ===
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float speedVariation = 1.0f - SPEED_VARIATION + random.nextFloat() * 2 * SPEED_VARIATION;
        float speed = BASE_SMOOTH_SPEED * speedVariation;

        float newYaw = interpolateYaw(currentYaw, targetYaw, speed);
        float newPitch = interpolatePitch(currentPitch, targetPitch, speed);

        // === Ограничение изменения за тик ===
        newYaw = clampYawChange(currentYaw, newYaw, MAX_YAW_CHANGE);
        newPitch = clampPitchChange(currentPitch, newPitch, MAX_PITCH_CHANGE);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        // === Атака ===
        long now2 = System.currentTimeMillis();
        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
        long delayMs = (long) (delay * 1000);

        if (now2 - lastAttackTime >= delayMs && target.isAlive()) {
            if (SPRINT_RESET && mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            // Если цель не видна, можно пропустить атаку (но мы уже проверили)
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(mc.player.getActiveHand());
            lastAttackTime = now2;
        }
    }

    // ==================== Методы нормализации и интерполяции ====================

    private static float normalizeYaw(float yaw) {
        yaw = yaw % 360.0f;
        if (yaw > 180.0f) yaw -= 360.0f;
        if (yaw < -180.0f) yaw += 360.0f;
        return yaw;
    }

    private static float normalizePitch(float pitch) {
        return Math.max(-90.0f, Math.min(90.0f, pitch));
    }

    private static float interpolateYaw(float from, float to, float speed) {
        float diff = normalizeYaw(to - from);
        if (Math.abs(diff) <= speed) {
            return to;
        }
        float step = Math.signum(diff) * speed;
        return normalizeYaw(from + step);
    }

    private static float interpolatePitch(float from, float to, float speed) {
        float diff = to - from;
        if (Math.abs(diff) <= speed) {
            return to;
        }
        float step = Math.signum(diff) * speed;
        return normalizePitch(from + step);
    }

    private static float clampYawChange(float from, float to, float maxChange) {
        float diff = normalizeYaw(to - from);
        if (Math.abs(diff) > maxChange) {
            diff = Math.signum(diff) * maxChange;
        }
        return normalizeYaw(from + diff);
    }

    private static float clampPitchChange(float from, float to, float maxChange) {
        float diff = to - from;
        if (Math.abs(diff) > maxChange) {
            diff = Math.signum(diff) * maxChange;
        }
        return normalizePitch(from + diff);
    }

    // ==================== Выбор цели ====================

    private static LivingEntity getTarget() {
        try {
            Box box = mc.player.getBoundingBox().expand(RANGE);
            List<LivingEntity> entities = mc.world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e != mc.player && e.isAlive() && !e.isDead());
            entities.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
            return entities.isEmpty() ? null : entities.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
