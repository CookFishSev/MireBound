package com.fish.mirebound.itemphysics;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableCompat.SinkingSample;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Resolves a mud column and its world-gravity sinking frame for a dropped item. */
final class DroppedItemContactResolver {
    private static final int MAXIMUM_COLUMN_BLOCKS = 64;
    private static final int MAXIMUM_SWEEP_SAMPLES = 24;
    private static final double SWEEP_SAMPLE_SPACING = 0.24D;
    private static final double SURFACE_CONTACT_TOLERANCE = 0.025D;
    private static final double PHYSICAL_SURFACE_SWITCH_HYSTERESIS = 0.18D;
    private static final ThreadLocal<BlockPos.MutableBlockPos> CURRENT_PROBE =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private DroppedItemContactResolver() {
    }

    static Contact resolve(ItemEntity item, ContactHint hint) {
        return resolve(item, hint.level(), hint.pos());
    }

    /**
     * Refreshes a controlled item's original mud column without using its current depth as
     * evidence. A Sable item can cross a thin rotated sheet before the post-tick solver runs;
     * treating that temporary overshoot as contact loss creates a capture/release loop.
     */
    static Contact refreshAnchor(ItemEntity item, Contact cached) {
        BlockState topState = cached.level().getBlockState(cached.topPos());
        SinkingMedium medium = ModBlocks.mediumOf(topState.getBlock());
        Frame frame = frame(item, cached.subLevel());
        if (frame == null || medium == null) {
            return null;
        }
        boolean physicalized = frame.transform() != null;
        Direction surfaceDirection = surfaceDirection(
                topState, medium, frame.transform(), cached.surfaceDirection());
        if (surfaceDirection != cached.surfaceDirection()
                || !isEnabledMud(cached.level(), cached.topPos(), surfaceDirection, physicalized)
                || medium != cached.medium()) {
            return resolve(item, cached.level(), cached.topPos(), frame, cached.subLevel(), false);
        }
        BlockPos exterior = cached.topPos().relative(cached.surfaceDirection());
        if (isEnabledMud(cached.level(), exterior, cached.surfaceDirection(), physicalized)) {
            return resolve(item, cached.level(), cached.topPos(), frame, cached.subLevel(), false);
        }
        BlockState bottomState = cached.level().getBlockState(cached.bottomPos());
        if (!isEnabledMud(cached.level(), cached.bottomPos(), cached.surfaceDirection(), physicalized)) {
            return resolve(item, cached.level(), cached.topPos(), frame, cached.subLevel(), false);
        }
        return new Contact(
                cached.level(), cached.subLevel(), cached.topPos(), cached.bottomPos(),
                topState, bottomState, medium, cached.surfaceDirection(), frame,
                cached.exactVolumeHit());
    }

    static Contact findSweptWorldContact(ItemEntity item) {
        Vec3 from = new Vec3(item.xo, item.yo, item.zo);
        Vec3 to = item.position();
        double distance = from.distanceTo(to);
        int samples = Mth.clamp(
                (int) Math.ceil(distance / SWEEP_SAMPLE_SPACING), 1, MAXIMUM_SWEEP_SAMPLES);
        BlockPos previous = null;
        for (int sample = 0; sample <= samples; sample++) {
            double amount = sample / (double) samples;
            Vec3 point = from.lerp(to, amount);
            BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
            if (pos.equals(previous)) {
                continue;
            }
            previous = pos;
            BlockState state = item.level().getBlockState(pos);
            SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
            if (medium == null || !MudMediumRuntime.enabled(item.level(), pos, medium)) {
                continue;
            }
            Vec3 contactPoint = sweptContactPoint(item.level(), pos, state, medium, from, to, point);
            Contact contact = resolve(item, item.level(), pos,
                    new Frame(contactPoint,
                            contactPoint.add(0.0D, item.getBbHeight() * 0.5D, 0.0D),
                            item.getDeltaMovement(), null));
            if (contact != null) {
                return contact;
            }
        }
        return null;
    }

    /** Builds a normal item contact from an exact Sable volume sample. */
    static Contact resolveSableSample(
            ItemEntity item, SinkingSample sample, Vec3 worldItemPosition) {
        if (sample == null || worldItemPosition == null) {
            return null;
        }
        Frame frame = frameAtWorldPosition(item, sample.subLevel(), worldItemPosition);
        Contact contact = frame == null ? null : resolve(
                item, item.level(), sample.pos(), frame, sample.subLevel(), false);
        return contact == null ? null : contact.withExactVolumeHit();
    }

    static Contact findCurrentWorldContact(ItemEntity item) {
        BlockPos.MutableBlockPos cursor = CURRENT_PROBE.get();
        int x = Mth.floor(item.getX());
        int y = Mth.floor(item.getY());
        int z = Mth.floor(item.getZ());
        cursor.set(x, y, z);
        Contact current = resolveCurrentCandidate(item, cursor);
        if (current != null) {
            return current;
        }
        cursor.set(x, y - 1, z);
        Contact below = resolveCurrentCandidate(item, cursor);
        if (below != null) {
            return below;
        }
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) {
                continue;
            }
            cursor.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
            Contact adjacent = resolveCurrentCandidate(item, cursor);
            if (adjacent != null) {
                return adjacent;
            }
        }
        return null;
    }

    private static Contact resolveCurrentCandidate(
            ItemEntity item, BlockPos.MutableBlockPos cursor) {
        BlockState state = item.level().getBlockState(cursor);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null || !MudMediumRuntime.enabled(item.level(), cursor, medium)) {
            return null;
        }
        return resolve(item, item.level(), cursor);
    }

    static boolean needsSweptWorldContact(ItemEntity item) {
        double movementSquared = new Vec3(item.xo, item.yo, item.zo)
                .distanceToSqr(item.position());
        return movementSquared >= SWEEP_SAMPLE_SPACING * SWEEP_SAMPLE_SPACING;
    }

    private static Contact resolve(ItemEntity item, Level level, BlockPos seedPos) {
        return resolve(item, level, seedPos, null, null);
    }

    private static Contact resolve(
            ItemEntity item, Level level, BlockPos seedPos, Frame suppliedFrame) {
        return resolve(item, level, seedPos, suppliedFrame, null);
    }

    private static Contact resolve(
            ItemEntity item, Level level, BlockPos seedPos, Frame suppliedFrame,
            Object suppliedSubLevel) {
        return resolve(item, level, seedPos, suppliedFrame, suppliedSubLevel, true);
    }

    private static Contact resolve(
            ItemEntity item, Level level, BlockPos seedPos, Frame suppliedFrame,
            Object suppliedSubLevel, boolean requireColumnContainment) {
        BlockState seedState = level.getBlockState(seedPos);
        SinkingMedium seedMedium = ModBlocks.mediumOf(seedState.getBlock());
        if (seedMedium == null || !MudMediumRuntime.enabled(level, seedPos, seedMedium)) {
            return null;
        }

        Object subLevel = suppliedSubLevel != null
                ? suppliedSubLevel
                : suppliedFrame == null ? SableCompat.subLevelAtStorage(level, seedPos) : null;
        if (subLevel == null && isProjectedCallback(item, seedPos) && suppliedFrame == null) {
            return null;
        }
        Frame frame = suppliedFrame == null ? frame(item, subLevel) : suppliedFrame;
        if (frame == null) {
            return null;
        }
        boolean physicalized = frame.transform() != null;
        Direction surfaceDirection = surfaceDirection(seedState, seedMedium, frame.transform());
        BlockPos topPos = findBoundary(
                level, seedPos, surfaceDirection, surfaceDirection, physicalized);
        BlockPos bottomPos = findBoundary(
                level, seedPos, surfaceDirection.getOpposite(), surfaceDirection, physicalized);
        BlockState topState = level.getBlockState(topPos);
        BlockState bottomState = level.getBlockState(bottomPos);
        SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
        if (topMedium == null || !isEnabledMud(level, topPos, surfaceDirection, physicalized)
                || !isEnabledMud(level, bottomPos, surfaceDirection, physicalized)) {
            return null;
        }
        if (requireColumnContainment && !containsColumn(level, frame, topPos, bottomPos,
                topState, bottomState, topMedium, surfaceDirection,
                item.getBbWidth(), item.getBbHeight())) {
            return null;
        }
        return new Contact(
                level, subLevel, topPos, bottomPos, topState, bottomState,
                topMedium, surfaceDirection, frame, false);
    }

    private static Vec3 sweptContactPoint(Level level, BlockPos seedPos,
            BlockState seedState, SinkingMedium seedMedium,
            Vec3 from, Vec3 to, Vec3 fallback) {
        Direction surfaceDirection = MudBlock.surfaceDirection(seedState, seedMedium);
        BlockPos topPos = findBoundary(
                level, seedPos, surfaceDirection, surfaceDirection, false);
        BlockState topState = level.getBlockState(topPos);
        SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
        if (topMedium == null || !isEnabledMud(level, topPos, surfaceDirection, false)) {
            return fallback;
        }
        Vec3 surfaceNormal = normal(surfaceDirection);
        double surfaceCoordinate = boundaryCoordinate(
                topPos, MudBlock.localBounds(
                        level, topPos, topState, topMedium), surfaceDirection, true);
        double fromDepth = DroppedItemDynamics.depth(from, surfaceNormal, surfaceCoordinate);
        double toDepth = DroppedItemDynamics.depth(to, surfaceNormal, surfaceCoordinate);
        if (fromDepth >= 0.0D || toDepth < 0.0D) {
            return fallback;
        }
        double progress = Mth.clamp(fromDepth / (fromDepth - toDepth), 0.0D, 1.0D);
        return from.lerp(to, progress);
    }

    private static BlockPos findBoundary(Level level, BlockPos start, Direction direction,
            Direction expectedSurfaceDirection, boolean physicalized) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        for (int scanned = 0; scanned < MAXIMUM_COLUMN_BLOCKS; scanned++) {
            int nextY = cursor.getY() + direction.getStepY();
            if (nextY < level.getMinBuildHeight() || nextY >= level.getMaxBuildHeight()) {
                break;
            }
            cursor.move(direction);
            if (!isEnabledMud(level, cursor, expectedSurfaceDirection, physicalized)) {
                cursor.move(direction.getOpposite());
                break;
            }
        }
        return cursor.immutable();
    }

    private static boolean isEnabledMud(Level level, BlockPos pos, Direction expectedSurfaceDirection,
            boolean physicalized) {
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        return medium != null
                && MudMediumRuntime.enabled(level, pos, medium)
                && (physicalized && MudBlock.heightPixels(state, medium) >= 16
                        || MudBlock.surfaceDirection(state, medium) == expectedSurfaceDirection);
    }

    private static boolean containsColumn(Level level, Frame frame,
            BlockPos topPos, BlockPos bottomPos,
            BlockState topState, BlockState bottomState, SinkingMedium topMedium,
            Direction surfaceDirection, double itemWidth, double itemHeight) {
        Vec3 localPoint = frame.position();
        AABB topBounds = MudBlock.localBounds(
                level, topPos, topState, topMedium);
        SinkingMedium bottomMedium = ModBlocks.mediumOf(bottomState.getBlock());
        if (bottomMedium == null) {
            return false;
        }
        AABB bottomBounds = MudBlock.localBounds(
                level, bottomPos, bottomState, bottomMedium);
        double extent = Math.max(itemWidth, itemHeight) * 0.5D;
        double tangentTolerance = Math.max(0.04D, extent);
        double surfaceCoordinate = boundaryCoordinate(topPos, topBounds, surfaceDirection, true);
        double bottomCoordinate = boundaryCoordinate(bottomPos, bottomBounds, surfaceDirection, false);
        Vec3 surfaceNormal = normal(surfaceDirection);
        if (frame.transform() == null) {
            if (!insideTangentialBounds(
                    localPoint, topPos, topBounds, surfaceDirection, tangentTolerance)) {
                return false;
            }
            double depth = DroppedItemDynamics.depth(localPoint, surfaceNormal, surfaceCoordinate);
            return depth >= -Math.max(SURFACE_CONTACT_TOLERANCE, extent)
                    && depth <= Math.max(0.0D, surfaceCoordinate - bottomCoordinate) + extent;
        }

        // A physicalized craft can rotate independently of world gravity.  Project the item
        // through its world-vertical depth axis back onto the exposed face before checking the
        // local face bounds; testing the unprojected local point releases a settled item near a
        // rotated surface and makes it repeatedly fall back into the same mud.
        Vec3 depthAxis = depthAxis(frame, surfaceDirection);
        Vec3 surfacePoint = DroppedItemDynamics.positionAtDepthAlongAxis(
                frame.center(), surfaceNormal, surfaceCoordinate, depthAxis, 0.0D);
        if (!insideTangentialBounds(
                surfacePoint, topPos, topBounds, surfaceDirection, tangentTolerance)) {
            return false;
        }
        double depth = DroppedItemDynamics.depthFromSurfacePlane(
                frame.center(), surfaceNormal, surfaceCoordinate, depthAxis);
        double availableDepth = DroppedItemDynamics.availableDepthFromSurfacePlane(
                surfaceCoordinate, bottomCoordinate, surfaceNormal, depthAxis);
        return depth >= -Math.max(SURFACE_CONTACT_TOLERANCE, extent)
                && depth <= availableDepth + extent;
    }

    private static boolean insideTangentialBounds(Vec3 point, BlockPos pos, AABB bounds,
            Direction surfaceDirection, double tolerance) {
        return switch (surfaceDirection.getAxis()) {
            case X -> point.y >= pos.getY() + bounds.minY - tolerance
                    && point.y <= pos.getY() + bounds.maxY + tolerance
                    && point.z >= pos.getZ() + bounds.minZ - tolerance
                    && point.z <= pos.getZ() + bounds.maxZ + tolerance;
            case Y -> point.x >= pos.getX() + bounds.minX - tolerance
                    && point.x <= pos.getX() + bounds.maxX + tolerance
                    && point.z >= pos.getZ() + bounds.minZ - tolerance
                    && point.z <= pos.getZ() + bounds.maxZ + tolerance;
            case Z -> point.x >= pos.getX() + bounds.minX - tolerance
                    && point.x <= pos.getX() + bounds.maxX + tolerance
                    && point.y >= pos.getY() + bounds.minY - tolerance
                    && point.y <= pos.getY() + bounds.maxY + tolerance;
        };
    }

    private static double boundaryCoordinate(BlockPos pos, AABB bounds,
            Direction surfaceDirection, boolean surface) {
        return switch (surfaceDirection) {
            case UP -> pos.getY() + (surface ? bounds.maxY : bounds.minY);
            case DOWN -> -(pos.getY() + (surface ? bounds.minY : bounds.maxY));
            case NORTH -> -(pos.getZ() + (surface ? bounds.minZ : bounds.maxZ));
            case SOUTH -> pos.getZ() + (surface ? bounds.maxZ : bounds.minZ);
            case EAST -> pos.getX() + (surface ? bounds.maxX : bounds.minX);
            case WEST -> -(pos.getX() + (surface ? bounds.minX : bounds.maxX));
        };
    }

    private static Vec3 normal(Direction direction) {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static Direction surfaceDirection(BlockState state, SinkingMedium medium,
            SableCompat.RigidTransform transform) {
        return surfaceDirection(state, medium, transform, null);
    }

    private static Direction surfaceDirection(BlockState state, SinkingMedium medium,
            SableCompat.RigidTransform transform, Direction previousDirection) {
        if (transform == null) {
            return MudBlock.surfaceDirection(state, medium);
        }
        Vec3 localDown = transform.toLocalDirection(new Vec3(0.0D, -1.0D, 0.0D));
        if (localDown == null || localDown.lengthSqr() < 1.0E-8D) {
            return previousDirection == null
                    ? MudBlock.surfaceDirection(state, medium) : previousDirection;
        }
        Direction candidate = dominantDirection(localDown).getOpposite();
        if (previousDirection == null || candidate == previousDirection) {
            return candidate;
        }

        Vec3 localUp = localDown.scale(-1.0D).normalize();
        double previousAlignment = localUp.dot(normal(previousDirection));
        double candidateAlignment = localUp.dot(normal(candidate));
        return candidateAlignment > previousAlignment + PHYSICAL_SURFACE_SWITCH_HYSTERESIS
                ? candidate : previousDirection;
    }

    private static Direction dominantDirection(Vec3 vector) {
        double x = Math.abs(vector.x);
        double y = Math.abs(vector.y);
        double z = Math.abs(vector.z);
        if (x >= y && x >= z) {
            return vector.x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (y >= z) {
            return vector.y >= 0.0D ? Direction.UP : Direction.DOWN;
        }
        return vector.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private static Frame frame(ItemEntity item, Object subLevel) {
        if (subLevel == null) {
            return new Frame(
                    item.position(), item.getBoundingBox().getCenter(),
                    item.getDeltaMovement(), null);
        }
        SableCompat.RigidTransform transform = SableCompat.rigidTransform(subLevel);
        if (transform == null) {
            return null;
        }
        return frameAtWorldPosition(item, subLevel, item.position());
    }

    private static Frame frameAtWorldPosition(
            ItemEntity item, Object subLevel, Vec3 worldPosition) {
        SableCompat.RigidTransform transform = SableCompat.rigidTransform(subLevel);
        if (transform == null) {
            return null;
        }
        Vec3 centerOffset = item.getBoundingBox().getCenter().subtract(item.position());
        Vec3 position = transform.toLocal(worldPosition);
        Vec3 center = transform.toLocal(worldPosition.add(centerOffset));
        Vec3 motion = transform.toLocalDirection(item.getDeltaMovement());
        return position == null || center == null || motion == null
                ? null : new Frame(position, center, motion, transform);
    }

    private static boolean isProjectedCallback(ItemEntity item, BlockPos pos) {
        AABB bounds = item.getBoundingBox().inflate(1.5D);
        return pos.getX() + 1.0D < bounds.minX || pos.getX() > bounds.maxX
                || pos.getY() + 1.0D < bounds.minY || pos.getY() > bounds.maxY
                || pos.getZ() + 1.0D < bounds.minZ || pos.getZ() > bounds.maxZ;
    }

    record ContactHint(Level level, BlockPos pos, long gameTime) {
    }

    record Contact(
            Level level,
            Object subLevel,
            BlockPos topPos,
            BlockPos bottomPos,
            BlockState topState,
            BlockState bottomState,
            SinkingMedium medium,
            Direction surfaceDirection,
            Frame frame,
            boolean exactVolumeHit) {

        Contact withExactVolumeHit() {
            return exactVolumeHit ? this : new Contact(
                    level, subLevel, topPos, bottomPos, topState, bottomState,
                    medium, surfaceDirection, frame, true);
        }

        Vec3 surfaceNormal() {
            return normal(surfaceDirection);
        }

        double surfaceCoordinate() {
            return boundaryCoordinate(
                    topPos, MudBlock.localBounds(
                            level, topPos, topState, medium), surfaceDirection, true);
        }

        double bottomCoordinate() {
            SinkingMedium bottomMedium = ModBlocks.mediumOf(bottomState.getBlock());
            return bottomMedium == null ? surfaceCoordinate() : boundaryCoordinate(
                    bottomPos, MudBlock.localBounds(
                            level, bottomPos, bottomState, bottomMedium),
                    surfaceDirection, false);
        }

        double availableDepth() {
            return DroppedItemDynamics.availableDepthFromSurfacePlane(
                    surfaceCoordinate(), bottomCoordinate(), surfaceNormal(), depthAxis());
        }

        double depth(Vec3 position) {
            return DroppedItemDynamics.depthFromSurfacePlane(
                    position, surfaceNormal(), surfaceCoordinate(), depthAxis());
        }

        double inwardSpeed(Vec3 motion) {
            return DroppedItemDynamics.depthSpeed(motion, depthAxis());
        }

        Vec3 tangentialMotion(Vec3 motion) {
            Vec3 axis = depthAxis();
            return motion.subtract(axis.scale(motion.dot(axis)));
        }

        double itemDepth(Vec3 position, double itemWidth, double itemHeight) {
            return itemDepth(position, frame.center().subtract(frame.position()), itemWidth, itemHeight);
        }

        double itemDepth(Vec3 position, Vec3 centerOffset,
                double itemWidth, double itemHeight) {
            return depth(position.add(centerOffset)) + supportExtent(itemWidth, itemHeight);
        }

        Vec3 positionAtItemDepth(Vec3 position, double itemWidth, double itemHeight,
                double itemDepth) {
            return positionAtItemDepth(
                    position, frame.center().subtract(frame.position()), itemWidth, itemHeight, itemDepth);
        }

        Vec3 positionAtItemDepth(Vec3 position, Vec3 centerOffset,
                double itemWidth, double itemHeight, double itemDepth) {
            return position.add(depthAxis().scale(
                    this.itemDepth(position, centerOffset, itemWidth, itemHeight) - itemDepth));
        }

        boolean hasEntered(Vec3 position, double itemWidth, double itemHeight) {
            return itemDepth(position, itemWidth, itemHeight) >= SURFACE_CONTACT_TOLERANCE;
        }

        /**
         * Keeps an active item attached to its originating column while allowing arbitrary
         * depth recovery along world gravity. Only lateral departure releases this anchor.
         */
        boolean withinSurfaceFootprint(Vec3 position, Vec3 centerOffset,
                double itemWidth, double itemHeight) {
            AABB topBounds = MudBlock.localBounds(
                    level, topPos, topState, medium);
            double extent = Math.max(itemWidth, itemHeight) * 0.5D;
            double tangentTolerance = Math.max(0.04D, extent);
            Vec3 point = position.add(centerOffset);
            if (frame.transform() != null) {
                point = DroppedItemDynamics.positionAtDepthAlongAxis(
                        point, surfaceNormal(), surfaceCoordinate(), depthAxis(), 0.0D);
            }
            return insideTangentialBounds(
                    point, topPos, topBounds, surfaceDirection, tangentTolerance);
        }

        private double supportExtent(double itemWidth, double itemHeight) {
            Vec3 worldNormal = frame.toWorldMotion(depthAxis());
            if (worldNormal == null || worldNormal.lengthSqr() < 1.0E-8D) {
                worldNormal = surfaceNormal();
            } else {
                worldNormal = worldNormal.normalize();
            }
            return (Math.abs(worldNormal.x) + Math.abs(worldNormal.z)) * itemWidth * 0.5D
                    + Math.abs(worldNormal.y) * itemHeight * 0.5D;
        }

        private Vec3 depthAxis() {
            return DroppedItemContactResolver.depthAxis(frame, surfaceDirection);
        }
    }

    private static Vec3 depthAxis(Frame frame, Direction surfaceDirection) {
        Vec3 surfaceNormal = normal(surfaceDirection);
        if (frame.transform() == null) {
            return surfaceNormal;
        }
        Vec3 localUp = frame.transform().toLocalDirection(new Vec3(0.0D, 1.0D, 0.0D));
        if (localUp == null || localUp.lengthSqr() < 1.0E-8D) {
            return surfaceNormal;
        }
        Vec3 normalized = localUp.normalize();
        return surfaceNormal.dot(normalized) > 1.0E-4D ? normalized : surfaceNormal;
    }

    record Frame(Vec3 position, Vec3 center, Vec3 motion, SableCompat.RigidTransform transform) {
        Vec3 toWorldPosition(Vec3 localPosition) {
            return transform == null ? localPosition : transform.toWorld(localPosition);
        }

        Vec3 toWorldMotion(Vec3 localMotion) {
            return transform == null ? localMotion : transform.toWorldDirection(localMotion);
        }
    }
}
