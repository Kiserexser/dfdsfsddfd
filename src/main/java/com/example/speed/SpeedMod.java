package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
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

    // === РЕЖИМЫ ===
    private static enum Mode { OFF, LEARN, PLAY }
    private static Mode mode = Mode.OFF;
    private static boolean isLearned = false; // обучен ли мод (есть ли стиль)

    // === ЗАПИСЬ ===
    private static final Queue<float[]> recordedData = new ConcurrentLinkedQueue<>();
    private static long recordStartTime = 0;
    private static String currentSessionId = "";
    private static int sampleCount = 0;
    private static final int MAX_SAMPLES = 10000;
    private static final String DATA_FILE = "killaura_style.txt";

    // === ВОСПРОИЗВЕДЕНИЕ ===
    private static List<float[]> neuralData = new ArrayList<>();
    private static int playIndex = 0;
    private static float lastYaw = 0, lastPitch = 0;

    // === СОСТОЯНИЕ ===
    private static PlayerEntity lockedTarget = null;
    private static long lastAttackTime = 0;
    private static final Random random = new Random();

    // КЛАВИШИ
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_Z;      // Z – боевой режим
    private static final int LEARN_KEY = GLFW.GLFW_KEY_X;      // X – начать обучение
    private static final int STOP_LEARN_KEY = GLFW.GLFW_KEY_C; // C – остановить обучение
    private static final int RESET_KEY = GLFW.GLFW_KEY_R;      // R – сброс обучения
    private static boolean lastZ = false, lastX = false, lastC = false, lastR = false;

    @Override
    public void onInitialize() {
        LOGGER.info("StyleCopy KillAura. Z - бой (после обучения), X - учить, C - запомнить, R - забыть");

        // Загружаем данные при старте
        loadNeuralData();
        if (!neuralData.isEmpty()) {
            isLearned = true;
            LOGGER.info("Загружено обучение из файла");
        }

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    try {
                        if (mc.getWindow() == null || mc.player == null || mc.world == null) return;

                        long window = mc.getWindow().getHandle();

                        // === ЧТЕНИЕ КЛАВИШ ===
                        boolean zPressed = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
                        boolean xPressed = GLFW.glfwGetKey(window, LEARN_KEY) == GLFW.GLFW_PRESS;
                        boolean cPressed = GLFW.glfwGetKey(window, STOP_LEARN_KEY) == GLFW.GLFW_PRESS;
                        boolean rPressed = GLFW.glfwGetKey(window, RESET_KEY) == GLFW.GLFW_PRESS;

                        // R – сброс обучения (забывание)
                        if (rPressed && !lastR) {
                            resetLearning();
                            if (mc.player != null)
                                mc.player.sendMessage(Text.literal("§cОбучение сброшено! Стиль забыт. Можешь переобучить (X → играть → C)."), true);
                        }
                        lastR = rPressed;

                        // Z – боевой режим (только если обучен)
                        if (zPressed && !lastZ) {
                            if (isLearned) {
                                if (mode == Mode.PLAY) {
                                    mode = Mode.OFF;
                                    if (mc.player != null)
                                        mc.player.sendMessage(Text.literal("§cБоевой режим выключен"), true);
                                } else {
                                    mode = Mode.PLAY;
                                    playIndex = 0;
                                    lastYaw = mc.player.getYaw();
                                    lastPitch = mc.player.getPitch();
                                    if (mc.player != null)
                                        mc.player.sendMessage(Text.literal("§aБоевой режим включён (твой стиль загружен)"), true);
                                }
                            } else {
                                if (mc.player != null)
                                    mc.player.sendMessage(Text.literal("§cСначала обучи меня: нажми X, играй, затем C"), true);
                            }
                        }
                        lastZ = zPressed;

                        // X – начать обучение (очищаем старые данные, чтобы не смешивались)
                        if (xPressed && !lastX) {
                            recordedData.clear();
                            sampleCount = 0;
                            recordStartTime = System.currentTimeMillis();
                            currentSessionId = String.valueOf(System.currentTimeMillis());
                            mode = Mode.LEARN;
                            if (mc.player != null)
                                mc.player.sendMessage(Text.literal("§aЗапись стиля начата! Играй как обычно."), true);
                        }
                        lastX = xPressed;

                        // C – остановить обучение и сохранить
                        if (cPressed && !lastC) {
                            if (mode == Mode.LEARN) {
                                saveNeuralData();
                                mode = Mode.OFF;
                                isLearned = true;
                                if (mc.player != null)
                                    mc.player.sendMessage(Text.literal("§aОбучение завершено! Стиль запомнен. Теперь жми Z для боя."), true);
                            } else {
                                if (mc.player != null)
                                    mc.player.sendMessage(Text.literal("§cСейчас не идёт обучение. Нажми X для начала."), true);
                            }
                        }
                        lastC = cPressed;

                        // ====================================================
                        // ЛОГИКА РЕЖИМОВ
                        // ====================================================

                        // PLAY – атака по обученному стилю
                        if (mode == Mode.PLAY && isLearned) {
                            PlayerEntity target = getTargetPlayer();
                            if (target == null) {
                                lockedTarget = null;
                                return;
                            }
                            double dist = mc.player.distanceTo(target);
                            if (dist > SEARCH_RANGE) {
                                lockedTarget = null;
                                return;
                            }
                            lockedTarget = target;

                            if (!neuralData.isEmpty()) {
                                float[] neuron = getInterpolatedSample();
                                if (neuron != null) {
                                    applyStyledNeuron(neuron, target);
                                    playIndex++;
                                }
                            }
                            return;
                        }

                        // LEARN – запись стиля
                        if (mode == Mode.LEARN) {
                            PlayerEntity target = getTargetPlayer();
                            if (target == null) return;
                            double dist = mc.player.distanceTo(target);
                            if (dist > SEARCH_RANGE) return;

                            if (sampleCount < MAX_SAMPLES) {
                                float[] sample = captureFullSample(target);
                                recordedData.offer(sample);
                                sampleCount++;
                                if (sampleCount % 200 == 0 && mc.player != null) {
                                    mc.player.sendMessage(Text.literal("§7Записано " + sampleCount + " сэмплов"), true);
                                }
                            } else {
                                if (mc.player != null)
                                    mc.player.sendMessage(Text.literal("§cДостигнут лимит сэмплов, остановите обучение (C)"), true);
                                mode = Mode.OFF;
                            }
                            return;
                        }

                    } catch (Exception e) {
                        LOGGER.error("Error", e);
                    }
                });
            }
        }).start();
    }

    // === СБРОС ОБУЧЕНИЯ (ЗАБЫВАНИЕ) ===
    private static void resetLearning() {
        recordedData.clear();
        neuralData.clear();
        sampleCount = 0;
        isLearned = false;
        mode = Mode.OFF;
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) file.delete();
        } catch (Exception ignored) {}
        LOGGER.info("Обучение сброшено (файл удалён)");
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

    // === ЗАХВАТ СЭМПЛА (10 параметров) ===
    private static float[] captureFullSample(LivingEntity target) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float pitch = (float) -MathHelper.atan2(dy, dist) * (180F / (float) Math.PI);

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        float yawVel = curYaw - lastYaw;
        float pitchVel = curPitch - lastPitch;
        lastYaw = curYaw;
        lastPitch = curPitch;

        float time = (float) (System.currentTimeMillis() - recordStartTime) / 1000f;
        float noise = (float) Math.sin(time * 2.5f) * 0.05f + (random.nextFloat() - 0.5f) * 0.03f;

        return new float[]{
            yaw, pitch, (float) dist, time, curYaw, curPitch,
            yawVel, pitchVel, noise, (float) (Math.random() * 0.1f)
        };
    }

    // === ИНТЕРПОЛЯЦИЯ СЭМПЛОВ ===
    private static float[] getInterpolatedSample() {
        if (neuralData.isEmpty()) return null;
        int idx = playIndex % neuralData.size();
        int nextIdx = (idx + 1) % neuralData.size();
        float[] curr = neuralData.get(idx);
        float[] next = neuralData.get(nextIdx);
        float t = 0.5f + 0.4f * (float) Math.sin(playIndex * 0.07f);
        float[] result = new float[10];
        for (int i = 0; i < 10; i++) {
            result[i] = curr[i] + (next[i] - curr[i]) * t * 0.5f;
        }
        result[0] += (float) Math.sin(playIndex * 0.13f) * 0.02f;
        result[1] += (float) Math.cos(playIndex * 0.17f) * 0.02f;
        return result;
    }

    // === ПРИМЕНЕНИЕ НЕЙРОНА (имитация стиля) ===
    private static void applyStyledNeuron(float[] neuron, LivingEntity target) {
        float targetYaw = neuron[0];
        float targetPitch = neuron[1];
        float time = neuron[3];
        float yawVel = neuron[6];
        float pitchVel = neuron[7];
        float noise = neuron[8];
        float extra = neuron[9];

        float speedFactor = 0.1f + 0.3f * (float) Math.abs(Math.sin(time * 0.5f));
        float dynamicSmooth = Math.min(1.0f, 0.05f + speedFactor);

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float microYaw = (float) Math.sin(playIndex * 0.23f) * 0.03f;
        float microPitch = (float) Math.cos(playIndex * 0.19f + 1.2f) * 0.03f;

        float finalYaw = targetYaw + noise * 0.5f + microYaw + (random.nextFloat() - 0.5f) * 0.01f;
        float finalPitch = targetPitch + noise * 0.3f + microPitch + (random.nextFloat() - 0.5f) * 0.01f;

        mc.player.setYaw(lerpAngle(currentYaw, finalYaw, dynamicSmooth));
        mc.player.setPitch(lerpAngle(currentPitch, finalPitch, dynamicSmooth));

        // Атака с вариациями
        long now = System.currentTimeMillis();
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
                if (random.nextFloat() < 0.04f + extra * 0.1f) {
                    lastAttackTime = now + 150; // пропуск удара
                }
            }
        }
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
            LOGGER.info("Сохранено " + sampleCount + " сэмплов");
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
            LOGGER.info("Загружено " + neuralData.size() + " сэмплов");
            if (!neuralData.isEmpty()) isLearned = true;
        } catch (IOException ignored) {
            // файл может отсутствовать — это нормально
        }
    }

    // === ВСПОМОГАТЕЛЬНОЕ ===
    private static float lerpAngle(float from, float to, float speed) {
        float diff = to - from;
        diff = (diff % 360 + 540) % 360 - 180;
        float step = diff * speed;
        if (Math.abs(step) > Math.abs(diff)) step = diff;
        return from + step;
    }
}
