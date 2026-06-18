package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final Random random = new Random();
    private long lastAttackTime = 0;
    private boolean enabled = false; // можно включить/выключить по клавише, но пока просто включено

    // Настройки (жёстко заданы)
    private static final double RANGE = 4.5;
    private static final double MIN_DELAY = 0.680; // сек
    private static final double MAX_DELAY = 0.740; // сек
    private static final double JITTER_H = 3.0; // градусы
    private static final double JITTER_V = 5.0; // градусы
    private static final boolean SPRINT_RESET = true;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod KillAura initialized.");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Поиск цели
            LivingEntity target = getTarget(client);
            if (target == null) return;

            // Дистанция
            double dist = client.player.distanceTo(target);
            if (dist > RANGE) return;

            // Задержка
            long now = System.currentTimeMillis();
            double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
            long delayMs = (long) (delay * 1000);
            if (now - lastAttackTime < delayMs) return;

            // Сброс спринта
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

            // Атака
            client.interactionManager.attackEntity(client.player, target);

            // Восстанавливаем углы
            client.player.setYaw(yaw);
            client.player.setPitch(pitch);

            lastAttackTime = now;
        });
    }

    private LivingEntity getTarget(MinecraftClient client) {
        Box box = client.player.getBoundingBox().expand(RANGE);
        List<LivingEntity> entities = client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != client.player && e.isAlive() && !e.isDead());
        entities.sort(Comparator.comparingDouble(e -> client.player.distanceTo(e)));
        return entities.isEmpty() ? null : entities.get(0);
    }
}
