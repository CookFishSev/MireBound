package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudSplashTrailTest {
    @Test
    void samplesTheRecordedCurveInsteadOfAProjectedStraightLine() {
        MudSplashTrail trail = new MudSplashTrail();
        trail.reset(new Vec3(0.0D, 0.0D, 0.0D));
        trail.record(new Vec3(0.0D, 1.0D, 0.0D));
        trail.record(new Vec3(1.0D, 1.0D, 0.0D));

        Vec3 head = new Vec3(1.0D, 1.0D, 0.0D);
        Vec3 corner = trail.sampleBack(head, 1.0D);
        Vec3 lower = trail.sampleBack(head, 1.5D);

        assertEquals(new Vec3(0.0D, 1.0D, 0.0D), corner);
        assertEquals(new Vec3(0.0D, 0.5D, 0.0D), lower);
        assertEquals(2.0D, trail.availableLength(head, 8.0D), 1.0E-9D);
    }
}
