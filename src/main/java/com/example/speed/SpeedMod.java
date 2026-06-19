package com.example.speed;

import Atheryx.client.event.EventHandler;
import Atheryx.client.event.list.player.MotionEvent;
import Atheryx.client.event.list.player.NoSlowEvent;
import Atheryx.client.module.Category;
import Atheryx.client.module.Module;
import Atheryx.client.module.ModuleInfo;
import Atheryx.client.module.setting.impl.ModeSetting;
import Atheryx.client.util.rotation.RotationUtil;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

@ModuleInfo(name = "NoSlow",
description = "Убирает замедление",
category = Category.MOVEMENT)
public class SpeedMod extends Module {
    private static final ModeSetting MODE = new ModeSetting("Режим", "Ванила", "Grim");

    public SpeedMod() {
        addSetting(MODE);
        setState(true); // всегда включён
    }

    @Override
    public boolean isEnabled() {
        return true; // никогда не выключается
    }

    @Override
    public void onDisable() {
        // игнорируем попытки выключения
        setState(true);
    }

    @EventHandler
    public void onSlow(NoSlowEvent event) {
        switch (MODE.getValue()) {
            case "Grim" -> {
                if (mc.player.isUsingItem()) {
                    event.cancel();
                }
            }
            case "Ванила" -> {
                if (mc.player.isUsingItem()) {
                    event.cancel();
                }
            }
        }
    }

    @EventHandler
    public void onMotion(MotionEvent event) {
        if (!mc.player.isUsingItem() || !event.isPre() || !MODE.is("Grim")) {
            return;
        }

        Hand hand = mc.player.getActiveHand();

        if (hand == Hand.MAIN_HAND) {
            mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(
                    Hand.OFF_HAND,
                    0,
                    RotationUtil.getServerYaw(),
                    RotationUtil.getServerPitch()
            ));
        } else if (hand == Hand.OFF_HAND) {
            mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(
                    Hand.MAIN_HAND,
                    0,
                    RotationUtil.getServerYaw(),
                    RotationUtil.getServerPitch()
            ));
        }
    }
}
