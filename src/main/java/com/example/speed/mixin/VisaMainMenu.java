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
        MinecraftClient.getInstance().setScreen(this);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int y = this.height / 2 - 60;

        this.addDrawableChild(new PinkButton(centerX - 100, y, 200, 20,
                Text.literal("Одиночная игра").formatted(Formatting.WHITE),
                button -> MinecraftClient.getInstance().setScreen(
                        new net.minecraft.client.gui.screen.world.SelectWorldScreen(this))));

        y += 24;
        this.addDrawableChild(new PinkButton(centerX - 100, y, 200, 20,
                Text.literal("Мультиплеер").formatted(Formatting.WHITE),
                button -> MinecraftClient.getInstance().setScreen(
                        new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(this))));

        y += 24;
        this.addDrawableChild(new PinkButton(centerX - 100, y, 200, 20,
                Text.literal("Настройки").formatted(Formatting.WHITE),
                button -> MinecraftClient.getInstance().setScreen(
                        new net.minecraft.client.gui.screen.option.OptionsScreen(this,
                                MinecraftClient.getInstance().options))));

        y += 24;
        this.addDrawableChild(new PinkButton(centerX - 100, y, 200, 20,
                Text.literal("Выйти").formatted(Formatting.WHITE),
                button -> MinecraftClient.getInstance().stop()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        Text title = Text.literal("visar").formatted(Formatting.BOLD, Formatting.WHITE);
        context.drawCenteredTextWithShadow(this.textRenderer, title, this.width / 2, 40, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    private static class PinkButton extends ButtonWidget {
        private static final int BG_COLOR = 0xFFFFB6C1;
        private static final int BORDER_COLOR = 0xFFFF69B4;

        public PinkButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(this.getX(), this.getY(),
                    this.getX() + this.getWidth(), this.getY() + this.getHeight(),
                    BG_COLOR);
            context.drawBorder(this.getX(), this.getY(),
                    this.getWidth(), this.getHeight(),
                    BORDER_COLOR);
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, this.getMessage(),
                    this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2,
                    0x333333);
        }
    }
}
