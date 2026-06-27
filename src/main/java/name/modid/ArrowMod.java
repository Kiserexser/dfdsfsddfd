package name.modid;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArrowMod implements ModInitializer, ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("arrowmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static boolean lastKeyState = false;
    private static final Identifier ARROW_TEXTURE = Identifier.of("arrowmod", "textures/arrow.png");
    private static final float FIXED_RADIUS = 70f; // фиксированное расстояние от центра
    private static long lastToggleTime = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("ArrowMod загружен. Нажми Z для включения/выключения.");
    }

    @Override
    public void onInitializeClient() {
        // Клавиша Z (не блокирует управление)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (mc.getWindow() == null) return;
            long window = mc.getWindow().getHandle();
            boolean currentState = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
            if (currentState && !lastKeyState) {
                enabled = !enabled;
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal(
                            enabled ? "§aСтрелки ВКЛ" : "§cСтрелки ВЫКЛ"
                    ), true);
                }
                lastToggleTime = System.currentTimeMillis();
            }
            lastKeyState = currentState;
        });

        // Отрисовка стрелок
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!enabled) return;
            if (mc.player == null || mc.world == null) return;

            int screenWidth = mc.getWindow().getScaledWidth();
            int screenHeight = mc.getWindow().getScaledHeight();

            for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player || player.isDead() || !player.isAlive()) continue;

                // Вычисляем направление на игрока
                double dx = player.getX() - mc.player.getX();
                double dz = player.getZ() - mc.player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < 1.5 || dist > 60) continue; // игнорируем слишком близких/далёких

                // Угол в горизонтальной плоскости
                float yaw = mc.player.getYaw();
                double cos = MathHelper.cos((float) (yaw * (Math.PI * 2 / 360)));
                double sin = MathHelper.sin((float) (yaw * (Math.PI * 2 / 360)));
                double rotY = -(dz * cos - dx * sin);
                double rotX = -(dx * cos + dz * sin);
                float angle = (float) (Math.atan2(rotY, rotX) * 180 / Math.PI);

                // Позиция стрелки на экране (фиксированный радиус)
                float arrowX = FIXED_RADIUS * MathHelper.cos((float) Math.toRadians(angle)) + screenWidth / 2f;
                float arrowY = FIXED_RADIUS * MathHelper.sin((float) Math.toRadians(angle)) + screenHeight / 2f;

                // Размер и пульсация
                float size = 24f;
                float pulse = 1f + 0.06f * (float) Math.sin((System.currentTimeMillis() + player.getId()) / 500.0 * Math.PI * 2);
                size *= pulse;

                int half = (int) (size / 2);

                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(arrowX, arrowY, 0);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));

                // Рисуем текстуру (PNG) или символ, если PNG нет
                try {
                    RenderSystem.setShaderTexture(0, ARROW_TEXTURE);
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                    Tessellator tessellator = Tessellator.getInstance();
                    BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                    var posMat = matrices.peek().getPositionMatrix();
                    buffer.vertex(posMat, -half, -half, 0).texture(0, 0).endVertex();
                    buffer.vertex(posMat, -half, half, 0).texture(0, 1).endVertex();
                    buffer.vertex(posMat, half, half, 0).texture(1, 1).endVertex();
                    buffer.vertex(posMat, half, -half, 0).texture(1, 0).endVertex();
                    BufferBuilder.BuiltBuffer builtBuffer = buffer.end();
                    tessellator.draw(builtBuffer);
                } catch (Exception e) {
                    // Если PNG не загружен, рисуем символ
                    context.drawText(mc.textRenderer, "▲", -half, -half, 0xFFFFFFFF, false);
                }

                // Отображение дистанции (опционально)
                if (dist > 0) {
                    String distText = String.format("%.1f", dist);
                    float textX = -mc.textRenderer.getWidth(distText) / 2f;
                    float textY = half + 4f;
                    context.drawText(mc.textRenderer, distText, (int) textX, (int) textY, 0xCCFFFFFF, false);
                }

                matrices.pop();
            }
        });
    }
}
