package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod загружен! Нажми R для переключения.");
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            new SpeedThread().start();
        }).start();
    }

    private static class SpeedThread extends Thread {
        private static final MinecraftClient mc = MinecraftClient.getInstance();

        private boolean enabled = false;
        private boolean lastKeyState = false;
        private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;

        private static final double BASE_SPEED = 0.2;
        private static final double MULTIPLIER = 1.6;
        private static final double MAX_SPEED = 1.2;

        @Override
        public void run() {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                try {
                    if (mc == null || mc.getWindow() == null || mc.player == null || mc.world == null) continue;

                    long window = mc.getWindow().getHandle();
                    boolean currentKeyState = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;

                    if (currentKeyState && !lastKeyState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "SpeedMod ВКЛЮЧЁН" : "SpeedMod ВЫКЛЮЧЁН");
                    }
                    lastKeyState = currentKeyState;
                    if (!enabled) continue;

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
                        || block instanceof SweetBerryBushBlock
                        || block instanceof CaveVinesBlock
                        || block instanceof CaveVinesPlantBlock;
            } catch (Exception e) {
                return false;
            }
        }

        private void applySpeed(ClientPlayerEntity player) {
            try {
                float yaw = player.getYaw();

                if (mc.options == null) return;
                if (mc.options.forwardKey == null) return;

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
                    double targetSpeed = Math.min(MAX_SPEED, BASE_SPEED * MULTIPLIER);

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
