package name.modid;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArrowMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("arrowmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static final Identifier ARROW_TEXTURE = Identifier.of("arrowmod", "textures/arrow.png");
    private static KeyBinding keyBinding;

    @Override
    public void onInitialize() {
        LOGGER.info("ArrowMod загружен. Нажми Z для включения/выключения.");

        // Регистрация клавиши через Fabric API (безопасно, т.к. KeyBindingHelper работает в onInitialize)
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.arrowmod.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "category.arrowmod"
        ));

        // Обработка нажатия клавиши в клиентском тике
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyBinding.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(
                            enabled ? "§aСтрелки ВКЛ" : "§cСтрелки ВЫКЛ"
                    ), true);
                }
            }
        });

        // Отрисовка через HUD Render Callback (тоже из Fabric API)
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!enabled) return;
            if (mc.player == null || mc.world == null) return;

            int screenWidth = mc.getWindow().getScaledWidth();
            int screenHeight = mc.getWindow().getScaledHeight();

            for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player || player.isDead() || !player.isAlive()) continue;

                double dx = player.getX() - mc.player.getX();
                double dz = player.getZ() - mc.player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 50) continue;

                float yaw = mc.player.getYaw();
                double cos = MathHelper.cos((float) (yaw * (Math.PI * 2 / 360)));
                double sin = MathHelper.sin((float) (yaw * (Math.PI * 2 / 360)));
                double rotY = -(dz * cos - dx * sin);
                double rotX = -(dx * cos + dz * sin);

                float angle = (float) (Math.atan2(rotY, rotX) * 180 / Math.PI);

                float baseDistance = 60 + 100 * (float) Math.min(1, dist / 50);
                float arrowX = (float) (baseDistance * MathHelper.cos((float) Math.toRadians(angle)) + screenWidth / 2f);
                float arrowY = (float) (baseDistance * MathHelper.sin((float) Math.toRadians(angle)) + screenHeight / 2f);

                int size = 24;
                int half = size / 2;

                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(arrowX, arrowY, 0);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));

                // Рисуем текстуру через DrawContext (безопасно)
                context.drawTexture(ARROW_TEXTURE, -half, -half, size, size, 0, 0, size, size, size, size);

                matrices.pop();
            }
        });
    }
}
