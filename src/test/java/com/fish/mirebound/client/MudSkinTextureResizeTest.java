package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudSkinTextureResizeTest {
    @Test
    void cachedSkinCannotBeReusedAfterSwitchingToHdOrLegacySkinDimensions() {
        MudSkinTextureCache.Entry vanilla = new MudSkinTextureCache.Entry(64, 64);
        assertTrue(vanilla.matchesSize(64, 64));
        assertFalse(vanilla.matchesSize(128, 128));
        assertFalse(vanilla.matchesSize(64, 32));
        assertFalse(vanilla.matchesSize(128, 64));
        MudSkinTextureCache.Entry hd = new MudSkinTextureCache.Entry(128, 128);
        assertTrue(hd.matchesSize(128, 128));
        assertFalse(hd.matchesSize(64, 64));
    }
}
