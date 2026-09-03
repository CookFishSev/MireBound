package com.fish.mirebound.mud;

import com.fish.mirebound.entitycoverage.EntityMudCoverageService;
import com.fish.mirebound.mixin.mud.PathNavigationMudAccessor;
import com.fish.mirebound.registry.ModBlocks;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Owns non-player mud navigation, entry, damping, sinking, and temporary support. */
public final class MudMobPhysics {
    private static final double MAXIMUM_SWEPT_BROADPHASE_DISTANCE_SQR = 4.0D;
    private static final int MAXIMUM_COMPATIBILITY_CONTACT_CELLS = 96;
    // SINKING_STATES and APPLIED_TICKS are reached from two threads in single-player: the
    // integrated server tick writes them, while applyMudEffects() also admits the client thread
    // for locally controlled mounts and getCollisionShape() reads them during client collision.
    // Unsynchronized WeakHashMap resizing is not safe under that overlap. Weak keys are still
    // required, so ConcurrentHashMap is not a substitute. LAST_REPLAN_TICKS is server-only today
    // and is wrapped too, so the whole class has one access rule instead of a per-field exception.
    private static final Map<Mob, Long> LAST_REPLAN_TICKS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LivingEntity, SinkingState> SINKING_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LivingEntity, Long> APPLIED_TICKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MudMobPhysics() {
    }

    static void applyMudEffects(LivingEntity entity, Level level, BlockPos pos,
            BlockState state, SinkingMedium medium) {
        EntityMudCoverageService.applyContact(entity, level, pos, state, medium);
        if (level.isClientSide() && !entity.isControlledByLocalInstance()) {
            return;
        }
        long gameTime = level.getGameTime();
        Long previousAppliedTick = APPLIED_TICKS.put(entity, gameTime);
        if (previousAppliedTick != null && previousAppliedTick == gameTime) {
            return;
        }
        if (!MudBlock.supportsVerticalSinking(state, medium)) {
            dampenEntity(entity, medium.entityHorizontalScale(), medium.entitySinkSpeed());
            return;
        }

        BlockPos topPos = MudColumnResolver.findTop(level, pos);
        BlockState topState = level.getBlockState(topPos);
        SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
        if (topMedium == null || !MudBlock.supportsVerticalSinking(topState, topMedium)) {
            topState = state;
            topMedium = medium;
            topPos = pos;
        }
        BlockPos bottomPos = MudColumnResolver.findBottom(level, topPos);
        double surfaceY = topPos.getY() + MudBlock.surfaceHeight(topState, topMedium);
        double targetFootY = targetFootY(
                surfaceY, bottomPos.getY(), entity.getBbHeight(), topMedium.maxSinkDepthFactor());
        double currentFootY = entity.getBoundingBox().minY;

        SinkingState sinking = SINKING_STATES.get(entity);
        if (sinking == null || sinking.level != level || sinking.medium != topMedium
                || sinking.topPos.distManhattan(topPos) > 1) {
            sinking = new SinkingState(level, topMedium, topPos, targetFootY,
                    currentFootY, level.getGameTime());
            SINKING_STATES.put(entity, sinking);
        } else {
            sinking.targetFootY = targetFootY;
            sinking.lastContactTick = level.getGameTime();
            sinking.supportY = Math.max(
                    targetFootY, currentFootY - Math.abs(topMedium.entitySinkSpeed()));
        }

        double depthFactor = Mth.clamp(
                (surfaceY - currentFootY) / Math.max(0.10D, entity.getBbHeight()), 0.0D, 1.25D);
        double horizontalScale = Math.min(
                topMedium.entityHorizontalScale(), topMedium.horizontalScale(depthFactor));
        dampenEntity(entity, horizontalScale, topMedium.entitySinkSpeed());
        if (currentFootY <= targetFootY + 0.015D && entity.getDeltaMovement().y < 0.0D) {
            entity.setDeltaMovement(entity.getDeltaMovement().x, 0.0D, entity.getDeltaMovement().z);
            sinking.supportY = targetFootY;
        }
    }

    /** Prevents cached or direct mob movement from entering a mud volume. */
    public static boolean shouldBlockMove(Mob mob, MoveControl moveControl) {
        if (mob.level().isClientSide() || mob.isNoAi() || !moveControl.hasWanted()) {
            return false;
        }

        PathNavigation navigation = mob.getNavigation();
        Path path = navigation.getPath();
        if (path == null || path.isDone()) {
            boolean blocked = directMoveTouchesMud(mob, moveControl);
            if (PhysicsTraceLog.anyEnabled()) {
                PhysicsTraceLog.traceMob(mob, "move-control",
                        "wanted=true path=none directMud=" + blocked
                                + " wantedPos=(" + String.format(Locale.ROOT, "%.3f,%.3f,%.3f",
                                        moveControl.getWantedX(), moveControl.getWantedY(),
                                        moveControl.getWantedZ()) + ")");
            }
            return blocked;
        }
        Node nextNode = path.getNextNode();
        if (nextNode == null) {
            if (PhysicsTraceLog.anyEnabled()) {
                PhysicsTraceLog.traceMob(mob, "move-control",
                        "wanted=true path=active next=none blocked=false");
            }
            return false;
        }
        BlockPos nextPos = new BlockPos(nextNode.x, nextNode.y, nextNode.z);
        boolean blocked = isMudBlockAt(mob.level(), nextPos);
        if (PhysicsTraceLog.anyEnabled()) {
            PhysicsTraceLog.traceMob(mob, "move-control",
                    "wanted=true path=active next=" + nextPos + " nextMud=" + blocked
                            + " pathIndex=" + path.getNextNodeIndex());
        }
        return blocked;
    }

    /** Treats both a mud block and the walk node directly above it as blocked. */
    public static boolean isPathBlocked(BlockGetter level, BlockPos pathPos) {
        return isPathBlockingBlock(level, pathPos)
                || isPathBlockingBlock(level, pathPos.below());
    }

    public static boolean replanAroundMud(Mob mob, MoveControl moveControl) {
        long gameTime = mob.level().getGameTime();
        Long lastReplan = LAST_REPLAN_TICKS.get(mob);
        if (lastReplan != null && gameTime - lastReplan < 5L) {
            PathNavigation current = mob.getNavigation();
            return current.getPath() != null && !current.getPath().isDone();
        }
        LAST_REPLAN_TICKS.put(mob, gameTime);

        PathNavigation navigation = mob.getNavigation();
        Path currentPath = navigation.getPath();
        if (currentPath == null || currentPath.isDone()) {
            boolean movedToTarget = navigation.moveTo(
                    moveControl.getWantedX(),
                    moveControl.getWantedY(),
                    moveControl.getWantedZ(),
                    moveControl.getSpeedModifier());
            if (movedToTarget) {
                return true;
            }
        }
        if (navigation instanceof PathNavigationMudAccessor accessor) {
            accessor.mirebound$setLastRecompute(Long.MIN_VALUE);
        }
        navigation.recomputePath();
        Path replanned = navigation.getPath();
        return replanned != null && !replanned.isDone();
    }

    /** Clips only the first horizontal step from safe ground into mud. */
    public static Vec3 clipMovement(Mob mob, Vec3 movement) {
        if (mob.level().isClientSide() || mob.isNoAi() || mob.isPassenger()
                || movement.horizontalDistanceSqr() < 1.0E-8D
                || movement.y < -0.035D) {
            return movement;
        }
        SinkingState active = SINKING_STATES.get(mob);
        if (active != null && active.level == mob.level()
                && active.lastContactTick >= mob.level().getGameTime() - 2L) {
            return movement;
        }

        AABB original = mob.getBoundingBox();
        double distance = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        if (movement.horizontalDistanceSqr() <= MAXIMUM_SWEPT_BROADPHASE_DISTANCE_SQR) {
            AABB sweptBounds = original.minmax(original.move(movement)).inflate(0.012D);
            if (!intersectsMudVolume(mob.level(), sweptBounds)) {
                return movement;
            }
        }
        int samples = Mth.clamp((int) Math.ceil(distance / 0.12D), 2, 24);
        double firstHit = -1.0D;
        for (int index = 1; index <= samples; index++) {
            double progress = index / (double) samples;
            if (intersectsMudVolume(mob.level(), original.move(
                    movement.x * progress, movement.y * progress, movement.z * progress))) {
                firstHit = progress;
                break;
            }
        }
        if (firstHit < 0.0D) {
            if (PhysicsTraceLog.anyEnabled()) {
                PhysicsTraceLog.traceMob(mob, "move-clip",
                        "hit=false movement=(" + String.format(Locale.ROOT, "%.4f,%.4f,%.4f",
                                movement.x, movement.y, movement.z) + ") samples=" + samples);
            }
            return movement;
        }

        double safe = 0.0D;
        double unsafe = firstHit;
        for (int index = 0; index < 7; index++) {
            double middle = (safe + unsafe) * 0.5D;
            if (intersectsMudVolume(mob.level(), original.move(
                    movement.x * middle, movement.y * middle, movement.z * middle))) {
                unsafe = middle;
            } else {
                safe = middle;
            }
        }
        if (mob.getNavigation().getPath() != null || mob.getMoveControl().hasWanted()) {
            replanAroundMud(mob, mob.getMoveControl());
        }
        double clipped = Math.max(0.0D, safe - 0.004D);
        if (PhysicsTraceLog.anyEnabled()) {
            PhysicsTraceLog.traceMob(mob, "move-clip",
                    "hit=true first=" + String.format(Locale.ROOT, "%.4f", firstHit)
                            + " safe=" + String.format(Locale.ROOT, "%.4f", safe)
                            + " clipped=" + String.format(Locale.ROOT, "%.4f", clipped)
                            + " movement=(" + String.format(Locale.ROOT, "%.4f,%.4f,%.4f",
                                    movement.x, movement.y, movement.z) + ")");
        }
        return new Vec3(movement.x * clipped, movement.y, movement.z * clipped);
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)
                || entity instanceof Player
                || entity.level().isClientSide()) {
            return;
        }

        if (!(living instanceof Mob mob) || mob.isNoAi() || mob.isPassenger()) {
            if (coverageFallbackDue(living.tickCount, living.getId())) {
                MobContact contact = findContact(living, living.level());
                if (contact != null) {
                    EntityMudCoverageService.applyContact(
                            living, living.level(), contact.pos(),
                            contact.state(), contact.medium());
                }
            }
            return;
        }

        Level level = mob.level();
        MobContact contact = findContact(mob, level);
        if (contact == null) {
            SINKING_STATES.remove(mob);
            APPLIED_TICKS.remove(mob);
            return;
        }
        applyMudEffects(mob, level, contact.pos(), contact.state(), contact.medium());
    }

    /** Returns temporary support only after a non-player entity has entered mud. */
    public static VoxelShape collisionShape(Level level, BlockPos pos, BlockState state,
            SinkingMedium medium, LivingEntity entity) {
        SinkingState sinking = SINKING_STATES.get(entity);
        if (sinking == null || sinking.level != level
                || sinking.lastContactTick < level.getGameTime() - 2L) {
            if (sinking != null && sinking.level == level) {
                SINKING_STATES.remove(entity);
            }
            return null;
        }
        double supportTop = sinking.supportY - pos.getY();
        double blockTop = MudBlock.surfaceHeight(state, medium);
        if (supportTop <= 1.0E-4D) {
            return null;
        }
        if (supportTop >= blockTop - 1.0E-4D) {
            return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, blockTop, 1.0D);
        }
        return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D,
                Mth.clamp(supportTop, 0.0D, blockTop), 1.0D);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        LAST_REPLAN_TICKS.clear();
        SINKING_STATES.clear();
        APPLIED_TICKS.clear();
    }

    static double targetFootY(double surfaceY, double bottomBlockY,
            double entityHeight, double maxDepthFactor) {
        double availableDepth = Math.max(0.05D, surfaceY - bottomBlockY);
        double maximumDepth = Math.min(
                availableDepth - 0.02D,
                Math.max(0.08D, entityHeight * maxDepthFactor));
        return surfaceY - Math.max(0.04D, maximumDepth);
    }

    private static boolean isMudBlockAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        return medium != null && MudMediumRuntime.enabled(level, pos, medium);
    }

    private static boolean isPathBlockingBlock(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null) {
            return false;
        }
        boolean enabled = !(level instanceof Level world)
                || MudMediumRuntime.enabled(world, pos, medium);
        return enabled
                && MudBlock.supportsVerticalSinking(state, medium)
                && !MudBlock.localShape(level, pos, state, medium).isEmpty();
    }

    private static boolean directMoveTouchesMud(Mob mob, MoveControl moveControl) {
        double deltaX = moveControl.getWantedX() - mob.getX();
        double deltaY = moveControl.getWantedY() - mob.getY();
        double deltaZ = moveControl.getWantedZ() - mob.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (!Double.isFinite(distance) || distance < 0.04D) {
            return false;
        }
        int steps = Mth.clamp((int) Math.ceil(distance / 0.35D), 1, 32);
        AABB original = mob.getBoundingBox();
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            AABB swept = original.move(deltaX * progress, deltaY * progress, deltaZ * progress)
                    .inflate(0.012D);
            if (intersectsMudVolume(mob.level(), swept)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersectsMudVolume(Level level, AABB bounds) {
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos max = BlockPos.containing(
                bounds.maxX - 1.0E-7D,
                bounds.maxY - 1.0E-7D,
                bounds.maxZ - 1.0E-7D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
                    if (medium == null || !MudMediumRuntime.enabled(level, cursor, medium)) {
                        continue;
                    }
                    if (intersectsMudCell(level, cursor, state, medium, bounds)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean intersectsMudCell(BlockGetter level, BlockPos pos, BlockState state,
            SinkingMedium medium, AABB bounds) {
        VoxelShape mudShape = MudBlock.localShape(level, pos, state, medium);
        if (mudShape.isEmpty()) {
            return false;
        }
        if (Shapes.joinIsNotEmpty(
                mudShape,
                Shapes.create(bounds.move(-pos.getX(), -pos.getY(), -pos.getZ())),
                BooleanOp.AND)) {
            return true;
        }
        if (!MudBlock.supportsVerticalSinking(state, medium)) {
            return false;
        }
        double surfaceY = pos.getY() + MudBlock.surfaceHeight(state, medium);
        if (bounds.minY > surfaceY + 0.065D || bounds.maxY < surfaceY - 0.012D) {
            return false;
        }
        AABB supportProbe = new AABB(
                bounds.minX,
                surfaceY - 0.012D,
                bounds.minZ,
                bounds.maxX,
                surfaceY + 0.008D,
                bounds.maxZ);
        return Shapes.joinIsNotEmpty(
                mudShape,
                Shapes.create(supportProbe.move(-pos.getX(), -pos.getY(), -pos.getZ())),
                BooleanOp.AND);
    }

    static boolean coverageFallbackDue(int tickCount, int entityId) {
        return Math.floorMod(tickCount + entityId, 5) == 0;
    }

    private static MobContact findContact(LivingEntity entity, Level level) {
        AABB probe = entity.getBoundingBox()
                .inflate(0.025D, 0.0D, 0.025D)
                .expandTowards(0.0D, -0.085D, 0.0D);
        BlockPos min = BlockPos.containing(probe.minX, probe.minY, probe.minZ);
        BlockPos max = BlockPos.containing(
                probe.maxX - 1.0E-7D,
                probe.maxY - 1.0E-7D,
                probe.maxZ - 1.0E-7D);
        long cellCount = (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        if (cellCount > MAXIMUM_COMPATIBILITY_CONTACT_CELLS) {
            return findSampledContact(level, probe, min, max);
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
                    if (medium == null || !MudMediumRuntime.enabled(level, cursor, medium)) {
                        continue;
                    }
                    if (intersectsMudCell(level, cursor, state, medium, probe)) {
                        return new MobContact(cursor.immutable(), state, medium);
                    }
                }
            }
        }
        return null;
    }

    private static MobContact findSampledContact(
            Level level, AABB probe, BlockPos min, BlockPos max) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int xSamples = Math.min(3, max.getX() - min.getX() + 1);
        int ySamples = Math.min(4, max.getY() - min.getY() + 1);
        int zSamples = Math.min(3, max.getZ() - min.getZ() + 1);
        for (int yi = 0; yi < ySamples; yi++) {
            int y = sampledCoordinate(min.getY(), max.getY(), yi, ySamples);
            for (int xi = 0; xi < xSamples; xi++) {
                int x = sampledCoordinate(min.getX(), max.getX(), xi, xSamples);
                for (int zi = 0; zi < zSamples; zi++) {
                    int z = sampledCoordinate(min.getZ(), max.getZ(), zi, zSamples);
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
                    if (medium != null
                            && MudMediumRuntime.enabled(level, cursor, medium)
                            && intersectsMudCell(level, cursor, state, medium, probe)) {
                        return new MobContact(cursor.immutable(), state, medium);
                    }
                }
            }
        }
        return null;
    }

    static int sampledCoordinate(int minimum, int maximum, int index, int count) {
        if (count <= 1 || minimum >= maximum) {
            return minimum;
        }
        return minimum + (int) Math.round(
                (maximum - minimum) * index / (double) (count - 1));
    }

    private static void dampenEntity(Entity entity, double horizontalScale, double sinkSpeed) {
        Vec3 motion = entity.getDeltaMovement();
        double limitedVertical = sinkSpeed < 0.0D
                ? Math.max(motion.y, sinkSpeed)
                : motion.y;
        entity.setDeltaMovement(
                motion.x * horizontalScale, limitedVertical, motion.z * horizontalScale);
        entity.resetFallDistance();
    }

    private static final class SinkingState {
        private final Level level;
        private final SinkingMedium medium;
        private final BlockPos topPos;
        private double targetFootY;
        private double supportY;
        private long lastContactTick;

        private SinkingState(Level level, SinkingMedium medium, BlockPos topPos,
                double targetFootY, double supportY, long lastContactTick) {
            this.level = level;
            this.medium = medium;
            this.topPos = topPos;
            this.targetFootY = targetFootY;
            this.supportY = supportY;
            this.lastContactTick = lastContactTick;
        }
    }

    private record MobContact(BlockPos pos, BlockState state, SinkingMedium medium) {
    }
}
