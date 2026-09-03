package com.fish.mirebound.client;

import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCoverageRules;
import com.fish.mirebound.mud.MudCoveragePatternSeed;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;

final class ArmorMudTextureCache {
    private static final int WIDTH = 64;
    private static final int HEIGHT = 32;
    private static final int MAX_ENTRIES = 512;
    private static final long UNUSED_TICKS = 400L;
    private static final Map<Key, Entry> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, Entry> BY_LOCATION = new HashMap<>();
    private static long lastPruneTick = Long.MIN_VALUE;

    private ArmorMudTextureCache() {
    }

    static ResourceLocation textureFor(int entityId, EquipmentSlot slot, MudBodyPart part, ArmorMudData data,
            long gameTime) {
        final boolean[] visible = {false};
        data.forEach((cell, coverage, medium) -> visible[0] |= MudSurfaceLayout.part(cell) == part && coverage > 0.001F);
        if (!visible[0]) {
            return null;
        }

        Key key = new Key(entityId, slot, part);
        Entry entry = CACHE.get(key);
        if (entry == null) {
            evictOldestIfFull();
            entry = createEntry();
            CACHE.put(key, entry);
        }
        int signature = 31 * (31 * data.hashCode() + part.ordinal())
                + Long.hashCode(animationSignature(data, part, gameTime));
        if (entry.signature != signature) {
            rebuild(entry, slot, part, data);
            entry.signature = signature;
        }
        entry.lastSeenTick = gameTime;
        prune(gameTime);
        return entry.location;
    }

    static boolean owns(ResourceLocation location) {
        return BY_LOCATION.containsKey(location);
    }

    static int pixel(ResourceLocation location, int x, int y) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) {
            return 0;
        }
        Entry entry = BY_LOCATION.get(location);
        return entry == null || entry.texture.getPixels() == null
                ? 0
                : entry.texture.getPixels().getPixelRGBA(x, y);
    }

    static void reset() {
        for (Entry entry : CACHE.values()) {
            entry.close();
        }
        CACHE.clear();
        BY_LOCATION.clear();
        lastPruneTick = Long.MIN_VALUE;
    }

    private static long animationSignature(ArmorMudData data, MudBodyPart targetPart, long gameTime) {
        final long[] mediumMask = {0L};
        final long[] appearance = {0L};
        data.forEachVisual((cell, coverage, medium, visualSource) -> {
            if (coverage > 0.001F && MudSurfaceLayout.part(cell) == targetPart) {
                mediumMask[0] |= MudSkinTextureCache.mediumBit(medium);
                int revision = AdaptiveMudClientCache.appearanceRevision(
                        Minecraft.getInstance().level, visualSource);
                if (revision != 0) {
                    appearance[0] = (appearance[0] * 31L
                            + Long.hashCode(visualSource)) * 31L
                            + Integer.toUnsignedLong(revision);
                }
            }
        });
        long animation = MudSkinTextureCache.skinCoverageAnimationSignature(
                mediumMask[0], gameTime);
        return appearance[0] == 0L ? animation : appearance[0] * 31L + animation;
    }

    private static Entry createEntry() {
        DynamicTexture texture = new DynamicTexture(WIDTH, HEIGHT, true);
        texture.setFilter(false, false);
        ResourceLocation location = Minecraft.getInstance().getTextureManager()
                .register("mirebound_armor_mud", texture);
        Entry entry = new Entry(texture, location);
        BY_LOCATION.put(location, entry);
        return entry;
    }

    private static void rebuild(Entry entry, EquipmentSlot slot, MudBodyPart targetPart, ArmorMudData data) {
        NativeImage image = entry.texture.getPixels();
        if (image == null) {
            image = new NativeImage(WIDTH, HEIGHT, true);
            entry.texture.setPixels(image);
        }
        image.fillRect(0, 0, WIDTH, HEIGHT, 0);
        ArmorMudPaintPlan plan = ArmorMudPaintPlan.build(data, targetPart);
        for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
            MudBodyPart part = MudSurfaceLayout.part(cell);
            float coverage = plan.coverage(cell);
            if (part != targetPart || coverage <= 0.001F) {
                continue;
            }
            SinkingMedium medium = plan.medium(cell);
            long visualSource = plan.visualSource(cell);
            int ownedIndex = ArmorMudManager.ownedCellIndex(slot, cell);
            if (!MudCoverageAppearance.allowsCoveragePixel(
                    medium,
                    MudCoverageRules.armorDomain(ArmorMudManager.armorSlotIndex(slot)),
                    ownedIndex,
                    ArmorMudManager.ownedCellCount(slot))) {
                continue;
            }
            MudSurface surface = MudSurfaceLayout.surface(cell);
            TexturePixel uv = texturePixel(
                    part,
                    surface,
                    MudSurfaceLayout.row(cell),
                    MudSurfaceLayout.column(cell));
            int salt = MudCoveragePatternSeed.mix(
                    cell * 31 + part.ordinal() * 101,
                    data.coveragePatternSeed());
            int alpha = Math.round(255.0F * Mth.clamp(coverage, 0.0F, 1.0F)
                    * MudCoverageAppearance.opacityScale(medium, uv.x, uv.y, salt));
            int textureX = MudSkinTextureCache.adaptiveCoverageSampleX(
                    uv.x, visualSource, part.ordinal())
                    + MudCoveragePatternSeed.sampleOffsetX(
                            data.coveragePatternSeed());
            int textureY = MudSkinTextureCache.adaptiveCoverageSampleY(
                    uv.y, visualSource, part.ordinal())
                    + MudCoveragePatternSeed.sampleOffsetY(
                            data.coveragePatternSeed());
            int color = MudSkinTextureCache.skinCoverageTextureAbgr(
                    medium, visualSource, textureX, textureY, salt, alpha);
            int existing = image.getPixelRGBA(uv.x, uv.y);
            if (FastColor.ABGR32.alpha(color) >= FastColor.ABGR32.alpha(existing)) {
                image.setPixelRGBA(uv.x, uv.y, color);
            }
        }
        entry.texture.upload();
        entry.texture.setFilter(false, false);
    }

    private static TexturePixel texturePixel(MudBodyPart part, MudSurface surface, int row, int column) {
        CubeUv cube = cubeUv(part);
        int x;
        int y;
        int uvColumn = (surface == MudSurface.LEFT || surface == MudSurface.BACK)
                ? faceWidth(part, surface) - 1 - column
                : column;
        int uvRow = surface == MudSurface.TOP || surface == MudSurface.BOTTOM
                ? row
                : faceHeight(part, surface) - 1 - row;
        switch (surface) {
            case TOP -> {
                x = cube.x + cube.depth + uvColumn;
                y = cube.y + uvRow;
            }
            case BOTTOM -> {
                x = cube.x + cube.depth + cube.width + uvColumn;
                y = cube.y + uvRow;
            }
            case RIGHT -> {
                x = cube.x + uvColumn;
                y = cube.y + cube.depth + uvRow;
            }
            case FRONT -> {
                x = cube.x + cube.depth + uvColumn;
                y = cube.y + cube.depth + uvRow;
            }
            case LEFT -> {
                x = cube.x + cube.depth + cube.width + uvColumn;
                y = cube.y + cube.depth + uvRow;
            }
            case BACK -> {
                x = cube.x + cube.depth * 2 + cube.width + uvColumn;
                y = cube.y + cube.depth + uvRow;
            }
            default -> throw new IllegalStateException("Unexpected surface " + surface);
        }
        return new TexturePixel(x, y);
    }

    private static CubeUv cubeUv(MudBodyPart part) {
        return switch (part) {
            case HEAD -> new CubeUv(0, 0, 8, 8, 8);
            case BODY -> new CubeUv(16, 16, 8, 12, 4);
            case LEFT_ARM, RIGHT_ARM -> new CubeUv(40, 16, 4, 12, 4);
            case LEFT_LEG, RIGHT_LEG -> new CubeUv(0, 16, 4, 12, 4);
        };
    }

    private static int faceWidth(MudBodyPart part, MudSurface surface) {
        return MudSurfaceLayout.face(part, surface).width();
    }

    private static int faceHeight(MudBodyPart part, MudSurface surface) {
        return MudSurfaceLayout.face(part, surface).height();
    }

    private static void prune(long gameTime) {
        long elapsed = gameTime - lastPruneTick;
        if (lastPruneTick != Long.MIN_VALUE && elapsed >= 0L && elapsed < 100L) {
            return;
        }
        lastPruneTick = gameTime;
        Iterator<Entry> iterator = CACHE.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            long age = gameTime - entry.lastSeenTick;
            if (age > UNUSED_TICKS || age < 0L) {
                BY_LOCATION.remove(entry.location);
                entry.close();
                iterator.remove();
            }
        }
    }

    private static void evictOldestIfFull() {
        while (CACHE.size() >= MAX_ENTRIES) {
            Key oldestKey = null;
            Entry oldest = null;
            for (Map.Entry<Key, Entry> candidate : CACHE.entrySet()) {
                if (oldest == null || candidate.getValue().lastSeenTick < oldest.lastSeenTick) {
                    oldestKey = candidate.getKey();
                    oldest = candidate.getValue();
                }
            }
            if (oldestKey == null) {
                return;
            }
            CACHE.remove(oldestKey);
            BY_LOCATION.remove(oldest.location);
            oldest.close();
        }
    }

    private record Key(int entityId, EquipmentSlot slot, MudBodyPart part) {
    }

    private record CubeUv(int x, int y, int width, int height, int depth) {
    }

    private record TexturePixel(int x, int y) {
    }

    private static final class Entry {
        private final DynamicTexture texture;
        private final ResourceLocation location;
        private int signature = Integer.MIN_VALUE;
        private long lastSeenTick;

        private Entry(DynamicTexture texture, ResourceLocation location) {
            this.texture = texture;
            this.location = location;
        }

        private void close() {
            DynamicTextureLifecycle.release(location, texture);
        }
    }
}
