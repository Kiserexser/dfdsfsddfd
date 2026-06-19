package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Настройки (можно менять)
    private static final int TICKS_TO_RELEASE = 3; // через сколько тиков отпускать тридент (по умолчанию 3)
    private static final boolean ALLOW_NO_WATER = true; // разрешать использовать без воды (обход)
    private static final boolean INSTANT = true; // мгновенный возврат (упрощённо – просто отпускаем)

    private boolean wasUsingTrident = false;
    private int useTime = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("Trident Fly loaded (always ON).");

        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    if (mc != null && mc.player != null && mc.world != null) {
                        mc.execute(() -> handleTrident());
                    }
                    Thread.sleep(50); // ~1 тик
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("Trident error", e);
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void handleTrident() {
        if (mc.player == null) return;

        boolean isUsingTrident = mc.player.isUsingItem() && mc.player.getMainHandStack().getItem() == Items.TRIDENT;

        if (isUsingTrident && !wasUsingTrident) {
            wasUsingTrident = true;
            useTime = 0;
        } else if (wasUsingTrident && !isUsingTrident) {
            wasUsingTrident = false;
            useTime = 0;
        } else if (isUsingTrident) {
            useTime++;
            if (useTime >= TICKS_TO_RELEASE) {
                if (mc.player.networkHandler != null) {
                    // Отправляем пакет отпускания тридента
                    mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                            BlockPos.ORIGIN,
                            Direction.DOWN
                    ));
                    mc.player.stopUsingItem();
                    wasUsingTrident = false;
                    useTime = 0;
                }
            }
        }
    }
}
