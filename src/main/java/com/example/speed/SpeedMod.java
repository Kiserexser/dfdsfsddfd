package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // === Состояния Y-Port ===
    private static boolean enabled = false;
    private static int step = 0;
    private static boolean vulcanDownwards = false;
    private static boolean vulcanSwitch = false;
    private static int vulcanResetCnt = 0;
    private static double startHeight = 0;
    private static double lastMotionX = 0;
    private static double lastMotionZ = 0;
    private static double motionY = 0;

    private static Thread workerThread;
    private static volatile boolean running = true;
    private static boolean wasGPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Y-Port Fly (Vulcan bypass) loaded. Press G to toggle.");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();
                        boolean gPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;

                        if (gPressed && !wasGPressed) {
                            enabled = !enabled;
                            if (enabled) resetState();
                            LOGGER.info("Y-Port Fly: " + (enabled ? "ON" : "OFF"));
                            wasGPressed = true;
                        } else if (!gPressed) {
                            wasGPressed = false;
                        }
                    }

                    if (mc != null && mc.player != null && mc.world != null && enabled) {
                        mc.execute(SpeedMod::updateFly);
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("Y-Port error", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private static void resetState() {
        step = 0;
        vulcanDownwards = false;
        vulcanSwitch = false;
        vulcanResetCnt = 0;
        startHeight = mc.player.getY();
        lastMotionX = 0;
        lastMotionZ = 0;
        motionY = 0;
    }

    private static void updateFly() {
        if (mc.player == null) return;

        // Проверка – если на земле, выходим (как в оригинале)
        if (mc.player.isOnGround()) {
            // Можно сбросить состояние, чтобы не было глюков
            // resetState();
            return;
        }

        double currentY = mc.player.getY();
        double motionX = mc.player.getVelocity().x;
        double motionZ = mc.player.getVelocity().z;
        boolean downwards = mc.options.sneakKey.isPressed();
        boolean upwards = mc.options.jumpKey.isPressed();

        // === Логика, адаптированная из Y-Port ===
        if (vulcanDownwards && !downwards) {
            startHeight = getFloorHeight(currentY);
            vulcanDownwards = false;
            vulcanResetCnt++;
            step--;
        }

        double deltaY = currentY - startHeight;
        step++;

        switch (step) {
            case 1:
                if (!mc.player.isOnGround() && deltaY < 0.073) {
                    if (deltaY > 0 || vulcanResetCnt > 1) {
                        motionY = -deltaY;
                    } else {
                        // если условия не выполнены – выходим
                        return;
                    }
                    vulcanSwitch = true;
                } else {
                    return;
                }
                break;
            case 2:
                // Отправляем фейковый пакет StatusOnly(false)
                if (mc.player.networkHandler != null) {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false));
                }
                break;
            case 3:
                lastMotionX = motionX;
                lastMotionZ = motionZ;
                if (vulcanSwitch) {
                    motionY = -deltaY + 0.015625;
                } else if (upwards) {
                    motionY = -deltaY + 0.5;
                    startHeight = currentY + motionY;
                } else {
                    motionY = -deltaY + 0.0625;
                }
                break;
            case 4:
                vulcanSwitch = !vulcanSwitch;
                // Устанавливаем ground в зависимости от состояния (мы не можем вызвать setGround, просто игнорируем)
                motionX = lastMotionX * 0.88;
                if (downwards) {
                    motionY = vulcanSwitch ? -0.097000002 : -0.147000003;
                } else {
                    motionY = -0.097000002;
                }
                motionZ = lastMotionZ * 0.88;

                vulcanDownwards = downwards;
                if (downwards) {
                    step--;
                } else {
                    step = 1;
                }
                break;
            default:
                return;
        }

        // Применяем скорость
        mc.player.setVelocity(motionX, motionY, motionZ);
        // В оригинале был lerpMotion, мы просто присваиваем
    }

    private static double getFloorHeight(double currentY) {
        // Имитация PositionHelper.getMathHeight(Face.DOWN, currentY, 0.015625)
        // Просто округляем вниз до ближайшего блока с шагом 0.015625
        double step = 0.015625;
        double floor = Math.floor(currentY / step) * step;
        // Если разница меньше step, возвращаем floor
        return floor;
    }
}
