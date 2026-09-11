package com.fish.mirebound.client.skin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Shared orthographic projection for the displayed quads and their pixel picking. */
public final class SkinEditorProjection {
    public record Point(double x, double y, double depth, float u, float v) {}
    public record Quad(SkinEditorMesh.Face face, List<Point> points, double depth, float light) {}
    public record Hit(Quad quad, float u, float v, double depth) {
        public int x(int width) { return Math.max(0, Math.min(width - 1, (int) (u * width))); }
        public int y(int height) { return Math.max(0, Math.min(height - 1, (int) (v * height))); }
    }
    private SkinEditorProjection() {}

    public static List<Quad> project(List<SkinEditorMesh.Face> faces, Predicate<SkinEditorMesh.Face> visible,
            double yaw, double pitch, double scale, double centerX, double centerY) {
        return project(faces,visible,yaw,pitch,scale,centerX,centerY,null);
    }

    public static List<Quad> project(List<SkinEditorMesh.Face> faces, Predicate<SkinEditorMesh.Face> visible,
            double yaw, double pitch, double scale, double centerX, double centerY, SkinEditorPose pose) {
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        List<Quad> result = new ArrayList<>();
        for (var face : faces) {
            if (!visible.test(face)) continue;
            double[] normal=pose==null?new double[]{face.nx(),face.ny(),face.nz()}:pose.transformNormal(face.part(),face.nx(),face.ny(),face.nz());
            double nz = -sy * normal[0] + cy * normal[2];
            double normalZ = sp * normal[1] + cp * nz;
            if (normalZ >= -1e-6) continue;
            List<Point> points = new ArrayList<>(4);
            double depth = 0;
            for (var vertex : face.vertices()) {
                double[] posed=pose==null?new double[]{vertex.x(),vertex.y(),vertex.z()}:pose.transform(face.part(),vertex.x(),vertex.y(),vertex.z());
                double x = cy * posed[0] + sy * posed[2];
                double z = -sy * posed[0] + cy * posed[2];
                double y = posed[1] - 8;
                double rotatedY = cp * y - sp * z, rotatedZ = sp * y + cp * z;
                points.add(new Point(centerX + x * scale, centerY + rotatedY * scale, rotatedZ, vertex.u(), vertex.v()));
                depth += rotatedZ;
            }
            float light = (float) (.65 + .35 * Math.max(0, -normalZ));
            result.add(new Quad(face, List.copyOf(points), depth / 4, light));
        }
        result.sort(Comparator.comparingDouble(Quad::depth).reversed());
        return result;
    }

    public static Hit pick(List<Quad> quads, double x, double y) {
        Hit closest = null;
        for (Quad quad : quads) {
            Hit hit = triangle(quad, 0, 1, 2, x, y);
            if (hit == null) hit = triangle(quad, 0, 2, 3, x, y);
            // Visible outer geometry owns editing even where its skin texel is transparent.
            if (hit != null && (closest == null || hit.depth < closest.depth)) closest = hit;
        }
        return closest;
    }

    static Point screenPoint(Quad quad, double x, double y) {
        Point a=quad.points.get(0), b=quad.points.get(1), d=quad.points.get(3);
        double bx=b.x-a.x, by=b.y-a.y, dx=d.x-a.x, dy=d.y-a.y, det=bx*dy-dx*by;
        if(Math.abs(det)<1e-10)return null;
        double s=((x-a.x)*dy-(y-a.y)*dx)/det, t=(bx*(y-a.y)-by*(x-a.x))/det;
        return new Point(x,y,a.depth+s*(b.depth-a.depth)+t*(d.depth-a.depth),
                (float)(a.u+s*(b.u-a.u)+t*(d.u-a.u)),(float)(a.v+s*(b.v-a.v)+t*(d.v-a.v)));
    }

    static boolean exposed(List<Quad> quads, Quad target, Point point) {
        for(Quad quad:quads) {
            if(quad==target)continue;
            Point a=quad.points.get(0), b=quad.points.get(1), d=quad.points.get(3);
            double bx=b.x-a.x, by=b.y-a.y, dx=d.x-a.x, dy=d.y-a.y, det=bx*dy-dx*by;
            if(Math.abs(det)<1e-10)continue;
            double s=((point.x-a.x)*dy-(point.y-a.y)*dx)/det;
            double t=(bx*(point.y-a.y)-by*(point.x-a.x))/det;
            if(s>=0&&s<=1&&t>=0&&t<=1&&a.depth+s*(b.depth-a.depth)+t*(d.depth-a.depth)<point.depth-1e-6)return false;
        }
        return true;
    }

    private static Hit triangle(Quad quad, int ai, int bi, int ci, double x, double y) {
        Point a = quad.points.get(ai), b = quad.points.get(bi), c = quad.points.get(ci);
        double det = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y);
        if (Math.abs(det) < 1e-10) return null;
        double wa = ((b.y-c.y)*(x-c.x)+(c.x-b.x)*(y-c.y))/det;
        double wb = ((c.y-a.y)*(x-c.x)+(a.x-c.x)*(y-c.y))/det;
        double wc = 1-wa-wb;
        if (wa < -1e-6 || wb < -1e-6 || wc < -1e-6) return null;
        return new Hit(quad, (float)(wa*a.u+wb*b.u+wc*c.u), (float)(wa*a.v+wb*b.v+wc*c.v),
                wa*a.depth+wb*b.depth+wc*c.depth);
    }

    public static Point uvPoint(Quad quad, float u, float v) {
        Point a = quad.points.get(0), b = quad.points.get(1), d = quad.points.get(3);
        double bu=b.u-a.u, bv=b.v-a.v, du=d.u-a.u, dv=d.v-a.v;
        double det=bu*dv-du*bv;
        if (Math.abs(det)<1e-10) return null;
        double s=((u-a.u)*dv-(v-a.v)*du)/det, t=(bu*(v-a.v)-bv*(u-a.u))/det;
        return new Point(a.x+s*(b.x-a.x)+t*(d.x-a.x), a.y+s*(b.y-a.y)+t*(d.y-a.y),
                a.depth+s*(b.depth-a.depth)+t*(d.depth-a.depth), u, v);
    }
}
