package com.fish.mirebound.compat.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SableCompatTest {
    @Test
    void embeddedAccessorCoordinatesResolveToPlotStorage() {
        BlockPos local = new BlockPos(-3, 4, 7);
        BlockPos center = new BlockPos(64, -37, -14);

        assertEquals(new BlockPos(61, -33, -7),
                SableCompat.embeddedStoragePos(local, center));
    }
}
