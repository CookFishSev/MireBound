package com.fish.mirebound.stain;

import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCoverageAppearanceSnapshot;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudFootprintSamplerTest {
    @Test
    void readsAnExactDirtySolePixel() {
        MudPlayerData data = new MudPlayerData();
        data.setSurfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.BOTTOM, 2, 1, 1.0F, SinkingMedium.SOFT_QUICKSAND);

        MudFootprintSampler.Sample sample = MudFootprintSampler.sample(data, MudBodyPart.LEFT_LEG);

        assertTrue(sample.strength() > 0.5F);
        assertEquals(SinkingMedium.SOFT_QUICKSAND, sample.medium());
        assertEquals(0.0F, MudFootprintSampler.sample(data, MudBodyPart.RIGHT_LEG).strength());
    }

    @Test
    void ignoresMudAboveTheLowestLegPixelRow() {
        MudPlayerData data = new MudPlayerData();
        data.setSurfacePixelCoverage(MudBodyPart.RIGHT_LEG, MudSurface.FRONT, 1, 0, 1.0F, SinkingMedium.MUD);

        assertEquals(0.0F, MudFootprintSampler.sample(data, MudBodyPart.RIGHT_LEG).strength());
    }

    @Test
    void choosesTheMediumWithMostWeightedFootContact() {
        MudPlayerData data = new MudPlayerData();
        for (int column = 0; column < 4; column++) {
            data.setSurfacePixelCoverage(MudBodyPart.RIGHT_LEG, MudSurface.BOTTOM, 0, column,
                    0.8F, SinkingMedium.PEAT_BOG);
        }
        data.setSurfacePixelCoverage(MudBodyPart.RIGHT_LEG, MudSurface.BOTTOM, 1, 0,
                1.0F, SinkingMedium.SOFT_QUICKSAND);

        MudFootprintSampler.Sample sample = MudFootprintSampler.sample(data, MudBodyPart.RIGHT_LEG);

        assertEquals(SinkingMedium.PEAT_BOG, sample.medium());
        assertTrue(sample.strength() > 0.6F);
    }

    @Test
    void armorSamplingUsesItsOwnPixelsWithoutReadingSkin() {
        int armorCell = MudSurfaceLayout.cellIndex(
                MudBodyPart.LEFT_LEG, MudSurface.BOTTOM, 1, 2);
        ArmorMudData.Builder builder = ArmorMudData.EMPTY.toBuilder();
        builder.mark(armorCell, 0.90F, SinkingMedium.TAR);
        MudPlayerData cleanSkin = new MudPlayerData();

        MudFootprintSampler.Sample armor = MudFootprintSampler.sample(
                builder.build(), MudBodyPart.LEFT_LEG, cleanSkin.footprintMediumWeightsScratch());

        assertTrue(armor.strength() > 0.45F);
        assertEquals(SinkingMedium.TAR, armor.medium());
        assertEquals(0.0F, MudFootprintSampler.sample(cleanSkin, MudBodyPart.LEFT_LEG).strength());
    }

    @Test
    void visibleFootprintStrengthIncludesTheStoredSkinOpacity() {
        MudPlayerData opaque = new MudPlayerData();
        MudPlayerData translucent = new MudPlayerData();
        int opaqueAppearance = MudCoverageAppearanceSnapshot.pack(1.0F, 1.0F, 0.0F);
        int translucentAppearance = MudCoverageAppearanceSnapshot.pack(1.0F, 0.40F, 0.0F);
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                opaque.setSurfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.BOTTOM,
                        row, column, 1.0F, SinkingMedium.SOFT_QUICKSAND, opaqueAppearance);
                translucent.setSurfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.BOTTOM,
                        row, column, 1.0F, SinkingMedium.SOFT_QUICKSAND, translucentAppearance);
            }
        }

        float opaqueStrength = MudFootprintSampler.sample(null, opaque, MudBodyPart.LEFT_LEG).strength();
        float translucentStrength = MudFootprintSampler.sample(
                null, translucent, MudBodyPart.LEFT_LEG).strength();

        assertTrue(translucentStrength < opaqueStrength * 0.45F);
        assertTrue(translucentStrength > opaqueStrength * 0.35F);
    }

    @Test
    void walkingFadeTouchesOnlyTheSelectedFootContactPixels() {
        MudPlayerData data = new MudPlayerData();
        data.setSurfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.BOTTOM, 0, 0,
                1.0F, SinkingMedium.MUD);
        data.setSurfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.FRONT, 0, 0,
                1.0F, SinkingMedium.MUD);
        data.setSurfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.FRONT, 1, 0,
                1.0F, SinkingMedium.MUD);
        data.setSurfacePixelCoverage(MudBodyPart.RIGHT_LEG, MudSurface.BOTTOM, 0, 0,
                1.0F, SinkingMedium.MUD);

        MudFootprintSampler.fadeSkinSource(data, MudBodyPart.LEFT_LEG, 0.20F);

        assertTrue(data.surfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.BOTTOM, 0, 0) >= 0.80F);
        assertTrue(data.surfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.BOTTOM, 0, 0) < 0.84F);
        assertTrue(data.surfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.FRONT, 0, 0) >= 0.89F);
        assertTrue(data.surfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.FRONT, 0, 0) < 0.91F);
        assertTrue(data.surfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.FRONT, 1, 0) >= 0.95F);
        assertTrue(data.surfacePixelCoverage(MudBodyPart.LEFT_LEG, MudSurface.FRONT, 1, 0) < 0.97F);
        assertEquals(1.0F, data.surfacePixelCoverage(
                MudBodyPart.RIGHT_LEG, MudSurface.BOTTOM, 0, 0), 1.0E-6F);
    }

    @Test
    void armorWalkingFadePersistsInTheArmorComponentBuilder() {
        int cell = MudSurfaceLayout.cellIndex(MudBodyPart.RIGHT_LEG, MudSurface.BOTTOM, 2, 3);
        ArmorMudData.Builder initial = ArmorMudData.EMPTY.toBuilder();
        initial.mark(cell, 0.75F, SinkingMedium.MIRE);
        ArmorMudData.Builder fading = initial.build().toBuilder();

        MudFootprintSampler.fadeArmorSource(fading, MudBodyPart.RIGHT_LEG, 0.20F);
        ArmorMudData result = fading.build();

        assertTrue(result.coverageAt(cell) >= 0.55F);
        assertTrue(result.coverageAt(cell) < 0.60F);
        assertEquals(SinkingMedium.MIRE, result.mediumAt(cell));

        ArmorMudData.Builder exhausted = result.toBuilder();
        for (int i = 0; i < 12; i++) {
            MudFootprintSampler.fadeArmorSource(exhausted, MudBodyPart.RIGHT_LEG, 0.20F);
        }
        assertEquals(0.55F, exhausted.build().coverageAt(cell), 1.0F / 255.0F);
    }
}
