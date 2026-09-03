package com.fish.mirebound.stain;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.compat.sable.SableCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MudFootprintBlockEntity extends BlockEntity {
    private static final int MAX_ENTRIES_PER_BLOCK = 32;
    private static final int WALL_GRID_SIZE = 16;
    private static final int MAX_WALL_PIXELS = WALL_GRID_SIZE * WALL_GRID_SIZE;
    private static final long WALL_PIXEL_CREATED_MASK = 0xFFFFFFL;
    private static final long WALL_PIXEL_TIMED_FLAG = 1L << 48;
    private static final int WALL_PIXEL_SECONDARY_MEDIUM_SHIFT = 49;
    private static final int WALL_PIXEL_SECONDARY_WEIGHT_SHIFT = 57;
    private static final long WALL_PIXEL_SECONDARY_MEDIUM_MASK = 0xFFL << WALL_PIXEL_SECONDARY_MEDIUM_SHIFT;
    private static final long WALL_PIXEL_SECONDARY_WEIGHT_MASK = 0x7FL << WALL_PIXEL_SECONDARY_WEIGHT_SHIFT;
    private static final long WALL_PIXEL_SECONDARY_DATA_MASK =
            WALL_PIXEL_SECONDARY_MEDIUM_MASK | WALL_PIXEL_SECONDARY_WEIGHT_MASK;
    private static final long[] NO_WALL_PIXELS = new long[0];
    private static final int RAIN_WASH_UPDATE_TICKS = 10;
    private static final float WATER_GUN_WALL_RADIUS_SCALE = 1.25F;
    private static final float WATER_GUN_WALL_AMOUNT_SCALE = 1.50F;
    private final List<Entry> entries = new ArrayList<>(MAX_ENTRIES_PER_BLOCK);

    public MudFootprintBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MUD_FOOTPRINT_ENTITY.get(), pos, state);
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean hasPreciseWallStain(Direction face) {
        for (Entry entry : entries) {
            if (entry.wallStain() && entry.face() == face && entry.wallPixels().length > 0) {
                return true;
            }
        }
        return false;
    }

    public long[] preciseWallPixels(Direction face) {
        for (Entry entry : entries) {
            if (entry.wallStain() && entry.face() == face && entry.wallPixels().length > 0) {
                return entry.wallPixels();
            }
        }
        return NO_WALL_PIXELS;
    }

    public long[] preciseWallPixels(Direction face, SinkingMedium medium,
            long visualSource) {
        for (Entry entry : entries) {
            if (samePreciseLayer(entry, face, medium, visualSource)) {
                return entry.wallPixels();
            }
        }
        return NO_WALL_PIXELS;
    }

    boolean hasPreciseWallPixel(Direction face, int cell) {
        for (Entry entry : entries) {
            if (!isPreciseWallStain(entry) || entry.face() != face) {
                continue;
            }
            for (long pixel : entry.wallPixels()) {
                if (((int) pixel & 0xFF) == cell) {
                    return true;
                }
            }
        }
        return false;
    }

    public long preciseWallVisualSource(Direction face) {
        for (Entry entry : entries) {
            if (entry.wallStain() && entry.face() == face
                    && entry.wallPixels().length > 0) {
                return entry.visualSource();
            }
        }
        return 0L;
    }

    public void protectPreciseWallStainFromExpansionEviction(ServerLevel level, Direction face) {
        for (Entry entry : entries) {
            if (entry.wallStain() && entry.face() == face && entry.wallPixels().length > 0) {
                MudFootprintLedger.get(level).refresh(level, entry.id(), worldPosition, entry.expiresAt());
                return;
            }
        }
    }

    List<Entry> mutableEntries() {
        return entries;
    }

    public boolean add(ServerLevel level, float localX, float localY, float localZ, float yawDegrees,
            float strength, SinkingMedium medium) {
        return addSurfaceFootprint(level, localX, localY, localZ, yawDegrees, Direction.UP, strength, medium);
    }

    public boolean addSurfaceFootprint(ServerLevel level, float localX, float localY, float localZ,
            float rotationDegrees, Direction face, float strength, SinkingMedium medium) {
        return addSurfaceFootprint(level, localX, localY, localZ, rotationDegrees, face,
                0.25F, strength, medium);
    }

    public boolean addSurfaceFootprint(ServerLevel level, float localX, float localY, float localZ,
            float rotationDegrees, Direction face, float size, float strength, SinkingMedium medium) {
        return addSurfaceFootprint(level, localX, localY, localZ,
                rotationDegrees, face, size, strength, medium, 0L);
    }

    public boolean addSurfaceFootprint(ServerLevel level, float localX, float localY, float localZ,
            float rotationDegrees, Direction face, float size, float strength,
            SinkingMedium medium, long visualSource) {
        return addDecal(level, localX, localY, localZ, rotationDegrees, face, false,
                size, size, strength, medium, visualSource, NO_WALL_PIXELS);
    }

    public boolean addWallStain(ServerLevel level, float localX, float localY, float localZ, float rotationDegrees,
            Direction face, float width, float height, float strength, SinkingMedium medium) {
        if (!face.getAxis().isHorizontal()) {
            return false;
        }
        return addDecal(level, localX, localY, localZ, rotationDegrees, face, true,
                width, height, strength, medium, 0L, NO_WALL_PIXELS);
    }

    public boolean addPreciseWallStain(ServerLevel level, float localX, float localY, float localZ, Direction face,
            long[] wallPixels, float strength, SinkingMedium medium) {
        return addPreciseWallStain(level, localX, localY, localZ, face,
                wallPixels, strength, medium, 0L);
    }

    public boolean addPreciseWallStain(ServerLevel level, float localX, float localY, float localZ, Direction face,
            long[] wallPixels, float strength, SinkingMedium medium,
            long visualSource) {
        if (wallPixels.length == 0) {
            return false;
        }
        MudFootprintLedger ledger = MudFootprintLedger.get(level);
        Entry precise = null;
        boolean removedLegacyLayer = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry existing = entries.get(i);
            if (!existing.wallStain() || existing.face() != face) {
                continue;
            }
            if (samePreciseLayer(existing, face, medium, visualSource)
                    && precise == null) {
                precise = existing;
            } else if (existing.wallPixels().length == 0) {
                entries.remove(i);
                ledger.unregister(existing.id());
                removedLegacyLayer = true;
            }
        }
        if (precise != null) {
            long[] existingPixels = precise.fade() < 0.999F
                    ? scaleWallPixels(precise.wallPixels(), precise.fade())
                    : precise.wallPixels();
            long[] merged = mergeWallPixels(existingPixels, wallPixels);
            boolean pixelsChanged = !Arrays.equals(precise.wallPixels(), merged);
            long now = level.getGameTime();
            boolean refreshLifetime = shouldRefreshWallLifetime(
                    pixelsChanged, precise.expiresAt(), now);
            int index = entries.indexOf(precise);
            if (pixelsChanged || refreshLifetime) {
                long expiresAt = Math.max(precise.expiresAt(), now + MudPhysicsSettings.footprintLifetimeTicks());
                entries.set(index, precise.withPreciseWallPixels(
                        merged,
                        Math.max(precise.strength(), strength),
                        medium,
                        visualSource,
                        expiresAt));
                ledger.refresh(level, precise.id(), worldPosition, expiresAt);
                if (pixelsChanged || removedLegacyLayer) {
                    sync();
                } else {
                    setChanged();
                }
                scheduleNext(level, 1);
            } else if (removedLegacyLayer) {
                sync();
            }
            return true;
        }
        float anchorY = face == Direction.DOWN ? localY : 0.5F;
        return addDecal(level, localX, anchorY, localZ, 0.0F, face, true,
                1.0F, 1.0F, strength, medium, visualSource, wallPixels);
    }

    private boolean addDecal(ServerLevel level, float localX, float localY, float localZ, float rotationDegrees,
            Direction face, boolean wallStain, float width, float height, float strength, SinkingMedium medium,
            long visualSource, long[] wallPixels) {
        MudFootprintLedger ledger = MudFootprintLedger.get(level);
        if (MudPhysicsSettings.maximumFootprints() <= 0) {
            return false;
        }
        if (entries.size() >= MAX_ENTRIES_PER_BLOCK) {
            Entry removed = entries.remove(oldestReplaceableEntryIndex(entries));
            ledger.unregister(removed.id());
        }

        long created = level.getGameTime();
        long expiresAt = created + MudPhysicsSettings.footprintLifetimeTicks();
        long id = ledger.allocate(level, worldPosition, expiresAt);
        entries.add(new Entry(
                id,
                Mth.clamp(localX, -0.25F, 1.25F),
                Mth.clamp(localY, -1.0F, 1.5F),
                Mth.clamp(localZ, -0.25F, 1.25F),
                Mth.wrapDegrees(rotationDegrees),
                face,
                wallStain,
                Mth.clamp(width, 0.125F, 0.80F),
                Mth.clamp(height, 0.125F, 1.25F),
                Mth.clamp(strength, 0.0F, 1.0F),
                medium,
                visualSource,
                wallPixels,
                created,
                expiresAt,
                1.0F));
        ledger.enforceLimit(level.getServer(), created);
        sync();
        scheduleNext(level, 1);
        return true;
    }

    static int oldestReplaceableEntryIndex(List<Entry> entries) {
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (!entry.wallStain() || entry.wallPixels().length == 0) {
                return index;
            }
        }
        return 0;
    }

    public boolean washFromWaterGun(ServerLevel level, net.minecraft.world.phys.Vec3 impact,
            float radius, float amount) {
        if (radius <= 0.0F || amount <= 0.0F || entries.isEmpty()) {
            return false;
        }
        MudFootprintLedger ledger = MudFootprintLedger.get(level);
        boolean changed = false;
        float wallRadius = radius * WATER_GUN_WALL_RADIUS_SCALE;
        float wallRadiusSqr = wallRadius * wallRadius;
        int salt = (int) (level.getGameTime() ^ worldPosition.asLong());
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            if (entry.wallStain() && entry.wallPixels().length > 0) {
                long[] washed = washWallPixelsInSphere(
                        entry, impact, wallRadius, wallRadiusSqr,
                        amount * WATER_GUN_WALL_AMOUNT_SCALE, salt);
                if (Arrays.equals(washed, entry.wallPixels())) {
                    continue;
                }
                changed = true;
                if (washed.length == 0) {
                    entries.remove(index);
                    ledger.unregister(entry.id());
                } else {
                    entries.set(index, entry.withWashedWallPixels(washed, maximumWallPixelStrength(washed)));
                }
                continue;
            }

            net.minecraft.world.phys.Vec3 center = new net.minecraft.world.phys.Vec3(
                    worldPosition.getX() + entry.localX(),
                    worldPosition.getY() + entry.localY(),
                    worldPosition.getZ() + entry.localZ());
            float footprintReach = Math.max(entry.width(), entry.height()) * 0.50F;
            double distance = Math.sqrt(center.distanceToSqr(impact));
            float effectiveRadius = entry.wallStain() ? wallRadius : radius;
            float effectiveAmount = entry.wallStain()
                    ? amount * WATER_GUN_WALL_AMOUNT_SCALE : amount;
            if (distance > effectiveRadius + footprintReach) {
                continue;
            }
            float falloff = Mth.clamp(
                    1.0F - (float) Math.max(0.0D, distance - footprintReach) / effectiveRadius,
                    0.18F, 1.0F);
            float nextFade = Math.max(0.0F, entry.fade() - effectiveAmount * falloff);
            changed = true;
            if (nextFade <= 0.01F) {
                entries.remove(index);
                ledger.unregister(entry.id());
            } else {
                entries.set(index, entry.withFade(nextFade));
            }
        }
        if (!changed) {
            return false;
        }
        if (entries.isEmpty()) {
            removeContainer(level);
        } else {
            sync();
            scheduleNext(level, 2);
        }
        return true;
    }

    private long[] washWallPixelsInSphere(Entry entry, net.minecraft.world.phys.Vec3 impact,
            float radius, float radiusSqr, float amount, int salt) {
        long[] washed = new long[entry.wallPixels().length];
        int count = 0;
        boolean changed = false;
        for (long pixel : entry.wallPixels()) {
            float horizontal = (wallPixelHorizontal(pixel) + 0.5F) / WALL_GRID_SIZE;
            float vertical = (wallPixelVertical(pixel) + 0.5F) / WALL_GRID_SIZE;
            net.minecraft.world.phys.Vec3 point = wallPixelWorldPoint(entry, horizontal, vertical);
            double distanceSqr = point.distanceToSqr(impact);
            if (distanceSqr > radiusSqr) {
                washed[count++] = pixel;
                continue;
            }
            float distance = Mth.sqrt((float) distanceSqr);
            float falloff = Mth.clamp(1.0F - distance / radius, 0.18F, 1.0F);
            int cell = wallPixelHorizontal(pixel) | wallPixelVertical(pixel) << 4;
            float variation = 0.82F + unitNoise(mix(entry.id() ^ cell * 0x9e3779b97f4a7c15L ^ salt)) * 0.18F;
            float strength = Math.max(0.0F, wallPixelStrength(pixel) - amount * falloff * variation);
            if (strength > 0.01F) {
                long next = repackWallPixel(pixel, strength, wallPixelMedium(pixel));
                washed[count++] = next;
                changed |= next != pixel;
            } else {
                changed = true;
            }
        }
        return changed ? Arrays.copyOf(washed, count) : entry.wallPixels();
    }

    private net.minecraft.world.phys.Vec3 wallPixelWorldPoint(Entry entry, float horizontal, float vertical) {
        double x = worldPosition.getX();
        double y = worldPosition.getY();
        double z = worldPosition.getZ();
        return switch (entry.face().getAxis()) {
            case X -> new net.minecraft.world.phys.Vec3(x + entry.localX(), y + vertical, z + horizontal);
            case Y -> new net.minecraft.world.phys.Vec3(x + horizontal, y + entry.localY(), z + vertical);
            case Z -> new net.minecraft.world.phys.Vec3(x + horizontal, y + vertical, z + entry.localZ());
        };
    }

    void serverCheck(ServerLevel level) {
        if (!getBlockState().canSurvive(level, worldPosition)) {
            removeContainer(level);
            return;
        }

        MudFootprintLedger ledger = MudFootprintLedger.get(level);
        boolean changed = consolidatePreciseStains(ledger);
        long gameTime = level.getGameTime();
        Iterator<Entry> supportIterator = entries.iterator();
        while (supportIterator.hasNext()) {
            Entry entry = supportIterator.next();
            BlockPos supportPos = worldPosition.relative(entry.face().getOpposite());
            BlockState support = level.getBlockState(supportPos);
            if (!MudFootprintBlock.isValidSupport(support, level, supportPos)) {
                supportIterator.remove();
                ledger.unregister(entry.id());
                changed = true;
            }
        }
        if (!MudPhysicsSettings.footprintPermanent()) {
            for (int i = entries.size() - 1; i >= 0; i--) {
                Entry entry = entries.get(i);
                if (entry.wallStain() && entry.wallPixels().length > 0) {
                    long[] active = removeExpiredWallPixels(entry.wallPixels(), gameTime);
                    if (active.length == 0) {
                        entries.remove(i);
                        ledger.unregister(entry.id());
                        changed = true;
                    } else if (!Arrays.equals(active, entry.wallPixels())) {
                        entries.set(i, entry.withFlowPixels(active));
                        changed = true;
                    }
                } else if (entry.expiresAt() <= gameTime) {
                    entries.remove(i);
                    ledger.unregister(entry.id());
                    changed = true;
                }
            }
        }

        boolean raining = MudPhysicsSettings.footprintRainWash()
                && level.isRaining() && !entries.isEmpty();
        boolean rainExposure = false;
        if (raining) {
            float wash = MudPhysicsSettings.footprintRainWashStep() * RAIN_WASH_UPDATE_TICKS / 20.0F;
            for (int i = entries.size() - 1; i >= 0; i--) {
                Entry entry = entries.get(i);
                if (!rainReaches(level, entry)) {
                    continue;
                }
                rainExposure = true;
                if (entry.wallStain() && entry.wallPixels().length > 0) {
                    long[] normalized = entry.fade() < 0.999F
                            ? scaleWallPixels(entry.wallPixels(), entry.fade())
                            : entry.wallPixels();
                    long[] washed = washWallPixels(normalized, wash, entry.id(), gameTime);
                    if (Arrays.equals(washed, entry.wallPixels())) {
                        continue;
                    }
                    changed = true;
                    if (washed.length == 0) {
                        entries.remove(i);
                        ledger.unregister(entry.id());
                    } else {
                        entries.set(i, entry.withWashedWallPixels(washed, maximumWallPixelStrength(washed)));
                    }
                    continue;
                }
                float fade = Math.max(0.0F, entry.fade() - wash);
                if (fade >= entry.fade()) {
                    continue;
                }
                changed = true;
                if (fade <= 0.001F) {
                    entries.remove(i);
                    ledger.unregister(entry.id());
                } else {
                    entries.set(i, entry.withFade(fade));
                }
            }
        }

        boolean hasPreciseStain = hasPreciseStain();
        if (hasPreciseStain && !SableCompat.containsBlockEntity(this)
                && flowPreciseStains(level, gameTime)) {
            changed = true;
        }
        ledger.enforceLimit(level.getServer(), gameTime);
        if (entries.isEmpty()) {
            removeContainer(level);
            return;
        }
        if (changed) {
            sync();
        }
        int nextDelay = hasPreciseStain ? MudPhysicsSettings.wallStainDripIntervalTicks() : 100;
        scheduleNext(level, rainExposure
                ? Math.min(RAIN_WASH_UPDATE_TICKS, nextDelay) : nextDelay);
    }

    boolean removeEntriesSupportedBy(ServerLevel level, BlockPos supportPos) {
        MudFootprintLedger ledger = MudFootprintLedger.get(level);
        boolean changed = false;
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (!worldPosition.relative(entry.face().getOpposite()).equals(supportPos)) {
                continue;
            }
            iterator.remove();
            ledger.unregister(entry.id());
            changed = true;
        }
        if (!changed) {
            return false;
        }
        if (entries.isEmpty()) {
            removeContainer(level);
        } else {
            sync();
        }
        return true;
    }

    private boolean consolidatePreciseStains(MudFootprintLedger ledger) {
        boolean changed = false;
        for (int keeperIndex = 0; keeperIndex < entries.size(); keeperIndex++) {
            Entry keeper = entries.get(keeperIndex);
            if (!isPreciseWallStain(keeper)) {
                continue;
            }
            for (int index = keeperIndex + 1; index < entries.size();) {
                Entry entry = entries.get(index);
                if (!samePreciseLayer(entry, keeper.face(), keeper.medium(),
                        keeper.visualSource())) {
                    index++;
                    continue;
                }
                long[] keeperPixels = keeper.fade() < 0.999F
                        ? scaleWallPixels(keeper.wallPixels(), keeper.fade())
                        : keeper.wallPixels();
                long[] incomingPixels = entry.fade() < 0.999F
                        ? scaleWallPixels(entry.wallPixels(), entry.fade())
                        : entry.wallPixels();
                long[] merged = mergeWallPixels(keeperPixels, incomingPixels);
                float nextStrength = Math.max(keeper.strength(), entry.strength());
                keeper = keeper.withPreciseWallPixels(
                        merged, nextStrength, keeper.medium(), keeper.visualSource(),
                        Math.max(keeper.expiresAt(), entry.expiresAt()));
                entries.set(keeperIndex, keeper);
                entries.remove(index);
                ledger.unregister(entry.id());
                changed = true;
            }
        }
        for (Direction face : Direction.values()) {
            boolean hasPrecise = false;
            for (Entry entry : entries) {
                hasPrecise |= isPreciseWallStain(entry) && entry.face() == face;
            }
            if (!hasPrecise) {
                continue;
            }
            for (int index = entries.size() - 1; index >= 0; index--) {
                Entry entry = entries.get(index);
                if (entry.wallStain() && entry.face() == face
                        && entry.wallPixels().length == 0) {
                    entries.remove(index);
                    ledger.unregister(entry.id());
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean isPreciseWallStain(Entry entry) {
        return entry.wallStain() && entry.wallPixels().length > 0;
    }

    static boolean samePreciseLayer(Entry entry, Direction face,
            SinkingMedium medium, long visualSource) {
        return isPreciseWallStain(entry)
                && entry.face() == face
                && entry.medium() == medium
                && entry.visualSource() == visualSource;
    }

    private boolean hasPreciseStain() {
        for (Entry entry : entries) {
            if (entry.wallStain() && entry.wallPixels().length > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean flowPreciseStains(ServerLevel level, long gameTime) {
        boolean anyChanged = false;
        MudFootprintLedger ledger = MudFootprintLedger.get(level);
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            Entry entry = entries.get(entryIndex);
            if (!entry.wallStain() || entry.wallPixels().length == 0
                    || entry.face() != Direction.DOWN) {
                continue;
            }
            long[] byCell = new long[WALL_GRID_SIZE * WALL_GRID_SIZE];
            for (long pixel : entry.wallPixels()) {
                byCell[(int) pixel & 0xFF] = pixel;
            }
            int flows = 0;
            boolean entryChanged = false;
            for (int cell = 0; cell < byCell.length && flows < 2; cell++) {
                long pixel = byCell[cell];
                if (pixel == 0L || flowNoise(entry.id(), cell, gameTime)
                        >= MudPhysicsSettings.wallStainDripChance()) {
                    continue;
                }
                spawnDripParticle(level, entry, pixel);
                float strength = wallPixelStrength(pixel) * MudPhysicsSettings.wallStainDripRetention();
                byCell[cell] = strength > 0.055F
                        ? repackWallPixel(pixel, strength, wallPixelMedium(pixel))
                        : 0L;
                flows++;
                entryChanged = true;
            }
            if (!entryChanged) {
                continue;
            }
            long[] compact = compactWallPixels(byCell);
            if (compact.length == 0) {
                entries.remove(entryIndex--);
                ledger.unregister(entry.id());
            } else {
                entries.set(entryIndex, entry.withFlowPixels(compact));
            }
            anyChanged = true;
        }
        return anyChanged;
    }

    private void spawnDripParticle(ServerLevel level, Entry entry, long pixel) {
        float horizontal = (wallPixelHorizontal(pixel) + 0.5F) / WALL_GRID_SIZE;
        float vertical = (wallPixelVertical(pixel) + 0.5F) / WALL_GRID_SIZE;
        double x;
        double y;
        double z;
        if (entry.face() == Direction.NORTH || entry.face() == Direction.SOUTH) {
            x = worldPosition.getX() + horizontal;
            y = worldPosition.getY() + vertical;
            z = worldPosition.getZ() + entry.localZ();
        } else if (entry.face() == Direction.EAST || entry.face() == Direction.WEST) {
            x = worldPosition.getX() + entry.localX();
            y = worldPosition.getY() + vertical;
            z = worldPosition.getZ() + horizontal;
        } else {
            x = worldPosition.getX() + horizontal;
            y = worldPosition.getY() + entry.localY() - 0.018D;
            z = worldPosition.getZ() + vertical;
        }
        SinkingMedium medium = wallPixelMedium(pixel);
        DustParticleOptions dust = new DustParticleOptions(
                MudVisualSource.particleColor(entry.visualSource(), medium.particleColor()),
                medium.particleScale() * 0.62F);
        level.sendParticles(dust, x, y, z, 1, 0.015D, 0.012D, 0.015D, 0.025D);
    }

    private boolean rainReaches(ServerLevel level, Entry entry) {
        net.minecraft.world.phys.Vec3 point;
        if (entry.wallStain() && entry.wallPixels().length > 0) {
            long pixel = entry.wallPixels()[0];
            point = wallPixelWorldPoint(
                    entry,
                    (wallPixelHorizontal(pixel) + 0.5F) / WALL_GRID_SIZE,
                    (wallPixelVertical(pixel) + 0.5F) / WALL_GRID_SIZE);
        } else {
            point = new net.minecraft.world.phys.Vec3(
                    worldPosition.getX() + entry.localX(),
                    worldPosition.getY() + entry.localY(),
                    worldPosition.getZ() + entry.localZ());
        }
        net.minecraft.world.phys.Vec3 exposed = point.add(
                entry.face().getStepX() * 0.03D,
                entry.face().getStepY() * 0.03D,
                entry.face().getStepZ() * 0.03D);
        BlockPos probe = BlockPos.containing(exposed);
        if (level.isRainingAt(probe)) {
            return true;
        }
        return entry.face() == Direction.UP && level.isRainingAt(probe.above());
    }

    private static float flowNoise(long id, int cell, long gameTime) {
        long value = id ^ cell * 0x9e3779b97f4a7c15L ^ gameTime * 0xc2b2ae3d27d4eb4fL;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (value & 0xFFFFL) / 65535.0F;
    }

    void removeFromLedger(long id) {
        if (entries.removeIf(entry -> entry.id() == id)) {
            if (entries.isEmpty() && level instanceof ServerLevel serverLevel) {
                removeContainer(serverLevel);
            } else {
                sync();
            }
        }
    }

    void unregisterAll(ServerLevel level) {
        MudFootprintLedger.get(level).unregisterAll(entries);
    }

    private void removeContainer(ServerLevel level) {
        MudDecalAccess.removeContainer(level, SableCompat.containingSubLevel(this), worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            MudFootprintLedger.get(serverLevel).reconcileLoaded(serverLevel, worldPosition, this);
            if (entries.isEmpty()) {
                scheduleNext(serverLevel, 1);
            } else {
                scheduleNext(serverLevel, 20);
            }
        }
    }

    private void scheduleNext(ServerLevel level, int delay) {
        level.scheduleTick(worldPosition, ModBlocks.MUD_FOOTPRINT.get(), Math.max(1, delay));
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            SableCompat.syncSubLevelBlockEntity(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag value = new CompoundTag();
            value.putLong("Id", entry.id());
            value.putShort("X", quantizePosition(entry.localX()));
            value.putShort("Y", quantizePosition(entry.localY()));
            value.putShort("Z", quantizePosition(entry.localZ()));
            value.putByte("Yaw", quantizeYaw(entry.yawDegrees()));
            value.putByte("Face", (byte) entry.face().get3DDataValue());
            value.putBoolean("Wall", entry.wallStain());
            value.putShort("Width", quantizePosition(entry.width()));
            value.putShort("Height", quantizePosition(entry.height()));
            value.putByte("Strength", (byte) Mth.clamp(Math.round(entry.strength() * 255.0F), 0, 255));
            value.putByte("Medium", (byte) entry.medium().id());
            if (entry.visualSource() != 0L) {
                value.putLong("VisualSource", entry.visualSource());
            }
            if (entry.wallPixels().length > 0) {
                value.putLongArray("WallPixels", entry.wallPixels());
            }
            value.putLong("Created", entry.createdAt());
            value.putLong("Expires", entry.expiresAt());
            value.putByte("Fade", (byte) Mth.clamp(Math.round(entry.fade() * 255.0F), 0, 255));
            list.add(value);
        }
        tag.put("Footprints", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        entries.clear();
        ListTag list = tag.getList("Footprints", Tag.TAG_COMPOUND);
        int count = Math.min(MAX_ENTRIES_PER_BLOCK, list.size());
        for (int i = 0; i < count; i++) {
            CompoundTag value = list.getCompound(i);
            entries.add(new Entry(
                    value.getLong("Id"),
                    dequantizePosition(value.getShort("X")),
                    dequantizePosition(value.getShort("Y")),
                    dequantizePosition(value.getShort("Z")),
                    dequantizeYaw(value.getByte("Yaw")),
                    value.contains("Face") ? Direction.from3DDataValue(value.getByte("Face") & 0xFF) : Direction.UP,
                    value.getBoolean("Wall"),
                    value.contains("Width") ? dequantizePosition(value.getShort("Width")) : 0.25F,
                    value.contains("Height") ? dequantizePosition(value.getShort("Height")) : 0.25F,
                    value.contains("Strength") ? (value.getByte("Strength") & 0xFF) / 255.0F : 1.0F,
                    SinkingMedium.byId(value.getByte("Medium") & 0xFF),
                    value.getLong("VisualSource"),
                    loadWallPixels(value),
                    value.getLong("Created"),
                    value.getLong("Expires"),
                    (value.getByte("Fade") & 0xFF) / 255.0F));
        }
    }

    private static long[] loadWallPixels(CompoundTag value) {
        if (!value.contains("WallPixels", Tag.TAG_LONG_ARRAY)) {
            return NO_WALL_PIXELS;
        }
        long[] pixels = value.getLongArray("WallPixels");
        if (pixels.length <= MAX_WALL_PIXELS) {
            return pixels;
        }
        Mirebound.LOGGER.warn("Ignoring {} wall pixels beyond the per-face limit of {}",
                pixels.length - MAX_WALL_PIXELS, MAX_WALL_PIXELS);
        return Arrays.copyOf(pixels, MAX_WALL_PIXELS);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    private static short quantizePosition(float value) {
        return (short) Math.round(value * 256.0F);
    }

    private static float dequantizePosition(short value) {
        return value / 256.0F;
    }

    private static byte quantizeYaw(float value) {
        return (byte) Math.round(Mth.wrapDegrees(value) * 256.0F / 360.0F);
    }

    private static float dequantizeYaw(byte value) {
        return (value & 0xFF) * 360.0F / 256.0F;
    }

    public static long packWallPixel(int horizontalCell, int verticalCell, float strength, SinkingMedium medium) {
        return packWallPixel(horizontalCell, verticalCell, strength, medium, 0L, false);
    }

    public static long packWallPixel(int horizontalCell, int verticalCell, float strength, SinkingMedium medium,
            long createdAt) {
        return packWallPixel(horizontalCell, verticalCell, strength, medium, createdAt, true);
    }

    private static long packWallPixel(int horizontalCell, int verticalCell, float strength, SinkingMedium medium,
            long createdAt, boolean timed) {
        int cell = Mth.clamp(horizontalCell, 0, WALL_GRID_SIZE - 1)
                | Mth.clamp(verticalCell, 0, WALL_GRID_SIZE - 1) << 4;
        int packedStrength = Mth.clamp(Math.round(strength * 255.0F), 1, 255);
        return (cell & 0xFFL)
                | (packedStrength & 0xFFL) << 8
                | (medium.id() & 0xFFL) << 16
                | (createdAt & WALL_PIXEL_CREATED_MASK) << 24
                | (timed ? WALL_PIXEL_TIMED_FLAG : 0L);
    }

    public static int wallPixelHorizontal(long pixel) {
        return (int) pixel & 15;
    }

    public static int wallPixelVertical(long pixel) {
        return ((int) pixel >>> 4) & 15;
    }

    public static float wallPixelStrength(long pixel) {
        return ((pixel >>> 8) & 0xFFL) / 255.0F;
    }

    static boolean shouldRefreshWallLifetime(boolean pixelsChanged,
            long expiresAt, long now) {
        return pixelsChanged && expiresAt - now <= 20L;
    }

    public static SinkingMedium wallPixelMedium(long pixel) {
        return SinkingMedium.byId((int) ((pixel >>> 16) & 0xFFL));
    }

    public static boolean wallPixelHasCreationTime(long pixel) {
        return (pixel & WALL_PIXEL_TIMED_FLAG) != 0L;
    }

    public static int wallPixelCreatedAt(long pixel) {
        return (int) ((pixel >>> 24) & WALL_PIXEL_CREATED_MASK);
    }

    public static float wallPixelSecondaryWeight(long pixel) {
        return ((pixel >>> WALL_PIXEL_SECONDARY_WEIGHT_SHIFT) & 0x7FL) / 127.0F;
    }

    public static SinkingMedium wallPixelSecondaryMedium(long pixel) {
        if (wallPixelSecondaryWeight(pixel) <= 0.0F) {
            return wallPixelMedium(pixel);
        }
        return SinkingMedium.byId((int) ((pixel >>> WALL_PIXEL_SECONDARY_MEDIUM_SHIFT) & 0xFFL));
    }

    private static long repackWallPixel(long source, float strength, SinkingMedium medium) {
        int cell = (int) source & 0xFF;
        long packed;
        if (wallPixelHasCreationTime(source)) {
            packed = packWallPixel(cell & 15, cell >>> 4, strength, medium, wallPixelCreatedAt(source), true);
        } else {
            packed = packWallPixel(cell & 15, cell >>> 4, strength, medium);
        }
        return packed | (source & WALL_PIXEL_SECONDARY_DATA_MASK);
    }

    private static long[] scaleWallPixels(long[] pixels, float scale) {
        long[] scaled = new long[pixels.length];
        int count = 0;
        for (long pixel : pixels) {
            float strength = wallPixelStrength(pixel) * Mth.clamp(scale, 0.0F, 1.0F);
            if (strength > 0.01F) {
                scaled[count++] = repackWallPixel(pixel, strength, wallPixelMedium(pixel));
            }
        }
        return Arrays.copyOf(scaled, count);
    }

    private static long[] washWallPixels(long[] pixels, float amount, long entryId, long gameTime) {
        long[] washed = new long[pixels.length];
        int count = 0;
        long washStep = gameTime / RAIN_WASH_UPDATE_TICKS;
        for (long pixel : pixels) {
            int cell = (int) pixel & 0xFF;
            long pixelSeed = mix(entryId ^ cell * 0x9e3779b97f4a7c15L);
            float baseRate = Mth.lerp(unitNoise(pixelSeed), 0.62F, 1.42F);
            float pulseNoise = unitNoise(mix(pixelSeed ^ washStep * 0xd1b54a32d192ed03L));
            float pulseRate = pulseNoise < 0.24F ? 0.18F : Mth.lerp(pulseNoise, 0.82F, 1.16F);
            float strength = Math.max(0.0F,
                    wallPixelStrength(pixel) - amount * baseRate * pulseRate);
            if (strength > 0.01F) {
                washed[count++] = repackWallPixel(pixel, strength, wallPixelMedium(pixel));
            }
        }
        return Arrays.copyOf(washed, count);
    }

    private static float unitNoise(long value) {
        return (mix(value) >>> 40) / (float) (1 << 24);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }

    private static long[] removeExpiredWallPixels(long[] pixels, long gameTime) {
        int lifetime = MudPhysicsSettings.footprintLifetimeTicks();
        long[] active = new long[pixels.length];
        int count = 0;
        for (long pixel : pixels) {
            if (!wallPixelHasCreationTime(pixel) || wallPixelAge(pixel, gameTime) < lifetime) {
                active[count++] = pixel;
            }
        }
        return Arrays.copyOf(active, count);
    }

    public static int wallPixelAge(long pixel, long gameTime) {
        if (!wallPixelHasCreationTime(pixel)) {
            return Integer.MAX_VALUE;
        }
        int now = (int) (gameTime & WALL_PIXEL_CREATED_MASK);
        int age = (now - wallPixelCreatedAt(pixel)) & (int) WALL_PIXEL_CREATED_MASK;
        // A client-side Sable sub-level may trail the authoritative server by a
        // few ticks. The upper half of the modular range therefore represents a
        // future timestamp, not an almost-16-million-tick-old stain.
        return age > (WALL_PIXEL_CREATED_MASK >>> 1) ? 0 : age;
    }

    private static float maximumWallPixelStrength(long[] pixels) {
        float maximum = 0.0F;
        for (long pixel : pixels) {
            maximum = Math.max(maximum, wallPixelStrength(pixel));
        }
        return maximum;
    }

    private static long withWallPixelSecondary(long pixel, SinkingMedium medium, float weight) {
        int packedWeight = Mth.clamp(Math.round(weight * 127.0F), 0, 127);
        long cleared = pixel & ~WALL_PIXEL_SECONDARY_DATA_MASK;
        if (packedWeight <= 0 || medium == wallPixelMedium(pixel)) {
            return cleared;
        }
        return cleared
                | (medium.id() & 0xFFL) << WALL_PIXEL_SECONDARY_MEDIUM_SHIFT
                | (packedWeight & 0x7FL) << WALL_PIXEL_SECONDARY_WEIGHT_SHIFT;
    }

    static long[] mergeWallPixels(long[] existing, long[] incoming) {
        long[] byCell = new long[WALL_GRID_SIZE * WALL_GRID_SIZE];
        for (long pixel : existing) {
            byCell[(int) pixel & 0xFF] = pixel;
        }
        for (long pixel : incoming) {
            int cell = (int) pixel & 0xFF;
            if (byCell[cell] == 0L) {
                byCell[cell] = pixel;
                continue;
            }
            long oldPixel = byCell[cell];
            float oldStrength = wallPixelStrength(oldPixel);
            float incomingStrength = wallPixelStrength(pixel);
            float blendedStrength = Math.max(oldStrength, incomingStrength);
            SinkingMedium oldPrimary = wallPixelMedium(oldPixel);
            SinkingMedium incomingMedium = wallPixelMedium(pixel);
            float oldSecondaryWeight = wallPixelSecondaryWeight(oldPixel);
            SinkingMedium oldSecondary = wallPixelSecondaryMedium(oldPixel);

            // One physical face has one final pixel. Reapplying the exact same coat
            // is idempotent, while a genuinely newer coat refreshes lifetime and owns
            // the foreground color instead of being trapped behind the old medium.
            if (incomingMedium == oldPrimary) {
                boolean sameCoat = sameWallPixelGeneration(oldPixel, pixel);
                long basis = sameCoat || !incomingIsNewer(oldPixel, pixel) ? oldPixel : pixel;
                long merged = repackWallPixel(basis, blendedStrength, oldPrimary);
                float secondaryWeight = oldSecondaryWeight;
                if (!sameCoat && basis == pixel && oldSecondaryWeight > 0.0F) {
                    float cover = incomingStrength
                            * (0.42F + MudPhysicsSettings.wallStainOverlapBlend() * 0.42F);
                    secondaryWeight *= 1.0F - Mth.clamp(cover, 0.0F, 0.92F);
                }
                byCell[cell] = withWallPixelSecondary(
                        merged, oldSecondary, secondaryWeight);
                continue;
            }

            // A newer coat may own the foreground only when it is at least as
            // thick. A shallow different medium remains a secondary mix and
            // must not visually cover an existing heavy coat.
            boolean incomingForeground = incomingStrength > oldStrength + 1.0E-4F
                    || (Math.abs(incomingStrength - oldStrength) <= 1.0E-4F
                            && incomingIsNewerOrSame(oldPixel, pixel));
            if (incomingForeground) {
                long merged = repackWallPixel(pixel, blendedStrength, incomingMedium);
                float incomingCover = incomingStrength
                        * (0.50F + MudPhysicsSettings.wallStainOverlapBlend() * 0.42F);
                float retainedOld = (1.0F - Mth.clamp(incomingCover, 0.0F, 0.94F))
                        * oldStrength / Math.max(0.001F, oldStrength + incomingStrength);
                byCell[cell] = withWallPixelSecondary(
                        merged, oldPrimary, Mth.clamp(retainedOld, 0.03F, 0.46F));
                continue;
            }

            long merged = repackWallPixel(oldPixel, blendedStrength, oldPrimary);
            float incomingShare = incomingStrength / Math.max(0.001F, oldStrength + incomingStrength);
            float targetWeight = Mth.clamp(
                    incomingShare * (0.55F + MudPhysicsSettings.wallStainOverlapBlend() * 0.45F),
                    0.08F,
                    0.82F);
            if (oldSecondaryWeight <= 0.0F || oldSecondary == incomingMedium) {
                float fusedWeight = oldSecondaryWeight <= 0.0F
                        ? targetWeight
                        : Math.max(oldSecondaryWeight, targetWeight);
                merged = withWallPixelSecondary(merged, incomingMedium, fusedWeight);
            } else if (targetWeight > oldSecondaryWeight + 0.10F) {
                merged = withWallPixelSecondary(merged, incomingMedium, targetWeight);
            }
            byCell[cell] = merged;
        }
        return compactWallPixels(byCell);
    }

    private static boolean sameWallPixelGeneration(long first, long second) {
        if (wallPixelHasCreationTime(first) != wallPixelHasCreationTime(second)) {
            return false;
        }
        return !wallPixelHasCreationTime(first)
                || wallPixelCreatedAt(first) == wallPixelCreatedAt(second);
    }

    private static boolean incomingIsNewer(long existing, long incoming) {
        if (!wallPixelHasCreationTime(incoming)) {
            return false;
        }
        if (!wallPixelHasCreationTime(existing)) {
            return true;
        }
        int delta = (wallPixelCreatedAt(incoming) - wallPixelCreatedAt(existing))
                & (int) WALL_PIXEL_CREATED_MASK;
        return delta != 0 && delta < 0x800000;
    }

    private static boolean incomingIsNewerOrSame(long existing, long incoming) {
        return sameWallPixelGeneration(existing, incoming)
                || incomingIsNewer(existing, incoming);
    }

    private static long[] compactWallPixels(long[] byCell) {
        int count = 0;
        for (long pixel : byCell) {
            if (pixel != 0L) {
                count++;
            }
        }
        long[] result = new long[count];
        int index = 0;
        for (long pixel : byCell) {
            if (pixel != 0L) {
                result[index++] = pixel;
            }
        }
        return result;
    }

    public record Entry(long id, float localX, float localY, float localZ, float yawDegrees,
            Direction face, boolean wallStain, float width, float height, float strength, SinkingMedium medium,
            long visualSource, long[] wallPixels, long createdAt, long expiresAt, float fade) {
        Entry withFade(float nextFade) {
            return new Entry(id, localX, localY, localZ, yawDegrees, face, wallStain, width, height, strength,
                    medium, visualSource, wallPixels, createdAt, expiresAt, nextFade);
        }

        Entry withPreciseWallPixels(long[] nextPixels, float nextStrength, SinkingMedium nextMedium,
                long nextExpiresAt) {
            return withPreciseWallPixels(nextPixels, nextStrength, nextMedium,
                    visualSource, nextExpiresAt);
        }

        Entry withPreciseWallPixels(long[] nextPixels, float nextStrength,
                SinkingMedium nextMedium, long nextVisualSource,
                long nextExpiresAt) {
            return new Entry(id, localX, localY, localZ, yawDegrees, face, wallStain, width, height, nextStrength,
                    nextMedium, nextVisualSource, nextPixels, createdAt, nextExpiresAt, 1.0F);
        }

        Entry withWashedWallPixels(long[] nextPixels, float nextStrength) {
            return new Entry(id, localX, localY, localZ, yawDegrees, face, wallStain, width, height, nextStrength,
                    medium, visualSource, nextPixels, createdAt, expiresAt, 1.0F);
        }

        Entry withFlowPixels(long[] nextPixels) {
            return new Entry(id, localX, localY, localZ, yawDegrees, face, wallStain, width, height, strength,
                    medium, visualSource, nextPixels, createdAt, expiresAt, fade);
        }
    }
}
