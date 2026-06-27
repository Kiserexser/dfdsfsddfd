package name.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArrowMod implements ModInitializer, ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("arrowmod");

    private static boolean enabled = false;
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // ===== Ресурсы для твоих PNG =====
    // Помести файлы в: src/main/resources/assets/arrowmod/textures/arrow.png
    private static final Identifier ARROW_TEXTURE = Identifier.of("arrowmod", "textures/arrow.png");

    // ===== Регистрация клавиши Z =====
    private static KeyBinding keyBinding;

    @Override
    public void onInitialize() {
        LOGGER.info("ArrowMod загружен!");
    }

    @Override
    public void onInitializeClient() {
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.arrowmod.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "category.arrowmod"
        ));

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

        // Хук для отрисовки (рендерим на экране)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (enabled && client.currentScreen == null && client.player != null && client.world != null) {
                // Мы не можем рисовать напрямую здесь, нужно использовать событие Render
                // Поэтому используем отдельный хук через ClientTickEvents для вызова метода рендера,
                // но лучше использовать RenderEvents.
                // Вместо этого мы можем рисовать в конце каждого тика, но это не гарантирует отрисовку.
                // Используем RenderEvents.END_CLIENT_TICK не подходит. Воспользуемся хуком рендера.
                // Рекомендую использовать ClientTickEvents для обновления, а для рисования - отдельный миксин.
                // Однако есть событие HudRenderCallback из Fabric API.
            }
        });

        // Правильный способ: использовать HudRenderCallback
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (enabled) {
                renderArrows(context);
            }
        });
    }

    // ===== Отрисовка стрелок =====
    private static void renderArrows(DrawContext context) {
        if (mc.player == null || mc.world == null) return;

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isDead() || !player.isAlive()) continue;

            // Вычисляем угол и расстояние до игрока
            double dx = player.getX() - mc.player.getX();
            double dz = player.getZ() - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 50) continue; // не показываем слишком далёких

            float yaw = mc.player.getYaw();
            double cos = MathHelper.cos((float) (yaw * (Math.PI * 2 / 360)));
            double sin = MathHelper.sin((float) (yaw * (Math.PI * 2 / 360)));
            double rotY = -(dz * cos - dx * sin);
            double rotX = -(dx * cos + dz * sin);

            float angle = (float) (Math.atan2(rotY, rotX) * 180 / Math.PI);

            // Расстояние от центра экрана (чем дальше игрок, тем ближе к краю)
            float baseDistance = 60 + 100 * (float) Math.min(1, dist / 50);
            float arrowX = (float) (baseDistance * MathHelper.cos((float) Math.toRadians(angle)) + screenWidth / 2f);
            float arrowY = (float) (baseDistance * MathHelper.sin((float) Math.toRadians(angle)) + screenHeight / 2f);

            // Рисуем текстуру стрелки (твоя PNG) с поворотом
            context.getMatrices().push();
            context.getMatrices().translate(arrowX, arrowY, 0);
            context.getMatrices().rotate((float) Math.toRadians(angle), 0, 0, 1);

            // Размер текстуры (подгони под свои PNG)
            int texSize = 24;
            context.drawTexture(ARROW_TEXTURE,
                    -texSize/2, -texSize/2,
                    0, 0,
                    texSize, texSize,
                    texSize, texSize);

            context.getMatrices().pop();
        }
    }
}
