package com.fish.mirebound.mud.tuning;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fish.mirebound.mud.MudBlockProfileStore;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.SinkingMedium;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

class MudTuningProfileSummaryTest {
    @Test
    void repeatedUniformProfileDoesNotAllocateAVectorPerBlock() {
        var bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocations = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        double[] baseline = defaults();
        MudTuningProfileSummary summary = new MudTuningProfileSummary();
        summary.offer(baseline, null);
        long thread = Thread.currentThread().threadId();
        long before = allocations.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 262_144; i++) {
            summary.offer(baseline, null);
        }
        long allocated = allocations.getThreadAllocatedBytes(thread) - before;
        assertTrue(allocated < 512 * 1024,
                () -> "Uniform aggregation allocated " + allocated + " bytes");
        assertArrayEquals(baseline, summary.displayed(baseline));
    }

    @Test
    void fullUniformSelectionKeepsItsValuesAndReturnsADefensiveCopy() {
        double[] baseline = defaults();
        MudTuningProfileSummary summary = new MudTuningProfileSummary();
        for (int i = 0; i < 262_144; i++) {
            summary.offer(baseline, null);
        }
        assertArrayEquals(baseline, summary.displayed(baseline));
        summary.displayed(baseline)[0] = -100;
        assertArrayEquals(baseline, summary.displayed(baseline));
    }

    @Test
    void repeatedLocalProfilePreservesItsCustomValues() {
        double[] baseline = defaults();
        double[] custom = baseline.clone();
        custom[MudPhysicsParameter.WALK_SURFACE.ordinal()] = 0.25D;
        MudBlockProfileStore.Profile local = MudBlockProfileStore.Profile.create(SinkingMedium.MUD, custom);
        MudTuningProfileSummary summary = new MudTuningProfileSummary();
        for (int i = 0; i < 1000; i++) {
            summary.offer(baseline, local);
        }
        assertArrayEquals(local.values(), summary.displayed(baseline));
    }

    @Test
    void mixedParametersStayMixedAfterReturningToTheFirstProfile() {
        double[] baseline = defaults();
        double[] custom = baseline.clone();
        custom[MudPhysicsParameter.WALK_SURFACE.ordinal()] = 0.25D;
        MudBlockProfileStore.Profile local = MudBlockProfileStore.Profile.create(SinkingMedium.MUD, custom);
        MudTuningProfileSummary summary = new MudTuningProfileSummary();
        summary.offer(baseline, local);
        summary.offer(baseline, null);
        summary.offer(baseline, local);
        assertArrayEquals(baseline, summary.displayed(baseline));
    }

    @Test
    void distinctBaselinesRespectExistingDisplayRounding() {
        double[] first = defaults();
        double[] second = first.clone();
        int index = MudPhysicsParameter.WALK_SURFACE.ordinal();
        first[index] = 0.2500D;
        second[index] = 0.2501D;
        MudTuningProfileSummary summary = new MudTuningProfileSummary();
        summary.offer(first, null);
        summary.offer(second, null);
        assertEquals(first[index], summary.displayed(defaults())[index]);
    }

    private static double[] defaults() {
        return MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
    }
}
