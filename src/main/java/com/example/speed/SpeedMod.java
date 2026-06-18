package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Pose;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static String currentMode = "Slap"; // "Slap" или "Matrix"

    // === Slap ===
    private boolean placed = false;
    private final StopWatch slapTimer = new StopWatch();
    private int counter = 0;

    // === Matrix ===
    private boolean canBoost = false;
    private boolean sent = false;
    private int ticks = 0;
    private double x = 0, z = 0, y = 0, firstDir = 0;
    private int matrixTicks = 20;
    private float matrixSpeed = 2.0f;

    private Thread workerThread;
    private volatile boolean running = true;
    private boolean wasRPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("LongJump (no Fabric API) loaded. Press R to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean rPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

                        if (rPressed && !wasRPressed) {
                            toggle();
                            wasRPressed = true;
                        } else if (!rPressed) {
                            wasRPressed = false;
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        update();
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) { break; }
                catch (Exception e) { LOGGER.error("LongJump error", e); }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void toggle() {
        enabled = !enabled;
        if (enabled) {
            onEnable();
            LOGGER.info("LongJump ON (mode: " + currentMode + ")");
        } else {
            onDisable();
            LOGGER.info("LongJump OFF");
        }
        // звук (опционально)
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
            }
        });
    }

    private void onEnable() {
        counter = 0;
        placed = false;
        slapTimer.reset();
        if (currentMode.equals("Matrix")) {
            canBoost = false;
            sent = false;
            ticks = 0;
            x = mc.player.getX();
            z = mc.player.getZ();
            y = mc.player.getY();
            firstDir = mc.player.getYaw();
            mc.player.setVelocity(0, 0, 0);
        }
    }

    private void onDisable() {
        mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        mc.player.setPose(Pose.STANDING);
        placed = false;
        sent = false;
        canBoost = false;
    }

    private void update() {
        if (mc.player == null || mc.world == null) return;

        if (currentMode.equals("Slap")) {
            updateSlap();
        } else if (currentMode.equals("Matrix")) {
            updateMatrix();
        }
    }

    // ======================== Slap ========================
    private void updateSlap() {
        if (mc.player.isInWater()) return;

        int slot = findSlabInHotbar();
        if (slot == -1) {
            if (enabled) {
                LOGGER.warn("LongJump (Slap): no slabs in hotbar! Disabling.");
                enabled = false;
            }
            return;
        }

        int oldSlot = mc.player.getInventory().selectedSlot;
        HitResult trace = mc.player.raycast(2, 1.0f, false);
        if (trace instanceof BlockHitResult result) {
            if (isMoving() && mc.player.fallDistance >= 0.8
                    && mc.world.getBlockState(mc.player.getBlockPos()).isAir()
                    && !mc.world.getBlockState(result.getBlockPos()).isAir()
                    && mc.world.getBlockState(result.getBlockPos()).isSolid()
                    && !(mc.world.getBlockState(result.getBlockPos()).getBlock() instanceof SlabBlock)
                    && !(mc.world.getBlockState(result.getBlockPos()).getBlock() instanceof StairsBlock)) {

                mc.player.getInventory().selectedSlot = slot;
                placed = true;
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
                mc.player.getInventory().selectedSlot = oldSlot;
                mc.player.fallDistance = 0;
            }

            mc.options.jumpKey.setPressed(false);

            if ((mc.player.isOnGround() && !mc.options.jumpKey.isPressed())
                    && placed
                    && mc.world.getBlockState(mc.player.getBlockPos()).isAir()
                    && !mc.world.getBlockState(result.getBlockPos()).isAir()
                    && mc.world.getBlockState(result.getBlockPos()).isSolid()
                    && !(mc.world.getBlockState(result.getBlockPos()).getBlock() instanceof SlabBlock)
                    && slapTimer.hasReached(750)) {

                mc.player.setPose(Pose.STANDING);
                slapTimer.reset();
                placed = false;
            } else if ((mc.player.isOnGround() && !mc.options.jumpKey.isPressed())) {
                mc.player.jump();
                placed = false;
            }
        } else {
            if ((mc.player.isOnGround() && !mc.options.jumpKey.isPressed())) {
                mc.player.jump();
                placed = false;
            }
        }
    }

    private int findSlabInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.SMOOTH_STONE_SLAB) {
                return i;
            }
        }
        return -1;
    }

    private boolean isMoving() {
        return mc.player.forwardSpeed != 0 || mc.player.sidewaysSpeed != 0;
    }

    // ======================== Matrix ========================
    private void updateMatrix() {
        if (!canBoost) {
            mc.player.setVelocity(0, 0, 0);
        }

        if (!sent) {
            mc.player.setVelocity(0, 0, 0);
            if (ticks > matrixTicks) {
                sent = true;
                ticks = 0;
                canBoost = true;
                // сброс таймера (не используется)
            }
        }

        if (canBoost) {
            // Установка скорости
            double yaw = Math.toRadians(mc.player.getYaw());
            double motionX = -Math.sin(yaw) * matrixSpeed;
            double motionZ = Math.cos(yaw) * matrixSpeed;
            mc.player.setVelocity(motionX, 0.42, motionZ);
            // Отключаем после рывка (как в оригинале)
            // В оригинале отключается при флаге, но мы сделаем через 10 тиков после рывка
            if (ticks > 10) {
                enabled = false;
                LOGGER.info("LongJump disabled after boost.");
                onDisable();
            }
        }

        ticks++;
    }

    // ======================== StopWatch (аналог) ========================
    private static class StopWatch {
        private long lastMillis = 0;

        public StopWatch() {
            reset();
        }

        public void reset() {
            lastMillis = System.currentTimeMillis();
        }

        public boolean hasReached(long delay) {
            return System.currentTimeMillis() - lastMillis >= delay;
        }
    }
}
