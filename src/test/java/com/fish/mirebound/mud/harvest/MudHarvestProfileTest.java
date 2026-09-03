package com.fish.mirebound.mud.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.SinkingMedium;
import org.junit.jupiter.api.Test;

class MudHarvestProfileTest {
    @Test
    void defaultsFollowVanillaMaterialToolConventions() {
        assertEquals(MudHarvestTool.HOE, profile(SinkingMedium.SCULK_MIRE).preferredTool());
        assertEquals(MudHarvestTool.HOE, profile(SinkingMedium.FUNGAL_MIRE).preferredTool());
        assertEquals(MudHarvestTool.PICKAXE, profile(SinkingMedium.STONE_CLAY).preferredTool());
        assertEquals(MudHarvestTool.SWORD, profile(SinkingMedium.TENDER_FLESH).preferredTool());
        assertEquals(MudHarvestTool.NONE, profile(SinkingMedium.LIVING_SLIME).preferredTool());
        assertEquals(MudHarvestTool.SHOVEL, profile(SinkingMedium.SOFT_QUICKSAND).preferredTool());
    }

    @Test
    void everyDefaultRequiresAVisibleHandMiningInterval() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            MudHarvestProfile profile = profile(medium);
            assertTrue(profile.hardness() >= 0.75D, medium + " hardness is too low");
            assertTrue(profile.handSpeedMultiplier() <= 1.0D,
                    medium + " hand multiplier is unexpectedly accelerated");
        }
    }

    @Test
    void breakSpeedNormalizesRegistryHardnessAndToolRules() {
        assertEquals(0.25F, MudHarvestSystem.scaledBreakSpeed(
                1.0F, 0.5F, 2.0D, 1.0F, 1.0F, 1.0D), 1.0E-6F);
        assertEquals(2.0F, MudHarvestSystem.scaledBreakSpeed(
                1.0F, 0.5F, 2.0D, 1.0F, 8.0F, 1.0D), 1.0E-6F);
        assertEquals(0.0F, MudHarvestSystem.scaledBreakSpeed(
                1.0F, 0.5F, 2.0D, 1.0F, 1.0F, 0.0D), 1.0E-6F);
    }

    private static MudHarvestProfile profile(SinkingMedium medium) {
        return MudHarvestProfile.fromValues(MudPhysicsProfiles.defaultValues(medium));
    }
}
