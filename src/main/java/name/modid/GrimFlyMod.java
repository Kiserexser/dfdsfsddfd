package name.modid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Timer;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class GrimFlyMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("grimfly");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    private static boolean enabled = false;
    private static boolean lastKeyState = false;
    private static int tickCounter = 0;

    // Настройки (можно менять прямо в коде)
    private static final double VERTICAL_SPEED = 0.25;
    private static final double HORIZONTAL_SPEED = 0.1;
    private static final float TIMER_SPEED = 1.05f;

    @Override
    public void onInitialize() {
        LOGGER.info("GrimFlyMod loaded. Press G to toggle.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    if (mc.getWindow() == null || mc.player == null || mc.world == null) return;
                    long window = mc.getWindow().getHandle();

                    // Переключение по G
                    boolean current = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
                    if (current && !lastKeyState) {
                        enabled = !enabled;
                        if (enabled) {
                            // При включении стартуем Timer
                            Timer timer = mc.timer;
                            if (timer != null) {
                                timer.timerSpeed = TIMER_SPEED;
                            }
                            mc.player.sendMessage(Text.literal("§aGrimFly ON"), true);
                        } else {
                            // Выключаем – сбрасываем Timer
                            Timer timer = mc.timer;
                            if (timer != null) {
                                timer.timerSpeed = 1.0f;
                            }
                            mc.player.sendMessage(Text.literal("§cGrimFly OFF"), true);
                            mc.player.setVelocity(0, 0, 0);
                        }
                        LOGGER.info("GrimFly: " + (enabled ? "ON" : "OFF"));
                    }
                    lastKeyState = current;

                    if (!enabled) return;

                    // ---- ОСНОВНАЯ ЛОГИКА ----
                    tickCounter++;

                    // Горизонтальное движение (WASD)
                    float yaw = mc.player.getYaw();
                    double forward = 0, strafe = 0;
                    if (mc.options.forwardKey.isPressed()) forward += 1.0;
                    if (mc.options.backKey.isPressed()) forward -= 1.0;
                    if (mc.options.leftKey.isPressed()) strafe -= 1.0;
                    if (mc.options.rightKey.isPressed()) strafe += 1.0;

                    double len = Math.sqrt(forward * forward + strafe * strafe);
                    if (len > 0) {
                        forward /= len;
                        strafe /= len;
                    }

                    double rad = Math.toRadians(yaw);
                    double moveX = (forward * -Math.sin(rad) + strafe * Math.cos(rad)) * HORIZONTAL_SPEED;
                    double moveZ = (forward * Math.cos(rad) + strafe * Math.sin(rad)) * HORIZONTAL_SPEED;

                    // Вертикальная скорость с небольшим колебанием
                    double deltaY = VERTICAL_SPEED + (random.nextDouble() - 0.5) * 0.02;
                    deltaY = Math.max(0.05, Math.min(0.6, deltaY));

                    // Иногда делаем микро-спуск для имитации падения (каждые 3 тика)
                    if (tickCounter % 3 == 0) {
                        deltaY = -deltaY * 0.2;
                    }

                    // Применяем движение
                    mc.player.setVelocity(moveX, deltaY, moveZ);
                    mc.player.fallDistance = 0f;

                    // Отправляем пакет позиции с чередованием onGround
                    boolean onGround = (tickCounter % 2 == 0);
                    double x = mc.player.getX();
                    double y = mc.player.getY();
                    double z = mc.player.getZ();
                    mc.getNetworkHandler().sendPacket(
                            new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround)
                    );

                    // Сброс счётчика
                    if (tickCounter > 100) tickCounter = 0;
                });
            }
        }).start();
    }
}
