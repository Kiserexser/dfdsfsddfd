package name.modid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
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

public class KillAura implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("killaura");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // === НАСТРОЙКИ ===
    private static final double SEARCH_RANGE = 5.0;
    private static final double ATTACK_RANGE = 3.5;
    private static final double MIN_DELAY = 0.640;   // сек
    private static final double MAX_DELAY = 0.690;
    private static final boolean RESET_SPRINT = true;
    private static final boolean REQUIRE_CROSSHAIR = true; // атакуем только если цель под прицелом
    private static final double MISS_CHANCE = 0.05; // 5% промахов

    private static boolean enabled = false;
    private static boolean lastKeyState = false;
    private static long lastAttackTime = 0;
    private static PlayerEntity lockedTarget = null;
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;

    @Override
    public void onInitialize() {
        LOGGER.info("Legit KillAura loaded. Press R to toggle.");

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
                            LOGGER.info("Legit KillAura: " + (enabled ? "ON" : "OFF"));
                        }
                        lastKeyState = current;

                        if (!enabled) return;

                        // === ПОИСК ЦЕЛИ ===
                        PlayerEntity target = lockedTarget;
                        if (target == null || target.isDead() || !target.isAlive() || mc.player.distanceTo(target) > SEARCH_RANGE) {
                            target = getTargetPlayer();
                            if (target != null) lockedTarget = target;
                            else { lockedTarget = null; return; }
                        }

                        double dist = mc.player.distanceTo(target);
                        if (dist > SEARCH_RANGE) { lockedTarget = null; return; }

                        // === ПРОВЕРКА ПРИЦЕЛА (если включено) ===
                        if (REQUIRE_CROSSHAIR) {
                            HitResult hit = mc.player.raycast(ATTACK_RANGE, 1.0f, false);
                            if (!(hit instanceof EntityHitResult) || ((EntityHitResult) hit).getEntity() != target) {
                                return; // не атакуем, если цель не под прицелом
                            }
                        }

                        // === ПЛАВНАЯ РОТАЦИЯ (только перед атакой) ===
                        float targetYaw = getYawTo(target);
                        float targetPitch = getPitchTo(target);

                        // Плавный поворот (без резких скачков)
                        float currentYaw = mc.player.getYaw();
                        float currentPitch = mc.player.getPitch();
                        float yawDiff = targetYaw - currentYaw;
                        yawDiff = (yawDiff % 360 + 540) % 360 - 180;
                        float pitchDiff = targetPitch - currentPitch;
                        pitchDiff = MathHelper.clamp(pitchDiff, -10, 10); // ограничиваем скорость
                        yawDiff = MathHelper.clamp(yawDiff, -10, 10);

                        mc.player.setYaw(currentYaw + yawDiff * 0.3f); // плавность 0.3
                        mc.player.setPitch(currentPitch + pitchDiff * 0.3f);

                        // === АТАКА ===
                        long now = System.currentTimeMillis();
                        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
                        if (now - lastAttackTime >= (long)(delay * 1000) && target.isAlive() && dist <= ATTACK_RANGE) {
                            // Промах
                            if (random.nextDouble() < MISS_CHANCE) {
                                mc.player.swingHand(mc.player.getActiveHand());
                                lastAttackTime = now + 50;
                                return;
                            }

                            // Сброс спринта
                            if (RESET_SPRINT && mc.player.isSprinting()) {
                                mc.player.setSprinting(false);
                            }

                            mc.interactionManager.attackEntity(mc.player, target);
                            mc.player.swingHand(mc.player.getActiveHand());
                            lastAttackTime = now;
                        }

                    } catch (Exception e) {
                        LOGGER.error("Legit KillAura error", e);
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

    private static float getYawTo(PlayerEntity target) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eye.x;
        double dz = targetPos.z - eye.z;
        return (float) (MathHelper.atan2(dz, dx) * 180f / Math.PI) - 90f;
    }

    private static float getPitchTo(PlayerEntity target) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eye.x;
        double dy = targetPos.y - eye.y;
        double dz = targetPos.z - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        return (float) (-MathHelper.atan2(dy, hDist) * 180f / Math.PI);
    }
}
