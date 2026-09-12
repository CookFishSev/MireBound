package com.fish.mirebound.client.coverage;

import com.fish.mirebound.client.ClientPollutionVisibility;
import com.fish.mirebound.client.ArmorVertexContactCapture;
import com.fish.mirebound.client.MudSkinTextureCache;
import com.fish.mirebound.client.compat.ClientRenderCompat;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceData;
import com.fish.mirebound.coverage.armor.EquipmentSurfacePatch;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceService;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceTarget;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.EquipmentSurfaceContactPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.PriorityQueue;
import java.util.Comparator;
import com.fish.mirebound.client.MudCoverageAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/** Draws and samples the same physical cells; it never copies head coverage onto decorations. */
public final class EquipmentSurfaceRenderer {
    private static final Map<ModelPart.Cube, List<EquipmentSurfaceGeometry.Vertex[]>> GEOMETRY = new IdentityHashMap<>();
    private static final Map<BakedQuad, EquipmentSurfaceGeometry.Face> BAKED_GEOMETRY = new IdentityHashMap<>();
    private static final Set<CaptureKey> CAPTURED = new HashSet<>();
    private record BakedCapture(long tick, int next) {}
    private static final Map<EquipmentSurfaceTarget, BakedCapture> LAST_BAKED_CAPTURE = new java.util.LinkedHashMap<>();
    private record CaptureKey(EquipmentSurfaceTarget target, long face) {}
    private static long captureTick = Long.MIN_VALUE;
    private static final Field POLYGONS = field(ModelPart.Cube.class, "polygons");
    private static final Field VERTICES = field(nested("Polygon"), "vertices");
    private static final Field POSITION = field(nested("Vertex"), "pos");
    private static final Field U = field(nested("Vertex"), "u"), V = field(nested("Vertex"), "v");
    private EquipmentSurfaceRenderer() {}
    public static void reset() { GEOMETRY.clear(); BAKED_GEOMETRY.clear(); CAPTURED.clear(); LAST_BAKED_CAPTURE.clear(); captureTick = Long.MIN_VALUE; }

    static boolean fullSurfaceCaptureDue(long tick, Long previous) {
        return previous == null || tick < previous || tick - previous >= 2;
    }

    public static void modelPart(LivingEntity entity, ItemStack stack, EquipmentSurfaceTarget target,
            String owner, ModelPart model, ResourceLocation texture, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, int color, boolean armorViewOffset) {
        if (!enabled(entity) || stack.isEmpty() || !model.visible) return;
        Session session = new Session(entity, stack, target, buffers, light, overlay, armorViewOffset, false);
        if (!session.capture && session.data.isEmpty()) return;
        SurfaceMaterial material = MudSkinTextureCache.currentMaterial(texture);
        if (material == null) return;
        var initial = model.getInitialPose();
        String prefix = owner + ':' + texture + ':' + initial.x + ':' + initial.y + ':' + initial.z
                + ':' + initial.xRot + ':' + initial.yRot + ':' + initial.zRot;
        model.visit(pose, (transform, path, index, cube) -> {
            if (!visiblePath(model, path)) return;
            List<EquipmentSurfaceGeometry.Vertex[]> polygons = geometry(cube);
            for (int i = 0; i < polygons.size(); i++) {
                var face = EquipmentSurfaceGeometry.face(prefix + path + ':' + index + ':' + i, polygons.get(i), 1F);
                session.face(face, transform, 1F / 16F, material, color >>> 24);
            }
        });
        session.finish();
    }

    /** One worn-item pass shares geometry and packet budgets across every model direction/material. */
    public static final class BakedSession {
        private final Session session;
        private final Set<Long> faces = new HashSet<>();

        public BakedSession(LivingEntity entity, ItemStack stack, EquipmentSurfaceTarget target,
                MultiBufferSource buffers, int light, int overlay) {
            session = enabled(entity) && !stack.isEmpty()
                    ? new Session(entity, stack, target, buffers, light, overlay, false, true) : null;
        }

        public void render(List<BakedQuad> quads, PoseStack.Pose pose) {
            if (session == null || !session.capture && session.data.isEmpty()) return;
            for (BakedQuad quad : quads) {
                if (faces.size() >= 4096) { session.completeSurface = false; break; }
                var sprite = quad.getSprite();
                if (!(sprite.contents() instanceof UploadedSpriteFrame material)) continue;
                var face = bakedFace(quad);
                if (face != null && faces.add(face.id())) session.face(face, pose, 1F, material, 255);
            }
        }

        public void finish() {
            if (session != null) session.finish();
        }
    }

    private static EquipmentSurfaceGeometry.Face bakedFace(BakedQuad quad) {
        var cached = BAKED_GEOMETRY.get(quad);
        if (cached != null) return cached;
        int[] raw = quad.getVertices();
        int stride = raw.length / 4;
        var sprite = quad.getSprite();
        float width = sprite.getU1() - sprite.getU0(), height = sprite.getV1() - sprite.getV0();
        if (stride < 6 || width <= 0 || height <= 0) return null;
        EquipmentSurfaceGeometry.Vertex[] vertices = new EquipmentSurfaceGeometry.Vertex[4];
        for (int i = 0; i < 4; i++) {
            int p = i * stride;
            float u = (Float.intBitsToFloat(raw[p + 4]) - sprite.getU0()) / width;
            float v = (Float.intBitsToFloat(raw[p + 5]) - sprite.getV0()) / height;
            vertices[i] = new EquipmentSurfaceGeometry.Vertex(Float.intBitsToFloat(raw[p]),
                    Float.intBitsToFloat(raw[p + 1]), Float.intBitsToFloat(raw[p + 2]), u, v);
        }
        var face = EquipmentSurfaceGeometry.face("baked:" + sprite.contents().name(), vertices, 1F / 16F);
        if (BAKED_GEOMETRY.size() >= 4096) BAKED_GEOMETRY.clear();
        BAKED_GEOMETRY.put(quad, face);
        return face;
    }

    private static boolean enabled(LivingEntity entity) {
        return entity != null && !entity.isInvisible() && !ClientPollutionVisibility.isSuppressed(entity)
                && !ClientRenderCompat.isRenderingShaderShadowPass()
                && MireboundClientSettings.independentSurfaceCoverage()
                && MireboundClientSettings.clientOptionEnabled(ClientOption.PLAYER_COVERAGE);
    }
    private static boolean visiblePath(ModelPart root, String path) {
        ModelPart part = root;
        for (String name : path.split("/")) {
            if (!part.visible) return false;
            if (!name.isEmpty()) part = part.getChild(name);
        }
        return part.visible && !part.skipDraw;
    }
    private static List<EquipmentSurfaceGeometry.Vertex[]> geometry(ModelPart.Cube cube) {
        List<EquipmentSurfaceGeometry.Vertex[]> cached = GEOMETRY.get(cube);
        if (cached != null) return cached;
        if (GEOMETRY.size() >= 4096) GEOMETRY.clear();
        List<EquipmentSurfaceGeometry.Vertex[]> faces = new ArrayList<>();
        try {
            for (Object polygon : (Object[]) POLYGONS.get(cube)) {
                Object[] raw = (Object[]) VERTICES.get(polygon);
                if (raw.length != 4) continue;
                var vertices = new EquipmentSurfaceGeometry.Vertex[4];
                for (int i = 0; i < 4; i++) {
                    Vector3f p = (Vector3f) POSITION.get(raw[i]);
                    vertices[i] = new EquipmentSurfaceGeometry.Vertex(p.x, p.y, p.z, U.getFloat(raw[i]), V.getFloat(raw[i]));
                }
                faces.add(vertices);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { return List.of(); }
        GEOMETRY.put(cube, List.copyOf(faces));
        return faces;
    }
    private static Class<?> nested(String name) {
        for (Class<?> type : ModelPart.class.getDeclaredClasses()) if (type.getSimpleName().equals(name)) return type;
        return null;
    }
    private static Field field(Class<?> type, String name) {
        try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }
    private static final class Session {
        private final LivingEntity entity;
        private final ItemStack stack;
        private final EquipmentSurfaceTarget target;
        private final EquipmentSurfaceData data;
        private final MultiBufferSource buffers;
        private VertexConsumer consumer;
        private final int light, overlay;
        private final boolean armorViewOffset;
        private final boolean fullSurface;
        private final boolean capture;
        private final List<EquipmentSurfacePatch> patches = new ArrayList<>();
        private boolean completeSurface = true;
        private record RankedSample(long priority, EquipmentSurfaceContactPayload.Sample sample) {}
        private final PriorityQueue<RankedSample> samples = new PriorityQueue<>(Comparator.comparingLong(RankedSample::priority).reversed());
        private final Vec3 camera, origin;
        private int visited;
        private int remainingTexels = 16384;
        Session(LivingEntity entity, ItemStack stack, EquipmentSurfaceTarget target,
                MultiBufferSource buffers, int light, int overlay, boolean armorViewOffset, boolean fullSurface) {
            this.entity = entity; this.stack = stack; this.target = target;
            this.light = light; this.overlay = overlay;
            this.armorViewOffset = armorViewOffset;
            this.fullSurface = fullSurface;
            data = EquipmentSurfaceService.data(stack);
            this.buffers = buffers;
            Minecraft minecraft = Minecraft.getInstance();
            camera = minecraft.gameRenderer.getMainCamera().getPosition();
            origin = entity.position();
            long tick = entity.level().getGameTime();
            if (captureTick != tick) { CAPTURED.clear(); captureTick = tick; }
            BakedCapture previous = LAST_BAKED_CAPTURE.get(target);
            capture = entity == minecraft.player && target != null
                    && (fullSurface ? fullSurfaceCaptureDue(tick, previous == null ? null : previous.tick()) : (entity.tickCount & 1) == 0)
                    && !ClientPollutionVisibility.isContactSamplingSuppressed(minecraft.player)
                    && (ArmorVertexContactCapture.needsLocalOffscreenCapture(minecraft.player) || !data.isEmpty());
        }
        void face(EquipmentSurfaceGeometry.Face face, PoseStack.Pose pose, float scale, SurfaceMaterial pixels, int vertexAlpha) {
            if (vertexAlpha == 0 || !capture && !data.hasFace(face.id())) return;
            // One dense face must not consume the draw or capture allowance of later model parts.
            if (fullSurface) { visited = 0; remainingTexels = 16384; }
            if (visited >= 8192 || remainingTexels <= 0) { completeSurface = false; return; }
            Vec3 localNormal = face.b().position().subtract(face.a().position())
                    .cross(face.d().position().subtract(face.a().position())).normalize();
            Vector3f n = pose.transformNormal((float)localNormal.x, (float)localNormal.y, (float)localNormal.z, new Vector3f());
            if (!Float.isFinite(n.lengthSquared()) || n.lengthSquared() < 1e-10) return;
            n.normalize();
            boolean captureFace = capture && CAPTURED.add(new CaptureKey(target, face.id()));
            List<Integer> probes = captureFace && fullSurface ? new ArrayList<>() : null;
            int visitedBefore = visited;
            for (int y = 0; y < face.height() && visited < 8192; y++) for (int x = 0; x < face.width() && visited++ < 8192; x++) {
                int cell = face.cell(x,y);
                var dirty = data.cell(face.id(), cell);
                boolean sample = captureFace && (fullSurface
                        || Math.floorMod(Long.hashCode(face.id()) + cell, 4) == (entity.tickCount / 2 & 3));
                if (dirty == null && !sample) continue;
                final boolean[] sampled = {false};
                final int px = x, py = y;
                remainingTexels -= SurfaceTexelClip.visitCoverage(face,x,y,pixels,remainingTexels,(s0,t0,s1,t1,material) -> {
                    if (sample && !sampled[0]) {
                        sampled[0] = true;
                        if (probes != null) {
                            probes.add(EquipmentSurfacePatch.probe(cell,
                                    (s0 + s1) * .5 * face.width() - px,
                                    (t0 + t1) * .5 * face.height() - py));
                        } else {
                            Vec3 p = transformed(face.point((s0+s1)*.5,(t0+t1)*.5), pose, scale).add(camera).subtract(origin);
                            if (p.lengthSqr() <= 16) offer(new EquipmentSurfaceContactPayload.Sample(face.id(),cell,(float)p.x,(float)p.y,(float)p.z));
                        }
                    }
                    if (dirty == null) return;
                    SinkingMedium medium = SinkingMedium.byId(dirty.medium());
                    int salt = Long.hashCode(face.id());
                    int alpha = Math.round(dirty.strength() * MudCoverageAppearance.opacityScale(medium,px,py,salt));
                    int color = MudSkinTextureCache.skinCoverageTextureAbgr(medium, dirty.source(), px,py,salt,
                            alpha * (material >>> 24) / 255 * vertexAlpha / 255);
                    int argb = (color & 0xff00ff00) | (color & 255) << 16 | (color >>> 16 & 255);
                    emit(face.point(s0,t0),pose,scale,n,argb); emit(face.point(s1,t0),pose,scale,n,argb);
                    emit(face.point(s1,t1),pose,scale,n,argb); emit(face.point(s0,t1),pose,scale,n,argb);
                });
            }
            if (probes != null && !probes.isEmpty()) {
                Vec3 a = transformed(face.a().position(), pose, scale).add(camera).subtract(origin);
                Vec3 b = transformed(face.b().position(), pose, scale).add(camera).subtract(origin);
                Vec3 c = transformed(face.c().position(), pose, scale).add(camera).subtract(origin);
                Vec3 d = transformed(face.d().position(), pose, scale).add(camera).subtract(origin);
                if (EquipmentSurfacePatch.validCorner(a) && EquipmentSurfacePatch.validCorner(b)
                        && EquipmentSurfacePatch.validCorner(c) && EquipmentSurfacePatch.validCorner(d)) {
                    patches.add(new EquipmentSurfacePatch(face.id(), face.width(), face.height(), a, b, c, d, probes));
                } else completeSurface = false;
            }
            if (visited - visitedBefore < face.width() * face.height() || remainingTexels <= 0) completeSurface = false;
        }
        private void offer(EquipmentSurfaceContactPayload.Sample sample) {
            long hash = sample.face() ^ (sample.cell() * 0x9E3779B97F4A7C15L) ^ entity.tickCount;
            hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
            hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
            RankedSample ranked = new RankedSample(hash ^ (hash >>> 31), sample);
            if (samples.size() < EquipmentSurfaceContactPayload.MAX_SAMPLES) samples.add(ranked);
            else if (ranked.priority() < samples.peek().priority()) { samples.poll(); samples.add(ranked); }
        }
        private Vec3 transformed(Vec3 p, PoseStack.Pose pose, float scale) {
            Vector3f point = pose.pose().transformPosition((float)p.x*scale,(float)p.y*scale,(float)p.z*scale,new Vector3f());
            return new Vec3(point.x,point.y,point.z);
        }
        private void emit(Vec3 p, PoseStack.Pose pose,float scale,Vector3f normal,int color) {
            if (consumer == null) consumer = buffers instanceof SurfaceDrawQueue queued ? queued.layer(armorViewOffset)
                    : buffers.getBuffer(SurfaceRenderTypes.translucent(armorViewOffset));
            Vec3 point=transformed(p,pose,scale);
            consumer.addVertex((float)point.x,(float)point.y,(float)point.z,color,.5F,.5F,overlay,light,normal.x,normal.y,normal.z);
        }
        void finish() {
            if (!capture) return;
            if (fullSurface && !patches.isEmpty()) {
                BakedCapture previous = LAST_BAKED_CAPTURE.get(target);
                EquipmentSurfaceBatch batch = EquipmentSurfaceBatch.select(patches, previous == null ? 0 : previous.next());
                LAST_BAKED_CAPTURE.put(target, new BakedCapture(entity.level().getGameTime(), batch.next()));
                while (LAST_BAKED_CAPTURE.size() > 32) LAST_BAKED_CAPTURE.remove(LAST_BAKED_CAPTURE.keySet().iterator().next());
                PacketDistributor.sendToServer(new EquipmentSurfaceContactPayload(target,
                        BuiltInRegistries.ITEM.getKey(stack.getItem()), origin, List.of(), batch.patches(),
                        completeSurface && batch.surfaceCells() <= EquipmentSurfaceData.MAX_CELLS,
                        Math.min(EquipmentSurfaceData.MAX_CELLS, batch.surfaceCells())));
            } else if (!samples.isEmpty()) PacketDistributor.sendToServer(new EquipmentSurfaceContactPayload(
                    target, BuiltInRegistries.ITEM.getKey(stack.getItem()), origin,
                    samples.stream().map(RankedSample::sample).toList()));
        }
    }
}
