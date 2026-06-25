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

    private static final double SEARCH_RANGE = 5.0;
    private static final double ATTACK_RANGE = 3.0;
    private static final int MIN_SAMPLES = 50; // минимальное количество сэмплов для обучения

    private static enum Mode { OFF, LEARN, PLAY }
    private static Mode mode = Mode.OFF;
    private static boolean isLearned = false;

    private static final Queue<float[]> recordedData = new ConcurrentLinkedQueue<>();
    private static long recordStartTime = 0;
    private static String currentSessionId = "";
    private static int sampleCount = 0;
    private static final int MAX_SAMPLES = 10000;
    private static final String DATA_FILE = "killaura_style.txt";

    private static List<float[]> neuralData = new ArrayList<>();
    private static int playIndex = 0;
    private static float lastYaw = 0, lastPitch = 0;

    private static PlayerEntity lockedTarget = null;
    private static long lastAttackTime = 0;
    private static final Random random = new Random();

    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;
    private static final int RESET_KEY = GLFW.GLFW_KEY_Z;
    private static final int LEARN_KEY = GLFW.GLFW_KEY_X;
    private static final int STOP_LEARN_KEY = GLFW.GLFW_KEY_C;

    private static boolean lastR = false, lastZ = false, lastX = false, lastC = false;

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura: R - вкл (после обучения), X - учить, C - запомнить, Z - забыть");
        loadNeuralData();
        if (!neuralData.isEmpty()) {
            isLearned = true;
            LOGGER.info("Загружено обучение из файла");
        }

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                mc.execute(() -> {
                    try {
                        if (mc.getWindow() == null || mc.player == null || mc.world == null) return;
                        long window = mc.getWindow().getHandle();

                        boolean rPressed = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
                        boolean zPressed = GLFW.glfwGetKey(window, RESET_KEY) == GLFW.GLFW_PRESS;
                        boolean xPressed = GLFW.glfwGetKey(window, LEARN_KEY) == GLFW.GLFW_PRESS;
                        boolean cPressed = GLFW.glfwGetKey(window, STOP_LEARN_KEY) == GLFW.GLFW_PRESS;

                        // Z – сброс
                        if (zPressed && !lastZ) {
                            resetLearning();
                            if (mc.player != null)
                                mc.player.sendMessage(Text.literal("§cОбучение сброшено!"), true);
                        }
                        lastZ = zPressed;

                        // R – боевой режим
                        if (rPressed && !lastR) {
                            if (isLearned) {
                                if (mode == Mode.PLAY) {
                                    mode = Mode.OFF;
                                    if (mc.player != null)
                                        mc.player.sendMessage(Text.literal("§cKillAura выключена"), true);
                                } else {
                                    if (neuralData.isEmpty()) {
                                        if (mc.player != null)
                                            mc.player.sendMessage(Text.literal("§cОшибка: данные обучения отсутствуют. Пройди обучение заново."), true);
                                        isLearned = false;
                                    } else {
                                        mode = Mode.PLAY;
                                        playIndex = 0;
                                        lastYaw = mc.player.getYaw();
                                        lastPitch = mc.player.getPitch();
                                        if (mc.player != null)
                                            mc.player.sendMessage(Text.literal("§aKillAura включена (твой стиль)"), true);
                                    }
                                }
                            } else {
                                if (mc.player != null)
                                    mc.player.sendMessage(Text.literal("§cСначала обучи: X → бой → C"), true);
                            }
                        }
                        lastR = rPressed;

                        // X – начать обучение
                        if (xPressed && !lastX) {
                            recordedData.clear();
                            sampleCount = 0;
                            recordStartTime = System.currentTimeMillis();
                            currentSessionId = String.valueOf(System.currentTimeMillis());
                            mode = Mode.LEARN;
                            if (mc.player != null)
                                mc.player.sendMessage(Text.literal("§aЗапись начата! Играй."), true);
                        }
                        lastX = xPressed;

                        // C – сохранить обучение
                        if (cPressed && !lastC) {
                            if (mode == Mode.LEARN) {
                                if (sampleCount < MIN_SAMPLES) {
                                    if (mc.player != null)
                                        mc.player.sendMessage(Text.literal("§cСлишком мало данных! Нанеси больше ударов."), true);
                                    mode = Mode.OFF;
                                } else {
                                    saveNeuralData();
                                    mode = Mode.OFF;
                                    isLearned = true;
                                    if (mc.player != null)
                                        mc.player.sendMessage(Text.literal("§aОбучение сохранено! Жми R для боя."), true);
                                }
                            } else {
                                if (mc.player != null)
                                    mc.player.sendMessage(Text.literal("§cСейчас не идёт обучение. Нажми X."), true);
                            }
                        }
                        lastC = cPressed;

                        // === ЛОГИКА РЕЖИМОВ ===
                        if (mode == Mode.PLAY && isLearned && !neuralData.isEmpty()) {
                            PlayerEntity target = getTargetPlayer();
                            if (target == null) { lockedTarget = null; return; }
                            double dist = mc.player.distanceTo(target);
                            if (dist > SEARCH_RANGE) { lockedTarget = null; return; }
                            lockedTarget = target;

                            float[] neuron = getInterpolatedSample();
                            if (neuron != null) {
                                applyStyledNeuron(neuron, target);
                                playIndex++;
                            }
                            return;
                        }

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
                                    mc.player.sendMessage(Text.literal("§cЛимит, нажми C"), true);
                                mode = Mode.OFF;
                            }
                        }

                    } catch (Exception e) {
                        LOGGER.error("Error", e);
                    }
                });
            }
        }).start();
    }

    private static void resetLearning() {
        recordedData.clear();
        neuralData.clear();
        sampleCount = 0;
        isLearned = false;
        mode = Mode.OFF;
        try { new File(DATA_FILE).delete(); } catch (Exception ignored) {}
        LOGGER.info("Обучение сброшено");
    }

    private static PlayerEntity getTargetPlayer() {
        if (mc.player == null || mc.world == null) return null;
        Box box = mc.player.getBoundingBox().expand(SEARCH_RANGE);
        List<PlayerEntity> players = mc.world.getEntitiesByClass(PlayerEntity.class, box,
                e -> e != mc.player && e.isAlive() && !e.isDead());
        players.removeIf(e -> mc.player.distanceTo(e) > SEARCH_RANGE);
        players.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        return players.isEmpty() ? null : players.get(0);
    }

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

    private static void applyStyledNeuron(float[] neuron, LivingEntity target) {
        if (neuron == null) return;
        float targetYaw = neuron[0];
        float targetPitch = neuron[1];
        float time = neuron[3];
        float extra = neuron[9];

        float speedFactor = 0.1f + 0.3f * (float) Math.abs(Math.sin(time * 0.5f));
        float dynamicSmooth = Math.min(1.0f, 0.05f + speedFactor);

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float microYaw = (float) Math.sin(playIndex * 0.23f) * 0.03f;
        float microPitch = (float) Math.cos(playIndex * 0.19f + 1.2f) * 0.03f;

        float finalYaw = targetYaw + (random.nextFloat() - 0.5f) * 0.01f + microYaw;
        float finalPitch = targetPitch + (random.nextFloat() - 0.5f) * 0.01f + microPitch;

        mc.player.setYaw(lerpAngle(currentYaw, finalYaw, dynamicSmooth));
        mc.player.setPitch(lerpAngle(currentPitch, finalPitch, dynamicSmooth));

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
                    lastAttackTime = now + 150;
                }
            }
        }
    }

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
        } catch (IOException ignored) {}
    }

    private static float lerpAngle(float from, float to, float speed) {
        float diff = to - from;
        diff = (diff % 360 + 540) % 360 - 180;
        float step = diff * speed;
        if (Math.abs(step) > Math.abs(diff)) step = diff;
        return from + step;
    }
}
