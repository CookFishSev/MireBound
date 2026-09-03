package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class MudTextureAnimationTest {
    @Test
    void parsesVanillaStyleVerticalStripAndQuantizesInterpolation() {
        MudTextureAnimation.Layout layout = MudTextureAnimation.parse(JsonParser.parseString("""
                {"animation":{"frametime":20,"interpolate":true}}
                """), 16, 64);

        assertTrue(layout.animated());
        assertEquals(16, layout.frameWidth());
        assertEquals(16, layout.frameHeight());
        assertEquals(0, layout.frameAt(0).currentFrame());
        assertEquals(1, layout.frameAt(0).nextFrame());
        assertEquals(0, layout.frameAt(0).blendStep());
        assertEquals(1, layout.frameAt(4).blendStep());
        assertEquals(4, layout.frameAt(19).blendStep());
        assertEquals(1, layout.frameAt(20).currentFrame());
    }

    @Test
    void honorsExplicitFrameOrderAndPerFrameDuration() {
        MudTextureAnimation.Layout layout = MudTextureAnimation.parse(JsonParser.parseString("""
                {"animation":{"frametime":5,"frames":[{"index":2,"time":3},0]}}
                """), 16, 64);

        assertEquals(2, layout.frameAt(0).currentFrame());
        assertEquals(2, layout.frameAt(2).currentFrame());
        assertEquals(0, layout.frameAt(3).currentFrame());
        assertEquals(2, layout.frameAt(8).currentFrame());
    }

    @Test
    void rejectsFrameSizesThatDoNotTileTheImage() {
        MudTextureAnimation.Layout layout = MudTextureAnimation.parse(JsonParser.parseString("""
                {"animation":{"width":10,"height":10,"frametime":4}}
                """), 16, 64);

        assertFalse(layout.animated());
        assertEquals(16, layout.frameWidth());
        assertEquals(64, layout.frameHeight());
    }
}
