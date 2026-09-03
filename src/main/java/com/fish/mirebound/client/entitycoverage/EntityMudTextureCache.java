package com.fish.mirebound.client.entitycoverage;

import com.fish.mirebound.client.AdaptiveMudClientCache;
import com.fish.mirebound.client.BoundedResourceReader;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Bounded per-entity UV textures matched to each renderer's source aspect ratio. */
public final class EntityMudTextureCache {
    private static final int FALLBACK_TEXTURE_SIZE = 64;
    private static final int MAXIMUM_TEXTURE_DIMENSION = 128;
    private static final int MAXIMUM_TEXTURES = 256;
    private static final int MAXIMUM_SOURCE_IMAGES = 512;
    private static final int UNUSED_TICKS = 200;
    private static final int PRUNE_INTERVAL_TICKS = 100;
    private static final Map<Integer, Entry> CACHE =
            new LinkedHashMap<>(64, 0.75F, true);
    private static final Map<ResourceLocation, SourceImage> SOURCE_IMAGES =
            new LinkedHashMap<>(64, 0.75F, true);
    private static int pruneTicks;

    private EntityMudTextureCache() {
    }

    public static ResourceLocation textureFor(
            LivingEntity entity, ClientEntityMudCoverage.View view,
            PoseStack poseStack, EntityModel<?> model,
            ResourceLocation sourceTexture) {
        Minecraft minecraft = Minecraft.getInstance();
        SourceImage sourceImage = sourceImage(minecraft, sourceTexture);
        return textureFor(entity, view, sourceTexture, sourceImage, model,
                (image, missing, collisionHeight) ->
                        EntityMudGeometryProjector.project(
                                model, poseStack,
                                image.getWidth(), image.getHeight(),
                                missing, view.patternSeed(), collisionHeight));
    }

    private static ResourceLocation textureFor(
            LivingEntity entity, ClientEntityMudCoverage.View view,
            ResourceLocation sourceTexture, SourceImage sourceImage,
            Object projectionGeometry, ProjectionFactory projector) {
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.ENTITY_COVERAGE)) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        TextureSize size = sourceImage.size();
        Entry entry = CACHE.get(entity.getId());
        if (entry != null && !entry.uuid.equals(entity.getUUID())) {
            release(entry);
            CACHE.remove(entity.getId());
            entry = null;
        }
        if (entry != null && (!Objects.equals(entry.sourceTexture, sourceTexture)
                || !entry.size.equals(size))) {
            release(entry);
            CACHE.remove(entity.getId());
            entry = null;
        }
        if (entry == null) {
            if (CACHE.size() >= MAXIMUM_TEXTURES) {
                prune(minecraft, true);
                if (CACHE.size() >= MAXIMUM_TEXTURES) {
                    return null;
                }
            }
            entry = create(entity.getUUID(), sourceTexture, size);
            CACHE.put(entity.getId(), entry);
        }
        entry.lastSeenTick = entity.tickCount;
        float collisionHeight = entity.getBbHeight();
        if (entry.projectionGeometry != projectionGeometry
                || Math.abs(entry.projectionCollisionHeight - collisionHeight) > 0.01F) {
            entry.projections.clear();
            entry.projectionSignatures.clear();
            entry.projectionGeometry = projectionGeometry;
            entry.projectionCollisionHeight = collisionHeight;
            entry.visualSignature = Long.MIN_VALUE;
        }
        long visualSignature = view.visualSignature();
        for (ClientEntityMudCoverage.SpotView spot : view.spots()) {
            int revision = AdaptiveMudClientCache.appearanceRevision(
                    minecraft.level, spot.visualSource());
            if (revision != 0) {
                visualSignature = (visualSignature * 31L
                        + Long.hashCode(spot.visualSource())) * 31L
                        + Integer.toUnsignedLong(revision);
            }
        }
        if (entry.visualSignature != visualSignature) {
            NativeImage image = entry.texture.getPixels();
            if (image == null) {
                return null;
            }
            Set<Integer> activeIds = entry.activeProjectionIds;
            ArrayList<ClientEntityMudCoverage.SpotView> missing =
                    entry.missingProjections;
            activeIds.clear();
            missing.clear();
            for (ClientEntityMudCoverage.SpotView spot : view.spots()) {
                activeIds.add(spot.id());
                if (!Objects.equals(
                        entry.projectionSignatures.get(spot.id()),
                        spot.geometrySignature())) {
                    missing.add(spot);
                }
            }
            entry.projections.keySet().removeIf(id -> !activeIds.contains(id));
            entry.projectionSignatures.keySet()
                    .removeIf(id -> !activeIds.contains(id));
            if (!missing.isEmpty()) {
                Map<Integer, EntityMudGeometryProjector.SpotProjection> projected =
                        projector.project(image, missing, collisionHeight);
                if (projected == null) {
                    return null;
                }
                for (ClientEntityMudCoverage.SpotView spot : missing) {
                    EntityMudGeometryProjector.SpotProjection fresh =
                            projected.get(spot.id());
                    if (fresh == null) {
                        continue;
                    }
                    EntityMudGeometryProjector.SpotProjection previous =
                            entry.projections.get(spot.id());
                    entry.projections.put(spot.id(), previous == null
                            ? fresh
                            : EntityMudGeometryProjector.union(previous, fresh));
                    entry.projectionSignatures.put(
                            spot.id(), spot.geometrySignature());
                }
            }
            EntityMudPixelCompositor.paint(image, view, entry.projections);
            applySourceAlpha(image, sourceImage.alpha());
            entry.texture.upload();
            entry.texture.setFilter(false, false);
            entry.visualSignature = visualSignature;
        }
        return entry.location;
    }

    @FunctionalInterface
    private interface ProjectionFactory {
        Map<Integer, EntityMudGeometryProjector.SpotProjection> project(
                NativeImage image,
                List<ClientEntityMudCoverage.SpotView> missing,
                float collisionHeight);
    }

    public static void tick(Minecraft minecraft) {
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.ENTITY_COVERAGE)) {
            if (!CACHE.isEmpty() || !SOURCE_IMAGES.isEmpty()) {
                reset();
            }
            return;
        }
        if (++pruneTicks >= PRUNE_INTERVAL_TICKS) {
            pruneTicks = 0;
            prune(minecraft, false);
        }
    }

    public static void reset() {
        for (Entry entry : CACHE.values()) {
            release(entry);
        }
        CACHE.clear();
        SOURCE_IMAGES.clear();
        pruneTicks = 0;
    }

    private static Entry create(
            UUID uuid, ResourceLocation sourceTexture, TextureSize size) {
        DynamicTexture texture = new DynamicTexture(
                size.width(), size.height(), true);
        texture.setFilter(false, false);
        ResourceLocation location = Minecraft.getInstance().getTextureManager()
                .register("mirebound_entity_mud", texture);
        return new Entry(uuid, sourceTexture, size, texture, location);
    }

    private static SourceImage sourceImage(
            Minecraft minecraft, ResourceLocation sourceTexture) {
        if (sourceTexture == null) {
            return SourceImage.FALLBACK;
        }
        SourceImage cached = SOURCE_IMAGES.get(sourceTexture);
        if (cached != null) {
            return cached;
        }
        SourceImage resolved = SourceImage.FALLBACK;
        try {
            var resource = minecraft.getResourceManager().getResource(sourceTexture);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open();
                        NativeImage image = BoundedResourceReader.readImage(stream)) {
                    TextureSize size = boundedSize(
                            image.getWidth(), image.getHeight());
                    resolved = new SourceImage(size, sampleAlpha(image, size));
                }
            }
        } catch (Exception ignored) {
            // Dynamic or renderer-owned textures may not be resource-pack files.
        }
        if (SOURCE_IMAGES.size() >= MAXIMUM_SOURCE_IMAGES) {
            ResourceLocation oldest = SOURCE_IMAGES.keySet().iterator().next();
            SOURCE_IMAGES.remove(oldest);
        }
        SOURCE_IMAGES.put(sourceTexture, resolved);
        return resolved;
    }

    private static byte[] sampleAlpha(NativeImage source, TextureSize target) {
        byte[] alpha = new byte[target.width() * target.height()];
        for (int y = 0; y < target.height(); y++) {
            int sourceY = Math.min(source.getHeight() - 1,
                    y * source.getHeight() / target.height());
            for (int x = 0; x < target.width(); x++) {
                int sourceX = Math.min(source.getWidth() - 1,
                        x * source.getWidth() / target.width());
                alpha[x + y * target.width()] = (byte) (
                        source.getPixelRGBA(sourceX, sourceY) >>> 24);
            }
        }
        return alpha;
    }

    private static void applySourceAlpha(NativeImage image, byte[] sourceAlpha) {
        if (sourceAlpha == null
                || sourceAlpha.length != image.getWidth() * image.getHeight()) {
            return;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixelRGBA(x, y);
                int alpha = maskedAlpha(
                        pixel >>> 24,
                        Byte.toUnsignedInt(sourceAlpha[x + y * image.getWidth()]));
                image.setPixelRGBA(x, y,
                        pixel & 0x00FFFFFF | alpha << 24);
            }
        }
    }

    static int maskedAlpha(int overlayAlpha, int sourceAlpha) {
        int overlay = Math.max(0, Math.min(255, overlayAlpha));
        int source = Math.max(0, Math.min(255, sourceAlpha));
        return (overlay * source + 127) / 255;
    }

    static TextureSize boundedSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return TextureSize.FALLBACK;
        }
        float scale = Math.max(1.0F,
                Math.max(width, height) / (float) MAXIMUM_TEXTURE_DIMENSION);
        return new TextureSize(
                Math.max(1, Math.round(width / scale)),
                Math.max(1, Math.round(height / scale)));
    }

    private static void prune(Minecraft minecraft, boolean makeRoom) {
        var iterator = CACHE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Entry> stored = iterator.next();
            Entry entry = stored.getValue();
            var entity = minecraft.level == null
                    ? null : minecraft.level.getEntity(stored.getKey());
            boolean stale = !(entity instanceof LivingEntity living)
                    || living.isRemoved() || !entry.uuid.equals(living.getUUID())
                    || living.tickCount - entry.lastSeenTick > UNUSED_TICKS;
            if (!stale) {
                continue;
            }
            iterator.remove();
            release(entry);
            if (makeRoom && CACHE.size() < MAXIMUM_TEXTURES) {
                return;
            }
        }
    }

    private static void release(Entry entry) {
        Minecraft.getInstance().getTextureManager().release(entry.location);
    }

    private static final class Entry {
        private final UUID uuid;
        private final ResourceLocation sourceTexture;
        private final TextureSize size;
        private final DynamicTexture texture;
        private final ResourceLocation location;
        private final Map<Integer, EntityMudGeometryProjector.SpotProjection>
                projections = new HashMap<>();
        private final Map<Integer, Long> projectionSignatures = new HashMap<>();
        private final Set<Integer> activeProjectionIds = new HashSet<>();
        private final ArrayList<ClientEntityMudCoverage.SpotView> missingProjections =
                new ArrayList<>();
        private Object projectionGeometry;
        private float projectionCollisionHeight = Float.NaN;
        private long visualSignature = Long.MIN_VALUE;
        private int lastSeenTick;

        private Entry(UUID uuid, ResourceLocation sourceTexture, TextureSize size,
                DynamicTexture texture, ResourceLocation location) {
            this.uuid = uuid;
            this.sourceTexture = sourceTexture;
            this.size = size;
            this.texture = texture;
            this.location = location;
        }
    }

    record TextureSize(int width, int height) {
        private static final TextureSize FALLBACK = new TextureSize(
                FALLBACK_TEXTURE_SIZE, FALLBACK_TEXTURE_SIZE);
    }

    private record SourceImage(TextureSize size, byte[] alpha) {
        private static final SourceImage FALLBACK = new SourceImage(
                TextureSize.FALLBACK, null);
    }
}
