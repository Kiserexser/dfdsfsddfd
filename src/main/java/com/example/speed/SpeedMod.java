package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static String currentMode = "Normal";
    private static float speedMultiplier = 2.0f;
    private static float energy = 0.0f;
    private static long lastSetbackTime = 0;

    // Дебаунс
    private boolean wasK = false, wasZ = false, wasX = false, wasC = false, wasV = false;

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
        LOGGER.info("Timer loaded. K=Toggle, Z=Normal, X=Matrix, C=Shift, V=Grim");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();

                        // === ВСЕГДА читаем клавиши ===
                        boolean kPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
                        boolean zPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
                        boolean xPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_X) == GLFW.GLFW_PRESS;
                        boolean cPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
                        boolean vPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS;

                        // === K – вкл/выкл (с дебаунсом) ===
                        if (kPressed && !wasK) {
                            enabled = !enabled;
                            if (!enabled) resetTimer();
                            LOGGER.info("Timer " + (enabled ? "ON" : "OFF") + " (Mode: " + currentMode + ")");
                            wasK = true;
                        } else if (!kPressed) wasK = false;

                        // === Z – Normal (всегда можно переключать) ===
                        if (zPressed && !wasZ) {
                            currentMode = "Normal";
                            LOGGER.info("Mode changed to: Normal");
                            wasZ = true;
                        } else if (!zPressed) wasZ = false;

                        // === X – Matrix ===
                        if (xPressed && !wasX) {
                            currentMode = "Matrix";
                            LOGGER.info("Mode changed to: Matrix");
                            wasX = true;
                        } else if (!xPressed) wasX = false;

                        // === C – Shift ===
                        if (cPressed && !wasC) {
                            currentMode = "Shift";
                            LOGGER.info("Mode changed to: Shift");
                            wasC = true;
                        } else if (!cPressed) wasC = false;

                        // === V – Grim ===
                        if (vPressed && !wasV) {
                            currentMode = "Grim";
                            LOGGER.info("Mode changed to: Grim");
                            wasV = true;
                        } else if (!vPressed) wasV = false;

                        // === Если мод включён – применяем таймер ===
                        if (enabled) {
                            updateTimer();
                        } else {
                            // Если выключен – сбрасываем tickDelta
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

    private void resetTimer() {
        setTickDelta(1.0f);
        energy = 0.0f;
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
            // Игнорируем, чтобы не спамить лог
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
                    energy = Math.max(energy - (float)((0.1 * speedMultiplier) - 0.1), 0.0f);
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
                    energy = Math.max(energy - (float)((0.0025 * speedMultiplier) - 0.0025), 0.0f);
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
}
