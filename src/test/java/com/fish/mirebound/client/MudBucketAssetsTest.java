package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.container.MudContainerRules;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MudBucketAssetsTest {
    private static final Path ASSET_ROOT = Path.of(
            "src/main/resources/assets/mirebound");

    @Test
    void everyBucketableMediumHasACompletePixelArtModel() throws Exception {
        BufferedImage shapeReference = ImageIO.read(ASSET_ROOT.resolve(
                "textures/item/bucket_fill/ash_quicksand.png").toFile());
        assertNotNull(shapeReference);
        int bucketableCount = 0;
        for (SinkingMedium medium : SinkingMedium.values()) {
            String name = medium.serializedName();
            Path texture = ASSET_ROOT.resolve(
                    "textures/item/bucket_fill/" + name + ".png");
            Path model = ASSET_ROOT.resolve(
                    "models/item/mud_bucket/" + name + ".json");
            if (!MudContainerRules.isBucketable(medium)) {
                assertFalse(Files.exists(texture), name + " must not have a bucket texture");
                assertFalse(Files.exists(model), name + " must not have a bucket model");
                continue;
            }

            bucketableCount++;
            assertTrue(Files.isRegularFile(texture), "Missing texture for " + name);
            assertTrue(Files.isRegularFile(model), "Missing model for " + name);
            BufferedImage image = ImageIO.read(texture.toFile());
            assertNotNull(image, "Unreadable texture for " + name);
            assertEquals(16, image.getWidth(), "Texture width for " + name);
            assertEquals(16, image.getHeight(), "Texture height for " + name);
            assertSameOpaqueShape(shapeReference, image, name);
            String json = Files.readString(model);
            assertTrue(json.contains("minecraft:item/bucket"), name);
            assertTrue(json.contains("mirebound:item/bucket_fill/" + name), name);
        }
        assertEquals(SinkingMedium.COUNT - 2, bucketableCount);
    }

    private static void assertSameOpaqueShape(BufferedImage expected,
            BufferedImage actual, String name) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean expectedOpaque = (expected.getRGB(x, y) >>> 24) != 0;
                boolean actualOpaque = (actual.getRGB(x, y) >>> 24) != 0;
                assertEquals(expectedOpaque, actualOpaque,
                        name + " alpha shape at " + x + "," + y);
            }
        }
    }
}
