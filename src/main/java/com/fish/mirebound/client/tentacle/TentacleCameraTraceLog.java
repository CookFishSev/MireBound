package com.fish.mirebound.client.tentacle;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.client.ClientMudDebugOptions;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.neoforged.fml.loading.FMLPaths;

/** Cold-path camera diagnostics enabled together with /fmud set physics_log true. */
public final class TentacleCameraTraceLog {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter LINE_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static BufferedWriter writer;
    private static Path activePath;

    private TentacleCameraTraceLog() {
    }

    public static synchronized void setEnabled(boolean enabled) {
        if (enabled) {
            ensureWriter();
            writeLine("=== Mirebound: Sinking Depths tentacle camera trace ===");
        } else {
            close();
        }
    }

    static boolean enabled() {
        return ClientMudDebugOptions.physicsLog();
    }

    static synchronized void trace(String message) {
        if (!enabled()) {
            return;
        }
        writeLine(message);
    }

    public static synchronized String debugLogPath() {
        return (activePath == null ? logDirectory() : activePath)
                .toAbsolutePath().toString();
    }

    public static synchronized void reset() {
        close();
    }

    private static void ensureWriter() {
        if (writer != null) {
            return;
        }
        try {
            Path directory = logDirectory();
            Files.createDirectories(directory);
            activePath = directory.resolve(
                    "tentacle-camera-" + LocalDateTime.now().format(FILE_TIME) + ".log");
            writer = Files.newBufferedWriter(activePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException exception) {
            Mirebound.LOGGER.warn("Failed to open Mirebound: Sinking Depths tentacle camera trace", exception);
            writer = null;
        }
    }

    private static void writeLine(String message) {
        ensureWriter();
        if (writer == null) {
            return;
        }
        try {
            writer.write(LocalDateTime.now().format(LINE_TIME));
            writer.write(' ');
            writer.write(message);
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            Mirebound.LOGGER.warn(
                    "Failed to write Mirebound: Sinking Depths tentacle camera trace: {}",
                    activePath, exception);
        }
    }

    private static void close() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException exception) {
            Mirebound.LOGGER.warn(
                    "Failed to close Mirebound: Sinking Depths tentacle camera trace: {}",
                    activePath, exception);
        } finally {
            writer = null;
        }
    }

    private static Path logDirectory() {
        return FMLPaths.GAMEDIR.get().resolve("logs").resolve("Fmud");
    }
}
