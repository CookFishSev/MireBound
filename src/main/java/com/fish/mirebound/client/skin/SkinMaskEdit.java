package com.fish.mirebound.client.skin;

import com.fish.mirebound.coverage.skin.SkinStainMask;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;

/** One undo step per stroke, bounded even for HD skins. */
public final class SkinMaskEdit {
    private static final int MAX_HISTORY_BYTES = 8 * 1024 * 1024;
    private SkinStainMask mask;
    private BitSet stroke;
    private final Deque<SkinStainMask> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private long revision;
    private int dirtyLeft=Integer.MAX_VALUE, dirtyTop=Integer.MAX_VALUE, dirtyRight=-1, dirtyBottom=-1;

    public SkinMaskEdit(SkinStainMask mask) { this.mask = mask; }
    public int width() { return mask.width(); }
    public int height() { return mask.height(); }
    public long revision() { return revision; }
    public boolean blocked(int x, int y) {
        return x >= 0 && y >= 0 && x < width() && y < height()
                && (stroke == null ? mask.blocked(x, y) : stroke.get(y * width() + x));
    }
    public SkinStainMask snapshot() {
        return stroke == null ? mask : new SkinStainMask(width(), height(), stroke);
    }
    public void begin() { if (stroke == null) stroke = mask.copyBits(); }
    public void rectangle(int x0, int y0, int x1, int y1, boolean deny) {
        begin();
        int left = Math.max(0, Math.min(x0, x1)), right = Math.min(width() - 1, Math.max(x0, x1));
        int top = Math.max(0, Math.min(y0, y1)), bottom = Math.min(height() - 1, Math.max(y0, y1));
        if (right < left || bottom < top) return;
        for (int y = top; y <= bottom; y++) stroke.set(y * width() + left, y * width() + right + 1, deny);
        dirty(left,top,right+1,bottom+1);
        revision++;
    }
    public void line(int x0, int y0, int x1, int y1, int brush, boolean deny) {
        line(x0,y0,x1,y1,brush,deny,0,0,width(),height());
    }
    public void line(int x0, int y0, int x1, int y1, int brush, boolean deny,
            int left, int top, int right, int bottom) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int low = (brush - 1) / 2, high = brush / 2;
        for (int i = 0; i <= steps; i++) {
            int x = steps == 0 ? x1 : x0 + (x1 - x0) * i / steps;
            int y = steps == 0 ? y1 : y0 + (y1 - y0) * i / steps;
            int l=Math.max(left,x-low),t=Math.max(top,y-low),r=Math.min(right-1,x+high),b=Math.min(bottom-1,y+high);
            if(l<=r&&t<=b)rectangle(l,t,r,b,deny);
        }
    }
    public void end() {
        if (stroke == null) return;
        SkinStainMask result = new SkinStainMask(width(), height(), stroke);
        stroke = null;
        if (!mask.equals(result)) {
            undo.addLast(mask); trim(undo); mask=result; redo.clear(); revision++;
        }
    }
    public void replace(SkinStainMask next) {
        if (mask.equals(next)) return;
        undo.addLast(mask);
        trim(undo);
        mask = next;
        redo.clear();
        dirty(0,0,width(),height());
        revision++;
    }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public void undo() {
        end();
        if (undo.isEmpty()) return;
        redo.addLast(mask); mask = undo.removeLast(); revision++;
        dirty(0,0,width(),height());
    }
    public void redo() {
        if (redo.isEmpty()) return;
        undo.addLast(mask); mask = redo.removeLast(); revision++;
        dirty(0,0,width(),height());
    }
    private void dirty(int x0,int y0,int x1,int y1) {
        dirtyLeft=Math.min(dirtyLeft,x0); dirtyTop=Math.min(dirtyTop,y0);
        dirtyRight=Math.max(dirtyRight,x1); dirtyBottom=Math.max(dirtyBottom,y1);
    }
    public int[] takeDirtyRegion(boolean full) {
        int[] region=full?new int[]{0,0,width(),height()}:dirtyRight<0?null:new int[]{dirtyLeft,dirtyTop,dirtyRight,dirtyBottom};
        dirtyLeft=dirtyTop=Integer.MAX_VALUE; dirtyRight=dirtyBottom=-1;
        return region;
    }
    private static void trim(Deque<SkinStainMask> history) {
        long bytes = history.stream().mapToLong(SkinStainMask::storageBytes).sum();
        while (history.size() > 32 || bytes > MAX_HISTORY_BYTES) bytes -= history.removeFirst().storageBytes();
    }
}
