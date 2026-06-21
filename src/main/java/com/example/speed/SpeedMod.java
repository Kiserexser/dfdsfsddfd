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

    // === Настройки ===
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

    // === Контроль прыжка и удара ===
    private static long lastJumpTime = 0;           // время последнего прыжка
    private static long lastLandTime = 0;           // время последнего приземления
    private static long jumpStartTime = 0;          // время начала прыжка (для отсчёта 290 мс)
    private static boolean hasJumped = false;       // флаг, что прыжок совершён и ждём удар
    private static boolean hasAttacked = false;     // флаг, что удар уже нанесён в текущем цикле

    private static boolean wasRPressed = false;
    private static Thread workerThread;
    private static volatile boolean running = true;

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura (auto-jump with 290ms delay, 50ms post-land delay) loaded. Press R to toggle.");

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
                                resetState();
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

    private static void resetState() {
        hasJumped = false;
        hasAttacked = false;
        jumpStartTime = 0;
        lastJumpTime = 0;
        lastLandTime = 0;
    }

    private static void updateKillAura() {
        if (mc.player == null || mc.world == null) return;

        // Отключаем спринт
        if (ONLY_CRITS && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

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

        // Если нет цели – выходим
        if (target == null || mc.player.distanceTo(target) > RANGE) {
            return;
        }

        // === Логика прыжка и удара ===

        // Если игрок на земле – обновляем время приземления
        if (mc.player.isOnGround()) {
            if (lastLandTime == 0) {
                lastLandTime = now;
            }
            // Если игрок на земле и был флаг hasJumped, но мы уже приземлились – сбрасываем состояние
            if (hasJumped) {
                resetState();
                lastLandTime = now;
            }
        } else {
            // если в воздухе – сбрасываем lastLandTime, чтобы после приземления засчитать
            lastLandTime = 0;
        }

        // Проверка возможности прыжка:
        // - на земле
        // - прошло 50 мс с момента приземления (чтобы не прыгать сразу)
        // - прошло более 1 секунды с последнего прыжка (не обязательно, можно убрать, но оставим)
        if (mc.player.isOnGround() && !hasJumped && (now - lastLandTime >= 50) && (now - lastJumpTime >= 1000)) {
            // Прыгаем
            mc.player.jump();
            lastJumpTime = now;
            jumpStartTime = now;
            hasJumped = true;
            hasAttacked = false;
            // после прыжка выходим, не атакуем в этом тике
            return;
        }

        // Если мы в прыжке и ещё не атаковали, проверяем условия для удара
        if (hasJumped && !hasAttacked) {
            // Прошло ли 290 мс с начала прыжка?
            if (now - jumpStartTime >= 290) {
                // Проверяем, что игрок в воздухе и падает (velocity.y < 0)
                if (!mc.player.isOnGround() && mc.player.getVelocity().y < 0) {
                    // Наносим удар
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(mc.player.getActiveHand());
                    hasAttacked = true;
                    // После удара, ждём пока приземлится, затем будет задержка 50 мс перед следующим прыжком
                    // Сбрасываем hasJumped? Нет, оставим, чтобы не прыгать повторно до приземления.
                    // При приземлении сбросится в блоке выше.
                }
            }
        }

        // Если мы уже атаковали, но всё ещё в воздухе – ничего не делаем, ждём приземления.
        // При приземлении сбросится hasJumped.
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
