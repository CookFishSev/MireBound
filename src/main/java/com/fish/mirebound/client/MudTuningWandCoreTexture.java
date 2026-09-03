package com.fish.mirebound.client;

import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Owns the bounded 16x16 medium transition texture used by the wand core. */
public final class MudTuningWandCoreTexture {
    private static final int SIZE = 16;
    private static final int TRANSITION_STEPS = 32;
    private static final double TRANSITION_TICKS = 28.0D;
    private static final double HOLD_TICKS = 120.0D;
    private static final double CYCLE_TICKS = HOLD_TICKS + TRANSITION_TICKS;
    private static final float TRANSITION_EDGE = 0.11F;
    private static DynamicTexture texture;
    private static ResourceLocation location;
    private static SinkingMedium currentMedium;
    private static int[] currentPixels;
    private static int[] targetPixels;
    private static float[] thresholds;
    private static long currentCycle = Long.MIN_VALUE;
    private static long sampledFrameSignature = Long.MIN_VALUE;
    private static int uploadedStep = -1;
    private static int displayedColor = 0xFFFFFF;

    private MudTuningWandCoreTexture() {
    }

    static ResourceLocation texture(double time) {
        ensureTexture(time);
        if (texture == null || location == null) {
            return null;
        }
        update(time);
        return location;
    }

    static int beamColor(double time) {
        ensureTexture(time);
        if (texture == null) {
            return 0xFFFFFF;
        }
        update(time);
        return displayedColor;
    }

    public static int hudColor(double time) {
        return beamColor(time);
    }

    static void reset() {
        DynamicTextureLifecycle.release(location, texture);
        texture = null;
        location = null;
        currentMedium = null;
        currentPixels = null;
        targetPixels = null;
        thresholds = null;
        currentCycle = Long.MIN_VALUE;
        sampledFrameSignature = Long.MIN_VALUE;
        uploadedStep = -1;
        displayedColor = 0xFFFFFF;
    }

    private static void ensureTexture(double time) {
        if (texture != null && location != null) {
            return;
        }
        texture = new DynamicTexture(SIZE, SIZE, true);
        texture.setFilter(false, false);
        location = Minecraft.getInstance().getTextureManager()
                .register("mirebound_mud_tuning_wand_core", texture);
    }

    private static void update(double time) {
        long cycle = (long) Math.floor(time / CYCLE_TICKS);
        if (cycle != currentCycle) {
            currentCycle = cycle;
            currentMedium = mediumForCycle(cycle);
            thresholds = createThresholds(cycle);
            sampledFrameSignature = Long.MIN_VALUE;
            uploadedStep = -1;
        }

        SinkingMedium targetMedium = mediumForCycle(cycle + 1L);
        long animationTick = Math.max(0L, (long) Math.floor(time));
        long frameSignature = frameSignature(
                currentMedium, targetMedium, animationTick);
        if (frameSignature != sampledFrameSignature) {
            currentPixels = sourcePixels(currentMedium);
            targetPixels = sourcePixels(targetMedium);
            sampledFrameSignature = frameSignature;
            uploadedStep = -1;
        }

        double cycleTime = Mth.positiveModulo(time, CYCLE_TICKS);
        if (cycleTime < HOLD_TICKS) {
            if (uploadedStep != -2) {
                writePixels(currentPixels);
                uploadedStep = -2;
            }
            return;
        }

        double progress = Mth.clamp(
                (cycleTime - HOLD_TICKS) / TRANSITION_TICKS, 0.0D, 1.0D);
        int step = Math.min(TRANSITION_STEPS,
                Mth.floor(progress * TRANSITION_STEPS));
        if (step != uploadedStep) {
            if (step >= TRANSITION_STEPS) {
                writePixels(targetPixels);
            } else {
                writeTransition(step / (float) TRANSITION_STEPS);
            }
            uploadedStep = step;
        }
    }

    private static SinkingMedium mediumForCycle(long cycle) {
        SinkingMedium[] media = SinkingMedium.values();
        int index = (int) Math.floorMod(mix64(cycle), (long) media.length);
        if (media.length > 1) {
            int previous = (int) Math.floorMod(mix64(cycle - 1L), (long) media.length);
            if (index == previous) {
                int offset = 1 + (int) Math.floorMod(
                        mix64(cycle ^ 0x632BE59BD9B4E019L), (long) (media.length - 1));
                index = (index + offset) % media.length;
            }
        }
        return media[index];
    }

    private static long mix64(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static int[] sourcePixels(SinkingMedium medium) {
        int[] pixels = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                pixels[x + y * SIZE] = MudSkinTextureCache.coverTexturePixelAbgr(
                        medium, x, y, 0);
            }
        }
        if (!MudSkinTextureCache.hasCoverTexture(medium)) {
            applyMissingTextureDetail(pixels, medium);
        }
        return pixels;
    }

    private static long frameSignature(SinkingMedium current,
            SinkingMedium target, long tick) {
        long currentAnimation = MudSkinTextureCache.coverTextureAnimationSignature(
                1L << current.id(), tick);
        long targetAnimation = MudSkinTextureCache.coverTextureAnimationSignature(
                1L << target.id(), tick);
        long signature = current.id() + 1L;
        signature = signature * 31L + currentAnimation;
        signature = signature * 31L + target.id() + 1L;
        return signature * 31L + targetAnimation;
    }

    private static void applyMissingTextureDetail(int[] pixels, SinkingMedium medium) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int index = x + y * SIZE;
                int pixel = pixels[index];
                int patch = Math.floorMod(
                        (x >> 2) * 3 + (y >> 2) * 5 + medium.id(), 7) - 3;
                float brightness = 1.0F + patch * 0.025F;
                pixels[index] = FastColor.ABGR32.color(
                        FastColor.ABGR32.alpha(pixel),
                        scale(FastColor.ABGR32.blue(pixel), brightness),
                        scale(FastColor.ABGR32.green(pixel), brightness),
                        scale(FastColor.ABGR32.red(pixel), brightness));
            }
        }
    }

    private static int scale(int channel, float brightness) {
        return Mth.clamp(Math.round(channel * brightness), 0, 255);
    }

    private static float[] createThresholds(long cycle) {
        RandomSource random = RandomSource.create(mix64(cycle ^ 0x4CF5AD432745937FL));
        int centerCount = 2 + random.nextInt(4);
        float[] centerX = new float[centerCount];
        float[] centerY = new float[centerCount];
        for (int index = 0; index < centerCount; index++) {
            centerX[index] = random.nextFloat() * (SIZE - 1);
            centerY[index] = random.nextFloat() * (SIZE - 1);
        }

        float[] raw = new float[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float nearest = Float.MAX_VALUE;
                for (int center = 0; center < centerCount; center++) {
                    float dx = x - centerX[center];
                    float dy = y - centerY[center];
                    nearest = Math.min(nearest, (float) Math.sqrt(dx * dx + dy * dy));
                }
                raw[x + y * SIZE] = nearest + random.nextFloat() * 2.2F;
            }
        }
        float[] blurred = blurThresholds(raw);
        normalizeThresholds(blurred);
        return blurred;
    }

    private static float[] blurThresholds(float[] source) {
        float[] result = new float[source.length];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float sum = 0.0F;
                float weight = 0.0F;
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    for (int offsetX = -1; offsetX <= 1; offsetX++) {
                        float sampleWeight = offsetX == 0 && offsetY == 0 ? 2.0F : 1.0F;
                        int sampleX = Mth.clamp(x + offsetX, 0, SIZE - 1);
                        int sampleY = Mth.clamp(y + offsetY, 0, SIZE - 1);
                        sum += source[sampleX + sampleY * SIZE] * sampleWeight;
                        weight += sampleWeight;
                    }
                }
                result[x + y * SIZE] = sum / weight;
            }
        }
        return result;
    }

    private static void normalizeThresholds(float[] values) {
        float minimum = Float.MAX_VALUE;
        float maximum = -Float.MAX_VALUE;
        for (float value : values) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        float range = Math.max(0.0001F, maximum - minimum);
        for (int index = 0; index < values.length; index++) {
            values[index] = 0.03F + 0.80F * (values[index] - minimum) / range;
        }
    }

    private static void writeTransition(float progress) {
        if (currentPixels == null || targetPixels == null || thresholds == null) {
            return;
        }
        int[] blended = new int[SIZE * SIZE];
        for (int index = 0; index < blended.length; index++) {
            float amount = smoothstep(
                    (progress - thresholds[index]) / TRANSITION_EDGE);
            blended[index] = blendAbgr(currentPixels[index], targetPixels[index], amount);
        }
        writePixels(blended);
    }

    private static void writePixels(int[] pixels) {
        if (texture == null) {
            return;
        }
        NativeImage image = texture.getPixels();
        if (image == null) {
            image = new NativeImage(SIZE, SIZE, true);
            texture.setPixels(image);
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setPixelRGBA(x, y, pixels[x + y * SIZE]);
            }
        }
        texture.upload();
        texture.setFilter(false, false);
        displayedColor = averageColor(pixels);
    }

    private static int averageColor(int[] pixels) {
        long alpha = 0L;
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        for (int pixel : pixels) {
            int pixelAlpha = FastColor.ABGR32.alpha(pixel);
            alpha += pixelAlpha;
            red += (long) FastColor.ABGR32.red(pixel) * pixelAlpha;
            green += (long) FastColor.ABGR32.green(pixel) * pixelAlpha;
            blue += (long) FastColor.ABGR32.blue(pixel) * pixelAlpha;
        }
        if (alpha <= 0L) {
            return 0xFFFFFF;
        }
        return Mth.clamp((int) (red / alpha), 0, 255) << 16
                | Mth.clamp((int) (green / alpha), 0, 255) << 8
                | Mth.clamp((int) (blue / alpha), 0, 255);
    }

    private static int blendAbgr(int from, int to, float amount) {
        float clamped = Mth.clamp(amount, 0.0F, 1.0F);
        return FastColor.ABGR32.color(
                Mth.lerpInt(clamped,
                        FastColor.ABGR32.alpha(from), FastColor.ABGR32.alpha(to)),
                Mth.lerpInt(clamped,
                        FastColor.ABGR32.blue(from), FastColor.ABGR32.blue(to)),
                Mth.lerpInt(clamped,
                        FastColor.ABGR32.green(from), FastColor.ABGR32.green(to)),
                Mth.lerpInt(clamped,
                        FastColor.ABGR32.red(from), FastColor.ABGR32.red(to)));
    }

    private static float smoothstep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
