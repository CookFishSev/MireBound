package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudTextureUvTest {
    @Test
    void bubbleRegionUsesHalfTheMaterialTexture() {
        MudTextureUv.Region region =
                MudTextureUv.sample(0x123456789abcdefL, 8);

        assertEquals(0.5F, region.u1() - region.u0(), 1.0E-6F);
        assertEquals(0.5F, region.v1() - region.v0(), 1.0E-6F);
        assertTrue(region.u0() >= 0.0F && region.u1() <= 1.0F);
        assertTrue(region.v0() >= 0.0F && region.v1() <= 1.0F);
    }

    @Test
    void textureRegionIsStableForTheSameSeed() {
        MudTextureUv.Region first = MudTextureUv.sample(991L, 5);
        MudTextureUv.Region second = MudTextureUv.sample(991L, 5);

        assertEquals(first, second);
    }
}
