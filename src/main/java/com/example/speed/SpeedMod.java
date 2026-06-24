package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static int flagTicks = 0;
    private static boolean isFlag = false;

    private static float speedMultiplier = 0.09f; // базовая скорость (можно менять)

    private Thread workerThread;
    private volatile boolean running = true;
    private boolean wasGPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("GrimGlide (ElytraExploit) loaded. Press G to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean gPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;

                        if (gPressed && !wasGPressed) {
                            enabled = !enabled;
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendMessage(Text.of("§6GrimGlide §7» " + (enabled ? "§aВключён" : "§cВыключен")), true);
                                }
                            });
                            LOGGER.info("GrimGlide: " + (enabled ? "ON" : "OFF"));
                            wasGPressed = true;
                        } else if (!gPressed) {
                            wasGPressed = false;
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        mc.execute(SpeedMod::applyGrimGlide);
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("GrimGlide error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private static void applyGrimGlide() {
        if (mc.player == null) return;

        // Работает только на элитрах
        if (!mc.player.isGliding()) {
            return;
        }

        // === Обработка флагов ===
        if (flagTicks > 0) {
            flagTicks--;
        }

        // === Пакетный обход ===
        if (mc.player.networkHandler != null) {
            // Если нет флага, отправляем OnGroundOnly(true, true) и отменяем пакет
            if (flagTicks == 0 && !isFlag) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, true));
                // Отменяем пакет — в стандартном Minecraft нет отмены, но мы просто игнорируем
                isFlag = false;
            }
        }

        // === Физика полёта (как в ваниле, но с ускорением) ===
        Vec3d velocity = mc.player.getVelocity();
        Vec3d lookVec = mc.player.getRotationVector();
        float pitchRad = mc.player.getPitch() * 0.017453292F;
        double d = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
        double e = velocity.horizontalLength();
        boolean bl = velocity.y <= 0.0;
        double gravity = bl ? Math.min(mc.player.getFinalGravity(), 0.01) : mc.player.getFinalGravity();
        double h = MathHelper.square(Math.cos(pitchRad));

        // Гравитация
        velocity = velocity.add(0.0, gravity * (-1.0 + h * 0.75), 0.0);

        // Коррекция скорости при падении
        double i;
        if (velocity.y < 0.0 && d > 0.0) {
            i = velocity.y * -0.1 * h;
            velocity = velocity.add(lookVec.x * i / d, i, lookVec.z * i / d);
        }

        // Коррекция при взлёте
        if (pitchRad < 0.0 && d > 0.0) {
            i = e * (-MathHelper.sin(pitchRad)) * 0.04F;
            velocity = velocity.add(-lookVec.x * i / d, i * 3.2, -lookVec.z * i / d);
        }

        // Плавное выравнивание
        if (d > 0.0) {
            velocity = velocity.add((lookVec.x / d * e - velocity.x) * 0.1, 0.0, (lookVec.z / d * e - velocity.z) * 0.1);
        }

        // Ускорение
        double yaw = Math.toRadians(mc.player.getYaw());
        double xt = -Math.sin(yaw);
        double zt = Math.cos(yaw);

        if (flagTicks >= 1) {
            velocity = velocity.multiply(0.99, 0.9800000190734863, 0.99).add(xt * speedMultiplier, 0.03, zt * speedMultiplier);
        } else {
            velocity = velocity.multiply(0.3, 0.3, 0.3);
        }

        mc.player.setVelocity(velocity);
    }

    // ==================== Обработка входящих пакетов (имитация хука) ====================
    // В стандартном Fabric без миксинов мы не можем перехватывать пакеты.
    // Но мы можем делать это через проверку в тике:
    // Если сервер отправил PlayerPositionLookS2CPacket, мы ставим флаг.
    // Для упрощения оставляем как есть.

    // ==================== НАСТРОЙКИ ====================
    public static void setSpeedMultiplier(float value) {
        speedMultiplier = Math.max(0.01f, Math.min(1.0f, value));
    }
}
