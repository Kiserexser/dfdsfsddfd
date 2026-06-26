package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
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
    private static final Random random = new Random();

    // === НАСТРОЙКИ ===
    private static final double SEARCH_RANGE = 5.0;
    private static final double ATTACK_RANGE = 3.5;
    private static final double MIN_DELAY = 0.690;
    private static final double MAX_DELAY = 0.800;
    private static final float MAX_ROTATION_STEP = 12.0f;
    private static final float JITTER_RANGE = 0.15f;
    private static final double MISS_CHANCE = 0.08;

    // === ПЛАВНОСТЬ С ПЕРЕМЕННЫМ ЗНАЧЕНИЕМ ===
    private static float currentRotationSpeed = 0.18f;
    private static float targetRotationSpeed = 0.18f;
    private static long lastSpeedChangeTime = 0;
    private static final long SPEED_CHANGE_INTERVAL_MIN = 2000; // 2 сек
    private static final long SPEED_CHANGE_INTERVAL_MAX = 5000; // 5 сек
    private static final float SPEED_MIN = 0.12f;
    private static final float SPEED_MAX = 0.28f;

    // === СОСТОЯНИЕ ===
    private static boolean enabled = false;
    private static long lastAttackTime = 0;
    private static PlayerEntity lockedTarget = null;
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;
    private static boolean lastKeyState = false;

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura (плавность меняется) загружена. R - вкл/выкл.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    try {
                        if (mc.getWindow() == null || mc.player == null || mc.world == null) return;
                        long window = mc.getWindow().getHandle();

                        // === ПЕРЕКЛЮЧЕНИЕ ПО R ===
                        boolean currentKey = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
                        if (currentKey && !lastKeyState) {
                            enabled = !enabled;
                            if (!enabled) lockedTarget = null;
                            if (mc.player != null) {
                                mc.player.sendMessage(Text.literal(
                                        enabled ? "§aKillAura ВКЛЮЧЕНА" : "§cKillAura ВЫКЛЮЧЕНА"
                                ), true);
                            }
                            LOGGER.info("KillAura: " + (enabled ? "ON" : "OFF"));
                        }
                        lastKeyState = currentKey;

                        if (!enabled) return;

                        // === ОБНОВЛЕНИЕ ПЛАВНОСТИ (каждые 2-5 сек) ===
                        long now = System.currentTimeMillis();
                        if (now - lastSpeedChangeTime > SPEED_CHANGE_INTERVAL_MIN + random.nextInt((int)(SPEED_CHANGE_INTERVAL_MAX - SPEED_CHANGE_INTERVAL_MIN))) {
                            targetRotationSpeed = SPEED_MIN + (SPEED_MAX - SPEED_MIN) * random.nextFloat();
                            lastSpeedChangeTime = now;
                        }

                        // Плавное приближение к целевой скорости
                        currentRotationSpeed += (targetRotationSpeed - currentRotationSpeed) * 0.02f;
                        currentRotationSpeed = MathHelper.clamp(currentRotationSpeed, SPEED_MIN, SPEED_MAX);

                        // === ПОИСК ЦЕЛИ ===
                        PlayerEntity target = lockedTarget;
                        if (target == null || target.isDead() || !target.isAlive() || mc.player.distanceTo(target) > SEARCH_RANGE) {
                            target = getTargetPlayer();
                            if (target != null) lockedTarget = target;
                            else { lockedTarget = null; return; }
                        }

                        double dist = mc.player.distanceTo(target);
                        if (dist > SEARCH_RANGE) {
                            lockedTarget = null;
                            return;
                        }

                        // === НАВЕДЕНИЕ С ПЕРЕМЕННОЙ ПЛАВНОСТЬЮ ===
                        smoothAimWithZones(target);

                        // === АТАКА ===
                        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
                        delay += (random.nextDouble() - 0.5) * 0.05;
                        delay = Math.max(0.650, Math.min(0.850, delay));

                        if (now - lastAttackTime >= (long)(delay * 1000) && target.isAlive() && !target.isDead()) {
                            double realDist = mc.player.distanceTo(target);
                            if (realDist <= ATTACK_RANGE && realDist > 0.1) {
                                if (random.nextDouble() < MISS_CHANCE) {
                                    mc.player.swingHand(mc.player.getActiveHand());
                                    lastAttackTime = now + 50;
                                    return;
                                }
                                mc.interactionManager.attackEntity(mc.player, target);
                                mc.player.swingHand(mc.player.getActiveHand());
                                lastAttackTime = now;
                            }
                        }

                    } catch (Exception e) {
                        LOGGER.error("Ошибка", e);
                    }
                });
            }
        }).start();
    }

    private static PlayerEntity getTargetPlayer() {
        if (mc.player == null || mc.world == null) return null;
        Box box = mc.player.getBoundingBox().expand(SEARCH_RANGE);
        List<PlayerEntity> players = mc.world.getEntitiesByClass(PlayerEntity.class, box,
                e -> e != mc.player && e.isAlive() && !e.isDead());
        players.removeIf(e -> mc.player.distanceTo(e) > SEARCH_RANGE);
        players.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        return players.isEmpty() ? null : players.get(0);
    }

    private static void smoothAimWithZones(PlayerEntity target) {
        if (mc.player == null || target == null) return;

        double height = target.getHeight();
        double baseY = target.getY() + height * 0.5;

        // Случайная зона
        int zone = random.nextInt(6);
        double offsetX = 0, offsetY = 0, offsetZ = 0;

        switch (zone) {
            case 0: // Голова
                offsetY = height * 0.35;
                offsetX = (random.nextDouble() - 0.5) * 0.1;
                offsetZ = (random.nextDouble() - 0.5) * 0.1;
                break;
            case 1: // Грудь
                offsetY = height * 0.15;
                offsetX = (random.nextDouble() - 0.5) * 0.15;
                offsetZ = (random.nextDouble() - 0.5) * 0.15;
                break;
            case 2: // Левая рука
                offsetY = height * 0.2;
                offsetX = -0.25;
                offsetZ = (random.nextDouble() - 0.5) * 0.1;
                break;
            case 3: // Правая рука
                offsetY = height * 0.2;
                offsetX = 0.25;
                offsetZ = (random.nextDouble() - 0.5) * 0.1;
                break;
            case 4: // Левая нога
                offsetY = -height * 0.25;
                offsetX = -0.15;
                offsetZ = (random.nextDouble() - 0.5) * 0.1;
                break;
            case 5: // Правая нога
                offsetY = -height * 0.25;
                offsetX = 0.15;
                offsetZ = (random.nextDouble() - 0.5) * 0.1;
                break;
        }

        double randomOffsetY = (random.nextDouble() - 0.5) * 0.08;
        double randomOffsetX = (random.nextDouble() - 0.5) * 0.08;
        double randomOffsetZ = (random.nextDouble() - 0.5) * 0.08;

        Vec3d targetPos = new Vec3d(
            target.getX() + offsetX + randomOffsetX,
            baseY + offsetY + randomOffsetY,
            target.getZ() + offsetZ + randomOffsetZ
        );

        Vec3d eyePos = mc.player.getEyePos();
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float idealPitch = (float) -MathHelper.atan2(dy, dist) * (180F / (float) Math.PI);

        float jitterYaw = (random.nextFloat() - 0.5f) * JITTER_RANGE * 2;
        float jitterPitch = (random.nextFloat() - 0.5f) * JITTER_RANGE * 2;

        float targetYaw = idealYaw + jitterYaw;
        float targetPitch = idealPitch + jitterPitch;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiff = targetYaw - currentYaw;
        yawDiff = (yawDiff % 360 + 540) % 360 - 180;
        float pitchDiff = targetPitch - currentPitch;

        yawDiff = MathHelper.clamp(yawDiff, -MAX_ROTATION_STEP, MAX_ROTATION_STEP);
        pitchDiff = MathHelper.clamp(pitchDiff, -MAX_ROTATION_STEP, MAX_ROTATION_STEP);

        // === ИСПОЛЬЗУЕМ ПЕРЕМЕННУЮ ПЛАВНОСТЬ ===
        mc.player.setYaw(currentYaw + yawDiff * currentRotationSpeed);
        mc.player.setPitch(currentPitch + pitchDiff * currentRotationSpeed);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch(), -90, 90));
    }
}
