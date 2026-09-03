package com.fish.mirebound.client;

import com.fish.mirebound.assimilation.AssimilationProfile;
import java.util.Random;
import net.minecraft.util.Mth;

/** Fixed-pool, low-frequency natural openings in the assimilation screen mask. */
final class AssimilationScreenCracks {
    static final int MAX_CRACKS = 5;
    static final int MAX_POINTS = 13;
    static final int MAX_BRANCH_POINTS = 5;
    private static final float CANVAS_ASPECT = 256.0F / 144.0F;
    private static final Crack[] CRACKS = createPool();
    private static int entityId = Integer.MIN_VALUE;
    private static int patternSeed;
    private static int lastTick = Integer.MIN_VALUE;
    private static int nextSpawnTick = Integer.MAX_VALUE;
    private static int serial;

    private AssimilationScreenCracks() {
    }

    static long tick(int localEntityId, int seed, float progress, int tick,
            AssimilationProfile profile) {
        profile = profile == null ? AssimilationProfile.DEFAULT : profile;
        if (localEntityId != entityId || seed != patternSeed || tick < lastTick) {
            reset(localEntityId, seed, tick, profile);
        }
        if (tick == lastTick) {
            return signature();
        }
        lastTick = tick;
        for (Crack crack : CRACKS) {
            if (crack.active && ++crack.age >= crack.totalTicks()) {
                crack.active = false;
            }
        }
        if (progress >= profile.screenCrackStartProgress() && tick >= nextSpawnTick) {
            spawn(seed, tick, profile);
            nextSpawnTick = tick + randomInterval(profile,
                    seed ^ serial * 0x632BE5AB);
        } else if (progress < profile.screenCrackStartProgress() - 0.08F) {
            nextSpawnTick = tick + profile.screenCrackMinIntervalTicks();
        }
        return signature();
    }

    static float openness(float x, float y) {
        float openness = 0.0F;
        for (Crack crack : CRACKS) {
            if (!crack.active) {
                continue;
            }
            float alpha = lifeAlpha(crack);
            float growth = growthProgress(crack);
            float widthScale = widthScale(crack);
            openness = Math.max(openness, alpha * pathOpenness(crack.x, crack.y,
                    crack.points, crack.width * widthScale, growth, x, y));
            if (crack.branchPoints > 1) {
                float branchStart = crack.branchOrigin
                        / (float) Math.max(1, crack.points - 1);
                float branchGrowth = smoothStep(Mth.clamp(
                        (growth - branchStart) / Math.max(0.001F, 1.0F - branchStart),
                        0.0F, 1.0F));
                openness = Math.max(openness, alpha * 0.82F * pathOpenness(
                        crack.branchX, crack.branchY, crack.branchPoints,
                        crack.width * 0.72F * widthScale, branchGrowth, x, y));
            }
        }
        return Mth.clamp(openness, 0.0F, 1.0F);
    }

    static void reset() {
        reset(Integer.MIN_VALUE, 0, 0, AssimilationProfile.DEFAULT);
    }

    private static void reset(int localEntityId, int seed, int tick,
            AssimilationProfile profile) {
        entityId = localEntityId;
        patternSeed = seed;
        lastTick = tick;
        serial = 0;
        nextSpawnTick = tick + randomInterval(profile, seed ^ 0x41A55A17);
        for (Crack crack : CRACKS) {
            crack.active = false;
        }
    }

    private static void spawn(int seed, int tick, AssimilationProfile profile) {
        Crack crack = acquire();
        Random random = new Random(mix(seed ^ tick * 0x9E3779B9 ^ ++serial * 0x85EBCA6B));
        crack.active = true;
        crack.age = 0;
        crack.points = 8 + random.nextInt(6);
        crack.width = Mth.lerp(random.nextFloat(),
                profile.screenCrackMinWidth(), profile.screenCrackMaxWidth());
        crack.fadeInTicks = profile.screenCrackFadeInTicks();
        crack.holdTicks = profile.screenCrackHoldTicks();
        crack.fadeOutTicks = profile.screenCrackFadeOutTicks();
        float angle = (float) (random.nextDouble() * Math.PI * 2.0D);
        float length = Mth.lerp(random.nextFloat(),
                profile.screenCrackMinLength(), profile.screenCrackMaxLength());
        float x = 0.14F + random.nextFloat() * 0.72F;
        float y = 0.14F + random.nextFloat() * 0.72F;
        float step = length / Math.max(1, crack.points - 1);
        for (int point = 0; point < crack.points; point++) {
            crack.x[point] = x;
            crack.y[point] = y;
            angle += (random.nextFloat() - 0.5F) * 0.46F;
            x = Mth.clamp(x + Mth.cos(angle) * step / CANVAS_ASPECT, 0.04F, 0.96F);
            y = Mth.clamp(y + Mth.sin(angle) * step, 0.04F, 0.96F);
        }
        crack.branchPoints = 0;
        crack.branchOrigin = 0;
        if (random.nextFloat() < 0.72F) {
            int origin = 2 + random.nextInt(Math.max(1, crack.points - 4));
            crack.branchOrigin = origin;
            crack.branchPoints = 3 + random.nextInt(MAX_BRANCH_POINTS - 2);
            x = crack.x[origin];
            y = crack.y[origin];
            angle += (random.nextBoolean() ? 1.0F : -1.0F)
                    * (0.65F + random.nextFloat() * 0.55F);
            for (int point = 0; point < crack.branchPoints; point++) {
                crack.branchX[point] = x;
                crack.branchY[point] = y;
                angle += (random.nextFloat() - 0.5F) * 0.52F;
                x = Mth.clamp(x + Mth.cos(angle) * step * 0.82F / CANVAS_ASPECT,
                        0.04F, 0.96F);
                y = Mth.clamp(y + Mth.sin(angle) * step * 0.82F, 0.04F, 0.96F);
            }
        }
    }

    private static Crack acquire() {
        for (Crack crack : CRACKS) {
            if (!crack.active) {
                return crack;
            }
        }
        Crack oldest = CRACKS[0];
        for (Crack crack : CRACKS) {
            if (crack.age > oldest.age) {
                oldest = crack;
            }
        }
        return oldest;
    }

    private static float pathOpenness(float[] xs, float[] ys, int points,
            float width, float growth, float x, float y) {
        if (points < 2 || growth <= 0.0F || width <= 0.0001F) {
            return 0.0F;
        }
        float openness = 0.0F;
        float pathPosition = Mth.clamp(growth, 0.0F, 1.0F) * (points - 1);
        int completeSegments = Math.min(points - 1, (int) Math.floor(pathPosition));
        for (int point = 1; point <= completeSegments; point++) {
            openness = Math.max(openness, segmentOpenness(
                    x * CANVAS_ASPECT, y,
                    xs[point - 1] * CANVAS_ASPECT, ys[point - 1],
                    xs[point] * CANVAS_ASPECT, ys[point],
                    point - 1, points, growth, width));
        }
        if (completeSegments < points - 1) {
            float segmentProgress = pathPosition - completeSegments;
            float endX = Mth.lerp(segmentProgress,
                    xs[completeSegments], xs[completeSegments + 1]);
            float endY = Mth.lerp(segmentProgress,
                    ys[completeSegments], ys[completeSegments + 1]);
            openness = Math.max(openness, segmentOpenness(
                    x * CANVAS_ASPECT, y,
                    xs[completeSegments] * CANVAS_ASPECT, ys[completeSegments],
                    endX * CANVAS_ASPECT, endY,
                    completeSegments, points, growth, width));
        }
        return openness;
    }

    private static float segmentOpenness(float px, float py,
            float ax, float ay, float bx, float by, int segment,
            int points, float growth, float baseWidth) {
        float dx = bx - ax;
        float dy = by - ay;
        float length = dx * dx + dy * dy;
        float t = length <= 1.0E-8F ? 0.0F
                : Mth.clamp(((px - ax) * dx + (py - ay) * dy) / length, 0.0F, 1.0F);
        float offsetX = ax + dx * t - px;
        float offsetY = ay + dy * t - py;
        float position = (segment + t) / Math.max(1.0F, points - 1.0F);
        float width = baseWidth * pathTaper(position, growth);
        float feather = width * 1.8F;
        float distance = Mth.sqrt(offsetX * offsetX + offsetY * offsetY);
        if (distance >= feather) {
            return 0.0F;
        }
        return 1.0F - smoothStep(Mth.clamp((distance - width * 0.42F)
                / Math.max(0.0001F, feather - width * 0.42F), 0.0F, 1.0F));
    }

    static float pathTaper(float position, float growth) {
        float normalized = Mth.clamp(position, 0.0F, 1.0F);
        float middle = (float) Math.pow(Math.max(0.0F,
                Math.sin(Math.PI * normalized)), 0.72D);
        float permanentTaper = Mth.lerp(middle, 0.22F, 1.0F);
        float distanceBehindTip = Math.max(0.0F, growth - normalized);
        float openingTip = Mth.lerp(smoothStep(Mth.clamp(
                distanceBehindTip / 0.13F, 0.0F, 1.0F)), 0.20F, 1.0F);
        return permanentTaper * openingTip;
    }

    private static float lifeAlpha(Crack crack) {
        if (crack.age < crack.fadeInTicks) {
            int openingFadeTicks = Math.min(5, crack.fadeInTicks);
            return smoothStep(Mth.clamp(
                    crack.age / (float) openingFadeTicks, 0.0F, 1.0F));
        }
        if (crack.age < crack.fadeInTicks + crack.holdTicks) {
            return 1.0F;
        }
        float healing = (crack.age - crack.fadeInTicks - crack.holdTicks)
                / (float) crack.fadeOutTicks;
        // Geometry does most of the healing. Alpha only softens the final pixel so
        // the last sub-cell does not blink when the slit reaches zero width.
        return 1.0F - smoothStep(Mth.clamp((healing - 0.78F) / 0.22F, 0.0F, 1.0F));
    }

    private static float growthProgress(Crack crack) {
        return smoothStep(Mth.clamp(
                crack.age / (float) Math.max(1, crack.fadeInTicks), 0.0F, 1.0F));
    }

    private static float widthScale(Crack crack) {
        if (crack.age < crack.fadeInTicks) {
            float opening = smoothStep(Mth.clamp(
                    crack.age / (float) Math.max(1, crack.fadeInTicks), 0.0F, 1.0F));
            return Mth.lerp(opening, 0.38F, 0.92F);
        }
        if (crack.age < crack.fadeInTicks + crack.holdTicks) {
            float expansion = (crack.age - crack.fadeInTicks)
                    / (float) Math.max(1, Math.round(crack.holdTicks * 0.68F));
            return Mth.lerp(smoothStep(Mth.clamp(expansion, 0.0F, 1.0F)), 0.92F, 1.34F);
        }
        float healing = (crack.age - crack.fadeInTicks - crack.holdTicks)
                / (float) Math.max(1, crack.fadeOutTicks);
        return 1.34F * (1.0F - smoothStep(Mth.clamp(healing, 0.0F, 1.0F)));
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - value * 2.0F);
    }

    private static long signature() {
        long value = entityId * 31L + patternSeed;
        for (Crack crack : CRACKS) {
            if (crack.active) {
                int expansionTicks = Math.max(1, Math.round(crack.holdTicks * 0.68F));
                int visualAge;
                if (crack.age < crack.fadeInTicks) {
                    visualAge = crack.age;
                } else if (crack.age < crack.fadeInTicks + crack.holdTicks) {
                    visualAge = crack.fadeInTicks
                            + Math.min(crack.age - crack.fadeInTicks, expansionTicks) / 2;
                } else {
                    visualAge = crack.fadeInTicks + expansionTicks / 2
                            + (crack.age - crack.fadeInTicks - crack.holdTicks) / 2;
                }
                value = value * 31L + visualAge;
                value = value * 31L + Float.floatToIntBits(crack.x[0]);
                value = value * 31L + Float.floatToIntBits(crack.y[0]);
            }
        }
        return value;
    }

    private static int positiveMix(int value) {
        return mix(value) & Integer.MAX_VALUE;
    }

    private static int randomInterval(AssimilationProfile profile, int salt) {
        int range = profile.screenCrackMaxIntervalTicks()
                - profile.screenCrackMinIntervalTicks() + 1;
        return profile.screenCrackMinIntervalTicks() + positiveMix(salt) % Math.max(1, range);
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    private static Crack[] createPool() {
        Crack[] pool = new Crack[MAX_CRACKS];
        for (int index = 0; index < pool.length; index++) {
            pool[index] = new Crack();
        }
        return pool;
    }

    private static final class Crack {
        final float[] x = new float[MAX_POINTS];
        final float[] y = new float[MAX_POINTS];
        final float[] branchX = new float[MAX_BRANCH_POINTS];
        final float[] branchY = new float[MAX_BRANCH_POINTS];
        boolean active;
        int age;
        int points;
        int branchPoints;
        int branchOrigin;
        float width;

        int fadeInTicks;
        int holdTicks;
        int fadeOutTicks;

        int totalTicks() {
            return fadeInTicks + holdTicks + fadeOutTicks;
        }
    }
}
