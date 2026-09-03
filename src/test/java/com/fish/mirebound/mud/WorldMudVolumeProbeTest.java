package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class WorldMudVolumeProbeTest {
    @Test
    void partialEdgeImmersionUsesActualBodyVolume() {
        AABB body = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 2.0D, 1.0D);
        WorldMudVolumeProbe probe = new WorldMudVolumeProbe(List.of(
                new WorldMudVolume(
                        BlockPos.ZERO, null, SinkingMedium.MUD,
                        new AABB(0.0D, 0.0D, 0.0D, 0.25D, 1.0D, 1.0D))));

        WorldVolumeImmersion result = probe.immersion(body);

        assertEquals(0.125D, result.immersion(), 1.0E-12D);
        assertEquals(0.25D, result.immersedVolume(), 1.0E-12D);
        assertEquals(2.0D, result.bodyVolume(), 1.0E-12D);
    }

    @Test
    void adjacentMudVolumesCombineWithoutAThresholdJump() {
        AABB body = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 2.0D, 1.0D);
        WorldMudVolumeProbe probe = new WorldMudVolumeProbe(List.of(
                new WorldMudVolume(
                        BlockPos.ZERO, null, SinkingMedium.MUD,
                        new AABB(0.0D, 0.0D, 0.0D, 0.45D, 1.0D, 1.0D)),
                new WorldMudVolume(
                        BlockPos.ZERO, null, SinkingMedium.SOFT_QUICKSAND,
                        new AABB(0.45D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D))));

        WorldVolumeImmersion result = probe.immersion(body);

        assertEquals(0.5D, result.immersion(), 1.0E-12D);
        assertEquals(SinkingMedium.SOFT_QUICKSAND, result.medium());
    }
}
