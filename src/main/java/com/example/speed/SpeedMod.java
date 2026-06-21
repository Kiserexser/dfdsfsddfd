case "Y-Port": {
                        if (event.type != EventType.MOTION_PRE) break;
                        
                        MotionEventPre motionEvent = (MotionEventPre) event;
                        boolean ground = motionEvent.getGround(), downwards = input.shiftKeyDown, upwards = input.jumping;
                        if (ground) break;
                                    
                        double currentY = motionEvent.getPos().y;
                                    
                        if (vulcanDownwards && !downwards) {
                            startHeight = PositionHelper.getMathHeight(PositionHelper.Face.DOWN, currentY, 0.015625);
                            vulcanDownwards = false;
                            vulcanResetCnt++;
                            step--;
                        }
                            
                        double motionX = motion.x, motionY = 0, motionZ = motion.z, deltaY = currentY - startHeight;
                        step++;
                                    
                        switch (step) {
                            case 1: {
                                if (!ground && deltaY < 0.073) {
                                    if (deltaY > 0 || vulcanResetCnt > 1) motionY = -deltaY;
                                    vulcanSwitch = true;
                                    break;
                                }
                                else return;
                            }
                            case 2: {
                                motionEvent.forcePacket(new ServerboundMovePlayerPacket.StatusOnly(false));
                                break;
                            }
                            case 3: {
                                lastMotionX = motionX;
                                lastMotionZ = motionZ;
                                    
                                motionY = -deltaY + (vulcanSwitch ? 0.015625 : (upwards ? 0.5 : 0.0625));
                                if (upwards) startHeight = currentY + motionY;
                                break;
                            }
                            case 4: {
                                vulcanSwitch = !vulcanSwitch;
                                motionEvent.setGround(!vulcanDownwards || !downwards);
                                
                                motionX = lastMotionX * 0.88;
                                motionY = downwards ? vulcanSwitch ? -0.097000002 : -0.147000003 : -0.097000002;
                                motionZ = lastMotionZ * 0.88;
                                        
                                vulcanDownwards = downwards;
                                if (downwards) step--;
                                else step = 1;
                                break;
                            }
                        }
                        
                        player.lerpMotion(motionX, motionY, motionZ);
                        break;
                    }
