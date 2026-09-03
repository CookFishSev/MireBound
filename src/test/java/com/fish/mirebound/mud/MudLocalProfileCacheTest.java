package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.network.payload.MudLocalProfilesPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MudLocalProfileCacheTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");

    @AfterEach
    void clearCache() {
        MudLocalProfileCache.reset();
    }

    @Test
    void replacesAChunkAtomicallyAndKeepsOtherDimensionsSeparate() {
        BlockPos pos = new BlockPos(18, 64, 33);
        float[] values = floats(MudPhysicsProfiles.defaultValues(SinkingMedium.TAR));
        values[MudPhysicsParameter.SURFACE_BUBBLE_RATE.ordinal()] = 0.37F;
        MudLocalProfileCache.accept(new MudLocalProfilesPayload(
                DIMENSION, 1, 2, true,
                List.of(new MudLocalProfilesPayload.Palette(values)),
                List.of(new MudLocalProfilesPayload.Entry(pos.asLong(), SinkingMedium.TAR.id(), 0))));

        assertEquals(0.37D, MudLocalProfileCache.valueForTests(
                DIMENSION, pos, SinkingMedium.TAR, MudPhysicsParameter.SURFACE_BUBBLE_RATE), 1.0E-6D);
        assertTrue(Double.isNaN(MudLocalProfileCache.valueForTests(
                ResourceLocation.withDefaultNamespace("the_nether"), pos,
                SinkingMedium.TAR, MudPhysicsParameter.SURFACE_BUBBLE_RATE)));

        MudLocalProfileCache.accept(new MudLocalProfilesPayload(
                DIMENSION, 1, 2, true, List.of(), List.of()));
        assertEquals(0, MudLocalProfileCache.profileCountForTests());
    }

    @Test
    void reusesOneDecodedProfileForEveryEntryUsingTheSamePalette() {
        BlockPos first = new BlockPos(18, 64, 33);
        BlockPos second = new BlockPos(19, 64, 33);
        float[] values = floats(MudPhysicsProfiles.defaultValues(SinkingMedium.TAR));

        MudLocalProfileCache.accept(new MudLocalProfilesPayload(
                DIMENSION, 1, 2, true,
                List.of(new MudLocalProfilesPayload.Palette(values)),
                List.of(
                        new MudLocalProfilesPayload.Entry(
                                first.asLong(), SinkingMedium.TAR.id(), 0),
                        new MudLocalProfilesPayload.Entry(
                                second.asLong(), SinkingMedium.TAR.id(), 0))));

        assertEquals(2, MudLocalProfileCache.profileCountForTests());
        assertEquals(1, MudLocalProfileCache.uniqueProfileCountForTests());
    }

    @Test
    void activeProfileUpdateSurvivesWithoutReplacingTheRestOfTheChunk() {
        BlockPos first = new BlockPos(18, 64, 33);
        BlockPos second = new BlockPos(19, 64, 33);
        float[] initial = floats(MudPhysicsProfiles.defaultValues(SinkingMedium.MUD));
        MudLocalProfileCache.accept(new MudLocalProfilesPayload(
                DIMENSION, 1, 2, true,
                List.of(new MudLocalProfilesPayload.Palette(initial)),
                List.of(
                        new MudLocalProfilesPayload.Entry(first.asLong(), SinkingMedium.MUD.id(), 0),
                        new MudLocalProfilesPayload.Entry(second.asLong(), SinkingMedium.MUD.id(), 0))));

        double[] activeValues = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        activeValues[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()] = 1.0D;
        activeValues[MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal()] = 1.0D;
        MudLocalProfilesPayload active = MudLocalProfileSync.activeProfilePayload(
                DIMENSION, first,
                MudBlockProfileStore.Profile.create(SinkingMedium.MUD, activeValues));

        assertFalse(active.replaceChunk());
        MudLocalProfileCache.accept(active);
        assertEquals(1.0D, MudLocalProfileCache.valueForTests(
                DIMENSION, first, SinkingMedium.MUD,
                MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH), 1.0E-6D);
        assertEquals(initial[MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal()],
                MudLocalProfileCache.valueForTests(
                        DIMENSION, second, SinkingMedium.MUD,
                        MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH), 1.0E-6D);
    }

    private static float[] floats(double[] source) {
        float[] result = new float[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = (float) source[index];
        }
        return result;
    }
}
