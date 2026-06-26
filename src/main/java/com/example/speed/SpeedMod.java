package ru.cat.modules.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import ru.cat.events.Event;
import ru.cat.events.impl.EventUpdate;
import ru.cat.manager.Manager;
import ru.cat.modules.Function;
import ru.cat.modules.FunctionAnnotation;
import ru.cat.modules.Type;
import ru.cat.modules.setting.BooleanSetting;
import ru.cat.modules.setting.MultiSetting;

import java.util.Arrays;
import java.util.Random;

@FunctionAnnotation(name = "TriggerBot", keywords = {"Триггер", "Триггербот"}, desc = "Автоудар по цели под прицелом (задержка 0.69-0.76с)", type = Type.Combat)
public class TriggerBot extends Function {

    private final MultiSetting targets = new MultiSetting(
            "Цели",
            Arrays.asList("Игроки", "Мобы", "Монстры"),
            new String[]{"Игроки", "Друзья", "Мобы", "Монстры", "Жители"}
    );

    private final BooleanSetting attackWhileEating = new BooleanSetting("Бить когда ешь", false);
    private final BooleanSetting throughShield = new BooleanSetting("Бить сквозь щит", true);
    private final BooleanSetting resetSprint = new BooleanSetting("Сброс спринта", true);

    // === НАСТРОЙКИ ЗАДЕРЖКИ И ОШИБОК ===
    private static final double MIN_DELAY = 0.690;
    private static final double MAX_DELAY = 0.760;
    private static final double MISS_CHANCE = 0.09;   // 9% промах (машем рукой)
    private static final double SKIP_CHANCE = 0.02;   // 2% пропуск атаки (вообще не бьём)
    private static final double REACH = 3.0;

    private final Random random = new Random();
    private long lastAttackTime = 0;

    public TriggerBot() {
        addSettings(targets, attackWhileEating, throughShield, resetSprint);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!isEnabled()) return;

        // ---- 1. ПОИСК ЦЕЛИ ----
        LivingEntity target = null;
        Lock lock = Manager.FUNCTION_MANAGER.lock;
        if (lock != null && lock.state && isValidLockedTarget(lock.lockedTarget)) {
            target = lock.lockedTarget;
        } else {
            if (!(mc.crosshairTarget instanceof EntityHitResult ehr)) return;
            Entity e = ehr.getEntity();
            if (!(e instanceof LivingEntity living)) return;
            if (!isValidTarget(living)) return;
            target = living;
        }

        if (target == null) return;

        // ---- 2. ПРОВЕРКИ НА ЕДУ, ЩИТ, КУЛДАУН ----
        if (!attackWhileEating.get() && mc.player.isUsingItem() && !mc.player.getActiveItem().isOf(Items.SHIELD)) {
            return;
        }

        float cooldown = mc.player.getAttackCooldownProgress(mc.getRenderTickCounter().getTickDelta(true));
        if (cooldown < 0.9F) return;

        if (throughShield.get() && mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.SHIELD)) {
            mc.interactionManager.stopUsingItem(mc.player);
        }

        double distanceSq = mc.player.squaredDistanceTo(target);
        if (distanceSq > REACH * REACH) return;

        // ---- 3. РАСЧЁТ ЗАДЕРЖКИ ----
        long now = System.currentTimeMillis();
        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
        // Естественный разброс ±0.015с
        delay += (random.nextDouble() - 0.5) * 0.015;
        delay = Math.max(0.660, Math.min(0.780, delay));

        if (now - lastAttackTime < (long)(delay * 1000)) return;

        // ---- 4. ПРОПУСК АТАКИ (2%) ----
        if (random.nextDouble() < SKIP_CHANCE) {
            // Пропускаем атаку – вообще не бьём, но обновляем таймер, чтобы не спамить
            lastAttackTime = now + 80; // небольшая пауза
            return;
        }

        // ---- 5. ПРОМАХ (9%) ----
        if (random.nextDouble() < MISS_CHANCE) {
            // Промах – просто машем рукой (без урона)
            mc.player.swingHand(Hand.MAIN_HAND);
            lastAttackTime = now + 50;
            return;
        }

        // ---- 6. СБРОС СПРИНТА ----
        if (resetSprint.get() && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        // ---- 7. АТАКА ----
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttackTime = now;
    }

    // ==================== МЕТОДЫ ПРОВЕРКИ ЦЕЛИ ====================

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isAlive() || entity == mc.player) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (Manager.FUNCTION_MANAGER.antiBot.check(entity)) return false;

        if (entity instanceof PlayerEntity) {
            if (!targets.get("Игроки")) return false;
            if (!targets.get("Друзья") && Manager.FRIEND_MANAGER.isFriend(entity.getName().getString())) return false;
        } else if (entity instanceof VillagerEntity && !targets.get("Жители")) return false;
        else if (entity instanceof MobEntity || entity instanceof AnimalEntity) {
            if (!targets.get("Мобы")) return false;
        } else if (entity instanceof Monster && !targets.get("Монстры")) return false;

        return !entity.hasStatusEffect(StatusEffects.INVISIBILITY) || mc.player.canSee(entity);
    }

    private boolean isValidLockedTarget(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isAlive() || entity == mc.player) return false;
        if (entity instanceof ArmorStandEntity) return false;
        return !Manager.FUNCTION_MANAGER.antiBot.check(entity);
    }
}
