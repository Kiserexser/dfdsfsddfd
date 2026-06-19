package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void onInitialize() {
        LOGGER.info("NoWeb (always ON) loaded. Speed x1.7 in webs and sweet berries.");

        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    if (mc != null && mc.player != null && mc.world != null) {
                        applySpeed();
                    }
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("NoWeb error", e);
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void applySpeed() {
        if (mc.player == null || mc.world == null) return;

        BlockPos pos = mc.player.getBlockPos();
        boolean inWeb = mc.world.getBlockState(pos).isOf(Blocks.COBWEB) ||
                        mc.world.getBlockState(pos.up()).isOf(Blocks.COBWEB) ||
                        mc.world.getBlockState(pos.down()).isOf(Blocks.COBWEB);
        boolean inBerries = mc.world.getBlockState(pos).isOf(Blocks.SWEET_BERRY_BUSH) ||
                            mc.world.getBlockState(pos.up()).isOf(Blocks.SWEET_BERRY_BUSH) ||
                            mc.world.getBlockState(pos.down()).isOf(Blocks.SWEET_BERRY_BUSH);

        if (!inWeb && !inBerries) return;

        double forward = mc.player.forwardSpeed;
        double strafe = mc.player.sidewaysSpeed;
        double motionY = mc.player.getVelocity().y;

        if (mc.options.jumpKey.isPressed()) {
            motionY = 0.42;
        } else if (mc.options.sneakKey.isPressed()) {
            motionY = -0.08;
        }

        float yaw = mc.player.getYaw() * 0.017453292F;
        float multiplier = 1.7f; // теперь 1.7
        float f = (float) forward * multiplier;
        float s = (float) strafe * multiplier;

        double motionX, motionZ;
        if (f != 0 || s != 0) {
            motionX = -MathHelper.sin(yaw) * f + MathHelper.cos(yaw) * s;
            motionZ =  MathHelper.cos(yaw) * f + MathHelper.sin(yaw) * s;
        } else {
            motionX = 0;
            motionZ = 0;
        }

        mc.player.setVelocity(motionX, motionY, motionZ);
        mc.player.fallDistance = 0;
    }
}
