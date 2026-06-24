package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.class_2248;
import net.minecraft.class_2250;
import net.minecraft.class_2309;
import net.minecraft.class_238;
import net.minecraft.class_2487;
import net.minecraft.class_2586;
import net.minecraft.class_2593;
import net.minecraft.class_2605;
import net.minecraft.class_2614;
import net.minecraft.class_2650;
import net.minecraft.class_2680;
import net.minecraft.class_2834;
import net.minecraft.class_2843;
import net.minecraft.class_2845;
import net.minecraft.class_2846;
import net.minecraft.class_2847;
import net.minecraft.class_2874;
import net.minecraft.class_2879;
import net.minecraft.class_2881;
import net.minecraft.class_2885;
import net.minecraft.class_2895;
import net.minecraft.class_2896;
import net.minecraft.class_2902;
import net.minecraft.class_2934;
import net.minecraft.class_310;
import net.minecraft.class_746;
import net.minecraft.class_7924;
import net.minecraft.class_8061;
import net.minecraft.class_8069;
import net.minecraft.class_8098;
import net.minecraft.class_8114;
import net.minecraft.class_8115;
import net.minecraft.class_8117;
import net.minecraft.class_8118;
import net.minecraft.class_8119;
import net.minecraft.class_8120;
import net.minecraft.class_8121;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod загружен! Нажми R для переключения.");
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            new SpeedThread().start();
        }).start();
    }

    private static class SpeedThread extends Thread {
        private static final class_310 mc = class_310.method_1551();
        private boolean enabled = false;
        private boolean lastKeyState = false;
        private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;
        private static final double BASE_SPEED = 0.2;
        private static final double MULTIPLIER = 1.6;
        private static final double MAX_SPEED = 1.2;

        @Override
        public void run() {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                try {
                    if (mc == null || mc.field_1714 == null || mc.field_1724 == null || mc.field_1687 == null) continue;

                    long window = mc.field_1714.method_4490();
                    boolean currentKeyState = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;

                    if (currentKeyState && !lastKeyState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "SpeedMod ВКЛЮЧЁН" : "SpeedMod ВЫКЛЮЧЁН");
                    }
                    lastKeyState = currentKeyState;
                    if (!enabled) continue;

                    class_746 player = mc.field_1724;
                    if (player == null) continue;

                    class_238 feetPos = player.method_23317();
                    class_2680 state = mc.field_1687.method_8320(feetPos);
                    class_2248 block = state.method_26204();

                    if (isSpecialBlock(block)) {
                        applySpeed(player);
                    }
                } catch (Exception ignored) {}
            }
        }

        private boolean isSpecialBlock(class_2248 block) {
            try {
                return block instanceof class_2250  // SnowBlock
                        || block instanceof class_2309  // SlabBlock
                        || block instanceof class_2487  // StairsBlock
                        || block instanceof class_2586  // TrapdoorBlock
                        || block instanceof class_2593  // FenceBlock
                        || block instanceof class_2605  // FenceGateBlock
                        || block instanceof class_2614  // FlowerPotBlock
                        || block instanceof class_2650  // PressurePlateBlock
                        || block instanceof class_2680  // LeverBlock
                        || block instanceof class_2834  // ButtonBlock
                        || block instanceof class_2843  // RailBlock
                        || block instanceof class_2845  // VineBlock
                        || block instanceof class_2846  // LadderBlock
                        || block instanceof class_2847  // ScaffoldingBlock
                        || block instanceof class_2874  // SweetBerryBushBlock
                        || block instanceof class_2879  // SnowyBlock
                        || block instanceof class_2881  // LightBlock
                        || block instanceof class_2885; // CaveVinesBlock
            } catch (Exception e) {
                return false;
            }
        }

        private void applySpeed(class_746 player) {
            try {
                float yaw = player.field_6084;
                if (mc.field_1690.field_1856.method_1434()) {
                    double forward = 1.0;
                    double strafe = 0.0;
                    if (mc.field_1690.field_1852.method_1434()) strafe -= 1.0;
                    if (mc.field_1690.field_1853.method_1434()) strafe += 1.0;

                    double len = Math.sqrt(forward * forward + strafe * strafe);
                    if (len > 0) { forward /= len; strafe /= len; }

                    double rad = Math.toRadians(yaw);
                    double dx = forward * -Math.sin(rad) + strafe * Math.cos(rad);
                    double dz = forward * Math.cos(rad) + strafe * Math.sin(rad);

                    class_8061 currentVel = player.method_18798();
                    double currentSpeed = Math.sqrt(currentVel.field_38488 * currentVel.field_38488 + currentVel.field_38490 * currentVel.field_38490);
                    double targetSpeed = Math.min(MAX_SPEED, BASE_SPEED * MULTIPLIER);

                    if (currentSpeed < targetSpeed) {
                        double newSpeed = Math.min(targetSpeed, currentSpeed + 0.02);
                        player.method_19053(new class_8061(dx * newSpeed, currentVel.field_38489, dz * newSpeed));
                    } else {
                        player.method_19053(new class_8061(dx * targetSpeed, currentVel.field_38489, dz * targetSpeed));
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
