package com.example.speed.mixin;

import com.example.speed.SpeedMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class HUDMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(DrawContext drawContext, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        int x = 10;
        int y = 10;

        // Водяной знак
        drawContext.drawText(mc.textRenderer, "SpeedMod", x, y, 0xFFFFFF, true);
        y += 15;

        // FPS
        drawContext.drawText(mc.textRenderer, mc.getCurrentFps() + " fps", x, y, 0xAAAAAA, true);
        y += 15;

        // Пинг
        if (mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            int ping = entry != null ? entry.getLatency() : 0;
            drawContext.drawText(mc.textRenderer, ping + " ms", x, y, 0xAAAAAA, true);
            y += 15;
        }

        // Информация о цели
        if (SpeedMod.isEnabled() && SpeedMod.getLockedTarget() != null) {
            LivingEntity target = SpeedMod.getLockedTarget();
            String name = target.getName().getString();
            int health = (int) target.getHealth();
            drawContext.drawText(mc.textRenderer, "Target: " + name + " | HP: " + health, x, y, 0xFF5555, true);
        } else {
            drawContext.drawText(mc.textRenderer, "No target", x, y, 0x555555, true);
        }
    }
}
