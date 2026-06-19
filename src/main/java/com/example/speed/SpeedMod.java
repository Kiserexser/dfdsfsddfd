package com.example.speed;

import dev.relictdlc.module.Module;
import dev.relictdlc.module.ModuleCategory;
import dev.relictdlc.setting.NumberSetting;
import dev.relictdlc.event.events.EventUpdate;
import dev.relictdlc.event.EventTarget;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class SpeedMod extends Module {

    // ===== Настройка множителя (можно менять через GUI) =====
    private final NumberSetting multiplier = addSetting(new NumberSetting("Множитель", "Скорость в паутине/ягодах", 4.0, 1.0, 10.0, 0.1));

    public SpeedMod() {
        super("NoWeb", "Ускорение в паутине и сладких ягодах", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_K);
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка: стоит ли игрок в паутине или кусте ягод
        if (!isInWebOrBerries()) return;

        double forward = mc.player.forwardSpeed;
        double strafe = mc.player.sidewaysSpeed;
        if (forward == 0 && strafe == 0) return;

        float yaw = mc.player.getYaw() * 0.017453292F; // радианы

        // Расчёт скорости с учётом направления
        double x = (-Math.sin(yaw) * forward) + (Math.cos(yaw) * strafe);
        double z = ( Math.cos(yaw) * forward) + (Math.sin(yaw) * strafe);

        float mult = multiplier.getValue().floatValue();
        x *= 0.23 * mult;
        z *= 0.23 * mult;

        double y = mc.player.getVelocity().y;

        if (mc.options.jumpKey.isPressed()) {
            y += 0.04 * mult;
        }
        if (mc.options.sneakKey.isPressed()) {
            y -= 0.04 * mult;
        }

        // Ограничение максимальной скорости (чтобы не слишком быстро)
        double maxSpeed = 0.53 * mult;
        double currentSpeed = Math.sqrt(x * x + z * z);
        if (currentSpeed > maxSpeed) {
            double scale = maxSpeed / currentSpeed;
            x *= scale;
            z *= scale;
        }

        mc.player.setVelocity(x, y, z);

        // Коррекция при столкновении
        if (mc.player.horizontalCollision || mc.player.verticalCollision) {
            Vec3d vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x, vel.y, vel.z);
        }
    }

    private boolean isInWebOrBerries() {
        BlockPos pos = mc.player.getBlockPos();
        var state = mc.world.getBlockState(pos);
        var stateUp = mc.world.getBlockState(pos.up());
        var stateDown = mc.world.getBlockState(pos.down());

        return state.isOf(Blocks.COBWEB) || stateUp.isOf(Blocks.COBWEB) || stateDown.isOf(Blocks.COBWEB) ||
               state.isOf(Blocks.SWEET_BERRY_BUSH) || stateUp.isOf(Blocks.SWEET_BERRY_BUSH) || stateDown.isOf(Blocks.SWEET_BERRY_BUSH);
    }
}
