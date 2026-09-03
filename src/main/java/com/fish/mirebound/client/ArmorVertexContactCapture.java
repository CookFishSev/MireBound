package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.ClientRenderCompat;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.compat.curios.CuriosCompat;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.ArmorTextureMudData;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.network.payload.ArmorTextureContactPayload;
import com.fish.mirebound.registry.ModBlocks;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Camera-independent UV contact capture for custom armor RenderLayers using VertexConsumer. */
public final class ArmorVertexContactCapture {
    private static final int CAPTURE_INTERVAL_TICKS = 2;
    private static final int MAX_CANDIDATES_PER_TEXTURE = 4096;
    private static final int MAX_UV_SAMPLES_PER_TEXTURE = 4096;
    private static final int MAX_OWNERSHIP_PIXELS_PER_TEXTURE = 32768;
    private static final int MAX_OWNERSHIP_CANDIDATES_PER_TEXTURE = 16384;
    private static final double NEARBY_SCAN_RADIUS = 2.0D;
    private static final double CONTACT_RADIUS = 0.028D;
    private static final int MAX_CAPTURE_RATE_KEYS = 512;
    private static final Map<CaptureKey, Integer> LAST_CAPTURE_TICK =
            new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CaptureKey, Integer> eldest) {
                    return size() > MAX_CAPTURE_RATE_KEYS;
                }
            };
    private static int nearbyProbeTick = Integer.MIN_VALUE;
    private static boolean nearbyProbeResult;

    private ArmorVertexContactCapture() {
    }

    public static MultiBufferSource wrapBuffers(MultiBufferSource delegate, LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(entity instanceof LocalPlayer player) || player != minecraft.player || player.level() == null
                || ClientPollutionVisibility.isContactSamplingSuppressed(player)
                || ClientRenderCompat.isRenderingShaderShadowPass()
                || delegate instanceof CapturingBufferSource) {
            return delegate;
        }
        boolean relevant;
        ArmorAccessoryRenderContext.suppressEquipmentCapture(true);
        try {
            relevant = nearRelevantMaterial(player);
        } finally {
            ArmorAccessoryRenderContext.suppressEquipmentCapture(false);
        }
        if (!relevant) {
            return delegate;
        }
        return new CapturingBufferSource(delegate, player);
    }

    public static void finishLayer(MultiBufferSource buffers) {
        if (buffers instanceof CapturingBufferSource capturing) {
            capturing.finish();
        }
    }

    /** True when a first-person/offscreen capture is worth scheduling this tick. */
    public static boolean needsLocalOffscreenCapture(LocalPlayer player) {
        if (player == null || player.level() == null
                || ClientPollutionVisibility.isContactSamplingSuppressed(player)) {
            return false;
        }
        ArmorAccessoryRenderContext.suppressEquipmentCapture(true);
        try {
            return nearRelevantMaterial(player);
        } finally {
            ArmorAccessoryRenderContext.suppressEquipmentCapture(false);
        }
    }

    public static void reset() {
        LAST_CAPTURE_TICK.clear();
        nearbyProbeTick = Integer.MIN_VALUE;
        nearbyProbeResult = false;
        RuntimeUvOwnership.reset();
    }

    private static boolean nearRelevantMaterial(LocalPlayer player) {
        if (nearbyProbeTick == player.tickCount) {
            return nearbyProbeResult;
        }
        nearbyProbeTick = player.tickCount;
        nearbyProbeResult = scanRelevantMaterial(player);
        return nearbyProbeResult;
    }

    private static boolean scanRelevantMaterial(LocalPlayer player) {
        AABB bounds = player.getBoundingBox().inflate(NEARBY_SCAN_RADIUS, 1.0D, NEARBY_SCAN_RADIUS);
        ClientLevel level = player.clientLevel;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = Mth.floor(bounds.minY); y <= Mth.floor(bounds.maxY); y++) {
            for (int x = Mth.floor(bounds.minX); x <= Mth.floor(bounds.maxX); x++) {
                for (int z = Mth.floor(bounds.minZ); z <= Mth.floor(bounds.maxZ); z++) {
                    cursor.set(x, y, z);
                    if (ModBlocks.mediumOf(level.getBlockState(cursor).getBlock()) != null
                            || level.getFluidState(cursor).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        if (SableCompat.isLoaded() && SableCompat.isTracking(player)) {
            return true;
        }
        if (level.isRaining()) {
            for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
                if (!ArmorMudManager.textureData(player.getItemBySlot(slot)).isEmpty()) {
                    return true;
                }
            }
            if (CuriosCompat.hasTextureMud(player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldCapture(LocalPlayer player, CaptureTarget target) {
        CaptureKey key = new CaptureKey(target.key, target.texture, target.width, target.height);
        int lastTick = LAST_CAPTURE_TICK.getOrDefault(key, Integer.MIN_VALUE);
        if (lastTick != Integer.MIN_VALUE && player.tickCount - lastTick >= 0
                && player.tickCount - lastTick < CAPTURE_INTERVAL_TICKS) {
            return false;
        }
        LAST_CAPTURE_TICK.put(key, player.tickCount);
        return true;
    }

    private static CaptureTarget target(LocalPlayer player) {
        ArmorAccessoryRenderContext.CaptureTarget context = ArmorAccessoryRenderContext.captureTarget();
        if (context == null || context.entity() != player) {
            return null;
        }
        ItemStack stack = context.stack();
        if (stack.isEmpty() || !SkinPixelCache.hasPixels(context.texture())) {
            return null;
        }
        int width = SkinPixelCache.width(context.texture());
        int height = SkinPixelCache.height(context.texture());
        if (width <= 0 || height <= 0 || width > ArmorTextureMudData.MAX_DIMENSION
                || height > ArmorTextureMudData.MAX_DIMENSION) {
            return null;
        }
        ArmorTextureMudData.Layer dirtyLayer = ArmorMudManager.textureData(stack)
                .layer(context.texture(), width, height);
        return new CaptureTarget(context.key(), stack, context.armorSlot(), context.curiosIdentifier(),
                context.curiosIndex(), context.curiosCosmetic(), context.texture(), width, height, dirtyLayer);
    }

    private static int slotIndex(EquipmentSlot slot) {
        EquipmentSlot[] slots = ArmorMudManager.armorSlots();
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] == slot) {
                return index;
            }
        }
        return -1;
    }

    private static Contact contactAt(LocalPlayer player, Vec3 point) {
        ClientLevel level = player.clientLevel;
        if (isWaterTouching(level, point)) {
            return Contact.WATER;
        }
        SinkingMedium medium = worldMediumAt(level, point);
        if (medium != null) {
            return new Contact(false, false, medium);
        }
        SableCompat.SinkingSample sample = SableCompat.sampleSinking(level, point, player);
        if (sample == null) {
            return rainContactAt(level, point);
        }
        return new Contact(false, false, sample.medium());
    }

    private static Contact rainContactAt(ClientLevel level, Vec3 point) {
        return level.isRainingAt(BlockPos.containing(point)) ? Contact.RAIN : Contact.NONE;
    }

    private static SinkingMedium worldMediumAt(ClientLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        SinkingMedium medium = ModBlocks.mediumOf(level.getBlockState(pos).getBlock());
        if (medium == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return MudBlock.containsLocalPoint(
                level, pos, state, medium,
                point.subtract(pos.getX(), pos.getY(), pos.getZ()),
                0.040D)
                ? medium
                : null;
    }

    private static boolean isWaterTouching(ClientLevel level, Vec3 point) {
        return isWaterAt(level, point)
                || isWaterAt(level, point.add(CONTACT_RADIUS, 0.0D, 0.0D))
                || isWaterAt(level, point.add(-CONTACT_RADIUS, 0.0D, 0.0D))
                || isWaterAt(level, point.add(0.0D, CONTACT_RADIUS, 0.0D))
                || isWaterAt(level, point.add(0.0D, -CONTACT_RADIUS, 0.0D))
                || isWaterAt(level, point.add(0.0D, 0.0D, CONTACT_RADIUS))
                || isWaterAt(level, point.add(0.0D, 0.0D, -CONTACT_RADIUS));
    }

    private static boolean isWaterAt(ClientLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER)) {
            return false;
        }
        double top = pos.getY() + fluid.getHeight(level, pos);
        return point.y <= top + 0.040D && point.y >= pos.getY() - 0.040D;
    }

    private static final class CapturingBufferSource implements MultiBufferSource {
        private final MultiBufferSource delegate;
        private final LocalPlayer player;
        private final Map<CaptureKey, Batch> batches = new LinkedHashMap<>();
        private final List<CapturingVertexConsumer> consumers = new ArrayList<>();

        private CapturingBufferSource(MultiBufferSource delegate, LocalPlayer player) {
            this.delegate = delegate;
            this.player = player;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            VertexConsumer original = delegate.getBuffer(renderType);
            CaptureTarget target = target(player);
            if (target == null) {
                return original;
            }
            CaptureKey key = new CaptureKey(target.key, target.texture, target.width, target.height);
            Batch batch = batches.get(key);
            if (batch == null) {
                if (!shouldCapture(player, target)) {
                    return original;
                }
                batch = new Batch(player, target);
                batches.put(key, batch);
            }
            CapturingVertexConsumer capturing = new CapturingVertexConsumer(original, batch);
            consumers.add(capturing);
            return capturing;
        }

        private void finish() {
            for (CapturingVertexConsumer consumer : consumers) {
                consumer.finish();
            }
            for (Batch batch : batches.values()) {
                batch.send();
            }
        }
    }

    private static final class Batch {
        private final LocalPlayer player;
        private final CaptureTarget target;
        private final Map<Integer, Candidate> candidates = new HashMap<>();
        private final boolean preciseOwnership;
        private final RuntimeUvOwnership.Frame<Candidate> ownership;
        private final BitSet dirtyMask;
        private int uvSamples;

        private Batch(LocalPlayer player, CaptureTarget target) {
            this.player = player;
            this.target = target;
            this.preciseOwnership = MireboundClientSettings.preciseUvOwnership();
            this.ownership = preciseOwnership
                    ? new RuntimeUvOwnership.Frame<>(MAX_OWNERSHIP_PIXELS_PER_TEXTURE,
                            MAX_OWNERSHIP_CANDIDATES_PER_TEXTURE)
                    : null;
            this.dirtyMask = target.dirtyLayer == null ? null : new BitSet(target.width * target.height);
            if (target.dirtyLayer != null) {
                target.dirtyLayer.forEach((pixel, coverage, medium) -> dirtyMask.set(pixel));
            }
        }

        private int registerOwner(RuntimeUvQuadAssembler.Vertex[] quad) {
            if (!preciseOwnership || ownership == null || !ownership.reliable()) {
                return -1;
            }
            return ownership.register(
                    RuntimeUvOwnership.physicalOwnerKey(quad),
                    RuntimeUvOwnership.rasterPixels(quad, target.texture, target.width, target.height));
        }

        private void offer(int owner, int pixel, Vec3 point, Contact contact) {
            if (contact == Contact.NONE || pixel < 0 || pixel >= target.width * target.height) {
                return;
            }
            if ((contact.water || contact.rain) && !isDirty(pixel)) {
                return;
            }
            if (candidates.size() < MAX_CANDIDATES_PER_TEXTURE || candidates.containsKey(pixel)) {
                Candidate current = candidates.get(pixel);
                if (current == null || priority(contact) > priority(current.contact)) {
                    candidates.put(pixel, new Candidate(pixel, point, contact));
                }
            }
            if (ownership != null && ownership.reliable()) {
                Candidate offered = new Candidate(pixel, point, contact);
                ownership.offer(owner, pixel, offered,
                        (previous, next) -> priority(next.contact) > priority(previous.contact) ? next : previous);
            }
        }

        private boolean isDirty(int pixel) {
            return dirtyMask != null && pixel >= 0 && dirtyMask.get(pixel);
        }

        private static int priority(Contact contact) {
            if (contact.water) {
                return 3;
            }
            if (contact.medium != null) {
                return 2;
            }
            return contact.rain ? 1 : 0;
        }

        private void send() {
            if (candidates.isEmpty()) {
                return;
            }
            Vec3 origin = player.position();
            int salt = player.tickCount / CAPTURE_INTERVAL_TICKS;
            List<Candidate> ordered = resolvedCandidates();
            if (ordered.isEmpty()) {
                return;
            }
            ordered.sort(Comparator.comparingInt(candidate -> mix(candidate.pixel ^ salt * 0x9E3779B9)));
            int count = Math.min(ordered.size(), ArmorTextureContactPayload.MAX_SAMPLES);
            List<ArmorTextureContactPayload.Sample> samples = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Candidate candidate = ordered.get(index);
                Vec3 offset = candidate.point.subtract(origin);
                samples.add(new ArmorTextureContactPayload.Sample(candidate.pixel,
                        (float) offset.x, (float) offset.y, (float) offset.z));
            }
            ArmorTextureContactPayload payload = target.armorSlot == null
                    ? ArmorTextureContactPayload.curios(target.curiosIdentifier, target.curiosIndex,
                            target.curiosCosmetic, target.texture, target.width, target.height,
                            origin, ordered.size(), samples)
                    : ArmorTextureContactPayload.armor(slotIndex(target.armorSlot), target.texture,
                            target.width, target.height, origin, ordered.size(), samples);
            PacketDistributor.sendToServer(payload);
        }

        private List<Candidate> resolvedCandidates() {
            if (!preciseOwnership || ownership == null || !ownership.reliable()) {
                return new ArrayList<>(candidates.values());
            }
            List<RuntimeUvOwnership.Resolved<Candidate>> resolved = ownership.resolve(Batch::resolveSharedPixel);
            List<Candidate> result = new ArrayList<>(resolved.size());
            for (RuntimeUvOwnership.Resolved<Candidate> entry : resolved) {
                result.add(entry.value());
            }
            return result;
        }

        private static Candidate resolveSharedPixel(int ownerCount, Map<Integer, Candidate> valuesByOwner) {
            Candidate selected = null;
            for (Candidate candidate : valuesByOwner.values()) {
                if (selected == null || priority(candidate.contact) > priority(selected.contact)) {
                    selected = candidate;
                }
            }
            // A fused texture cannot display two states for the exact same texel. Keep the
            // stable any-contact result for that unrepresentable case instead of dropping mud.
            return selected;
        }
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final Batch batch;
        private final RuntimeUvQuadAssembler assembler;
        private boolean finished;

        private CapturingVertexConsumer(VertexConsumer delegate, Batch batch) {
            this.delegate = delegate;
            this.batch = batch;
            this.assembler = new RuntimeUvQuadAssembler(this::sampleQuad);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 cameraPosition = camera == null ? Vec3.ZERO : camera.getPosition();
            assembler.beginVertex(cameraPosition.add(x, y, z));
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            assembler.setUv(u, v);
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            assembler.commitCurrent();
            return this;
        }

        private void finish() {
            if (!finished) {
                finished = true;
                assembler.commitCurrent();
            }
        }

        private void sampleQuad(RuntimeUvQuadAssembler.Vertex[] quad) {
            int width = batch.target.width;
            int height = batch.target.height;
            int owner = batch.registerOwner(quad);
            float minU = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;
            for (RuntimeUvQuadAssembler.Vertex vertex : quad) {
                minU = Math.min(minU, vertex.u);
                maxU = Math.max(maxU, vertex.u);
                minV = Math.min(minV, vertex.v);
                maxV = Math.max(maxV, vertex.v);
            }
            int x0 = Mth.clamp(Mth.floor(minU * width + 0.0001F), 0, width);
            int x1 = Mth.clamp(Mth.ceil(maxU * width - 0.0001F), 0, width);
            int y0 = Mth.clamp(Mth.floor(minV * height + 0.0001F), 0, height);
            int y1 = Mth.clamp(Mth.ceil(maxV * height - 0.0001F), 0, height);
            recordUvFootprint(quad, width, height, x0, x1, y0, y1);
            if (batch.uvSamples >= MAX_UV_SAMPLES_PER_TEXTURE
                    || !quadMayTouch(batch, quad)) {
                return;
            }
            int area = Math.max(1, (x1 - x0) * (y1 - y0));
            int phases = Math.max(1, Mth.ceil(area / 1024.0F));
            int phase = Math.floorMod(batch.player.tickCount / CAPTURE_INTERVAL_TICKS, phases);
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int pixel = y * width + x;
                    if (Math.floorMod(mix(pixel), phases) != phase) {
                        continue;
                    }
                    if (batch.uvSamples++ >= MAX_UV_SAMPLES_PER_TEXTURE) {
                        return;
                    }
                    float u = (x + 0.5F) / width;
                    float v = (y + 0.5F) / height;
                    Vec3 point = RuntimeUvQuadAssembler.interpolateTriangle(u, v, quad[0], quad[1], quad[2]);
                    if (point == null) {
                        point = RuntimeUvQuadAssembler.interpolateTriangle(u, v, quad[0], quad[2], quad[3]);
                    }
                    if (point == null || !SkinPixelCache.isOpaque(batch.target.texture, u, v)) {
                        continue;
                    }
                    Contact contact = contactAt(batch.player, point);
                    batch.offer(owner, pixel, point, contact);
                    if (contact == Contact.NONE) {
                        offerInsetContact(owner, quad, pixel, x, y, width, height, 0.20F, 0.20F);
                        offerInsetContact(owner, quad, pixel, x, y, width, height, 0.80F, 0.20F);
                        offerInsetContact(owner, quad, pixel, x, y, width, height, 0.20F, 0.80F);
                        offerInsetContact(owner, quad, pixel, x, y, width, height, 0.80F, 0.80F);
                    }
                }
            }
        }

        private void recordUvFootprint(RuntimeUvQuadAssembler.Vertex[] quad, int width, int height,
                int x0, int x1, int y0, int y1) {
            if (!ArmorTextureFootprintCache.claimRect(batch.target.stack, batch.target.texture,
                    width, height, x0, y0, x1, y1)) {
                return;
            }
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    float u = (x + 0.5F) / width;
                    float v = (y + 0.5F) / height;
                    if (pointOnQuad(u, v, quad) != null
                            && SkinPixelCache.isOpaque(batch.target.texture, u, v)) {
                        ArmorTextureFootprintCache.recordPixel(batch.target.stack, batch.target.texture,
                                width, height, y * width + x);
                    }
                }
            }
        }

        private void offerInsetContact(int owner, RuntimeUvQuadAssembler.Vertex[] quad, int pixel, int x, int y,
                int width, int height, float insetX, float insetY) {
            float u = (x + insetX) / width;
            float v = (y + insetY) / height;
            Vec3 point = pointOnQuad(u, v, quad);
            if (point != null) {
                batch.offer(owner, pixel, point, contactAt(batch.player, point));
            }
        }

        private static Vec3 pointOnQuad(float u, float v, RuntimeUvQuadAssembler.Vertex[] quad) {
            Vec3 point = RuntimeUvQuadAssembler.interpolateTriangle(u, v, quad[0], quad[1], quad[2]);
            return point != null
                    ? point
                    : RuntimeUvQuadAssembler.interpolateTriangle(u, v, quad[0], quad[2], quad[3]);
        }
    }

    private static boolean quadMayTouch(Batch batch, RuntimeUvQuadAssembler.Vertex[] quad) {
        LocalPlayer player = batch.player;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (RuntimeUvQuadAssembler.Vertex vertex : quad) {
            minX = Math.min(minX, vertex.position.x);
            minY = Math.min(minY, vertex.position.y);
            minZ = Math.min(minZ, vertex.position.z);
            maxX = Math.max(maxX, vertex.position.x);
            maxY = Math.max(maxY, vertex.position.y);
            maxZ = Math.max(maxZ, vertex.position.z);
        }
        AABB bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.045D);
        ClientLevel level = player.clientLevel;
        if (batch.dirtyMask != null && level.isRaining()) {
            return true;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = Mth.floor(bounds.minY); y <= Mth.floor(bounds.maxY); y++) {
            for (int x = Mth.floor(bounds.minX); x <= Mth.floor(bounds.maxX); x++) {
                for (int z = Mth.floor(bounds.minZ); z <= Mth.floor(bounds.maxZ); z++) {
                    cursor.set(x, y, z);
                    if (ModBlocks.mediumOf(level.getBlockState(cursor).getBlock()) != null
                            || level.getFluidState(cursor).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        if (!SableCompat.isLoaded()) {
            return false;
        }
        Vec3 center = new Vec3((minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D);
        if (contactAt(player, center) != Contact.NONE) {
            return true;
        }
        for (RuntimeUvQuadAssembler.Vertex vertex : quad) {
            if (contactAt(player, vertex.position) != Contact.NONE) {
                return true;
            }
        }
        return false;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        return value ^ value >>> 16;
    }

    private record CaptureKey(String targetKey, net.minecraft.resources.ResourceLocation texture,
            int width, int height) {
    }

    private record CaptureTarget(String key, ItemStack stack, EquipmentSlot armorSlot,
            String curiosIdentifier, int curiosIndex, boolean curiosCosmetic,
            net.minecraft.resources.ResourceLocation texture, int width, int height,
            ArmorTextureMudData.Layer dirtyLayer) {
    }

    private record Candidate(int pixel, Vec3 point, Contact contact) {
    }

    private record Contact(boolean water, boolean rain, SinkingMedium medium) {
        private static final Contact NONE = new Contact(false, false, null);
        private static final Contact WATER = new Contact(true, false, null);
        private static final Contact RAIN = new Contact(false, true, null);
    }

}
