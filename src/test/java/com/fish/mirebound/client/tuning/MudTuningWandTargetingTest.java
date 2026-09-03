package com.fish.mirebound.client.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudTuningWandTargetingTest {
    @Test
    void nearerHitWinsWhenVanillaAndExtendedRaysBothResolve() {
        BlockHitResult near = hit(12.0D);
        BlockHitResult far = hit(96.0D);

        assertEquals(near, MudTuningWandTargeting.nearer(
                Vec3.ZERO, far, near));
    }

    @Test
    void extendedHitIsUsedAfterVanillaMisses() {
        BlockHitResult far = hit(96.0D);

        assertEquals(far, MudTuningWandTargeting.nearer(
                Vec3.ZERO, null, far));
    }

    private static BlockHitResult hit(double x) {
        return new BlockHitResult(
                new Vec3(x, 0.5D, 0.5D), Direction.WEST,
                new BlockPos((int) x, 0, 0), false);
    }
}
