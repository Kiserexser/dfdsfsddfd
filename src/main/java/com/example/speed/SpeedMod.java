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

    // Теперь ждём 10 тиков (≈0.5 сек), чтобы тридент успел натянуться
    private static final int TICKS_TO_RELEASE = 10;
    private boolean wasUsingTrident = false;
    private int useTime = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("Trident Fly (fixed) loaded. Hold right-click with trident.");

        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    if (mc != null && mc.player != null && mc.world != null) {
                        mc.execute(() -> handleTrident());
                    }
                    Thread.sleep(50);
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
            // Начали использование
            wasUsingTrident = true;
            useTime = 0;
        } else if (wasUsingTrident && !isUsingTrident) {
            // Перестали использовать
            wasUsingTrident = false;
            useTime = 0;
        } else if (isUsingTrident) {
            useTime++;
            if (useTime >= TICKS_TO_RELEASE) {
                // Отпускаем тридент
                if (mc.player.networkHandler != null) {
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
