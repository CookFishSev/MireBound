package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SculkMireAnimationAssetsTest {
    @Test
    void baseAndConnectedTilesContainFourNativeAnimationFrames() throws Exception {
        assertAnimatedTexture("textures/block/sculk_mire.png");
        for (int variant = 0; variant < 9; variant++) {
            assertAnimatedTexture("textures/block/connected/sculk_mire_" + variant + ".png");
        }
    }

    private static void assertAnimatedTexture(String path) throws Exception {
        String resourcePath = "/assets/mirebound/" + path;
        try (InputStream stream = SculkMireAnimationAssetsTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, resourcePath);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, resourcePath);
            assertEquals(16, image.getWidth(), resourcePath);
            assertEquals(64, image.getHeight(), resourcePath);
        }
        try (InputStream metadata = SculkMireAnimationAssetsTest.class
                .getResourceAsStream(resourcePath + ".mcmeta")) {
            assertNotNull(metadata, resourcePath + ".mcmeta");
        }
    }
}
