package name.modid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KillAura implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("killaura");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean lastKeyState = false;

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura (Teleport) loaded. Press V to teleport forward 50 blocks.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                mc.execute(() -> {
                    if (mc.getWindow() == null || mc.player == null) return;
                    long window = mc.getWindow().getHandle();
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

        float yaw = mc.player.getYaw();
        double x = -Math.sin(Math.toRadians(yaw));
        double z = Math.cos(Math.toRadians(yaw));

        Vec3d currentPos = mc.player.getPos();
        Vec3d newPos = currentPos.add(x * 50, 0, z * 50);

        mc.player.setPos(newPos.x, newPos.y, newPos.z);
        mc.player.sendMessage(Text.literal("§aTeleported 50 blocks forward!"), true);
        LOGGER.info("Teleported to " + newPos);
    }
}
