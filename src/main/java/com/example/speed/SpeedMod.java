package com.example.speed;

import dev.relictdlc.event.EventTarget;
import dev.relictdlc.event.events.EventPacket;
import dev.relictdlc.event.events.EventUpdate;
import dev.relictdlc.event.events.EventPostPlayerUpdate;
import dev.relictdlc.module.Module;
import dev.relictdlc.module.ModuleCategory;
import dev.relictdlc.setting.BooleanSetting;
import dev.relictdlc.setting.ModeSetting;
import dev.relictdlc.setting.NumberSetting;
import dev.relictdlc.setting.KeySetting;
import dev.relictdlc.util.ChatUtil;
import dev.relictdlc.util.MovementUtility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class Timer extends Module {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public final ModeSetting mode = addSetting(new ModeSetting("Режим", "Режим работы таймера", "Normal", "Normal", "Matrix", "Shift", "Grim"));
    public final BooleanSetting oldMatrix = addSetting(new BooleanSetting("Старый Matrix", "Старый обход для Matrix", false));
    public final NumberSetting speed = addSetting(new NumberSetting("Скорость", "Множитель таймера", 2.0, 0.1, 10.0, 0.1));
    public final NumberSetting shiftTicks = addSetting(new NumberSetting("Тики сдвига", "Количество тиков для сдвига (Shift)", 10.0, 1.0, 40.0, 1.0));
    public final KeySetting boostKey = addSetting(new KeySetting("Кнопка Grim", "Клавиша для ускорения в Grim", GLFW.GLFW_KEY_UNKNOWN));
    public final ModeSetting onFlag = addSetting(new ModeSetting("При флаге", "Действие при флаге античита", "Reset", "Reset", "Disable", "None"));

    private double tickTimer = 1.0;
    private float energy = 0.0f;
    private long cancelTime = 0;
    private long lastSetbackTime = 0;

    private static double prevPosX, prevPosY, prevPosZ;
    private static float yaw, pitch;

    // Для дебаунса клавиш переключения режимов
    private boolean wasZ = false, wasX = false, wasC = false, wasV = false;

    public Timer() {
        super("Timer", "Ускоряет время в игре", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void onEnable() {
        tickTimer = 1.0;
        if (!mode.is("Matrix")) {
            energy = 0.0f;
        }
        if (mode.is("Grim")) {
            cancelTime = System.currentTimeMillis();
        }
    }

    @Override
    protected void onDisable() {
        tickTimer = 1.0;
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        // === Переключение режимов по клавишам Z X C V ===
        long window = mc.getWindow().getHandle();
        boolean z = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
        boolean x = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_X) == GLFW.GLFW_PRESS;
        boolean c = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
        boolean v = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS;

        if (z && !wasZ) {
            mode.setValue("Normal");
            ChatUtil.message("§6Timer режим: §aNormal");
            wasZ = true;
        } else if (!z) wasZ = false;

        if (x && !wasX) {
            mode.setValue("Matrix");
            ChatUtil.message("§6Timer режим: §aMatrix");
            wasX = true;
        } else if (!x) wasX = false;

        if (c && !wasC) {
            mode.setValue("Shift");
            ChatUtil.message("§6Timer режим: §aShift");
            wasC = true;
        } else if (!c) wasC = false;

        if (v && !wasV) {
            mode.setValue("Grim");
            ChatUtil.message("§6Timer режим: §aGrim");
            wasV = true;
        } else if (!v) wasV = false;

        // === Основная логика Timer ===
        if (mode.is("Matrix")) {
            energy = MathHelper.clamp(notMoving() ? energy + 0.025f : energy - (oldMatrix.getValue() ? 0.005f : 0.0f), 0.0f, 1.0f);
        }

        prevPosX = mc.player.getX();
        prevPosY = mc.player.getY();
        prevPosZ = mc.player.getZ();
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();

        switch (mode.getValue()) {
            case "Normal" -> tickTimer = speed.getValue();
            case "Matrix" -> {
                if (!MovementUtility.isMoving()) {
                    tickTimer = 1.0;
                    return;
                }
                tickTimer = Math.max(speed.getValue(), 1.0);
                if (energy > 0) {
                    energy = MathHelper.clamp(energy - (float) ((0.1 * speed.getValue()) - 0.1), 0.0f, 1.0f);
                } else {
                    disableWithMessage("Заряд таймера кончился! Отключаю..");
                }
            }
            case "Grim" -> {
                long setBackTime = System.currentTimeMillis() - lastSetbackTime;
                if (energy <= 0 || !isKeyPressed(boostKey.getValue()) || setBackTime < 2000) {
                    tickTimer = 1.0;
                    return;
                }
                tickTimer = Math.max(speed.getValue(), 1.0);
                energy = MathHelper.clamp(energy - (float) ((0.0025 * speed.getValue()) - 0.0025), 0.0f, 1.0f);
            }
            default -> tickTimer = 1.0;
        }
    }

    @EventTarget
    public void onPacketReceive(EventPacket event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getType() == EventPacket.Type.RECEIVE) {
            if (mode.is("Grim")) {
                if (event.getPacket() instanceof CommonPingS2CPacket) {
                    long setBackTime = System.currentTimeMillis() - lastSetbackTime;
                    if (setBackTime > 2000) {
                        if (System.currentTimeMillis() - cancelTime > 25000) {
                            cancelTime = System.currentTimeMillis();
                            energy = 0.0f;
                            return;
                        }
                        if (!MovementUtility.isMoving()) {
                            energy = MathHelper.clamp(energy + 0.005f, 0.0f, 1.0f);
                        }
                        event.setCancelled(true);
                    }
                }
            }

            if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
                lastSetbackTime = System.currentTimeMillis();
                switch (onFlag.getValue()) {
                    case "Reset" -> {
                        tickTimer = 1.0;
                        energy = 0.0f;
                    }
                    case "Disable" -> {
                        energy = 0.0f;
                        disableWithMessage("Отключён т.к. тебя флагнуло!");
                    }
                }
            }

            if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velo && mode.is("Grim")) {
                if (velo.getEntityId() == mc.player.getId()) {
                    tickTimer = 1.0;
                    energy = 0.0f;
                }
            }
        }
    }

    @EventTarget
    public void onPostPlayerUpdate(EventPostPlayerUpdate event) {
        if (mode.is("Shift")) {
            if (energy < 0.9f) {
                disableWithMessage("Перед повторным использованием необходимо постоять на месте!");
                return;
            }
            event.setCancelled(true);
            event.setIterations(shiftTicks.getValueAsInt());
            disableWithMessage("Тики пропущены! Отключаем");
        }
    }

    public double getTimerSpeed() {
        return isEnabled() ? tickTimer : 1.0;
    }

    private void disableWithMessage(String message) {
        ChatUtil.warn(message);
        disable();
    }

    private static boolean notMoving() {
        if (mc.player == null) return true;
        return prevPosX == mc.player.getX()
            && prevPosY == mc.player.getY()
            && prevPosZ == mc.player.getZ()
            && yaw == mc.player.getYaw()
            && pitch == mc.player.getPitch();
    }

    private static boolean isKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return false;
        return GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
    }
}
