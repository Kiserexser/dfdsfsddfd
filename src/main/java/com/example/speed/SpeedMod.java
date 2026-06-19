package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void onInitialize() {
        LOGGER.info("NoSlow (always ON) loaded.");

        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    if (mc != null && mc.player != null && mc.world != null) {
                        applyNoSlow();
                    }
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("NoSlow error", e);
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void applyNoSlow() {
        if (mc.player == null || mc.world == null) return;

        // Проверяем, использует ли игрок предмет
        if (!mc.player.isUsingItem()) return;

        // Получаем ввод
        float forward = mc.player.forwardSpeed;
        float strafe = mc.player.sidewaysSpeed;
        if (forward == 0 && strafe == 0) return;

        float yaw = mc.player.getYaw() * 0.017453292F; // радианы

        // Определяем базовую скорость ходьбы или спринта
        float speed = mc.player.isSprinting() ? 0.3f : 0.23f;

        // Вычисляем движение
        double x = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * speed;
        double z = ( Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * speed;

        // Сохраняем вертикальную скорость
        double y = mc.player.getVelocity().y;

        // Применяем скорость
        mc.player.setVelocity(x, y, z);
    }
}
