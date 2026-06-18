case "Vanilla":
    if (MoveUtil.motYstate() == 0) {
        if (MoveUtil.getdir() != -1.0F) {
            mothor = deffval;
        }
    } else if (MoveUtil.motYstate() > 0) {
        mothor = 0.0;
        motver = deffval;
        if (MoveUtil.getdir() != -1.0F) {
            motver = deffval / Math.sqrt(2.0) / divval;
            mothor = deffval / Math.sqrt(2.0) / divval;
        }
    } else if (MoveUtil.motYstate() < 0) {
        mothor = 0.0;
        motver = -deffval;
        if (MoveUtil.getdir() != -1.0F) {
            motver = -deffval / Math.sqrt(2.0) / divval;
            mothor = deffval / Math.sqrt(2.0) / divval;
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
        if (MFRotFix.isEnabled()) {
            PacketHelper.Values.sendPacket(new PositionAndOnGround(nx, pc.LastPosY, nz, false, false), 10, true);
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
                    new PositionAndOnGround(pc.LastPosX + xt * mothor, pc.LastPosY + motver, pc.LastPosZ + zt * mothor, false, false), 10, true
                );
            }

            PacketHelper.Values.sendPacket(new Full(nx, pc.LastPosY, nz, pc.LastYaw, pc.LastPitch, false, false), 10, true);
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

        if (MFRotFix.isEnabled()) {
            pc.LastYaw = this.fixedyaw;
            pc.LastPitch = this.fixedpitch;
            this.flytype = 0;
            Disabler.savedabusepacket--;
            PacketHelper.Values.sendPacket(
                new Full(pc.LastPosX, pc.LastPosY, pc.LastPosZ, pc.LastYaw, pc.LastPitch, false, false), 10, true
            );
        }

        index1++;
    }

    MoveUtil.stop3();
    TimerUtil.setTimerspeed(LowTimer.getValue());
    if (this.flydelay > 0) {
        this.flydelay--;
    }
    break;
