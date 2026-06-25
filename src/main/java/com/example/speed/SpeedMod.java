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

    // === РАБОТА С ФАЙЛАМИ ===
    private static Path getSessionDir() {
        return mc.runDirectory.toPath();
    }

    private static String generateFileName() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        return FILE_PREFIX + timestamp + FILE_EXT;
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
        LOGGER.info("🎯 FULL-STYLE KillAura: R - бой, X - учить, C - сохранить, Z - забыть");
        Path latestFile = getLatestSessionFile();
        if (latestFile != null) {
            loadNeuralDataFromFile(latestFile);
            if (!neuralData.isEmpty()) {
                isLearned = true;
                LOGGER.info("✅ Загружено обучение из файла: " + latestFile.getFileName());
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

                        // Z – забыть стиль (файлы не удаляются)
                        if (zPressed && !lastZ) {
                            resetLearning();
                            if (mc.player != null)
                                mc.player.sendMessage(Text.literal("§cСтиль забыт (файлы сохранены)."), true);
                        }
                        lastZ = zPressed;

                        // R – включить KillAura (автозагрузка последнего файла, если нет стиля)
                        if (rPressed && !lastR) {
                            if (isLearned) {
                                if (mode == Mode.PLAY) {
                                    mode = Mode.OFF;
                                    if (mc.player != null)
                                        mc.player.sendMessage(Text.literal("§cKillAura выключена"), true);
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
                                        if (mc.player != null)
                                            mc.player.sendMessage(Text.literal("§aKillAura включена (твой полный стиль)"), true);
                                    } else {
                                        if (mc.player != null)
                                            mc.player.sendMessage(Text.literal("§cНет сохранённого стиля. Пройди обучение (X → бой → C)."), true);
                                        isLearned = false;
                                    }
                                }
                            } else {
                                Path latest = getLatestSessionFile();
                                if (latest != null) {
                                    loadNeuralDataFromFile(latest);
                                    if (!neuralData.isEmpty()) {
                                        isLearned = true;
                                        if (mc.player != null)
                                            mc.player.sendMessage(Text.literal("§aЗагружен последний стиль: " + latest.getFileName()), true);
                                        mode = Mode.PLAY;
                                        playIndex = 0;
                                        lastYaw = mc.player.getYaw();
                                        lastPitch = mc.player.getPitch();
                                        if (mc.player != null)
                                            mc.player.sendMessage(Text.literal("§aKillAura включена"), true);
                                    } else {
                                        if (mc.player != null)
                                            mc.player.sendMessage(Text.literal("§cНет сохранённого стиля. Пройди обучение (X → бой → C)."), true);
                                    }
                                } else {
                                    if (mc.player != null)
                                        mc.player.sendMessage(Text.literal("§cСначала обучи: X → бой → C"), true);
                                }
                            }
                        }
                        lastR = rPressed;

                        // X – начать обучение (очищаем временную запись)
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

                        // C – сохранить обучение в НОВЫЙ файл
                        if (cPressed && !lastC) {
                            if (mode == Mode.LEARN) {
                                if (sampleCount < MIN_SAMPLES) {
                                    if (mc.player != null)
                                        mc.player.sendMessage(Text.literal("§cСлишком мало данных! Нанеси больше ударов."), true);
                                    mode = Mode.OFF;
                                } else {
                                    saveNeuralDataToNewFile();
                                    mode = Mode.OFF;
                                    isLearned = true;
                                    // Загружаем только что созданный файл
                                    Path latest = getLatestSessionFile();
                                    if (latest != null) {
                                        loadNeuralDataFromFile(latest);
                                    }
                                    if (mc.player != null)
                                        mc.player.sendMessage(Text.literal("§aСтиль сохранён в новый файл! Жми R для боя."), true);
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

    // === ЗАПИСЬ ПОЛНОГО СТИЛЯ ===
    private static float[] captureFullSample(LivingEntity target) {
        Vec3d eyePos = mc.player.getEyePos();
        // Целимся в центр тела (можно изменить на голову – но мы сохраняем смещение)
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        // Идеальный угол (центр тела)
        float idealYaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float idealPitch = (float) -MathHelper.atan2(dy, dist) * (180F / (float) Math.PI);

        // Текущий реальный угол игрока
        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        // Смещение = твой реальный угол минус идеальный
        float offsetYaw = curYaw - idealYaw;
        float offsetPitch = curPitch - idealPitch;

        // Скорость поворота (для плавности)
        float yawSpeed = curYaw - lastYaw;
        float pitchSpeed = curPitch - lastPitch;
        lastYaw = curYaw;
        lastPitch = curPitch;

        // Время с начала записи (в секундах)
        float time = (float) (System.currentTimeMillis() - recordStartTime) / 1000f;

        // Естественный шум (дрожание, микро-коррекции)
        float noise = (float) Math.sin(time * 2.5f) * 0.05f + (random.nextFloat() - 0.5f) * 0.03f;

        // Дополнительная случайная вариация (для индивидуальности)
        float extra = (float) (Math.random() * 0.1f);

        return new float[]{
            offsetYaw,       // 0
            offsetPitch,     // 1
            (float) dist,    // 2
            time,            // 3
            yawSpeed,        // 4
            pitchSpeed,      // 5
            noise,           // 6
            extra            // 7
        };
    }

    // === ИНТЕРПОЛЯЦИЯ МЕЖДУ СЭМПЛАМИ ===
    private static float[] getInterpolatedSample() {
        if (neuralData.isEmpty()) return null;
        int idx = playIndex % neuralData.size();
        int nextIdx = (idx + 1) % neuralData.size();
        float[] curr = neuralData.get(idx);
        float[] next = neuralData.get(nextIdx);
        // Плавное переключение между соседними сэмплами
        float t = 0.5f + 0.4f * (float) Math.sin(playIndex * 0.07f);
        float[] result = new float[8];
        for (int i = 0; i < 8; i++) {
            result[i] = curr[i] + (next[i] - curr[i]) * t * 0.5f;
        }
        // Добавляем микро-колебания для естественности
        result[0] += (float) Math.sin(playIndex * 0.13f) * 0.02f;
        result[1] += (float) Math.cos(playIndex * 0.17f) * 0.02f;
        return result;
    }

    // === ВОСПРОИЗВЕДЕНИЕ ПОЛНОГО СТИЛЯ ===
    private static void applyStyledNeuron(float[] neuron, LivingEntity target) {
        if (neuron == null || target == null) return;
        if (!(target instanceof PlayerEntity)) return;

        // Извлекаем параметры стиля
        float offsetYaw = neuron[0];
        float offsetPitch = neuron[1];
        float time = neuron[3];
        float yawSpeed = neuron[4];
        float pitchSpeed = neuron[5];
        float noise = neuron[6];
        float extra = neuron[7];

        // Вычисляем идеальный угол на текущую цель (центр тела)
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) MathHelper.atan2(dz, dx) * (180F / (float) Math.PI) - 90F;
        float idealPitch = (float) -MathHelper.atan2(dy, dist) * (180F / (float) Math.PI);

        // Целевой угол = идеальный + твоё смещение + микро-коррекции
        float targetYaw = idealYaw + offsetYaw + noise * 0.3f;
        float targetPitch = idealPitch + offsetPitch + noise * 0.2f;

        // Плавность поворота (используем твою скорость из записи)
        float smooth = 0.05f + 0.3f * (float) Math.abs(Math.sin(time * 0.5f));
        float dynamicSmooth = Math.min(1.0f, smooth);

        // Микро-рыскания (естественные колебания)
        float microYaw = (float) Math.sin(playIndex * 0.23f) * 0.02f;
        float microPitch = (float) Math.cos(playIndex * 0.19f + 1.2f) * 0.02f;

        // Случайный джиттер для вариативности (но в рамках стиля)
        float jitterYaw = (random.nextFloat() - 0.5f) * 0.005f;
        float jitterPitch = (random.nextFloat() - 0.5f) * 0.005f;

        float finalYaw = targetYaw + microYaw + jitterYaw;
        float finalPitch = targetPitch + microPitch + jitterPitch;

        // Плавный поворот
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        mc.player.setYaw(lerpAngle(currentYaw, finalYaw, dynamicSmooth));
        mc.player.setPitch(lerpAngle(currentPitch, finalPitch, dynamicSmooth));

        // === АТАКА С ЗАДЕРЖКАМИ ИЗ СТИЛЯ ===
        long now = System.currentTimeMillis();

        // Базовая задержка вычисляется из времени записи (твой ритм)
        float baseDelay = 0.500f + 0.300f * (float) Math.abs(Math.sin(time * 1.3f));
        float randomShift = (random.nextFloat() - 0.5f) * 0.150f;
        long delayMs = (long) ((baseDelay + randomShift + extra * 0.2f) * 1000);

        if (now - lastAttackTime >= delayMs && target.isAlive() && !target.isDead()) {
            double realDist = mc.player.distanceTo(target);
            if (realDist <= ATTACK_RANGE && realDist > 0.1) {
                if (mc.player.isSprinting()) mc.player.setSprinting(false);
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(mc.player.getActiveHand());
                lastAttackTime = now;

                // Иногда пропускаем удар (как человек)
                if (random.nextFloat() < 0.04f + extra * 0.1f) {
                    lastAttackTime = now + 120;
                }
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
