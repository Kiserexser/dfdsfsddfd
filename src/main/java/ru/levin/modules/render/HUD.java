package ru.levin.modules.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

import org.joml.Vector4f;

import ru.levin.ExosWare;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.manager.dragManager.Dragging;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.math.MathUtil;
import ru.levin.util.render.RenderAddon;
import ru.levin.util.render.RenderUtil;

import java.awt.*;

@FunctionAnnotation(
        name = "HUD",
        desc = "Delta Style HUD",
        type = Type.Render
)
public class HUD extends Function {

    public final MultiSetting elements = new MultiSetting(
            "Elements",
            java.util.Arrays.asList(
                    "Watermark",
                    "TargetHUD",
                    "KeyBinds",
                    "ArmorHUD"
            ),
            new String[]{
                    "Watermark",
                    "TargetHUD",
                    "KeyBinds",
                    "ArmorHUD"
            }
    );

    private final BooleanSetting blur =
            new BooleanSetting("Blur", true);

    public final Dragging watermarkDrag =
            ExosWare.getInstance().createDrag(
                    this,
                    "Watermark",
                    10,
                    10
            );

    public final Dragging targetDrag =
            ExosWare.getInstance().createDrag(
                    this,
                    "TargetHUD",
                    10,
                    40
            );

    public final Dragging keybindDrag =
            ExosWare.getInstance().createDrag(
                    this,
                    "KeybindHUD",
                    10,
                    100
            );

    public final Dragging armorDrag =
            ExosWare.getInstance().createDrag(
                    this,
                    "ArmorHUD",
                    400,
                    450
            );

    private float healthAnimation;

    public HUD() {
        addSettings(elements, blur);
    }

    @Override
    public void onEvent(Event event) {

        if (!(event instanceof EventRender2D e))
            return;

        if (mc.player == null || mc.world == null)
            return;

        if (elements.get("Watermark"))
            renderWatermark(e);

        if (elements.get("TargetHUD"))
            renderTargetHUD(e);

        if (elements.get("KeyBinds"))
            renderKeybinds(e);

        if (elements.get("ArmorHUD"))
            renderArmor(e);
    }

    /*
        WATERMARK
     */

    private void renderWatermark(EventRender2D e) {

        float x = watermarkDrag.getX();
        float y = watermarkDrag.getY();

        String text =
                "sqvirtik | "
                        + ClientManager.getFps()
                        + " fps | "
                        + ClientManager.getPing()
                        + " ms";

        float width =
                FontUtils.durman[15]
                        .getWidth(text) + 20;

        MatrixStack matrices =
                e.getDrawContext()
                        .getMatrices();

        drawPanel(
                matrices,
                x,
                y,
                width,
                18
        );

        FontUtils.durman[15]
                .drawLeftAligned(
                        matrices,
                        text,
                        x + 8,
                        y + 5,
                        -1
                );

        watermarkDrag.setWidth(width);
        watermarkDrag.setHeight(18);
    }

    /*
        TARGET HUD
     */

    private void renderTargetHUD(EventRender2D e) {

        LivingEntity target =
                Manager.FUNCTION_MANAGER
                        .attackAura.target instanceof LivingEntity
                        ? (LivingEntity)
                          Manager.FUNCTION_MANAGER
                          .attackAura.target
                        : mc.player;

        float x = targetDrag.getX();
        float y = targetDrag.getY();

        float width = 135;
        float height = 42;

        DrawContext context =
                e.getDrawContext();

        MatrixStack matrices =
                context.getMatrices();

        drawPanel(
                matrices,
                x,
                y,
                width,
                height
        );

        RenderAddon.drawHead(
                matrices,
                target,
                x + 5,
                y + 5,
                30,
                5
        );

        String name =
                target.getName().getString();

        if (name.length() > 12)
            name = name.substring(0, 12);

        FontUtils.durman[16]
                .drawLeftAligned(
                        matrices,
                        name,
                        x + 42,
                        y + 7,
                        -1
                );

        float health =
                MathHelper.clamp(
                        target.getHealth()
                                / target.getMaxHealth(),
                        0,
                        1
                );

        healthAnimation =
                MathUtil.fast(
                        healthAnimation,
                        health,
                        15
                );

        RenderUtil.drawRoundedRect(
                matrices,
                x + 42,
                y + 27,
                80,
                5,
                2,
                new Color(
                        30,
                        30,
                        30,
                        255
                ).getRGB()
        );

        RenderUtil.drawRoundedRect(
                matrices,
                x + 42,
                y + 27,
                80 * healthAnimation,
                5,
                2,
                ColorUtil.getColorStyle(0)
        );

        FontUtils.durman[13]
                .drawLeftAligned(
                        matrices,
                        (int)(target.getHealth()) + " HP",
                        x + 42,
                        y + 17,
                        new Color(
                                200,
                                200,
                                200
                        ).getRGB()
                );

        targetDrag.setWidth(width);
        targetDrag.setHeight(height);
    }

    /*
        KEYBINDS
     */

    private void renderKeybinds(EventRender2D e) {

        float x = keybindDrag.getX();
        float y = keybindDrag.getY();

        float width = 105;
        float offset = 0;

        MatrixStack matrices =
                e.getDrawContext()
                        .getMatrices();

        drawPanel(
                matrices,
                x,
                y,
                width,
                18
        );

        FontUtils.durman[15]
                .drawLeftAligned(
                        matrices,
                        "Keybinds",
                        x + 8,
                        y + 5,
                        -1
                );

        y += 20;

        for (Function f :
                Manager.FUNCTION_MANAGER.getFunctions()) {

            if (!f.state || f.bind == 0)
                continue;

            drawPanel(
                    matrices,
                    x,
                    y + offset,
                    width,
                    16
            );

            FontUtils.durman[13]
                    .drawLeftAligned(
                            matrices,
                            f.name,
                            x + 6,
                            y + 4 + offset,
                            -1
                    );

            String bind =
                    "[" +
                            ClientManager.getKey(f.bind)
                            + "]";

            FontUtils.durman[13]
                    .drawRightAligned(
                            matrices,
                            bind,
                            x + width - 6,
                            y + 4 + offset,
                            ColorUtil.getColorStyle(0)
                    );

            offset += 18;
        }

        keybindDrag.setWidth(width);
        keybindDrag.setHeight(offset + 20);
    }

    /*
        ARMOR HUD
     */

    private void renderArmor(EventRender2D e) {

        float x = armorDrag.getX();
        float y = armorDrag.getY();

        drawPanel(
                e.getDrawContext().getMatrices(),
                x,
                y,
                82,
                20
        );

        int offset = 0;

        for (int i = 3; i >= 0; i--) {

            var stack =
                    mc.player.getInventory()
                            .armor.get(i);

            if (stack.isEmpty())
                continue;

            e.getDrawContext()
                    .drawItem(
                            stack,
                            (int)x + 4 + offset,
                            (int)y + 2
                    );

            offset += 20;
        }

        armorDrag.setWidth(82);
        armorDrag.setHeight(20);
    }

    /*
        PANEL
     */

    private void drawPanel(
            MatrixStack matrices,
            float x,
            float y,
            float width,
            float height
    ) {

        if (blur.get()) {

            RenderUtil.drawBlur(
                    matrices,
                    x,
                    y,
                    width,
                    height,
                    new Vector4f(5, 5, 5, 5),
                    15,
                    Color.WHITE.getRGB()
            );
        }

        RenderUtil.drawRoundedRect(
                matrices,
                x,
                y,
                width,
                height,
                5,
                new Color(
                        15,
                        15,
                        15,
                        170
                ).getRGB()
        );
    }
}
