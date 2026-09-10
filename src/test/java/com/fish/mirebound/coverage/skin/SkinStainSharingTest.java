package com.fish.mirebound.coverage.skin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.BitSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkinStainSharingTest {
    @Test void onlyTheAuthenticatedSenderChangesAndUpdatesAreThrottled() {
        var state=new SkinStainSharing.State();
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var allow=SkinStainMaskWire.encode(SkinStainMask.empty(64,64));
        BitSet bits=new BitSet();bits.set(10);
        var deny=SkinStainMaskWire.encode(new SkinStainMask(64,64,bits));
        assertTrue(state.update(a,100,allow));assertTrue(state.update(b,100,deny));
        assertFalse(state.update(a,101,deny));assertEquals(allow,state.get(a));
        assertTrue(state.update(a,141,deny));assertEquals(deny,state.get(b));
        assertFalse(state.update(a,190,deny));
        state.remove(a);assertNull(state.get(a));assertEquals(deny,state.get(b));
        state.clear();assertNull(state.get(b));
    }

    @Test void invalidCompressedInputKeepsPreviousSetting() {
        var state=new SkinStainSharing.State();UUID id=UUID.randomUUID();
        var allow=SkinStainMaskWire.encode(SkinStainMask.empty(64,64));
        state.update(id,0,allow);
        assertFalse(state.update(id,100,new SkinStainMaskWire(64,64,new byte[]{1,2,3})));
        assertEquals(allow,state.get(id));
    }
}
