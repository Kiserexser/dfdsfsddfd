package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static final float SPEED_MULTIPLIER = 4.0f;

    private Thread workerThread;
    private volatile boolean running = true;
    private boolean wasKPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("NoWeb loaded. Press K to toggle. Status is shown on HUD.");

        // Запускаем поток для отслеживания клавиш
        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean kPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;

                        if (kPressed && !wasKPressed) {
                            enabled = !enabled;
                            // Отправляем сообщение в чат о включении/выключении
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendMessage(Text.of("§6NoWeb §7» §a" + (enabled ? "Включён" : "Выключен")), true);
                                }
                            });
                            LOGGER.info("NoWeb: " + (enabled ? "ON" : "OFF"));
                            wasKPressed = true;
                        } else if (!kPressed) {
                            wasKPressed = false;
                        }
                    }

                    // Применяем скорость только если мод включён и игрок в мире
                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        mc.execute(() -> {
                            if (mc.player != null && mc.world != null && isInWebOrBerries()) {
                                applySpeed();
                            }
                        });
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) { break; }
                catch (Exception e) { LOGGER.error("NoWeb error", e); }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();

        // Регистрируем рендер HUD
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (mc.player == null) return;
            String status = enabled ? "§aON" : "§cOFF";
            String text = "§6NoWeb: " + status;
            context.drawText(mc.textRenderer, Text.of(text), 5, 5, 0xFFFFFF, true);
        });
    }

    private void applySpeed() {
        if (mc.player == null) return;

        double forward = mc.player.forwardSpeed;
        double strafe = mc.player.sidewaysSpeed;
        if (forward == 0 && strafe == 0) return;

        float yaw = mc.player.getYaw() * 0.017453292F;

        double x = (-Math.sin(yaw) * forward) + (Math.cos(yaw) * strafe);
        double z = ( Math.cos(yaw) * forward) + (Math.sin(yaw) * strafe);

        x *= 0.23 * SPEED_MULTIPLIER;
        z *= 0.23 * SPEED_MULTIPLIER;

        double y = mc.player.getVelocity().y;
        if (mc.options.jumpKey.isPressed()) y += 0.04 * SPEED_MULTIPLIER;
        if (mc.options.sneakKey.isPressed()) y -= 0.04 * SPEED_MULTIPLIER;

        double currentSpeed = Math.sqrt(x * x + z * z);
        double maxSpeed = 0.53 * SPEED_MULTIPLIER;
        if (currentSpeed > maxSpeed) {
            double scale = maxSpeed / currentSpeed;
            x *= scale;
            z *= scale;
        }

        mc.player.setVelocity(x, y, z);

        if (mc.player.horizontalCollision || mc.player.verticalCollision) {
            Vec3d vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x, vel.y, vel.z);
        }
    }

    private boolean isInWebOrBerries() {
        BlockPos pos = mc.player.getBlockPos();
        var state = mc.world.getBlockState(pos);
        var stateUp = mc.world.getBlockState(pos.up());
        var stateDown = mc.world.getBlockState(pos.down());

        return state.isOf(Blocks.COBWEB) || stateUp.isOf(Blocks.COBWEB) || stateDown.isOf(Blocks.COBWEB) ||
               state.isOf(Blocks.SWEET_BERRY_BUSH) || stateUp.isOf(Blocks.SWEET_BERRY_BUSH) || stateDown.isOf(Blocks.SWEET_BERRY_BUSH);
    }
}
