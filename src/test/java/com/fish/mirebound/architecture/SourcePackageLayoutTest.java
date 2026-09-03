package com.fish.mirebound.architecture;

import com.fish.mirebound.command.MudCommands;
import com.fish.mirebound.content.mudwork.MudSlingItem;
import com.fish.mirebound.content.mudwork.MudBallProjectile;
import com.fish.mirebound.content.mudwork.WetAdobeBlock;
import com.fish.mirebound.coverage.MudCoverageService;
import com.fish.mirebound.coverage.MudSkinCoverageOperations;
import com.fish.mirebound.coverage.MudVisionSamplingLayout;
import com.fish.mirebound.coverage.armor.ArmorTextureMudManager;
import com.fish.mirebound.eruption.MudEruptionDynamics;
import com.fish.mirebound.eruption.MudEruptionProfile;
import com.fish.mirebound.eruption.MudEruptionSystem;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsProfile;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsSystem;
import com.fish.mirebound.physics.MudMovementControl;
import com.fish.mirebound.network.ServerInputBudget;
import com.fish.mirebound.splash.MudSplashImpactDetector;
import com.fish.mirebound.splash.MudSplashProfile;
import com.fish.mirebound.splash.MudSplashSystem;
import com.fish.mirebound.stain.MudDecalAccess;
import com.fish.mirebound.stain.MudFootprintBlockEntity;
import com.fish.mirebound.stain.MudFootprintSystem;
import com.fish.mirebound.stain.MudWallStainSystem;
import com.fish.mirebound.tool.MudProbeItem;
import com.fish.mirebound.water.MudWashingSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourcePackageLayoutTest {
    @Test
    void domainOwnersStayOutsideTheMudCorePackage() {
        assertAll(
                () -> assertEquals("com.fish.mirebound.command", MudCommands.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.content.mudwork",
                        MudSlingItem.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.content.mudwork",
                        MudBallProjectile.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.content.mudwork",
                        WetAdobeBlock.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.coverage", MudCoverageService.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.coverage",
                        MudSkinCoverageOperations.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.coverage",
                        MudVisionSamplingLayout.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.coverage.armor",
                        ArmorTextureMudManager.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.eruption",
                        MudEruptionDynamics.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.eruption",
                        MudEruptionProfile.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.eruption",
                        MudEruptionSystem.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.itemphysics",
                        DroppedItemPhysicsProfile.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.itemphysics",
                        DroppedItemPhysicsSystem.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.physics",
                        MudMovementControl.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.network",
                        ServerInputBudget.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.splash",
                        MudSplashImpactDetector.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.splash",
                        MudSplashProfile.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.splash", MudSplashSystem.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.stain", MudDecalAccess.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.stain", MudFootprintBlockEntity.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.stain", MudFootprintSystem.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.stain", MudWallStainSystem.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.tool", MudProbeItem.class.getPackageName()),
                () -> assertEquals("com.fish.mirebound.water", MudWashingSystem.class.getPackageName()));
    }

    @Test
    void productionSourcePackagesMatchTheirDirectory() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java").toAbsolutePath().normalize();
        Pattern packagePattern = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String declaredPackage = Files.readAllLines(path).stream()
                                    .map(packagePattern::matcher)
                                    .filter(matcher -> matcher.matches())
                                    .map(matcher -> matcher.group(1))
                                    .findFirst()
                                    .orElse("");
                            String expectedPackage = sourceRoot.relativize(path.getParent()).toString()
                                    .replace('\\', '.')
                                    .replace('/', '.');
                            if (!expectedPackage.equals(declaredPackage)) {
                                violations.add(sourceRoot.relativize(path) + " declares "
                                        + declaredPackage + ", expected " + expectedPackage);
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException("Unable to inspect " + path, exception);
                        }
                    });
        }
        assertTrue(violations.isEmpty(), () -> "Production package layout violations:\n"
                + String.join("\n", violations));
    }
}
