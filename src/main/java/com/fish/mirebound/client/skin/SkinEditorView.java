package com.fish.mirebound.client.skin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Editor-only grid and orientation gizmo, using the model preview's camera transform. */
final class SkinEditorView {
    static final double DEFAULT_YAW = .55D, DEFAULT_PITCH = .35D;
    record Angles(double yaw, double pitch) {}
    record Vector(double x, double y, double depth) {}
    record Frame(double cy, double sy, double cp, double sp) {
        Vector project(double x, double y, double z) {
            double depth=-sy*x+cy*z;
            return new Vector(cy*x+sy*z,cp*y-sp*depth,sp*y+cp*depth);
        }
    }
    enum Axis {
        X(1,0,0,"X",0xFFE16B69,Math.PI/2,0), NEG_X(-1,0,0,"",0xFF743C3C,-Math.PI/2,0),
        Y(0,-1,0,"Y",0xFF79BF71,0,Math.PI/2), NEG_Y(0,1,0,"",0xFF3C683A,0,-Math.PI/2),
        Z(0,0,1,"Z",0xFF6E94E1,Math.PI,0), NEG_Z(0,0,-1,"",0xFF394F83,0,0);
        final double x,y,z,yaw,pitch;
        final String label;
        final int color;
        Axis(double x,double y,double z,String label,int color,double yaw,double pitch) {
            this.x=x;this.y=y;this.z=z;this.label=label;this.color=color;this.yaw=yaw;this.pitch=pitch;
        }
    }
    private record Dot(Axis axis,Vector point) {}
    private SkinEditorView() {}

    static Frame frame(double yaw,double pitch) {return new Frame(Math.cos(yaw),Math.sin(yaw),Math.cos(pitch),Math.sin(pitch));}
    static Angles drag(double yaw,double pitch,double dx,double dy) {
        return new Angles(yaw-dx*.012,Math.max(-Math.PI/2,Math.min(Math.PI/2,pitch+dy*.012)));
    }
    static int centerX(SkinEditorLayout layout) {return layout.modelRight()-35;}
    static int centerY(SkinEditorLayout layout) {return layout.bottom()-35;}
    static boolean inGizmo(SkinEditorLayout layout,double x,double y) {
        return Math.abs(x-centerX(layout))<=33&&Math.abs(y-centerY(layout))<=33;
    }
    private static List<Dot> dots(double yaw,double pitch) {
        Frame frame=frame(yaw,pitch);
        List<Dot> dots=new ArrayList<>(6);
        for(Axis axis:Axis.values())dots.add(new Dot(axis,frame.project(axis.x,axis.y,axis.z)));
        dots.sort(Comparator.comparingDouble((Dot dot)->dot.point.depth).reversed());
        return dots;
    }

    static void gizmo(GuiGraphics g,SkinEditorLayout layout,double yaw,double pitch,int mx,int my) {
        int cx=centerX(layout),cy=centerY(layout);
        var dots=dots(yaw,pitch);
        for(Dot dot:dots) {
            int x=cx+(int)Math.round(dot.point.x*24),y=cy+(int)Math.round(dot.point.y*24);
            SkinEditorDraw.line(g,cx,cy,x,y,dot.axis.color);
        }
        g.flush();
        for(Dot dot:dots) {
            int x=cx+(int)Math.round(dot.point.x*24),y=cy+(int)Math.round(dot.point.y*24);
            int radius=dot.axis.label.isEmpty()?5:7;
            if(Math.hypot(mx-x,my-y)<=radius+1)circle(g,x,y,radius+1,0xFFEBD8A7);
            circle(g,x,y,radius,dot.axis.color);
            if(!dot.axis.label.isEmpty())g.drawCenteredString(Minecraft.getInstance().font,dot.axis.label,x,y-4,0xFF141519);
        }
        g.flush();
    }

    static Angles click(SkinEditorLayout layout,double yaw,double pitch,double x,double y) {
        var dots=dots(yaw,pitch);
        for(int i=dots.size()-1;i>=0;i--) {
            Dot dot=dots.get(i);
            if(Math.hypot(x-centerX(layout)-dot.point.x*24,y-centerY(layout)-dot.point.y*24)<=8)
                return new Angles(dot.axis.yaw,dot.axis.pitch);
        }
        return null;
    }

    static Axis axisAt(SkinEditorLayout layout,double yaw,double pitch,double x,double y) {
        for(var dot:dots(yaw,pitch)) {
            int px=centerX(layout)+(int)Math.round(dot.point.x*24), py=centerY(layout)+(int)Math.round(dot.point.y*24);
            if(Math.hypot(x-px,y-py)<=9)return dot.axis;
        }
        return null;
    }

    static SkinEditorPose.Axis poseAxisAt(double cx,double cy,double x,double y,double radius,double yaw,double pitch,
            SkinEditorPose pose,String part) {
        SkinEditorPose.Axis best=null; double bestDistance=8; Frame frame=frame(yaw,pitch);
        for(SkinEditorPose.Axis axis:SkinEditorPose.Axis.values()) {
            double previousX=0,previousY=0;
            for(int i=0;i<=96;i++) {
                double a=Math.PI*2*i/96,px,py,pz;
                switch(axis) {
                    case X -> { px=0; py=Math.cos(a); pz=Math.sin(a); }
                    case Y -> { px=Math.cos(a); py=0; pz=Math.sin(a); }
                    case Z -> { px=Math.cos(a); py=Math.sin(a); pz=0; }
                    default -> throw new IllegalStateException();
                }
                double[] local=pose.transformNormal(part,px,py,pz);
                var point=frame.project(local[0],local[1],local[2]);
                double sx=cx+point.x()*radius, sy=cy+point.y()*radius;
                if(i>0) {
                    double distance=segmentDistance(x,y,previousX,previousY,sx,sy);
                    if(distance<bestDistance){bestDistance=distance;best=axis;}
                }
                previousX=sx; previousY=sy;
            }
        }
        return best;
    }

    static void poseGizmo(GuiGraphics g,double cx,double cy,double radius,SkinEditorPose.Axis active,double yaw,double pitch,
            SkinEditorPose pose,String part) {
        Frame frame=frame(yaw,pitch);
        ring(g,cx,cy,radius,frame,pose,part,SkinEditorPose.Axis.X,0xD0E16B69,active==SkinEditorPose.Axis.X);
        ring(g,cx,cy,radius,frame,pose,part,SkinEditorPose.Axis.Y,0xD079BF71,active==SkinEditorPose.Axis.Y);
        ring(g,cx,cy,radius,frame,pose,part,SkinEditorPose.Axis.Z,0xD06E94E1,active==SkinEditorPose.Axis.Z);
        circle(g,(int)cx,(int)cy,5,0xFFEBD8A7);
    }

    private static void ring(GuiGraphics g,double cx,double cy,double radius,Frame frame,SkinEditorPose pose,String part,
            SkinEditorPose.Axis axis,int color,boolean active) {
        int lineColor=active?0xFFFFD98A:color; double oldX=0,oldY=0;
        for(int i=0;i<=96;i++) {
            double a=Math.PI*2*i/96,px,py,pz;
            switch(axis) {
                case X -> {px=0;py=Math.cos(a);pz=Math.sin(a);}
                case Y -> {px=Math.cos(a);py=0;pz=Math.sin(a);}
                case Z -> {px=Math.cos(a);py=Math.sin(a);pz=0;}
                default -> throw new IllegalStateException();
            }
            double[] local=pose.transformNormal(part,px,py,pz);
            var point=frame.project(local[0],local[1],local[2]);
            double sx=cx+point.x()*radius,sy=cy+point.y()*radius;
            if(i>0)SkinEditorDraw.line(g,oldX,oldY,sx,sy,lineColor);
            oldX=sx;oldY=sy;
        }
    }

    private static double segmentDistance(double px,double py,double x0,double y0,double x1,double y1) {
        double dx=x1-x0,dy=y1-y0;
        if(dx*dx+dy*dy<1e-8)return Math.hypot(px-x0,py-y0);
        double t=Math.max(0,Math.min(1,((px-x0)*dx+(py-y0)*dy)/(dx*dx+dy*dy)));
        return Math.hypot(px-(x0+t*dx),py-(y0+t*dy));
    }

    private static void circle(GuiGraphics g,int x,int y,int radius,int color) {
        SkinEditorDraw.disc(g,x,y,radius,color);
    }

    static void grid(GuiGraphics g,Frame frame,double scale,double cx,double cy) {
        for(int i=-16;i<=16;i+=2) {
            line(g,frame,scale,cx,cy,i,24,-16,i,24,16,i==0?0x994D72A8:0x443E3D39);
            line(g,frame,scale,cx,cy,-16,24,i,16,24,i,i==0?0x99B66859:0x443E3D39);
        }
        line(g,frame,scale,cx,cy,0,24,0,0,-8,0,0x666E9D61);
        g.flush();
    }
    private static void line(GuiGraphics g,Frame frame,double scale,double cx,double cy,
            double x0,double y0,double z0,double x1,double y1,double z1,int color) {
        Vector a=frame.project(x0,y0-8,z0),b=frame.project(x1,y1-8,z1);
        SkinEditorDraw.modelLine(g,cx+a.x*scale,cy+a.y*scale,a.depth,
                cx+b.x*scale,cy+b.y*scale,b.depth,color);
    }
}
