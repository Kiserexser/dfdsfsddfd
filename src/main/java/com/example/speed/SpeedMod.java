package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    public static SpeedThread speedThread;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod загружен! Нажми R для открытия меню.");
        speedThread = new SpeedThread();
        speedThread.setDaemon(true);
        speedThread.start();
    }

    // ==================== ГЛАВНОЕ МЕНЮ ====================
    public static class SpeedMenuScreen extends Screen {
        private static SpeedMenuScreen instance;
        private boolean enabled = false;
        private float multiplier = 1.6f;

        public SpeedMenuScreen() {
            super(Text.literal("SpeedMod Menu"));
            instance = this;
        }

        @Override
        protected void init() {
            super.init();

            // Кнопка включения/выключения
            ButtonWidget toggleButton = ButtonWidget.builder(
                    Text.literal(enabled ? "§aВКЛЮЧЁН" : "§cВЫКЛЮЧЁН"),
                    button -> {
                        enabled = !enabled;
                        button.setMessage(Text.literal(enabled ? "§aВКЛЮЧЁН" : "§cВЫКЛЮЧЁН"));
                        SpeedThread.enabled = enabled;
                    }
            ).dimensions(width / 2 - 75, height / 2 - 30, 150, 20).build();

            addDrawableChild(toggleButton);

            // Ползунок скорости
            SliderWidget speedSlider = new SliderWidget(
                    width / 2 - 100, height / 2, 200, 20,
                    Text.literal("Скорость: " + multiplier + "x"),
                    1.0
            ) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.literal("Скорость: " + String.format("%.1f", multiplier) + "x"));
                }

                @Override
                protected void applyValue() {
                    multiplier = (float) (1.0 + this.value * 2.0); // от 1.0 до 3.0
                    SpeedThread.MULTIPLIER = multiplier;
                    SpeedThread.updateTargetSpeed();
                }
            };
            speedSlider.setValue((multiplier - 1.0) / 2.0);
            addDrawableChild(speedSlider);

            // Информация
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("§7Ускорение на снегу и полублоках"),
                    button -> {}
            ).dimensions(width / 2 - 100, height / 2 + 30, 200, 20).build());

            // Кнопка закрытия
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Закрыть"),
                    button -> this.close()
            ).dimensions(width / 2 - 50, height / 2 + 60, 100, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, "§6SpeedMod", width / 2, height / 2 - 55, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, "§7Нажми R для открытия", width / 2, height / 2 - 40, 0xAAAAAA);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_R) {
                this.close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void close() {
            super.close();
            if (client != null) {
                client.setScreen(null);
            }
        }

        public static void open() {
            if (MinecraftClient.getInstance().currentScreen instanceof SpeedMenuScreen) {
                MinecraftClient.getInstance().setScreen(null);
            } else {
                MinecraftClient.getInstance().setScreen(new SpeedMenuScreen());
            }
        }
    }

    // ==================== ПОТОК СКОРОСТИ ====================
    public static class SpeedThread extends Thread {
        private static final MinecraftClient mc = MinecraftClient.getInstance();

        public static boolean enabled = false;
        public static float MULTIPLIER = 1.6f;
        private static double targetSpeed = 0.32;

        private static final double BASE_SPEED = 0.2;
        private static final double MAX_SPEED = 1.5;

        private boolean lastKeyState = false;
        private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;

        public static void updateTargetSpeed() {
            targetSpeed = Math.min(MAX_SPEED, BASE_SPEED * MULTIPLIER);
            LOGGER.info("SpeedMod скорость изменена: " + String.format("%.1f", MULTIPLIER) + "x (" + String.format("%.3f", targetSpeed) + " б/т)");
        }

        @Override
        public void run() {
            updateTargetSpeed();
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                try {
                    if (mc == null || mc.getWindow() == null) continue;

                    long window = mc.getWindow().getHandle();

                    // Открытие GUI по R
                    boolean currentKeyState = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
                    if (currentKeyState && !lastKeyState) {
                        mc.execute(SpeedMenuScreen::open);
                    }
                    lastKeyState = currentKeyState;

                    if (!enabled) continue;
                    if (mc.player == null || mc.world == null) continue;

                    ClientPlayerEntity player = mc.player;
                    if (player == null) continue;

                    BlockPos feetPos = player.getBlockPos();
                    BlockState state = mc.world.getBlockState(feetPos);
                    Block block = state.getBlock();

                    if (isSpecialBlock(block)) {
                        applySpeed(player);
                    }
                } catch (Exception ignored) {}
            }
        }

        private boolean isSpecialBlock(Block block) {
            try {
                return block instanceof SnowBlock
                        || block instanceof SnowyBlock
                        || block instanceof SlabBlock
                        || block instanceof StairsBlock
                        || block instanceof TrapdoorBlock
                        || block instanceof FenceBlock
                        || block instanceof FenceGateBlock
                        || block instanceof FlowerPotBlock
                        || block instanceof LightBlock
                        || block instanceof PressurePlateBlock
                        || block instanceof LeverBlock
                        || block instanceof ButtonBlock
                        || block instanceof RailBlock
                        || block instanceof VineBlock
                        || block instanceof LadderBlock
                        || block instanceof ScaffoldingBlock
                        || block instanceof SweetBerryBushBlock;
            } catch (Exception e) {
                return false;
            }
        }

        private void applySpeed(ClientPlayerEntity player) {
            try {
                float yaw = player.getYaw();

                if (mc.options == null || mc.options.forwardKey == null) return;

                if (mc.options.forwardKey.isPressed()) {
                    double forward = 1.0;
                    double strafe = 0.0;

                    if (mc.options.leftKey != null && mc.options.leftKey.isPressed()) strafe -= 1.0;
                    if (mc.options.rightKey != null && mc.options.rightKey.isPressed()) strafe += 1.0;

                    double len = Math.sqrt(forward * forward + strafe * strafe);
                    if (len > 0) {
                        forward /= len;
                        strafe /= len;
                    }

                    double rad = Math.toRadians(yaw);
                    double dx = forward * -Math.sin(rad) + strafe * Math.cos(rad);
                    double dz = forward * Math.cos(rad) + strafe * Math.sin(rad);

                    Vec3d currentVel = player.getVelocity();
                    if (currentVel == null) return;

                    double currentSpeed = Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);

                    if (currentSpeed < targetSpeed) {
                        double newSpeed = Math.min(targetSpeed, currentSpeed + 0.02);
                        player.setVelocity(dx * newSpeed, player.getVelocity().y, dz * newSpeed);
                    } else {
                        player.setVelocity(dx * targetSpeed, player.getVelocity().y, dz * targetSpeed);
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
