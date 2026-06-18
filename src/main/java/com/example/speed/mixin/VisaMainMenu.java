package com.example.speed.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class VisaMainMenu extends Screen {

    protected VisaMainMenu() {
        super(Text.literal("Visa Client"));
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        ci.cancel();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this);
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int y = this.height / 2 - 60;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Одиночная игра").formatted(Formatting.WHITE),
                button -> MinecraftClient.getInstance().setScreen(
                        new net.minecraft.client.gui.screen.world.SelectWorldScreen(this)))
                .dimensions(centerX - 100, y, 200, 20).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Мультиплеер").formatted(Formatting.WHITE),
                button -> MinecraftClient.getInstance().setScreen(
                        new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(this)))
                .dimensions(centerX - 100, y, 200, 20).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Настройки").formatted(Formatting.WHITE),
                button -> MinecraftClient.getInstance().setScreen(
                        new net.minecraft.client.gui.screen.option.OptionsScreen(this,
                                MinecraftClient.getInstance().options)))
                .dimensions(centerX - 100, y, 200, 20).build());

        y += 24;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Выйти").formatted(Formatting.WHITE),
                button -> MinecraftClient.getInstance().stop())
                .dimensions(centerX - 100, y, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Отрисовка фона (стандартный тёмный фон, чтобы не было белого экрана)
        this.renderBackground(context, mouseX, mouseY, delta);
        // Заголовок
        Text title = Text.literal("visar").formatted(Formatting.BOLD, Formatting.WHITE);
        context.drawCenteredTextWithShadow(this.textRenderer, title, this.width / 2, 40, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    // Переопределяем фон на белый (используем метод renderBackground)
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Заливаем белым
        context.fill(0, 0, this.width, this.height, 0xFFFFFFFF);
    }
}
