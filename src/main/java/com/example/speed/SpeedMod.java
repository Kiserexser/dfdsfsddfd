package com.example.speed;

import dev.relictdlc.module.Module;
import dev.relictdlc.module.ModuleCategory;
import dev.relictdlc.setting.ModeSetting;
import dev.relictdlc.setting.NumberSetting;
import dev.relictdlc.event.events.EventUpdate;
import dev.relictdlc.event.EventTarget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import org.lwjgl.glfw.GLFW;

public class SpeedMod extends Module {

    // Настройки (как в твоём HighJump)
    public final ModeSetting mode = addSetting(new ModeSetting("Режим", "Режим полёта", "Vanilla", "Vanilla", "Grim", "Vulcan"));
    public final NumberSetting speed = addSetting(new NumberSetting("Скорость", "Скорость полёта", 1.0, 0.1, 5.0, 0.1));
    public final NumberSetting deffval = addSetting(new NumberSetting("Значение", "Базовое значение", 0.5, 0.1, 2.0, 0.05));
    public final NumberSetting divval = addSetting(new NumberSetting("Делитель", "Делитель для диагонали", 1.0, 0.5, 2.0, 0.05));
    public final NumberSetting MFPacketCount = addSetting(new NumberSetting("Пакеты", "Количество пакетов", 20, 1, 50, 1));
    public final NumberSetting LowTimer = addSetting(new NumberSetting("Таймер", "Скорость таймера", 1.0, 0.1, 2.0, 0.05));
    public final ModeSetting MFRotFix = addSetting(new ModeSetting("RotFix", "Фикс поворота", "On", "On", "Off"));

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private int flydelay = 0;
    private int index1 = 0;
    private double mothor = 0, motver = 0;
    private double nx = 0, nz = 0;
    private float fixedyaw = 0, fixedpitch = 0;
    private int flytype = 0;
    private int LastTpNum = 0;
    private double xt = 0, zt = 0;

    // Эти объекты должны быть в твоём проекте – я их просто объявляю
    private final Object pc = new Object(); // замени на свой класс PlayerController
    private final Object client = Client.instance; // если есть Client.instance
    private final Object disabler = new Object(); // заглушка

    public SpeedMod() {
        super("SpeedMod", "Полёт с обходом Vanilla", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onDisable() {
        flydelay = 0;
        index1 = 0;
        mothor = 0;
        motver = 0;
        MoveUtil.stop3();
        TimerUtil.setTimerspeed(1.0f);
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.player == null) return;

        String currentMode = mode.getValue();
        switch (currentMode) {
            case "Vanilla":
                // ====== ТВОЙ ОРИГИНАЛЬНЫЙ КОД (без изменений) ======
                if (MoveUtil.motYstate() == 0) {
                    if (MoveUtil.getdir() != -1.0F) {
                        mothor = deffval.getValue();
                    }
                } else if (MoveUtil.motYstate() > 0) {
                    mothor = 0.0;
                    motver = deffval.getValue();
                    if (MoveUtil.getdir() != -1.0F) {
                        motver = deffval.getValue() / Math.sqrt(2.0) / divval.getValue();
                        mothor = deffval.getValue() / Math.sqrt(2.0) / divval.getValue();
                    }
                } else if (MoveUtil.motYstate() < 0) {
                    mothor = 0.0;
                    motver = -deffval.getValue();
                    if (MoveUtil.getdir() != -1.0F) {
                        motver = -deffval.getValue() / Math.sqrt(2.0) / divval.getValue();
                        mothor = deffval.getValue() / Math.sqrt(2.0) / divval.getValue();
                    }
                }

                int tries = (int) MFPacketCount.getValue();
                if (pc.lastTptimer.hasTimeElapsed(4000L, false)) {
                    this.flydelay = 3;
                }

                if (this.flydelay > 0) {
                    tries = 1;
                    mothor = 0.0;
                    motver = 0.0;
                } else {
                    tries = (int) MFPacketCount.getValue();
                }

                if ((mothor != 0.0 || motver != 0.0 || this.flydelay > 0) && Client.instance.flagsch.getFlags().size() <= 0) {
                    if (MFRotFix.getValue().equals("On")) {
                        PacketHelper.Values.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(nx, pc.LastPosY, nz, false), 10, true);
                        pc.LastTpNum++;
                        PacketHelper.Values.sendPacket(new TeleportConfirmC2SPacket(pc.LastTpNum));
                        Client.instance
                            .flagsch
                            .getFlags()
                            .addFirst(new FlagHelper.SkippedFlag(pc.LastPosX, pc.LastPosY, pc.LastPosZ, pc.LastTpNum, false, false));
                    }

                    pc.lastTptimer.reset();

                    for (int ix = 0; ix < tries; ix++) {
                        nx = pc.LastPosX + 90.0 + Math.random() * 90.0;
                        nz = pc.LastPosZ + 90.0 + Math.random() * 90.0;
                        if (index1 > 0) {
                            PacketHelper.Values.sendPacket(
                                new PlayerMoveC2SPacket.PositionAndOnGround(pc.LastPosX + xt * mothor, pc.LastPosY + motver, pc.LastPosZ + zt * mothor, false), 10, true
                            );
                        }

                        PacketHelper.Values.sendPacket(new PlayerMoveC2SPacket.Full(nx, pc.LastPosY, nz, pc.LastYaw, pc.LastPitch, false), 10, true);
                        pc.LastTpNum++;
                        PacketHelper.Values.sendPacket(new TeleportConfirmC2SPacket(pc.LastTpNum));
                        Client.instance
                            .flagsch
                            .getFlags()
                            .addFirst(new FlagHelper.SkippedFlag(pc.LastPosX, pc.LastPosY, pc.LastPosZ, pc.LastTpNum, false, false));
                        if (index1 > 0) {
                            mc.player.setPosition(pc.LastPosX + xt * mothor, pc.LastPosY + motver, pc.LastPosZ + zt * mothor);
                            pc.LastPosX = mc.player.getX();
                            pc.LastPosY = mc.player.getY();
                            pc.LastPosZ = mc.player.getZ();
                        }

                        Disabler.savedabusepacket--;
                    }

                    if (MFRotFix.getValue().equals("On")) {
                        pc.LastYaw = this.fixedyaw;
                        pc.LastPitch = this.fixedpitch;
                        this.flytype = 0;
                        Disabler.savedabusepacket--;
                        PacketHelper.Values.sendPacket(
                            new PlayerMoveC2SPacket.Full(pc.LastPosX, pc.LastPosY, pc.LastPosZ, pc.LastYaw, pc.LastPitch, false), 10, true
                        );
                    }

                    index1++;
                }

                MoveUtil.stop3();
                TimerUtil.setTimerspeed((float) LowTimer.getValue());
                if (this.flydelay > 0) {
                    this.flydelay--;
                }
                break;

            case "Grim":
                // можно добавить другой обход
                break;
            case "Vulcan":
                // можно добавить другой обход
                break;
            default:
                break;
        }
    }
}
