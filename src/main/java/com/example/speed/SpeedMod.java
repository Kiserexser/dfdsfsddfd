package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final String MOD_ID = "speedmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean enabled = false;
    private static final double MULTIPLIER = 2.0;
    private static final Identifier MODIFIER_ID = Identifier.of("speedmod", "boost");

    private Thread keyThread;
    private volatile boolean running = true;

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod loaded. Press R to toggle.");

        keyThread = new Thread(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            while (running) {
                try {
                    if (client != null && client.player != null && client.getWindow() != null) {
                        long window = client.getWindow().getHandle();
                        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) {
                            enabled = !enabled;
                            updateSpeed(client);
                            LOGGER.info("SpeedMod: " + (enabled ? "ON" : "OFF"));
                            Thread.sleep(300);
                        }
                    }
                    Thread.sleep(50);
                } catch (Exception ignored) {}
            }
        });
        keyThread.setDaemon(true);
        keyThread.start();
    }

    private void updateSpeed(MinecraftClient client) {
        if (client.player == null) return;
        EntityAttributeInstance speed = client.player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed == null) return;

        speed.removeModifier(MODIFIER_ID);

        if (enabled) {
            double base = speed.getBaseValue();
            double added = base * MULTIPLIER - base;
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                    MODIFIER_ID,
                    added,
                    EntityAttributeModifier.Operation.ADD_VALUE
            );
            speed.addPersistentModifier(modifier);
        }
    }
}
