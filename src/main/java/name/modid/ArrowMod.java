package name.modid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
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
        LOGGER.info("ArrowMod загружен (без GL11). Нажми Z для открытия/закрытия.");

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

        // Пустой фон – прозрачный экран
        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
            // Не рисуем фон
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

                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(arrowX, arrowY, 0);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));

                // Рисуем символ ▲ с поворотом
                context.drawText(mc.textRenderer, "▲", -6, -9, 0xFFFFFFFF, false);

                matrices.pop();

                // Показываем дистанцию
                if (dist > 0) {
                    String distText = String.format("%.1f", dist);
                    context.drawText(mc.textRenderer, distText, (int) arrowX - mc.textRenderer.getWidth(distText) / 2, (int) (arrowY + 18), 0xCCFFFFFF, false);
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
            return false; // пропускаем все остальные клавиши в игру
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false; // пропускаем клики в игру
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
