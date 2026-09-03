package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class MudLocalProfilesPayloadTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.withDefaultNamespace("overworld");

    @Test
    void paletteCopiesAndValidatesItsValues() {
        float[] values = new float[MudPhysicsParameter.COUNT];
        values[0] = 1.0F;
        MudLocalProfilesPayload.Palette palette =
                new MudLocalProfilesPayload.Palette(values);

        values[0] = 2.0F;
        float[] exposed = palette.values();
        exposed[0] = 3.0F;

        assertEquals(1.0F, palette.values()[0]);
        assertThrows(IllegalArgumentException.class,
                () -> new MudLocalProfilesPayload.Palette(
                        new float[MudPhysicsParameter.COUNT - 1]));
        float[] nonFinite = new float[MudPhysicsParameter.COUNT];
        nonFinite[0] = Float.NaN;
        assertThrows(IllegalArgumentException.class,
                () -> new MudLocalProfilesPayload.Palette(nonFinite));
    }

    @Test
    void payloadRejectsOversizedOrInvalidEntriesBeforeEncoding() {
        MudLocalProfilesPayload.Palette palette =
                new MudLocalProfilesPayload.Palette(
                        new float[MudPhysicsParameter.COUNT]);
        MudLocalProfilesPayload.Entry entry = new MudLocalProfilesPayload.Entry(
                new BlockPos(0, 64, 0).asLong(), SinkingMedium.MUD.id(), 0);

        assertThrows(IllegalArgumentException.class, () -> new MudLocalProfilesPayload(
                DIMENSION, 0, 0, true, List.of(palette),
                Collections.nCopies(MudLocalProfilesPayload.MAX_ENTRIES + 1, entry)));
        assertThrows(IllegalArgumentException.class, () -> new MudLocalProfilesPayload(
                DIMENSION, 0, 0, true, List.of(palette),
                List.of(new MudLocalProfilesPayload.Entry(
                        entry.blockPos(), SinkingMedium.COUNT, 0))));
        assertThrows(IllegalArgumentException.class, () -> new MudLocalProfilesPayload(
                DIMENSION, 0, 0, true, List.of(palette),
                List.of(new MudLocalProfilesPayload.Entry(
                        entry.blockPos(), SinkingMedium.MUD.id(), 1))));
    }
}
