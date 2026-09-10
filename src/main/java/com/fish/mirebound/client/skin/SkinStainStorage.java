package com.fish.mirebound.client.skin;

import com.fish.mirebound.coverage.skin.SkinStainMask;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Instance-local, bounded JSON files; display names are never filesystem paths. */
public final class SkinStainStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_FILE_BYTES = 12L * 1024 * 1024;
    private final Path directory;

    public SkinStainStorage(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }

    public SkinStainMask loadMask() throws IOException {
        Path file = directory.resolve("self.json");
        if (!Files.exists(file)) return SkinStainMask.empty(64, 64);
        try {
            JsonObject root = read(file);
            requireVersion(root);
            return decodeMask(root.getAsJsonObject("mask"));
        } catch (RuntimeException error) { throw new IOException("Invalid skin rules", error); }
    }

    public void saveMask(SkinStainMask mask) throws IOException {
        JsonObject root = root();
        root.add("mask", encodeMask(mask));
        write(directory.resolve("self.json"), root);
    }

    public List<Preset> presets() throws IOException {
        Path path = directory.resolve("presets");
        if (!Files.isDirectory(path)) return List.of();
        try (var files = Files.list(path)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted().limit(128).map(this::readPreset).filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(Preset::name)).toList();
        }
    }

    private Preset readPreset(Path file) {
        try {
            String filename = file.getFileName().toString();
            UUID id = UUID.fromString(filename.substring(0, filename.length() - 5));
            JsonObject root = read(file);
            requireVersion(root);
            return new Preset(id, checkedName(root.get("name").getAsString()), decodeMask(root.getAsJsonObject("mask")));
        } catch (RuntimeException | IOException ignored) { return null; }
    }

    public Preset savePreset(UUID id, String name, SkinStainMask mask) throws IOException {
        if (id == null) {
            if (presets().size() >= 128) throw new IOException("Too many skin presets");
            id = UUID.randomUUID();
        }
        Preset preset = new Preset(id, checkedName(name), mask);
        JsonObject root = root();
        root.addProperty("name", preset.name());
        root.add("mask", encodeMask(mask));
        write(directory.resolve("presets").resolve(id + ".json"), root);
        return preset;
    }

    public void deletePreset(UUID id) throws IOException {
        Files.deleteIfExists(directory.resolve("presets").resolve(id + ".json"));
    }

    private static String checkedName(String name) {
        if (name == null || name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid preset name");
        return name.trim();
    }

    private static JsonObject root() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        return root;
    }

    private static void requireVersion(JsonObject root) {
        if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported skin rule version");
    }

    private static JsonObject encodeMask(SkinStainMask mask) {
        JsonObject json = new JsonObject();
        json.addProperty("width", mask.width());
        json.addProperty("height", mask.height());
        json.addProperty("blocked", Base64.getEncoder().encodeToString(mask.copyBits().toByteArray()));
        return json;
    }

    private static SkinStainMask decodeMask(JsonObject json) {
        int width = json.get("width").getAsInt(), height = json.get("height").getAsInt();
        if (width < 1 || height < 1 || width > SkinStainMask.MAX_DIMENSION || height > SkinStainMask.MAX_DIMENSION)
            throw new IllegalArgumentException("Invalid skin size");
        String encoded = json.get("blocked").getAsString();
        if (encoded.length() > ((width * height + 7) / 8 + 2) / 3 * 4)
            throw new IllegalArgumentException("Oversized mask");
        return new SkinStainMask(width, height, BitSet.valueOf(Base64.getDecoder().decode(encoded)));
    }

    private static JsonObject read(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Skin rule file too large");
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void write(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".skin-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(json), StandardCharsets.UTF_8);
            if (Files.size(temporary) > MAX_FILE_BYTES) throw new IOException("Skin rules exceed storage budget");
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public record Preset(UUID id, String name, SkinStainMask mask) {}
}
