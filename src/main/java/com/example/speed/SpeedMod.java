package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
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

    // ==================== KillAura ====================
    private static final double RANGE = 4.5;
    private static final double MIN_DELAY = 0.680;
    private static final double MAX_DELAY = 0.700;
    private static final boolean SPRINT_RESET = true;
    private static final float SMOOTH_SPEED = 0.15f;
    private static final boolean ENABLE_SHIFT = true;
    private static final float SHIFT_DEGREES = 0.5f;
    private static final long SHIFT_DURATION_MS = 3000;
    private static final long RETURN_DURATION_MS = 2000;
    private static final float JITTER_RANGE = 0.15f;

    private static boolean killAuraEnabled = false;
    private static final Random random = new Random();
    private long lastAttackTime = 0;

    private float targetYaw = 0, targetPitch = 0;
    private long shiftCycleStart = System.currentTimeMillis();
    private boolean isShiftPhase = true;
    private LivingEntity lockedTarget = null;

    // ==================== SlowFall ====================
    private static boolean slowFallEnabled = false;
    private static final float SLOW_FACTOR = 1.0f / 1.5f; // 0.666... (замедление в 1.5 раза)

    // ==================== Поток ====================
    private Thread workerThread;
    private volatile boolean running = true;

    // Дебаунс клавиш
    private boolean wasRPressed = false;
    private boolean wasGPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura (R) and SlowFall (G) loaded.");

        workerThread = new Thread(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            while (running) {
                try {
                    if (client != null && client.getWindow() != null) {
                        long window = client.getWindow().getHandle();

                        // === Обработка R (KillAura) ===
                        boolean rPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                        if (rPressed && !wasRPressed) {
                            killAuraEnabled = !killAuraEnabled;
                            if (!killAuraEnabled) lockedTarget = null;
                            final String status = killAuraEnabled ? "§aВключён" : "§cВыключен";
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendMessage(Text.of("§6KillAura §7» " + status), true);
                                }
                            });
                            LOGGER.info("KillAura: " + (killAuraEnabled ? "ON" : "OFF"));
                            wasRPressed = true;
                        } else if (!rPressed) {
                            wasRPressed = false;
                        }

                        // === Обработка G (SlowFall) ===
                        boolean gPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
                        if (gPressed && !wasGPressed) {
                            slowFallEnabled = !slowFallEnabled;
                            final String status = slowFallEnabled ? "§aВключён" : "§cВыключен";
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendMessage(Text.of("§6SlowFall §7» " + status), true);
                                }
                            });
                            LOGGER.info("SlowFall: " + (slowFallEnabled ? "ON" : "OFF"));
                            wasGPressed = true;
                        } else if (!gPressed) {
                            wasGPressed = false;
                        }
                    }

                    // === Выполнение модулей ===
                    if (client != null && client.player != null && client.world != null) {
                        if (killAuraEnabled) {
                            updateKillAura(client);
                        }
                        if (slowFallEnabled) {
                            applySlowFall();
                        }
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("Module error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // ==================== KillAura Логика ====================
    private void updateKillAura(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

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
            double dist = client.player.distanceTo(lockedTarget);
            if (dist <= RANGE) target = lockedTarget;
        }

        if (target == null) {
            lockedTarget = getTarget(client);
            target = lockedTarget;
        }

        if (target == null) return;

        double dist = client.player.distanceTo(target);
        if (dist > RANGE) {
            lockedTarget = null;
            return;
        }

        Vec3d eyePos = client.player.getEyePos();
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
            if (now2 - lastAttackTime >= delayMs && finalTarget.isAlive()) {
                if (SPRINT_RESET && client.player.isSprinting()) {
                    client.player.setSprinting(false);
                }
                client.interactionManager.attackEntity(client.player, finalTarget);
                client.player.swingHand(client.player.getActiveHand());
                lastAttackTime = now2;
            }
        });
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

    // ==================== SlowFall Логика ====================
    private void applySlowFall() {
        if (mc.player == null) return;

        double yVelocity = mc.player.getVelocity().y;
        if (yVelocity < 0) {
            mc.player.setVelocity(mc.player.getVelocity().x, yVelocity * SLOW_FACTOR, mc.player.getVelocity().z);
            mc.player.fallDistance = 0;
        }
    }
}
