package com.fish.mirebound.client.skin;

import java.util.ArrayList;
import java.util.List;

/** Bounded UV-space selection work, with the same geometry occlusion as model picking. */
final class SkinEditorSelection {
    private record Region(SkinEditorProjection.Quad quad,int left,int top,int right,int bottom) {}
    private final List<SkinEditorProjection.Quad> quads;
    private final List<Region> regions=new ArrayList<>();
    private final double left,top,right,bottom;
    private final boolean deny;
    private int regionIndex,x,y,visited,total;

    SkinEditorSelection(List<SkinEditorProjection.Quad> quads,int width,int height,
            double x0,double y0,double x1,double y1,boolean deny) {
        this.quads=List.copyOf(quads);this.deny=deny;
        left=Math.min(x0,x1);right=Math.max(x0,x1);top=Math.min(y0,y1);bottom=Math.max(y0,y1);
        for(var quad:quads) {
            float u0=1,v0=1,u1=0,v1=0;
            for(var p:quad.points()) {u0=Math.min(u0,p.u());v0=Math.min(v0,p.v());u1=Math.max(u1,p.u());v1=Math.max(v1,p.v());}
            float minU=Float.POSITIVE_INFINITY,minV=minU,maxU=Float.NEGATIVE_INFINITY,maxV=maxU;
            for(double sx:new double[]{left,right})for(double sy:new double[]{top,bottom}) {
                var p=SkinEditorProjection.screenPoint(quad,sx,sy);if(p==null)continue;
                minU=Math.min(minU,p.u());minV=Math.min(minV,p.v());maxU=Math.max(maxU,p.u());maxV=Math.max(maxV,p.v());
            }
            int l=Math.max(0,(int)Math.floor(Math.max(u0,minU)*width));
            int t=Math.max(0,(int)Math.floor(Math.max(v0,minV)*height));
            int r=Math.min(width,(int)Math.ceil(Math.min(u1,maxU)*width));
            int b=Math.min(height,(int)Math.ceil(Math.min(v1,maxV)*height));
            if(r>l&&b>t) {regions.add(new Region(quad,l,t,r,b));total+=(r-l)*(b-t);}
        }
        startRegion();
    }

    private void startRegion() {
        if(regionIndex<regions.size()){x=regions.get(regionIndex).left;y=regions.get(regionIndex).top;}
    }
    boolean step(SkinMaskEdit edit,int budget) {
        // No world queries or texture reads: even 4096px skins use a bounded amount of work per tick.
        while(regionIndex<regions.size()&&budget-->0) {
            Region region=regions.get(regionIndex);
            var point=SkinEditorProjection.uvPoint(region.quad,(x+.5F)/edit.width(),(y+.5F)/edit.height());
            if(point!=null&&point.x()>=left&&point.x()<=right&&point.y()>=top&&point.y()<=bottom
                    &&SkinEditorProjection.exposed(quads,region.quad,point))edit.rectangle(x,y,x,y,deny);
            visited++;
            if(++x>=region.right) {x=region.left;if(++y>=region.bottom){regionIndex++;startRegion();}}
        }
        return regionIndex==regions.size();
    }
    int percent() { return total==0?100:(int)(100L*visited/total); }
}
