package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;

    private Thread workerThread;
    private volatile boolean running = true;
    private boolean wasKPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedFlow (Vodkacraft спиды) loaded. Press K to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean kPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;

                        if (kPressed && !wasKPressed) {
                            enabled = !enabled;
                            LOGGER.info("SpeedFlow: " + (enabled ? "ON" : "OFF"));
                            wasKPressed = true;
                        } else if (!kPressed) {
                            wasKPressed = false;
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        mc.execute(SpeedMod::applySpeed);
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("SpeedFlow error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private static void applySpeed() {
        if (mc.player == null) return;

        // === Условия для активации спид-прыжка ===
        boolean isOnGround = mc.player.isOnGround();
        boolean hasMovement = mc.player.forwardSpeed != 0 || mc.player.sidewaysSpeed != 0;
        boolean hasHorizontalCollision = mc.player.horizontalCollision;
        boolean isJumpKeyPressed = mc.options.jumpKey.isPressed();

        if (isOnGround && hasMovement && !hasHorizontalCollision && !isJumpKeyPressed) {
            // Прыгаем
            mc.player.jump();

            // Устанавливаем вертикальную скорость 0.1
            mc.player.setVelocity(mc.player.getVelocity().x, 0.1, mc.player.getVelocity().z);

            // Устанавливаем горизонтальную скорость 0.4
            float yaw = mc.player.getYaw() * 0.017453292F;
            float speed = 0.40f;
            double x = -MathHelper.sin(yaw) * speed;
            double z = MathHelper.cos(yaw) * speed;
            mc.player.setVelocity(x, mc.player.getVelocity().y, z);
        }
    }
}
