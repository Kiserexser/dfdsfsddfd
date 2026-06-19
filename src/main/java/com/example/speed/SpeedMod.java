package com.example.speed;

import dev.relictdlc.module.Module;
import dev.relictdlc.module.ModuleCategory;
import dev.relictdlc.setting.NumberSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

public class SpeedMod extends Module {

    public final NumberSetting multiplier = addSetting(new NumberSetting("Множитель", "Сила взлета", 2.0, 1.0, 5.0, 0.1));
    public final NumberSetting maxGrimVelocity = addSetting(new NumberSetting("Скорость", "Максимальная скорость по Y", 0.75, 0.5, 1.5, 0.05));

    private boolean boosted = false;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public SpeedMod() {
        super("HighJump", "Буст от заряда на слизи", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
        setState(true); // всегда включён
    }

    @Override
    public boolean isEnabled() {
        return true; // всегда активен
    }

    @Override
    public void onDisable() {
        // не даём отключиться
        boosted = false;
        setState(true); // если кто-то попытается выключить, принудительно включаем обратно
    }

    @dev.relictdlc.event.EventTarget
    public void onUpdate(dev.relictdlc.event.events.EventUpdate event) {
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
            double calculatedVelocity = yVel * multiplier.getValue();
            double safeVelocity = Math.min(calculatedVelocity, maxGrimVelocity.getValue());
            mc.player.setVelocity(mc.player.getVelocity().x, safeVelocity, mc.player.getVelocity().z);
            boosted = true;
        }

        if (mc.player.isOnGround() || yVel <= 0) {
            boosted = false;
        }
    }
}
