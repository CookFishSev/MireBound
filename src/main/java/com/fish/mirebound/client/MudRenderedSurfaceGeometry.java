package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Cached final-model triangles for optional model-accurate surface effects. */
final class MudRenderedSurfaceGeometry {
    private static final int RESOLUTION = 16;
    private static final int MAXIMUM_CACHED_BLOCKS = 1024;
    private static final int MAXIMUM_CACHED_SPRITES = 2048;
    private static final int MAXIMUM_CAPTURED_QUADS = 8192;
    private static final long CACHE_LIFETIME_TICKS = 20L;
    private static final double TRIANGLE_EPSILON = 1.0E-6D;
    private static final double PLANE_EPSILON = 1.0E-4D;
    private static final double PATCH_AREA_EPSILON = 1.0E-9D;
    private static final Map<PositionKey, CachedGeometry> CACHE =
            new LinkedHashMap<>(128, 0.75F, true);
    private static final Map<TextureAtlasSprite, TextureAlphaMask> ALPHA_MASKS =
            new IdentityHashMap<>();

    private MudRenderedSurfaceGeometry() {
    }

    static SurfaceHit surfaceHit(Level level, BlockPos pos, BlockState state,
            Direction face, double localX, double localY, double localZ) {
        Geometry geometry = resolve(level, pos, state);
        return geometry == null
                ? null : geometry.surfaceHit(face, localX, localY, localZ);
    }

    static RenderedSurface renderedSurface(
            Level level, BlockPos pos, BlockState state, Direction face) {
        Geometry geometry = resolve(level, pos, state);
        return geometry == null || !geometry.available()
                ? null : geometry.renderedSurface(face);
    }

    static SurfacePatch surfacePatch(Level level, BlockPos pos, BlockState state,
            Direction face, int cellU, int cellV) {
        Geometry geometry = resolve(level, pos, state);
        return geometry == null || !geometry.available()
                ? null : geometry.surfacePatch(face, cellU, cellV);
    }

    static double topSurfaceAt(Level level, BlockPos pos, BlockState state,
            double localX, double localZ) {
        SurfaceHit hit = topSurfaceHit(level, pos, state, localX, localZ);
        return hit == null ? Double.NaN : hit.coordinate();
    }

    static SurfaceHit topSurfaceHit(Level level, BlockPos pos, BlockState state,
            double localX, double localZ) {
        Geometry geometry = resolve(level, pos, state);
        return geometry == null
                ? null : geometry.topSurfaceAt(localX, localZ);
    }

    static List<RenderedFace> axisFaces(
            Level level, BlockPos pos, BlockState state) {
        Geometry geometry = resolve(level, pos, state);
        return geometry == null ? List.of() : geometry.axisFaces();
    }

    static SurfaceHit intersectTriangle(Direction face,
            Vec3 first, Vec3 second, Vec3 third,
            double localX, double localY, double localZ) {
        Vec3 normal = second.subtract(first).cross(third.subtract(first));
        if (normal.lengthSqr() <= TRIANGLE_EPSILON * TRIANGLE_EPSILON) {
            return null;
        }
        normal = normal.normalize();
        Vec3 expected = new Vec3(
                face.getStepX(), face.getStepY(), face.getStepZ());
        if (normal.dot(expected) < 0.0D) {
            normal = normal.scale(-1.0D);
        }
        return new Triangle(first, second, third, normal, face)
                .surfaceHit(face, localX, localY, localZ);
    }

    static SurfaceHit intersectTexturedTriangle(Direction face,
            Vec3 first, float firstU, float firstV,
            Vec3 second, float secondU, float secondV,
            Vec3 third, float thirdU, float thirdV,
            TextureAlphaMask alphaMask,
            double localX, double localY, double localZ) {
        Vec3 normal = second.subtract(first).cross(third.subtract(first));
        if (normal.lengthSqr() <= TRIANGLE_EPSILON * TRIANGLE_EPSILON) {
            return null;
        }
        normal = normal.normalize();
        Vec3 expected = new Vec3(
                face.getStepX(), face.getStepY(), face.getStepZ());
        if (normal.dot(expected) < 0.0D) {
            normal = normal.scale(-1.0D);
        }
        return new Triangle(first, second, third, normal, face,
                new Uv(firstU, firstV), new Uv(secondU, secondV),
                new Uv(thirdU, thirdV), alphaMask)
                .surfaceHit(face, localX, localY, localZ);
    }

    static TextureAlphaMask testAlphaMask(
            int width, int height, boolean[] visible) {
        return TextureAlphaMask.create(width, height, visible);
    }

    static SurfacePatch clipTriangleForTest(Direction face,
            Vec3 first, Vec3 second, Vec3 third, int cellU, int cellV) {
        Vec3 normal = second.subtract(first).cross(third.subtract(first));
        if (normal.lengthSqr() <= TRIANGLE_EPSILON * TRIANGLE_EPSILON) {
            return null;
        }
        normal = normal.normalize();
        Vec3 expected = new Vec3(
                face.getStepX(), face.getStepY(), face.getStepZ());
        if (normal.dot(expected) < 0.0D) {
            normal = normal.scale(-1.0D);
        }
        Triangle triangle = new Triangle(first, second, third, normal, face);
        return buildSurfacePatch(
                face, cellU, cellV, triangle, List.of(triangle), 1);
    }

    static void invalidate(Set<BlockPos> positions) {
        if (positions == null || positions.isEmpty() || CACHE.isEmpty()) {
            return;
        }
        LongOpenHashSet packed = new LongOpenHashSet(positions.size());
        for (BlockPos pos : positions) {
            packed.add(pos.asLong());
        }
        CACHE.keySet().removeIf(key -> packed.contains(key.blockPos()));
    }

    static void reset() {
        CACHE.clear();
        ALPHA_MASKS.clear();
    }

    private static Geometry resolve(Level level, BlockPos pos, BlockState state) {
        if (!MudSurfaceClientSettings.preciseModelGeometry()
                || level == null || pos == null || state == null
                || state.isAir()) {
            return null;
        }
        BlockState source = state.getBlock() instanceof AdaptiveMudBlock
                ? AdaptiveMudClientCache.sourceState(level, pos)
                : state;
        if (source == null) {
            return null;
        }
        PositionKey key = new PositionKey(level.dimension().location(), pos.asLong());
        long gameTime = level.getGameTime();
        CachedGeometry cached = CACHE.get(key);
        if (cached != null && cached.proxyState().equals(state)
                && cached.sourceState().equals(source)
                && gameTime >= cached.builtAt()
                && (!cached.dynamicModel()
                        || gameTime - cached.builtAt() < CACHE_LIFETIME_TICKS)) {
            return cached.geometry();
        }
        CapturedGeometry captured = capture(level, pos, state);
        Geometry geometry = captured.geometry();
        CACHE.put(key, new CachedGeometry(
                state, source, gameTime, geometry, captured.dynamicModel()));
        while (CACHE.size() > MAXIMUM_CACHED_BLOCKS) {
            CACHE.remove(CACHE.keySet().iterator().next());
        }
        return geometry;
    }

    private static CapturedGeometry capture(
            Level level, BlockPos pos, BlockState state) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            ModelData baseData = blockEntity == null
                    ? ModelData.EMPTY : blockEntity.getModelData();
            ModelData modelData = model.getModelData(level, pos, state, baseData);
            List<BakedQuad> quads = collectQuads(model, state, modelData, pos);
            boolean dynamicModel = state.getBlock() instanceof AdaptiveMudBlock
                    || blockEntity != null || modelData != ModelData.EMPTY;
            return new CapturedGeometry(Geometry.build(quads), dynamicModel);
        } catch (RuntimeException ignored) {
            // Third-party dynamic models remain isolated behind the voxel fallback.
            return new CapturedGeometry(Geometry.UNAVAILABLE, true);
        }
    }

    private static List<BakedQuad> collectQuads(BakedModel model, BlockState state,
            ModelData modelData, BlockPos pos) {
        List<BakedQuad> result = new ArrayList<>();
        long seed = state.getSeed(pos);
        RandomSource random = RandomSource.create(seed);
        boolean hadRenderType = false;
        for (RenderType renderType : model.getRenderTypes(state, random, modelData)) {
            hadRenderType = true;
            collectQuads(model, state, modelData, renderType, seed, random, result);
            if (result.size() >= MAXIMUM_CAPTURED_QUADS) {
                break;
            }
        }
        if (!hadRenderType && result.size() < MAXIMUM_CAPTURED_QUADS) {
            collectQuads(model, state, modelData, null, seed, random, result);
        }
        return result.size() <= MAXIMUM_CAPTURED_QUADS
                ? List.copyOf(result)
                : List.copyOf(result.subList(0, MAXIMUM_CAPTURED_QUADS));
    }

    private static void collectQuads(BakedModel model, BlockState state,
            ModelData modelData, RenderType renderType, long seed,
            RandomSource random, List<BakedQuad> result) {
        random.setSeed(seed);
        addBounded(result, model.getQuads(
                state, null, random, modelData, renderType));
        for (Direction side : Direction.values()) {
            if (result.size() >= MAXIMUM_CAPTURED_QUADS) {
                return;
            }
            random.setSeed(seed);
            addBounded(result, model.getQuads(
                    state, side, random, modelData, renderType));
        }
    }

    private static void addBounded(List<BakedQuad> target, List<BakedQuad> source) {
        int remaining = MAXIMUM_CAPTURED_QUADS - target.size();
        if (remaining <= 0 || source.isEmpty()) {
            return;
        }
        target.addAll(source.size() <= remaining
                ? source : source.subList(0, remaining));
    }

    record SurfaceHit(double coordinate, Vec3 normal, Vec3 axisX, Vec3 axisZ) {
        private static SurfaceHit create(
                double coordinate, Vec3 normal, Direction face) {
            if (face.getAxis() == Direction.Axis.Y
                    && Math.abs(normal.y) > TRIANGLE_EPSILON) {
                return new SurfaceHit(
                        coordinate,
                        normal,
                        new Vec3(1.0D, -normal.x / normal.y, 0.0D),
                        new Vec3(0.0D, -normal.z / normal.y, 1.0D));
            }
            Vec3 preferred = face.getAxis() == Direction.Axis.X
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D);
            Vec3 axisX = preferred.subtract(normal.scale(preferred.dot(normal)));
            if (axisX.lengthSqr() <= TRIANGLE_EPSILON * TRIANGLE_EPSILON) {
                axisX = new Vec3(0.0D, 0.0D, 1.0D)
                        .subtract(normal.scale(normal.z));
            }
            axisX = axisX.normalize();
            return new SurfaceHit(
                    coordinate, normal, axisX, normal.cross(axisX).normalize());
        }
    }

    record RenderedFace(Direction face, double plane,
            boolean[] cells, int geometryKey) {
        boolean any() {
            for (boolean cell : cells) {
                if (cell) {
                    return true;
                }
            }
            return false;
        }
    }

    record RenderedQuad(Direction face, Vec3 first, Vec3 second,
            Vec3 third, Vec3 fourth, Vec3 normal) {
    }

    record PatchVertex(double u, double v) {
    }

    record PatchPolygon(List<PatchVertex> vertices) {
    }

    static List<PatchPolygon> triangleQuads(
            PatchVertex first, PatchVertex second, PatchVertex third) {
        PatchVertex firstSecond = midpoint(first, second);
        PatchVertex secondThird = midpoint(second, third);
        PatchVertex thirdFirst = midpoint(third, first);
        PatchVertex center = new PatchVertex(
                (first.u() + second.u() + third.u()) / 3.0D,
                (first.v() + second.v() + third.v()) / 3.0D);
        return List.of(
                new PatchPolygon(List.of(
                        first, firstSecond, center, thirdFirst)),
                new PatchPolygon(List.of(
                        second, secondThird, center, firstSecond)),
                new PatchPolygon(List.of(
                        third, thirdFirst, center, secondThird)));
    }

    private static PatchVertex midpoint(PatchVertex first, PatchVertex second) {
        return new PatchVertex(
                (first.u() + second.u()) * 0.5D,
                (first.v() + second.v()) * 0.5D);
    }

    record PatchEdge(PatchVertex first, PatchVertex second) {
    }

    record SurfacePatch(
            List<PatchPolygon> polygons, List<PatchEdge> boundaryEdges,
            boolean full, double coverage, int geometryKey) {
        private static SurfacePatch full(int geometryKey) {
            return new SurfacePatch(List.of(), List.of(), true, 1.0D, geometryKey);
        }
    }

    static final class RenderedSurface {
        private final Geometry geometry;
        private final Direction face;
        private final List<RenderedQuad> quads;
        private final int geometryKey;

        private RenderedSurface(Geometry geometry, Direction face,
                List<RenderedQuad> quads, int geometryKey) {
            this.geometry = geometry;
            this.face = face;
            this.quads = quads;
            this.geometryKey = geometryKey;
        }

        List<RenderedQuad> quads() {
            return quads;
        }

        int geometryKey() {
            return geometryKey;
        }

        SurfaceHit surfaceHit(double localX, double localY, double localZ) {
            return geometry.surfaceHit(face, localX, localY, localZ);
        }
    }

    private record PositionKey(ResourceLocation dimension, long blockPos) {
    }

    private record CachedGeometry(BlockState proxyState, BlockState sourceState,
            long builtAt, Geometry geometry, boolean dynamicModel) {
    }

    private record CapturedGeometry(Geometry geometry, boolean dynamicModel) {
    }

    private record FacePlane(Direction face, long plane) {
    }

    private record Uv(float u, float v) {
        private static final Uv ZERO = new Uv(0.0F, 0.0F);
    }

    private record VertexData(Vec3 position, Uv uv) {
    }

    private record QuadData(VertexData[] vertices, TextureAlphaMask alphaMask) {
    }

    private record Triangle(Vec3 first, Vec3 second, Vec3 third,
            Vec3 normal, Direction face,
            Uv firstUv, Uv secondUv, Uv thirdUv,
            TextureAlphaMask alphaMask) {
        private Triangle(Vec3 first, Vec3 second, Vec3 third,
                Vec3 normal, Direction face) {
            this(first, second, third, normal, face,
                    Uv.ZERO, Uv.ZERO, Uv.ZERO, TextureAlphaMask.OPAQUE);
        }

        private SurfaceHit surfaceHit(Direction requested,
                double localX, double localY, double localZ) {
            if (face != requested) {
                return null;
            }
            Direction.Axis axis = requested.getAxis();
            double firstU = firstCoordinate(first, axis);
            double firstV = secondCoordinate(first, axis);
            double secondU = firstCoordinate(second, axis);
            double secondV = secondCoordinate(second, axis);
            double thirdU = firstCoordinate(third, axis);
            double thirdV = secondCoordinate(third, axis);
            double queryU = firstCoordinate(localX, localY, localZ, axis);
            double queryV = secondCoordinate(localX, localY, localZ, axis);
            double denominator = (secondV - thirdV) * (firstU - thirdU)
                    + (thirdU - secondU) * (firstV - thirdV);
            if (Math.abs(denominator) <= TRIANGLE_EPSILON) {
                return null;
            }
            double firstWeight = ((secondV - thirdV) * (queryU - thirdU)
                    + (thirdU - secondU) * (queryV - thirdV)) / denominator;
            double secondWeight = ((thirdV - firstV) * (queryU - thirdU)
                    + (firstU - thirdU) * (queryV - thirdV)) / denominator;
            double thirdWeight = 1.0D - firstWeight - secondWeight;
            if (firstWeight < -TRIANGLE_EPSILON
                    || secondWeight < -TRIANGLE_EPSILON
                    || thirdWeight < -TRIANGLE_EPSILON) {
                return null;
            }
            double textureU = firstWeight * firstUv.u()
                    + secondWeight * secondUv.u()
                    + thirdWeight * thirdUv.u();
            double textureV = firstWeight * firstUv.v()
                    + secondWeight * secondUv.v()
                    + thirdWeight * thirdUv.v();
            if (!alphaMask.visible(textureU, textureV)) {
                return null;
            }
            double coordinate = firstWeight * axisCoordinate(first, axis)
                    + secondWeight * axisCoordinate(second, axis)
                    + thirdWeight * axisCoordinate(third, axis);
            return SurfaceHit.create(coordinate, normal, requested);
        }

        private long visualKey() {
            long hash = pointKey(first);
            hash = mix(hash ^ Long.rotateLeft(pointKey(second), 17));
            hash = mix(hash ^ Long.rotateLeft(pointKey(third), 31));
            hash = mix(hash ^ uvKey(firstUv));
            hash = mix(hash ^ Long.rotateLeft(uvKey(secondUv), 13));
            hash = mix(hash ^ Long.rotateLeft(uvKey(thirdUv), 29));
            return mix(hash ^ alphaMask.signature());
        }
    }

    private record Geometry(List<Triangle> triangles,
            List<RenderedFace> axisFaces,
            Map<Direction, List<RenderedQuad>> renderedQuads,
            Map<Direction, Integer> renderedGeometryKeys,
            SurfaceHit[] faceCellHits, boolean[] faceCellResolved,
            SurfacePatch[] faceCellPatches, boolean[] facePatchResolved,
            boolean available) {
        private static final Geometry EMPTY = new Geometry(
                List.of(), List.of(), Map.of(), Map.of(),
                new SurfaceHit[0], new boolean[0],
                new SurfacePatch[0], new boolean[0], true);
        private static final Geometry UNAVAILABLE = new Geometry(
                List.of(), List.of(), Map.of(), Map.of(),
                new SurfaceHit[0], new boolean[0],
                new SurfacePatch[0], new boolean[0], false);

        private static Geometry build(List<BakedQuad> quads) {
            if (quads.isEmpty()) {
                return EMPTY;
            }
            List<Triangle> triangles = new ArrayList<>(quads.size() * 2);
            Map<FacePlane, List<Triangle>> axisGroups = new LinkedHashMap<>();
            Map<Direction, List<RenderedQuad>> renderedQuads =
                    new EnumMap<>(Direction.class);
            Map<Direction, Long> renderedHashes = new EnumMap<>(Direction.class);
            for (BakedQuad quad : quads) {
                QuadData data = quadData(quad);
                if (data == null) {
                    continue;
                }
                VertexData[] vertexData = data.vertices();
                Vec3[] vertices = new Vec3[] {
                        vertexData[0].position(), vertexData[1].position(),
                        vertexData[2].position(), vertexData[3].position()};
                Vec3 normal = vertices[1].subtract(vertices[0])
                        .cross(vertices[2].subtract(vertices[0]));
                if (normal.lengthSqr() <= TRIANGLE_EPSILON * TRIANGLE_EPSILON) {
                    continue;
                }
                normal = normal.normalize();
                Vec3 expected = new Vec3(
                        quad.getDirection().getStepX(),
                        quad.getDirection().getStepY(),
                        quad.getDirection().getStepZ());
                if (normal.dot(expected) < 0.0D) {
                    normal = normal.scale(-1.0D);
                }
                Triangle first = new Triangle(
                        vertices[0], vertices[1], vertices[2], normal,
                        quad.getDirection(), vertexData[0].uv(),
                        vertexData[1].uv(), vertexData[2].uv(), data.alphaMask());
                Triangle second = new Triangle(
                        vertices[0], vertices[2], vertices[3], normal,
                        quad.getDirection(), vertexData[0].uv(),
                        vertexData[2].uv(), vertexData[3].uv(), data.alphaMask());
                triangles.add(first);
                triangles.add(second);
                RenderedQuad renderedQuad = new RenderedQuad(
                        quad.getDirection(), vertices[0], vertices[1],
                        vertices[2], vertices[3], normal);
                renderedQuads.computeIfAbsent(
                        quad.getDirection(), ignored -> new ArrayList<>())
                        .add(renderedQuad);
                long quadHash = mix(first.visualKey()
                        ^ Long.rotateLeft(second.visualKey(), 23));
                renderedHashes.merge(quad.getDirection(), quadHash,
                        (current, next) -> mix(current ^ next));

                Direction.Axis axis = quad.getDirection().getAxis();
                double plane = axisCoordinate(vertices[0], axis);
                if (samePlane(vertices, axis, plane)) {
                    FacePlane key = new FacePlane(
                            quad.getDirection(), Math.round(plane * 4096.0D));
                    List<Triangle> group = axisGroups.computeIfAbsent(
                            key, ignored -> new ArrayList<>());
                    group.add(first);
                    group.add(second);
                }
            }
            if (triangles.isEmpty()) {
                return EMPTY;
            }
            List<RenderedFace> faces = new ArrayList<>(axisGroups.size());
            for (Map.Entry<FacePlane, List<Triangle>> entry : axisGroups.entrySet()) {
                Direction face = entry.getKey().face();
                double plane = entry.getKey().plane() / 4096.0D;
                boolean[] cells = rasterize(face, plane, entry.getValue());
                int geometryKey = geometryKey(face, plane, entry.getValue());
                RenderedFace rendered = new RenderedFace(
                        face, plane, cells, geometryKey);
                if (rendered.any()) {
                    faces.add(rendered);
                }
            }
            Map<Direction, List<RenderedQuad>> immutableQuads =
                    new EnumMap<>(Direction.class);
            renderedQuads.forEach((face, faceQuads) ->
                    immutableQuads.put(face, List.copyOf(faceQuads)));
            Map<Direction, Integer> geometryKeys = new EnumMap<>(Direction.class);
            renderedHashes.forEach((face, hash) ->
                    geometryKeys.put(face, (int) (hash ^ hash >>> 32)));
            return new Geometry(
                    List.copyOf(triangles), List.copyOf(faces),
                    Map.copyOf(immutableQuads), Map.copyOf(geometryKeys),
                    new SurfaceHit[Direction.values().length * RESOLUTION * RESOLUTION],
                    new boolean[Direction.values().length * RESOLUTION * RESOLUTION],
                    new SurfacePatch[Direction.values().length * RESOLUTION * RESOLUTION],
                    new boolean[Direction.values().length * RESOLUTION * RESOLUTION], true);
        }

        private SurfaceHit topSurfaceAt(double localX, double localZ) {
            return surfaceHit(Direction.UP, localX, 0.0D, localZ);
        }

        private RenderedSurface renderedSurface(Direction face) {
            List<RenderedQuad> faceQuads = renderedQuads.getOrDefault(
                    face, List.of());
            return faceQuads.isEmpty() ? null : new RenderedSurface(
                    this, face, faceQuads,
                    renderedGeometryKeys.getOrDefault(face, 0));
        }

        private SurfacePatch surfacePatch(Direction face, int cellU, int cellV) {
            if (cellU < 0 || cellU >= RESOLUTION
                    || cellV < 0 || cellV >= RESOLUTION
                    || facePatchResolved.length == 0) {
                return null;
            }
            int index = face.get3DDataValue() * RESOLUTION * RESOLUTION
                    + (cellU | cellV << 4);
            if (!facePatchResolved[index]) {
                Vec3 center = MudSurfaceShapeGeometry.cellCenter(
                        face, 0.0D, cellU, cellV);
                Triangle selected = bestTriangle(
                        face, center.x, center.y, center.z);
                int geometryKey = renderedGeometryKeys.getOrDefault(face, 0);
                faceCellPatches[index] = selected == null ? null
                        : buildSurfacePatch(
                                face, cellU, cellV, selected,
                                triangles, geometryKey);
                facePatchResolved[index] = true;
            }
            return faceCellPatches[index];
        }

        private SurfaceHit surfaceHit(Direction face,
                double localX, double localY, double localZ) {
            int cachedIndex = faceCellIndex(
                    face, localX, localY, localZ);
            if (cachedIndex >= 0) {
                if (!faceCellResolved[cachedIndex]) {
                    faceCellHits[cachedIndex] = surfaceHitUncached(
                            face, localX, localY, localZ);
                    faceCellResolved[cachedIndex] = true;
                }
                return faceCellHits[cachedIndex];
            }
            return surfaceHitUncached(face, localX, localY, localZ);
        }

        private SurfaceHit surfaceHitUncached(Direction face,
                double localX, double localY, double localZ) {
            Triangle triangle = bestTriangle(
                    face, localX, localY, localZ);
            return triangle == null ? null : triangle.surfaceHit(
                    face, localX, localY, localZ);
        }

        private Triangle bestTriangle(Direction face,
                double localX, double localY, double localZ) {
            Triangle bestTriangle = null;
            SurfaceHit best = null;
            boolean positive = face.getAxisDirection()
                    == Direction.AxisDirection.POSITIVE;
            for (Triangle triangle : triangles) {
                SurfaceHit candidate = triangle.surfaceHit(
                        face, localX, localY, localZ);
                if (candidate == null || best != null
                        && (positive
                                ? candidate.coordinate() <= best.coordinate()
                                : candidate.coordinate() >= best.coordinate())) {
                    continue;
                }
                best = candidate;
                bestTriangle = triangle;
            }
            return bestTriangle;
        }

        private int faceCellIndex(Direction face,
                double localX, double localY, double localZ) {
            if (faceCellResolved.length == 0) {
                return -1;
            }
            double first = firstCoordinate(localX, localY, localZ, face.getAxis());
            double second = secondCoordinate(localX, localY, localZ, face.getAxis());
            int u = (int) Math.floor(first * RESOLUTION);
            int v = (int) Math.floor(second * RESOLUTION);
            if (u < 0 || u >= RESOLUTION || v < 0 || v >= RESOLUTION) {
                return -1;
            }
            double centerU = (u + 0.5D) / RESOLUTION;
            double centerV = (v + 0.5D) / RESOLUTION;
            if (Math.abs(first - centerU) > TRIANGLE_EPSILON
                    || Math.abs(second - centerV) > TRIANGLE_EPSILON) {
                return -1;
            }
            return face.get3DDataValue() * RESOLUTION * RESOLUTION
                    + (u | v << 4);
        }
    }

    private static QuadData quadData(BakedQuad quad) {
        int[] packed = quad.getVertices();
        int stride = packed.length / 4;
        if (stride < 3) {
            return null;
        }
        TextureAlphaMask alphaMask = stride >= 6
                ? alphaMask(quad.getSprite()) : TextureAlphaMask.OPAQUE;
        VertexData[] result = new VertexData[4];
        for (int index = 0; index < 4; index++) {
            int offset = index * stride;
            Vec3 position = new Vec3(
                    Float.intBitsToFloat(packed[offset]),
                    Float.intBitsToFloat(packed[offset + 1]),
                    Float.intBitsToFloat(packed[offset + 2]));
            Uv uv = stride >= 6
                    ? new Uv(
                            quad.getSprite().getUOffset(
                                    Float.intBitsToFloat(packed[offset + 4])),
                            quad.getSprite().getVOffset(
                                    Float.intBitsToFloat(packed[offset + 5])))
                    : Uv.ZERO;
            result[index] = new VertexData(position, uv);
        }
        return new QuadData(result, alphaMask);
    }

    private static TextureAlphaMask alphaMask(TextureAtlasSprite sprite) {
        TextureAlphaMask cached = ALPHA_MASKS.get(sprite);
        if (cached != null) {
            return cached;
        }
        TextureAlphaMask built = TextureAlphaMask.capture(sprite.contents());
        if (ALPHA_MASKS.size() >= MAXIMUM_CACHED_SPRITES) {
            ALPHA_MASKS.remove(ALPHA_MASKS.keySet().iterator().next());
        }
        ALPHA_MASKS.put(sprite, built);
        return built;
    }

    private static boolean samePlane(
            Vec3[] vertices, Direction.Axis axis, double plane) {
        for (int index = 1; index < vertices.length; index++) {
            if (Math.abs(axisCoordinate(vertices[index], axis) - plane)
                    > PLANE_EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static boolean[] rasterize(Direction face, double plane,
            List<Triangle> triangles) {
        boolean[] cells = new boolean[RESOLUTION * RESOLUTION];
        for (int v = 0; v < RESOLUTION; v++) {
            for (int u = 0; u < RESOLUTION; u++) {
                Vec3 point = MudSurfaceShapeGeometry.cellCenter(face, plane, u, v);
                for (Triangle triangle : triangles) {
                    if (triangle.surfaceHit(
                            face, point.x, point.y, point.z) != null) {
                        cells[u | v << 4] = true;
                        break;
                    }
                }
            }
        }
        return cells;
    }

    private static int geometryKey(Direction face, double plane,
            List<Triangle> triangles) {
        long hash = face.ordinal() * 0x9e3779b97f4a7c15L
                ^ Double.doubleToLongBits(plane);
        for (Triangle triangle : triangles) {
            hash = mix(hash ^ triangle.visualKey());
        }
        return (int) (hash ^ hash >>> 32);
    }

    private static SurfacePatch buildSurfacePatch(Direction face,
            int cellU, int cellV, Triangle selected,
            List<Triangle> triangles, int geometryKey) {
        double minimumU = cellU / (double) RESOLUTION;
        double maximumU = (cellU + 1.0D) / RESOLUTION;
        double minimumV = cellV / (double) RESOLUTION;
        double maximumV = (cellV + 1.0D) / RESOLUTION;
        double centerU = (minimumU + maximumU) * 0.5D;
        double centerV = (minimumV + maximumV) * 0.5D;
        List<PatchPolygon> patches = new ArrayList<>(2);
        double coveredArea = 0.0D;
        for (Triangle triangle : triangles) {
            if (!sameSurface(selected, triangle)) {
                continue;
            }
            List<PatchVertex> polygon = new ArrayList<>(List.of(
                    faceCoordinates(face, triangle.first()),
                    faceCoordinates(face, triangle.second()),
                    faceCoordinates(face, triangle.third())));
            polygon = clipPolygon(
                    polygon, minimumU, minimumV, maximumU, maximumV);
            double signedArea = signedArea(polygon);
            if (Math.abs(signedArea) <= PATCH_AREA_EPSILON) {
                continue;
            }
            if (signedArea < 0.0D) {
                java.util.Collections.reverse(polygon);
                signedArea = -signedArea;
            }
            coveredArea += signedArea;
            List<PatchVertex> normalized = new ArrayList<>(polygon.size());
            for (PatchVertex point : polygon) {
                normalized.add(new PatchVertex(
                        (point.u() - centerU) * RESOLUTION,
                        (point.v() - centerV) * RESOLUTION));
            }
            patches.add(new PatchPolygon(List.copyOf(normalized)));
        }
        double cellArea = 1.0D / (RESOLUTION * (double) RESOLUTION);
        int patchKey = geometryKey * 31 + (cellU | cellV << 4);
        if (coveredArea >= cellArea * 0.998D) {
            return SurfacePatch.full(patchKey);
        }
        return patches.isEmpty()
                ? null : new SurfacePatch(
                        List.copyOf(patches), boundaryEdges(patches),
                        false, Mth.clamp(coveredArea / cellArea, 0.0D, 1.0D),
                        patchKey);
    }

    private static List<PatchEdge> boundaryEdges(List<PatchPolygon> polygons) {
        Map<PatchEdgeKey, PatchEdgeCount> edges = new LinkedHashMap<>();
        for (PatchPolygon polygon : polygons) {
            List<PatchVertex> vertices = polygon.vertices();
            PatchVertex previous = vertices.getLast();
            for (PatchVertex current : vertices) {
                PatchEdge edge = new PatchEdge(previous, current);
                PatchEdgeKey key = PatchEdgeKey.of(previous, current);
                PatchEdgeCount count = edges.get(key);
                if (count == null) {
                    edges.put(key, new PatchEdgeCount(edge));
                } else {
                    count.count++;
                }
                previous = current;
            }
        }
        List<PatchEdge> boundary = new ArrayList<>(edges.size());
        for (PatchEdgeCount count : edges.values()) {
            if (count.count == 1) {
                boundary.add(count.edge);
            }
        }
        return List.copyOf(boundary);
    }

    static List<PatchVertex> clipPolygon(List<PatchVertex> polygon,
            double minimumU, double minimumV,
            double maximumU, double maximumV) {
        List<PatchVertex> clipped = clipBoundary(
                polygon, true, minimumU, true);
        clipped = clipBoundary(clipped, true, maximumU, false);
        clipped = clipBoundary(clipped, false, minimumV, true);
        return clipBoundary(clipped, false, maximumV, false);
    }

    private static List<PatchVertex> clipBoundary(List<PatchVertex> polygon,
            boolean uAxis, double boundary, boolean keepGreater) {
        if (polygon.isEmpty()) {
            return List.of();
        }
        List<PatchVertex> result = new ArrayList<>(polygon.size() + 1);
        PatchVertex previous = polygon.getLast();
        boolean previousInside = insideBoundary(
                previous, uAxis, boundary, keepGreater);
        for (PatchVertex current : polygon) {
            boolean currentInside = insideBoundary(
                    current, uAxis, boundary, keepGreater);
            if (currentInside != previousInside) {
                result.add(boundaryIntersection(
                        previous, current, uAxis, boundary));
            }
            if (currentInside) {
                result.add(current);
            }
            previous = current;
            previousInside = currentInside;
        }
        return result;
    }

    private static boolean insideBoundary(PatchVertex point,
            boolean uAxis, double boundary, boolean keepGreater) {
        double coordinate = uAxis ? point.u() : point.v();
        return keepGreater
                ? coordinate >= boundary - TRIANGLE_EPSILON
                : coordinate <= boundary + TRIANGLE_EPSILON;
    }

    private static PatchVertex boundaryIntersection(PatchVertex first,
            PatchVertex second, boolean uAxis, double boundary) {
        double firstCoordinate = uAxis ? first.u() : first.v();
        double secondCoordinate = uAxis ? second.u() : second.v();
        double denominator = secondCoordinate - firstCoordinate;
        double amount = Math.abs(denominator) <= TRIANGLE_EPSILON
                ? 0.0D : (boundary - firstCoordinate) / denominator;
        return new PatchVertex(
                uAxis ? boundary : Mth.lerp(amount, first.u(), second.u()),
                uAxis ? Mth.lerp(amount, first.v(), second.v()) : boundary);
    }

    private static boolean sameSurface(Triangle first, Triangle second) {
        return first.face() == second.face()
                && first.normal().dot(second.normal()) >= 0.9999D
                && Math.abs(first.normal().dot(first.first())
                        - first.normal().dot(second.first())) <= PLANE_EPSILON;
    }

    private static PatchVertex faceCoordinates(Direction face, Vec3 point) {
        return switch (face) {
            case UP, DOWN -> new PatchVertex(point.x, point.z);
            case SOUTH -> new PatchVertex(point.x, point.y);
            case NORTH -> new PatchVertex(1.0D - point.x, point.y);
            case WEST -> new PatchVertex(point.z, point.y);
            case EAST -> new PatchVertex(1.0D - point.z, point.y);
        };
    }

    private static double signedArea(List<PatchVertex> polygon) {
        if (polygon.size() < 3) {
            return 0.0D;
        }
        double area = 0.0D;
        PatchVertex previous = polygon.getLast();
        for (PatchVertex current : polygon) {
            area += previous.u() * current.v() - current.u() * previous.v();
            previous = current;
        }
        return area * 0.5D;
    }

    private record PatchEdgeKey(long firstU, long firstV,
            long secondU, long secondV) {
        private static PatchEdgeKey of(PatchVertex first, PatchVertex second) {
            long firstU = Math.round(first.u() * 1_000_000.0D);
            long firstV = Math.round(first.v() * 1_000_000.0D);
            long secondU = Math.round(second.u() * 1_000_000.0D);
            long secondV = Math.round(second.v() * 1_000_000.0D);
            if (firstU > secondU || firstU == secondU && firstV > secondV) {
                return new PatchEdgeKey(secondU, secondV, firstU, firstV);
            }
            return new PatchEdgeKey(firstU, firstV, secondU, secondV);
        }
    }

    private static final class PatchEdgeCount {
        private final PatchEdge edge;
        private int count = 1;

        private PatchEdgeCount(PatchEdge edge) {
            this.edge = edge;
        }
    }

    private static long uvKey(Uv uv) {
        return (long) Float.floatToIntBits(uv.u()) << 32
                ^ Float.floatToIntBits(uv.v()) & 0xFFFFFFFFL;
    }

    private static long pointKey(Vec3 point) {
        long hash = Double.doubleToLongBits(point.x);
        hash = hash * 31L + Double.doubleToLongBits(point.y);
        return hash * 31L + Double.doubleToLongBits(point.z);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    private static double axisCoordinate(Vec3 point, Direction.Axis axis) {
        return switch (axis) {
            case X -> point.x;
            case Y -> point.y;
            case Z -> point.z;
        };
    }

    private static double firstCoordinate(Vec3 point, Direction.Axis axis) {
        return firstCoordinate(point.x, point.y, point.z, axis);
    }

    private static double secondCoordinate(Vec3 point, Direction.Axis axis) {
        return secondCoordinate(point.x, point.y, point.z, axis);
    }

    private static double firstCoordinate(
            double x, double y, double z, Direction.Axis axis) {
        return switch (axis) {
            case X -> y;
            case Y -> x;
            case Z -> x;
        };
    }

    private static double secondCoordinate(
            double x, double y, double z, Direction.Axis axis) {
        return switch (axis) {
            case X -> z;
            case Y -> z;
            case Z -> y;
        };
    }

    static final class TextureAlphaMask {
        private static final TextureAlphaMask OPAQUE =
                new TextureAlphaMask(0, 0, new boolean[0], 0x6f706171);
        private final int width;
        private final int height;
        private final boolean[] visible;
        private final int signature;

        private TextureAlphaMask(
                int width, int height, boolean[] visible, int signature) {
            this.width = width;
            this.height = height;
            this.visible = visible;
            this.signature = signature;
        }

        private static TextureAlphaMask capture(SpriteContents contents) {
            try {
                int width = Math.max(1, contents.width());
                int height = Math.max(1, contents.height());
                int[] frames = contents.getUniqueFrames().toArray();
                if (frames.length == 0) {
                    frames = new int[] {0};
                }
                boolean[] visible = new boolean[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        for (int frame : frames) {
                            if (!contents.isTransparent(frame, x, y)) {
                                visible[x + y * width] = true;
                                break;
                            }
                        }
                    }
                }
                return create(width, height, visible);
            } catch (RuntimeException ignored) {
                return OPAQUE;
            }
        }

        private static TextureAlphaMask create(
                int width, int height, boolean[] visible) {
            if (width <= 0 || height <= 0
                    || visible == null || visible.length != width * height) {
                throw new IllegalArgumentException("Invalid texture alpha mask");
            }
            boolean allVisible = true;
            long hash = width * 31L + height;
            for (boolean pixel : visible) {
                allVisible &= pixel;
                hash = mix(hash ^ (pixel ? 1L : 0L));
            }
            if (allVisible) {
                return OPAQUE;
            }
            return new TextureAlphaMask(width, height, visible.clone(),
                    (int) (hash ^ hash >>> 32));
        }

        private boolean visible(double u, double v) {
            if (this == OPAQUE) {
                return true;
            }
            int x = Math.min(width - 1,
                    Math.max(0, (int) Math.floor(u * width)));
            int y = Math.min(height - 1,
                    Math.max(0, (int) Math.floor(v * height)));
            return visible[x + y * width];
        }

        private int signature() {
            return signature;
        }
    }
}
