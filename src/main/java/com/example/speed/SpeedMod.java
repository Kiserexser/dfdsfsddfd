package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Identifier INVENTORY_TEXTURE = Identifier.of("textures/gui/container/inventory.png");

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
        private static final int TOTAL_WIDTH = 669;
        private static final int TOTAL_HEIGHT = 324;
        private static final int COLS = 5;
        private static final int WINDOW_HEIGHT = 300;
        private static final int GAP = 6;
        private static final int WINDOW_WIDTH = 129;

        protected SpeedModGUI() {
            super(Text.literal("SpeedMod GUI"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Нет затемнения фона

            int centerX = (this.width - TOTAL_WIDTH) / 2;
            int centerY = (this.height - TOTAL_HEIGHT) / 2;

            int totalWindowsWidth = COLS * WINDOW_WIDTH + (COLS - 1) * GAP;
            int marginLeft = (TOTAL_WIDTH - totalWindowsWidth) / 2;
            int marginTop = (TOTAL_HEIGHT - WINDOW_HEIGHT) / 2;

            for (int col = 0; col < COLS; col++) {
                int x = centerX + marginLeft + col * (WINDOW_WIDTH + GAP);
                int y = centerY + marginTop;

                // Рисуем панель инвентаря с закруглёнными углами
                context.drawTexture(RenderLayer::getGuiTextured, INVENTORY_TEXTURE, x, y, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, 176, 166);
            }
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
        public boolean shouldCloseOnEsc() {
            return true;
        }

        // === Клавиши: ESC закрывает, остальные идут в игру ===
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.close();
                return true;
            }
            return false; // пропускаем в игру
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            return false; // пропускаем в игру
        }

        // === Мышь не блокируется – клики будут обрабатываться в будущем ===

        @Override
        public void close() {
            if (client != null) client.setScreen(null);
        }
    }
}
