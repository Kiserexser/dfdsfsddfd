package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class SpeedMod implements ModInitializer {
    public static final String MOD_ID = "speedmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean enabled = false;
    private static final double MULTIPLIER = 2.0;
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

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
                            Thread.sleep(300); // дебаунс
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
        EntityAttributeInstance speed = client.player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(MODIFIER_UUID);
        if (enabled) {
            double base = speed.getBaseValue();
            speed.addPersistentModifier(new EntityAttributeModifier(
                    MODIFIER_UUID,
                    "SpeedBoost",
                    base * MULTIPLIER - base,
                    EntityAttributeModifier.Operation.ADD_VALUE
            ));
        }
    }
}
