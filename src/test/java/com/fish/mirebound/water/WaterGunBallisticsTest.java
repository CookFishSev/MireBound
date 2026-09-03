package com.fish.mirebound.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WaterGunBallisticsTest {
    @Test
    void sampledStreamIsBoundedAndEndsNearConfiguredRange() {
        WaterGunProfile profile = WaterGunProfile.DEFAULT;
        List<Vec3> points = WaterGunBallistics.sample(
                Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D), profile);

        assertTrue(points.size() >= 2 && points.size() <= 128);
        double traveled = 0.0D;
        for (int index = 1; index < points.size(); index++) {
            traveled += points.get(index - 1).distanceTo(points.get(index));
            assertTrue(points.get(index - 1).distanceTo(points.get(index))
                    <= profile.segmentLength() + 1.0E-7D);
        }
        assertTrue(traveled >= profile.maximumRange());
        assertEquals(profile.maximumRange(),
                points.getLast().distanceTo(points.getFirst()), 1.0E-6D);
    }

    @Test
    void upwardShotCompletesItsRiseAndFallInsideTheSpatialRange() {
        Vec3 origin = new Vec3(2.0D, 5.0D, -1.0D);
        List<Vec3> points = WaterGunBallistics.sample(
                origin,
                new Vec3(0.45D, 0.89D, 0.0D),
                WaterGunProfile.DEFAULT);

        double highest = points.stream().mapToDouble(point -> point.y).max().orElseThrow();
        assertTrue(highest > origin.y + 10.0D);
        assertTrue(points.getLast().y < origin.y - 8.0D);
        assertEquals(WaterGunProfile.DEFAULT.maximumRange(),
                points.getLast().distanceTo(origin), 1.0E-5D);
    }

    @Test
    void networkSnapshotKeepsTheCompleteArcAndBothEndpoints() {
        List<Vec3> points = WaterGunBallistics.sample(
                Vec3.ZERO,
                new Vec3(0.45D, 0.89D, 0.0D),
                WaterGunProfile.DEFAULT);
        List<Vec3> synced = WaterGunSystem.syncPath(points);

        assertTrue(synced.size() <= 48);
        assertEquals(points.getFirst(), synced.getFirst());
        assertEquals(points.getLast(), synced.getLast());
        assertTrue(synced.stream().mapToDouble(point -> point.y).max().orElseThrow() > 10.0D);
        assertTrue(synced.getLast().y < -8.0D);
    }

    @Test
    void gravityBendsHorizontalStreamDownWithoutReversingForwardTravel() {
        List<Vec3> points = WaterGunBallistics.sample(
                new Vec3(2.0D, 5.0D, -1.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                WaterGunProfile.DEFAULT);

        for (int index = 1; index < points.size(); index++) {
            assertTrue(points.get(index).x > points.get(index - 1).x);
            assertTrue(points.get(index).y <= points.get(index - 1).y);
        }
        assertTrue(points.getLast().y < points.getFirst().y - 3.0D);
        assertTrue(points.getLast().y > points.getFirst().y - 4.5D);
    }

    @Test
    void samplingDependsOnlyOnMuzzleAndLaunchDirection() {
        WaterGunProfile profile = WaterGunProfile.DEFAULT;
        Vec3 origin = new Vec3(0.45D, 1.35D, 0.0D);
        Vec3 translation = new Vec3(-2.0D, 4.0D, 7.5D);
        Vec3 direction = new Vec3(0.12D, 0.08D, 1.0D).normalize();
        List<Vec3> original = WaterGunBallistics.sample(origin, direction, profile);
        List<Vec3> moved = WaterGunBallistics.sample(origin.add(translation), direction, profile);

        assertEquals(original.size(), moved.size());
        for (int index = 0; index < original.size(); index++) {
            Vec3 expected = original.get(index).add(translation);
            assertEquals(expected.x, moved.get(index).x, 1.0E-12D);
            assertEquals(expected.y, moved.get(index).y, 1.0E-12D);
            assertEquals(expected.z, moved.get(index).z, 1.0E-12D);
        }
    }

    @Test
    void washRadiusClearlySpreadsWithDistanceAndStopsAtConfiguredMaximum() {
        WaterGunProfile profile = WaterGunProfile.DEFAULT;

        float near = profile.washRadius(1.0D);
        float middle = profile.washRadius(9.0D);
        float far = profile.washRadius(profile.maximumRange());

        assertTrue(middle > near + 0.30F);
        assertTrue(far > middle + 0.30F);
        assertEquals(profile.maximumWashRadius(), far, 1.0E-6F);
        assertEquals(profile.maximumWashRadius(), profile.washRadius(1000.0D), 1.0E-6F);
    }

    @Test
    void syncedVisualSettingsDoNotChangeServerOnlyCleaningValues() {
        WaterGunProfile base = WaterGunProfile.DEFAULT;
        WaterGunProfile synced = base.withVisualSettings(
                1500,
                7,
                2.0D, 0.08D, 24.0D, 0.4D, 0.06D,
                0.76D, 4.25D,
                0.4F, 0.06F, 1.4F, 384);

        assertEquals(1500, synced.capacity());
        assertEquals(7, synced.waterPerTick());
        assertEquals(base.washAmountPerTick(), synced.washAmountPerTick());
        assertEquals(24.0D, synced.maximumRange());
        assertEquals(0.76D, synced.firingMovementScale());
        assertEquals(4.25D, synced.recoilDegrees());
        assertEquals(1.4F, synced.maximumWashRadius());
        assertEquals(384, synced.sableMaximumBlockSamples());
    }
}
