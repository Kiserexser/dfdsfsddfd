package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static long timer = 0;

    private Thread workerThread;
    private volatile boolean running = true;
    private boolean wasXPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("WaterSpeed loaded. Press X to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean xPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_X) == GLFW.GLFW_PRESS;

                        if (xPressed && !wasXPressed) {
                            enabled = !enabled;
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendMessage(Text.of("§6WaterSpeed §7» " + (enabled ? "§aВключён" : "§cВыключен")), true);
                                }
                            });
                            LOGGER.info("WaterSpeed: " + (enabled ? "ON" : "OFF"));
                            if (enabled) timer = System.currentTimeMillis();
                            wasXPressed = true;
                        } else if (!xPressed) {
                            wasXPressed = false;
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        mc.execute(SpeedMod::applyWaterSpeed);
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("WaterSpeed error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private static void applyWaterSpeed() {
        if (mc.player == null) return;

        if (!mc.player.isTouchingWater()) return;

        boolean isMoving = isMoving();
        if (isMoving) {
            timer = System.currentTimeMillis();
        }

        boolean forward = mc.options.forwardKey.isPressed();
        boolean hasDepthStrider = false;
        // Исправлено: используем getEntitySlotId()
        ItemStack boots = mc.player.getInventory().getArmorStack(EquipmentSlot.FEET.getEntitySlotId());

        if (!boots.isEmpty()) {
            String bootsString = boots.toString().toLowerCase();
            if (bootsString.contains("depth_strider") ||
                bootsString.contains("depth strider") ||
                bootsString.contains("aqua_affinity") ||
                bootsString.contains("aqua affinity")) {
                hasDepthStrider = true;
            }
            if (!hasDepthStrider) {
                String bootsName = boots.getName().getString().toLowerCase();
                if (bootsName.contains("depth") || bootsName.contains("aqua") || bootsName.contains("water")) {
                    hasDepthStrider = true;
                }
            }
        }

        ItemStack offhand = mc.player.getOffHandStack();
        boolean hasPlayerHead = !offhand.isEmpty() && offhand.getItem() == Items.PLAYER_HEAD;

        float speedMultiplier;

        if (hasDepthStrider) {
            if (hasPlayerHead) {
                speedMultiplier = 1.040f;
            } else {
                speedMultiplier = 1.043f;
            }
        } else {
            speedMultiplier = 1.046f;
        }

        if (forward) {
            mc.player.setVelocity(
                    mc.player.getVelocity().x * speedMultiplier,
                    mc.player.getVelocity().y,
                    mc.player.getVelocity().z * speedMultiplier
            );
        }

        if (!mc.player.horizontalCollision &&
            !isMoving &&
            System.currentTimeMillis() - timer > 300) {

            if (mc.player.age % 3 == 0) {
                mc.player.setVelocity(
                        mc.player.getVelocity().x,
                        mc.player.getVelocity().y - 0.03,
                        mc.player.getVelocity().z
                );
            } else {
                mc.player.setVelocity(
                        mc.player.getVelocity().x,
                        mc.player.getVelocity().y + 0.019,
                        mc.player.getVelocity().z
                );
            }
        }
    }

    private static boolean isMoving() {
        return mc.options.forwardKey.isPressed() ||
               mc.options.backKey.isPressed() ||
               mc.options.leftKey.isPressed() ||
               mc.options.rightKey.isPressed();
    }
}
