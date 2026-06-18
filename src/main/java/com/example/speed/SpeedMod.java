package com.example.speed;

import dev.relictdlc.module.Module;
import dev.relictdlc.module.ModuleCategory;
import dev.relictdlc.setting.BooleanSetting;
import dev.relictdlc.setting.NumberSetting;
import dev.relictdlc.event.events.EventUpdate;
import dev.relictdlc.event.EventTarget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SpeedMod extends Module {

    // ===== Настройки =====
    private final NumberSetting range = addSetting(new NumberSetting("Радиус", "Дистанция атаки", 4.5, 1.0, 8.0, 0.1));
    private final NumberSetting minDelay = addSetting(new NumberSetting("Мин. задержка", "Минимальная задержка (сек)", 0.680, 0.1, 1.0, 0.005));
    private final NumberSetting maxDelay = addSetting(new NumberSetting("Макс. задержка", "Максимальная задержка (сек)", 0.740, 0.1, 1.0, 0.005));
    private final NumberSetting jitterH = addSetting(new NumberSetting("Дрожание (гор)", "Горизонтальное дрожание (градусы)", 3.0, 0.0, 10.0, 0.1));
    private final NumberSetting jitterV = addSetting(new NumberSetting("Дрожание (верт)", "Вертикальное дрожание (градусы)", 5.0, 0.0, 10.0, 0.1));
    private final BooleanSetting sprintReset = addSetting(new BooleanSetting("Сброс спринта", "Отключать спринт перед ударом", true));

    // ===== Внутренние переменные =====
    private long lastAttackTime = 0;
    private final Random random = new Random();

    public SpeedMod() {
        super("SpeedMod", "KillAura с дрожанием", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        // 1. Поиск цели
        LivingEntity target = getTarget();
        if (target == null) return;

        // 2. Проверка дистанции
        double dist = mc.player.distanceTo(target);
        if (dist > range.getValue()) return;

        // 3. Задержка между ударами
        long now = System.currentTimeMillis();
        double delay = minDelay.getValue() + (maxDelay.getValue() - minDelay.getValue()) * random.nextDouble();
        long delayMs = (long) (delay * 1000);
        if (now - lastAttackTime < delayMs) return;

        // 4. Сброс спринта (если включено)
        if (sprintReset.isEnabled() && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        // 5. Дрожание прицела
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        float jitterYaw = (float) (jitterH.getValue() * (random.nextDouble() - 0.5) * 2);
        float jitterPitch = (float) (jitterV.getValue() * (random.nextDouble() - 0.5) * 2);
        mc.player.setYaw(yaw + jitterYaw);
        mc.player.setPitch(pitch + jitterPitch);

        // 6. Атака (без отводки)
        mc.interactionManager.attackEntity(mc.player, target);

        // 7. Восстанавливаем углы (чтобы не было постоянного дрейфа)
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);

        // 8. Обновляем время атаки
        lastAttackTime = now;
    }

    private LivingEntity getTarget() {
        Box box = mc.player.getBoundingBox().expand(range.getValue());
        List<LivingEntity> entities = mc.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != mc.player && e.isAlive() && !e.isDead());
        entities.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        return entities.isEmpty() ? null : entities.get(0);
    }

    @Override
    public void onDisable() {
        lastAttackTime = 0;
        super.onDisable();
    }
}
