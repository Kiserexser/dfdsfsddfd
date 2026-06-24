private void handleGriefCarpet() {
    if (mc.player == null || mc.world == null) return;
    
    // 1. Поиск ковра в хотбаре
    int carpetSlot = -1;
    for (int i = 0; i < 9; i++) {
        ItemStack stack = mc.player.getInventory().getStack(i);
        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
            if (((BlockItem) stack.getItem()).getBlock() instanceof CarpetBlock) {
                carpetSlot = i;
                break;
            }
        }
    }
    
    if (carpetSlot == -1) return;
    
    BlockPos playerPos = mc.player.getBlockPos();
    BlockPos blockUnder = playerPos.down();
    
    // 2. Установка ковра в воздухе
    if (!mc.player.isOnGround() && mc.world.isAir(playerPos) && !mc.world.isAir(blockUnder)) {
        int prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = carpetSlot;
        
        float prevPitch = mc.player.getPitch();
        mc.player.setPitch(90f);
        
        // Создаём BlockHitResult
        Vec3d hitVec = new Vec3d(blockUnder.getX() + 0.5, blockUnder.getY() + 0.5, blockUnder.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, blockUnder, false);
        
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
        
        mc.player.setPitch(prevPitch);
        mc.player.getInventory().selectedSlot = prevSlot;
    }
    
    // 3. Высокий прыжок с ковра
    if (mc.player.isOnGround()) {
        BlockState state = mc.world.getBlockState(playerPos);
        if (state.getBlock() instanceof CarpetBlock) {
            // Отключаем замедление при прыжке
            mc.player.jumpMovementFactor = 0f;
            
            // Отправляем фейковый пакет
            if (mc.player.networkHandler != null) {
                // В 1.21.4 пакет называется CPlayerActionC2SPacket
                // Это для примера, нужна адаптация
            }
            
            mc.player.jump();
            
            double motionX = mc.player.getVelocity().x;
            double motionZ = mc.player.getVelocity().z;
            mc.player.setVelocity(motionX, 0.55, motionZ);
        }
    }
}
