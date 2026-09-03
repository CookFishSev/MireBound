package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ArmorTextureContactPayloadTest {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("mirebound", "textures/test.png");

    @Test
    void acceptsVanillaArmorSlotsOnly() {
        assertTrue(ArmorTextureContactPayload.armor(0, TEXTURE, 16, 16, Vec3.ZERO, List.of()).validTarget());
        assertTrue(ArmorTextureContactPayload.armor(3, TEXTURE, 16, 16, Vec3.ZERO, List.of()).validTarget());
        assertFalse(ArmorTextureContactPayload.armor(-1, TEXTURE, 16, 16, Vec3.ZERO, List.of()).validTarget());
        assertFalse(ArmorTextureContactPayload.armor(4, TEXTURE, 16, 16, Vec3.ZERO, List.of()).validTarget());
    }

    @Test
    void validatesCuriosAddressesIndependentlyFromArmorSlots() {
        assertTrue(ArmorTextureContactPayload.curios(
                "necklace", 0, false, TEXTURE, 32, 32, Vec3.ZERO, List.of()).validTarget());
        assertTrue(ArmorTextureContactPayload.curios(
                "cosmetic_hat", 127, true, TEXTURE, 32, 32, Vec3.ZERO, List.of()).validTarget());
        assertFalse(ArmorTextureContactPayload.curios(
                "", 0, false, TEXTURE, 32, 32, Vec3.ZERO, List.of()).validTarget());
        assertFalse(ArmorTextureContactPayload.curios(
                "ring", 128, false, TEXTURE, 32, 32, Vec3.ZERO, List.of()).validTarget());
    }

    @Test
    void keepsTextureDimensionValidationForCurios() {
        assertTrue(ArmorTextureContactPayload.curios(
                "belt", 0, false, TEXTURE, 1024, 1024, Vec3.ZERO, List.of()).validDimensions());
        assertFalse(ArmorTextureContactPayload.curios(
                "belt", 0, false, TEXTURE, 1025, 16, Vec3.ZERO, List.of()).validDimensions());
    }

    @Test
    void rejectsNonFiniteOriginsThatWouldDefeatTheServerRangeTest() {
        assertTrue(ArmorTextureContactPayload.armor(
                0, TEXTURE, 16, 16, new Vec3(12.0D, 64.0D, -8.5D), List.of()).validOrigin());

        // Every distance comparison against NaN is false, so an unchecked non-finite origin would
        // pass the server range gate instead of failing it.
        for (double poisoned : new double[] {
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertFalse(ArmorTextureContactPayload.armor(
                    0, TEXTURE, 16, 16, new Vec3(poisoned, 0.0D, 0.0D), List.of()).validOrigin());
            assertFalse(ArmorTextureContactPayload.armor(
                    0, TEXTURE, 16, 16, new Vec3(0.0D, poisoned, 0.0D), List.of()).validOrigin());
            assertFalse(ArmorTextureContactPayload.armor(
                    0, TEXTURE, 16, 16, new Vec3(0.0D, 0.0D, poisoned), List.of()).validOrigin());
        }
    }

    @Test
    void nonFiniteOriginFailsClosedBecauseRangeComparisonsCannotRejectIt() {
        Vec3 poisoned = new Vec3(Double.NaN, Double.NaN, Double.NaN);
        // Documents the exact reason validOrigin() must run before the range check.
        assertFalse(Vec3.ZERO.distanceToSqr(poisoned) > 16.0D);
        assertFalse(ArmorTextureContactPayload.armor(
                0, TEXTURE, 16, 16, poisoned, List.of()).validOrigin());
    }

    @Test
    void validatesCandidateCountAgainstSentSamplesAndServerLimit() {
        List<ArmorTextureContactPayload.Sample> samples = List.of(
                new ArmorTextureContactPayload.Sample(0, 0.0F, 0.0F, 0.0F),
                new ArmorTextureContactPayload.Sample(1, 0.0F, 0.0F, 0.0F));

        assertTrue(ArmorTextureContactPayload.armor(
                0, TEXTURE, 16, 16, Vec3.ZERO, 32, samples).validCandidateCount());
        assertFalse(ArmorTextureContactPayload.armor(
                0, TEXTURE, 16, 16, Vec3.ZERO, 1, samples).validCandidateCount());
        assertFalse(ArmorTextureContactPayload.armor(
                0, TEXTURE, 16, 16, Vec3.ZERO,
                ArmorTextureContactPayload.MAX_CANDIDATES + 1, samples).validCandidateCount());
    }
}
