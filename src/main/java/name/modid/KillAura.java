package name.modid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class KillAura implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("killaura");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // ==================== НАСТРОЙКИ ====================
    private static final double SEARCH_RANGE = 5.0;
    private static final double ATTACK_RANGE = 3.0;
    private static final float FOLLOW_SPEED = 0.85f;          // ← теперь float
    private static final float MAX_FOLLOW_ANGLE = 130f;       // град/сек
    private static final long COOLDOWN_AFTER_HIT = 535;       // мс
    private static final float IDLE_SHAKE_YAW = 0.5f;
    private static final float IDLE_SHAKE_PITCH = 0.3f;
    private static final int FLICK_HIT_INTERVAL = 86;
    private static final long FLICK_DURATION = 240;           // мс

    // ==================== СОСТОЯНИЕ ====================
    private static boolean enabled = false;
    private static boolean lastKeyState = false;
    private static long lastHitTime = 0;
    private static long lastFlickTime = 0;
    private static int hitCounter = 0;
    private static boolean isFlicking = false;
    private static float targetYaw = 0f, targetPitch = 0f;
    private static PlayerEntity lockedTarget = null;

    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;

    @Override
    public void onInitialize() {
        LOGGER.info("Neuro KillAura loaded. Press R to toggle.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    try {
                        if (mc.getWindow() == null || mc.player == null || mc.world == null) return;
                        long window = mc.getWindow().getHandle();

                        boolean current = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
                        if (current && !lastKeyState) {
                            enabled = !enabled;
                            if (!enabled) lockedTarget = null;
                            LOGGER.info("Neuro KillAura: " + (enabled ? "ON" : "OFF"));
                        }
                        lastKeyState = current;

                        if (!enabled) return;

                        // --- Поиск цели ---
                        PlayerEntity target = lockedTarget;
                        if (target == null || target.isDead() || !target.isAlive() || mc.player.distanceTo(target) > SEARCH_RANGE) {
                            target = getTargetPlayer();
                            if (target != null) lockedTarget = target;
                            else { lockedTarget = null; return; }
                        }

                        double dist = mc.player.distanceTo(target);
                        if (dist > SEARCH_RANGE) { lockedTarget = null; return; }

                        // --- Вычисляем идеальный угол ---
                        Vec3d eyePos = mc.player.getEyePos();
                        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
                        double dx = targetPos.x - eyePos.x;
                        double dy = targetPos.y - eyePos.y;
                        double dz = targetPos.z - eyePos.z;
                        double hDist = Math.sqrt(dx * dx + dz * dz);

                        float idealYaw = (float) (MathHelper.atan2(dz, dx) * 180f / Math.PI) - 90f;
                        float idealPitch = (float) (-MathHelper.atan2(dy, hDist) * 180f / Math.PI);

                        // --- Применяем ротацию ---
                        applyRotation(target, idealYaw, idealPitch);

                        // --- Атака ---
                        attackTarget(target);

                    } catch (Exception e) {
                        LOGGER.error("Neuro KillAura error", e);
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

    // ==================== РОТАЦИЯ ====================
    private static void applyRotation(LivingEntity target, float idealYaw, float idealPitch) {
        long now = System.currentTimeMillis();
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        // 1. FOLLOW: cap = abs(axisΔ/total)*130°, next = lerp(0.85, current, current + clamp(Δ,±cap))
        float total = MathHelper.abs(idealYaw - currentYaw) + MathHelper.abs(idealPitch - currentPitch);
        float cap = 130f * (MathHelper.abs(idealYaw - currentYaw) / (total + 0.001f));
        float yawStep = MathHelper.clamp(idealYaw - currentYaw, -cap, cap);
        float pitchStep = MathHelper.clamp(idealPitch - currentPitch, -cap, cap);

        float followYaw = currentYaw + yawStep * FOLLOW_SPEED;
        float followPitch = currentPitch + pitchStep * FOLLOW_SPEED;

        // 2. IDLE (есть цель, но нельзя бить)
        boolean canAttack = (now - lastHitTime >= COOLDOWN_AFTER_HIT);
        if (!canAttack) {
            // Тряска в простое: yaw += rand(18..28)*sin(ms/60), pitch += rand(6..16)*cos(ms/60)
            float yawShake = (0.5f + random.nextFloat()) * IDLE_SHAKE_YAW * 2f;
            float pitchShake = (0.5f + random.nextFloat()) * IDLE_SHAKE_PITCH * 2f;
            float time = now / 60f;
            followYaw += yawShake * MathHelper.sin(time);
            followPitch += pitchShake * MathHelper.cos(time);
        }

        // 3. Флик (каждый 86-й хит, pitch = -90° + swing на 240мс)
        if (hitCounter % FLICK_HIT_INTERVAL == 0 && now - lastFlickTime > FLICK_DURATION) {
            isFlicking = true;
            lastFlickTime = now;
        }
        if (isFlicking) {
            if (now - lastFlickTime < FLICK_DURATION) {
                followPitch = -90f + (now - lastFlickTime) / (float) FLICK_DURATION * 90f;
            } else {
                isFlicking = false;
            }
        }

        // Ограничиваем лимит доворота после хита
        if (now - lastHitTime < COOLDOWN_AFTER_HIT) {
            // лимит 0° пока не прошло 535мс, потом 45°
            float limit = (now - lastHitTime < COOLDOWN_AFTER_HIT) ? 0f : 45f;
            float yawDiff = followYaw - currentYaw;
            float pitchDiff = followPitch - currentPitch;
            followYaw = currentYaw + MathHelper.clamp(yawDiff, -limit, limit);
            followPitch = currentPitch + MathHelper.clamp(pitchDiff, -limit, limit);
        }

        // Применяем
        mc.player.setYaw(followYaw);
        mc.player.setPitch(MathHelper.clamp(followPitch, -90, 90));
    }

    private static void attackTarget(LivingEntity target) {
        long now = System.currentTimeMillis();
        if (now - lastHitTime < COOLDOWN_AFTER_HIT) return;

        double dist = mc.player.distanceTo(target);
        if (dist > ATTACK_RANGE) return;

        // Атака
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(mc.player.getActiveHand());
        lastHitTime = now;
        hitCounter++;

        // После удара сбрасываем цель (опционально)
    }
}
