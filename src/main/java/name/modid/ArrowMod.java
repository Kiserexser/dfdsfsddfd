package name.modid;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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

public class ArrowMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("arrowmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static final Identifier ARROW_TEXTURE = Identifier.of("arrowmod", "textures/arrow.png");

    private static boolean lastKeyState = false;

    @Override
    public void onInitialize() {
        LOGGER.info("ArrowMod загружен (без Fabric API). Нажми Z для включения.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    if (mc.getWindow() == null || mc.player == null) return;
                    long window = mc.getWindow().getHandle();

                    boolean currentState = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
                    if (currentState && !lastKeyState) {
                        enabled = !enabled;
                        if (mc.player != null) {
                            mc.player.sendMessage(Text.literal(
                                    enabled ? "§aСтрелки ВКЛ" : "§cСтрелки ВЫКЛ"
                            ), true);
                        }
                    }
                    lastKeyState = currentState;
                });
            }
        }).start();

        mc.setScreen(new IndicatorScreen());
    }

    public static class IndicatorScreen extends Screen {
        protected IndicatorScreen() {
            super(Text.literal("ArrowIndicator"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
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

                // === РЕНДЕРИНГ ЧЕРЕЗ РЕNDER SYSTEM (БЕЗ DRAWCONTEXT) ===
                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(arrowX, arrowY, 0);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));

                RenderSystem.setShaderTexture(0, ARROW_TEXTURE);
                RenderSystem.setShaderColor(1, 1, 1, 1);

                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

                float half = size / 2f;
                // Левая нижняя
                buffer.vertex(matrices.peek().getPositionMatrix(), -half, -half, 0).texture(0, 0).next();
                // Левая верхняя
                buffer.vertex(matrices.peek().getPositionMatrix(), -half, half, 0).texture(0, 1).next();
                // Правая верхняя
                buffer.vertex(matrices.peek().getPositionMatrix(), half, half, 0).texture(1, 1).next();
                // Правая нижняя
                buffer.vertex(matrices.peek().getPositionMatrix(), half, -half, 0).texture(1, 0).next();

                Tessellator.getInstance().draw();

                matrices.pop();
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_Z) {
                enabled = !enabled;
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal(
                            enabled ? "§aСтрелки ВКЛ" : "§cСтрелки ВЫКЛ"
                    ), true);
                }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
