package com.fish.mirebound.mud.tuning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.network.payload.MudTuningRequestPayload;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MudTuningConversionSafetyTest {
    @Test
    void freshPlayerDataStartsLockedAndPersistsAcknowledgement() {
        CompoundTag playerData = new CompoundTag();

        assertFalse(MudTuningConversionSafety.isUnlocked(playerData));
        MudTuningConversionSafety.setUnlocked(playerData, true);
        assertTrue(MudTuningConversionSafety.isUnlocked(playerData));
        MudTuningConversionSafety.setUnlocked(playerData, false);
        assertFalse(MudTuningConversionSafety.isUnlocked(playerData));
    }

    @Test
    void safetyChordAdvancesThroughUnlockThenPermanentToggle() {
        CompoundTag playerData = new CompoundTag();

        assertEquals(MudTuningConversionSafety.Change.STANDARD_UNLOCKED,
                MudTuningConversionSafety.advance(playerData));
        assertTrue(MudTuningConversionSafety.isUnlocked(playerData));
        assertFalse(MudTuningConversionSafety.isUnrestrictedUnlocked(playerData));

        assertEquals(MudTuningConversionSafety.Change.UNRESTRICTED_UNLOCKED,
                MudTuningConversionSafety.advance(playerData));
        assertTrue(MudTuningConversionSafety.isUnrestrictedUnlocked(playerData));
        assertTrue(MudTuningConversionSafety.isUnrestrictedEnabled(playerData));

        assertEquals(MudTuningConversionSafety.Change.UNRESTRICTED_DISABLED,
                MudTuningConversionSafety.advance(playerData));
        assertTrue(MudTuningConversionSafety.isUnrestrictedUnlocked(playerData));
        assertFalse(MudTuningConversionSafety.isUnrestrictedEnabled(playerData));

        assertEquals(MudTuningConversionSafety.Change.UNRESTRICTED_ENABLED,
                MudTuningConversionSafety.advance(playerData));
        assertTrue(MudTuningConversionSafety.isUnrestrictedEnabled(playerData));
    }

    @Test
    void clearingEarlierUnlockAlsoClearsUnrestrictedState() {
        CompoundTag playerData = new CompoundTag();
        MudTuningConversionSafety.setUnrestrictedEnabled(playerData, true);

        MudTuningConversionSafety.setUnlocked(playerData, false);

        assertFalse(MudTuningConversionSafety.isUnlocked(playerData));
        assertFalse(MudTuningConversionSafety.isUnrestrictedUnlocked(playerData));
        assertFalse(MudTuningConversionSafety.isUnrestrictedEnabled(playerData));
    }

    @Test
    void everyDirectAdaptiveMutationRequiresTheSafetyUnlock() {
        assertTrue(MudTuningConversionSafety.requiresUnlock(
                MudTuningRequestPayload.Action.CONVERT_SINGLE));
        assertTrue(MudTuningConversionSafety.requiresUnlock(
                MudTuningRequestPayload.Action.RESTORE_SINGLE));
        assertTrue(MudTuningConversionSafety.requiresUnlock(
                MudTuningRequestPayload.Action.CONVERT_RANGE));
        assertTrue(MudTuningConversionSafety.requiresUnlock(
                MudTuningRequestPayload.Action.RESTORE_RANGE));
        assertFalse(MudTuningConversionSafety.requiresUnlock(
                MudTuningRequestPayload.Action.OPEN_SINGLE));
    }
}
