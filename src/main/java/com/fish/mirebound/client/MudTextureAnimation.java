package com.fish.mirebound.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Lightweight reader for the standard vertical/horizontal animated texture layout. */
final class MudTextureAnimation {
    private static final int MAX_FRAMES = 256;
    private static final int MAX_FRAME_TICKS = 1200;
    private static final int INTERPOLATION_STEPS = 5;
    private static final int MAX_METADATA_BYTES = 256 * 1024;

    private MudTextureAnimation() {
    }

    static Layout load(ResourceManager resources, ResourceLocation texture, int imageWidth, int imageHeight) {
        ResourceLocation metadata = ResourceLocation.fromNamespaceAndPath(
                texture.getNamespace(), texture.getPath() + ".mcmeta");
        try {
            Resource resource = resources.getResource(metadata).orElse(null);
            if (resource == null) {
                return Layout.still(imageWidth, imageHeight);
            }
            byte[] metadataBytes;
            try (var stream = resource.open()) {
                metadataBytes = BoundedResourceReader.readBytes(stream, MAX_METADATA_BYTES);
            }
            try (InputStreamReader reader = new InputStreamReader(
                    new ByteArrayInputStream(metadataBytes), StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader), imageWidth, imageHeight);
            }
        } catch (IOException | RuntimeException ignored) {
            return Layout.still(imageWidth, imageHeight);
        }
    }

    static Layout parse(JsonElement rootElement, int imageWidth, int imageHeight) {
        if (rootElement == null || !rootElement.isJsonObject() || imageWidth <= 0 || imageHeight <= 0) {
            return Layout.still(imageWidth, imageHeight);
        }
        JsonObject animation = object(rootElement.getAsJsonObject(), "animation");
        if (animation == null) {
            return Layout.still(imageWidth, imageHeight);
        }

        int frameWidth = positiveInt(animation, "width", imageWidth);
        int frameHeight = positiveInt(animation, "height", frameWidth);
        if (frameWidth <= 0 || frameHeight <= 0
                || imageWidth % frameWidth != 0 || imageHeight % frameHeight != 0) {
            return Layout.still(imageWidth, imageHeight);
        }

        int columns = imageWidth / frameWidth;
        int availableFrames = columns * (imageHeight / frameHeight);
        if (availableFrames <= 1 || availableFrames > MAX_FRAMES) {
            return Layout.still(frameWidth, frameHeight);
        }

        int defaultTime = clamp(positiveInt(animation, "frametime", 1), 1, MAX_FRAME_TICKS);
        List<Integer> frames = new ArrayList<>();
        List<Integer> durations = new ArrayList<>();
        JsonElement framesElement = animation.get("frames");
        if (framesElement != null && framesElement.isJsonArray()) {
            JsonArray frameArray = framesElement.getAsJsonArray();
            for (JsonElement frameElement : frameArray) {
                if (frames.size() >= MAX_FRAMES) {
                    break;
                }
                int frame = -1;
                int duration = defaultTime;
                if (frameElement.isJsonPrimitive() && frameElement.getAsJsonPrimitive().isNumber()) {
                    frame = frameElement.getAsInt();
                } else if (frameElement.isJsonObject()) {
                    JsonObject frameObject = frameElement.getAsJsonObject();
                    frame = intValue(frameObject, "index", -1);
                    duration = clamp(positiveInt(frameObject, "time", defaultTime), 1, MAX_FRAME_TICKS);
                }
                if (frame >= 0 && frame < availableFrames) {
                    frames.add(frame);
                    durations.add(duration);
                }
            }
        }
        if (frames.isEmpty()) {
            for (int frame = 0; frame < availableFrames; frame++) {
                frames.add(frame);
                durations.add(defaultTime);
            }
        }
        if (frames.size() <= 1) {
            return Layout.still(frameWidth, frameHeight);
        }

        int[] frameIndices = new int[frames.size()];
        int[] frameDurations = new int[durations.size()];
        int totalDuration = 0;
        for (int index = 0; index < frames.size(); index++) {
            frameIndices[index] = frames.get(index);
            frameDurations[index] = durations.get(index);
            totalDuration += frameDurations[index];
        }
        boolean interpolate = booleanValue(animation, "interpolate", false);
        return new Layout(frameWidth, frameHeight, columns, frameIndices, frameDurations,
                Math.max(1, totalDuration), interpolate);
    }

    private static JsonObject object(JsonObject parent, String member) {
        JsonElement value = parent.get(member);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static int positiveInt(JsonObject object, String member, int fallback) {
        int value = intValue(object, member, fallback);
        return value > 0 ? value : fallback;
    }

    private static int intValue(JsonObject object, String member, int fallback) {
        try {
            JsonElement value = object.get(member);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                    ? value.getAsInt()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean booleanValue(JsonObject object, String member, boolean fallback) {
        try {
            JsonElement value = object.get(member);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                    ? value.getAsBoolean()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Layout(int frameWidth, int frameHeight, int columns, int[] frames, int[] durations,
            int totalDuration, boolean interpolate) {
        private static Layout still(int width, int height) {
            return new Layout(Math.max(1, width), Math.max(1, height), 1,
                    new int[] {0}, new int[] {1}, 1, false);
        }

        boolean animated() {
            return frames.length > 1;
        }

        FrameSample frameAt(long tick) {
            if (!animated()) {
                return FrameSample.STILL;
            }
            int cycleTick = (int) Math.floorMod(tick, totalDuration);
            int frameSlot = 0;
            while (frameSlot < durations.length - 1 && cycleTick >= durations[frameSlot]) {
                cycleTick -= durations[frameSlot++];
            }
            int current = frames[frameSlot];
            int next = interpolate ? frames[(frameSlot + 1) % frames.length] : current;
            int blendStep = interpolate
                    ? Math.min(INTERPOLATION_STEPS - 1,
                            cycleTick * INTERPOLATION_STEPS / Math.max(1, durations[frameSlot]))
                    : 0;
            long key = 1L + current;
            key = key * 257L + next;
            key = key * 17L + blendStep;
            return new FrameSample(current, next, blendStep, key);
        }

        int frameX(int frame) {
            return frame % columns * frameWidth;
        }

        int frameY(int frame) {
            return frame / columns * frameHeight;
        }
    }

    record FrameSample(int currentFrame, int nextFrame, int blendStep, long key) {
        private static final FrameSample STILL = new FrameSample(0, 0, 0, 0L);

        float blend() {
            return blendStep / (float) INTERPOLATION_STEPS;
        }
    }
}
