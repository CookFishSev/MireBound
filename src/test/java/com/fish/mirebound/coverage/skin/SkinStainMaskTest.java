package com.fish.mirebound.coverage.skin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.BitSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SkinStainMaskTest {
    @Test void hdPixelsRemainIndependentAndLegacyHeightDoesNotMoveTheEyes() {
        BitSet bits=new BitSet(); bits.set(12*64+9);
        SkinStainMask original=new SkinStainMask(64,64,bits);
        SkinStainMask hd=original.resized(256,256);
        assertEquals(16,hd.count());
        assertTrue(hd.blocked(36,48)); assertTrue(hd.blocked(39,51));
        assertFalse(hd.blocked(40,48));
        SkinStainMask legacy=original.resized(64,32);
        assertFalse(legacy.blocked(9,12)); assertTrue(legacy.blocked(9,6));
        BitSet one=hd.copyBits(); one.clear(48*256+36);
        assertFalse(new SkinStainMask(256,256,one).blocked(36,48));
        assertTrue(hd.blocked(36,48));
    }

    @Test void sparseRasterAndDirectLookupAgreeAtNonIntegerScale() {
        BitSet bits=new BitSet(); Random random=new Random(713);
        for(int i=0;i<64*64;i++)if(random.nextInt(7)==0)bits.set(i);
        SkinStainMask source=new SkinStainMask(64,64,bits);
        for(int width:new int[]{32,96,128,257}) {
            BitSet raster=new BitSet(); source.forEachBlocked(width,width/2,raster::set);
            for(int y=0;y<width/2;y++)for(int x=0;x<width;x++)
                assertEquals(!source.allows(x,y,width,width/2),raster.get(y*width+x));
        }
    }

    @Test void callerMutationAndOutOfBoundsBitsCannotChangeSavedMask() {
        BitSet bits=new BitSet(); bits.set(7);
        SkinStainMask mask=new SkinStainMask(64,64,bits);
        bits.clear(); mask.copyBits().clear(); assertEquals(1,mask.count());
        bits.set(4096);
        assertThrows(IllegalArgumentException.class,()->new SkinStainMask(64,64,bits));
        assertThrows(IllegalArgumentException.class,()->SkinStainMask.empty(100000,64));
    }
}
