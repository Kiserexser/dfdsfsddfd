package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
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

    // === Настройки ===
    public static double RANGE = 4.5;
    public static double MIN_DELAY = 0.680;
    public static double MAX_DELAY = 0.700;
    public static boolean SPRINT_RESET = true;
    public static float SMOOTH_SPEED = 0.15f;
    public static boolean ENABLE_SHIFT = true;
    public static float SHIFT_DEGREES = 0.5f;
    public static long SHIFT_DURATION_MS = 3000;
    public static long RETURN_DURATION_MS = 2000;
    public static float JITTER_RANGE = 0.15f;
    public static boolean ENABLED = false;

    private static final Random random = new Random();
    private long lastAttackTime = 0;

    private Thread workerThread;
    private volatile boolean running = true;
    private float targetYaw = 0, targetPitch = 0;
    private long shiftCycleStart = System.currentTimeMillis();
    private boolean isShiftPhase = true;
    private LivingEntity lockedTarget = null;
    private boolean wasRightShiftPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod KillAura loaded. Press RIGHT SHIFT to open settings.");

        workerThread = new Thread(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            while (running) {
                try {
                    if (client != null && client.getWindow() != null) {
                        long window = client.getWindow().getHandle();

                        boolean rightShiftPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                        if (rightShiftPressed && !wasRightShiftPressed) {
                            client.execute(() -> client.setScreen(new SettingsGUI()));
                            wasRightShiftPressed = true;
                        } else if (!rightShiftPressed) {
                            wasRightShiftPressed = false;
                        }
                    }

                    if (!ENABLED || client == null || client.player == null || client.world == null) {
                        Thread.sleep(50);
                        continue;
                    }

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

    // =================== GUI ===================
    private static class SettingsGUI extends Screen {
        private static final int WIDTH = 230;
        private static final int HEIGHT = 300;
        private int x, y;

        private ButtonWidget toggleButton;
        private SliderWidget rangeSlider;
        private SliderWidget minDelaySlider;
        private SliderWidget maxDelaySlider;
        private SliderWidget jitterSlider;
        private SliderWidget shiftSlider;
        private ButtonWidget sprintToggle;
        private ButtonWidget shiftToggle;

        protected SettingsGUI() {
            super(Text.literal("SpeedMod Settings"));
        }

        @Override
        protected void init() {
            super.init();
            this.x = (this.width - WIDTH) / 2;
            this.y = (this.height - HEIGHT) / 2;

            // === Кнопка On/Off ===
            toggleButton = ButtonWidget.builder(
                    Text.literal(ENABLED ? "§aON" : "§cOFF"),
                    btn -> {
                        ENABLED = !ENABLED;
                        toggleButton.setMessage(Text.literal(ENABLED ? "§aON" : "§cOFF"));
                        if (client != null && client.player != null) {
                            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                        }
                    }
            ).dimensions(x + 10, y + 25, 60, 20).build();
            this.addDrawableChild(toggleButton);

            // === Радиус ===
            rangeSlider = new SliderWidget(x + 10, y + 55, 200, 20, Text.literal("Радиус: " + RANGE), RANGE) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.literal("Радиус: " + String.format("%.1f", value)));
                }
                @Override
                protected void applyValue() {
                    RANGE = value;
                }
                @Override
                public double getMinValue() {
                    return 1.0;
                }
                @Override
                public double getMaxValue() {
                    return 8.0;
                }
            };
            this.addDrawableChild(rangeSlider);

            // === Мин. задержка ===
            minDelaySlider = new SliderWidget(x + 10, y + 80, 200, 20, Text.literal("Мин. задержка: " + MIN_DELAY), MIN_DELAY) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.literal("Мин. задержка: " + String.format("%.3f", value)));
                }
                @Override
                protected void applyValue() {
                    MIN_DELAY = value;
                    if (MIN_DELAY > MAX_DELAY) MAX_DELAY = MIN_DELAY;
                }
                @Override
                public double getMinValue() {
                    return 0.1;
                }
                @Override
                public double getMaxValue() {
                    return 1.0;
                }
            };
            this.addDrawableChild(minDelaySlider);

            // === Макс. задержка ===
            maxDelaySlider = new SliderWidget(x + 10, y + 105, 200, 20, Text.literal("Макс. задержка: " + MAX_DELAY), MAX_DELAY) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.literal("Макс. задержка: " + String.format("%.3f", value)));
                }
                @Override
                protected void applyValue() {
                    MAX_DELAY = value;
                    if (MAX_DELAY < MIN_DELAY) MIN_DELAY = MAX_DELAY;
                }
                @Override
                public double getMinValue() {
                    return 0.1;
                }
                @Override
                public double getMaxValue() {
                    return 1.0;
                }
            };
            this.addDrawableChild(maxDelaySlider);

            // === Джиттер ===
            jitterSlider = new SliderWidget(x + 10, y + 130, 200, 20, Text.literal("Джиттер: " + JITTER_RANGE), JITTER_RANGE) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.literal("Джиттер: " + String.format("%.2f", value)));
                }
                @Override
                protected void applyValue() {
                    JITTER_RANGE = (float) value;
                }
                @Override
                public double getMinValue() {
                    return 0.0;
                }
                @Override
                public double getMaxValue() {
                    return 1.0;
                }
            };
            this.addDrawableChild(jitterSlider);

            // === Смещение ===
            shiftSlider = new SliderWidget(x + 10, y + 155, 200, 20, Text.literal("Смещение: " + SHIFT_DEGREES + "°"), SHIFT_DEGREES) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.literal("Смещение: " + String.format("%.2f", value) + "°"));
                }
                @Override
                protected void applyValue() {
                    SHIFT_DEGREES = (float) value;
                }
                @Override
                public double getMinValue() {
                    return 0.0;
                }
                @Override
                public double getMaxValue() {
                    return 1.0;
                }
            };
            this.addDrawableChild(shiftSlider);

            // === Сброс спринта (кнопка-переключатель) ===
            sprintToggle = ButtonWidget.builder(
                    Text.literal(SPRINT_RESET ? "§aСброс спринта Вкл" : "§cСброс спринта Выкл"),
                    btn -> {
                        SPRINT_RESET = !SPRINT_RESET;
                        sprintToggle.setMessage(Text.literal(SPRINT_RESET ? "§aСброс спринта Вкл" : "§cСброс спринта Выкл"));
                    }
            ).dimensions(x + 10, y + 180, 140, 20).build();
            this.addDrawableChild(sprintToggle);

            // === Смещение вкл/выкл (кнопка-переключатель) ===
            shiftToggle = ButtonWidget.builder(
                    Text.literal(ENABLE_SHIFT ? "§aСмещение Вкл" : "§cСмещение Выкл"),
                    btn -> {
                        ENABLE_SHIFT = !ENABLE_SHIFT;
                        shiftToggle.setMessage(Text.literal(ENABLE_SHIFT ? "§aСмещение Вкл" : "§cСмещение Выкл"));
                    }
            ).dimensions(x + 10, y + 205, 140, 20).build();
            this.addDrawableChild(shiftToggle);

            // === Кнопка "Закрыть" ===
            ButtonWidget closeButton = ButtonWidget.builder(
                    Text.literal("Закрыть"),
                    btn -> this.close()
            ).dimensions(x + 80, y + HEIGHT - 30, 70, 20).build();
            this.addDrawableChild(closeButton);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int bgColor = 0xFFFFFF;
            int borderColor = 0xFFB6C1;
            int textColor = 0xFF69B4;

            context.fill(x, y, x + WIDTH, y + HEIGHT, bgColor);
            context.fill(x, y, x + WIDTH, y + 1, borderColor);
            context.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, borderColor);
            context.fill(x, y, x + 1, y + HEIGHT, borderColor);
            context.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, borderColor);

            context.drawCenteredTextWithShadow(textRenderer, Text.literal("§dCombat"), x + WIDTH/2, y + 5, textColor);
            context.drawTextWithShadow(textRenderer, Text.literal("§dKillAura"), x + 15, y + 28, textColor);

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
