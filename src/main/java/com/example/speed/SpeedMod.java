package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static String currentMode = "Normal"; // Normal, Matrix, Shift, Grim
    private static float speedMultiplier = 2.0f;
    private static float energy = 0.0f;
    private static long lastSetbackTime = 0;
    private static float normalTickDelta = 1.0f;

    // Для дебаунса клавиш
    private boolean wasZ = false, wasX = false, wasC = false;
    private boolean wasRightShift = false;

    private Thread workerThread;
    private volatile boolean running = true;

    private static Field timerField;
    private static Field tickDeltaField;

    static {
        try {
            timerField = MinecraftClient.class.getDeclaredField("timer");
            timerField.setAccessible(true);
        } catch (Exception e) {
            LOGGER.error("Could not find timer field", e);
        }
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Timer loaded. RightShift=GUI, Z=Normal, X=Matrix, C=Shift");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();

                        // === Проверка правого Shift ===
                        boolean rightShiftPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                        if (rightShiftPressed && !wasRightShift) {
                            mc.execute(() -> mc.setScreen(new TimerGUI()));
                            wasRightShift = true;
                        } else if (!rightShiftPressed) {
                            wasRightShift = false;
                        }

                        // === Z X C (только если мод включён) ===
                        boolean zPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
                        boolean xPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_X) == GLFW.GLFW_PRESS;
                        boolean cPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;

                        if (zPressed && !wasZ) {
                            currentMode = "Normal";
                            LOGGER.info("Mode: Normal");
                            wasZ = true;
                        } else if (!zPressed) wasZ = false;

                        if (xPressed && !wasX) {
                            currentMode = "Matrix";
                            LOGGER.info("Mode: Matrix");
                            wasX = true;
                        } else if (!xPressed) wasX = false;

                        if (cPressed && !wasC) {
                            currentMode = "Shift";
                            LOGGER.info("Mode: Shift");
                            wasC = true;
                        } else if (!cPressed) wasC = false;

                        // === Применение таймера ===
                        if (enabled) {
                            updateTimer();
                        } else {
                            setTickDelta(1.0f);
                        }
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) { break; }
                catch (Exception e) { LOGGER.error("Timer error", e); }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void setTickDelta(float value) {
        try {
            if (timerField == null) return;
            Object timer = timerField.get(mc);
            if (timer == null) return;
            if (tickDeltaField == null) {
                tickDeltaField = timer.getClass().getDeclaredField("tickDelta");
                tickDeltaField.setAccessible(true);
            }
            tickDeltaField.setFloat(timer, value);
        } catch (Exception e) {
            // игнорируем
        }
    }

    private void updateTimer() {
        float target = 1.0f;

        switch (currentMode) {
            case "Normal":
                target = speedMultiplier;
                break;
            case "Matrix":
                if (isPlayerStill()) {
                    energy = Math.min(energy + 0.025f, 1.0f);
                } else {
                    energy = Math.max(energy - 0.005f, 0.0f);
                }
                if (energy > 0 && isPlayerMoving()) {
                    target = speedMultiplier;
                    energy = Math.max(energy - (float) ((0.1 * speedMultiplier) - 0.1), 0.0f);
                } else {
                    target = 1.0f;
                }
                break;
            case "Shift":
                target = speedMultiplier;
                break;
            case "Grim":
                long window = mc.getWindow().getHandle();
                boolean boostPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
                long now = System.currentTimeMillis();
                if (boostPressed && (now - lastSetbackTime > 2000)) {
                    target = speedMultiplier;
                    energy = Math.max(energy - (float) ((0.0025 * speedMultiplier) - 0.0025), 0.0f);
                } else {
                    target = 1.0f;
                }
                break;
            default:
                target = 1.0f;
        }

        setTickDelta(target);
    }

    private boolean isPlayerStill() {
        if (mc.player == null) return true;
        return mc.player.getVelocity().lengthSquared() < 0.001 &&
               !mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed() &&
               !mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed();
    }

    private boolean isPlayerMoving() {
        if (mc.player == null) return false;
        return mc.player.getVelocity().lengthSquared() > 0.001 ||
               mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() ||
               mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    // ======================== GUI ========================
    private static class TimerGUI extends Screen {
        private ButtonWidget toggleButton;
        private ButtonWidget normalBtn, matrixBtn, shiftBtn, grimBtn;

        protected TimerGUI() {
            super(Text.literal("Timer Settings"));
        }

        @Override
        protected void init() {
            super.init();
            int centerX = this.width / 2;
            int startY = this.height / 2 - 50;

            toggleButton = ButtonWidget.builder(
                    Text.literal(enabled ? "§aВключён" : "§cВыключен"),
                    btn -> {
                        enabled = !enabled;
                        btn.setMessage(Text.literal(enabled ? "§aВключён" : "§cВыключен"));
                    }
            ).dimensions(centerX - 50, startY, 100, 20).build();
            this.addDrawableChild(toggleButton);

            normalBtn = ButtonWidget.builder(
                    Text.literal(currentMode.equals("Normal") ? "§aNormal" : "Normal"),
                    btn -> {
                        currentMode = "Normal";
                        updateButtons();
                    }
            ).dimensions(centerX - 60, startY + 30, 50, 20).build();
            this.addDrawableChild(normalBtn);

            matrixBtn = ButtonWidget.builder(
                    Text.literal(currentMode.equals("Matrix") ? "§aMatrix" : "Matrix"),
                    btn -> {
                        currentMode = "Matrix";
                        updateButtons();
                    }
            ).dimensions(centerX, startY + 30, 50, 20).build();
            this.addDrawableChild(matrixBtn);

            shiftBtn = ButtonWidget.builder(
                    Text.literal(currentMode.equals("Shift") ? "§aShift" : "Shift"),
                    btn -> {
                        currentMode = "Shift";
                        updateButtons();
                    }
            ).dimensions(centerX - 60, startY + 60, 50, 20).build();
            this.addDrawableChild(shiftBtn);

            grimBtn = ButtonWidget.builder(
                    Text.literal(currentMode.equals("Grim") ? "§aGrim" : "Grim"),
                    btn -> {
                        currentMode = "Grim";
                        updateButtons();
                    }
            ).dimensions(centerX, startY + 60, 50, 20).build();
            this.addDrawableChild(grimBtn);

            ButtonWidget closeBtn = ButtonWidget.builder(
                    Text.literal("Закрыть"),
                    btn -> this.close()
            ).dimensions(centerX - 30, startY + 90, 60, 20).build();
            this.addDrawableChild(closeBtn);

            updateButtons();
        }

        private void updateButtons() {
            toggleButton.setMessage(Text.literal(enabled ? "§aВключён" : "§cВыключен"));
            normalBtn.setMessage(Text.literal(currentMode.equals("Normal") ? "§aNormal" : "Normal"));
            matrixBtn.setMessage(Text.literal(currentMode.equals("Matrix") ? "§aMatrix" : "Matrix"));
            shiftBtn.setMessage(Text.literal(currentMode.equals("Shift") ? "§aShift" : "Shift"));
            grimBtn.setMessage(Text.literal(currentMode.equals("Grim") ? "§aGrim" : "Grim"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Timer Settings"), this.width / 2, this.height / 2 - 80, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() { return false; }

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
