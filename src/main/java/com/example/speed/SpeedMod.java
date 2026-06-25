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
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final double SEARCH_RANGE = 5.0;
    private static final double ATTACK_RANGE = 3.0;
    private static final int MIN_SAMPLES = 50;
    private static final String FILE_PREFIX = "killaura_style_";
    private static final String FILE_EXT = ".txt";

    private static enum Mode { OFF, LEARN, PLAY }
    private static Mode mode = Mode.OFF;
    private static boolean isLearned = false;

    private static final Queue<float[]> recordedData = new ConcurrentLinkedQueue<>();
    private static long recordStartTime = 0;
    private static String currentSessionId = "";
    private static int sampleCount = 0;
    private static final int MAX_SAMPLES = 10000;

    private static List<float[]> neuralData = new ArrayList<>();
    private static int playIndex = 0;
    private static float lastYaw = 0, lastPitch = 0;
    private static long lastAttackTime = 0;
    private static final Random random = new Random();

    private static PlayerEntity lockedTarget = null;

    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_R;
    private static final int RESET_KEY = GLFW.GLFW_KEY_Z;
    private static final int LEARN_KEY = GLFW.GLFW_KEY_X;
    private static final int STOP_LEARN_KEY = GLFW.GLFW_KEY_C;

    private static boolean lastR = false, lastZ = false, lastX = false, lastC = false;

    private static Path getSessionDir() { return mc.runDirectory.toPath(); }

    private static String generateFileName() {
        return FILE_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + FILE_EXT;
    }

    private static Path getLatestSessionFile() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(getSessionDir(), FILE_PREFIX + "*" + FILE_EXT)) {
            Path latest = null;
            long latestTime = 0;
            for (Path entry : stream) {
                long modTime = Files.getLastModifiedTime(entry).toMillis();
                if (modTime > latestTime) {
                    latestTime = modTime;
                    latest = entry;
                }
            }
            return latest;
        } catch (IOException e) {
            LOGGER.error("Ошибка поиска файлов сессий", e);
            return null;
        }
    }

    private static void loadNeuralDataFromFile(Path file) {
        if (file == null || !Files.exists(file)) return;
        neuralData.clear();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean inSession = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("===SESSION")) { inSession = true; continue; }
                if (line.startsWith("===END===")) { inSession = false; continue; }
                if (inSession && !line.startsWith("SAMPLES:")) {
                    String[] parts = line.split(",");
                    if (parts.length >= 8) {
                        float[] sample = new float[8];
                        for (int i = 0; i < 8; i++) sample[i] = Float.parseFloat(parts[i]);
                        neuralData.add(sample);
                    }
                }
            }
            LOGGER.info("Загружено " + neuralData.size() + " сэмплов из " + file.getFileName());
        } catch (IOException e) {
            LOGGER.error("Ошибка загрузки файла", e);
        }
    }

    private static void saveNeuralDataToNewFile() {
        if (recordedData.isEmpty()) return;
        Path newFile = getSessionDir().resolve(generateFileName());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(newFile, StandardCharsets.UTF_8))) {
            writer.println("===SESSION " + currentSessionId + "===");
            writer.println("SAMPLES:" + sampleCount);
            for (float[] sample : recordedData) {
                for (float val : sample) {
                    writer.print(val + ",");
                }
                writer.println();
            }
            writer.println("===END===");
            LOGGER.info("Сохранено " + sampleCount + " сэмплов в " + newFile.getFileName());
        } catch (IOException e) {
            LOGGER.error("Ошибка сохранения", e);
        }
    }

    @Override
    public void onInitialize() {
        LOGGER.info("KillAura (фиксация, без интерполяции смещений) R - бой, X - учить, C - сохранить, Z - забыть");
        Path latestFile = getLatestSessionFile();
        if (latestFile != null) {
            loadNeuralDataFromFile(latestFile);
            if (!neuralData.isEmpty()) {
                isLearned = true;
                LOGGER.info("Загружено обучение из файла: " + latestFile.getFileName());
            }
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

                        if (zPressed && !lastZ) {
                            resetLearning();
                            if (mc.player != null) mc.player.sendMessage(Text.literal("§cСтиль забыт (файлы сохранены)."), true);
                        }
                        lastZ = zPressed;

                        if (rPressed && !lastR) {
                            if (isLearned) {
                                if (mode == Mode.PLAY) {
                                    mode = Mode.OFF;
                                    lockedTarget = null;
                                    if (mc.player != null) mc.player.sendMessage(Text.literal("§cKillAura выключена"), true);
                                } else {
                                    if (neuralData.isEmpty()) {
                                        Path latest = getLatestSessionFile();
                                        if (latest != null) {
                                            loadNeuralDataFromFile(latest);
                                            if (!neuralData.isEmpty()) {
                                                isLearned = true;
                                                if (mc.player != null)
                                                    mc.player.sendMessage(Text.literal("§aЗагружен последний стиль: " + latest.getFileName()), true);
                                            }
                                        }
                                    }
                                    if (!neuralData.isEmpty()) {
                                        mode = Mode.PLAY;
                                        playIndex = 0;
                                        lastYaw = mc.player.getYaw();
                                        lastPitch = mc.player.getPitch();
                                        lockedTarget = null;
                                        if (mc.player != null) mc.player.sendMessage(Text.literal("§aKillAura включена (твой стиль)"), true);
                                    } else {
                                        if (mc.player != null) mc.player.sendMessage(Text.literal("§cНет сохранённого стиля."), true);
                                        isLearned = false;
                                    }
                                }
                            } else {
                                Path latest = getLatestSessionFile();
                                if (latest != null) {
                                    loadNeuralDataFromFile(latest);
                                    if (!neuralData.isEmpty()) {
                                        isLearned = true;
                                        if (mc.player != null) mc.player.sendMessage(Text.literal("§aЗагружен последний стиль: " + latest.getFileName()), true);
                                        mode = Mode.PLAY;
                                        playIndex = 0;
                                        lastYaw = mc.player.getYaw();
                                        lastPitch = mc.player.getPitch();
                                        lockedTarget = null;
                                        if (mc.player != null) mc.player.sendMessage(Text.literal("§aKillAura включена"), true);
                                    } else {
                                        if (mc.player != null) mc.player.sendMessage(Text.literal("§cНет сохранённого стиля. Пройди обучение (X → бой → C)."), true);
                                    }
                                } else {
                                    if (mc.player != null) mc.player.sendMessage(Text.literal("§cСначала обучи: X → бой → C"), true);
                                }
                            }
                        }
                        lastR = rPressed;

                        if (xPressed && !lastX) {
                            recordedData.clear();
                            sampleCount = 0;
                            recordStartTime = System.currentTimeMillis();
                            currentSessionId = String.valueOf(System.currentTimeMillis());
                            mode = Mode.LEARN;
                            lockedTarget = null;
                            if (mc.player != null) mc.player.sendMessage(Text.literal("§aЗапись начата! Играй как обычно."), true);
                        }
                        lastX = xPressed;

                        if (cPressed && !lastC) {
                            if (mode == Mode.LEARN) {
                                if (sampleCount < MIN_SAMPLES) {
                                    if (mc.player != null) mc.player.sendMessage(Text.literal("§cСлишком мало данных!"), true);
                                    mode = Mode.OFF;
                                } else {
                                    saveNeuralDataToNewFile();
                                    mode = Mode.OFF;
                                    isLearned = true;
                                    lockedTarget = null;
                                    Path latest = getLatestSessionFile();
                                    if (latest != null) loadNeuralDataFromFile(latest);
                                    if (mc.player != null) mc.player.sendMessage(Text.literal("§aСтиль сохранён! Жми R."), true);
                                }
                            } else {
                                if (mc.player != null) mc.player.sendMessage(Text.literal("§cНажми X для обучения."), true);
                            }
                        }
                        lastC = cPressed;

                        // === PLAY ===
                        if (mode == Mode.PLAY && isLearned && !neuralData.isEmpty()) {
                            PlayerEntity target = lockedTarget;
                            if (target == null || target.isDead() || !target.isAlive() || mc.player.distanceTo(target) > SEARCH_RANGE) {
                                target = getTargetPlayer();
                                if (target != null) lockedTarget = target;
                                else { lockedTarget = null; return; }
                            }

                            double dist = mc.player.distanceTo(target);
                            if (dist > SEARCH_RANGE) { lockedTarget = null; return; }

                            // Берём текущий сэмпл без интерполяции смещения, используем точное значение
                            if (playIndex >= neuralData.size()) playIndex = 0;
                            float[] neuron = neuralData.get(playIndex);
                            if (neuron != null) {
                                applyStyledNeuronDirect(neuron, target);
                                playIndex++;
                            }
                            return;
                        }

                        // === LEARN ===
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
                                if (mc.player != null) mc.player.sendMessage(Text.literal("§cЛимит, нажми C"), true);
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
        lockedTarget = null;
        LOGGER.info("Стиль забыт (файлы сохранены)");
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

        float idealYaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float idealPitch = (float) -MathHelper.atan2(dy, dist) * (180F / (float) Math.PI);

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        float offsetYaw = curYaw - idealYaw;
        float offsetPitch = curPitch - idealPitch;

        float yawSpeed = curYaw - lastYaw;
        float pitchSpeed = curPitch - lastPitch;
        lastYaw = curYaw;
        lastPitch = curPitch;

        float time = (float) (System.currentTimeMillis() - recordStartTime) / 1000f;

        return new float[]{
            offsetYaw, offsetPitch, (float) dist, time,
            yawSpeed, pitchSpeed, 0f, 0f
        };
    }

    // Прямое применение сэмпла без интерполяции смещения – для точных попаданий при беге
    private static void applyStyledNeuronDirect(float[] neuron, LivingEntity target) {
        if (neuron == null || target == null) return;
        if (!(target instanceof PlayerEntity)) return;

        float offsetYaw = neuron[0];
        float offsetPitch = neuron[1];
        float time = neuron[3];
        float yawSpeed = neuron[4];
        float pitchSpeed = neuron[5];

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float idealPitch = (float) -MathHelper.atan2(dy, dist) * (180F / (float) Math.PI);

        // Целевой угол без сглаживания смещения – точное попадание
        float targetYaw = idealYaw + offsetYaw;
        float targetPitch = idealPitch + offsetPitch;

        // Плавность поворота из записи (без интерполяции смещения)
        float smooth = 0.05f + 0.3f * (float) Math.abs(Math.sin(time * 0.5f));
        float dynamicSmooth = Math.min(1.0f, smooth);

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        mc.player.setYaw(lerpAngle(currentYaw, targetYaw, dynamicSmooth));
        mc.player.setPitch(lerpAngle(currentPitch, targetPitch, dynamicSmooth));

        long now = System.currentTimeMillis();
        float baseDelay = 0.500f + 0.250f * (float) Math.abs(Math.sin(time * 1.3f));
        long delayMs = (long) (baseDelay * 1000);

        if (now - lastAttackTime >= delayMs && target.isAlive() && !target.isDead()) {
            double realDist = mc.player.distanceTo(target);
            if (realDist <= ATTACK_RANGE && realDist > 0.1) {
                if (mc.player.isSprinting()) mc.player.setSprinting(false);
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(mc.player.getActiveHand());
                lastAttackTime = now;
            }
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
