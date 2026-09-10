package com.fish.mirebound.client.coverage;

import net.minecraft.world.phys.Vec3;

/** Geometry identity and interpolation are independent of material UV ownership. */
public final class EquipmentSurfaceGeometry {
    private EquipmentSurfaceGeometry() {}
    public record Vertex(float x, float y, float z, float u, float v) {
        public Vec3 position() { return new Vec3(x, y, z); }
    }
    public record Face(long id, Vertex a, Vertex b, Vertex c, Vertex d, int width, int height) {
        public Vec3 point(double u, double v) { return a.position().lerp(b.position(), u).lerp(d.position().lerp(c.position(), u), v); }
        public float u(double x, double y) { return (float) ((a.u + (b.u - a.u) * x) * (1-y) + (d.u + (c.u - d.u) * x) * y); }
        public float v(double x, double y) { return (float) ((a.v + (b.v - a.v) * x) * (1-y) + (d.v + (c.v - d.v) * x) * y); }
        public int cell(int x, int y) { return y * 16 + x; }
    }
    public static Face face(String owner, Vertex[] v, float unitsPerPixel) {
        if (owner == null || v == null || v.length != 4 || !Float.isFinite(unitsPerPixel) || unitsPerPixel <= 0)
            throw new IllegalArgumentException("Invalid equipment face");
        long id = 0xcbf29ce484222325L;
        for (int i = 0; i < owner.length(); i++) id = (id ^ owner.charAt(i)) * 0x100000001b3L;
        for (Vertex p : v) {
            if (p == null || !Float.isFinite(p.x) || !Float.isFinite(p.y) || !Float.isFinite(p.z)
                    || !Float.isFinite(p.u) || !Float.isFinite(p.v)) throw new IllegalArgumentException("Invalid equipment vertex");
            id = (id ^ Float.floatToIntBits(p.x)) * 0x100000001b3L;
            id = (id ^ Float.floatToIntBits(p.y)) * 0x100000001b3L;
            id = (id ^ Float.floatToIntBits(p.z)) * 0x100000001b3L;
        }
        int width = Math.max(1, Math.min(16, (int) Math.ceil(v[0].position().distanceTo(v[1].position()) / unitsPerPixel)));
        int height = Math.max(1, Math.min(16, (int) Math.ceil(v[0].position().distanceTo(v[3].position()) / unitsPerPixel)));
        return new Face(id == 0 ? 1 : id, v[0], v[1], v[2], v[3], width, height);
    }
    public static boolean visible(int abgr, int vertexAlpha) { return (abgr >>> 24) > 0 && vertexAlpha > 0; }
}
