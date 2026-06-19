package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void onInitialize() {
        LOGGER.info("Always Speed loaded.");

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
                    LOGGER.error("Speed error", e);
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void applySpeed() {
        if (mc.player == null || mc.world == null) return;

        // Отправляем пакеты для обхода (как в оригинале)
        if (mc.player.networkHandler != null) {
            // ServerboundMovePlayerPacket.StatusOnly(false, false)
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.StatusOnly(false));
            // ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }

        // Основная формула скорости
        double grim = 0.03;
        if (mc.player.isOnGround()) {
            grim *= 2.8500699;
        } else {
            grim *= 1.0200699;
        }

        // Направление движения (используем yaw игрока)
        float yaw = mc.player.getYaw() + 90f; // как в коде: (getdir() + 90f)
        double rad = Math.toRadians(yaw);

        double mx = grim * Math.cos(rad);
        double mz = grim * Math.sin(rad);

        // Добавляем к текущей скорости
        mc.player.setVelocity(mc.player.getVelocity().x + mx, mc.player.getVelocity().y, mc.player.getVelocity().z + mz);

        // Если не на земле, добавляем отрицательную вертикальную скорость
        if (!mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y - 0.050699, mc.player.getVelocity().z);
        }
    }
}
