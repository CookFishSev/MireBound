package com.fish.mirebound.assimilation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AssimilationPartialPurgeTest {
    @Test
    void cursorBouncesWithoutLeavingBar() {
        AssimilationPartialPurge.Cursor cursor =
                AssimilationPartialPurge.advance(0.98F, true, 20);
        assertEquals(0.97F, cursor.position(), 0.0001F);
        assertFalse(cursor.forward());

        cursor = AssimilationPartialPurge.advance(0.01F, false, 20);
        assertEquals(0.04F, cursor.position(), 0.0001F);
        assertTrue(cursor.forward());
    }

    @Test
    void judgementIncludesBothZoneEdges() {
        assertTrue(AssimilationPartialPurge.succeeds(0.25F, 0.25F, 0.50F));
        assertTrue(AssimilationPartialPurge.succeeds(0.50F, 0.25F, 0.50F));
        assertFalse(AssimilationPartialPurge.succeeds(0.51F, 0.25F, 0.50F));
    }

    @Test
    void failureCanNeverKillPlayer() {
        assertEquals(1.0F, AssimilationPartialPurge.nonLethalHealth(1.0F, 1.0F));
        assertEquals(1.0F, AssimilationPartialPurge.nonLethalHealth(1.5F, 8.0F));
        assertEquals(7.0F, AssimilationPartialPurge.nonLethalHealth(8.0F, 1.0F));
    }

    @Test
    void purgePreservesMixedMediumRatio() {
        float[] values = new float[SinkingMedium.COUNT];
        values[SinkingMedium.ASSIMILATION_SLIME.id()] = 0.60F;
        values[SinkingMedium.RED_QUICKSAND.id()] = 0.20F;

        float removed = AssimilationContributions.removeProportional(values, 0.20F);

        assertEquals(0.20F, removed, 0.0001F);
        assertEquals(0.45F, values[SinkingMedium.ASSIMILATION_SLIME.id()], 0.0001F);
        assertEquals(0.15F, values[SinkingMedium.RED_QUICKSAND.id()], 0.0001F);
        assertEquals(0.60F, AssimilationContributions.total(values), 0.0001F);
    }

    @Test
    void activeAssimilationMudBlocksPartialPurge() {
        assertTrue(AssimilationSystem.blocksPartialPurge(true, 0L, true));
        assertTrue(AssimilationSystem.blocksPartialPurge(true, 1L, true));
        assertFalse(AssimilationSystem.blocksPartialPurge(false, 0L, true));
        assertFalse(AssimilationSystem.blocksPartialPurge(true, 0L, false));
        assertFalse(AssimilationSystem.blocksPartialPurge(true, 2L, true));
    }

    @Test
    void movementCancellationIgnoresStationaryPositionAndTinyCorrection() {
        Vec3 origin = new Vec3(12.0D, 64.0D, -4.0D);

        assertFalse(AssimilationPurgeSystem.movedFromOrigin(origin, origin));
        assertFalse(AssimilationPurgeSystem.movedFromOrigin(
                origin, origin.add(0.005D, 0.0D, 0.0D)));
        assertTrue(AssimilationPurgeSystem.movedFromOrigin(
                origin, origin.add(0.02D, 0.0D, 0.0D)));
    }

    @Test
    void movementCancellationRespectsProfileToggle() {
        AssimilationState state = new AssimilationState();
        state.partialPurgeActive = true;

        assertTrue(AssimilationPurgeSystem.cancelForMovement(
                state, AssimilationProfile.DEFAULT));
        assertFalse(state.partialPurgeActive);

        state.partialPurgeActive = true;
        double[] values = new double[
                com.fish.mirebound.mud.MudPhysicsParameter.COUNT];
        AssimilationProfile.DEFAULT.writeTo(values);
        values[com.fish.mirebound.mud.MudPhysicsParameter
                .ASSIMILATION_PARTIAL_PURGE_CANCEL_ON_MOVE.ordinal()] = 0.0D;
        AssimilationProfile disabled = AssimilationProfile.fromValues(values);
        assertFalse(AssimilationPurgeSystem.cancelForMovement(state, disabled));
        assertTrue(state.partialPurgeActive);
    }
}
