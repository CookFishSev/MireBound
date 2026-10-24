package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenderFleshPoolRulesTest {
    @Test
    void completePoolQualifiesAtBlockCentersAndAcrossEverySeam() {
        for (double x : new double[] {0.0D, 0.1D, 0.5D, 0.9D, 1.0D}) {
            for (double z : new double[] {0.0D, 0.1D, 0.5D, 0.9D, 1.0D}) {
                Set<String> columns = new HashSet<>();
                assertTrue(TenderFleshPoolRules.supportsFootprint(
                        TenderFleshProfile.DEFAULT, 2.0D, x, z, 0.30D,
                        (columnX, columnZ, layers) -> {
                            assertTrue(columns.add(columnX + ":" + columnZ));
                            assertEquals(2, layers);
                            return columnX >= -1 && columnX <= 1
                                    && columnZ >= -1 && columnZ <= 1;
                        }), "Rejected interior point " + x + "," + z);
                assertTrue(columns.size() <= 9);
            }
        }
    }

    @Test
    void smallestPoolWorksAtItsCenterButRejectsRootsOutsideTheEdge() {
        TenderFleshPoolRules.ColumnSupport pool = (x, z, layers) ->
                x >= 0 && x <= 1 && z >= 0 && z <= 1;
        assertTrue(TenderFleshPoolRules.supportsFootprint(
                TenderFleshProfile.DEFAULT, 2.0D, 1.0D, 1.0D, 0.30D, pool));
        assertFalse(TenderFleshPoolRules.supportsFootprint(
                TenderFleshProfile.DEFAULT, 2.0D, 0.2D, 1.0D, 0.30D, pool));
        assertFalse(TenderFleshPoolRules.supportsFootprint(
                TenderFleshProfile.DEFAULT, 1.0D, 1.0D, 1.0D, 0.30D, pool));
        assertFalse(TenderFleshPoolRules.supportsFootprint(
                TenderFleshProfile.DEFAULT, 2.0D, 1.0D, 1.0D, 0.30D,
                (x, z, layers) -> pool.contains(x, z, layers) && !(x == 1 && z == 1)));
    }

    @Test
    void qualifiedBlockCenterTriggersServerEnclosureWithoutAdhesion() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.TENDER_FLESH);
        TenderFleshProfile profile = TenderFleshProfile.fromValues(values);
        boolean allowed = TenderFleshPoolRules.supportsFootprint(
                profile, 2.0D, 0.5D, 0.5D, 0.30D, (x, z, layers) -> true);
        TenderFleshRuntimeState state = new TenderFleshRuntimeState();
        TenderFleshMechanics.step(profile, state, new TenderFleshMechanics.Input(
                1L, 0.8D, 0.2D, 0.0D, 0.0D, false, -1.0D,
                0.0D, -0.01D, 0.0D, 0.01D, allowed,
                0.5D, 64.0D, 0.5D, 0.5D, 0.5D));
        assertTrue(state.enclosureActive);
        assertTrue(state.enclosureProgress > 0.0D);
        assertFalse(AdhesionStrandProfile.fromValues(values).enabled());
    }

    @Test
    void oversizedPlayersDoNotCauseUnboundedPoolScans() {
        assertFalse(TenderFleshPoolRules.supportsFootprint(
                TenderFleshProfile.DEFAULT, 2.0D, 0.5D, 0.5D, 30.0D,
                (x, z, layers) -> { throw new AssertionError("Unexpected world query"); }));
    }
}
