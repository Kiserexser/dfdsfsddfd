package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvents;
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

    // === Жёсткие настройки (не редактируются) ===
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

    private static boolean enabled = false;
    private static final Random random = new Random();
    private long lastAttackTime = 0;

    private Thread workerThread;
    private volatile boolean running = true;

    private float targetYaw = 0, targetPitch = 0;
    private long shiftCycleStart = System.currentTimeMillis();
    private boolean isShiftPhase = true;
    private LivingEntity lockedTarget = null;

    // Для синхронизации с GUI
    private static boolean wasRightShiftPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod KillAura loaded. Press R or RIGHT SHIFT to toggle.");

        workerThread = new Thread(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            while (running) {
                try {
                    if (client != null && client.getWindow() != null) {
                        long window = client.getWindow().getHandle();

                        // === Обработка R ===
                        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                            toggle();
                            Thread.sleep(300);
                        }

                        // === Обработка правого Shift (открытие GUI) ===
                        boolean rightShiftPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                        if (rightShiftPressed && !wasRightShiftPressed) {
                            client.execute(() -> client.setScreen(new KillAuraGUI()));
                            wasRightShiftPressed = true;
                        } else if (!rightShiftPressed) {
                            wasRightShiftPressed = false;
                        }
                    }

                    if (!enabled || client == null || client.player == null || client.world == null) {
                        Thread.sleep(50);
                        continue;
                    }

                    // === Логика KillAura ===
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

    private static void toggle() {
        enabled = !enabled;
        if (!enabled) {
            // сброс цели при выключении
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal(enabled ? "§aKillAura ON" : "§cKillAura OFF"), true);
            }
        }
        LOGGER.info("KillAura: " + (enabled ? "ON" : "OFF"));
        // Проигрываем звук через клиент
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
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

    // =================== GUI (только On/Off) ===================
    private static class KillAuraGUI extends Screen {
        private static final int WIDTH = 160;
        private static final int HEIGHT = 90;
        private int x, y;
        private ButtonWidget toggleButton;

        protected KillAuraGUI() {
            super(Text.literal("SpeedMod"));
        }

        @Override
        protected void init() {
            super.init();
            this.x = (this.width - WIDTH) / 2;
            this.y = (this.height - HEIGHT) / 2;

            toggleButton = ButtonWidget.builder(
                    Text.literal(enabled ? "§aВКЛ" : "§cВЫКЛ"),
                    btn -> {
                        toggle(); // переключаем состояние через тот же метод
                        toggleButton.setMessage(Text.literal(enabled ? "§aВКЛ" : "§cВЫКЛ"));
                    }
            ).dimensions(x + 30, y + 40, 100, 20).build();
            this.addDrawableChild(toggleButton);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Белый фон с розовой рамкой
            int bgColor = 0xFFFFFF;
            int borderColor = 0xFFB6C1;
            int textColor = 0xFF69B4;

            context.fill(x, y, x + WIDTH, y + HEIGHT, bgColor);
            context.fill(x, y, x + WIDTH, y + 1, borderColor);
            context.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, borderColor);
            context.fill(x, y, x + 1, y + HEIGHT, borderColor);
            context.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, borderColor);

            context.drawCenteredTextWithShadow(textRenderer, Text.literal("§dKillAura"), x + WIDTH/2, y + 10, textColor);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(enabled ? "§aВключена" : "§cВыключена"), x + WIDTH/2, y + 25, textColor);

            super.render(context, mouseX, mouseY, delta);
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
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
