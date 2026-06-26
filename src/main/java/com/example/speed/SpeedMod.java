package com.example.triggerbot; // поменяй на свой пакет

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class TriggerBot implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("triggerbot");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    private static boolean enabled = false;
    private static long lastAttackTime = 0;

    // === НАСТРОЙКИ ===
    private static final double MIN_DELAY = 0.690;   // секунды
    private static final double MAX_DELAY = 0.750;
    private static final double RANGE = 5.0;         // дистанция до цели

    // Клавиша включения/выключения (T)
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_T;
    private static boolean lastKeyState = false;

    @Override
    public void onInitialize() {
        LOGGER.info("TriggerBot загружен (без Fabric API). Нажми T для включения/выключения.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    try {
                        if (mc.getWindow() == null || mc.player == null || mc.world == null) return;
                        long window = mc.getWindow().getHandle();

                        // Переключение по T
                        boolean currentKeyState = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
                        if (currentKeyState && !lastKeyState) {
                            enabled = !enabled;
                            if (mc.player != null) {
                                mc.player.sendMessage(Text.literal(
                                        enabled ? "§aTriggerBot ВКЛЮЧЁН" : "§cTriggerBot ВЫКЛЮЧЁН"
                                ), true);
                            }
                            LOGGER.info("TriggerBot: " + (enabled ? "ON" : "OFF"));
                        }
                        lastKeyState = currentKeyState;

                        if (!enabled) return;

                        // Проверяем, смотрит ли игрок на игрока
                        HitResult hit = mc.player.raycast(RANGE, mc.getTickDelta(), false);
                        if (hit.getType() == HitResult.Type.ENTITY) {
                            EntityHitResult entityHit = (EntityHitResult) hit;
                            if (entityHit.getEntity() instanceof PlayerEntity target) {
                                if (target.isAlive() && !target.isDead() && target != mc.player) {
                                    // Задержка 0.690–0.750 с рандомным сдвигом ±0.020
                                    double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
                                    // Добавляем небольшой случайный сдвиг для естественности
                                    delay += (random.nextDouble() - 0.5) * 0.020;
                                    delay = Math.max(0.650, Math.min(0.800, delay));

                                    long now = System.currentTimeMillis();
                                    if (now - lastAttackTime >= (long)(delay * 1000)) {
                                        // Атакуем
                                        mc.interactionManager.attackEntity(mc.player, target);
                                        mc.player.swingHand(mc.player.getActiveHand());
                                        lastAttackTime = now;
                                    }
                                }
                            }
                        }

                    } catch (Exception e) {
                        LOGGER.error("TriggerBot error", e);
                    }
                });
            }
        }).start();
    }
}
