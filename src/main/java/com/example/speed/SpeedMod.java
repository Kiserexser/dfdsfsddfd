package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // === НАСТРОЙКИ ===
    private static final double ATTACK_RANGE = 4.0;          // УВЕЛИЧИЛ до 4.0
    private static final double MIN_DELAY = 0.690;
    private static final double MAX_DELAY = 0.760;
    private static final boolean RESET_SPRINT = true;

    private static boolean enabled = false;
    private static long lastAttackTime = 0;
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;
    private static boolean lastKeyState = false;

    @Override
    public void onInitialize() {
        LOGGER.info("TriggerBot (исправленный) загружен. Нажми R для включения.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    try {
                        if (mc.getWindow() == null || mc.player == null || mc.world == null) return;
                        if (mc.interactionManager == null) {
                            LOGGER.warn("interactionManager is null");
                            return;
                        }

                        long window = mc.getWindow().getHandle();

                        // === ПЕРЕКЛЮЧЕНИЕ ПО R ===
                        boolean currentKey = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
                        if (currentKey && !lastKeyState) {
                            enabled = !enabled;
                            if (mc.player != null) {
                                mc.player.sendMessage(Text.literal(
                                        enabled ? "§aTriggerBot ВКЛЮЧЁН" : "§cTriggerBot ВЫКЛЮЧЁН"
                                ), true);
                            }
                            LOGGER.info("TriggerBot: " + (enabled ? "ON" : "OFF"));
                        }
                        lastKeyState = currentKey;

                        if (!enabled) return;

                        // === ПОЛУЧАЕМ СУЩНОСТЬ ПОД ПРИЦЕЛОМ ===
                        // Используем getTickDelta() для точности
                        HitResult hit = mc.player.raycast(ATTACK_RANGE, mc.getTickDelta(), false);
                        if (hit.getType() != HitResult.Type.ENTITY) {
                            // Отладка: если не видит сущность
                            // LOGGER.debug("Не сущность: " + hit.getType());
                            return;
                        }

                        EntityHitResult entityHit = (EntityHitResult) hit;
                        if (!(entityHit.getEntity() instanceof LivingEntity target)) {
                            LOGGER.debug("Цель не LivingEntity");
                            return;
                        }

                        // === ПРОВЕРКИ ===
                        if (target.isDead() || !target.isAlive() || target == mc.player) {
                            LOGGER.debug("Цель мертва или это игрок");
                            return;
                        }

                        double dist = mc.player.distanceTo(target);
                        if (dist > ATTACK_RANGE) {
                            LOGGER.debug("Дистанция {} > {}", dist, ATTACK_RANGE);
                            return;
                        }

                        // === ЗАДЕРЖКА ===
                        long now = System.currentTimeMillis();
                        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
                        delay += (random.nextDouble() - 0.5) * 0.015;
                        delay = Math.max(0.660, Math.min(0.780, delay));

                        if (now - lastAttackTime < (long)(delay * 1000)) {
                            // LOGGER.debug("Задержка");
                            return;
                        }

                        // === СБРОС СПРИНТА ===
                        if (RESET_SPRINT && mc.player.isSprinting()) {
                            mc.player.setSprinting(false);
                        }

                        // === АТАКА ===
                        mc.interactionManager.attackEntity(mc.player, target);
                        mc.player.swingHand(mc.player.getActiveHand());
                        lastAttackTime = now;

                        // Отладка: успешный удар
                        LOGGER.info("Удар по " + target.getName().getString());

                    } catch (Exception e) {
                        LOGGER.error("Ошибка TriggerBot", e);
                    }
                });
            }
        }).start();
    }
}
