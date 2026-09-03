package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WaterGunRendererTest {
    private static final Path ASSET_ROOT = Path.of(
            "src/main/resources/assets/mirebound");
    private static final Path BLOCKBENCH_SOURCE = Path.of(
            "art/blockbench/water_gun.bbmodel");

    @Test
    void modelKeepsGlassAndWaterSeparateFromOpaqueBody() throws Exception {
        JsonObject item = json("models/item/water_gun.json");
        assertEquals("minecraft:builtin/entity", item.get("parent").getAsString());
        JsonObject display = item.getAsJsonObject("display");
        JsonObject blockbenchDisplay = JsonParser.parseString(
                Files.readString(BLOCKBENCH_SOURCE)).getAsJsonObject()
                .getAsJsonObject("display");
        assertEquals(blockbenchDisplay, display,
                "Runtime item poses must follow the Blockbench source");
        JsonObject rightHand = display.getAsJsonObject("firstperson_righthand");
        JsonObject leftHand = display.getAsJsonObject("firstperson_lefthand");
        assertEquals(rightHand.getAsJsonArray("translation"),
                leftHand.getAsJsonArray("translation"));
        assertEquals(rightHand.getAsJsonArray("scale"),
                leftHand.getAsJsonArray("scale"));
        double scale = rightHand.getAsJsonArray("scale").get(0).getAsDouble();
        assertTrue(scale >= 0.38D && scale <= 0.42D);

        JsonObject body = json("models/item/water_gun/body.json");
        JsonArray bodyElements = body.getAsJsonArray("elements");
        assertTrue(bodyElements.size() >= 35);
        assertTrue(hasNamedElement(bodyElements, "tank_band_top"));
        assertTrue(hasNamedElement(bodyElements, "tank_hose_vertical"));
        assertTrue(hasNamedElement(bodyElements, "nozzle_flare"));
        assertFalse(hasNamedElement(bodyElements, "rear_water_tank"));
        assertWithinVanillaElementBounds(bodyElements);

        JsonObject glass = json("models/item/water_gun/tank_glass.json");
        JsonArray glassElements = glass.getAsJsonArray("elements");
        assertEquals(5, glassElements.size());
        assertTrue(hasNamedElement(glassElements, "tank_glass_center"));
        assertWithinVanillaElementBounds(glassElements);
    }

    @Test
    void runtimeGeometryMatchesTheBlockbenchSource() throws Exception {
        JsonArray source = JsonParser.parseString(
                Files.readString(BLOCKBENCH_SOURCE)).getAsJsonObject()
                .getAsJsonArray("elements");
        JsonArray body = json("models/item/water_gun/body.json")
                .getAsJsonArray("elements");
        JsonArray glass = json("models/item/water_gun/tank_glass.json")
                .getAsJsonArray("elements");

        int bodyIndex = 0;
        int glassIndex = 0;
        for (int index = 0; index < source.size(); index++) {
            JsonObject sourceElement = source.get(index).getAsJsonObject();
            if (!sourceElement.get("export").getAsBoolean()) {
                continue;
            }
            boolean glassElement = sourceElement.get("name").getAsString()
                    .startsWith("tank_glass_");
            JsonArray target = glassElement ? glass : body;
            int targetIndex = glassElement ? glassIndex++ : bodyIndex++;
            JsonObject runtimeElement = target.get(targetIndex).getAsJsonObject();
            assertEquals(sourceElement.get("name"), runtimeElement.get("name"));
            assertEquals(sourceElement.get("from"), runtimeElement.get("from"));
            assertEquals(sourceElement.get("to"), runtimeElement.get("to"));
            for (String face : new String[] {
                    "north", "east", "south", "west", "up", "down"}) {
                assertEquals(
                        sourceElement.getAsJsonObject("faces")
                                .getAsJsonObject(face).get("uv"),
                        runtimeElement.getAsJsonObject("faces")
                                .getAsJsonObject(face).get("uv"));
            }
        }
        assertEquals(body.size(), bodyIndex);
        assertEquals(glass.size(), glassIndex);
    }

    @Test
    void capturedNozzleTracksTheBlockbenchNozzleTip() throws Exception {
        JsonArray elements = JsonParser.parseString(
                Files.readString(BLOCKBENCH_SOURCE)).getAsJsonObject()
                .getAsJsonArray("elements");
        JsonObject nozzle = namedElement(elements, "nozzle_tip");
        JsonArray from = nozzle.getAsJsonArray("from");
        JsonArray to = nozzle.getAsJsonArray("to");

        assertEquals((from.get(0).getAsFloat() + to.get(0).getAsFloat()) / 32.0F,
                WaterGunNozzleFocus.NOZZLE_X, 1.0E-6F);
        assertEquals((from.get(1).getAsFloat() + to.get(1).getAsFloat()) / 32.0F,
                WaterGunNozzleFocus.NOZZLE_Y, 1.0E-6F);
        assertEquals((from.get(2).getAsFloat() - 0.05F) / 16.0F,
                WaterGunNozzleFocus.NOZZLE_Z, 1.0E-6F);
    }

    @Test
    void tankGlassTextureContainsVisibleTranslucency() throws Exception {
        BufferedImage image = ImageIO.read(ASSET_ROOT.resolve(
                "textures/item/water_gun_glass.png").toFile());
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        boolean foundTranslucent = false;
        for (int y = 0; y < image.getHeight() && !foundTranslucent; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha > 0 && alpha < 255) {
                    foundTranslucent = true;
                    break;
                }
            }
        }
        assertTrue(foundTranslucent);
    }

    @Test
    void storedWaterUsesAStationarySprite() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/fish/mirebound/client/WaterGunRenderer.java"));
        assertTrue(source.contains("WATER_STILL.sprite()"));
        assertFalse(source.contains("ModelBakery.WATER_FLOW.sprite()"));
    }

    @Test
    void sprayUsesBoundedParticlesWithoutStreamOrIndicatorGeometry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/fish/mirebound/client/WaterGunStreamRenderer.java"));

        assertTrue(source.contains("MAXIMUM_PATH_PARTICLES"));
        assertTrue(source.contains("ParticleTypes.RAIN"));
        assertTrue(source.contains("ParticleTypes.SPLASH"));
        assertFalse(source.contains("VertexConsumer"));
        assertFalse(source.contains("RenderType"));
        assertFalse(source.contains("WaterGunImpactDecal"));
    }

    @Test
    void muzzleAlignmentConvergesWithoutMovingTheBallisticEndpoint() {
        List<Vec3> path = List.of(
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 0.75D),
                new Vec3(0.0D, -0.1D, 2.0D));
        Vec3 muzzle = new Vec3(0.35D, -0.20D, 0.10D);

        List<Vec3> aligned = WaterGunStreamRenderer.alignNearNozzle(path, muzzle);

        assertEquals(muzzle, aligned.getFirst());
        assertEquals(path.getLast(), aligned.getLast());
        assertTrue(aligned.get(1).x < muzzle.x);
    }

    @Test
    void bodyTextureKeepsPixelDetailInsideEveryUvTile() throws Exception {
        BufferedImage image = ImageIO.read(ASSET_ROOT.resolve(
                "textures/item/water_gun.png").toFile());
        assertNotNull(image);
        assertEquals(32, image.getWidth());
        assertEquals(32, image.getHeight());

        for (int tileY = 0; tileY < 4; tileY++) {
            for (int tileX = 0; tileX < 4; tileX++) {
                Set<Integer> colors = new HashSet<>();
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        colors.add(image.getRGB(tileX * 8 + x, tileY * 8 + y));
                    }
                }
                assertTrue(colors.size() >= 8,
                        "Water-gun UV tile must not collapse into a flat color");
            }
        }
    }

    private static JsonObject json(String relativePath) throws Exception {
        return JsonParser.parseString(Files.readString(
                ASSET_ROOT.resolve(relativePath))).getAsJsonObject();
    }

    private static boolean hasNamedElement(JsonArray elements, String name) {
        return namedElement(elements, name) != null;
    }

    private static JsonObject namedElement(JsonArray elements, String name) {
        for (int index = 0; index < elements.size(); index++) {
            JsonObject element = elements.get(index).getAsJsonObject();
            if (name.equals(element.get("name").getAsString())) {
                return element;
            }
        }
        return null;
    }

    private static void assertWithinVanillaElementBounds(JsonArray elements) {
        for (int index = 0; index < elements.size(); index++) {
            JsonObject element = elements.get(index).getAsJsonObject();
            for (String endpoint : new String[] {"from", "to"}) {
                JsonArray coordinates = element.getAsJsonArray(endpoint);
                for (int axis = 0; axis < coordinates.size(); axis++) {
                    double coordinate = coordinates.get(axis).getAsDouble();
                    assertTrue(coordinate >= -16.0 && coordinate <= 32.0,
                            element.get("name").getAsString()
                                    + " exceeds the vanilla model boundary");
                }
            }
        }
    }
}
