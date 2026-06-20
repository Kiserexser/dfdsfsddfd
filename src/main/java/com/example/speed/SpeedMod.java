package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private Thread workerThread;
    private volatile boolean running = true;
    private boolean wasRightShiftPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod GUI loaded. Press Right Shift to open.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean rightShiftPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

                        if (rightShiftPressed && !wasRightShiftPressed) {
                            mc.execute(() -> {
                                if (mc.currentScreen == null || !(mc.currentScreen instanceof SpeedModGUI)) {
                                    mc.setScreen(new SpeedModGUI());
                                } else {
                                    mc.setScreen(null);
                                }
                            });
                            wasRightShiftPressed = true;
                        } else if (!rightShiftPressed) {
                            wasRightShiftPressed = false;
                        }
                    }
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("GUI error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // ==================== GUI ====================
    private static class SpeedModGUI extends Screen {
        private static final int TOTAL_WIDTH = 576;
        private static final int TOTAL_HEIGHT = 324;
        private static final int COLS = 5;
        private static final int WINDOW_HEIGHT = 300;
        private static final int GAP = 5;

        protected SpeedModGUI() {
            super(Text.literal("SpeedMod GUI"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Нет заливки фона – видно игровой мир

            // Центрируем область
            int centerX = (this.width - TOTAL_WIDTH) / 2;
            int centerY = (this.height - TOTAL_HEIGHT) / 2;

            // Ширина окна: (576 - 4*5) / 5 = 111.2 → 111 (целое)
            int windowWidth = (TOTAL_WIDTH - (COLS - 1) * GAP) / COLS; // = 111

            // Общая ширина всех окон с промежутками
            int totalWindowsWidth = COLS * windowWidth + (COLS - 1) * GAP;
            int marginLeft = (TOTAL_WIDTH - totalWindowsWidth) / 2;
            int marginTop = (TOTAL_HEIGHT - WINDOW_HEIGHT) / 2;

            for (int col = 0; col < COLS; col++) {
                int x = centerX + marginLeft + col * (windowWidth + GAP);
                int y = centerY + marginTop;

                // Белый фон окна
                context.fill(x, y, x + windowWidth, y + WINDOW_HEIGHT, 0xFFFFFFFF);
                // Серая рамка
                context.drawBorder(x, y, windowWidth, WINDOW_HEIGHT, 0xFFAAAAAA);

                // Закругление углов (белые квадраты по углам)
                int r = 4;
                context.fill(x, y, x + r, y + r, 0xFFFFFFFF);
                context.fill(x + windowWidth - r, y, x + windowWidth, y + r, 0xFFFFFFFF);
                context.fill(x, y + WINDOW_HEIGHT - r, x + r, y + WINDOW_HEIGHT, 0xFFFFFFFF);
                context.fill(x + windowWidth - r, y + WINDOW_HEIGHT - r, x + windowWidth, y + WINDOW_HEIGHT, 0xFFFFFFFF);
            }

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
