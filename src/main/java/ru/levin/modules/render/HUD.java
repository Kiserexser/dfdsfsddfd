package ru.levin.modules.render;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class HUD {
    private static boolean enabled = true;
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean showWatermark = true;
    private static boolean showTargetHUD = true;
    private static boolean showKeyBinds = true;
    private static boolean showArmorHUD = true;
    private static boolean blur = true;

    // Перетаскиваемые позиции (простые координаты)
    private static float watermarkX = 10, watermarkY = 10;
    private static float targetX = 10, targetY = 40;
    private static float keybindX = 10, keybindY = 100;
    private static float armorX = 400, armorY = 450;

    private static boolean dragging = false;
    private static String dragElement = "";
    private static float dragOffsetX = 0, dragOffsetY = 0;

    public static void init() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!enabled || mc.player == null || mc.world == null) return;

            // Обработка перетаскивания (зажатие левой кнопки мыши)
            if (dragging) {
                double mouseX = mc.mouse.getX() / mc.getWindow().getScaleFactor();
                double mouseY = mc.mouse.getY() / mc.getWindow().getScaleFactor();
                switch (dragElement) {
                    case "watermark":
                        watermarkX = (float) (mouseX - dragOffsetX);
                        watermarkY = (float) (mouseY - dragOffsetY);
                        break;
                    case "target":
                        targetX = (float) (mouseX - dragOffsetX);
                        targetY = (float) (mouseY - dragOffsetY);
                        break;
                    case "keybind":
                        keybindX = (float) (mouseX - dragOffsetX);
                        keybindY = (float) (mouseY - dragOffsetY);
                        break;
                    case "armor":
                        armorX = (float) (mouseX - dragOffsetX);
                        armorY = (float) (mouseY - dragOffsetY);
                        break;
                }
            }

            if (showWatermark) renderWatermark(drawContext);
            if (showTargetHUD) renderTargetHUD(drawContext);
            if (showKeyBinds) renderKeybinds(drawContext);
            if (showArmorHUD) renderArmor(drawContext);
        });

        // Обработка кликов мыши для начала/конца перетаскивания
        // (это упрощённо, можно доработать)
    }

    // === WATERMARK ===
    private static void renderWatermark(DrawContext context) {
        String text = "sqvirtik | " + mc.getCurrentFps() + " fps | " + getPing() + " ms";
        float width = mc.textRenderer.getWidth(text) + 20;
        float x = watermarkX;
        float y = watermarkY;

        drawPanel(context, x, y, width, 18);
        context.drawText(mc.textRenderer, text, (int) x + 8, (int) y + 5, 0xFFFFFFFF, true);
    }

    // === TARGET HUD ===
    private static void renderTargetHUD(DrawContext context) {
        LivingEntity target = getTarget();
        if (target == null) return;

        float x = targetX;
        float y = targetY;
        float width = 135;
        float height = 42;

        drawPanel(context, x, y, width, height);

        // Рисуем голову (упрощённо – просто квадрат)
        context.drawText(mc.textRenderer, "Head", (int) x + 5, (int) y + 5, 0xFFFFFFFF, true);

        String name = target.getName().getString();
        if (name.length() > 12) name = name.substring(0, 12);
        context.drawText(mc.textRenderer, name, (int) x + 42, (int) y + 7, 0xFFFFFFFF, true);

        float health = MathHelper.clamp(target.getHealth() / target.getMaxHealth(), 0, 1);
        float healthAnim = health; // упрощённо, без анимации

        // Фон здоровья
        context.fill((int) x + 42, (int) y + 27, (int) x + 122, (int) y + 32, 0xFF1E1E1E);
        // Заполнение здоровья
        context.fill((int) x + 42, (int) y + 27, (int) (x + 42 + 80 * healthAnim), (int) y + 32, Color.GREEN.getRGB());

        context.drawText(mc.textRenderer, (int) target.getHealth() + " HP", (int) x + 42, (int) y + 17, 0xFFC8C8C8, true);
    }

    // === KEYBINDS ===
    private static void renderKeybinds(DrawContext context) {
        // В упрощённой версии просто показываем пример
        String[] binds = {"Module1: R", "Module2: V"};
        float x = keybindX;
        float y = keybindY;
        float width = 105;
        float offset = 0;

        drawPanel(context, x, y, width, 18);
        context.drawText(mc.textRenderer, "Keybinds", (int) x + 8, (int) y + 5, 0xFFFFFFFF, true);
        y += 20;

        for (String bind : binds) {
            drawPanel(context, x, y + offset, width, 16);
            context.drawText(mc.textRenderer, bind, (int) x + 6, (int) y + 4 + offset, 0xFFFFFFFF, true);
            offset += 18;
        }
    }

    // === ARMOR HUD ===
    private static void renderArmor(DrawContext context) {
        float x = armorX;
        float y = armorY;
        drawPanel(context, x, y, 82, 20);

        int offset = 0;
        for (int i = 3; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().armor.get(i);
            if (stack.isEmpty()) continue;
            context.drawItem(stack, (int) x + 4 + offset, (int) y + 2);
            offset += 20;
        }
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===
    private static void drawPanel(DrawContext context, float x, float y, float width, float height) {
        if (blur) {
            // Упрощённо – просто заливка с прозрачностью
            context.fill((int) x, (int) y, (int) (x + width), (int) (y + height), 0xAA0F0F0F);
        } else {
            context.fill((int) x, (int) y, (int) (x + width), (int) (y + height), 0xAA0F0F0F);
        }
    }

    private static int getPing() {
        // Заглушка – вернуть 0 или получить из мультиплеера
        return 0;
    }

    private static LivingEntity getTarget() {
        // Упрощённо – можно вернуть первого моба в радиусе или null
        // Для реальной работы нужно интегрировать с вашей системой атаки
        return null;
    }

    public static void toggle() {
        enabled = !enabled;
    }
}
