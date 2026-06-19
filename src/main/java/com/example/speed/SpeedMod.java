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

    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("Always Speed loaded.");

        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    if (mc != null && mc.player != null && mc.world != null) {
                        tickCounter++;
                        if (tickCounter % 2 == 0) {
                            mc.execute(() -> sendPackets());
                        }
                        mc.execute(() -> applySpeed());
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

    private void sendPackets() {
        if (mc.player == null || mc.player.networkHandler == null) return;

        // Отправляем пакет позиции с onGround = false (обход)
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                false, // onGround
                false  // horizontalCollision
        ));
        // Отправляем START_FALL_FLYING для активации элитр (обход)
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }

    private void applySpeed() {
        if (mc.player == null) return;

        double grim = 0.03;
        if (mc.player.isOnGround()) {
            grim *= 2.8500699;
        } else {
            grim *= 1.0200699;
        }

        float yaw = mc.player.getYaw() + 90f;
        double rad = Math.toRadians(yaw);

        double mx = grim * Math.cos(rad);
        double mz = grim * Math.sin(rad);

        mc.player.setVelocity(mc.player.getVelocity().x + mx, mc.player.getVelocity().y, mc.player.getVelocity().z + mz);

        if (!mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y - 0.050699, mc.player.getVelocity().z);
        }
    }
}
