package im.expensive.functions.impl.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.GlStateManager;
import im.expensive.command.friends.FriendStorage;
import im.expensive.events.EventDisplay;
import im.expensive.functions.api.Category;
import im.expensive.functions.api.Function;
import im.expensive.functions.api.FunctionRegister;
import im.expensive.functions.settings.Setting;
import im.expensive.functions.settings.impl.ColorSetting;
import im.expensive.functions.settings.impl.ModeSetting;
import im.expensive.utils.math.MathUtil;
import im.expensive.utils.player.MoveUtils;
import im.expensive.utils.player.PlayerUtils;
import im.expensive.utils.render.ColorUtils;
import im.expensive.utils.render.DisplayUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;

@FunctionRegister(name = "Pointers", type = Category.Render)
public class Pointers extends Function {
    private final ModeSetting colores = new ModeSetting("Тип", "Клиент", new String[]{"Клиент", "Свой"});
    private final ColorSetting color1 = (new ColorSetting("Цвет", ColorUtils.rgb(255, 255, 255))).setVisible(this::lambda$new$0);
    private final ColorSetting colorfr1 = (new ColorSetting("Цвет друзей", ColorUtils.rgb(73, 252, 3))).setVisible(this::lambda$new$1);
    public float animationStep;
    private float lastYaw;
    private float lastPitch;
    private float animatedYaw;
    private float animatedPitch;
    LivingEntity entity;

    public Pointers() {
        this.addSettings(new Setting[]{this.colores, this.color1, this.colorfr1});
    }

    @Subscribe
    public void onDisplay(EventDisplay var1) {
        if (mc.player == null || mc.world == null || var1.getType() != EventDisplay.Type.PRE) return;

        this.animatedYaw = MathUtil.fast(this.animatedYaw, mc.player.moveStrafing * 10.0F, 5.0F);
        this.animatedPitch = MathUtil.fast(this.animatedPitch, mc.player.moveForward * 10.0F, 5.0F);

        float distance = 30.0F;
        if (mc.currentScreen instanceof InventoryScreen) distance += 30.0F;
        if (MoveUtils.isMoving()) distance += 0.0F; // можно убрать

        this.animationStep = MathUtil.fast(this.animationStep, distance, 6.0F);

        if (mc.gameSettings.getPointOfView() != PointOfView.FIRST_PERSON) return;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (!PlayerUtils.isNameValid(player.getNameClear())) continue;
            if (player == mc.player) continue;

            double x = player.lastTickPosX + (player.getPosX() - player.lastTickPosX) * mc.getRenderPartialTicks()
                    - mc.getRenderManager().info.getProjectedView().getX();
            double z = player.lastTickPosZ + (player.getPosZ() - player.lastTickPosZ) * mc.getRenderPartialTicks()
                    - mc.getRenderManager().info.getProjectedView().getZ();

            double cos = MathHelper.cos((float) (mc.getRenderManager().info.getYaw() * (Math.PI * 2 / 360)));
            double sin = MathHelper.sin((float) (mc.getRenderManager().info.getYaw() * (Math.PI * 2 / 360)));
            double rotY = -(z * cos - x * sin);
            double rotX = -(x * cos + z * sin);

            float angle = (float) (Math.atan2(rotY, rotX) * 180 / Math.PI);

            float screenWidth = mc.getWindow().getScaledWidth();
            float screenHeight = mc.getWindow().getScaledHeight();

            double arrowX = (this.animationStep * MathHelper.cos((float) Math.toRadians(angle)) + screenWidth / 2.0F)
                    + this.animatedYaw;
            double arrowY = (this.animationStep * MathHelper.sin((float) Math.toRadians(angle)) + screenHeight / 2.0F)
                    + this.animatedPitch;

            GlStateManager.pushMatrix();
            GlStateManager.disableBlend();
            GlStateManager.translated(arrowX, arrowY, 0);
            GlStateManager.rotatef(angle, 0, 0, 1);

            boolean isFriend = FriendStorage.isFriend(player.getGameProfile().getName());

            if (colores.is("Свой")) {
                int color = isFriend ? (int) colorfr1.get() : (int) color1.get();
                // Рисуем чёрный треугольник для тени
                drawTriangle(-4.0F, -1.0F, 4.0F, 7.0F, new Color(0, 0, 0, 32));
                // Рисуем основной треугольник
                drawTriangle(-3.0F, 0.0F, 3.0F, 5.0F, new Color(color));
                // Рисуем PNG (если есть)
                DisplayUtils.drawImage(new ResourceLocation("expensive/images/arrow.png"), -8.0F, -9.0F, 18.0F, 18.0F, color);
            } else {
                // "Клиент" – используем цвет из FriendStorage или дефолтный белый
                int color = isFriend ? FriendStorage.getColor() : ColorUtils.rgb(255, 255, 255);
                drawTriangle(-4.0F, -1.0F, 4.0F, 7.0F, new Color(0, 0, 0, 32));
                drawTriangle(-3.0F, 0.0F, 3.0F, 5.0F, new Color(color));
                DisplayUtils.drawImage(new ResourceLocation("expensive/images/arrow.png"), -8.0F, -9.0F, 18.0F, 18.0F, color);
            }

            GlStateManager.enableBlend();
            GlStateManager.popMatrix();
        }

        this.lastYaw = mc.player.rotationYaw;
        this.lastPitch = mc.player.rotationPitch;
    }

    // === РЕАЛИЗАЦИЯ РИСОВАНИЯ ТРЕУГОЛЬНИКА ===
    public static void drawTriangle(float x, float y, float width, float height, Color color) {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;

        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x, y + height);
        GL11.glVertex2f(x + width / 2, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private Boolean lambda$new$1() {
        return this.colores.is("Свой");
    }

    private Boolean lambda$new$0() {
        return this.colores.is("Свой");
    }
}
