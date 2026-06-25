package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // === НАСТРОЙКИ ===
    private static final double SEARCH_RANGE = 5.0;
    private static final double ATTACK_RANGE = 3.0;
    private static final float SMOOTH_SPEED = 0.15f;

    // === РЕЖИМЫ ===
    private static enum Mode { OFF, LEARN, PLAY }
    private static Mode mode = Mode.OFF;

    // === ЗАПИСЬ ===
    private static final Queue<float[]> recordedData = new ConcurrentLinkedQueue<>();
    private static long recordStartTime = 0;
    private static String currentSessionId = "";
    private static int sampleCount = 0;
    private static final int MAX_SAMPLES = 5000;
    private static final String DATA_FILE = "killaura_neurons.txt";

    // === ВОСПРОИЗВЕДЕНИЕ ===
    private static List<float[]> neuralData = new ArrayList<>();
    private static int playIndex = 0;
    private static long lastPlayTime = 0;

    // === СОСТОЯНИЕ ===
    private static boolean isEnabled = false;
    private static PlayerEntity lockedTarget = null;  // ← теперь только игроки
    private static long lastAttackTime = 0;
    private static final Random random = new Random();

    // КЛАВИШИ
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;
    private static final int LEARN_KEY = GLFW.GLFW_KEY_L;
    private static final int PLAY_KEY = GLFW.GLFW_KEY_P;
    private static boolean lastR = false, lastL = false, lastP = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Neural KillAura (только игроки) загружена! R - вкл/выкл, L - запись, P - воспроизведение");
        loadNeuralData();

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    try {
                        if (mc.getWindow() == null || mc.player == null || mc.world == null) return;

                        long window = mc.getWindow().getHandle();

                        // === УПРАВЛЕНИЕ ===
                        boolean rPressed = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
                        boolean lPressed = GLFW.glfwGetKey(window, LEARN_KEY) == GLFW.GLFW_PRESS;
                        boolean pPressed = GLFW.glfwGetKey(window, PLAY_KEY) == GLFW.GLFW_PRESS;

                        if (rPressed && !lastR) { isEnabled = !isEnabled; if (!isEnabled) { mode = Mode.OFF; lockedTarget = null; } }
                        if (lPressed && !lastL) {
                            if (isEnabled) {
                                mode = (mode == Mode.LEARN) ? Mode.OFF : Mode.LEARN;
                                if (mode == Mode.LEARN) {
                                    recordedData.clear();
                                    sampleCount = 0;
                                    recordStartTime = System.currentTimeMillis();
                                    currentSessionId = String.valueOf(System.currentTimeMillis());
                                    LOGGER.info("=== НАЧАЛО ЗАПИСИ (только игроки) ===");
                                } else {
                                    saveNeuralData();
                                    LOGGER.info("=== ЗАПИСЬ ОСТАНОВЛЕНА, СОХРАНЕНО " + sampleCount + " СЭМПЛОВ ===");
                                }
                            }
                        }
                        if (pPressed && !lastP) {
                            if (isEnabled) {
                                mode = (mode == Mode.PLAY) ? Mode.OFF : Mode.PLAY;
                                if (mode == Mode.PLAY) {
                                    loadNeuralData();
                                    playIndex = 0;
                                    lastPlayTime = 0;
                                    LOGGER.info("=== ВОСПРОИЗВЕДЕНИЕ ЗАПУЩЕНО (только игроки) ===");
                                } else {
                                    LOGGER.info("=== ВОСПРОИЗВЕДЕНИЕ ОСТАНОВЛЕНО ===");
                                }
                            }
                        }
                        lastR = rPressed; lastL = lPressed; lastP = pPressed;

                        if (!isEnabled) return;

                        // === ПОИСК ЦЕЛИ (ТОЛЬКО ИГРОКИ) ===
                        PlayerEntity target = getTargetPlayer();   // ← изменённая функция
                        if (target == null) { lockedTarget = null; return; }

                        double dist = mc.player.distanceTo(target);
                        if (dist > SEARCH_RANGE) { lockedTarget = null; return; }

                        // === РЕЖИМ ЗАПИСИ ===
                        if (mode == Mode.LEARN && target != null && sampleCount < MAX_SAMPLES) {
                            float[] sample = captureSample(target);
                            recordedData.offer(sample);
                            sampleCount++;
                            if (sampleCount % 100 == 0) {
                                LOGGER.info("Записано: " + sampleCount + " сэмплов");
                            }
                        }

                        // === РЕЖИМ ВОСПРОИЗВЕДЕНИЯ ===
                        if (mode == Mode.PLAY) {
                            if (!neuralData.isEmpty()) {
                                if (playIndex >= neuralData.size()) {
                                    playIndex = 0;
                                    if (neuralData.size() > 1) {
                                        Collections.shuffle(neuralData, random);
                                    }
                                }
                                float[] neuron = neuralData.get(playIndex % neuralData.size());
                                applyNeuron(neuron, target);
                                playIndex++;
                                return;
                            } else {
                                LOGGER.warn("Нет нейроданных для воспроизведения");
                                mode = Mode.OFF;
                                return;
                            }
                        }

                        // === РЕЖИМ АТАКИ (ОБЫЧНЫЙ) ===
                        applySmartAttack(target, dist);

                    } catch (Exception e) {
                        LOGGER.error("Neural error", e);
                    }
                });
            }
        }).start();
    }

    // ==================== НОВАЯ ФУНКЦИЯ ПОИСКА ТОЛЬКО ИГРОКОВ ====================
    private static PlayerEntity getTargetPlayer() {
        if (mc.player == null || mc.world == null) return null;
        Box box = mc.player.getBoundingBox().expand(SEARCH_RANGE);
        List<PlayerEntity> players = mc.world.getEntitiesByClass(
                PlayerEntity.class,
                box,
                e -> e != mc.player && e.isAlive() && !e.isDead()
        );
        // Фильтруем по дистанции поиска
        players.removeIf(e -> mc.player.distanceTo(e) > SEARCH_RANGE);
        players.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        return players.isEmpty() ? null : players.get(0);
    }

    // === ЗАХВАТ СЭМПЛА ДЛЯ ОБУЧЕНИЯ ===
    private static float[] captureSample(LivingEntity target) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float pitch = (float) -MathHelper.atan2(dy, dist) * (180F / (float) Math.PI);

        return new float[]{
            yaw,
            pitch,
            (float) dist,
            (float) (System.currentTimeMillis() - recordStartTime) / 1000f,
            (float) mc.player.getYaw(),
            (float) mc.player.getPitch(),
            random.nextFloat() * 0.1f
        };
    }

    // === ПРИМЕНЕНИЕ НЕЙРО-СЭМПЛА ===
    private static void applyNeuron(float[] neuron, LivingEntity target) {
        float yaw = neuron[0];
        float pitch = neuron[1];
        float dist = neuron[2];
        float timeOffset = neuron[3];
        float oldYaw = neuron[4];
        float oldPitch = neuron[5];
        float noise = neuron[6];

        float variation = (random.nextFloat() - 0.5f) * 0.2f;
        float finalYaw = yaw + variation;
        float finalPitch = pitch + variation * 0.5f;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        mc.player.setYaw(lerpAngle(currentYaw, finalYaw, SMOOTH_SPEED));
        mc.player.setPitch(lerpAngle(currentPitch, finalPitch, SMOOTH_SPEED));

        long now = System.currentTimeMillis();
        long delayMs = (long) (0.500 + Math.abs(noise) * 0.500);

        if (now - lastAttackTime >= delayMs && target.isAlive()) {
            double realDist = mc.player.distanceTo(target);
            if (realDist <= ATTACK_RANGE) {
                if (mc.player.isSprinting()) mc.player.setSprinting(false);
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(mc.player.getActiveHand());
                lastAttackTime = now;
                if (random.nextFloat() < 0.05f) {
                    LOGGER.info("[Нейрон] Пропуск удара (имитация усталости)");
                    lastAttackTime = now + 200;
                }
            }
        }
    }

    // === СОХРАНЕНИЕ ДАННЫХ ===
    private static void saveNeuralData() {
        if (recordedData.isEmpty()) return;
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(DATA_FILE, true), StandardCharsets.UTF_8))) {
            writer.println("===SESSION " + currentSessionId + "===");
            writer.println("SAMPLES:" + sampleCount);
            for (float[] sample : recordedData) {
                for (float val : sample) {
                    writer.print(val + ",");
                }
                writer.println();
            }
            writer.println("===END===");
            LOGGER.info("Сохранено " + sampleCount + " сэмплов в " + DATA_FILE);
        } catch (IOException e) {
            LOGGER.error("Ошибка сохранения", e);
        }
    }

    // === ЗАГРУЗКА ДАННЫХ ===
    private static void loadNeuralData() {
        neuralData.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(DATA_FILE), StandardCharsets.UTF_8))) {
            String line;
            boolean inSession = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("===SESSION")) {
                    inSession = true;
                    continue;
                }
                if (line.startsWith("===END===")) {
                    inSession = false;
                    continue;
                }
                if (inSession && !line.startsWith("SAMPLES:")) {
                    String[] parts = line.split(",");
                    if (parts.length >= 7) {
                        float[] sample = new float[7];
                        for (int i = 0; i < 7; i++) {
                            sample[i] = Float.parseFloat(parts[i]);
                        }
                        neuralData.add(sample);
                    }
                }
            }
            LOGGER.info("Загружено " + neuralData.size() + " нейро-сэмплов (только игроки)");
        } catch (IOException e) {
            LOGGER.warn("Файл данных не найден, начните с записи (L)");
        }
    }

    // === ОБЫЧНАЯ АТАКА (ТОЛЬКО ПО ИГРОКАМ) ===
    private static void applySmartAttack(LivingEntity target, double dist) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float pitch = (float) -MathHelper.atan2(dy, distance) * (180F / (float) Math.PI);

        float jitterYaw = (random.nextFloat() - 0.5f) * 0.15f * 2;
        float jitterPitch = (random.nextFloat() - 0.5f) * 0.15f * 2;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        mc.player.setYaw(lerpAngle(currentYaw, yaw + jitterYaw, SMOOTH_SPEED));
        mc.player.setPitch(lerpAngle(currentPitch, pitch + jitterPitch, SMOOTH_SPEED));

        long now = System.currentTimeMillis();
        double delay = 0.760 + (0.800 - 0.760) * random.nextDouble();
        if (now - lastAttackTime >= (long)(delay * 1000) && target.isAlive() && dist <= ATTACK_RANGE) {
            if (mc.player.isSprinting()) mc.player.setSprinting(false);
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(mc.player.getActiveHand());
            lastAttackTime = now;
        }
    }

    private static float lerpAngle(float from, float to, float speed) {
        float diff = to - from;
        diff = (diff % 360 + 540) % 360 - 180;
        float step = diff * speed;
        if (Math.abs(step) > Math.abs(diff)) step = diff;
        return from + step;
    }
}
