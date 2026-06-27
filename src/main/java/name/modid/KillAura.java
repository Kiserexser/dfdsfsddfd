package name.modid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
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

public class NeuroAura implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("neuroaura");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // === НАСТРОЙКИ ===
    private static final int TOTAL_FAKES = 20;
    private static final double FAKE_RADIUS = 2.0;
    private static final double LEARN_DURATION_SEC = 2.0; // 2 секунды записи на каждого фейка
    private static final double ATTACK_RANGE = 3.5;
    private static final double MIN_DELAY = 0.650;
    private static final double MAX_DELAY = 0.730;
    private static final double PLAY_SPEED_MULTIPLIER = 1.6; // плавность * 1.6
    private static final float MAX_ROTATION_STEP = 8.0f; // град/тик при воспроизведении

    private static enum Mode { OFF, LEARN, PLAY }
    private static Mode mode = Mode.OFF;

    // === ДАННЫЕ ОБУЧЕНИЯ ===
    private static final Queue<float[]> recordedSamples = new ConcurrentLinkedQueue<>();
    private static long recordStartTime = 0;
    private static int sampleCount = 0;
    private static final int MAX_SAMPLES = 5000;
    private static final String FILE_PREFIX = "neuro_style_";
    private static final String FILE_EXT = ".txt";

    // === СОСТОЯНИЕ ОБУЧЕНИЯ ===
    private static ArmorStandEntity currentFake = null;
    private static long fakeSpawnTime = 0;
    private static boolean isRecording = false;
    private static int fakeCount = 0;
    private static long lastAttackTime = 0;
    private static long lastFakeAttackTime = 0;

    // === ВОСПРОИЗВЕДЕНИЕ ===
    private static List<float[]> neuralData = new ArrayList<>();
    private static int playIndex = 0;
    private static float lastYaw = 0, lastPitch = 0;
    private static PlayerEntity lockedTarget = null;
    private static long lastPlayAttackTime = 0;

    // === КЛАВИШИ ===
    private static final int KEY_LEARN = GLFW.GLFW_KEY_Z;
    private static final int KEY_SAVE = GLFW.GLFW_KEY_X;
    private static final int KEY_PLAY = GLFW.GLFW_KEY_R;
    private static boolean lastLearn = false, lastSave = false, lastPlay = false;

    @Override
    public void onInitialize() {
        LOGGER.info("NeuroAura: Z - спавн фейка (обучение), X - сохранить, R - включить бой");
        loadLatestStyle();

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    try {
                        if (mc.getWindow() == null || mc.player == null || mc.world == null) return;
                        long window = mc.getWindow().getHandle();

                        boolean zPressed = GLFW.glfwGetKey(window, KEY_LEARN) == GLFW.GLFW_PRESS;
                        boolean xPressed = GLFW.glfwGetKey(window, KEY_SAVE) == GLFW.GLFW_PRESS;
                        boolean rPressed = GLFW.glfwGetKey(window, KEY_PLAY) == GLFW.GLFW_PRESS;

                        // === ОБРАБОТКА КЛАВИШ ===
                        if (zPressed && !lastLearn) {
                            if (mode == Mode.LEARN) {
                                spawnFakePlayer();
                            } else {
                                mc.player.sendMessage(Text.literal("§cСначала включи обучение (X)"), true);
                            }
                        }
                        lastLearn = zPressed;

                        if (xPressed && !lastSave) {
                            if (mode == Mode.LEARN) {
                                finishLearning();
                            } else {
                                mode = Mode.LEARN;
                                mc.player.sendMessage(Text.literal("§aОбучение начато. Нажимай Z для спавна фейков."), true);
                                fakeCount = 0;
                                recordedSamples.clear();
                                sampleCount = 0;
                            }
                        }
                        lastSave = xPressed;

                        if (rPressed && !lastPlay) {
                            if (mode == Mode.PLAY) {
                                mode = Mode.OFF;
                                lockedTarget = null;
                                mc.player.sendMessage(Text.literal("§cБоевой режим выключен"), true);
                            } else {
                                if (!neuralData.isEmpty()) {
                                    mode = Mode.PLAY;
                                    playIndex = 0;
                                    lastYaw = mc.player.getYaw();
                                    lastPitch = mc.player.getPitch();
                                    lockedTarget = null;
                                    mc.player.sendMessage(Text.literal("§aБоевой режим включён (нейро-стиль)"), true);
                                } else {
                                    mc.player.sendMessage(Text.literal("§cНет сохранённого стиля. Пройди обучение."), true);
                                }
                            }
                        }
                        lastPlay = rPressed;

                        // === ЛОГИКА ОБУЧЕНИЯ ===
                        if (mode == Mode.LEARN) {
                            updateLearning();
                        }

                        // === ЛОГИКА БОЯ ===
                        if (mode == Mode.PLAY) {
                            updatePlay();
                        }

                    } catch (Exception e) {
                        LOGGER.error("NeuroAura error", e);
                    }
                });
            }
        }).start();
    }

    // ==================== ОБУЧЕНИЕ ====================
    private static void spawnFakePlayer() {
        if (fakeCount >= TOTAL_FAKES) {
            mc.player.sendMessage(Text.literal("§eВсе 20 фейков пройдены. Нажми X для сохранения."), true);
            return;
        }
        if (currentFake != null && !currentFake.isRemoved()) {
            mc.player.sendMessage(Text.literal("§cУже есть активный фейк. Наведись на него."), true);
            return;
        }

        // Случайная позиция вокруг игрока в радиусе 2 блоков
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = 1.5 + random.nextDouble() * 0.5;
        double x = mc.player.getX() + radius * MathHelper.cos((float) angle);
        double z = mc.player.getZ() + radius * MathHelper.sin((float) angle);
        double y = mc.player.getY() + 0.2; // над землёй

        currentFake = new ArmorStandEntity(mc.world, x, y, z);
        currentFake.setInvisible(true);
        currentFake.setCustomName(Text.literal("§cFake #" + (fakeCount + 1)));
        currentFake.setCustomNameVisible(true);
        mc.world.addEntity(currentFake);

        fakeSpawnTime = System.currentTimeMillis();
        isRecording = false;
        mc.player.sendMessage(Text.literal("§aФейк #" + (fakeCount + 1) + " появился. Наведись на него."), true);
    }

    private static void updateLearning() {
        if (currentFake == null || currentFake.isRemoved()) {
            // Если фейк исчез, спавним следующего
            if (fakeCount < TOTAL_FAKES) {
                spawnFakePlayer();
            }
            return;
        }

        // Проверяем, наведён ли игрок на фейка
        HitResult hit = mc.player.raycast(4.0, 1.0f, false);
        boolean aimed = (hit instanceof EntityHitResult) && ((EntityHitResult) hit).getEntity() == currentFake;

        if (aimed) {
            if (!isRecording) {
                // Начало записи
                isRecording = true;
                recordStartTime = System.currentTimeMillis();
                mc.player.sendMessage(Text.literal("§aЗапись стиля начата..."), true);
            }

            // Запись сэмплов
            if (isRecording && sampleCount < MAX_SAMPLES) {
                float[] sample = captureSample(currentFake);
                if (sample != null) {
                    recordedSamples.offer(sample);
                    sampleCount++;
                    if (sampleCount % 50 == 0) {
                        mc.player.sendMessage(Text.literal("§7Записано " + sampleCount + " сэмплов"), true);
                    }
                }
            }

            // Проверка времени записи
            long elapsed = System.currentTimeMillis() - recordStartTime;
            if (elapsed >= LEARN_DURATION_SEC * 1000) {
                // Завершаем запись для этого фейка
                mc.player.sendMessage(Text.literal("§aЗапись для фейка #" + (fakeCount + 1) + " завершена."), true);
                currentFake.remove(Entity.RemovalReason.DISCARDED);
                currentFake = null;
                fakeCount++;
                isRecording = false;
                if (fakeCount >= TOTAL_FAKES) {
                    mc.player.sendMessage(Text.literal("§eВсе 20 фейков пройдены! Нажми X для сохранения."), true);
                } else {
                    spawnFakePlayer(); // спавним следующего
                }
            }
        } else {
            if (isRecording) {
                // Если убрал прицел, запись останавливается (можно перезапустить позже)
                isRecording = false;
                mc.player.sendMessage(Text.literal("§cЗапись приостановлена. Наведись снова."), true);
            }
        }
    }

    private static float[] captureSample(Entity target) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, 0.5, 0);
        double dx = targetPos.x - eye.x;
        double dy = targetPos.y - eye.y;
        double dz = targetPos.z - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) (MathHelper.atan2(dz, dx) * 180f / Math.PI) - 90f;
        float idealPitch = (float) (-MathHelper.atan2(dy, hDist) * 180f / Math.PI);

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        // Смещение от идеального угла
        float offsetYaw = curYaw - idealYaw;
        float offsetPitch = curPitch - idealPitch;

        // Скорость изменения углов (плавность)
        float yawSpeed = curYaw - lastYaw;
        float pitchSpeed = curPitch - lastPitch;
        lastYaw = curYaw;
        lastPitch = curPitch;

        // Фильтр резких поворотов (> 25° за тик)
        if (Math.abs(yawSpeed) > 25f || Math.abs(pitchSpeed) > 25f) {
            return null; // не сохраняем аномалии
        }

        float time = (float) (System.currentTimeMillis() - recordStartTime) / 1000f;

        return new float[]{
                offsetYaw, offsetPitch,
                yawSpeed, pitchSpeed,
                time,
                (float) mc.player.distanceTo(target) // дистанция
        };
    }

    private static void finishLearning() {
        if (recordedSamples.isEmpty()) {
            mc.player.sendMessage(Text.literal("§cНет записанных сэмплов."), true);
            return;
        }

        saveSamples();
        mode = Mode.OFF;
        mc.player.sendMessage(Text.literal("§aОбучение сохранено! Жми R для боя."), true);
        if (currentFake != null && !currentFake.isRemoved()) {
            currentFake.remove(Entity.RemovalReason.DISCARDED);
            currentFake = null;
        }
    }

    // ==================== СОХРАНЕНИЕ ====================
    private static void saveSamples() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        Path file = mc.runDirectory.toPath().resolve(FILE_PREFIX + timestamp + FILE_EXT);
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            writer.println("SAMPLES:" + sampleCount);
            for (float[] s : recordedSamples) {
                for (float v : s) {
                    writer.print(v + ",");
                }
                writer.println();
            }
            LOGGER.info("Saved " + sampleCount + " samples to " + file.getFileName());
        } catch (IOException e) {
            LOGGER.error("Save error", e);
        }
    }

    private static void loadLatestStyle() {
        Path dir = mc.runDirectory.toPath();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, FILE_PREFIX + "*" + FILE_EXT)) {
            Path latest = null;
            long latestTime = 0;
            for (Path entry : stream) {
                long mod = Files.getLastModifiedTime(entry).toMillis();
                if (mod > latestTime) {
                    latestTime = mod;
                    latest = entry;
                }
            }
            if (latest != null) {
                loadFromFile(latest);
            }
        } catch (IOException e) {
            LOGGER.error("Load error", e);
        }
    }

    private static void loadFromFile(Path file) {
        neuralData.clear();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean reading = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("SAMPLES:")) {
                    reading = true;
                    continue;
                }
                if (reading) {
                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
                        float[] sample = new float[6];
                        for (int i = 0; i < 6; i++) sample[i] = Float.parseFloat(parts[i]);
                        neuralData.add(sample);
                    }
                }
            }
            LOGGER.info("Loaded " + neuralData.size() + " samples from " + file.getFileName());
        } catch (IOException e) {
            LOGGER.error("Load error", e);
        }
    }

    // ==================== БОЕВОЙ РЕЖИМ ====================
    private static void updatePlay() {
        if (mc.player == null || mc.world == null || neuralData.isEmpty()) {
            mode = Mode.OFF;
            return;
        }

        PlayerEntity target = lockedTarget;
        if (target == null || target.isDead() || !target.isAlive() || mc.player.distanceTo(target) > 5.0) {
            target = getTargetPlayer();
            if (target != null) lockedTarget = target;
            else { lockedTarget = null; return; }
        }

        double dist = mc.player.distanceTo(target);
        if (dist > 5.0 || dist < 0.5) {
            lockedTarget = null;
            return;
        }

        // Применяем обученный стиль
        applyLearnedStyle(target);
    }

    private static PlayerEntity getTargetPlayer() {
        if (mc.player == null || mc.world == null) return null;
        Box box = mc.player.getBoundingBox().expand(5.0);
        List<PlayerEntity> players = mc.world.getEntitiesByClass(PlayerEntity.class, box,
                e -> e != mc.player && e.isAlive() && !e.isDead());
        players.removeIf(e -> mc.player.distanceTo(e) > 5.0);
        players.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        return players.isEmpty() ? null : players.get(0);
    }

    private static void applyLearnedStyle(PlayerEntity target) {
        if (playIndex >= neuralData.size()) playIndex = 0;
        float[] sample = neuralData.get(playIndex);
        if (sample == null) return;

        float offsetYaw = sample[0];
        float offsetPitch = sample[1];
        float yawSpeed = sample[2] * (float) PLAY_SPEED_MULTIPLIER; // умножаем на 1.6
        float pitchSpeed = sample[3] * (float) PLAY_SPEED_MULTIPLIER;
        float time = sample[4];

        // Вычисляем идеальный угол на цель
        Vec3d eye = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eye.x;
        double dy = targetPos.y - eye.y;
        double dz = targetPos.z - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) (MathHelper.atan2(dz, dx) * 180f / Math.PI) - 90f;
        float idealPitch = (float) (-MathHelper.atan2(dy, hDist) * 180f / Math.PI);

        // Целевой угол = идеальный + смещение
        float targetYaw = idealYaw + offsetYaw;
        float targetPitch = idealPitch + offsetPitch;

        // Применяем плавность (с учётом умножения)
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float yawDiff = targetYaw - currentYaw;
        yawDiff = (yawDiff % 360 + 540) % 360 - 180;
        float pitchDiff = targetPitch - currentPitch;

        // Ограничиваем скорость (чтобы не было рывков)
        yawDiff = MathHelper.clamp(yawDiff, -MAX_ROTATION_STEP, MAX_ROTATION_STEP);
        pitchDiff = MathHelper.clamp(pitchDiff, -MAX_ROTATION_STEP, MAX_ROTATION_STEP);

        // Плавность = среднее между записанной и текущей (но с умножением)
        float smoothFactor = 0.15f + 0.3f * (float) Math.abs(Math.sin(time * 0.5f));
        smoothFactor = Math.min(1.0f, smoothFactor * 1.6f); // применяем множитель
        mc.player.setYaw(currentYaw + yawDiff * smoothFactor);
        mc.player.setPitch(currentPitch + pitchDiff * smoothFactor);

        // Атака с задержкой 0.650-0.730
        long now = System.currentTimeMillis();
        double delay = MIN_DELAY + (MAX_DELAY - MIN_DELAY) * random.nextDouble();
        if (now - lastPlayAttackTime >= (long)(delay * 1000) && target.isAlive() && mc.player.distanceTo(target) <= ATTACK_RANGE) {
            if (mc.player.isSprinting()) mc.player.setSprinting(false);
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(mc.player.getActiveHand());
            lastPlayAttackTime = now;
        }

        playIndex++;
    }
}
