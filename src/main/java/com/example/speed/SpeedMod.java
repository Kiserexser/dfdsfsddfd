package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private Vec3d jumpPos = null;
    private boolean isJumpPosSet = false;

    private Thread workerThread;
    private volatile boolean running = true;
    private boolean wasRPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("FastLeaveFt loaded. Press R to save position, press R again to return.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean rPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

                        if (rPressed && !wasRPressed) {
                            mc.execute(this::handleKeyPress);
                            wasRPressed = true;
                        } else if (!rPressed) {
                            wasRPressed = false;
                        }
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("FastLeaveFt error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void handleKeyPress() {
        if (mc.player == null || mc.world == null) return;

        if (!isJumpPosSet) {
            // Сохраняем позицию
            jumpPos = mc.player.getPos();
            isJumpPosSet = true;
            mc.player.sendMessage(Text.of("§6FastLeaveFt §7» §aПозиция сохранена!"), true);
            LOGGER.info("Position saved: " + jumpPos);
        } else {
            // Телепортируем обратно
            if (jumpPos != null) {
                mc.player.setPosition(jumpPos.x, jumpPos.y, jumpPos.z);
                mc.player.sendMessage(Text.of("§6FastLeaveFt §7» §aТы вернулся!"), true);
                LOGGER.info("Returned to: " + jumpPos);
                // Сбрасываем
                jumpPos = null;
                isJumpPosSet = false;
            }
        }
    }
}
