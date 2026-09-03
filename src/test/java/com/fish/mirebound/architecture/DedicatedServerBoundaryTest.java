package com.fish.mirebound.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedServerBoundaryTest {
    private static final List<String> CLIENT_ONLY_IMPORTS = List.of(
            "net.minecraft.client.",
            "com.mojang.blaze3d.",
            "org.lwjgl.");

    @Test
    void commonSourcesDoNotImportClientOnlyMinecraftTypes() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java").toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !isClientOwned(sourceRoot.relativize(path)))
                    .forEach(path -> inspect(path, sourceRoot, violations));
        }
        assertTrue(violations.isEmpty(),
                () -> "Client-only imports escaped their client package:\n"
                        + String.join("\n", violations));
    }

    private static boolean isClientOwned(Path relativePath) {
        String path = relativePath.toString().replace('\\', '/');
        return path.contains("/client/")
                || path.startsWith("com/fish/mirebound/client/")
                || path.contains("/mixin/client/");
    }

    private static void inspect(Path path, Path sourceRoot, List<String> violations) {
        try {
            int lineNumber = 0;
            for (String line : Files.readAllLines(path)) {
                lineNumber++;
                String trimmed = line.trim();
                for (String forbiddenReference : CLIENT_ONLY_IMPORTS) {
                    if (trimmed.contains(forbiddenReference)) {
                        violations.add(sourceRoot.relativize(path) + ":" + lineNumber
                                + " " + trimmed);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }
}
