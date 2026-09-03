package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MudCapePersistenceTest {
    @Test
    void capeCoverageSurvivesPersistentRoundTrip() {
        MudPlayerData source = new MudPlayerData();
        source.applyCapeSample(MudCapeLayout.Side.OUTER, 9, 6, 1.0F, SinkingMedium.TAR);
        source.applyCapeSample(MudCapeLayout.Side.INNER, 9, 6, 0.8F, SinkingMedium.SOFT_QUICKSAND);

        MudPlayerData loaded = new MudPlayerData();
        loaded.loadPersistent(source.savePersistent());

        assertTrue(loaded.capeCoverage[MudCapeLayout.index(MudCapeLayout.Side.OUTER, 9, 6)] > 0.5F);
        assertTrue(loaded.capeCoverage[MudCapeLayout.index(MudCapeLayout.Side.INNER, 9, 6)] > 0.4F);
        assertEquals(SinkingMedium.TAR,
                loaded.capePixelMedium(MudCapeLayout.Side.OUTER, 9, 6));
        assertEquals(SinkingMedium.SOFT_QUICKSAND,
                loaded.capePixelMedium(MudCapeLayout.Side.INNER, 9, 6));
    }

    @Test
    void legacySingleFaceCapeMigratesToOuterFaceOnly() {
        MudPlayerData source = new MudPlayerData();
        source.applyCapeSample(MudCapeLayout.Side.OUTER, 4, 3, 1.0F, SinkingMedium.PEAT_BOG);
        CompoundTag legacy = source.savePersistent();
        legacy.putInt("Version", 4);
        legacy.putByteArray("CapeCoverage", Arrays.copyOf(
                legacy.getByteArray("CapeCoverage"), MudCapeLayout.FACE_CELL_COUNT));
        legacy.putByteArray("CapeMedium", Arrays.copyOf(
                legacy.getByteArray("CapeMedium"), MudCapeLayout.FACE_CELL_COUNT));

        MudPlayerData loaded = new MudPlayerData();
        loaded.loadPersistent(legacy);

        assertTrue(loaded.capeCoverage[MudCapeLayout.index(MudCapeLayout.Side.OUTER, 4, 3)] > 0.5F);
        assertEquals(0.0F,
                loaded.capeCoverage[MudCapeLayout.index(MudCapeLayout.Side.INNER, 4, 3)]);
        assertEquals(SinkingMedium.MUD,
                loaded.capePixelMedium(MudCapeLayout.Side.INNER, 4, 3));
    }

    @Test
    void versionThreeExactSkinCoverageStillLoadsAfterCapeUpgrade() {
        MudPlayerData source = new MudPlayerData();
        source.setSurfacePixelCoverage(
                MudBodyPart.BODY, MudSurface.BACK, 5, 3, 0.72F, SinkingMedium.PEAT_BOG);
        CompoundTag legacy = source.savePersistent();
        legacy.putInt("Version", 3);
        legacy.remove("CapeCoverage");
        legacy.remove("CapeMedium");

        MudPlayerData loaded = new MudPlayerData();
        loaded.loadPersistent(legacy);

        assertTrue(loaded.surfacePixelCoverage(
                MudBodyPart.BODY, MudSurface.BACK, 5, 3) > 0.70F);
        assertEquals(SinkingMedium.PEAT_BOG,
                loaded.surfacePixelMedium(MudBodyPart.BODY, MudSurface.BACK, 5, 3));
    }

    @Test
    void deathClearsCapeCoverage() {
        MudPlayerData data = new MudPlayerData();
        data.applyCapeSample(2, 4, 1.0F, SinkingMedium.MUD);

        data.clearAfterDeath();

        assertEquals(0.0F, data.capeCoverage[MudCapeLayout.index(2, 4)]);
        assertEquals(0.0F,
                data.capeCoverage[MudCapeLayout.index(MudCapeLayout.Side.INNER, 2, 4)]);
    }
}
