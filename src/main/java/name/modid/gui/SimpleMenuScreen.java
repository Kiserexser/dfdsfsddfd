package name.modid.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionScreen;
import net.minecraft.network.chat.Component;

public class SimpleMenuScreen extends Screen {

    public SimpleMenuScreen() {
        super(Component.literal("Simple Menu"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Singleplayer"), button ->
                        Minecraft.getInstance().setScreen(new WorldSelectionScreen(this)))
                .pos(centerX - 75, centerY - 50)
                .size(150, 25)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Multiplayer"), button ->
                        Minecraft.getInstance().setScreen(new JoinMultiplayerScreen(this)))
                .pos(centerX - 75, centerY)
                .size(150, 25)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Options"), button ->
                        Minecraft.getInstance().setScreen(new OptionsScreen(this, Minecraft.getInstance().options)))
                .pos(centerX - 75, centerY + 50)
                .size(150, 25)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Exit"), button ->
                        Minecraft.getInstance().stop())
                .pos(centerX - 75, centerY + 100)
                .size(150, 25)
                .build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Простая заливка фона
        context.fill(0, 0, this.width, this.height, 0xFF1A2A1A);

        Font font = Minecraft.getInstance().font;
        String title = "Simple Menu";
        int titleWidth = font.width(title);
        context.drawString(font, title, (this.width - titleWidth) / 2, 30, 0xFFFFFFFF, false);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new TitleScreen());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
