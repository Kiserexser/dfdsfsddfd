package name.modid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastLeaveMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("fastleave");
    private static final Minecraft mc = Minecraft.getInstance();
    private static boolean lastKeyState = false;

    @Override
    public void onInitialize() {
        LOGGER.info("FastLeaveMod loaded. Press V to teleport forward 50 blocks.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                mc.execute(() -> {
                    if (mc.getWindow() == null || mc.player == null) return;
                    long window = mc.getWindow().getWindow();
                    boolean currentState = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS;
                    if (currentState && !lastKeyState) {
                        teleportForward();
                    }
                    lastKeyState = currentState;
                });
            }
        }).start();
    }

    private static void teleportForward() {
        if (mc.player == null) return;

        float yaw = mc.player.getYRot();
        double x = -Math.sin(Math.toRadians(yaw));
        double z = Math.cos(Math.toRadians(yaw));

        Vec3 currentPos = mc.player.position();
        Vec3 newPos = currentPos.add(x * 50, 0, z * 50);

        mc.player.setPos(newPos.x, newPos.y, newPos.z);
        mc.player.displayClientMessage(Component.literal("§aTeleported 50 blocks forward!"), true);
        LOGGER.info("Teleported to " + newPos);
    }
}
