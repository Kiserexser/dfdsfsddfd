package rich.modules.impl.movement;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.MathHelper;
import rich.events.api.EventHandler;
import rich.events.impl.TickEvent;
import rich.modules.module.ModuleStructure;
import rich.modules.module.category.ModuleCategory;
import rich.modules.module.setting.implement.SelectSetting;
import rich.util.Instance;
import rich.util.string.PlayerInteractionHelper;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NoWeb extends ModuleStructure {
    public static NoWeb getInstance() {
        return Instance.get(NoWeb.class);
    }

    public final SelectSetting webMode = new SelectSetting("Режим", "Выберите режим обхода").value("Grim");

    public NoWeb() {
        super("NoWeb", "No Web", ModuleCategory.MOVEMENT);
        settings(webMode);
        setState(true); // сразу включаем
    }

    @Override
    public boolean isEnabled() {
        return true; // всегда включён, рубильник не нужен
    }

    @EventHandler
    public void onTick(TickEvent e) {
        boolean inWeb = PlayerInteractionHelper.isPlayerInBlock(Blocks.COBWEB);
        boolean inBerries = PlayerInteractionHelper.isPlayerInBlock(Blocks.SWEET_BERRY_BUSH);

        if (inWeb || inBerries) {
            double motionY = mc.options.jumpKey.isPressed() ? 1.3 : mc.options.sneakKey.isPressed() ? -1.3 : 0;
            float yaw = mc.player.getYaw() * ((float) Math.PI / 180.0F);
            // Множитель 4x: 0.633 * 4 = 2.532
            float multiplier = 2.532f;
            float f = mc.player.forwardSpeed * multiplier;
            float s = mc.player.sidewaysSpeed * multiplier;
            
            if (f != 0 || s != 0) {
                mc.player.setVelocity(-MathHelper.sin(yaw) * f + MathHelper.cos(yaw) * s, motionY,
                                      MathHelper.cos(yaw) * f + MathHelper.sin(yaw) * s);
            } else {
                mc.player.setVelocity(0, motionY, 0);
            }
        }
    }
}
