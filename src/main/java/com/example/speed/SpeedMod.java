package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
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
import java.util.concurrent.ThreadLocalRandom;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // ==================== Состояния ====================
    private static boolean enabled = false;
    private static String currentMode = "FunTime";
    private static LivingEntity target = null;
    private static long lastAttackTime = 0;
    private static float lastYaw = 0;
    private static float lastPitch = 0;
    private static int hitCounter = 0;
    private static final Random random = new Random();
    private static final double RANGE = 4.5;

    // ==================== GUI ====================
    private static boolean wasRightShiftPressed = false;

    // ==================== Поток ====================
    private Thread workerThread;
    private volatile boolean running = true;

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura (5 modes) loaded. RightShift = GUI, R = toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();

                        // === R — включить/выключить ===
                        boolean rPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                        if (rPressed) {
                            enabled = !enabled;
                            if (!enabled) target = null;
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendMessage(Text.of("§6KillAura §7» " + (enabled ? "§aON" : "§cOFF")), true);
                                }
                            });
                            LOGGER.info("KillAura: " + (enabled ? "ON" : "OFF"));
                            Thread.sleep(300);
                        }

                        // === RightShift — GUI ===
                        boolean rightShiftPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                        if (rightShiftPressed && !wasRightShiftPressed) {
                            mc.execute(() -> {
                                if (mc.currentScreen == null || !(mc.currentScreen instanceof ModeMenuGUI)) {
                                    mc.setScreen(new ModeMenuGUI());
                                } else {
                                    mc.setScreen(null);
                                }
                            });
                            wasRightShiftPressed = true;
                        } else if (!rightShiftPressed) {
                            wasRightShiftPressed = false;
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

    // ==================== KILLAURA ЛОГИКА ====================
    private static void updateKillAura() {
        if (mc.player == null || mc.world == null) return;

        // === Выбор цели ===
        if (target == null || !target.isAlive() || mc.player.distanceTo(target) > RANGE) {
            target = getTarget();
            if (target == null) return;
        }

        if (mc.player.distanceTo(target) > RANGE) {
            target = null;
            return;
        }

        // === Вычисление углов ===
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);

        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float pitch = (float) -MathHelper.atan2(dy, dist) * (180F / (float) Math.PI);

        // === Ротация (режимы) ===
        float targetYaw = yaw;
        float targetPitch = pitch;

        switch (currentMode) {
            case "FunTime":
                targetYaw = yaw + (float) Math.sin(System.currentTimeMillis() / 1000.0 * 1.2) * 13.4f;
                targetPitch = pitch + (float) Math.cos(System.currentTimeMillis() / 1000.0 * 1.2) * 13.4f;
                targetYaw += (float) (random.nextDouble() - 0.5) * 0.05f;
                targetPitch += (float) (random.nextDouble() - 0.5) * 0.05f;
                break;
            case "Grim":
                targetYaw = yaw;
                targetPitch = pitch;
                targetYaw += (float) (random.nextDouble() - 0.5) * 0.02f;
                targetPitch += (float) (random.nextDouble() - 0.5) * 0.02f;
                break;
            case "SpookyTime":
                targetYaw = yaw + (float) Math.sin(System.currentTimeMillis() / 1000.0 * 0.6) * 4.2f;
                targetPitch = pitch + (float) Math.cos(System.currentTimeMillis() / 1000.0 * 0.6) * 4.2f;
                targetYaw += (float) (random.nextDouble() - 0.5) * 3.0f;
                targetPitch += (float) (random.nextDouble() - 0.5) * 3.0f;
                break;
            case "HolyLite":
                targetYaw = yaw;
                targetPitch = pitch;
                targetYaw += (float) (random.nextDouble() - 0.5) * 0.15f;
                targetPitch += (float) (random.nextDouble() - 0.5) * 0.15f;
                break;
            case "ReallyWorld":
                // Быстрый поворот без лишних эффектов
                targetYaw = yaw;
                targetPitch = pitch;
                break;
            default:
                targetYaw = yaw;
                targetPitch = pitch;
        }

        // === Ограничение изменения ===
        float maxYawChange = 35.0f;
        float maxPitchChange = 30.0f;
        float yawDiff = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float pitchDiff = targetPitch - lastPitch;
        if (Math.abs(yawDiff) > maxYawChange) {
            targetYaw = lastYaw + Math.signum(yawDiff) * maxYawChange;
        }
        if (Math.abs(pitchDiff) > maxPitchChange) {
            targetPitch = lastPitch + Math.signum(pitchDiff) * maxPitchChange;
        }

        lastYaw = targetYaw;
        lastPitch = targetPitch;

        mc.player.setYaw(targetYaw);
        mc.player.setPitch(targetPitch);

        // === Атака ===
        long now = System.currentTimeMillis();
        if (now - lastAttackTime > 500 && !mc.player.isOnGround() && mc.player.fallDistance > 0) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            lastAttackTime = now;
            hitCounter++;
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

    // ==================== GUI ====================
    private static class ModeMenuGUI extends Screen {
        private static final int WIDTH = 180;
        private static final int HEIGHT = 230;
        private int x, y;

        protected ModeMenuGUI() {
            super(Text.literal("KillAura Modes"));
        }

        @Override
        protected void init() {
            super.init();
            this.x = (this.width - WIDTH) / 2;
            this.y = (this.height - HEIGHT) / 2;

            String[] modes = {"FunTime", "Grim", "SpookyTime", "HolyLite", "ReallyWorld"};
            int btnY = y + 20;
            for (String mode : modes) {
                boolean isActive = currentMode.equals(mode);
                ButtonWidget btn = ButtonWidget.builder(
                        Text.literal(isActive ? "§a" + mode : mode),
                        button -> {
                            currentMode = mode;
                            this.clearChildren();
                            this.init();
                        }
                ).dimensions(x + 10, btnY, 160, 25).build();
                this.addDrawableChild(btn);
                btnY += 35;
            }

            ButtonWidget closeBtn = ButtonWidget.builder(
                    Text.literal("Закрыть"),
                    button -> this.close()
            ).dimensions(x + 55, y + HEIGHT - 30, 70, 20).build();
            this.addDrawableChild(closeBtn);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(x, y, x + WIDTH, y + HEIGHT, 0xDD222222);
            context.drawBorder(x, y, WIDTH, HEIGHT, 0xFFAAAAAA);

            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("§6Выберите режим"),
                    this.width / 2,
                    y + 5,
                    0xFFFFFF
            );

            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("§7Текущий: §a" + currentMode),
                    this.width / 2,
                    y + HEIGHT - 55,
                    0xFFFFFF
            );

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
            // Пусто – убираем затемнение
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        public void close() {
            if (client != null) client.setScreen(null);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.close();
                return true;
            }
            return false;
        }
    }
}
