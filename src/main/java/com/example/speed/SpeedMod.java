package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
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
    private static final float SMOOTH_SPEED = 0.15f; // базовая плавность (будет переопределяться стилем)

    // === РЕЖИМЫ ===
    private static enum Mode { OFF, LEARN, PLAY }
    private static Mode mode = Mode.OFF;

    // === ЗАПИСЬ (расширенный формат) ===
    private static final Queue<float[]> recordedData = new ConcurrentLinkedQueue<>();
    private static long recordStartTime = 0;
    private static String currentSessionId = "";
    private static int sampleCount = 0;
    private static final int MAX_SAMPLES = 10000; // больше сэмплов = точнее стиль
    private static final String DATA_FILE = "killaura_style.txt";

    // === ВОСПРОИЗВЕДЕНИЕ ===
    private static List<float[]> neuralData = new ArrayList<>();
    private static int playIndex = 0;
    private static long lastPlayTime = 0;
    private static float lastYaw = 0, lastPitch = 0; // для плавного перехода

    // === СОСТОЯНИЕ ===
    private static boolean isEnabled = false;
    private static PlayerEntity lockedTarget = null;
    private static long lastAttackTime = 0;
    private static final Random random = new Random();

    // КЛАВИШИ
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;
    private static final int LEARN_KEY = GLFW.GLFW_KEY_L;
    private static final int PLAY_KEY = GLFW.GLFW_KEY_P;
    private static boolean lastR = false, lastL = false, lastP = false;

    @Override
    public void onInitialize() {
        LOGGER.info("StyleCopy KillAura (полная имитация) загружена! R - вкл/выкл, L - запись, P - воспроизведение");
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
                                    LOGGER.info("=== НАЧАЛО ЗАПИСИ СТИЛЯ ===");
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
                                    lastYaw = mc.player.getYaw();
                                    lastPitch = mc.player.getPitch();
                                    LOGGER.info("=== ВОСПРОИЗВЕДЕНИЕ СТИЛЯ ЗАПУЩЕНО (" + neuralData.size() + " сэмплов) ===");
                                } else {
                                    LOGGER.info("=== ВОСПРОИЗВЕДЕНИЕ ОСТАНОВЛЕНО ===");
                                }
                            }
                        }
                        lastR = rPressed; lastL = lPressed; lastP = pPressed;

                        if (!isEnabled) return;

                        // === ПОИСК ЦЕЛИ (ТОЛЬКО ИГРОКИ) ===
                        PlayerEntity target = getTargetPlayer();
                        if (target == null) { lockedTarget = null; return; }

                        double dist = mc.player.distanceTo(target);
                        if (dist > SEARCH_RANGE) { lockedTarget = null; return; }

                        // === РЕЖИМ ЗАПИСИ (расширенный) ===
                        if (mode == Mode.LEARN && target != null && sampleCount < MAX_SAMPLES) {
                            float[] sample = captureFullSample(target);
                            recordedData.offer(sample);
                            sampleCount++;
                            if (sampleCount % 200 == 0) {
                                LOGGER.info("Записано: " + sampleCount + " сэмплов");
                            }
                        }

                        // === РЕЖИМ ВОСПРОИЗВЕДЕНИЯ (с плавным смешиванием) ===
                        if (mode == Mode.PLAY) {
                            if (!neuralData.isEmpty()) {
                                // Используем несколько сэмплов для интерполяции
                                float[] neuron = getInterpolatedSample();
                                applyStyledNeuron(neuron, target);
                                playIndex++;
                                return;
                            } else {
                                LOGGER.warn("Нет данных стиля");
                                mode = Mode.OFF;
                                return;
                            }
                        }

                        // === ОБЫЧНАЯ АТАКА (если не PLAY/LEARN) ===
                        applySmartAttack(target, dist);

                    } catch (Exception e) {
                        LOGGER.error("Style error", e);
                    }
                });
            }
        }).start();
    }

    // ==================== РАСШИРЕННЫЙ ЗАХВАТ СЭМПЛА ====================
    private static float[] captureFullSample(LivingEntity target) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float pitch = (float) -MathHelper.atan2(dy, dist) * (180F / (float) Math.PI);

        // Текущие углы игрока
        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        // Производные (скорость изменения углов)
        float yawVel = curYaw - lastYaw;
        float pitchVel = curPitch - lastPitch;
        lastYaw = curYaw;
        lastPitch = curPitch;

        // Время с начала записи
        float time = (float) (System.currentTimeMillis() - recordStartTime) / 1000f;

        // Случайный шум (но связанный с временем)
        float noise = (float) Math.sin(time * 2.5f) * 0.05f + (random.nextFloat() - 0.5f) * 0.03f;

        return new float[]{
            yaw,                   // 0: целевой yaw
            pitch,                 // 1: целевой pitch
            (float) dist,          // 2: дистанция
            time,                  // 3: время
            curYaw,                // 4: текущий yaw
            curPitch,              // 5: текущий pitch
            yawVel,                // 6: скорость yaw
            pitchVel,              // 7: скорость pitch
            noise,                 // 8: шум
            (float) (Math.random() * 0.1f) // 9: дополнительная вариация
        };
    }

    // ==================== ИНТЕРПОЛЯЦИЯ МЕЖДУ СЭМПЛАМИ ====================
    private static float[] getInterpolatedSample() {
        if (neuralData.isEmpty()) return null;
        int idx = playIndex % neuralData.size();
        int nextIdx = (idx + 1) % neuralData.size();
        float[] curr = neuralData.get(idx);
        float[] next = neuralData.get(nextIdx);
        float t = 0.5f + 0.4f * (float) Math.sin(playIndex * 0.07f); // плавное переключение

        float[] result = new float[10];
        for (int i = 0; i < 10; i++) {
            result[i] = curr[i] + (next[i] - curr[i]) * t * 0.5f;
        }
        // Добавляем микро-рыскания, основанные на времени
        result[0] += (float) Math.sin(playIndex * 0.13f) * 0.02f;
        result[1] += (float) Math.cos(playIndex * 0.17f) * 0.02f;
        return result;
    }

    // ==================== ПРИМЕНЕНИЕ СТИЛИЗОВАННОГО НЕЙРОНА ====================
    private static void applyStyledNeuron(float[] neuron, LivingEntity target) {
        float targetYaw = neuron[0];
        float targetPitch = neuron[1];
        float dist = neuron[2];
        float time = neuron[3];
        float curYaw = neuron[4];
        float curPitch = neuron[5];
        float yawVel = neuron[6];
        float pitchVel = neuron[7];
        float noise = neuron[8];
        float extra = neuron[9];

        // Восстанавливаем скорость движения мыши (имитация твоего стиля)
        float speedFactor = 0.1f + 0.3f * (float) Math.abs(Math.sin(time * 0.5f));
        float dynamicSmooth = Math.min(1.0f, 0.05f + speedFactor);

        // Плавный поворот с учётом твоей скорости
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        // Добавляем микро-рыскания (как у живого человека)
        float microYaw = (float) Math.sin(playIndex * 0.23f) * 0.03f;
        float microPitch = (float) Math.cos(playIndex * 0.19f + 1.2f) * 0.03f;

        float finalYaw = targetYaw + noise * 0.5f + microYaw + (random.nextFloat() - 0.5f) * 0.01f;
        float finalPitch = targetPitch + noise * 0.3f + microPitch + (random.nextFloat() - 0.5f) * 0.01f;

        mc.player.setYaw(lerpAngle(currentYaw, finalYaw, dynamicSmooth));
        mc.player.setPitch(lerpAngle(currentPitch, finalPitch, dynamicSmooth));

        // === АТАКА С ВАРИАЦИЯМИ ===
        long now = System.currentTimeMillis();
        // Задержка зависит от времени записи + случайность
        float baseDelay = 0.500f + (float) Math.abs(Math.sin(time * 1.3f)) * 0.300f;
        float randomShift = (random.nextFloat() - 0.5f) * 0.150f;
        long delayMs = (long) ((baseDelay + randomShift + extra * 0.2f) * 1000);

        if (now - lastAttackTime >= delayMs && target.isAlive()) {
            double realDist = mc.player.distanceTo(target);
            if (realDist <= ATTACK_RANGE) {
                if (mc.player.isSprinting()) mc.player.setSprinting(false);
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(mc.player.getActiveHand());
                lastAttackTime = now;

                // Иногда "ошибаемся" — пропускаем удар (как человек)
                if (random.nextFloat() < 0.04f + extra * 0.1f) {
                    LOGGER.debug("[Стиль] Пропуск удара");
                    lastAttackTime = now + 150;
                }
            }
        }
    }

    // === ПОИСК ТОЛЬКО ИГРОКОВ ===
    private static PlayerEntity getTargetPlayer() {
        if (mc.player == null || mc.world == null) return null;
        Box box = mc.player.getBoundingBox().expand(SEARCH_RANGE);
        List<PlayerEntity> players = mc.world.getEntitiesByClass(
                PlayerEntity.class,
                box,
                e -> e != mc.player && e.isAlive() && !e.isDead()
        );
        players.removeIf(e -> mc.player.distanceTo(e) > SEARCH_RANGE);
        players.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        return players.isEmpty() ? null : players.get(0);
    }

    // === СОХРАНЕНИЕ ===
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
            LOGGER.info("Сохранено " + sampleCount + " сэмплов стиля");
        } catch (IOException e) {
            LOGGER.error("Ошибка сохранения", e);
        }
    }

    // === ЗАГРУЗКА ===
    private static void loadNeuralData() {
        neuralData.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(DATA_FILE), StandardCharsets.UTF_8))) {
            String line;
            boolean inSession = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("===SESSION")) { inSession = true; continue; }
                if (line.startsWith("===END===")) { inSession = false; continue; }
                if (inSession && !line.startsWith("SAMPLES:")) {
                    String[] parts = line.split(",");
                    if (parts.length >= 10) {
                        float[] sample = new float[10];
                        for (int i = 0; i < 10; i++) sample[i] = Float.parseFloat(parts[i]);
                        neuralData.add(sample);
                    }
                }
            }
            LOGGER.info("Загружено " + neuralData.size() + " сэмплов стиля");
        } catch (IOException e) {
            LOGGER.warn("Файл стиля не найден, начните запись (L)");
        }
    }

    // === ОБЫЧНАЯ АТАКА (без обучения) ===
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
