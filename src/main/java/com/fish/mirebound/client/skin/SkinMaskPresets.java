package com.fish.mirebound.client.skin;

import com.fish.mirebound.coverage.skin.SkinStainMask;

final class SkinMaskPresets {
    static final String[] NAMES={"all_allowed","all_denied","eyes","face","outer","fresh_moves"};
    private SkinMaskPresets() {}
    static SkinStainMask create(int preset,int width,int height) {
        SkinMaskEdit edit=new SkinMaskEdit(SkinStainMask.empty(64,64));
        if(preset==1)edit.rectangle(0,0,63,63,true);
        else if(preset==2) {
            edit.rectangle(8,11,15,13,true);
            edit.rectangle(40,11,47,13,true);
        } else if(preset==3) {
            edit.rectangle(8,8,15,15,true);
            edit.rectangle(40,8,47,15,true);
        } else if(preset==4) {
            edit.rectangle(32,0,63,15,true);
            edit.rectangle(0,32,55,47,true);
            edit.rectangle(0,48,15,63,true);
            edit.rectangle(48,48,63,63,true);
        } else if(preset==5) {
            // UV cells used by the eyes subtree in Fresh Moves 3.1, including blink/pupil variants.
            long[] rows={0x000000E7000000F3L,0x000000FF000000FFL,0x000000E7FF0000F3L,0x000000FFFF0000FFL,
                    0x0000000FFF0000F3L,0x0000000FFF0000FFL,0x000000FFFF0000F3L,0x000000F9FF0000FFL};
            for(int y=0;y<rows.length;y++)for(int x=0;x<64;x++)
                if((rows[y]&(1L<<x))!=0)edit.rectangle(x,y,x,y,true);
        }
        edit.end();
        return edit.snapshot().resized(width,height);
    }
}
