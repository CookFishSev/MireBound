package com.fish.mirebound.content.mudwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudBallHitGeometryTest {
    private static final Vec3 PLAYER_POSITION = new Vec3(10.0D, 64.0D, 20.0D);
    private static final double EYE_Y = 65.62D;

    @Test
    void onlyFrontHeadHitsObstructTheScreen() {
        assertTrue(MudBallHitGeometry.strikesFrontHead(
                new Vec3(10.0D, 65.45D, 20.30D),
                new Vec3(0.0D, 0.0D, -1.0D),
                PLAYER_POSITION, EYE_Y, 0.0F, 0.0F));
        assertFalse(MudBallHitGeometry.strikesFrontHead(
                new Vec3(10.0D, 65.45D, 19.70D),
                new Vec3(0.0D, 0.0D, 1.0D),
                PLAYER_POSITION, EYE_Y, 0.0F, 0.0F));
        assertFalse(MudBallHitGeometry.strikesFrontHead(
                new Vec3(10.30D, 65.45D, 20.0D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                PLAYER_POSITION, EYE_Y, 0.0F, 0.0F));
        assertFalse(MudBallHitGeometry.strikesFrontHead(
                new Vec3(10.0D, 64.95D, 20.30D),
                new Vec3(0.0D, 0.0D, -1.0D),
                PLAYER_POSITION, EYE_Y, 0.0F, 0.0F));
    }

    @Test
    void headYawRotatesTheAcceptedFace() {
        assertTrue(MudBallHitGeometry.strikesFrontHead(
                new Vec3(9.70D, 65.45D, 20.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                PLAYER_POSITION, EYE_Y, 90.0F, 0.0F));
        assertFalse(MudBallHitGeometry.strikesFrontHead(
                new Vec3(10.30D, 65.45D, 20.0D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                PLAYER_POSITION, EYE_Y, 90.0F, 0.0F));
    }

    @Test
    void lookingUpTreatsAProjectileFromAboveAsAFaceHit() {
        assertTrue(MudBallHitGeometry.strikesFrontHead(
                new Vec3(10.0D, 65.95D, 20.0D),
                new Vec3(0.0D, -1.0D, 0.0D),
                PLAYER_POSITION, EYE_Y, 0.0F, -90.0F));
    }

    @Test
    void flightSegmentKeepsTheActualVerticalHitPosition() {
        AABB playerBox = new AABB(
                9.7D, 64.0D, 19.7D,
                10.3D, 65.8D, 20.3D);

        Vec3 impact = MudBallHitGeometry.resolveEntityImpact(
                playerBox,
                new Vec3(10.0D, 65.5D, 21.0D),
                new Vec3(10.0D, 65.5D, 19.0D),
                new Vec3(10.0D, 64.0D, 20.0D));

        assertEquals(65.5D, impact.y, 1.0E-9D);
        assertEquals(20.3D, impact.z, 1.0E-9D);
    }
}
