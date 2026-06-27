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

public class KillAura implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("killaura");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // === НАСТРОЙКИ ===
    private static final int TOTAL_FAKES = 20;
    private static final double FAKE_RADIUS = 2.0;
    private static final double LEARN_DURATION_SEC = 2.0;
    private static final double ATTACK_RANGE = 3.5;
    private static final double MIN_DELAY = 0.650;
    private static final double MAX_DELAY = 0.730;
    private static final double PLAY_SPEED_MULTIPLIER = 1.6;
    private static final float MAX_ROTATION_STEP = 8.0f;

    private static enum Mode { OFF, LEARN, PLAY }
    private static Mode mode = Mode.OFF;

    // === ДАННЫЕ ОБУЧЕНИЯ ===
    private static final Queue<float[]> recordedSamples = new ConcurrentLinkedQueue<>();
    private static long recordStartTime = 0;
    private static int sampleCount = 0;
    private static final int MAX_SAMPLES = 5000;
    private static final String FILE_PREFIX = "neuro_style_";
    private static final String FILE_EXT = ".txt";

    private static ArmorStandEntity currentFake = null;
    private static long fakeSpawnTime = 0;
    private static boolean isRecording = false;
    private static int fakeCount = 0;
    private static long lastAttackTime = 0;

    private static List<float[]> neuralData = new ArrayList<>();
    private static int playIndex = 0;
    private static float lastYaw = 0, lastPitch = 0;
    private static PlayerEntity lockedTarget = null;
    private static long lastPlayAttackTime = 0;

    private static final int KEY_LEARN = GLFW.GLFW_KEY_X;
    private static final int KEY_SPAWN = GLFW.GLFW_KEY_Z;
    private static final int KEY_PLAY = GLFW.GLFW_KEY_R;
    private static boolean lastLearn = false, lastSpawn = false, lastPlay = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Neuro KillAura: X - обучение, Z - спавн фейка, R - бой");
        loadLatestStyle();

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                mc.execute(() -> {
                    try {
                        if (mc.getWindow() == null || mc.player == null || mc.world == null) return;
                        long window = mc.getWindow().getHandle();

                        boolean xPressed = GLFW.glfwGetKey(window, KEY_LEARN) == GLFW.GLFW_PRESS;
                        boolean zPressed = GLFW.glfwGetKey(window, KEY_SPAWN) == GLFW.GLFW_PRESS;
                        boolean rPressed = GLFW.glfwGetKey(window, KEY_PLAY) == GLFW.GLFW_PRESS;

                        // === X – переключение обучения ===
                        if (xPressed && !lastLearn) {
                            if (mode == Mode.LEARN) {
                                finishLearning();
                            } else {
                                mode = Mode.LEARN;
                                fakeCount = 0;
                                recordedSamples.clear();
                                sampleCount = 0;
                                mc.player.sendMessage(Text.literal("§aОбучение начато. Жми Z для спавна фейков."), true);
                            }
                        }
                        lastLearn = xPressed;

                        // === Z – спавн фейка (только в режиме обучения) ===
                        if (zPressed && !lastSpawn) {
                            if (mode == Mode.LEARN) {
                                spawnFake();
                            } else {
                                mc.player.sendMessage(Text.literal("§cСначала включи обучение (X)"), true);
                            }
                        }
                        lastSpawn = zPressed;

                        // === R – боевой режим ===
                        if (rPressed && !lastPlay) {
                            if (mode == Mode.PLAY) {
                                mode = Mode.OFF;
                                lockedTarget = null;
                                mc.player.sendMessage(Text.literal("§cБой выключен"), true);
                            } else {
                                if (!neuralData.isEmpty()) {
                                    mode = Mode.PLAY;
                                    playIndex = 0;
                                    lastYaw = mc.player.getYaw();
                                    lastPitch = mc.player.getPitch();
                                    lockedTarget = null;
                                    mc.player.sendMessage(Text.literal("§aБой включён (нейро-стиль)"), true);
                                } else {
                                    mc.player.sendMessage(Text.literal("§cНет сохранённого стиля. Пройди обучение."), true);
                                }
                            }
                        }
                        lastPlay = rPressed;

                        // === ЛОГИКА ===
                        if (mode == Mode.LEARN) updateLearning();
                        if (mode == Mode.PLAY) updatePlay();

                    } catch (Exception e) {
                        LOGGER.error("KillAura error", e);
                    }
                });
            }
        }).start();
    }

    // ==================== ОБУЧЕНИЕ ====================
    private static void spawnFake() {
        if (fakeCount >= TOTAL_FAKES) {
            mc.player.sendMessage(Text.literal("§eВсе 20 фейков пройдены. Нажми X для сохранения."), true);
            return;
        }
        if (currentFake != null && !currentFake.isRemoved()) {
            mc.player.sendMessage(Text.literal("§cУже есть фейк. Наведись на него."), true);
            return;
        }

        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = 1.5 + random.nextDouble() * 0.5;
        double x = mc.player.getX() + radius * MathHelper.cos((float) angle);
        double z = mc.player.getZ() + radius * MathHelper.sin((float) angle);
        double y = mc.player.getY() + 0.2;

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
            if (fakeCount < TOTAL_FAKES) spawnFake();
            return;
        }

        HitResult hit = mc.player.raycast(4.0, 1.0f, false);
        boolean aimed = (hit instanceof EntityHitResult) && ((EntityHitResult) hit).getEntity() == currentFake;

        if (aimed) {
            if (!isRecording) {
                isRecording = true;
                recordStartTime = System.currentTimeMillis();
                mc.player.sendMessage(Text.literal("§aЗапись начата..."), true);
            }
            if (isRecording && sampleCount < MAX_SAMPLES) {
                float[] sample = captureSample(currentFake);
                if (sample != null) {
                    recordedSamples.offer(sample);
                    sampleCount++;
                }
            }
            long elapsed = System.currentTimeMillis() - recordStartTime;
            if (elapsed >= LEARN_DURATION_SEC * 1000) {
                mc.player.sendMessage(Text.literal("§aЗапись для фейка #" + (fakeCount + 1) + " завершена."), true);
                currentFake.remove(Entity.RemovalReason.DISCARDED);
                currentFake = null;
                fakeCount++;
                isRecording = false;
                if (fakeCount >= TOTAL_FAKES) {
                    mc.player.sendMessage(Text.literal("§eВсе фейки пройдены! Нажми X для сохранения."), true);
                } else {
                    spawnFake();
                }
            }
        } else {
            if (isRecording) {
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
        float offsetYaw = curYaw - idealYaw;
        float offsetPitch = curPitch - idealPitch;
        float yawSpeed = curYaw - lastYaw;
        float pitchSpeed = curPitch - lastPitch;
        lastYaw = curYaw;
        lastPitch = curPitch;

        if (Math.abs(yawSpeed) > 25f || Math.abs(pitchSpeed) > 25f) return null;

        float time = (float) (System.currentTimeMillis() - recordStartTime) / 1000f;
        return new float[]{offsetYaw, offsetPitch, yawSpeed, pitchSpeed, time};
    }

    private static void finishLearning() {
        if (recordedSamples.isEmpty()) {
            mc.player.sendMessage(Text.literal("§cНет данных."), true);
            return;
        }
        saveSamples();
        mode = Mode.OFF;
        mc.player.sendMessage(Text.literal("§aСтиль сохранён! Жми R."), true);
        if (currentFake != null && !currentFake.isRemoved()) {
            currentFake.remove(Entity.RemovalReason.DISCARDED);
            currentFake = null;
        }
    }

    // ==================== СОХРАНЕНИЕ / ЗАГРУЗКА ====================
    private static void saveSamples() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        Path file = mc.runDirectory.toPath().resolve(FILE_PREFIX + ts + FILE_EXT);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            w.println("SAMPLES:" + sampleCount);
            for (float[] s : recordedSamples) {
                for (float v : s) w.print(v + ",");
                w.println();
            }
        } catch (IOException e) { LOGGER.error("Save error", e); }
    }

    private static void loadLatestStyle() {
        Path dir = mc.runDirectory.toPath();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, FILE_PREFIX + "*" + FILE_EXT)) {
            Path latest = null; long latestTime = 0;
            for (Path entry : stream) {
                long mod = Files.getLastModifiedTime(entry).toMillis();
                if (mod > latestTime) { latestTime = mod; latest = entry; }
            }
            if (latest != null) {
                neuralData.clear();
                try (BufferedReader r = Files.newBufferedReader(latest, StandardCharsets.UTF_8)) {
                    String line; boolean reading = false;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("SAMPLES:")) { reading = true; continue; }
                        if (reading) {
                            String[] parts = line.split(",");
                            if (parts.length >= 5) {
                                float[] s = new float[5];
                                for (int i=0;i<5;i++) s[i] = Float.parseFloat(parts[i]);
                                neuralData.add(s);
                            }
                        }
                    }
                }
                LOGGER.info("Loaded {} samples", neuralData.size());
            }
        } catch (IOException e) { LOGGER.error("Load error", e); }
    }

    // ==================== БОЕВОЙ РЕЖИМ ====================
    private static void updatePlay() {
        if (neuralData.isEmpty()) { mode = Mode.OFF; return; }
        PlayerEntity target = lockedTarget;
        if (target == null || target.isDead() || !target.isAlive() || mc.player.distanceTo(target) > 5.0) {
            target = getTargetPlayer();
            if (target != null) lockedTarget = target;
            else { lockedTarget = null; return; }
        }
        if (target == null) return;
        double dist = mc.player.distanceTo(target);
        if (dist > 5.0 || dist < 0.5) { lockedTarget = null; return; }

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
        float[] s = neuralData.get(playIndex);
        if (s == null) return;

        float offsetYaw = s[0], offsetPitch = s[1];
        float yawSpeed = s[2] * (float) PLAY_SPEED_MULTIPLIER;
        float pitchSpeed = s[3] * (float) PLAY_SPEED_MULTIPLIER;
        float time = s[4];

        Vec3d eye = mc.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        double dx = targetPos.x - eye.x, dy = targetPos.y - eye.y, dz = targetPos.z - eye.z;
        double hDist = Math.sqrt(dx*dx + dz*dz);
        float idealYaw = (float)(MathHelper.atan2(dz, dx)*180f/Math.PI) - 90f;
        float idealPitch = (float)(-MathHelper.atan2(dy, hDist)*180f/Math.PI);

        float targetYaw = idealYaw + offsetYaw;
        float targetPitch = idealPitch + offsetPitch;

        float curYaw = mc.player.getYaw(), curPitch = mc.player.getPitch();
        float yawDiff = targetYaw - curYaw;
        yawDiff = (yawDiff % 360 + 540) % 360 - 180;
        float pitchDiff = targetPitch - curPitch;
        yawDiff = MathHelper.clamp(yawDiff, -MAX_ROTATION_STEP, MAX_ROTATION_STEP);
        pitchDiff = MathHelper.clamp(pitchDiff, -MAX_ROTATION_STEP, MAX_ROTATION_STEP);

        float smooth = 0.15f + 0.3f * (float)Math.abs(Math.sin(time * 0.5f));
        smooth = Math.min(1.0f, smooth * (float)PLAY_SPEED_MULTIPLIER);
        mc.player.setYaw(curYaw + yawDiff * smooth);
        mc.player.setPitch(curPitch + pitchDiff * smooth);

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
