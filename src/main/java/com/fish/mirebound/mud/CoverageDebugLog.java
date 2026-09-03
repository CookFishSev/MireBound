package com.fish.mirebound.mud;

import com.fish.mirebound.Mirebound;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class CoverageDebugLog {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int FLUSH_BATCH_LINES = 32;
    private static final Map<String, Integer> LAST_LOG_TICK_BY_KEY = new ConcurrentHashMap<>();
    private static volatile boolean enabled;
    private static boolean wroteHeader;
    private static BufferedWriter writer;
    private static int pendingFlushLines;

    private CoverageDebugLog() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static synchronized void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            LAST_LOG_TICK_BY_KEY.clear();
            wroteHeader = false;
            writeLine("coverage_debug enabled");
        } else {
            writeLine("coverage_debug disabled");
            LAST_LOG_TICK_BY_KEY.clear();
            closeWriter();
        }
    }

    public static String debugLogPath() {
        return debugLogPathInternal().toString();
    }

    public static boolean reserve(ServerPlayer player, String key, int intervalTicks) {
        if (!enabled) {
            return false;
        }

        return reserveInternal(player, key, intervalTicks);
    }

    static boolean reserveSableDiagnostic(ServerPlayer player, String key, int intervalTicks) {
        return enabled && reserveInternal(player, "sable-diagnostic:" + key, intervalTicks);
    }

    private static boolean reserveInternal(ServerPlayer player, String key, int intervalTicks) {

        String combinedKey = player.getId() + ":" + key;
        int lastTick = LAST_LOG_TICK_BY_KEY.getOrDefault(combinedKey, Integer.MIN_VALUE / 2);
        if (player.tickCount - lastTick < intervalTicks) {
            return false;
        }

        LAST_LOG_TICK_BY_KEY.put(combinedKey, player.tickCount);
        return true;
    }

    public static void event(ServerPlayer player, String key, String message) {
        if (!enabled) {
            return;
        }

        eventInternal(player, key, message);
    }

    static void sableDiagnostic(ServerPlayer player, String key, String message) {
        if (enabled) {
            eventInternal(player, "sable-diagnostic:" + key, message);
        }
    }

    private static void eventInternal(ServerPlayer player, String key, String message) {

        writeLine("tick=" + player.tickCount
                + " player=" + player.getGameProfile().getName()
                + " id=" + player.getId()
                + " key=" + key
                + " " + message);
    }

    public static String vec(Vec3 value) {
        return String.format(Locale.ROOT, "(%.3f,%.3f,%.3f)", value.x, value.y, value.z);
    }

    static SampleTrace trace(String kind, Vec3 worldPoint, String details) {
        if (!enabled) {
            return null;
        }
        return new SampleTrace(kind, worldPoint, details);
    }

    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        enabled = false;
        LAST_LOG_TICK_BY_KEY.clear();
        closeWriter();
    }

    private static synchronized void writeLine(String message) {
        Path path = debugLogPathInternal();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (writer == null) {
                writer = Files.newBufferedWriter(path,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE);
            }
            if (!wroteHeader) {
                writer.write("\n=== Mirebound: Sinking Depths coverage debug " + LocalDateTime.now() + " ===\n");
                wroteHeader = true;
            }
            writer.write(LocalTime.now().format(TIME_FORMAT));
            writer.write(' ');
            writer.write(message);
            writer.newLine();
            pendingFlushLines++;
            if (pendingFlushLines >= FLUSH_BATCH_LINES) {
                writer.flush();
                pendingFlushLines = 0;
            }
        } catch (IOException | RuntimeException exception) {
            Mirebound.LOGGER.warn("Failed to write Mirebound: Sinking Depths coverage debug log: {}", path, exception);
            closeWriter();
        }
    }

    private static void closeWriter() {
        if (writer == null) {
            pendingFlushLines = 0;
            return;
        }
        try {
            writer.close();
        } catch (IOException exception) {
            Mirebound.LOGGER.warn("Failed to close Mirebound: Sinking Depths coverage debug log", exception);
        } finally {
            writer = null;
            pendingFlushLines = 0;
        }
    }

    private static Path debugLogPathInternal() {
        return FMLPaths.GAMEDIR.get()
                .resolve("logs")
                .resolve("Fmud")
                .resolve("mirebound-coverage-debug.log")
                .toAbsolutePath();
    }

    record SampleTrace(String kind, Vec3 worldPoint, String details) {
        String describe() {
            return kind + " world=" + vec(worldPoint) + " " + details;
        }
    }
}
