package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.junit.jupiter.api.Test;

class MudSurfaceAppearanceTest {
    @Test
    void preservesOpaqueArgbBlockTints() {
        assertEquals(0x2A7BC4,
                MudSurfaceAppearance.normalizeBlockTint(0xFF2A7BC4));
        assertEquals(0x224466,
                MudSurfaceAppearance.normalizeBlockTint(0x7F224466));
        assertEquals(-1, MudSurfaceAppearance.normalizeBlockTint(-1));
    }

    @Test
    void mapsNormalizedSamplesIntoTheSourceAtlasSprite() {
        MudSurfaceAppearance.Appearance appearance = new MudSurfaceAppearance.Appearance(
                ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"),
                0.25F, 0.50F, 0.75F, 1.0F,
                255, 255, 255, null);

        assertEquals(0.25F, appearance.u(0.0F), 1.0E-6F);
        assertEquals(0.50F, appearance.u(0.5F), 1.0E-6F);
        assertEquals(0.75F, appearance.u(1.0F), 1.0E-6F);
        assertEquals(0.625F, appearance.v(0.25F), 1.0E-6F);
    }

    @Test
    void combinesModelTintWithExistingSurfaceShading() {
        MudSurfaceAppearance.Appearance appearance = new MudSurfaceAppearance.Appearance(
                ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"),
                0.0F, 0.0F, 1.0F, 1.0F,
                128, 64, 32, null);

        assertEquals(64, appearance.shadedRed(128));
        assertEquals(32, appearance.shadedGreen(128));
        assertEquals(16, appearance.shadedBlue(128));
        assertEquals(0, appearance.shadedRed(-20));
        assertEquals(128, appearance.shadedRed(400));
    }

    @Test
    void adaptiveCoverageUsesStableCoordinatesAcrossSectionSalts() {
        int[] pixels = new int[16 * 16];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = 0xFF000000 | index;
        }
        MudSurfaceAppearance.Appearance appearance = new MudSurfaceAppearance.Appearance(
                ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"),
                0.0F, 0.0F, 1.0F, 1.0F,
                255, 255, 255, null, pixels);

        assertEquals(
                appearance.sampleAbgr(19, 22, 101, 0),
                appearance.sampleAbgr(19, 22, 1701, 0));
        assertEquals(3, MudSurfaceAppearance.Appearance.adaptiveCoordinate(19, 101, 16));
        assertEquals(3, MudSurfaceAppearance.Appearance.adaptiveCoordinate(19, 1701, 16));
    }

    @Test
    void adaptiveCoverageReconstructsRegularSourcePatterns() {
        int[] pixels = new int[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                pixels[x + y * 16] = (x + y & 1) == 0
                        ? 0xFF202020 : 0xFFE0E0E0;
            }
        }
        MudSurfaceAppearance.Appearance appearance = new MudSurfaceAppearance.Appearance(
                ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"),
                0.0F, 0.0F, 1.0F, 1.0F,
                255, 255, 255, null, pixels);

        int changed = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int mixed = appearance.sampleAbgr(x, y, 17, 0);
                changed += mixed == pixels[x + y * 16] ? 0 : 1;
                assertTrue((mixed & 0xFF) >= 0x20 && (mixed & 0xFF) <= 0xE0);
                assertEquals(mixed, appearance.sampleAbgr(x, y, 1701, 0));
            }
        }
        assertTrue(changed >= 128,
                "converted coverage must visibly disrupt a regular source pattern");
    }

    @Test
    void adaptiveCoverageMixDoesNotRepeatOnTheSourceTilePeriod() {
        int[] pixels = new int[16 * 16];
        for (int index = 0; index < pixels.length; index++) {
            int shade = index & 0xFF;
            pixels[index] = 0xFF000000 | shade << 16 | shade << 8 | shade;
        }
        MudSurfaceAppearance.Appearance appearance = new MudSurfaceAppearance.Appearance(
                ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"),
                0.0F, 0.0F, 1.0F, 1.0F,
                255, 255, 255, null, pixels);

        Set<Integer> tileSamples = new HashSet<>();
        for (int tile = 0; tile < 8; tile++) {
            tileSamples.add(appearance.sampleAbgr(3 + tile * 16, 5, 0, 0));
        }
        assertTrue(tileSamples.size() > 1);
    }

    @Test
    void adaptiveCoverageUsesAverageSourceTextureAlpha() {
        int[] pixels = new int[16 * 16];
        java.util.Arrays.fill(pixels, FastColor.ABGR32.color(64, 80, 120, 160));
        for (int index = 0; index < pixels.length / 2; index++) {
            pixels[index] = FastColor.ABGR32.color(128, 80, 120, 160);
        }
        MudSurfaceAppearance.Appearance appearance = new MudSurfaceAppearance.Appearance(
                ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"),
                0.0F, 0.0F, 1.0F, 1.0F,
                255, 255, 255, null, pixels);

        int sampled = appearance.sampleAbgr(4, 7, 31, 0xFF102030);
        assertEquals(96, FastColor.ABGR32.alpha(sampled));
        assertEquals(48, MudSkinTextureCache.sourceAdjustedAlpha(128, sampled, 1L));
        assertEquals(128, MudSkinTextureCache.sourceAdjustedAlpha(128, sampled, 0L));
    }

    @Test
    void fullyTransparentAdaptiveTextureDoesNotUseOpaqueFallbackAlpha() {
        MudSurfaceAppearance.Appearance appearance = new MudSurfaceAppearance.Appearance(
                ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"),
                0.0F, 0.0F, 1.0F, 1.0F,
                255, 255, 255, null, new int[16 * 16]);

        int sampled = appearance.sampleAbgr(2, 9, 17, 0xFF405060);
        assertEquals(0, FastColor.ABGR32.alpha(sampled));
        assertEquals(0, MudSkinTextureCache.sourceAdjustedAlpha(255, sampled, 1L));
    }

    @Test
    void convertedSkinCoverageUsesContinuousDistinctBodyPartPhases() {
        assertEquals(9, MudSkinTextureCache.adaptiveCoverageSampleX(9, 0L, 3));
        assertEquals(12, MudSkinTextureCache.adaptiveCoverageSampleY(12, 0L, 3));

        Set<Long> phases = new HashSet<>();
        for (int part = 0; part < 6; part++) {
            int x = MudSkinTextureCache.adaptiveCoverageSampleX(0, 1L, part);
            int y = MudSkinTextureCache.adaptiveCoverageSampleY(0, 1L, part);
            phases.add((long) x << 32 | Integer.toUnsignedLong(y));
            assertEquals(1,
                    MudSkinTextureCache.adaptiveCoverageSampleX(1, 1L, part) - x);
            assertEquals(1,
                    MudSkinTextureCache.adaptiveCoverageSampleY(1, 1L, part) - y);
        }
        assertEquals(6, phases.size());
        assertTrue(phases.stream().allMatch(value -> value != 0L));
    }
}
