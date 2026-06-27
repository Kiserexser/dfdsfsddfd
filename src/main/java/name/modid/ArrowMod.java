package name.modid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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
    private static final float FIXED_RADIUS = 70f;

    @Override
    public void onInitialize() {
        LOGGER.info("ArrowMod загружен (без Fabric API). Нажми Z для открытия/закрытия.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                mc.execute(() -> {
                    if (mc.getWindow() == null || mc.player == null) return;
                    long window = mc.getWindow().getHandle();
                    boolean currentState = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
                    if (currentState && !lastKeyState) {
                        enabled = !enabled;
                        if (enabled) {
                            mc.setScreen(new IndicatorScreen());
                        } else {
                            if (mc.currentScreen instanceof IndicatorScreen) {
                                mc.setScreen(null);
                            }
                        }
                    }
                    lastKeyState = currentState;
                });
            }
        }).start();
    }

    public static class IndicatorScreen extends Screen {
        protected IndicatorScreen() {
            super(Text.literal(""));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (mc.player == null || mc.world == null) return;
            int screenWidth = mc.getWindow().getScaledWidth();
            int screenHeight = mc.getWindow().getScaledHeight();

            for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player || player.isDead() || !player.isAlive()) continue;

                double dx = player.getX() - mc.player.getX();
                double dz = player.getZ() - mc.player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < 1.5 || dist > 60) continue;

                float yaw = mc.player.getYaw();
                double cos = MathHelper.cos((float) (yaw * (Math.PI * 2 / 360)));
                double sin = MathHelper.sin((float) (yaw * (Math.PI * 2 / 360)));
                double rotY = -(dz * cos - dx * sin);
                double rotX = -(dx * cos + dz * sin);
                float angle = (float) (Math.atan2(rotY, rotX) * 180 / Math.PI);

                float arrowX = FIXED_RADIUS * MathHelper.cos((float) Math.toRadians(angle)) + screenWidth / 2f;
                float arrowY = FIXED_RADIUS * MathHelper.sin((float) Math.toRadians(angle)) + screenHeight / 2f;

                // Рисуем треугольник через GL11
                GL11.glPushMatrix();
                GL11.glTranslatef(arrowX, arrowY, 0);
                GL11.glRotatef(angle, 0, 0, 1);

                // Цвет белый (можно поменять)
                GL11.glColor4f(1, 1, 1, 1);

                // Размеры: ширина 12, длина 18 (в 1.5 раза больше)
                float halfWidth = 6f;
                float length = 18f;

                GL11.glBegin(GL11.GL_TRIANGLES);
                // Остриё (впереди, по оси Y вверх)
                GL11.glVertex2f(0, length / 2);
                // Левая нижняя точка
                GL11.glVertex2f(-halfWidth, -length / 2);
                // Правая нижняя точка
                GL11.glVertex2f(halfWidth, -length / 2);
                GL11.glEnd();

                GL11.glPopMatrix();

                // Отображение дистанции (опционально)
                if (dist > 0) {
                    String distText = String.format("%.1f", dist);
                    context.drawText(mc.textRenderer, distText, (int) arrowX - mc.textRenderer.getWidth(distText) / 2, (int) (arrowY + length / 2 + 6), 0xCCFFFFFF, false);
                }
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_Z || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                enabled = false;
                this.close();
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
