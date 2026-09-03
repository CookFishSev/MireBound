package com.fish.mirebound.assimilation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AssimilationSoulMudIsolationTest {
    @Test
    void frozenBodyStopsRigidFallWhenItStillTouchesMud() throws Exception {
        String source = source("assimilation/AssimilationFrozenBodySystem.java");
        assertTrue(source.contains("MudPhysics.hasSinkingContact(player)"));
        assertTrue(source.contains("state.rigidVelocity = Vec3.ZERO"));
    }

    @Test
    void frozenPlayersCannotRefreshTopOrSideCompression() throws Exception {
        String top = source("client/MudSurfaceEffectManager.java");
        String side = source("client/MudSideSurfaceEffectManager.java");
        String guard = "ClientAssimilationState.isFrozen(player.getId())";

        assertTrue(top.contains(guard));
        assertTrue(side.contains(guard));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/fish/mirebound")
                .resolve(relative.replace('/', java.io.File.separatorChar)));
    }
}
