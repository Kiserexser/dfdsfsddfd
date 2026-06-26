package com.example.speed; // поменяй на свой пакет

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
    private static final double ATTACK_RANGE = 3.0;          // дистанция атаки
    private static final double MIN_DELAY = 0.690;           // мин. задержка (сек)
    private static final double MAX_DELAY = 0.760;           // макс. задержка (сек)
    private static final boolean RESET_SPRINT = true;        // сброс спринта перед ударом

    private static boolean enabled = false;
    private static long lastAttackTime = 0;
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;
    private static boolean lastKeyState = false;

    @Override
    public void onInitialize() {
        LOGGER.info("TriggerBot (все сущности, без промахов) загружен. Нажми R для включения.");

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

                        // === ПОЛУЧАЕМ СУЩНОСТЬ ПОД ПРИЦЕЛОМ ===
                        HitResult hit = mc.player.raycast(ATTACK_RANGE, 1.0f, false);
                        if (hit.getType() != HitResult.Type.ENTITY) return;

                        EntityHitResult entityHit = (EntityHitResult) hit;
                        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;

                        // === ПРОВЕРКИ ===
                        if (target.isDead() || !target.isAlive() || target == mc.player) return;
                        if (mc.player.distanceTo(target) > ATTACK_RANGE) return;

                        // === ЗАДЕРЖКА (рандомная) ===
                        long now = System.currentTimeMillis();
                        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
                        // небольшой разброс для естественности
                        delay += (random.nextDouble() - 0.5) * 0.015;
                        delay = Math.max(0.660, Math.min(0.780, delay));

                        if (now - lastAttackTime < (long)(delay * 1000)) return;

                        // === СБРОС СПРИНТА (если включено) ===
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
