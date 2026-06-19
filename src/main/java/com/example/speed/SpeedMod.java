package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void onInitialize() {
        LOGGER.info("NoWeb (always ON) loaded. Speed x4 in webs and sweet berries.");

        // Подписываемся на тики через отдельный поток (для простоты)
        // Но лучше использовать ClientTickEvents, но без Fabric API мы сделаем через поток
        // Для простоты и надёжности используем отдельный поток, который будет применять скорость каждый тик.
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    if (mc != null && mc.player != null && mc.world != null) {
                        applySpeed();
                    }
                    Thread.sleep(50); // ~20 тиков в секунду (достаточно)
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

        // Проверяем, находится ли игрок в паутине или сладких ягодах
        BlockPos pos = mc.player.getBlockPos();
        boolean inWeb = mc.world.getBlockState(pos).isOf(Blocks.COBWEB) ||
                        mc.world.getBlockState(pos.up()).isOf(Blocks.COBWEB) ||
                        mc.world.getBlockState(pos.down()).isOf(Blocks.COBWEB);
        boolean inBerries = mc.world.getBlockState(pos).isOf(Blocks.SWEET_BERRY_BUSH) ||
                            mc.world.getBlockState(pos.up()).isOf(Blocks.SWEET_BERRY_BUSH) ||
                            mc.world.getBlockState(pos.down()).isOf(Blocks.SWEET_BERRY_BUSH);

        if (!inWeb && !inBerries) return;

        // Получаем ввод игрока
        double forward = mc.player.forwardSpeed;
        double strafe = mc.player.sidewaysSpeed;
        double motionY = mc.player.getVelocity().y;

        // Вертикаль (прыжок/присед)
        if (mc.options.jumpKey.isPressed()) {
            motionY = 0.42; // стандартный прыжок
        } else if (mc.options.sneakKey.isPressed()) {
            motionY = -0.08; // приседание
        }

        // Вычисляем горизонтальную скорость с множителем 4
        float yaw = mc.player.getYaw() * 0.017453292F; // радианы
        float multiplier = 4.0f;
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

        // Применяем скорость
        mc.player.setVelocity(motionX, motionY, motionZ);

        // Сбрасываем fallDistance, чтобы не получать урон
        mc.player.fallDistance = 0;
    }
}
