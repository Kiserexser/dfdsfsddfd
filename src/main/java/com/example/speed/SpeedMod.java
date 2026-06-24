package com.yourname.speedmod;

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
        LOGGER.info("SpeedMod (1.6x спринта) загружен! Нажми R для переключения.");
        new SpeedThread().start();
    }

    private static class SpeedThread extends Thread {
        private static final MinecraftClient mc = MinecraftClient.getInstance();

        private boolean enabled = false;
        private boolean lastKeyState = false;

        private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;

        // Настройки скорости
        private static final double BASE_SPEED = 0.2;
        private static final double MULTIPLIER = 1.6;
        private static final double MAX_SPEED = 1.2;

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    break;
                }

                mc.execute(() -> {
                    if (mc.getWindow() == null || mc.player == null || mc.world == null) return;

                    long window = mc.getWindow().getHandle();

                    boolean currentKeyState = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
                    if (currentKeyState && !lastKeyState) {
                        enabled = !enabled;
                    }
                    lastKeyState = currentKeyState;

                    if (!enabled) return;

                    ClientPlayerEntity player = mc.player;
                    if (player == null) return;

                    BlockPos feetPos = player.getBlockPos();
                    BlockState state = mc.world.getBlockState(feetPos);
                    Block block = state.getBlock();

                    if (isSpecialBlock(block, state)) {
                        applySpeed(player);
                    }
                });
            }
        }

        private boolean isSpecialBlock(Block block, BlockState state) {
            return block instanceof SnowBlock
                    || block instanceof SnowyBlock
                    || block instanceof SlabBlock
                    || block instanceof StairBlock
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
        }

        private void applySpeed(ClientPlayerEntity player) {
            float yaw = player.getYaw();

            if (mc.options.forwardKey.isPressed()) {
                double forward = 1.0;
                double strafe = 0.0;
                if (mc.options.leftKey.isPressed()) strafe -= 1.0;
                if (mc.options.rightKey.isPressed()) strafe += 1.0;

                double len = Math.sqrt(forward * forward + strafe * strafe);
                if (len > 0) {
                    forward /= len;
                    strafe /= len;
                }

                double rad = Math.toRadians(yaw);
                double dx = forward * -Math.sin(rad) + strafe * Math.cos(rad);
                double dz = forward * Math.cos(rad) + strafe * Math.sin(rad);

                Vec3d currentVel = player.getVelocity();
                double currentSpeed = Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);
                double targetSpeed = Math.min(MAX_SPEED, BASE_SPEED * MULTIPLIER);

                if (currentSpeed < targetSpeed) {
                    double newSpeed = Math.min(targetSpeed, currentSpeed + 0.02);
                    player.setVelocity(dx * newSpeed, player.getVelocity().y, dz * newSpeed);
                } else {
                    player.setVelocity(dx * targetSpeed, player.getVelocity().y, dz * targetSpeed);
                }
            }
        }
    }
}
