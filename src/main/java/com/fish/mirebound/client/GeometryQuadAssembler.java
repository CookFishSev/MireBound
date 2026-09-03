package com.fish.mirebound.client;

import net.minecraft.world.phys.Vec3;

/** Fixed-storage quad assembly for rendered player geometry capture. */
final class GeometryQuadAssembler {
    @FunctionalInterface
    interface QuadConsumer {
        void accept(Quad quad);
    }

    static final class Quad {
        private final float[] x = new float[4];
        private final float[] y = new float[4];
        private final float[] z = new float[4];
        private final float[] u = new float[4];
        private final float[] v = new float[4];

        void set(int index, float x, float y, float z, float u, float v) {
            this.x[index] = x;
            this.y[index] = y;
            this.z[index] = z;
            this.u[index] = u;
            this.v[index] = v;
        }

        float minU(float scale) {
            return Math.min(Math.min(u[0], u[1]), Math.min(u[2], u[3])) * scale;
        }

        float maxU(float scale) {
            return Math.max(Math.max(u[0], u[1]), Math.max(u[2], u[3])) * scale;
        }

        float minV(float scale) {
            return Math.min(Math.min(v[0], v[1]), Math.min(v[2], v[3])) * scale;
        }

        float maxV(float scale) {
            return Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3])) * scale;
        }

        Vec3 interpolate(float targetU, float targetV, Vec3 cameraPosition) {
            Vec3 point = interpolateTriangle(targetU, targetV, 0, 1, 2);
            if (point == null) {
                point = interpolateTriangle(targetU, targetV, 0, 2, 3);
            }
            return point == null ? null : point.add(cameraPosition);
        }

        private Vec3 interpolateTriangle(float targetU, float targetV,
                int first, int second, int third) {
            float denominator = (v[second] - v[third]) * (u[first] - u[third])
                    + (u[third] - u[second]) * (v[first] - v[third]);
            if (Math.abs(denominator) < 1.0E-8F) {
                return null;
            }
            float firstWeight = ((v[second] - v[third]) * (targetU - u[third])
                    + (u[third] - u[second]) * (targetV - v[third])) / denominator;
            float secondWeight = ((v[third] - v[first]) * (targetU - u[third])
                    + (u[first] - u[third]) * (targetV - v[third])) / denominator;
            float thirdWeight = 1.0F - firstWeight - secondWeight;
            if (firstWeight < -0.001F || secondWeight < -0.001F
                    || thirdWeight < -0.001F) {
                return null;
            }
            return new Vec3(
                    x[first] * firstWeight + x[second] * secondWeight
                            + x[third] * thirdWeight,
                    y[first] * firstWeight + y[second] * secondWeight
                            + y[third] * thirdWeight,
                    z[first] * firstWeight + z[second] * secondWeight
                            + z[third] * thirdWeight);
        }
    }

    private final Quad quad = new Quad();
    private final QuadConsumer consumer;
    private int size;
    private int completedQuads;
    private float currentX;
    private float currentY;
    private float currentZ;
    private float currentU;
    private float currentV;
    private boolean current;
    private boolean currentHasUv;

    GeometryQuadAssembler(QuadConsumer consumer) {
        this.consumer = consumer;
    }

    void append(float x, float y, float z, float u, float v) {
        commitCurrent();
        appendComplete(x, y, z, u, v);
    }

    void begin(float x, float y, float z) {
        commitCurrent();
        currentX = x;
        currentY = y;
        currentZ = z;
        current = true;
        currentHasUv = false;
    }

    void setUv(float u, float v) {
        if (!current) {
            return;
        }
        currentU = u;
        currentV = v;
        currentHasUv = true;
    }

    void commitCurrent() {
        if (!current) {
            return;
        }
        if (currentHasUv) {
            appendComplete(currentX, currentY, currentZ, currentU, currentV);
        }
        current = false;
        currentHasUv = false;
    }

    int completedQuads() {
        return completedQuads;
    }

    private void appendComplete(float x, float y, float z, float u, float v) {
        quad.set(size++, x, y, z, u, v);
        if (size == 4) {
            consumer.accept(quad);
            completedQuads++;
            size = 0;
        }
    }
}
