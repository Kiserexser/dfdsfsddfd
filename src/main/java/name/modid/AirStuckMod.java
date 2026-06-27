package name.modid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AirStuckMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("airstuck");
    public static final Minecraft mc = Minecraft.getInstance();
    private static boolean enabled = false;
    private static boolean lastKeyState = false;

    public static boolean isEnabled() {
        return enabled;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("AirStuckMod (with mixins) loaded. Press X to toggle.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    if (mc.getWindow() == null || mc.player == null) return;
                    long window = mc.getWindow().getWindow();

                    boolean current = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_X) == GLFW.GLFW_PRESS;
                    if (current && !lastKeyState) {
                        enabled = !enabled;
                        if (enabled) {
                            mc.player.sendSystemMessage(Component.literal("§aAirStuck ON (mixins)"));
                            mc.player.setDeltaMovement(0, 0, 0);
                            mc.player.fallDistance = 0;
                        } else {
                            mc.player.sendSystemMessage(Component.literal("§cAirStuck OFF"));
                        }
                        LOGGER.info("AirStuck: " + (enabled ? "ON" : "OFF"));
                    }
                    lastKeyState = current;

                    if (enabled && mc.player != null) {
                        mc.player.setDeltaMovement(0, 0, 0);
                        mc.player.fallDistance = 0;
                    }
                });
            }
        }).start();
    }
}
