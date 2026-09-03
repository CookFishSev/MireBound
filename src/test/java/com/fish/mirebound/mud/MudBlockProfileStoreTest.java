package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MudBlockProfileStoreTest {
    @Test
    void identicalSanitizedProfilesFollowTheirInheritedBaseline() {
        assertTrue(MudBlockProfileStore.sameValues(
                new double[] {0.0D, 0.5D, 1.0D},
                new double[] {0.0D, 0.5D, 1.0D}));
    }

    @Test
    void aRealDifferenceKeepsTheLocalProfile() {
        assertFalse(MudBlockProfileStore.sameValues(
                new double[] {0.0D, 0.5D, 1.0D},
                new double[] {0.0D, 0.5D, 0.0D}));
    }

    @Test
    void ordinaryHeightVariantIsNotAProfileModification() {
        assertFalse(MudBlockProfileStore.shapeCountsAsModified(MudBlockVariant.HEIGHT));
    }

    @Test
    void floatNetworkRoundTripDoesNotCreateAProfileDifference() {
        double baseline = 0.055D;

        assertTrue(MudBlockProfileStore.sameSyncedValue((float) baseline, baseline));
        assertFalse(MudBlockProfileStore.sameSyncedValue(0.065D, baseline));
    }

    @Test
    void legacyMudProfileLoadsAsNativeAndKeepsFiniteFlow() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.FLOW_ENABLED.ordinal()] = 1.0D;

        MudBlockProfileStore.Profile profile = MudBlockProfileStore.loadProfile(
                9, new CompoundTag(), SinkingMedium.MUD, values);

        assertFalse(profile.adaptive());
        assertTrue(profile.flow().enabled());
    }

    @Test
    void currentAdaptiveProfileUsesItsRestrictedParameterSet() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        values[MudPhysicsParameter.FLOW_ENABLED.ordinal()] = 1.0D;
        CompoundTag entry = new CompoundTag();
        entry.putBoolean("Adaptive", true);

        MudBlockProfileStore.Profile profile = MudBlockProfileStore.loadProfile(
                10, entry, SinkingMedium.MUD, values);

        assertTrue(profile.adaptive());
        assertFalse(profile.flow().enabled());
    }
}
