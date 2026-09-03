package com.fish.mirebound.client.worldgen;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.generation.natural.NaturalMudGenerationPresetCodec;
import com.fish.mirebound.generation.natural.NaturalMudGenerationPresetCodec.NamedProfile;
import com.fish.mirebound.generation.natural.NaturalMudGenerationProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

/** Reads and writes user-authored presets inside the active game instance. */
public final class NaturalMudGenerationPresetStore {
    private static final Pattern ILLEGAL_FILENAME = Pattern.compile(
            "[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern RESERVED_WINDOWS_NAME = Pattern.compile(
            "(?i)(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?");
    private static final int MAX_NAME_LENGTH = 64;

    private NaturalMudGenerationPresetStore() {
    }

    public static List<Preset> load() {
        Path directory = directory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString()
                            .toLowerCase(java.util.Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(NaturalMudGenerationPresetStore::read)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException exception) {
            Mirebound.LOGGER.warn("Unable to list Mirebound generation presets", exception);
            return List.of();
        }
    }

    public static SaveResult save(String name, NaturalMudGenerationProfile profile) {
        String displayName = name == null ? "" : name.trim();
        if (displayName.isEmpty()) {
            return SaveResult.INVALID_NAME;
        }
        String stem = safeStem(displayName);
        if (stem.isEmpty()) {
            return SaveResult.INVALID_NAME;
        }
        Path directory = directory();
        Path target = directory.resolve(stem + ".json").normalize();
        if (!target.getParent().equals(directory)) {
            return SaveResult.INVALID_NAME;
        }
        return write(target, displayName, profile)
                ? SaveResult.SAVED : SaveResult.IO_ERROR;
    }

    public static UpdateResult update(
            Preset preset, NaturalMudGenerationProfile profile) {
        if (preset == null || preset.fileName() == null
                || preset.fileName().isBlank()) {
            return UpdateResult.INVALID_NAME;
        }
        Path directory = directory();
        Path target = directory.resolve(preset.fileName()).normalize();
        if (!isSafePresetPath(target, directory, preset.fileName())) {
            return UpdateResult.INVALID_NAME;
        }
        return write(target, preset.name(), profile)
                ? UpdateResult.UPDATED : UpdateResult.IO_ERROR;
    }

    public static DeleteResult delete(Preset preset) {
        if (preset == null || preset.fileName() == null
                || preset.fileName().isBlank()) {
            return DeleteResult.INVALID_NAME;
        }
        Path directory = directory();
        Path target = directory.resolve(preset.fileName()).normalize();
        if (!isSafePresetPath(target, directory, preset.fileName())) {
            return DeleteResult.INVALID_NAME;
        }
        try {
            return Files.deleteIfExists(target)
                    ? DeleteResult.DELETED : DeleteResult.NOT_FOUND;
        } catch (IOException exception) {
            Mirebound.LOGGER.warn("Unable to delete Mirebound generation preset {}",
                    target, exception);
            return DeleteResult.IO_ERROR;
        }
    }

    private static boolean write(
            Path target, String displayName,
            NaturalMudGenerationProfile profile) {
        Path directory = target.getParent();
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, ".mirebound-", ".tmp");
            try {
                Files.writeString(temporary,
                        NaturalMudGenerationPresetCodec.encode(displayName, profile),
                        StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return true;
        } catch (IOException exception) {
            Mirebound.LOGGER.warn("Unable to write Mirebound generation preset {}",
                    target, exception);
            return false;
        }
    }

    private static boolean isSafePresetPath(
            Path target, Path directory, String fileName) {
        return fileName != null
                && target.getParent().equals(directory)
                && target.getFileName().toString().equals(fileName)
                && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".json");
    }

    private static Optional<Preset> read(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Optional<NamedProfile> decoded = NaturalMudGenerationPresetCodec.decode(
                    stripExtension(path.getFileName().toString()), text);
            return decoded.map(value -> new Preset(
                    value.name(), value.profile(), path.getFileName().toString()));
        } catch (IOException | RuntimeException exception) {
            Mirebound.LOGGER.warn("Ignoring invalid Mirebound generation preset {}",
                    path, exception);
            return Optional.empty();
        }
    }

    private static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("mirebound").resolve("preset").toAbsolutePath().normalize();
    }

    private static String safeStem(String name) {
        if (ILLEGAL_FILENAME.matcher(name).find()
                || RESERVED_WINDOWS_NAME.matcher(name).matches()) {
            return "";
        }
        String stem = name.trim();
        while (stem.endsWith(".") || stem.endsWith(" ")) {
            stem = stem.substring(0, stem.length() - 1).trim();
        }
        if (stem.equals(".") || stem.equals("..")) {
            return "";
        }
        return stem.length() > MAX_NAME_LENGTH
                ? stem.substring(0, MAX_NAME_LENGTH).trim() : stem;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    public record Preset(String name, NaturalMudGenerationProfile profile,
            String fileName) {
    }

    public enum SaveResult {
        SAVED,
        INVALID_NAME,
        IO_ERROR
    }

    public enum UpdateResult {
        UPDATED,
        INVALID_NAME,
        IO_ERROR
    }

    public enum DeleteResult {
        DELETED,
        NOT_FOUND,
        INVALID_NAME,
        IO_ERROR
    }
}
