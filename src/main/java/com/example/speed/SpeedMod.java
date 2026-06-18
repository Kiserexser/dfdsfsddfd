package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");

    // === Общие ===
    private static final Random random = new Random();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // === Состояния модулей ===
    private static boolean killAuraEnabled = false;
    private static boolean elytraFlyEnabled = false;

    // === Параметры KillAura ===
    private static final double KA_RANGE = 4.5;
    private static final double KA_MIN_DELAY = 0.680;
    private static final double KA_MAX_DELAY = 0.700;
    private static final boolean KA_SPRINT_RESET = true;
    private static final float KA_SMOOTH_SPEED = 0.15f;
    private static final boolean KA_ENABLE_SHIFT = true;
    private static final float KA_SHIFT_DEGREES = 0.5f;
    private static final long KA_SHIFT_DURATION_MS = 3000;
    private static final long KA_RETURN_DURATION_MS = 2000;
    private static final float KA_JITTER_RANGE = 0.15f;

    private static long kaLastAttackTime = 0;
    private static float kaTargetYaw = 0, kaTargetPitch = 0;
    private static long kaShiftCycleStart = System.currentTimeMillis();
    private static boolean kaIsShiftPhase = true;
    private static LivingEntity kaLockedTarget = null;

    // === Параметры ElytraFly ===
    private static final Mode elytraMode = new Mode("Normal", "Normal", "Grim-2.3.69", "Grim-2.3.71");
    private static int elytraPacketCounter = 0;
    private static long elytraLastActionTime = 0;
    private static int elytraOldSlot = -1;
    private static int elytraBestSlot = -1;
    private static boolean elytraSlotNotNull = false;

    // === Дебаунс клавиш ===
    private boolean wasR = false, wasG = false;
    private boolean wasRightShift = false;

    // === Поток ===
    private Thread workerThread;
    private volatile boolean running = true;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod loaded. R=KillAura, G=ElytraFly, RightShift=GUI");

        workerThread = new Thread(() -> {
            while (running) {
                try {
                    if (mc != null && mc.getWindow() != null) {
                        long window = mc.getWindow().getHandle();

                        boolean rPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                        boolean gPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
                        boolean rightShiftPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

                        if (rPressed && !wasR) { toggleKillAura(); wasR = true; }
                        else if (!rPressed) wasR = false;

                        if (gPressed && !wasG) { toggleElytraFly(); wasG = true; }
                        else if (!gPressed) wasG = false;

                        if (rightShiftPressed && !wasRightShift) {
                            mc.execute(() -> mc.setScreen(new SpeedModGUI()));
                            wasRightShift = true;
                        } else if (!rightShiftPressed) wasRightShift = false;
                    }

                    if (mc != null && mc.player != null && mc.world != null) {
                        if (killAuraEnabled) updateKillAura();
                        if (elytraFlyEnabled) updateElytraFly();
                    }

                    Thread.sleep(10);
                } catch (InterruptedException ignored) { break; }
                catch (Exception e) { LOGGER.error("SpeedMod error", e); }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // ======================== KillAura ========================
    private static void toggleKillAura() {
        killAuraEnabled = !killAuraEnabled;
        if (!killAuraEnabled) kaLockedTarget = null;
        LOGGER.info("KillAura: " + (killAuraEnabled ? "ON" : "OFF"));
        mc.execute(() -> { if (mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f); });
    }

    private static void updateKillAura() {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        long elapsed = now - kaShiftCycleStart;
        if (kaIsShiftPhase && elapsed >= KA_SHIFT_DURATION_MS) {
            kaIsShiftPhase = false;
            kaShiftCycleStart = now;
        } else if (!kaIsShiftPhase && elapsed >= KA_RETURN_DURATION_MS) {
            kaIsShiftPhase = true;
            kaShiftCycleStart = now;
        }

        LivingEntity target = null;
        if (kaLockedTarget != null && kaLockedTarget.isAlive() && !kaLockedTarget.isDead()) {
            double dist = mc.player.distanceTo(kaLockedTarget);
            if (dist <= KA_RANGE) target = kaLockedTarget;
        }

        if (target == null) {
            kaLockedTarget = getTarget();
            target = kaLockedTarget;
        }
        if (target == null) return;

        double dist = mc.player.distanceTo(target);
        if (dist > KA_RANGE) {
            kaLockedTarget = null;
            return;
        }

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);

        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float pitch = (float) -MathHelper.atan2(dy, distance) * (180F / (float) Math.PI);

        float jitterYaw = (random.nextFloat() - 0.5f) * KA_JITTER_RANGE * 2;
        float jitterPitch = (random.nextFloat() - 0.5f) * KA_JITTER_RANGE * 2;
        float shift = 0f;
        if (KA_ENABLE_SHIFT && kaIsShiftPhase) shift = KA_SHIFT_DEGREES;

        kaTargetYaw = yaw + jitterYaw;
        kaTargetPitch = pitch + jitterPitch + shift;

        final LivingEntity finalTarget = target;
        final float finalYaw = kaTargetYaw;
        final float finalPitch = kaTargetPitch;

        mc.execute(() -> {
            if (mc.player == null) return;
            float currentYaw = mc.player.getYaw();
            float currentPitch = mc.player.getPitch();
            float newYaw = lerpAngle(currentYaw, finalYaw, KA_SMOOTH_SPEED);
            float newPitch = lerpAngle(currentPitch, finalPitch, KA_SMOOTH_SPEED);
            mc.player.setYaw(newYaw);
            mc.player.setPitch(newPitch);

            long now2 = System.currentTimeMillis();
            double delay = KA_MIN_DELAY + (KA_MAX_DELAY - KA_MIN_DELAY) * random.nextDouble();
            long delayMs = (long) (delay * 1000);
            if (now2 - kaLastAttackTime >= delayMs && finalTarget.isAlive()) {
                if (KA_SPRINT_RESET && mc.player.isSprinting()) mc.player.setSprinting(false);
                mc.interactionManager.attackEntity(mc.player, finalTarget);
                mc.player.swingHand(mc.player.getActiveHand());
                kaLastAttackTime = now2;
            }
        });
    }

    // ======================== ElytraFly ========================
    private static void toggleElytraFly() {
        elytraFlyEnabled = !elytraFlyEnabled;
        if (!elytraFlyEnabled) {
            elytraPacketCounter = 0;
        }
        LOGGER.info("ElytraFly: " + (elytraFlyEnabled ? "ON" : "OFF"));
        mc.execute(() -> { if (mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f); });
    }

    private static void updateElytraFly() {
        if (mc.player == null) return;

        // Поиск элитры и фейерверков
        int elytraSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof ElytraItem) {
                elytraSlot = i;
                break;
            }
        }

        int fireworkSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.FIREWORK_ROCKET) {
                fireworkSlot = i;
                break;
            }
        }

        // Если нет элитры или фейерверков – выключаем модуль
        if (elytraSlot == -1 || fireworkSlot == -1) {
            if (elytraFlyEnabled) {
                LOGGER.warn("ElytraFly: missing elytra or fireworks! Disabling.");
                elytraFlyEnabled = false;
            }
            return;
        }

        // Если не в полёте и не на земле – пытаемся надеть элитру и стартовать
        if (!mc.player.isOnGround() && !mc.player.isElytraFlying()) {
            // Надеваем элитру
            if (!(mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ElytraItem)) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }

        // Если в полёте – применяем обходы
        if (mc.player.isElytraFlying()) {
            String mode = elytraMode.getValue();
            if (mode.equals("Grim-2.3.69")) {
                handleGrim269();
            } else if (mode.equals("Grim-2.3.71")) {
                handleGrim271();
            } else {
                // Normal – просто используем фейерверки для ускорения
                if (mc.player.age % 10 == 0 && fireworkSlot != -1) {
                    useFirework(fireworkSlot);
                }
            }
        }
    }

    private static void handleGrim269() {
        if (mc.player.isElytraFlying()) {
            if (elytraPacketCounter % 3 == 0) {
                mc.player.setPosition(
                        mc.player.getX() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02,
                        mc.player.getY(),
                        mc.player.getZ() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02
                );
            }

            if (elytraPacketCounter % 5 == 0) {
                for (int i = 0; i < 2; i++) {
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03,
                            mc.player.getY() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02,
                            mc.player.getZ() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03,
                            false,
                            false
                    ));
                }
            }

            if (ThreadLocalRandom.current().nextFloat() < 0.2) {
                double motionX = mc.player.getVelocity().x * (0.95 + ThreadLocalRandom.current().nextDouble() * 0.1);
                double motionY = mc.player.getVelocity().y * (0.95 + ThreadLocalRandom.current().nextDouble() * 0.1);
                double motionZ = mc.player.getVelocity().z * (0.95 + ThreadLocalRandom.current().nextDouble() * 0.1);
                mc.player.setVelocity(motionX, motionY, motionZ);
            }
        }
        elytraPacketCounter++;
    }

    private static void handleGrim271() {
        if (mc.player.isElytraFlying()) {
            if (elytraPacketCounter % 2 == 0) {
                mc.player.setPosition(
                        mc.player.getX() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03,
                        mc.player.getY(),
                        mc.player.getZ() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03
                );
            }

            if (elytraPacketCounter % 4 == 0) {
                for (int i = 0; i < 3; i++) {
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.04,
                            mc.player.getY() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03,
                            mc.player.getZ() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.04,
                            false,
                            false
                    ));
                }
            }

            if (ThreadLocalRandom.current().nextFloat() < 0.3) {
                double motionX = mc.player.getVelocity().x * (0.9 + ThreadLocalRandom.current().nextDouble() * 0.2);
                double motionY = mc.player.getVelocity().y * (0.9 + ThreadLocalRandom.current().nextDouble() * 0.2);
                double motionZ = mc.player.getVelocity().z * (0.9 + ThreadLocalRandom.current().nextDouble() * 0.2);
                mc.player.setVelocity(motionX, motionY, motionZ);
            }
        }
        elytraPacketCounter++;
    }

    private static void useFirework(int slot) {
        // Временно меняем слот, используем фейерверк, возвращаем слот
        int currentSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                false,
                false
        ));
        mc.player.getInventory().selectedSlot = currentSlot;
    }

    // ======================== Вспомогательные методы ========================
    private static LivingEntity getTarget() {
        try {
            Box box = mc.player.getBoundingBox().expand(KA_RANGE);
            List<LivingEntity> entities = mc.world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e != mc.player && e.isAlive() && !e.isDead());
            entities.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
            return entities.isEmpty() ? null : entities.get(0);
        } catch (Exception e) { return null; }
    }

    private static float lerpAngle(float from, float to, float speed) {
        float diff = to - from;
        diff = (diff % 360 + 540) % 360 - 180;
        float step = diff * speed;
        if (Math.abs(step) > Math.abs(diff)) step = diff;
        return from + step;
    }

    // ======================== Mode (для ElytraFly) ========================
    private static class Mode {
        private final String[] values;
        private int index;
        private final String defaultValue;

        public Mode(String defaultValue, String... values) {
            this.defaultValue = defaultValue;
            this.values = values;
            this.index = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(defaultValue)) {
                    this.index = i;
                    break;
                }
            }
        }

        public String getValue() {
            return values[index];
        }

        public void cycle() {
            index = (index + 1) % values.length;
        }
    }

    // ======================== GUI ========================
    private static class SpeedModGUI extends Screen {
        private static final int WIDTH = 180;
        private static final int HEIGHT = 100;
        private int x, y;

        protected SpeedModGUI() {
            super(Text.literal("SpeedMod"));
        }

        @Override
        protected void init() {
            super.init();
            this.x = (this.width - WIDTH) / 2;
            this.y = (this.height - HEIGHT) / 2;

            ButtonWidget killAuraBtn = ButtonWidget.builder(
                    Text.literal("KillAura: " + (killAuraEnabled ? "§aON" : "§cOFF")),
                    btn -> toggleKillAura()
            ).dimensions(x + 10, y + 20, 160, 20).build();
            this.addDrawableChild(killAuraBtn);

            ButtonWidget elytraFlyBtn = ButtonWidget.builder(
                    Text.literal("ElytraFly: " + (elytraFlyEnabled ? "§aON" : "§cOFF")),
                    btn -> toggleElytraFly()
            ).dimensions(x + 10, y + 50, 160, 20).build();
            this.addDrawableChild(elytraFlyBtn);

            ButtonWidget closeBtn = ButtonWidget.builder(
                    Text.literal("Закрыть"),
                    btn -> this.close()
            ).dimensions(x + 55, y + HEIGHT - 25, 70, 20).build();
            this.addDrawableChild(closeBtn);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int bgColor = 0xFFFFFF;
            int borderColor = 0xFFB6C1;
            int textColor = 0xFF69B4;

            context.fill(x, y, x + WIDTH, y + HEIGHT, bgColor);
            context.fill(x, y, x + WIDTH, y + 1, borderColor);
            context.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, borderColor);
            context.fill(x, y, x + 1, y + HEIGHT, borderColor);
            context.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, borderColor);

            context.drawCenteredTextWithShadow(textRenderer, Text.literal("§dSpeedMod Controls"), x + WIDTH/2, y + 5, textColor);

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() { return false; }

        @Override
        public void close() { if (client != null) client.setScreen(null); }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
