package com.fish.mirebound.client.entitycoverage;

import com.fish.mirebound.entitycoverage.EntityMudCoverageSpot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Projects each stain once, then recomposes stable sparse UV masks. */
final class EntityMudGeometryProjector {
    private static final float EPSILON = 1.0E-6F;
    private static final int PROJECTION_BATCH_SIZE = 8;

    private EntityMudGeometryProjector() {
    }

    static Map<Integer, SpotProjection> project(
            EntityModel<?> model, PoseStack poseStack,
            int width, int height,
            List<ClientEntityMudCoverage.SpotView> spots,
            int patternSeed, float collisionHeight) {
        if (width <= 0 || height <= 0 || spots.isEmpty()
                || (long) width * height > Character.MAX_VALUE + 1L) {
            return Map.of();
        }
        QuadCollector collector;
        try {
            collector = new QuadCollector(
                    new Matrix4f(poseStack.last().pose()).invert());
            model.renderToBuffer(
                    poseStack, collector, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, -1);
            collector.finish();
        } catch (RuntimeException exception) {
            return null;
        }
        return project(collector.quads, width, height, spots,
                patternSeed, collisionHeight);
    }

    private static Map<Integer, SpotProjection> project(
            List<Quad> quads, int width, int height,
            List<ClientEntityMudCoverage.SpotView> spots,
            int patternSeed, float collisionHeight) {
        if (quads.isEmpty()) {
            return null;
        }
        Bounds bounds = Bounds.of(quads);
        if (!bounds.valid()) {
            return null;
        }

        Map<Integer, SpotProjection> result = new LinkedHashMap<>(spots.size());
        for (int start = 0; start < spots.size();
                start += PROJECTION_BATCH_SIZE) {
            int count = Math.min(PROJECTION_BATCH_SIZE, spots.size() - start);
            ProjectedSpot[] projected = new ProjectedSpot[count];
            for (int index = 0; index < count; index++) {
                projected[index] = project(
                        spots.get(start + index), quads, bounds,
                        collisionHeight);
            }
            byte[][] edgeMasks = new byte[count][width * height];
            for (Quad quad : quads) {
                projectTriangle(edgeMasks, width, height,
                        quad.a, quad.b, quad.c,
                        projected, patternSeed, bounds);
                projectTriangle(edgeMasks, width, height,
                        quad.a, quad.c, quad.d,
                        projected, patternSeed, bounds);
            }
            for (int index = 0; index < count; index++) {
                result.put(spots.get(start + index).id(),
                        compress(edgeMasks[index]));
            }
        }
        return result;
    }

    static float spotAlphaFactor(float distance, float radius, float strength) {
        return spotEdgeFactor(distance, radius)
                * (0.18F + Mth.clamp(strength, 0.0F, 1.0F) * 0.82F);
    }

    private static float spotEdgeFactor(float distance, float radius) {
        if (radius <= EPSILON || distance >= radius) {
            return 0.0F;
        }
        float edge = Mth.clamp((radius - distance)
                / Math.max(EPSILON, radius * 0.30F), 0.0F, 1.0F);
        return edge * edge * (3.0F - 2.0F * edge);
    }

    private static void projectTriangle(
            byte[][] edgeMasks, int width, int height,
            Vertex a, Vertex b, Vertex c, ProjectedSpot[] spots,
            int seed, Bounds bounds) {
        float denominator = (b.v - c.v) * (a.u - c.u)
                + (c.u - b.u) * (a.v - c.v);
        if (!Float.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
            return;
        }
        int minX = Mth.clamp(Mth.floor(
                Math.min(a.u, Math.min(b.u, c.u)) * width), 0, width - 1);
        int maxX = Mth.clamp(Mth.ceil(
                Math.max(a.u, Math.max(b.u, c.u)) * width), 0, width);
        int minY = Mth.clamp(Mth.floor(
                Math.min(a.v, Math.min(b.v, c.v)) * height), 0, height - 1);
        int maxY = Mth.clamp(Mth.ceil(
                Math.max(a.v, Math.max(b.v, c.v)) * height), 0, height);
        for (int y = minY; y < maxY; y++) {
            float v = (y + 0.5F) / height;
            for (int x = minX; x < maxX; x++) {
                float u = (x + 0.5F) / width;
                float wa = ((b.v - c.v) * (u - c.u)
                        + (c.u - b.u) * (v - c.v)) / denominator;
                float wb = ((c.v - a.v) * (u - c.u)
                        + (a.u - c.u) * (v - c.v)) / denominator;
                float wc = 1.0F - wa - wb;
                if (wa < -0.0001F || wb < -0.0001F || wc < -0.0001F) {
                    continue;
                }
                float px = wa * a.x + wb * b.x + wc * c.x;
                float py = wa * a.y + wb * b.y + wc * c.y;
                float pz = wa * a.z + wb * b.z + wc * c.z;
                projectPixel(edgeMasks, y * width + x, x, y,
                        px, py, pz, spots, seed, bounds);
            }
        }
    }

    private static void projectPixel(
            byte[][] edgeMasks, int pixelIndex, int x, int y,
            float px, float py, float pz,
            ProjectedSpot[] spots, int seed, Bounds bounds) {
        for (int index = 0; index < spots.length; index++) {
            ProjectedSpot spot = spots[index];
            float edgeFactor;
            if (spot.source.shape() == EntityMudCoverageSpot.Shape.RADIAL) {
                float dx = px - spot.x;
                float dy = py - spot.y;
                float dz = pz - spot.z;
                float distance = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                int spotSalt = seed ^ spot.source.id() * 0x632BE5AB;
                float noise = smoothNoise(x, y, spotSalt);
                float radius = spot.radius * (0.94F + noise * 0.12F);
                edgeFactor = spotEdgeFactor(distance, radius);
            } else {
                float localY = Mth.clamp(
                        (bounds.maxY - py) / bounds.height(), 0.0F, 1.0F);
                edgeFactor = volumeEdgeFactor(
                        localY, spot.volumeBoundary, spot.source.shape());
                if (spot.source.shape().localized()) {
                    float dx = px - spot.x;
                    float dz = pz - spot.z;
                    float horizontalDistance = Mth.sqrt(dx * dx + dz * dz);
                    edgeFactor = localizedVolumeEdgeFactor(
                            edgeFactor, horizontalDistance, spot.radius);
                }
            }
            int edge = Mth.clamp(Math.round(edgeFactor * 255.0F), 0, 255);
            if (edge > Byte.toUnsignedInt(edgeMasks[index][pixelIndex])) {
                edgeMasks[index][pixelIndex] = (byte) edge;
            }
        }
    }

    static float volumeEdgeFactor(
            float localY, float boundary,
            EntityMudCoverageSpot.Shape shape) {
        float signedDepth = shape == EntityMudCoverageSpot.Shape.LOWER_VOLUME
                || shape == EntityMudCoverageSpot.Shape.LOWER_CONTACT_VOLUME
                ? boundary - localY : localY - boundary;
        float transition = Mth.clamp((signedDepth + 0.018F) / 0.018F,
                0.0F, 1.0F);
        return transition * transition * (3.0F - 2.0F * transition);
    }

    static float localizedVolumeEdgeFactor(
            float verticalFactor, float horizontalDistance, float radius) {
        return Mth.clamp(verticalFactor, 0.0F, 1.0F)
                * spotEdgeFactor(horizontalDistance, radius * 1.35F);
    }

    static float modelVolumeBoundary(
            float collisionBoundary, float collisionHeight, float modelHeight,
            EntityMudCoverageSpot.Shape shape) {
        float boundary = Mth.clamp(collisionBoundary, 0.0F, 1.0F);
        if (!Float.isFinite(collisionHeight) || collisionHeight <= EPSILON
                || !Float.isFinite(modelHeight) || modelHeight <= EPSILON) {
            return boundary;
        }
        float depth = shape == EntityMudCoverageSpot.Shape.UPPER_VOLUME
                ? (1.0F - boundary) * collisionHeight
                : boundary * collisionHeight;
        float modelShare = Mth.clamp(depth / modelHeight, 0.0F, 1.0F);
        return shape == EntityMudCoverageSpot.Shape.UPPER_VOLUME
                ? 1.0F - modelShare : modelShare;
    }

    private static SpotProjection compress(byte[] mask) {
        int size = 0;
        for (byte value : mask) {
            if (value != 0) {
                size++;
            }
        }
        char[] pixels = new char[size];
        byte[] edges = new byte[size];
        int target = 0;
        for (int pixel = 0; pixel < mask.length; pixel++) {
            if (mask[pixel] == 0) {
                continue;
            }
            pixels[target] = (char) pixel;
            edges[target] = mask[pixel];
            target++;
        }
        return new SpotProjection(pixels, edges);
    }

    static SpotProjection union(SpotProjection first, SpotProjection second) {
        char[] pixels = new char[first.pixels.length + second.pixels.length];
        byte[] edges = new byte[pixels.length];
        int firstIndex = 0;
        int secondIndex = 0;
        int target = 0;
        while (firstIndex < first.pixels.length
                || secondIndex < second.pixels.length) {
            if (secondIndex >= second.pixels.length
                    || firstIndex < first.pixels.length
                    && first.pixels[firstIndex] < second.pixels[secondIndex]) {
                pixels[target] = first.pixels[firstIndex];
                edges[target++] = first.edges[firstIndex++];
            } else if (firstIndex >= first.pixels.length
                    || second.pixels[secondIndex] < first.pixels[firstIndex]) {
                pixels[target] = second.pixels[secondIndex];
                edges[target++] = second.edges[secondIndex++];
            } else {
                pixels[target] = first.pixels[firstIndex];
                edges[target++] = (byte) Math.max(
                        Byte.toUnsignedInt(first.edges[firstIndex++]),
                        Byte.toUnsignedInt(second.edges[secondIndex++]));
            }
        }
        if (target == pixels.length) {
            return new SpotProjection(pixels, edges);
        }
        return new SpotProjection(
                java.util.Arrays.copyOf(pixels, target),
                java.util.Arrays.copyOf(edges, target));
    }

    private static ProjectedSpot project(
            ClientEntityMudCoverage.SpotView spot,
            List<Quad> quads, Bounds global, float collisionHeight) {
        float targetY = Mth.lerp(spot.localY(), global.maxY, global.minY);
        float radius = Math.max(0.12F, spot.radius() * global.height());
        Bounds slice = Bounds.slice(quads, targetY, radius * 1.35F);
        if (!slice.valid()) {
            slice = global;
        }
        float x = Mth.lerp((spot.localX() + 1.0F) * 0.5F,
                slice.maxX, slice.minX);
        float z = Mth.lerp((spot.localZ() + 1.0F) * 0.5F,
                slice.maxZ, slice.minZ);
        float volumeBoundary = modelVolumeBoundary(
                spot.localY(), collisionHeight, global.height(), spot.shape());
        return new ProjectedSpot(
                x, targetY, z, radius, volumeBoundary, spot);
    }

    private static float hash01(int x, int y, int seed) {
        int hash = seed ^ x * 0x632BE5AB;
        hash = Integer.rotateLeft(hash, 13) ^ y * 0x85157AF5;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        return (hash & 0xFFFF) / 65535.0F;
    }

    private static float smoothNoise(int x, int y, int seed) {
        int gridX = Math.floorDiv(x, 2);
        int gridY = Math.floorDiv(y, 2);
        float localX = Math.floorMod(x, 2) * 0.5F;
        float localY = Math.floorMod(y, 2) * 0.5F;
        float smoothX = localX * localX * (3.0F - 2.0F * localX);
        float smoothY = localY * localY * (3.0F - 2.0F * localY);
        float top = Mth.lerp(smoothX,
                hash01(gridX, gridY, seed),
                hash01(gridX + 1, gridY, seed));
        float bottom = Mth.lerp(smoothX,
                hash01(gridX, gridY + 1, seed),
                hash01(gridX + 1, gridY + 1, seed));
        return Mth.lerp(smoothY, top, bottom);
    }

    record SpotProjection(char[] pixels, byte[] edges) {
    }

    private static final class QuadCollector implements VertexConsumer {
        private final Matrix4f inverseRoot;
        private final List<Quad> quads = new ArrayList<>();
        private final Vertex[] pendingQuad = new Vertex[4];
        private Vector3f position;
        private float u;
        private float v;
        private boolean hasUv;
        private int pendingCount;

        private QuadCollector(Matrix4f inverseRoot) {
            this.inverseRoot = inverseRoot;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            commitVertex();
            position = inverseRoot.transformPosition(x, y, z, new Vector3f());
            hasUv = false;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            hasUv = Float.isFinite(u) && Float.isFinite(v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            commitVertex();
            return this;
        }

        private void commitVertex() {
            if (position == null) {
                return;
            }
            if (hasUv) {
                pendingQuad[pendingCount++] = new Vertex(
                        position.x(), position.y(), position.z(), u, v);
                if (pendingCount == 4) {
                    quads.add(new Quad(
                            pendingQuad[0], pendingQuad[1],
                            pendingQuad[2], pendingQuad[3]));
                    pendingCount = 0;
                }
            } else {
                pendingCount = 0;
            }
            position = null;
        }

        private void finish() {
            commitVertex();
            pendingCount = 0;
        }
    }

    private record Vertex(float x, float y, float z, float u, float v) {
    }

    private record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {
    }

    private record ProjectedSpot(
            float x, float y, float z, float radius, float volumeBoundary,
            ClientEntityMudCoverage.SpotView source) {
    }

    private record Bounds(
            float minX, float maxX, float minY, float maxY,
            float minZ, float maxZ) {
        private static Bounds of(List<Quad> quads) {
            MutableBounds bounds = new MutableBounds();
            for (Quad quad : quads) {
                bounds.include(quad.a);
                bounds.include(quad.b);
                bounds.include(quad.c);
                bounds.include(quad.d);
            }
            return bounds.freeze();
        }

        private static Bounds slice(
                List<Quad> quads, float targetY, float reach) {
            MutableBounds bounds = new MutableBounds();
            for (Quad quad : quads) {
                float minY = Math.min(Math.min(quad.a.y, quad.b.y),
                        Math.min(quad.c.y, quad.d.y));
                float maxY = Math.max(Math.max(quad.a.y, quad.b.y),
                        Math.max(quad.c.y, quad.d.y));
                if (maxY < targetY - reach || minY > targetY + reach) {
                    continue;
                }
                bounds.include(quad.a);
                bounds.include(quad.b);
                bounds.include(quad.c);
                bounds.include(quad.d);
            }
            return bounds.freeze();
        }

        private boolean valid() {
            return Float.isFinite(minX) && maxX - minX > EPSILON
                    && maxY - minY > EPSILON && maxZ - minZ > EPSILON;
        }

        private float height() {
            return maxY - minY;
        }
    }

    private static final class MutableBounds {
        private float minX = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        private void include(Vertex vertex) {
            minX = Math.min(minX, vertex.x);
            maxX = Math.max(maxX, vertex.x);
            minY = Math.min(minY, vertex.y);
            maxY = Math.max(maxY, vertex.y);
            minZ = Math.min(minZ, vertex.z);
            maxZ = Math.max(maxZ, vertex.z);
        }

        private Bounds freeze() {
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
        }
    }
}
