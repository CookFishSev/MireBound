package com.fish.mirebound.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeOwnerBoundaryTest {
    @Test
    void assimilationFacadeDelegatesPersistentAndInteractiveState() throws IOException {
        String source = source("assimilation/AssimilationSystem.java");
        assertTrue(source.contains("AssimilationStateStore"));
        assertTrue(source.contains("AssimilationPurgeSystem"));
        assertTrue(source.contains("AssimilationRescueSystem"));
        assertTrue(source.contains("AssimilationFrozenBodySystem"));
        assertFalse(source.contains("new WeakHashMap"));
        assertFalse(source.contains("CompoundTag"));
    }

    @Test
    void coverageSamplerDelegatesSplashDebugAndDiagnostics() throws IOException {
        String source = source("mud/MudCoverageSampler.java");
        assertTrue(source.contains("MudSplashPlayerPainter"));
        assertTrue(source.contains("MudDebugSynchronizer"));
        assertTrue(source.contains("MudCoverageDiagnostics"));
        assertFalse(source.contains("private static String debugServerState"));
        assertFalse(source.contains("new boolean[MudBodyPart.SURFACE_COUNT]"));
    }

    @Test
    void screenOverlayDoesNotOwnWorldSamplingOrDebugDrawing() throws IOException {
        String source = source("client/ScreenMudOverlay.java");
        assertTrue(source.contains("ScreenMudWorldSampler"));
        assertTrue(source.contains("ScreenMudDebugRenderer"));
        assertFalse(source.contains("private static void renderVisionDebug"));
        assertFalse(source.contains("FACE_VISION_BLOCK_CACHE"));
    }

    @Test
    void surfaceManagerDelegatesBubbleAndBridgeIntegration() throws IOException {
        String source = source("client/MudSurfaceEffectManager.java");
        assertTrue(source.contains("MudSurfaceBubbleSystem"));
        assertTrue(source.contains("extends MudAdhesionStrandState"));
        assertFalse(source.contains("private Vec3 bridgeLateralAxis"));
        assertFalse(source.contains("PendingProbeBubble"));
    }

    @Test
    void eruptionDomainReusesSurfaceAndSplashOwners() throws IOException {
        String facade = source("eruption/MudEruptionSystem.java");
        String server = source("eruption/MudEruptionLevelState.java");
        String client = source("client/MudSurfaceEffectManager.java");
        assertTrue(server.contains("MudEruptionSurfaceSampler"));
        assertTrue(server.contains("MudSplashSystem.spawnFountain"));
        assertTrue(server.contains("hasObserverNear"));
        assertTrue(server.contains("getChunkSource().hasChunk"));
        assertTrue(client.contains("transferSurfaceCells"));
        assertFalse(facade.contains("MudSplashSystem"));
        assertFalse(server.contains("DynamicTexture"));
        assertFalse(server.contains("addFreshEntity"));
    }

    @Test
    void sableFacadeDelegatesReflectionAndPoseOwnership() throws IOException {
        String source = source("compat/sable/SableCompat.java");
        assertTrue(source.contains("SableReflectionApi.api()"));
        assertTrue(source.contains("SablePoseTransform.position"));
        assertTrue(source.contains("SablePoseTransform.rigid"));
        assertFalse(source.contains("Class.forName(SABLE_CLASS)"));
        assertFalse(source.contains("LOGICAL_POSE_METHODS"));
    }

    @Test
    void removedHighPrecisionCompatibilityHasNoRuntimeEntrypoints() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java", "com", "fish", "mirebound");
        try (var paths = Files.walk(sourceRoot)) {
            assertTrue(paths.filter(path -> path.toString().endsWith(".java"))
                    .noneMatch(path -> {
                        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return name.contains("ysm") || name.contains("entitymudrenderfallback");
                    }));
        }
        String mixins = Files.readString(Path.of("src", "main", "resources", "mirebound.mixins.json"));
        assertFalse(mixins.contains("compat.ysm"));
        assertTrue(Files.isRegularFile(sourceRoot.resolve("client/entitycoverage/EntityMudRenderLayer.java")));
    }

    @Test
    void removedHighPrecisionSymbolsDoNotReturnThroughAnotherClass() throws IOException {
        Path sourceRoot = Path.of("src", "main");
        List<String> removedSymbols = List.of(
                "Ysm", "ysm", "YesSteveModel", "EntityMudRenderFallback", "projectCaptured");
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            for (String symbol : removedSymbols) {
                                if (source.contains(symbol)) {
                                    violations.add(sourceRoot.relativize(path) + " contains " + symbol);
                                }
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException("Unable to inspect " + path, exception);
                        }
                    });
        }
        assertTrue(violations.isEmpty(), () -> "Removed high-precision symbols returned:\n"
                + String.join("\n", violations));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src", "main", "java", "com", "fish", "mirebound")
                .resolve(relative.replace('/', java.io.File.separatorChar)));
    }
}
