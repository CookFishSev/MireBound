package com.fish.mirebound.client;

import net.minecraft.world.phys.Vec3;

/** Shared allocation-free assembly and interpolation for runtime-rendered UV quads. */
final class RuntimeUvQuadAssembler {
    @FunctionalInterface
    interface QuadConsumer {
        void accept(Vertex[] quad);
    }

    static final class Vertex {
        final Vec3 position;
        Vec3 normal;
        float u;
        float v;
        boolean hasUv;
        private boolean committed;

        Vertex(Vec3 position) {
            this.position = position;
        }
    }

    private final Vertex[] quad = new Vertex[4];
    private final QuadConsumer consumer;
    private Vertex current;
    private int quadSize;

    RuntimeUvQuadAssembler(QuadConsumer consumer) {
        this.consumer = consumer;
    }

    void beginVertex(Vec3 position) {
        commitCurrent();
        current = new Vertex(position);
    }

    void setUv(float u, float v) {
        if (current == null) {
            return;
        }
        current.u = u;
        current.v = v;
        current.hasUv = true;
    }

    void setNormal(float x, float y, float z) {
        if (current != null) {
            current.normal = new Vec3(x, y, z);
        }
    }

    void commitCurrent() {
        if (current == null || current.committed) {
            return;
        }
        current.committed = true;
        if (!current.hasUv) {
            return;
        }
        quad[quadSize++] = current;
        if (quadSize == quad.length) {
            consumer.accept(quad);
            quadSize = 0;
        }
    }

    static Vec3 interpolateTriangle(float u, float v, Vertex a, Vertex b, Vertex c) {
        float denominator = (b.v - c.v) * (a.u - c.u) + (c.u - b.u) * (a.v - c.v);
        if (Math.abs(denominator) < 1.0E-8F) {
            return null;
        }
        float wa = ((b.v - c.v) * (u - c.u) + (c.u - b.u) * (v - c.v)) / denominator;
        float wb = ((c.v - a.v) * (u - c.u) + (a.u - c.u) * (v - c.v)) / denominator;
        float wc = 1.0F - wa - wb;
        if (wa < -0.001F || wb < -0.001F || wc < -0.001F) {
            return null;
        }
        return new Vec3(
                a.position.x * wa + b.position.x * wb + c.position.x * wc,
                a.position.y * wa + b.position.y * wb + c.position.y * wc,
                a.position.z * wa + b.position.z * wb + c.position.z * wc);
    }

    static boolean containsUv(float u, float v, Vertex[] quad) {
        return containsTriangleUv(u, v, quad[0], quad[1], quad[2])
                || containsTriangleUv(u, v, quad[0], quad[2], quad[3]);
    }

    private static boolean containsTriangleUv(float u, float v, Vertex a, Vertex b, Vertex c) {
        float denominator = (b.v - c.v) * (a.u - c.u) + (c.u - b.u) * (a.v - c.v);
        if (Math.abs(denominator) < 1.0E-8F) {
            return false;
        }
        float wa = ((b.v - c.v) * (u - c.u) + (c.u - b.u) * (v - c.v)) / denominator;
        float wb = ((c.v - a.v) * (u - c.u) + (a.u - c.u) * (v - c.v)) / denominator;
        float wc = 1.0F - wa - wb;
        return wa >= -0.001F && wb >= -0.001F && wc >= -0.001F;
    }

    static Vec3 bilerp(Vertex v0, Vertex v1, Vertex v2, Vertex v3, double tx, double ty) {
        Vec3 top = v0.position.lerp(v1.position, tx);
        Vec3 bottom = v3.position.lerp(v2.position, tx);
        return top.lerp(bottom, ty);
    }
}
