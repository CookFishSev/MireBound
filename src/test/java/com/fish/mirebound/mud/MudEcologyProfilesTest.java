package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MudEcologyProfilesTest {
    @Test
    void everyMediumDefaultsToAFullBlock() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            assertShape(medium, MudShapeType.FULL, 1.0D);
        }
    }

    @Test
    void legacyEcologyShapesRemainAvailableAsExplicitSpecialVariants() {
        assertSpecial(SinkingMedium.THIN_MUD, MudShapeType.STATIC_HEIGHT, 4);
        assertSpecial(SinkingMedium.SHALLOW_MUD, MudShapeType.STATIC_HEIGHT, 8);
        assertSpecial(SinkingMedium.TIDAL_MUD, MudShapeType.STATIC_HEIGHT, 14);
        assertSpecial(SinkingMedium.LIVING_SLIME, MudShapeType.STATIC_HEIGHT, 14);
        assertSpecial(SinkingMedium.PEAT_BOG, MudShapeType.IRREGULAR_PILE, 10);
        assertSpecial(SinkingMedium.JUNGLE_QUICKSAND, MudShapeType.SPECIAL_MODEL, 14);
        assertTrue(!MudShapeProfile.supportsSpecial(SinkingMedium.INSECT_MOUND));
    }

    @Test
    void specialMediaHaveStableUniqueIdsAndBehaviors() {
        Set<Integer> ids = new HashSet<>();
        for (SinkingMedium medium : SinkingMedium.values()) {
            assertTrue(ids.add(medium.id()), "duplicate medium id " + medium.id());
            assertEquals(medium.ordinal(), medium.id());
            assertEquals(medium, SinkingMedium.byId(medium.id()));
        }
        assertEquals(SinkingMedium.COUNT, ids.size());
        assertEquals(MudBehaviorType.SWARM, SinkingMedium.INSECT_MOUND.defaultBehaviorType());
        assertEquals(MudBehaviorType.ORDINARY, SinkingMedium.END_SILT.defaultBehaviorType());
        assertEquals(MudBehaviorType.ORDINARY, SinkingMedium.SCULK_MIRE.defaultBehaviorType());
        assertEquals(MudBehaviorType.CONTRACTILE, SinkingMedium.TENDER_FLESH.defaultBehaviorType());
        assertEquals(MudBehaviorType.ORDINARY, SinkingMedium.MIRE.defaultBehaviorType());
    }

    @Test
    void ecologyBudgetsAndRatesStayInsideTheirConfiguredSafetyBounds() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            double[] values = MudPhysicsProfiles.defaultValues(medium);
            for (MudPhysicsParameter parameter : MudPhysicsParameter.forMedium(medium)) {
                double value = values[parameter.ordinal()];
                assertTrue(value >= parameter.minimum() && value <= parameter.maximum(),
                        medium + "/" + parameter + "=" + value);
            }
        }
    }

    @Test
    void insectMoundUsesTranslucentSkinCoverageAndSwarmPresentation() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.INSECT_MOUND);
        assertEquals(1.0D, values[MudPhysicsParameter.POLLUTION_MULTIPLIER.ordinal()], 1.0E-9D);
        assertTrue(!SinkingMedium.INSECT_MOUND.opaqueCoverage());
        assertTrue(values[MudPhysicsParameter.SWARM_INSECT_SCALE.ordinal()] > 1.0D);
        assertTrue(values[MudPhysicsParameter.SWARM_SILK_DENSITY.ordinal()] > 0.0D);
        assertTrue(values[MudPhysicsParameter.SWARM_SILK_OPACITY.ordinal()] > 0.0D);
    }

    @Test
    void redQuicksandIsAnOrdinaryTranslucentSandMedium() {
        assertEquals(MudBehaviorType.ORDINARY, SinkingMedium.RED_QUICKSAND.defaultBehaviorType());
        assertTrue(SinkingMedium.RED_QUICKSAND.liquefies());
        assertTrue(!SinkingMedium.RED_QUICKSAND.opaqueCoverage());
    }

    @Test
    void newOrdinaryMediaKeepTheirMaterialCoverageClasses() {
        assertTrue(SinkingMedium.ASH_QUICKSAND.liquefies());
        assertTrue(SinkingMedium.SOUL_SILT.liquefies());
        assertTrue(!SinkingMedium.ASH_QUICKSAND.opaqueCoverage());
        assertTrue(!SinkingMedium.SOUL_SILT.opaqueCoverage());
        assertTrue(SinkingMedium.GEL_CLAY.opaqueCoverage());
        assertTrue(SinkingMedium.LIME_MUD.opaqueCoverage());
        assertEquals(MudBehaviorType.ORDINARY, SinkingMedium.GEL_CLAY.defaultBehaviorType());
        assertTrue(!SinkingMedium.END_SILT.opaqueCoverage());
        assertTrue(SinkingMedium.SCULK_MIRE.opaqueCoverage());
        assertTrue(SinkingMedium.GRAVEL_SILT.liquefies());
        assertTrue(!SinkingMedium.GRAVEL_SILT.opaqueCoverage());
        assertTrue(SinkingMedium.FUNGAL_MIRE.opaqueCoverage());
        assertTrue(SinkingMedium.STONE_CLAY.opaqueCoverage());
        assertTrue(!SinkingMedium.PALE_MIRE.liquefies());
        assertTrue(!SinkingMedium.PEAT_SILT.liquefies());
        assertTrue(SinkingMedium.PALE_MIRE.opaqueCoverage());
        assertTrue(SinkingMedium.PEAT_SILT.opaqueCoverage());
    }

    @Test
    void coverageExtensionsAreGenericPerMedium() {
        double[] tenderValues = MudPhysicsProfiles.defaultValues(SinkingMedium.TENDER_FLESH);
        double[] mireValues = MudPhysicsProfiles.defaultValues(SinkingMedium.MIRE);
        assertEquals(600.0D,
                tenderValues[MudPhysicsParameter.COVERAGE_NATURAL_FADE_TICKS.ordinal()], 1.0E-9D);
        assertEquals(0.0D,
                mireValues[MudPhysicsParameter.COVERAGE_NATURAL_FADE_TICKS.ordinal()], 1.0E-9D);
        assertTrue(SinkingMedium.TENDER_FLESH.translucentSkinCoverage());
        assertTrue(!SinkingMedium.ASSIMILATION_SLIME.opaqueCoverage());
        assertTrue(SinkingMedium.ASSIMILATION_SLIME.translucentSkinCoverage());
        assertEquals(0.60D,
                SinkingMedium.ASSIMILATION_SLIME.defaultCoverageOpacity(), 1.0E-9D);
        assertTrue(SinkingMedium.ASSIMILATION_SLIME.coverTexture()
                .getPath().endsWith("textures/block/assimilation_slime.png"));
        assertTrue(!SinkingMedium.TENDER_FLESH.skinCoverageTexture()
                .equals(SinkingMedium.TENDER_FLESH.coverTexture()));
        assertTrue(SinkingMedium.MIRE.skinCoverageTexture().getPath().endsWith("textures/block/mire.png"));
    }

    private static void assertShape(SinkingMedium medium, MudShapeType shape, double height) {
        double[] values = MudPhysicsProfiles.defaultValues(medium);
        assertEquals(shape, medium.defaultShapeType());
        assertEquals(height, values[MudPhysicsParameter.SURFACE_HEIGHT.ordinal()], 1.0E-9D);
    }

    private static void assertSpecial(SinkingMedium medium, MudShapeType shape, int heightPixels) {
        assertTrue(MudShapeProfile.supportsSpecial(medium));
        MudShapeProfile profile = MudShapeProfile.special(medium);
        assertEquals(shape, profile.type());
        assertEquals(heightPixels, profile.heightPixels());
    }
}
