package com.example.speed;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.zenith.api.event.EventHandler;
import ru.zenith.api.event.types.EventType;
import ru.zenith.api.feature.module.Module;
import ru.zenith.api.feature.module.ModuleCategory;
import ru.zenith.api.feature.module.setting.implement.ValueSetting;
import ru.zenith.common.util.other.Instance;
import ru.zenith.common.util.task.TaskPriority;
import ru.zenith.implement.events.player.RotationUpdateEvent;
import ru.zenith.implement.features.modules.combat.Aura;
import ru.zenith.implement.features.modules.combat.killaura.rotation.Angle;
import ru.zenith.implement.features.modules.combat.killaura.rotation.AngleUtil;
import ru.zenith.implement.features.modules.combat.killaura.rotation.RotationConfig;
import ru.zenith.implement.features.modules.combat.killaura.rotation.RotationController;
import ru.zenith.implement.features.modules.combat.killaura.rotation.angle.SnapSmoothMode;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SpeedMod extends Module implements ModInitializer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    
    public static SpeedMod getInstance() {
        return Instance.get(SpeedMod.class);
    }
    
    ValueSetting elytraDistance = new ValueSetting("Elytra Distance", "Distance to target players (applies to Aura when enabled)")
            .setValue(15).range(1F, 100F);
    
    public SpeedMod() {
        super("SpeedMod", ModuleCategory.COMBAT);
        setup(elytraDistance);
    }
    
    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod (ElytraTarget) initialized!");
        // Модуль автоматически регистрируется системой Zenith через аннотации.
        // Если нужно вручную – раскомментируй:
        // ModuleManager.getInstance().registerModule(this);
    }
    
    @Override
    public void deactivate() {
        RotationController.INSTANCE.clear();
        super.deactivate();
    }
    
    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent e) {
        if (e.getType() != EventType.PRE) return;
        
        Aura aura = Aura.getInstance();
        if (aura == null || !aura.isState()) {
            return;
        }
        
        if (!hasElytra() || !mc.player.isGliding()) {
            return;
        }
        
        LivingEntity target = aura.getTarget();
        if (target == null) {
            return;
        }
        
        improvedTargeting(target);
    }
    
    private boolean hasElytra() {
        return mc.player != null &&
               mc.player.getInventory().getArmorStack(2).getItem() == Items.ELYTRA;
    }
    
    private void improvedTargeting(LivingEntity target) {
        if (target == null || mc.player == null) return;
        
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos();
        Vec3d targetVelocity = target.getVelocity();
        
        double distance = playerPos.distanceTo(targetPos);
        int predictionTicks = (int) Math.min(5, Math.max(2, distance / 10));
        Vec3d predictedPos = targetPos.add(targetVelocity.multiply(predictionTicks));
        
        Vec3d aimPoint = calculateOptimalAimPoint(predictedPos, target, distance);
        
        Angle targetAngle = AngleUtil.fromVec3d(aimPoint.subtract(playerPos));
        
        RotationController controller = RotationController.INSTANCE;
        RotationConfig config = new RotationConfig(new SnapSmoothMode(), true, true);
        
        controller.rotateTo(
            new Angle.VecRotation(targetAngle, targetAngle.toVector()),
            target,
            1,
            config,
            TaskPriority.HIGH_IMPORTANCE_1,
            this
        );
    }
    
    private Vec3d calculateOptimalAimPoint(Vec3d targetPos, LivingEntity target, double distance) {
        double targetHeight = target.getHeight();
        double targetWidth = target.getWidth();
        
        Vec3d basePoint = targetPos.add(0, targetHeight / 2, 0);
        
        if (distance > 10) {
            basePoint = targetPos.add(0, targetHeight * 0.6, 0);
        } else if (distance < 4) {
            basePoint = targetPos.add(0, targetHeight * 0.4, 0);
        }
        
        double randomX = (Math.random() - 0.5) * targetWidth * 0.3;
        double randomY = (Math.random() - 0.5) * targetHeight * 0.2;
        double randomZ = (Math.random() - 0.5) * targetWidth * 0.3;
        
        return basePoint.add(randomX, randomY, randomZ);
    }
    
    public boolean isActiveForElytra() {
        Aura aura = Aura.getInstance();
        return aura != null && aura.isState() && hasElytra() && mc.player != null && mc.player.isGliding();
    }
}
