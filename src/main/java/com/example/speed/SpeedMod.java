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

    // === НАСТРОЙКИ ===
    private static final double RANGE = 4.2;
    private static final double MIN_DELAY = 0.690;   // изменено
    private static final double MAX_DELAY = 0.730;   // изменено
    private static final boolean SPRINT_RESET = true;
    private static final float SMOOTH_SPEED = 0.08f;
    private static final boolean ENABLE_SHIFT = true;
    private static final float SHIFT_DEGREES = 0.8f;
    private static final long SHIFT_DURATION_MS = 2500;
    private static final long RETURN_DURATION_MS = 1500;
    private static final float JITTER_RANGE = 0.3f;
    private static final boolean RAYCAST_CHECK = true;
    private static final float MISS_CHANCE = 0.05f;
    private static final int MAX_ATTACKS_PER_SECOND = 8;

    private static boolean enabled = false;
    private static final Random random = new Random();
    private long lastAttackTime = 0;
    private long lastSecondCheck = System.currentTimeMillis();
    private int attacksInSecond = 0;

    private Thread workerThread;
    private volatile boolean running = true;

    private float targetYaw = 0, targetPitch = 0;
    private long shiftCycleStart = System.currentTimeMillis();
    private boolean isShiftPhase = true;
    private LivingEntity lockedTarget = null;

    private double targetOffsetX = 0, targetOffsetZ = 0;
    private double offsetTime = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura (Orim-friendly) loaded. Press R to toggle.");

        workerThread = new Thread(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            while (running) {
                try {
                    if (client != null && client.getWindow() != null) {
                        long window = client.getWindow().getHandle();
                        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                            enabled = !enabled;
                            if (!enabled) lockedTarget = null;
                            LOGGER.info("KillAura: " + (enabled ? "ON" : "OFF"));
                            Thread.sleep(300);
                        }
                    }

                    if (!enabled || client == null || client.player == null || client.world == null) {
                        Thread.sleep(50);
                        continue;
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastSecondCheck >= 1000) {
                        attacksInSecond = 0;
                        lastSecondCheck = now;
                    }

                    long elapsedShift = now - shiftCycleStart;
                    if (isShiftPhase && elapsedShift >= SHIFT_DURATION_MS) {
                        isShiftPhase = false;
                        shiftCycleStart = now;
                    } else if (!isShiftPhase && elapsedShift >= RETURN_DURATION_MS) {
                        isShiftPhase = true;
                        shiftCycleStart = now;
                    }

                    LivingEntity target = null;
                    if (lockedTarget != null && lockedTarget.isAlive() && !lockedTarget.isDead()) {
                        double dist = client.player.distanceTo(lockedTarget);
                        if (dist <= RANGE) target = lockedTarget;
                    }

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
                        lockedTarget = null;
                        Thread.sleep(50);
                        continue;
                    }

                    if (RAYCAST_CHECK) {
                        Vec3d eye = client.player.getEyePos();
                        Vec3d lookVec = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(eye).normalize();
                        HitResult hit = client.world.raycast(eye, eye.add(lookVec.multiply(RANGE)), Box.of(eye, 0.1, 0.1, 0.1), (entity) -> entity == target, 0.1);
                        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
                            // цель не видна – пропускаем атаку
                        }
                    }

                    double targetWidth = target.getWidth();
                    double targetHeight = target.getHeight();
                    offsetTime += 0.02;
                    double noiseX = Math.sin(offsetTime * 1.3) * 0.2 + Math.sin(offsetTime * 0.7 + 1.0) * 0.1;
                    double noiseZ = Math.cos(offsetTime * 0.9) * 0.2 + Math.cos(offsetTime * 1.1 + 0.5) * 0.1;
                    double maxOffset = 0.3;
                    targetOffsetX = MathHelper.clamp(noiseX * targetWidth * 0.2, -maxOffset * targetWidth, maxOffset * targetWidth);
                    targetOffsetZ = MathHelper.clamp(noiseZ * targetWidth * 0.2, -maxOffset * targetWidth, maxOffset * targetWidth);

                    Vec3d eyePos = client.player.getEyePos();
                    Vec3d targetPos = target.getPos().add(targetWidth / 2 + targetOffsetX, targetHeight / 2, targetWidth / 2 + targetOffsetZ);

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

                    final LivingEntity finalTarget = target;
                    final float finalYaw = targetYaw;
                    final float finalPitch = targetPitch;

                    client.execute(() -> {
                        if (client.player == null) return;

                        float currentYaw = client.player.getYaw();
                        float currentPitch = client.player.getPitch();
                        float newYaw = lerpAngle(currentYaw, finalYaw, SMOOTH_SPEED);
                        float newPitch = lerpAngle(currentPitch, finalPitch, SMOOTH_SPEED);
                        client.player.setYaw(newYaw);
                        client.player.setPitch(newPitch);

                        long now2 = System.currentTimeMillis();
                        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
                        long delayMs = (long) (delay * 1000);

                        if (attacksInSecond >= MAX_ATTACKS_PER_SECOND) {
                            return;
                        }
                        if (random.nextFloat() < MISS_CHANCE) {
                            return;
                        }

                        if (now2 - lastAttackTime >= delayMs && finalTarget.isAlive()) {
                            if (SPRINT_RESET && client.player.isSprinting()) {
                                client.player.setSprinting(false);
                            }
                            client.interactionManager.attackEntity(client.player, finalTarget);
                            client.player.swingHand(client.player.getActiveHand());
                            lastAttackTime = now2;
                            attacksInSecond++;
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
        try {
            Box box = client.player.getBoundingBox().expand(RANGE);
            List<LivingEntity> entities = client.world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e != client.player && e.isAlive() && !e.isDead());
            entities.sort(Comparator.comparingDouble(e -> client.player.distanceTo(e)));
            return entities.isEmpty() ? null : entities.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private float lerpAngle(float from, float to, float speed) {
        float diff = to - from;
        diff = (diff % 360 + 540) % 360 - 180;
        float step = diff * speed;
        if (Math.abs(step) > Math.abs(diff)) step = diff;
        return from + step;
    }
}
