package name.modid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class MultiMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("multimod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // Состояния
    private static boolean airStuckEnabled = false;
    private static boolean flyEnabled = false;
    private static boolean lastX = false, lastZ = false;
    private static int tickCounter = 0;
    private static double lastY = 0;

    // Настройки Fly
    private static final double VERTICAL_SPEED = 0.25;
    private static final double HORIZONTAL_SPEED = 0.1;
    private static final int PACKET_DELAY = 4;

    @Override
    public void onInitialize() {
        LOGGER.info("MultiMod loaded: AirStuck (X), Fly (Z)");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    if (mc.getWindow() == null || mc.player == null || mc.world == null) return;
                    long window = mc.getWindow().getHandle();

                    // ---- КЛАВИША X: AirStuck ----
                    boolean xPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_X) == GLFW.GLFW_PRESS;
                    if (xPressed && !lastX) {
                        airStuckEnabled = !airStuckEnabled;
                        if (airStuckEnabled) {
                            mc.player.sendMessage(Text.literal("§aAirStuck ON"), true);
                        } else {
                            mc.player.sendMessage(Text.literal("§cAirStuck OFF"), true);
                            mc.player.setVelocity(0, 0, 0);
                        }
                        LOGGER.info("AirStuck: " + (airStuckEnabled ? "ON" : "OFF"));
                    }
                    lastX = xPressed;

                    // ---- КЛАВИША Z: Fly ----
                    boolean zPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
                    if (zPressed && !lastZ) {
                        flyEnabled = !flyEnabled;
                        if (flyEnabled) {
                            mc.player.sendMessage(Text.literal("§aFly ON"), true);
                            lastY = mc.player.getY();
                        } else {
                            mc.player.sendMessage(Text.literal("§cFly OFF"), true);
                            mc.player.setVelocity(0, 0, 0);
                        }
                        LOGGER.info("Fly: " + (flyEnabled ? "ON" : "OFF"));
                    }
                    lastZ = zPressed;

                    // ---- ЛОГИКА AirStuck ----
                    if (airStuckEnabled) {
                        // Замораживаем игрока: скорость = 0
                        mc.player.setVelocity(0, 0, 0);
                        // Отправляем фиктивный пакет с той же позицией, чтобы сервер не двигал нас
                        double x = mc.player.getX();
                        double y = mc.player.getY();
                        double z = mc.player.getZ();
                        mc.getNetworkHandler().sendPacket(
                                new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true, true)
                        );
                        // Также можно добавить небольшой подъём для стабильности
                        return; // чтобы не выполнялась логика Fly
                    }

                    // ---- ЛОГИКА Fly ----
                    if (flyEnabled) {
                        tickCounter++;

                        // Проверка под ногами: только снег / полублоки / лестницы (необязательно)
                        // Можно убрать, чтобы летать везде, но тогда больше риска.
                        BlockPos belowPos = mc.player.getBlockPos().down();
                        boolean isOnSpecialBlock = mc.world.getBlockState(belowPos).isIn(net.minecraft.registry.tag.BlockTags.SNOW)
                                || mc.world.getBlockState(belowPos).isIn(net.minecraft.registry.tag.BlockTags.SLABS)
                                || mc.world.getBlockState(belowPos).isIn(net.minecraft.registry.tag.BlockTags.STAIRS);

                        // Если хотите летать только на спец. блоках – раскомментируйте проверку
                        // if (!isOnSpecialBlock) {
                        //     mc.player.setVelocity(0, 0, 0);
                        //     return;
                        // }

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

                        // Вертикальная скорость
                        double deltaY = 0;
                        if (mc.options.jumpKey.isPressed()) {
                            deltaY = VERTICAL_SPEED + (random.nextDouble() - 0.5) * 0.02;
                        } else if (mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() ||
                                mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()) {
                            deltaY = VERTICAL_SPEED * 0.6;
                        } else {
                            deltaY = 0;
                        }

                        // Ограничиваем, чтобы не было слишком быстро
                        deltaY = Math.max(0.05, Math.min(0.4, deltaY));

                        // Применяем движение
                        mc.player.setVelocity(moveX, deltaY, moveZ);
                        mc.player.fallDistance = 0f;

                        // Отправка пакета позиции (не каждый тик)
                        if (tickCounter % PACKET_DELAY == 0) {
                            double x = mc.player.getX();
                            double y = mc.player.getY();
                            double z = mc.player.getZ();
                            // Чередуем onGround для сбивания предсказаний
                            boolean onGround = (tickCounter % 4 < 2);
                            mc.getNetworkHandler().sendPacket(
                                    new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, true)
                            );
                            lastY = y;
                        }

                        if (tickCounter > 100) tickCounter = 0;
                    } else {
                        // Если Fly выключен, не мешаем
                        // Но можно ничего не делать
                    }
                });
            }
        }).start();
    }
}
