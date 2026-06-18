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
        private static final int HEIGHT = 320;
        private int x, y;

        private ButtonWidget toggleButton;

        // Кнопки для изменения параметров
        private ButtonWidget rangeDec, rangeInc;
        private ButtonWidget minDelayDec, minDelayInc;
        private ButtonWidget maxDelayDec, maxDelayInc;
        private ButtonWidget jitterDec, jitterInc;
        private ButtonWidget shiftDec, shiftInc;
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

            // On/Off
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

            // Радиус
            addLabelAndButtons("Радиус", RANGE, 0.1, 1.0, 8.0, 
                    val -> { RANGE = val; }, 
                    y + 55, 
                    (rangeDec = new ButtonWidget(x + 80, y + 55, 20, 20, Text.literal("-"), btn -> {
                        RANGE = Math.max(1.0, RANGE - 0.1);
                    })),
                    (rangeInc = new ButtonWidget(x + 110, y + 55, 20, 20, Text.literal("+"), btn -> {
                        RANGE = Math.min(8.0, RANGE + 0.1);
                    }))
            );
            this.addDrawableChild(rangeDec);
            this.addDrawableChild(rangeInc);

            // Мин. задержка
            addLabelAndButtons("Мин. задержка", MIN_DELAY, 0.005, 0.1, 1.0,
                    val -> { MIN_DELAY = val; if (MIN_DELAY > MAX_DELAY) MAX_DELAY = MIN_DELAY; },
                    y + 80,
                    (minDelayDec = new ButtonWidget(x + 110, y + 80, 20, 20, Text.literal("-"), btn -> {
                        MIN_DELAY = Math.max(0.1, MIN_DELAY - 0.005);
                        if (MIN_DELAY > MAX_DELAY) MAX_DELAY = MIN_DELAY;
                    })),
                    (minDelayInc = new ButtonWidget(x + 140, y + 80, 20, 20, Text.literal("+"), btn -> {
                        MIN_DELAY = Math.min(1.0, MIN_DELAY + 0.005);
                        if (MIN_DELAY > MAX_DELAY) MAX_DELAY = MIN_DELAY;
                    }))
            );
            this.addDrawableChild(minDelayDec);
            this.addDrawableChild(minDelayInc);

            // Макс. задержка
            addLabelAndButtons("Макс. задержка", MAX_DELAY, 0.005, 0.1, 1.0,
                    val -> { MAX_DELAY = val; if (MAX_DELAY < MIN_DELAY) MIN_DELAY = MAX_DELAY; },
                    y + 105,
                    (maxDelayDec = new ButtonWidget(x + 110, y + 105, 20, 20, Text.literal("-"), btn -> {
                        MAX_DELAY = Math.max(0.1, MAX_DELAY - 0.005);
                        if (MAX_DELAY < MIN_DELAY) MIN_DELAY = MAX_DELAY;
                    })),
                    (maxDelayInc = new ButtonWidget(x + 140, y + 105, 20, 20, Text.literal("+"), btn -> {
                        MAX_DELAY = Math.min(1.0, MAX_DELAY + 0.005);
                        if (MAX_DELAY < MIN_DELAY) MIN_DELAY = MAX_DELAY;
                    }))
            );
            this.addDrawableChild(maxDelayDec);
            this.addDrawableChild(maxDelayInc);

            // Джиттер
            addLabelAndButtons("Джиттер", JITTER_RANGE, 0.01, 0.0, 1.0,
                    val -> { JITTER_RANGE = (float) val; },
                    y + 130,
                    (jitterDec = new ButtonWidget(x + 110, y + 130, 20, 20, Text.literal("-"), btn -> {
                        JITTER_RANGE = (float) Math.max(0.0, JITTER_RANGE - 0.01);
                    })),
                    (jitterInc = new ButtonWidget(x + 140, y + 130, 20, 20, Text.literal("+"), btn -> {
                        JITTER_RANGE = (float) Math.min(1.0, JITTER_RANGE + 0.01);
                    }))
            );
            this.addDrawableChild(jitterDec);
            this.addDrawableChild(jitterInc);

            // Смещение
            addLabelAndButtons("Смещение", SHIFT_DEGREES, 0.05, 0.0, 1.0,
                    val -> { SHIFT_DEGREES = (float) val; },
                    y + 155,
                    (shiftDec = new ButtonWidget(x + 110, y + 155, 20, 20, Text.literal("-"), btn -> {
                        SHIFT_DEGREES = (float) Math.max(0.0, SHIFT_DEGREES - 0.05);
                    })),
                    (shiftInc = new ButtonWidget(x + 140, y + 155, 20, 20, Text.literal("+"), btn -> {
                        SHIFT_DEGREES = (float) Math.min(1.0, SHIFT_DEGREES + 0.05);
                    }))
            );
            this.addDrawableChild(shiftDec);
            this.addDrawableChild(shiftInc);

            // Сброс спринта (кнопка-переключатель)
            sprintToggle = ButtonWidget.builder(
                    Text.literal(SPRINT_RESET ? "§aСброс спринта Вкл" : "§cСброс спринта Выкл"),
                    btn -> {
                        SPRINT_RESET = !SPRINT_RESET;
                        sprintToggle.setMessage(Text.literal(SPRINT_RESET ? "§aСброс спринта Вкл" : "§cСброс спринта Выкл"));
                    }
            ).dimensions(x + 10, y + 180, 140, 20).build();
            this.addDrawableChild(sprintToggle);

            // Смещение вкл/выкл
            shiftToggle = ButtonWidget.builder(
                    Text.literal(ENABLE_SHIFT ? "§aСмещение Вкл" : "§cСмещение Выкл"),
                    btn -> {
                        ENABLE_SHIFT = !ENABLE_SHIFT;
                        shiftToggle.setMessage(Text.literal(ENABLE_SHIFT ? "§aСмещение Вкл" : "§cСмещение Выкл"));
                    }
            ).dimensions(x + 10, y + 205, 140, 20).build();
            this.addDrawableChild(shiftToggle);

            // Закрыть
            ButtonWidget closeButton = ButtonWidget.builder(
                    Text.literal("Закрыть"),
                    btn -> this.close()
            ).dimensions(x + 80, y + HEIGHT - 30, 70, 20).build();
            this.addDrawableChild(closeButton);
        }

        // Вспомогательный метод для отображения текста
        private void addLabelAndButtons(String label, double value, double step, double min, double max, java.util.function.DoubleConsumer setter, int yPos, ButtonWidget dec, ButtonWidget inc) {
            // Здесь не делаем ничего, просто рисуем текст в render
            // Но добавим кнопки уже созданы
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

            // Отображаем значения параметров
            drawLabelValue(context, "Радиус", RANGE, 1, 55);
            drawLabelValue(context, "Мин. задержка", MIN_DELAY, 1, 80);
            drawLabelValue(context, "Макс. задержка", MAX_DELAY, 1, 105);
            drawLabelValue(context, "Джиттер", JITTER_RANGE, 1, 130);
            drawLabelValue(context, "Смещение", SHIFT_DEGREES, 1, 155);

            super.render(context, mouseX, mouseY, delta);
        }

        private void drawLabelValue(DrawContext context, String label, double value, int col, int row) {
            String text = label + ": " + String.format("%.2f", value);
            context.drawTextWithShadow(textRenderer, Text.literal(text), x + 10, y + row + 5, 0x333333);
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
