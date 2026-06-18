package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");

    private static boolean enabled = false;
    private static final Random random = new Random();
    private long lastAttackTime = 0;

    // Настройки (можно поменять в коде)
    private static final double RANGE = 4.5;
    private static final double MIN_DELAY = 0.680; // сек
    private static final double MAX_DELAY = 0.740;
    private static final double JITTER_H = 3.0;    // градусы
    private static final double JITTER_V = 5.0;
    private static final boolean SPRINT_RESET = true;

    private Thread workerThread;
    private volatile boolean running = true;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod KillAura loaded. Press R to toggle.");

        workerThread = new Thread(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            while (running) {
                try {
                    // Проверка клавиши R (включение/выключение)
                    if (client != null && client.getWindow() != null) {
                        long window = client.getWindow().getHandle();
                        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                            enabled = !enabled;
                            LOGGER.info("KillAura: " + (enabled ? "ON" : "OFF"));
                            Thread.sleep(300); // дебаунс
                        }
                    }

                    // Если выключено – спим дальше
                    if (!enabled || client == null || client.player == null || client.world == null) {
                        Thread.sleep(50);
                        continue;
                    }

                    // 1. Поиск цели
                    LivingEntity target = getTarget(client);
                    if (target == null) {
                        Thread.sleep(50);
                        continue;
                    }

                    // 2. Дистанция
                    double dist = client.player.distanceTo(target);
                    if (dist > RANGE) {
                        Thread.sleep(50);
                        continue;
                    }

                    // 3. Задержка между ударами
                    long now = System.currentTimeMillis();
                    double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
                    long delayMs = (long) (delay * 1000);
                    if (now - lastAttackTime < delayMs) {
                        Thread.sleep(10);
                        continue;
                    }

                    // 4. Атака в основном потоке Minecraft
                    client.execute(() -> {
                        if (client.player == null || target == null || !target.isAlive()) return;

                        // Сброс спринта (если включено)
                        if (SPRINT_RESET && client.player.isSprinting()) {
                            client.player.setSprinting(false);
                        }

                        // Дрожание прицела
                        float yaw = client.player.getYaw();
                        float pitch = client.player.getPitch();
                        float jitterYaw = (float) (JITTER_H * (random.nextDouble() - 0.5) * 2);
                        float jitterPitch = (float) (JITTER_V * (random.nextDouble() - 0.5) * 2);
                        client.player.setYaw(yaw + jitterYaw);
                        client.player.setPitch(pitch + jitterPitch);

                        // Атака (без отводки)
                        client.interactionManager.attackEntity(client.player, target);

                        // Восстановление углов
                        client.player.setYaw(yaw);
                        client.player.setPitch(pitch);

                        lastAttackTime = System.currentTimeMillis();
                    });

                    Thread.sleep(10); // маленькая пауза, чтобы не спамить
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("Error in KillAura thread", e);
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private LivingEntity getTarget(MinecraftClient client) {
        Box box = client.player.getBoundingBox().expand(RANGE);
        List<LivingEntity> entities = client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != client.player && e.isAlive() && !e.isDead());
        entities.sort(Comparator.comparingDouble(e -> client.player.distanceTo(e)));
        return entities.isEmpty() ? null : entities.get(0);
    }

    // При завершении игры можно остановить поток (но т.к. он daemon, он завершится сам)
}
