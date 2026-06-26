package com.example.speed; // измени на свой пакет

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

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // === НАСТРОЙКИ ===
    private static final double ATTACK_RANGE = 3.0;
    private static final double MIN_DELAY = 0.690;
    private static final double MAX_DELAY = 0.760;
    private static final double MISS_CHANCE = 0.09;   // 9% промах
    private static final double SKIP_CHANCE = 0.02;   // 2% пропуск
    private static final boolean RESET_SPRINT = true;

    private static boolean enabled = false;
    private static long lastAttackTime = 0;
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;
    private static boolean lastKeyState = false;

    @Override
    public void onInitialize() {
        LOGGER.info("TriggerBot (автономный) загружен. Нажми R для включения/выключения.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    try {
                        if (mc.getWindow() == null || mc.player == null || mc.world == null) return;
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

                        // === ПРОВЕРКА ПРИЦЕЛА ===
                        HitResult hit = mc.player.raycast(ATTACK_RANGE, 1.0f, false);
                        if (hit.getType() != HitResult.Type.ENTITY) return;

                        EntityHitResult entityHit = (EntityHitResult) hit;
                        if (!(entityHit.getEntity() instanceof PlayerEntity target)) return;

                        if (target.isDead() || !target.isAlive() || target == mc.player) return;
                        if (mc.player.distanceTo(target) > ATTACK_RANGE) return;

                        // === ЗАДЕРЖКА ===
                        long now = System.currentTimeMillis();
                        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
                        delay += (random.nextDouble() - 0.5) * 0.015;
                        delay = Math.max(0.660, Math.min(0.780, delay));

                        if (now - lastAttackTime < (long)(delay * 1000)) return;

                        // === 2% ПРОПУСК ===
                        if (random.nextDouble() < SKIP_CHANCE) {
                            lastAttackTime = now + 80;
                            return;
                        }

                        // === 9% ПРОМАХ ===
                        if (random.nextDouble() < MISS_CHANCE) {
                            mc.player.swingHand(mc.player.getActiveHand());
                            lastAttackTime = now + 50;
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

                    } catch (Exception e) {
                        LOGGER.error("Ошибка TriggerBot", e);
                    }
                });
            }
        }).start();
    }
}
