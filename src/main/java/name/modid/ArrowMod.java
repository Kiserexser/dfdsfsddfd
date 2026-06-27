package name.modid;

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
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArrowMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("arrowmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static boolean lastKeyState = false;

    @Override
    public void onInitialize() {
        LOGGER.info("ArrowMod загружен. Нажми Z.");

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

                // Рисуем треугольник через GL11
                GL11.glPushMatrix();
                GL11.glTranslatef(arrowX, arrowY, 0);
                GL11.glRotatef(angle, 0, 0, 1);
                GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION);

                buffer.vertex(0, -6, 0).endVertex();   // остриё
                buffer.vertex(-5, 4, 0).endVertex();   // лево-низ
                buffer.vertex(5, 4, 0).endVertex();    // право-низ

                BufferBuilder.BuiltBuffer builtBuffer = buffer.end();
                tessellator.draw(builtBuffer);

                GL11.glPopMatrix();
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
