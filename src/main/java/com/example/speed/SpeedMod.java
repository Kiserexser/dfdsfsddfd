package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private boolean boosted = false;
    private double multiplier = 10.0;          // достаточно большой, чтобы превысить лимит
    private double maxGrimVelocity = 0.9;      // даёт высоту ~5 блоков

    @Override
    public void onInitialize() {
        LOGGER.info("HighJump (always ON) loaded. Jump height ~5 blocks.");

        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    if (mc != null && mc.player != null && mc.world != null) {
                        applyHighJump();
                    }
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("HighJump error", e);
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void applyHighJump() {
        if (mc.player == null || mc.world == null) return;

        double yVel = mc.player.getVelocity().y;

        Box box = mc.player.getBoundingBox().offset(0, -0.1, 0);
        boolean isSlime = false;

        BlockPos minPos = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
        BlockPos maxPos = BlockPos.ofFloored(box.maxX, box.minY, box.maxZ);

        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                if (mc.world.getBlockState(new BlockPos(x, minPos.getY(), z)).getBlock() == Blocks.SLIME_BLOCK) {
                    isSlime = true;
                    break;
                }
            }
            if (isSlime) break;
        }

        if (isSlime && yVel > 0.05 && !boosted) {
            double calculatedVelocity = yVel * multiplier;
            double safeVelocity = Math.min(calculatedVelocity, maxGrimVelocity);
            mc.player.setVelocity(mc.player.getVelocity().x, safeVelocity, mc.player.getVelocity().z);
            boosted = true;
        }

        if (mc.player.isOnGround() || yVel <= 0) {
            boosted = false;
        }
    }
}
