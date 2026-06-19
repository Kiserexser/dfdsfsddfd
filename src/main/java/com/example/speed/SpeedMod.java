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

        // Проверяем, использует ли игрок предмет (едят, лук, блоки и т.д.)
        if (!mc.player.isUsingItem()) return;

        // Получаем ввод игрока
        double forward = mc.player.forwardSpeed;
        double strafe = mc.player.sidewaysSpeed;
        if (forward == 0 && strafe == 0) {
            // Если не двигается, не трогаем скорость
            return;
        }

        float yaw = mc.player.getYaw() * 0.017453292F; // радианы

        // Вычисляем нормальную скорость (как при ходьбе без замедления)
        double x = (-Math.sin(yaw) * forward) + (Math.cos(yaw) * strafe);
        double z = ( Math.cos(yaw) * forward) + (Math.sin(yaw) * strafe);

        // Множитель скорости при обычной ходьбе ~0.23, но мы используем 0.23 * 1.0 (норма)
        // Чтобы полностью убрать замедление, умножаем на 1.0 (без дополнительного множителя)
        // Однако в ваниле при использовании предмета скорость равна 0.2 от нормальной,
        // поэтому мы просто подставляем нормальную скорость.
        double speed = 0.23; // базовая скорость ходьбы (без спринта)
        // Если игрок бежит, учитываем спринт (проверяем спринт)
        if (mc.player.isSprinting()) {
            speed = 0.3; // спринт
        }
        // Но лучше вычислить скорость из текущего состояния, чтобы сохранить эффекты
        // Проще: мы просто заменяем скорость на ту, которая была бы без замедления.
        // Для точности можно сохранить нормальный множитель, но для простоты используем константу.
        // Однако это не совсем точно, но для NoSlow достаточно.

        // Более точный способ: взять множитель из атрибутов
        double baseSpeed = mc.player.getMovementSpeed(); // текущий множитель (с учётом замедления)
        // Но мы хотим убрать замедление, поэтому мы можем умножить на обратную величину
        // Но проще вычислить скорость напрямую:
        // Скорость = (база) * множитель, где множитель зависит от предмета.
        // Мы просто зададим скорость, как если бы предмета не было.
        // Вместо этого мы используем стандартные множители:
        double moveSpeed = 0.23;
        if (mc.player.isSprinting()) moveSpeed = 0.3;
        // Учитываем возможные эффекты (например, скорость от зелий) – можно добавить, но для простоты опустим.

        // Умножаем на moveSpeed
        x *= moveSpeed;
        z *= moveSpeed;

        // Вертикальная скорость остаётся без изменений
        double y = mc.player.getVelocity().y;

        // Применяем скорость
        mc.player.setVelocity(x, y, z);
    }
}
