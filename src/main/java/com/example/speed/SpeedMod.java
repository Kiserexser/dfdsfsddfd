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
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static final Random random = new Random();
    private static long lastAttackTime = 0;

    // === Настройки ===
    private static final double RANGE = 4.5;
    private static final double MIN_DELAY = 0.690;
    private static final double MAX_DELAY = 0.750;
    private static final boolean SPRINT_RESET = true;
    private static final float SMOOTH_SPEED = 0.15f;

    // === Смещение (Shift) ===
    private static final boolean ENABLE_SHIFT = true;
    private static final float SHIFT_DEGREES = 1.5f;   // изменено с 0.5 на 1.5
    private static final long SHIFT_DURATION_MS = 3000;
    private static final long RETURN_DURATION_MS = 2000;

    // === Джиттер ===
    private static final float JITTER_RANGE = 0.35f;    // изменено с 0.15 на 0.35

    private static float targetYaw = 0;
    private static float targetPitch = 0;
    private static long shiftCycleStart = System.currentTimeMillis();
    private static boolean isShiftPhase = true;
    private static LivingEntity lockedTarget = null;

    private static boolean wasRPressed = false;
    private static Thread workerThread;
    private static volatile boolean running = true;

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura loaded. Press R to toggle.");

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
                        mc.execute(SpeedMod::updateKillAura);
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

    private static void updateKillAura() {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        long elapsed = now - shiftCycleStart;
        if (isShiftPhase && elapsed >= SHIFT_DURATION_MS) {
            isShiftPhase = false;
            shiftCycleStart = now;
        } else if (!isShiftPhase && elapsed >= RETURN_DURATION_MS) {
            isShiftPhase = true;
            shiftCycleStart = now;
        }

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

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);

        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float pitch = (float) -MathHelper.atan2(dy, distance) * (180F / (float) Math.PI);

        float jitterYaw = (random.nextFloat() - 0.5f) * JITTER_RANGE * 2;
        float jitterPitch = (random.nextFloat() - 0.5f) * JITTER_RANGE * 2;
        float shift = 0f;
        if (ENABLE_SHIFT && isShiftPhase) shift = SHIFT_DEGREES;

        targetYaw = yaw + jitterYaw;
        targetPitch = pitch + jitterPitch + shift;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float newYaw = lerpAngle(currentYaw, targetYaw, SMOOTH_SPEED);
        float newPitch = lerpAngle(currentPitch, targetPitch, SMOOTH_SPEED);
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        long now2 = System.currentTimeMillis();
        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
        long delayMs = (long) (delay * 1000);

        if (now2 - lastAttackTime >= delayMs && target.isAlive()) {
            if (SPRINT_RESET && mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(mc.player.getActiveHand());
            lastAttackTime = now2;
        }
    }

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

    private static float lerpAngle(float from, float to, float speed) {
        float diff = to - from;
        diff = (diff % 360 + 540) % 360 - 180;
        float step = diff * speed;
        if (Math.abs(step) > Math.abs(diff)) step = diff;
        return from + step;
    }
}
