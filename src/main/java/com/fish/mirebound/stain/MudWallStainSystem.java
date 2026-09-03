package com.fish.mirebound.stain;

import com.fish.mirebound.adaptive.MudVisualSource;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.CoverageDebugLog;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCoverageAppearanceSnapshot;
import com.fish.mirebound.mud.MudEntityGeometry;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.PhysicsTraceLog;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Owns precise player-to-surface mud transfer and wall-stain geometry. */
public final class MudWallStainSystem {
    private static final double WALL_STAIN_NEARBY_WALL_REACH = 0.280D;
    private static final double WALL_STAIN_MODEL_PENETRATION = 0.240D;
    private static final double WALL_STAIN_PLAYER_SIDE_TOLERANCE = 0.055D;
    private static final int[][] WALL_STAIN_SPREAD_OFFSETS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
    };
    private static final MudBodyPart[] MUD_BODY_PARTS = MudBodyPart.values();
    private static final MudSurface[] MUD_SURFACES = MudSurface.values();
    private static final ThreadLocal<ArmorWallCoverageScratch> ARMOR_COVERAGE_SCRATCH =
            ThreadLocal.withInitial(ArmorWallCoverageScratch::new);

    private MudWallStainSystem() {
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos supportPos = event.getPos();
        for (Direction face : Direction.values()) {
            BlockPos containerPos = supportPos.relative(face);
            if (level.getBlockEntity(containerPos)
                    instanceof MudFootprintBlockEntity blockEntity) {
                blockEntity.removeEntriesSupportedBy(level, supportPos);
            }
        }
    }

    public static void tick(ServerPlayer player, MudPlayerData data) {
        float minimumCoverage = MudPhysicsSettings.wallStainMinimumSourceCoverage();
        int updateInterval = MudPhysicsSettings.wallStainUpdateIntervalTicks();
        if (!data.wallStainTrackingInitialized) {
            data.wallStainTrackingInitialized = true;
            data.wallStainResidue = data.coverage;
            data.wallStainObservedCoverage = data.coverage;
            data.wallStainWasInMud = data.inMud;
        } else if (data.inMud && (!data.wallStainWasInMud
                || data.coverage > data.wallStainObservedCoverage + 0.035F)) {
            data.wallStainResidue = Math.max(data.wallStainResidue, data.coverage);
        } else {
            data.wallStainResidue = Math.min(data.wallStainResidue, data.coverage);
        }
        data.wallStainObservedCoverage = data.coverage;
        data.wallStainWasInMud = data.inMud;
        if (ticksSince(player, data.lastWallStainTick) < updateInterval
                || (data.wallStainResidue <= minimumCoverage
                        && !hasTransferableArmorCoverage(player, minimumCoverage))) {
            return;
        }

        SableCompat.SurfaceProbe sableProbe = SableCompat.surfaceProbe(
                player.level(), player.getBoundingBox().inflate(WALL_STAIN_NEARBY_WALL_REACH + 0.08D), player);
        boolean nearbyWall = player.horizontalCollision || hasNearbyWallSurface(player) || !sableProbe.isEmpty();
        if (PhysicsTraceLog.enabled(player) && Math.floorMod(player.tickCount - player.getId(), 20) == 0) {
            PhysicsTraceLog.traceDecal(player, "wall-probe sublevels=" + sableProbe.discoveredSubLevels()
                    + " faces=" + sableProbe.faceCount()
                    + " nearby=" + nearbyWall
                    + " residue=" + round3(data.wallStainResidue));
        }
        if (!nearbyWall) {
            debugWallStain(player, data, "idle", null, false);
            return;
        }
        data.lastWallStainTick = player.tickCount;
        List<WallStainCandidate> candidates = findWallStainCandidates(player, data, sableProbe);
        if (candidates.isEmpty()) {
            debugWallStain(player, data, "no-contact", null, false);
            return;
        }
        int placed = 0;
        BitSet skinSources = new BitSet(MudSurfaceLayout.CELL_COUNT);
        BitSet[] armorSources = new BitSet[ArmorMudManager.ARMOR_SLOT_COUNT];
        for (WallStainCandidate candidate : candidates) {
            WallTransferResult transfer = placeWallStain(player, candidate);
            if (transfer.placed()) {
                collectTransferredSources(transfer.sources(), skinSources, armorSources);
                placed++;
            }
        }
        WallStainCandidate first = candidates.getFirst();
        if (placed == 0) {
            debugWallStain(player, data, "place-failed", first, false);
            return;
        }
        fadeTransferredWallPixels(
                player, data, skinSources, armorSources, minimumCoverage);
        data.refreshCoverageAfterSurfaceUpdate();
        data.wallStainResidue = Math.min(data.wallStainResidue, data.coverage);
        debugWallStain(player, data, "placed=" + placed + "/" + candidates.size(), first, true);
    }

    private static void collectTransferredSources(WallTransferSource[] sources,
            BitSet skinSources, BitSet[] armorSources) {
        for (WallTransferSource source : sources) {
            if (source.slot() == null) {
                skinSources.set(source.cell());
                continue;
            }
            int slotIndex = ArmorMudManager.armorSlotIndex(source.slot());
            BitSet slotSources = armorSources[slotIndex];
            if (slotSources == null) {
                slotSources = new BitSet(MudSurfaceLayout.CELL_COUNT);
                armorSources[slotIndex] = slotSources;
            }
            slotSources.set(source.cell());
        }
    }

    private static void fadeTransferredWallPixels(ServerPlayer player, MudPlayerData data,
            BitSet skinSources, BitSet[] armorSources, float minimumCoverage) {
        float transfer = MudPhysicsSettings.wallStainTransferAmount();
        for (int cell = skinSources.nextSetBit(0);
                cell >= 0;
                cell = skinSources.nextSetBit(cell + 1)) {
            float current = data.surfaceCoverage[cell];
            float faded = drainTransferredCoverage(
                    current, transfer, minimumCoverage);
            if (faded < current) {
                data.surfaceCoverage[cell] = faded;
                continue;
            }
            skinSources.clear(cell);
        }
        if (!skinSources.isEmpty()) {
            data.fadeTransferredSurfaceEdges(skinSources,
                    cell -> outerArmorWallSlot(
                            player,
                            MudSurfaceLayout.part(cell),
                            MudSurfaceLayout.surface(cell),
                            MudSurfaceLayout.row(cell)) == null);
        }

        EquipmentSlot[] slots = ArmorMudManager.armorSlots();
        for (int slotIndex = 0; slotIndex < armorSources.length; slotIndex++) {
            BitSet transferred = armorSources[slotIndex];
            if (transferred == null || transferred.isEmpty()) {
                continue;
            }
            EquipmentSlot slot = slots[slotIndex];
            var stack = player.getItemBySlot(slot);
            if (!ArmorMudManager.validArmor(stack, slot)) {
                continue;
            }
            ArmorMudData.Builder builder = ArmorMudManager.data(stack).toBuilder();
            for (int cell = transferred.nextSetBit(0);
                    cell >= 0;
                    cell = transferred.nextSetBit(cell + 1)) {
                if (!builder.fadeToFloor(cell, transfer, minimumCoverage)) {
                    transferred.clear(cell);
                }
            }
            if (!transferred.isEmpty()) {
                builder.fadeTransferredSurfaceEdges(transferred,
                        cell -> outerArmorWallSlot(
                                player,
                                MudSurfaceLayout.part(cell),
                                MudSurfaceLayout.surface(cell),
                                MudSurfaceLayout.row(cell)) == slot);
            }
            if (builder.changed()) {
                ArmorMudManager.store(stack, builder.build());
            }
        }
    }

    static float drainTransferredCoverage(float current, float transfer, float minimumCoverageFloor) {
        float floor = Mth.clamp(minimumCoverageFloor, 0.0F, 1.0F);
        if (current <= floor) {
            return current;
        }
        return Math.max(floor, current - Math.max(0.0F, transfer));
    }

    private static boolean hasNearbyWallSurface(ServerPlayer player) {
        Level level = player.level();
        AABB playerBox = player.getBoundingBox();
        double reach = WALL_STAIN_NEARBY_WALL_REACH;
        int minX = Mth.floor(playerBox.minX - reach);
        int maxX = Mth.floor(playerBox.maxX + reach);
        int minY = Mth.floor(playerBox.minY + 0.04D);
        int maxY = Mth.floor(playerBox.maxY + reach);
        int minZ = Mth.floor(playerBox.minZ - reach);
        int maxZ = Mth.floor(playerBox.maxZ + reach);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || ModBlocks.isSinkingBlock(state.getBlock())
                            || state.getBlock() == ModBlocks.MUD_FOOTPRINT.get()) {
                        continue;
                    }
                    for (AABB localBox : state.getCollisionShape(level, pos).toAabbs()) {
                        AABB wallBox = localBox.move(pos);
                        double verticalOverlap = overlap(playerBox.minY, playerBox.maxY, wallBox.minY, wallBox.maxY);
                        double xGap = axisGap(playerBox.minX, playerBox.maxX, wallBox.minX, wallBox.maxX);
                        double zGap = axisGap(playerBox.minZ, playerBox.maxZ, wallBox.minZ, wallBox.maxZ);
                        double xOverlap = overlap(playerBox.minX, playerBox.maxX, wallBox.minX, wallBox.maxX);
                        double zOverlap = overlap(playerBox.minZ, playerBox.maxZ, wallBox.minZ, wallBox.maxZ);
                        boolean sideContact = verticalOverlap > 0.04D
                                && ((xGap <= reach && zOverlap > 0.04D)
                                        || (zGap <= reach && xOverlap > 0.04D));
                        double ceilingGap = wallBox.minY - playerBox.maxY;
                        boolean ceilingContact = ceilingGap >= -0.025D
                                && ceilingGap <= reach
                                && xOverlap > 0.04D
                                && zOverlap > 0.04D;
                        if (sideContact || ceilingContact) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static double overlap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.max(0.0D, Math.min(firstMax, secondMax) - Math.max(firstMin, secondMin));
    }

    private static double axisGap(double firstMin, double firstMax, double secondMin, double secondMax) {
        if (firstMax < secondMin) {
            return secondMin - firstMax;
        }
        if (secondMax < firstMin) {
            return firstMin - secondMax;
        }
        return 0.0D;
    }

    private static void debugWallStain(ServerPlayer player, MudPlayerData data, String result,
            WallStainCandidate candidate, boolean force) {
        if (!CoverageDebugLog.enabled()
                || (!force && !CoverageDebugLog.reserve(player, "wall-stain:" + result, 20))) {
            return;
        }
        int dirtyPixels = 0;
        float strongestPixel = 0.0F;
        float threshold = MudPhysicsSettings.wallStainMinimumSourceCoverage();
        for (float value : data.surfaceCoverage) {
            if (value > threshold) {
                dirtyPixels++;
                strongestPixel = Math.max(strongestPixel, value);
            }
        }
        String candidateDetails = candidate == null
                ? "none"
                : candidate.medium().serializedName()
                        + " strength=" + round3(candidate.strength())
                        + " face=" + candidate.contact().face()
                        + " pos=" + blockPosString(candidate.contact().containerPos())
                        + " distance=" + round3(candidate.contact().distance())
                        + " projectedPixels=" + candidate.wallPixels().length;
        CoverageDebugLog.event(player, "wall-stain", "result=" + result
                + " coverage=" + round3(data.coverage)
                + " residue=" + round3(data.wallStainResidue)
                + " dirtyPixels=" + dirtyPixels
                + " strongest=" + round3(strongestPixel)
                + " horizontalCollision=" + player.horizontalCollision
                + " player=" + CoverageDebugLog.vec(player.position())
                + " candidate={" + candidateDetails + "}");
    }

    private static List<WallStainCandidate> findWallStainCandidates(ServerPlayer player, MudPlayerData data,
            SableCompat.SurfaceProbe sableProbe) {
        float minimumCoverage = MudPhysicsSettings.wallStainMinimumSourceCoverage();
        float imprintScale = MudPhysicsSettings.wallStainImprintOpacityScale();
        long createdAt = player.level().getGameTime();
        ArmorWallCoverageScratch armorScratch = ARMOR_COVERAGE_SCRATCH.get();
        armorScratch.begin();
        try {
            Map<WallSurfaceKey, WallStainAccumulator> bySurface = new HashMap<>();
            for (MudBodyPart part : MUD_BODY_PARTS) {
                MudEntityGeometry.SurfacePixelSampler geometry =
                        MudEntityGeometry.surfacePixelSampler(player, part);
                for (MudSurface surface : MUD_SURFACES) {
                    MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                    Vec3 outwardNormal = geometry.outwardNormal(surface);
                    for (int row = 0; row < face.height(); row++) {
                        EquipmentSlot armorSlot = outerArmorWallSlot(
                                player, part, surface, row);
                        ArmorWallCoverageSnapshot armor = armorSlot == null
                                ? null : armorScratch.snapshot(player, armorSlot);
                        for (int column = 0; column < face.width(); column++) {
                            int sourceCell = MudSurfaceLayout.cellIndex(
                                    part, surface, row, column);
                            float coverage = armor == null
                                    ? data.surfacePixelCoverage(part, surface, row, column)
                                    : armor.coverageAt(sourceCell);
                            if (!aboveTransferFloor(
                                    coverage, minimumCoverage, armor != null)) {
                                continue;
                            }
                            SinkingMedium sourceMedium = armor == null
                                    ? data.surfacePixelMedium(part, surface, row, column)
                                    : armor.mediumAt(sourceCell);
                            float sourceOpacity = armor == null
                                    ? MudCoverageAppearanceSnapshot.opacity(
                                            data.surfacePixelAppearance(
                                                    part, surface, row, column),
                                            sourceMedium)
                                    : MudMediumRuntime.coverageOpacity(
                                            player.level(), sourceMedium);
                            long sourceVisual = armor == null
                                    ? data.surfacePixelVisualSource(
                                            part, surface, row, column)
                                    : armor.visualSourceAt(sourceCell);
                            float visibleCoverage = wallTransferStrength(
                                    coverage, sourceOpacity, 1.0F);
                            if (visibleCoverage <= 0.0F) {
                                continue;
                            }
                            Vec3 point = geometry.point(part, surface, row, column);
                            if (armorSlot != null) {
                                point = point.add(outwardNormal.scale(
                                        ArmorMudManager.surfaceOffset(armorSlot)));
                            }
                            WallContact contact = findWallContact(
                                    player.level(), point, outwardNormal,
                                    player.position(), sableProbe);
                            if (contact == null) {
                                continue;
                            }
                            WallSurfaceKey key = new WallSurfaceKey(
                                    contact.subLevel(), contact.containerPos(), contact.face(),
                                    sourceMedium, sourceVisual);
                            bySurface.computeIfAbsent(
                                            key,
                                            ignored -> new WallStainAccumulator(
                                                    contact, createdAt))
                                    .add(contact, visibleCoverage * imprintScale,
                                            sourceMedium, new WallTransferSource(
                                                    sourceCell, armorSlot, -1),
                                             sourceVisual);
                        }
                    }
                }
            }
            List<WallStainCandidate> result = new ArrayList<>(bySurface.size());
            for (WallStainAccumulator accumulator : bySurface.values()) {
                WallStainCandidate candidate = accumulator.finish();
                if (candidate != null) {
                    result.add(candidate);
                }
            }
            return result;
        } finally {
            armorScratch.end();
        }
    }

    static float wallTransferStrength(float coverage, float opacity, float imprintScale) {
        return Mth.clamp(coverage, 0.0F, 1.0F)
                * Mth.clamp(opacity, 0.0F, 1.0F)
                * Math.max(0.0F, imprintScale);
    }

    static boolean aboveTransferFloor(float coverage, float minimumCoverage,
            boolean quantized) {
        float floor = Mth.clamp(minimumCoverage, 0.0F, 1.0F);
        if (quantized) {
            floor = Math.round(floor * 255.0F) / 255.0F;
        }
        return coverage > floor;
    }

    static void bridgeSinglePixelWallGaps(
            long[] shaped, boolean[] directPixels, long fallbackCreatedAt) {
        for (int y = 1; y < 15; y++) {
            for (int x = 1; x < 15; x++) {
                int cell = x | y << 4;
                if (shaped[cell] != 0L) {
                    continue;
                }
                int left = cell - 1;
                int right = cell + 1;
                int up = cell - 16;
                int down = cell + 16;
                int neighbors = (directPixels[left] ? 1 : 0)
                        + (directPixels[right] ? 1 : 0)
                        + (directPixels[up] ? 1 : 0)
                        + (directPixels[down] ? 1 : 0);
                if (!(directPixels[left] && directPixels[right])
                        && !(directPixels[up] && directPixels[down])
                        && neighbors < 3) {
                    continue;
                }
                long strongest = strongestDirectPixel(shaped, directPixels, left, 0L);
                strongest = strongestDirectPixel(shaped, directPixels, right, strongest);
                strongest = strongestDirectPixel(shaped, directPixels, up, strongest);
                strongest = strongestDirectPixel(shaped, directPixels, down, strongest);
                if (strongest == 0L) {
                    continue;
                }
                long createdAt = MudFootprintBlockEntity.wallPixelHasCreationTime(strongest)
                        ? MudFootprintBlockEntity.wallPixelCreatedAt(strongest)
                        : fallbackCreatedAt;
                shaped[cell] = MudFootprintBlockEntity.packWallPixel(
                        x,
                        y,
                        MudFootprintBlockEntity.wallPixelStrength(strongest) * 0.92F,
                        MudFootprintBlockEntity.wallPixelMedium(strongest),
                        createdAt);
            }
        }
    }

    private static long strongestDirectPixel(
            long[] shaped, boolean[] directPixels, int cell, long current) {
        long candidate = directPixels[cell] ? shaped[cell] : 0L;
        if (candidate == 0L || current != 0L
                && MudFootprintBlockEntity.wallPixelStrength(candidate)
                        <= MudFootprintBlockEntity.wallPixelStrength(current)) {
            return current;
        }
        return candidate;
    }

    private static boolean hasTransferableArmorCoverage(
            ServerPlayer player, float minimumCoverage) {
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            var stack = player.getItemBySlot(slot);
            if (ArmorMudManager.validArmor(stack, slot)
                    && aboveTransferFloor(
                            ArmorMudManager.data(stack).maximumCoverage(),
                            minimumCoverage, true)) {
                return true;
            }
        }
        return false;
    }

    private static EquipmentSlot outerArmorWallSlot(ServerPlayer player, MudBodyPart part,
            MudSurface surface, int row) {
        EquipmentSlot best = null;
        double bestOffset = Double.NEGATIVE_INFINITY;
        for (var slot : ArmorMudManager.armorSlots()) {
            var stack = player.getItemBySlot(slot);
            if (!ArmorMudManager.validArmor(stack, slot)
                    || !ArmorMudManager.slotOwnsSurface(slot, part, surface, row)) {
                continue;
            }
            double offset = ArmorMudManager.surfaceOffset(slot);
            // The outermost equipped layer owns contact even when it is clean. This prevents
            // mud on the hidden skin from transferring through armor to a wall.
            if (offset > bestOffset) {
                best = slot;
                bestOffset = offset;
            }
        }
        return best;
    }

    private static WallContact findWallContact(Level level, Vec3 point, Vec3 outwardNormal, Vec3 playerCenter,
            SableCompat.SurfaceProbe sableProbe) {
        double reach = WALL_STAIN_NEARBY_WALL_REACH;
        int minX = Mth.floor(point.x - reach);
        int maxX = Mth.floor(point.x + reach);
        int minY = Mth.floor(point.y - reach);
        int maxY = Mth.floor(point.y + reach);
        int minZ = Mth.floor(point.z - reach);
        int maxZ = Mth.floor(point.z + reach);
        WallContact best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos supportPos = new BlockPos(x, y, z);
                    BlockState support = level.getBlockState(supportPos);
                    if (support.isAir() || ModBlocks.isSinkingBlock(support.getBlock())
                            || support.getBlock() == ModBlocks.MUD_FOOTPRINT.get()) {
                        continue;
                    }
                    for (AABB localBox : support.getCollisionShape(level, supportPos).toAabbs()) {
                        double worldMinY = supportPos.getY() + localBox.minY;
                        double worldMaxY = supportPos.getY() + localBox.maxY;
                        if (point.y < worldMinY - 0.025D || point.y > worldMaxY + 0.025D) {
                            continue;
                        }
                        best = closerWallContact(level, point, outwardNormal, playerCenter, supportPos, localBox, Direction.WEST,
                                best, bestDistance);
                        if (best != null) {
                            bestDistance = best.distance();
                        }
                        best = closerWallContact(level, point, outwardNormal, playerCenter, supportPos, localBox, Direction.EAST,
                                best, bestDistance);
                        if (best != null) {
                            bestDistance = best.distance();
                        }
                        best = closerWallContact(level, point, outwardNormal, playerCenter, supportPos, localBox, Direction.NORTH,
                                best, bestDistance);
                        if (best != null) {
                            bestDistance = best.distance();
                        }
                        best = closerWallContact(level, point, outwardNormal, playerCenter, supportPos, localBox, Direction.SOUTH,
                                best, bestDistance);
                        if (best != null) {
                            bestDistance = best.distance();
                        }
                        best = closerWallContact(level, point, outwardNormal, playerCenter, supportPos, localBox, Direction.DOWN,
                                best, bestDistance);
                        if (best != null) {
                            bestDistance = best.distance();
                        }
                    }
                }
            }
        }
        SableCompat.SurfaceContact sableContact = SableCompat.findSurface(
                sableProbe, point, outwardNormal.scale(-1.0D), reach, 0.28D);
        if (sableContact != null && sableContact.distance() < bestDistance) {
            Vec3 local = sableContact.localPoint();
            BlockPos container = sableContact.containerPos();
            best = new WallContact(
                    sableContact.subLevel(),
                    container,
                    (float) (local.x - container.getX()),
                    (float) (local.y - container.getY()),
                    (float) (local.z - container.getZ()),
                    sableContact.face(),
                    sableContact.distance(),
                    sableContact.worldPoint());
        }
        return best;
    }

    private static WallContact closerWallContact(Level level, Vec3 point, Vec3 outwardNormal, Vec3 playerCenter,
            BlockPos supportPos, AABB box, Direction face, WallContact current, double currentDistance) {
        Vec3 faceNormal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        if (outwardNormal.dot(faceNormal) > -0.28D) {
            return current;
        }
        double plane;
        double distance;
        boolean boundary;
        switch (face) {
            case WEST -> {
                double minZ = supportPos.getZ() + box.minZ;
                double maxZ = supportPos.getZ() + box.maxZ;
                if (point.z < minZ - 0.025D || point.z > maxZ + 0.025D) {
                    return current;
                }
                boundary = box.minX <= 0.01D;
                plane = supportPos.getX() + box.minX;
                distance = Math.abs(point.x - plane);
                if (playerCenter.x > plane + WALL_STAIN_PLAYER_SIDE_TOLERANCE
                        || point.x < plane - WALL_STAIN_NEARBY_WALL_REACH
                        || point.x > plane + WALL_STAIN_MODEL_PENETRATION) {
                    return current;
                }
            }
            case EAST -> {
                double minZ = supportPos.getZ() + box.minZ;
                double maxZ = supportPos.getZ() + box.maxZ;
                if (point.z < minZ - 0.025D || point.z > maxZ + 0.025D) {
                    return current;
                }
                boundary = box.maxX >= 0.99D;
                plane = supportPos.getX() + box.maxX;
                distance = Math.abs(point.x - plane);
                if (playerCenter.x < plane - WALL_STAIN_PLAYER_SIDE_TOLERANCE
                        || point.x > plane + WALL_STAIN_NEARBY_WALL_REACH
                        || point.x < plane - WALL_STAIN_MODEL_PENETRATION) {
                    return current;
                }
            }
            case NORTH -> {
                double minX = supportPos.getX() + box.minX;
                double maxX = supportPos.getX() + box.maxX;
                if (point.x < minX - 0.025D || point.x > maxX + 0.025D) {
                    return current;
                }
                boundary = box.minZ <= 0.01D;
                plane = supportPos.getZ() + box.minZ;
                distance = Math.abs(point.z - plane);
                if (playerCenter.z > plane + WALL_STAIN_PLAYER_SIDE_TOLERANCE
                        || point.z < plane - WALL_STAIN_NEARBY_WALL_REACH
                        || point.z > plane + WALL_STAIN_MODEL_PENETRATION) {
                    return current;
                }
            }
            case SOUTH -> {
                double minX = supportPos.getX() + box.minX;
                double maxX = supportPos.getX() + box.maxX;
                if (point.x < minX - 0.025D || point.x > maxX + 0.025D) {
                    return current;
                }
                boundary = box.maxZ >= 0.99D;
                plane = supportPos.getZ() + box.maxZ;
                distance = Math.abs(point.z - plane);
                if (playerCenter.z < plane - WALL_STAIN_PLAYER_SIDE_TOLERANCE
                        || point.z > plane + WALL_STAIN_NEARBY_WALL_REACH
                        || point.z < plane - WALL_STAIN_MODEL_PENETRATION) {
                    return current;
                }
            }
            case DOWN -> {
                double minX = supportPos.getX() + box.minX;
                double maxX = supportPos.getX() + box.maxX;
                double minZ = supportPos.getZ() + box.minZ;
                double maxZ = supportPos.getZ() + box.maxZ;
                if (point.x < minX - 0.025D || point.x > maxX + 0.025D
                        || point.z < minZ - 0.025D || point.z > maxZ + 0.025D) {
                    return current;
                }
                boundary = box.minY <= 0.01D;
                plane = supportPos.getY() + box.minY;
                distance = Math.abs(point.y - plane);
                if (playerCenter.y > plane + WALL_STAIN_PLAYER_SIDE_TOLERANCE
                        || point.y < plane - WALL_STAIN_NEARBY_WALL_REACH
                        || point.y > plane + WALL_STAIN_MODEL_PENETRATION) {
                    return current;
                }
            }
            default -> {
                return current;
            }
        }
        if (!boundary || distance >= currentDistance) {
            return current;
        }

        BlockPos containerPos = supportPos.relative(face);
        BlockState container = level.getBlockState(containerPos);
        if (!container.isAir() && container.getBlock() != ModBlocks.MUD_FOOTPRINT.get()) {
            return current;
        }
        double decalX = point.x;
        double decalY = point.y;
        double decalZ = point.z;
        if (face == Direction.WEST || face == Direction.EAST) {
            decalX = plane + face.getStepX() * 0.006D;
        } else if (face == Direction.NORTH || face == Direction.SOUTH) {
            decalZ = plane + face.getStepZ() * 0.006D;
        } else if (face == Direction.DOWN) {
            decalY = plane - 0.006D;
        }
        return new WallContact(
                null,
                containerPos,
                (float) (decalX - containerPos.getX()),
                (float) (decalY - containerPos.getY()),
                (float) (decalZ - containerPos.getZ()),
                face,
                distance,
                new Vec3(decalX, decalY, decalZ));
    }

    private static WallTransferResult placeWallStain(ServerPlayer player, WallStainCandidate candidate) {
        ServerLevel level = player.serverLevel();
        WallContact contact = candidate.contact();
        long[] clippedPixels = clipWallPixelsToSupport(level, contact, candidate.wallPixels());
        if (clippedPixels.length == 0) {
            return WallTransferResult.EMPTY;
        }
        BitSet cleanWallPixels = new BitSet(256);
        if (MudDecalAccess.blockEntity(level, contact.subLevel(), contact.containerPos())
                instanceof MudFootprintBlockEntity blockEntity) {
            for (long pixel : clippedPixels) {
                int cell = (int) pixel & 0xFF;
                if (!blockEntity.hasPreciseWallPixel(contact.face(), cell)) {
                    cleanWallPixels.set(cell);
                }
            }
        } else {
            for (long pixel : clippedPixels) {
                cleanWallPixels.set((int) pixel & 0xFF);
            }
        }
        boolean added = addPreciseWallPixels(
                level, contact, clippedPixels, candidate.strength(),
                candidate.medium(), candidate.visualSource());
        if (added) {
            placeCornerWrappedStains(
                    level, contact, clippedPixels, candidate.visualSource());
            spawnWallStainParticles(level, contact, candidate.medium(),
                    candidate.visualSource());
        }
        if (!added) {
            return WallTransferResult.EMPTY;
        }
        List<WallTransferSource> transferable = new ArrayList<>();
        for (WallTransferSource source : candidate.sources()) {
            if (cleanWallPixels.get(source.wallCell())) {
                transferable.add(source);
            }
        }
        return new WallTransferResult(true,
                transferable.toArray(WallTransferSource[]::new));
    }

    public static boolean placeMudSplashStain(ServerLevel level, Object subLevel,
            BlockPos supportPos, BlockPos containerPos, Direction face,
            Vec3 localPoint, Vec3 worldPoint, float radius, float strength,
            SinkingMedium medium, long visualSource) {
        if (radius <= 0.0F || strength <= 0.0F
                || !canHostDecal(level, subLevel, containerPos)) {
            return false;
        }
        Vec3 relative = subLevel == null
                ? worldPoint.subtract(containerPos.getX(), containerPos.getY(), containerPos.getZ())
                : localPoint.subtract(containerPos.getX(), containerPos.getY(), containerPos.getZ());
        WallContact contact = new WallContact(
                subLevel,
                containerPos,
                (float) relative.x,
                (float) relative.y,
                (float) relative.z,
                face,
                0.0D,
                worldPoint);
        long[] pixels = splashWallPixels(
                contact, radius, strength, medium, level.getGameTime());
        if (pixels.length == 0
                || !addPreciseWallPixels(level, contact, pixels, strength,
                        medium, visualSource)) {
            return false;
        }
        placeCornerWrappedStains(level, contact, pixels, visualSource);
        return true;
    }

    private static long[] splashWallPixels(WallContact contact, float radius,
            float strength, SinkingMedium medium, long createdAt) {
        Direction face = contact.face();
        boolean horizontalFace = face.getAxis() == Direction.Axis.Y;
        boolean northSouth = face == Direction.NORTH || face == Direction.SOUTH;
        float centerU = horizontalFace
                ? contact.localX()
                : northSouth ? contact.localX() : contact.localZ();
        float centerV = horizontalFace
                ? contact.localZ()
                : contact.localY();
        float radiusPixels = Mth.clamp(radius * 16.0F, 0.65F, 6.0F);
        int minU = Mth.clamp(Mth.floor(centerU * 16.0F - radiusPixels - 1.0F), 0, 15);
        int maxU = Mth.clamp(Mth.floor(centerU * 16.0F + radiusPixels + 1.0F), 0, 15);
        int minV = Mth.clamp(Mth.floor(centerV * 16.0F - radiusPixels - 1.0F), 0, 15);
        int maxV = Mth.clamp(Mth.floor(centerV * 16.0F + radiusPixels + 1.0F), 0, 15);
        long seed = contact.containerPos().asLong()
                ^ (long) face.ordinal() * 0x9e3779b97f4a7c15L
                ^ createdAt * 0xc2b2ae3d27d4eb4fL;
        long[] scratch = new long[256];
        int count = 0;
        for (int v = minV; v <= maxV; v++) {
            for (int u = minU; u <= maxU; u++) {
                float dx = u + 0.5F - centerU * 16.0F;
                float dy = v + 0.5F - centerV * 16.0F;
                float distance = Mth.sqrt(dx * dx + dy * dy);
                float noise = WallStainAccumulator.wallStainNoise(
                        WallStainAccumulator.wallStainHash(
                                seed ^ (u | v << 4) * 0x632be59bd9b4e019L));
                float contour = radiusPixels * (0.82F + noise * 0.32F);
                if (distance > contour) {
                    continue;
                }
                float falloff = Mth.clamp(1.0F - distance / Math.max(0.65F, contour), 0.0F, 1.0F);
                float pixelStrength = strength * (0.48F + falloff * 0.52F);
                scratch[count++] = MudFootprintBlockEntity.packWallPixel(
                        u, v, pixelStrength, medium, createdAt);
            }
        }
        return count == 0 ? new long[0] : Arrays.copyOf(scratch, count);
    }


    private static boolean addPreciseWallPixels(ServerLevel level, WallContact contact,
            long[] wallPixels, float strength, SinkingMedium medium) {
        return addPreciseWallPixels(
                level, contact, wallPixels, strength, medium, 0L);
    }

    private static boolean addPreciseWallPixels(ServerLevel level, WallContact contact,
            long[] wallPixels, float strength, SinkingMedium medium,
            long visualSource) {
        wallPixels = clipWallPixelsToSupport(level, contact, wallPixels);
        if (wallPixels.length == 0) {
            return false;
        }
        BlockPos pos = contact.containerPos();
        BlockState current = MudDecalAccess.state(level, contact.subLevel(), pos);
        boolean created = false;
        if (current.getBlock() == ModBlocks.MUD_FOOTPRINT.get()
                && !(MudDecalAccess.blockEntity(level, contact.subLevel(), pos)
                        instanceof MudFootprintBlockEntity)) {
            MudDecalAccess.removeContainer(level, contact.subLevel(), pos);
            current = MudDecalAccess.state(level, contact.subLevel(), pos);
        }
        if (current.isAir()) {
            if (!MudDecalAccess.placeContainer(level, contact.subLevel(), pos)) {
                return false;
            }
            created = true;
        } else if (current.getBlock() != ModBlocks.MUD_FOOTPRINT.get()) {
            return false;
        }
        if (!(MudDecalAccess.blockEntity(level, contact.subLevel(), pos)
                instanceof MudFootprintBlockEntity blockEntity)) {
            if (created) {
                MudDecalAccess.removeContainer(level, contact.subLevel(), pos);
            }
            return false;
        }
        boolean added = blockEntity.addPreciseWallStain(
                level,
                contact.localX(),
                contact.localY(),
                contact.localZ(),
                contact.face(),
                wallPixels,
                strength,
                medium,
                visualSource);
        if (!added && created) {
            MudDecalAccess.removeContainer(level, contact.subLevel(), pos);
        }
        return added;
    }

    private static long[] clipWallPixelsToSupport(ServerLevel level, WallContact contact,
            long[] wallPixels) {
        if (wallPixels.length == 0) {
            return wallPixels;
        }
        BlockPos supportPos = contact.containerPos().relative(contact.face().getOpposite());
        BlockState support = MudDecalAccess.state(level, contact.subLevel(), supportPos);
        if (support.isAir() || support.getBlock() == ModBlocks.MUD_FOOTPRINT.get()
                || ModBlocks.isSinkingBlock(support.getBlock())) {
            return new long[0];
        }
        List<AABB> boxes = support.getCollisionShape(level, supportPos).toAabbs();
        if (boxes.isEmpty()) {
            return new long[0];
        }
        long[] clipped = new long[wallPixels.length];
        int count = 0;
        for (long pixel : wallPixels) {
            double horizontal = (MudFootprintBlockEntity.wallPixelHorizontal(pixel) + 0.5D) / 16.0D;
            double vertical = (MudFootprintBlockEntity.wallPixelVertical(pixel) + 0.5D) / 16.0D;
            if (collisionFaceContains(boxes, contact.face(), horizontal, vertical)) {
                clipped[count++] = pixel;
            }
        }
        return count == wallPixels.length ? wallPixels : Arrays.copyOf(clipped, count);
    }

    private static boolean collisionFaceContains(List<AABB> boxes, Direction face,
            double horizontal, double vertical) {
        final double tolerance = 1.0E-4D;
        for (AABB box : boxes) {
            boolean reachesFace = switch (face) {
                case WEST -> box.minX <= tolerance;
                case EAST -> box.maxX >= 1.0D - tolerance;
                case DOWN -> box.minY <= tolerance;
                case UP -> box.maxY >= 1.0D - tolerance;
                case NORTH -> box.minZ <= tolerance;
                case SOUTH -> box.maxZ >= 1.0D - tolerance;
            };
            if (!reachesFace) {
                continue;
            }
            boolean inside = switch (face.getAxis()) {
                case X -> horizontal >= box.minZ - tolerance && horizontal <= box.maxZ + tolerance
                        && vertical >= box.minY - tolerance && vertical <= box.maxY + tolerance;
                case Y -> horizontal >= box.minX - tolerance && horizontal <= box.maxX + tolerance
                        && vertical >= box.minZ - tolerance && vertical <= box.maxZ + tolerance;
                case Z -> horizontal >= box.minX - tolerance && horizontal <= box.maxX + tolerance
                        && vertical >= box.minY - tolerance && vertical <= box.maxY + tolerance;
            };
            if (inside) {
                return true;
            }
        }
        return false;
    }

    private static void placeCornerWrappedStains(ServerLevel level, WallContact sourceContact,
            long[] sourcePixels, long visualSource) {
        int maximumPixels = MudPhysicsSettings.wallStainCornerWrapMaxPixels();
        if (maximumPixels <= 0) {
            return;
        }
        BlockPos supportPos = sourceContact.containerPos()
                .relative(sourceContact.face().getOpposite());
        List<WallStainCornerWrap.WrappedFace> wrappedFaces = WallStainCornerWrap.build(
                sourceContact.face(),
                sourcePixels,
                maximumPixels,
                MudPhysicsSettings.wallStainMinimumSourceCoverage(),
                MudPhysicsSettings.wallStainCornerWrapRetention(),
                MudPhysicsSettings.wallStainCornerWrapRoughness(),
                level.getGameTime(),
                sourceContact.containerPos().asLong()
                        ^ (long) sourceContact.face().ordinal() * 0x9e3779b97f4a7c15L);
        List<CornerWrappedTarget> targets = new ArrayList<>(wrappedFaces.size());
        for (WallStainCornerWrap.WrappedFace wrapped : wrappedFaces) {
            if (!hasOuterCollisionFace(
                    level, sourceContact.subLevel(), supportPos, wrapped.face())
                    || outerCornerBlocked(
                            level, sourceContact.subLevel(), supportPos,
                            sourceContact.face(), wrapped.face())) {
                continue;
            }
            WallContact target = wallContactForSupportFace(
                    sourceContact.subLevel(), supportPos, wrapped.face());
            if (canHostDecal(level, target.subLevel(), target.containerPos())) {
                targets.add(new CornerWrappedTarget(target, wrapped));
            }
        }
        boolean allocatesContainer = targets.stream().anyMatch(target ->
                !(MudDecalAccess.blockEntity(
                        level, target.contact().subLevel(), target.contact().containerPos())
                        instanceof MudFootprintBlockEntity));
        if (allocatesContainer
                && MudDecalAccess.blockEntity(
                        level, sourceContact.subLevel(), sourceContact.containerPos())
                        instanceof MudFootprintBlockEntity sourceBlockEntity) {
            sourceBlockEntity.protectPreciseWallStainFromExpansionEviction(
                    level, sourceContact.face());
        }
        for (CornerWrappedTarget target : targets) {
            WallStainCornerWrap.WrappedFace wrapped = target.wrapped();
            addPreciseWallPixels(
                    level,
                    target.contact(),
                    wrapped.pixels(),
                    wrapped.strength(),
                    wrapped.medium(),
                    visualSource);
        }
    }

    private static boolean outerCornerBlocked(ServerLevel level, Object subLevel,
            BlockPos supportPos, Direction sourceFace, Direction targetFace) {
        if (sourceFace.getAxis() == targetFace.getAxis()) {
            return true;
        }
        BlockPos cornerPos = supportPos.relative(sourceFace).relative(targetFace);
        BlockState corner = MudDecalAccess.state(level, subLevel, cornerPos);
        if (corner.isAir() || corner.getBlock() == ModBlocks.MUD_FOOTPRINT.get()) {
            return false;
        }
        for (AABB box : corner.getCollisionShape(level, cornerPos).toAabbs()) {
            if (box.minX <= 0.001D && box.minY <= 0.001D && box.minZ <= 0.001D
                    && box.maxX >= 0.999D && box.maxY >= 0.999D && box.maxZ >= 0.999D) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOuterCollisionFace(ServerLevel level, Object subLevel,
            BlockPos supportPos, Direction face) {
        BlockState support = MudDecalAccess.state(level, subLevel, supportPos);
        if (support.isAir()
                || support.getBlock() == ModBlocks.MUD_FOOTPRINT.get()
                || ModBlocks.isSinkingBlock(support.getBlock())) {
            return false;
        }
        for (AABB box : support.getCollisionShape(level, supportPos).toAabbs()) {
            boolean reachesBoundary = switch (face) {
                case WEST -> box.minX <= 0.01D;
                case EAST -> box.maxX >= 0.99D;
                case DOWN -> box.minY <= 0.01D;
                case UP -> box.maxY >= 0.99D;
                case NORTH -> box.minZ <= 0.01D;
                case SOUTH -> box.maxZ >= 0.99D;
            };
            if (reachesBoundary) {
                return true;
            }
        }
        return false;
    }

    private static boolean canHostDecal(ServerLevel level, Object subLevel, BlockPos pos) {
        BlockState state = MudDecalAccess.state(level, subLevel, pos);
        return state.isAir() || state.getBlock() == ModBlocks.MUD_FOOTPRINT.get();
    }

    private static WallContact wallContactForSupportFace(Object subLevel, BlockPos supportPos,
            Direction face) {
        BlockPos containerPos = supportPos.relative(face);
        double x = supportPos.getX() + 0.5D;
        double y = supportPos.getY() + 0.5D;
        double z = supportPos.getZ() + 0.5D;
        double planeOffset = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : 0.0D;
        switch (face.getAxis()) {
            case X -> x = supportPos.getX() + planeOffset + face.getStepX() * 0.006D;
            case Y -> y = supportPos.getY() + planeOffset + face.getStepY() * 0.006D;
            case Z -> z = supportPos.getZ() + planeOffset + face.getStepZ() * 0.006D;
        }
        Vec3 point = new Vec3(x, y, z);
        return new WallContact(
                subLevel,
                containerPos,
                (float) (x - containerPos.getX()),
                (float) (y - containerPos.getY()),
                (float) (z - containerPos.getZ()),
                face,
                0.0D,
                point);
    }


    private static void spawnWallStainParticles(ServerLevel level, WallContact contact,
            SinkingMedium medium, long visualSource) {
        DustParticleOptions dust = new DustParticleOptions(
                MudVisualSource.particleColor(visualSource, medium.particleColor()),
                medium.particleScale() * 0.72F);
        Vec3 point = contact.worldPoint();
        level.sendParticles(
                dust,
                point.x,
                point.y,
                point.z,
                4,
                0.035D,
                0.050D,
                0.035D,
                0.001D);
    }

    private static final class ArmorWallCoverageScratch {
        private final ArmorWallCoverageSnapshot[] snapshots =
                new ArmorWallCoverageSnapshot[ArmorMudManager.ARMOR_SLOT_COUNT];
        private final boolean[] loaded = new boolean[ArmorMudManager.ARMOR_SLOT_COUNT];

        private ArmorWallCoverageScratch() {
            Arrays.setAll(snapshots, ignored -> new ArmorWallCoverageSnapshot());
        }

        private void begin() {
            Arrays.fill(loaded, false);
        }

        private ArmorWallCoverageSnapshot snapshot(
                ServerPlayer player, EquipmentSlot slot) {
            int index = ArmorMudManager.armorSlotIndex(slot);
            if (!loaded[index]) {
                snapshots[index].load(ArmorMudManager.data(player.getItemBySlot(slot)));
                loaded[index] = true;
            }
            return snapshots[index];
        }

        private void end() {
            Arrays.fill(loaded, false);
        }
    }

    private static final class ArmorWallCoverageSnapshot {
        private final byte[] coverage = new byte[MudSurfaceLayout.CELL_COUNT];
        private final byte[] medium = new byte[MudSurfaceLayout.CELL_COUNT];
        private final long[] visualSource = new long[MudSurfaceLayout.CELL_COUNT];

        private void load(ArmorMudData data) {
            Arrays.fill(coverage, (byte) 0);
            Arrays.fill(medium, (byte) SinkingMedium.MUD.id());
            Arrays.fill(visualSource, 0L);
            data.forEachVisual((cell, strength, sinkingMedium, source) -> {
                coverage[cell] = (byte) Mth.clamp(
                        Math.round(strength * 255.0F), 0, 255);
                medium[cell] = (byte) sinkingMedium.id();
                visualSource[cell] = source;
            });
        }

        private float coverageAt(int cell) {
            return (coverage[cell] & 0xFF) / 255.0F;
        }

        private SinkingMedium mediumAt(int cell) {
            return SinkingMedium.byId(medium[cell] & 0xFF);
        }

        private long visualSourceAt(int cell) {
            return visualSource[cell];
        }
    }


    private record WallContact(Object subLevel, BlockPos containerPos,
            float localX, float localY, float localZ, Direction face, double distance, Vec3 worldPoint) {
    }

    private record WallSurfaceKey(Object subLevel, BlockPos containerPos, Direction face,
            SinkingMedium medium, long visualSource) {
    }

    private record WallTransferSource(int cell, net.minecraft.world.entity.EquipmentSlot slot,
            int wallCell) {
    }

    private record WallTransferResult(boolean placed, WallTransferSource[] sources) {
        private static final WallTransferResult EMPTY = new WallTransferResult(false, new WallTransferSource[0]);
    }

    private record WallStainCandidate(WallContact contact, SinkingMedium medium,
            long visualSource, float strength,
            long[] wallPixels, WallTransferSource[] sources) {
    }

    private record CornerWrappedTarget(
            WallContact contact, WallStainCornerWrap.WrappedFace wrapped) {
    }

    private static final class WallStainAccumulator {
        private final WallContact contact;
        private final long createdAt;
        private final long[] pixels = new long[256];
        private final boolean[] directPixels = new boolean[256];
        private WallTransferSource[] sources = new WallTransferSource[64];
        private int sourceCount;
        private float strongest;
        private SinkingMedium dominantMedium = SinkingMedium.MUD;
        private long dominantVisualSource;

        private WallStainAccumulator(WallContact contact, long createdAt) {
            this.contact = contact;
            this.createdAt = createdAt;
        }

        private void add(WallContact pixelContact, float strength, SinkingMedium medium,
                WallTransferSource source, long visualSource) {
            Direction face = pixelContact.face();
            boolean horizontalFace = face.getAxis() == Direction.Axis.Y;
            boolean northSouth = face == Direction.NORTH || face == Direction.SOUTH;
            float horizontal = horizontalFace
                    ? pixelContact.localX()
                    : northSouth ? pixelContact.localX() : pixelContact.localZ();
            float vertical = horizontalFace
                    ? pixelContact.localZ()
                    : pixelContact.localY();
            int horizontalCell = Mth.clamp(Mth.floor(horizontal * 16.0F), 0, 15);
            int verticalCell = Mth.clamp(Mth.floor(vertical * 16.0F), 0, 15);
            putProjectedPixel(horizontalCell, verticalCell, strength, medium,
                    visualSource);
            directPixels[horizontalCell | verticalCell << 4] = true;
            if (sourceCount >= sources.length) {
                sources = Arrays.copyOf(sources, sources.length * 2);
            }
            sources[sourceCount++] = new WallTransferSource(
                    source.cell(), source.slot(), horizontalCell | verticalCell << 4);
        }

        private void putProjectedPixel(int horizontalCell, int verticalCell,
                float strength, SinkingMedium medium, long visualSource) {
            int cell = horizontalCell | verticalCell << 4;
            long packed = MudFootprintBlockEntity.packWallPixel(
                    horizontalCell, verticalCell, strength, medium, createdAt);
            if (pixels[cell] == 0L
                    || MudFootprintBlockEntity.wallPixelStrength(packed)
                            >= MudFootprintBlockEntity.wallPixelStrength(pixels[cell])) {
                pixels[cell] = packed;
            }
            if (strength > strongest) {
                strongest = strength;
                dominantMedium = medium;
                dominantVisualSource = visualSource;
            }
        }

        private static float wallStainNoise(int value) {
            value ^= value >>> 16;
            value *= 0x7feb352d;
            value ^= value >>> 15;
            value *= 0x846ca68b;
            value ^= value >>> 16;
            return (value & 0xFFFF) / 65535.0F;
        }

        private WallStainCandidate finish() {
            long[] shapedPixels = shapeBoundary();
            int count = 0;
            for (long pixel : shapedPixels) {
                if (pixel != 0L) {
                    count++;
                }
            }
            if (count == 0) {
                return null;
            }
            long[] compact = new long[count];
            int index = 0;
            for (long pixel : shapedPixels) {
                if (pixel != 0L) {
                    compact[index++] = pixel;
                }
            }
            return new WallStainCandidate(
                    contact,
                    dominantMedium,
                    dominantVisualSource,
                    strongest,
                    compact,
                    Arrays.copyOf(sources, sourceCount));
        }

        private long[] shapeBoundary() {
            long[] shaped = Arrays.copyOf(pixels, pixels.length);
            int directCount = 0;
            for (boolean direct : directPixels) {
                if (direct) {
                    directCount++;
                }
            }
            if (directCount == 0) {
                return shaped;
            }

            bridgeSinglePixelWallGaps(shaped, directPixels, createdAt);

            float spreadChance = MudPhysicsSettings.wallStainEdgeSpreadChance();
            // Geometry must stay stable while the same body pixels rub the same face.
            // Creation time belongs to lifetime fading, not to the contour seed.
            long baseSeed = contact.containerPos().asLong()
                    ^ (long) contact.face().ordinal() * 0xc2b2ae3d27d4eb4fL;

            for (int cell = 0; cell < directPixels.length; cell++) {
                long sourcePixel = shaped[cell];
                if (sourcePixel == 0L || !directPixels[cell]) {
                    continue;
                }
                int x = cell & 15;
                int y = cell >>> 4;
                if (directNeighborCount(x, y) >= 4) {
                    continue;
                }
                for (int offset = 0; offset < WALL_STAIN_SPREAD_OFFSETS.length; offset++) {
                    int targetX = x + WALL_STAIN_SPREAD_OFFSETS[offset][0];
                    int targetY = y + WALL_STAIN_SPREAD_OFFSETS[offset][1];
                    if (targetX < 0 || targetX > 15 || targetY < 0 || targetY > 15) {
                        continue;
                    }
                    int targetCell = targetX | targetY << 4;
                    if (directPixels[targetCell]) {
                        continue;
                    }
                    float chance = offset < 4 ? spreadChance * 0.55F : spreadChance * 0.20F;
                    long seed = baseSeed ^ cell * 0x94d049bb133111ebL
                            ^ offset * 0x369dea0f31a53f85L;
                    if (wallStainNoise(wallStainHash(seed)) >= chance) {
                        continue;
                    }
                    float strength = MudFootprintBlockEntity.wallPixelStrength(sourcePixel)
                            * (0.45F + wallStainNoise(wallStainHash(seed ^ 0x5deece66dL)) * 0.23F);
                    putBoundaryPixel(shaped, targetX, targetY, strength,
                            MudFootprintBlockEntity.wallPixelMedium(sourcePixel));
                }
            }
            return shaped;
        }

        private int directNeighborCount(int x, int y) {
            int count = 0;
            if (x > 0 && directPixels[x - 1 | y << 4]) {
                count++;
            }
            if (x < 15 && directPixels[x + 1 | y << 4]) {
                count++;
            }
            if (y > 0 && directPixels[x | y - 1 << 4]) {
                count++;
            }
            if (y < 15 && directPixels[x | y + 1 << 4]) {
                count++;
            }
            return count;
        }

        private void putBoundaryPixel(long[] target, int x, int y, float strength, SinkingMedium medium) {
            int cell = x | y << 4;
            long packed = MudFootprintBlockEntity.packWallPixel(x, y, strength, medium, createdAt);
            if (target[cell] == 0L
                    || MudFootprintBlockEntity.wallPixelStrength(packed)
                            > MudFootprintBlockEntity.wallPixelStrength(target[cell])) {
                target[cell] = packed;
            }
        }

        private static int wallStainHash(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdL;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53L;
            value ^= value >>> 33;
            return (int) (value ^ value >>> 32);
        }
    }


    private static long ticksSince(ServerPlayer player, int lastTick) {
        return (long) player.tickCount - (long) lastTick;
    }

    private static String blockPosString(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
