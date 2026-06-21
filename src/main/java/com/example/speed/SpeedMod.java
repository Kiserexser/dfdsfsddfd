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

    private static final double RANGE = 4.5;
    private static final boolean SPRINT_RESET = true;
    private static final float SMOOTH_SPEED = 0.15f;
    private static final boolean ONLY_CRITS = true;

    private static final boolean ENABLE_SHIFT = true;
    private static final float SHIFT_DEGREES = 1.5f;
    private static final long SHIFT_DURATION_MS = 3000;
    private static final long RETURN_DURATION_MS = 2000;
    private static final float JITTER_RANGE = 0.35f;

    private static float targetYaw = 0;
    private static float targetPitch = 0;
    private static long shiftCycleStart = System.currentTimeMillis();
    private static boolean isShiftPhase = true;
    private static LivingEntity lockedTarget = null;

    // === Состояния автомата ===
    private static final int STATE_READY = 0;
    private static final int STATE_JUMPING = 1;
    private static final int STATE_ATTACKED = 2;

    private static int state = STATE_READY;
    private static long jumpStartTime = 0;
    private static long lastLandTime = 0;

    private static final long POST_JUMP_DELAY = 290;   // 290 мс (как ты просил)
    private static final long POST_LAND_DELAY = 50;    // 50 мс после приземления

    private static boolean wasRPressed = false;
    private static Thread workerThread;
    private static volatile boolean running = true;

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura (auto-jump with 290ms delay, 50ms post-land) loaded. Press R to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean rPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

                        if (rPressed && !wasRPressed) {
                            enabled = !enabled;
                            if (!enabled) {
                                lockedTarget = null;
                                state = STATE_READY;
                                jumpStartTime = 0;
                                lastLandTime = 0;
                            }
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

        // Отключаем спринт
        if (ONLY_CRITS && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        long now = System.currentTimeMillis();

        // === Смещение ===
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

        // === Наведение (всегда) ===
        if (target != null) {
            double dist = mc.player.distanceTo(target);
            if (dist <= RANGE) {
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
            } else {
                lockedTarget = null;
            }
        }

        if (target == null || mc.player.distanceTo(target) > RANGE) return;

        // === Автомат состояний ===
        switch (state) {
            case STATE_READY:
                if (mc.player.isOnGround()) {
                    if (lastLandTime == 0) lastLandTime = now;
                    if (now - lastLandTime >= POST_LAND_DELAY) {
                        mc.player.jump();
                        jumpStartTime = now;
                        state = STATE_JUMPING;
                        lastLandTime = 0;
                    }
                } else {
                    lastLandTime = 0;
                }
                break;

            case STATE_JUMPING:
                if (now - jumpStartTime >= POST_JUMP_DELAY) {
                    if (!mc.player.isOnGround() && mc.player.getVelocity().y < 0) {
                        mc.interactionManager.attackEntity(mc.player, target);
                        mc.player.swingHand(mc.player.getActiveHand());
                        state = STATE_ATTACKED;
                    }
                }
                break;

            case STATE_ATTACKED:
                if (mc.player.isOnGround()) {
                    state = STATE_READY;
                    lastLandTime = now;
                }
                break;
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
