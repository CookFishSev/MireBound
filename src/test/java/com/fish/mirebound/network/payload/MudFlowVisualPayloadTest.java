package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MudFlowVisualPayloadTest {
    @Test
    void clampsVisualOnlyValuesToCompactProtocolBounds() {
        MudFlowVisualPayload payload = new MudFlowVisualPayload(
                BlockPos.ZERO.asLong(), BlockPos.ZERO.below().asLong(),
                SinkingMedium.MUD, -4, 22, 0, 40);

        assertEquals(0, payload.sourcePixelsAfter());
        assertEquals(16, payload.targetPixelsAfter());
        assertEquals(1, payload.transferredPixels());
        assertEquals(12, payload.durationTicks());
    }
}
