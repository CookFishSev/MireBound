package com.fish.mirebound.tool;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.MudProbeBubblePayload;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Survival-safe probe that measures one continuous ray through mud volume.
 * Sampling at one skin pixel keeps partial and irregular shapes meaningful
 * without making normal use a hot path.
 */
public final class MudProbeItem extends Item {
    static final double MAX_RANGE = 3.0D;
    private static final double SAMPLE_STEP = 1.0D / 16.0D;
    private static final double SAMPLE_OFFSET = SAMPLE_STEP * 0.25D;
    private static final double RAY_STEP = 1.0D / 32.0D;
    private static final double CONTACT_TOLERANCE = 1.0D / 512.0D;
    private static final double BUBBLE_SURFACE_OFFSET = 0.004D;
    private static final double BUBBLE_VALIDATION_OFFSET = 0.012D;
    private static final double BUBBLE_MINIMUM_SPACING = 0.065D;
    private static final double SABLE_COLLISION_PATH_STEP = 0.5D;
    private static final int SABLE_COLLISION_BLOCK_LIMIT = 128;

    public MudProbeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack,
            int remainingUseDuration) {
        if (!(livingEntity instanceof Player player)) {
            return;
        }
        ProbeTarget target = findTarget(level, player);
        if (target == null) {
            player.displayClientMessage(
                    Component.translatable("message.mirebound.mud_probe.no_target"), true);
            return;
        }
        slowWhileProbing(player);
        if (level.isClientSide) {
            return;
        }
        Reading reading = measure(level, target);
        emitProbeParticles((ServerLevel) level, target);
        int heldTicks = Math.max(0, getUseDuration(stack, livingEntity) - remainingUseDuration);
        double reveal = elasticReveal(heldTicks);
        double targetDepth = reportedDepth(reading, target, player);
        double displayedDepth = targetDepth * reveal;
        player.displayClientMessage(resultMessage(
                reading, target.state().getBlock().getName(), displayedDepth,
                !reading.outOfRange() || reveal >= 0.995D), true);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity,
            int timeLeft) {
        if (!(livingEntity instanceof Player player) || level.isClientSide) {
            return;
        }
        ProbeTarget target = findTarget(level, player);
        if (target == null) {
            player.displayClientMessage(
                    Component.translatable("message.mirebound.mud_probe.no_target"), true);
            return;
        }
        Reading reading = measure(level, target);
        double depth = reportedDepth(reading, target, player);
        player.displayClientMessage(resultMessage(
                reading, target.state().getBlock().getName(), depth), true);
        player.getCooldowns().addCooldown(this, MudPhysicsSettings.mudProbeCooldownTicks());

        if (level instanceof ServerLevel serverLevel) {
            List<Vec3> bubblePoints = bubbleCluster(serverLevel, target);
            Vec3 bubbleCenter = bubblePoints.getFirst();
            PacketDistributor.sendToPlayersNear(
                    serverLevel, null, bubbleCenter.x, bubbleCenter.y, bubbleCenter.z, 32.0D,
                    new MudProbeBubblePayload(
                            staggerBubbles(serverLevel, bubblePoints),
                            target.worldSurfaceNormal(), target.worldSurfaceTangent(),
                            target.medium(),
                            target.pos()));
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72_000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    private static void slowWhileProbing(Player player) {
        Vec3 velocity = player.getDeltaMovement();
        double movementScale = MudPhysicsSettings.mudProbeMovementScale();
        player.setDeltaMovement(
                velocity.x * movementScale, velocity.y, velocity.z * movementScale);
    }

    private static double elasticReveal(int heldTicks) {
        double progress = Math.min(1.0D, Math.max(0.0D, heldTicks / 20.0D));
        double settled = 1.0D - Math.exp(-heldTicks / 7.0D);
        double overshoot = Math.sin(progress * Math.PI * 1.5D) * 0.035D * (1.0D - progress);
        return Math.min(1.0D, Math.max(0.0D, settled + overshoot));
    }

    private static ProbeTarget findTarget(Level level, Player player) {
        Vec3 origin = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        double interactionRange = Math.max(0.0D, player.blockInteractionRange());
        Vec3 end = origin.add(direction.scale(interactionRange));
        BlockHitResult collisionHit = level.clip(new ClipContext(
                origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double collisionDistance = collisionHit.getType() == HitResult.Type.BLOCK
                ? origin.distanceTo(collisionHit.getLocation()) : Double.POSITIVE_INFINITY;
        SableCompat.SinkingVolumeProbe sableProbe = SableCompat.sinkingVolumeProbe(
                level, new AABB(origin, end).inflate(RAY_STEP), player);
        double sableCollisionDistance = nearestSableCollisionDistance(
                sableProbe, origin, end);
        ProbeTarget nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        Object lastSableSubLevel = null;
        BlockPos lastSablePos = null;
        int samples = (int) Math.ceil(interactionRange / RAY_STEP);
        for (int index = 1; index <= samples; index++) {
            double distance = Math.min(interactionRange, index * RAY_STEP);
            Vec3 point = origin.add(direction.scale(distance));
            BlockPos pos = BlockPos.containing(point);
            if (!level.hasChunkAt(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
            if (medium != null && insideMudShape(level, state, medium, pos, point)) {
                VoxelShape shape = MudBlock.localShape(level, pos, state, medium);
                BlockHitResult mudHit = shape.clip(origin, end, pos);
                if (mudHit != null) {
                    double hitDistance = origin.distanceTo(mudHit.getLocation());
                    if (hitDistance <= interactionRange + CONTACT_TOLERANCE
                            && hitDistance < nearestDistance
                            && (collisionDistance >= hitDistance - CONTACT_TOLERANCE
                            || collisionHit.getBlockPos().equals(pos))) {
                        Direction surfaceNormal = mudHit.getDirection();
                        nearest = new ProbeTarget(
                                null,
                                pos,
                                mudHit.getLocation(),
                                surfaceNormal,
                                preferredSurfaceTangent(surfaceNormal),
                                state,
                                medium);
                        nearestDistance = hitDistance;
                    }
                }
            }

            SableCompat.SinkingSample sableSample = sableProbe.sample(point);
            if (sableSample == null
                    || sableSample.subLevel() == lastSableSubLevel
                    && sableSample.pos().equals(lastSablePos)) {
                continue;
            }
            lastSableSubLevel = sableSample.subLevel();
            lastSablePos = sableSample.pos();
            SableCompat.RigidTransform transform =
                    SableCompat.rigidTransform(sableSample.subLevel());
            Vec3 localOrigin = transform == null ? null : transform.toLocal(origin);
            Vec3 localEnd = transform == null ? null : transform.toLocal(end);
            if (localOrigin == null || localEnd == null) {
                continue;
            }
            VoxelShape sableShape = MudBlock.localShape(
                    level, sableSample.pos(), sableSample.state(), sableSample.medium());
            BlockHitResult sableHit = sableShape.clip(
                    localOrigin, localEnd, sableSample.pos());
            if (sableHit == null) {
                continue;
            }
            Vec3 worldHit = transform.toWorld(sableHit.getLocation());
            if (worldHit == null) {
                continue;
            }
            double hitDistance = origin.distanceTo(worldHit);
            if (hitDistance > interactionRange + CONTACT_TOLERANCE
                    || hitDistance >= nearestDistance
                    || collisionDistance < hitDistance - CONTACT_TOLERANCE
                    || sableCollisionDistance < hitDistance - CONTACT_TOLERANCE) {
                continue;
            }
            Direction surfaceNormal = sableHit.getDirection();
            nearest = new ProbeTarget(
                    transform,
                    sableSample.pos(),
                    sableHit.getLocation(),
                    surfaceNormal,
                    preferredSurfaceTangent(surfaceNormal),
                    sableSample.state(),
                    sableSample.medium());
            nearestDistance = hitDistance;
        }
        return nearest;
    }

    private static double nearestSableCollisionDistance(
            SableCompat.SinkingVolumeProbe probe, Vec3 origin, Vec3 end) {
        double length = origin.distanceTo(end);
        int steps = Math.max(1, (int) Math.ceil(length / SABLE_COLLISION_PATH_STEP));
        List<Vec3> path = new ArrayList<>(steps + 1);
        for (int index = 0; index <= steps; index++) {
            path.add(origin.lerp(end, index / (double) steps));
        }

        double nearest = Double.POSITIVE_INFINITY;
        for (SableCompat.SubLevelCollisionGeometry geometry : probe.collisionGeometry(
                List.of(path), CONTACT_TOLERANCE, SABLE_COLLISION_BLOCK_LIMIT)) {
            Vec3 localOrigin = geometry.transform().toLocal(origin);
            Vec3 localEnd = geometry.transform().toLocal(end);
            if (localOrigin == null || localEnd == null) {
                continue;
            }
            for (AABB box : geometry.localBoxes()) {
                Optional<Vec3> clipped = box.clip(localOrigin, localEnd);
                if (clipped.isEmpty()) {
                    continue;
                }
                Vec3 worldHit = geometry.transform().toWorld(clipped.get());
                if (worldHit != null) {
                    nearest = Math.min(nearest, origin.distanceTo(worldHit));
                }
            }
        }
        return nearest;
    }

    private static void emitProbeParticles(ServerLevel level, ProbeTarget target) {
        if (level.getGameTime() % 4L != 0L) {
            return;
        }
        Vec3 normal = target.worldSurfaceNormal();
        Vec3 tangent = target.worldSurfaceTangent();
        Vec3 bitangent = normal.cross(tangent).normalize();
        BlockState particleState = target.state();
        if (particleState.getBlock() instanceof AdaptiveMudBlock) {
            BlockState sourceState = AdaptiveMudBlock.sourceState(level, target.pos());
            if (sourceState != null) {
                particleState = sourceState;
            }
        }
        BlockParticleOption particle = new BlockParticleOption(
                ParticleTypes.BLOCK, particleState);
        for (int count = 0; count < 2; count++) {
            double first = (level.random.nextDouble() - 0.5D) * 0.16D;
            double second = (level.random.nextDouble() - 0.5D) * 0.16D;
            Vec3 point = target.worldSurfacePoint()
                    .add(tangent.scale(first))
                    .add(bitangent.scale(second))
                    .add(normal.scale(0.012D));
            level.sendParticles(particle, point.x, point.y, point.z, 1,
                    normal.x * 0.015D, normal.y * 0.015D, normal.z * 0.015D, 0.018D);
        }
    }

    private static List<Vec3> bubbleCluster(
            ServerLevel level, ProbeTarget target) {
        int minimum = MudPhysicsSettings.mudProbeMinimumBubbles();
        int maximum = MudPhysicsSettings.mudProbeMaximumBubbles();
        int desired = minimum + level.getRandom().nextInt(maximum - minimum + 1);
        double radius = MudPhysicsSettings.mudProbeBubbleRadius();
        Vec3 normal = Vec3.atLowerCornerOf(target.surfaceNormal().getNormal());
        Vec3 tangent = target.surfaceTangent();
        Vec3 bitangent = normal.cross(tangent).normalize();
        List<Vec3> localPoints = new ArrayList<>(desired);

        int attempts = Math.max(12, desired * 8);
        while (localPoints.size() < desired && attempts-- > 0) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = radius * (0.16D
                    + Math.sqrt(level.getRandom().nextDouble()) * 0.84D);
            Vec3 candidate = target.surfacePoint()
                    .add(tangent.scale(Math.cos(angle) * distance))
                    .add(bitangent.scale(Math.sin(angle) * distance))
                    .add(normal.scale(BUBBLE_SURFACE_OFFSET));
            if (isValidBubblePoint(level, candidate, normal, target.medium())
                    && farEnoughFromExisting(localPoints, candidate)) {
                localPoints.add(candidate);
            }
        }

        if (localPoints.isEmpty()) {
            localPoints.add(target.surfacePoint().add(
                    normal.scale(BUBBLE_SURFACE_OFFSET)));
        }
        List<Vec3> worldPoints = new ArrayList<>(localPoints.size());
        for (Vec3 point : localPoints) {
            Vec3 worldPoint = target.toWorldPoint(point);
            if (worldPoint != null) {
                worldPoints.add(worldPoint);
            }
        }
        if (worldPoints.isEmpty()) {
            worldPoints.add(target.worldSurfacePoint().add(
                    target.worldSurfaceNormal().scale(BUBBLE_SURFACE_OFFSET)));
        }
        return List.copyOf(worldPoints);
    }

    private static Vec3 preferredSurfaceTangent(Direction normal) {
        Vec3 surfaceNormal = Vec3.atLowerCornerOf(normal.getNormal());
        Vec3 tangent = new Vec3(0.0D, 1.0D, 0.0D)
                .subtract(surfaceNormal.scale(surfaceNormal.y));
        if (tangent.lengthSqr() < 1.0E-8D) {
            tangent = new Vec3(1.0D, 0.0D, 0.0D);
        }
        return tangent.normalize();
    }

    private static List<MudProbeBubblePayload.BubbleSpawn> staggerBubbles(
            ServerLevel level, List<Vec3> points) {
        int minimumInterval = MudPhysicsSettings.mudProbeMinimumBubbleIntervalTicks();
        int maximumInterval = MudPhysicsSettings.mudProbeMaximumBubbleIntervalTicks();
        int delay = level.getRandom().nextInt(minimumInterval + 1);
        List<MudProbeBubblePayload.BubbleSpawn> spawns = new ArrayList<>(points.size());
        for (Vec3 point : points) {
            spawns.add(MudProbeBubblePayload.BubbleSpawn.at(point, delay));
            delay += minimumInterval + level.getRandom().nextInt(
                    maximumInterval - minimumInterval + 1);
        }
        return List.copyOf(spawns);
    }

    private static boolean isValidBubblePoint(
            Level level, Vec3 point, Vec3 normal, SinkingMedium expectedMedium) {
        Vec3 inside = point.add(normal.scale(-BUBBLE_VALIDATION_OFFSET));
        BlockPos insidePos = BlockPos.containing(inside.x, inside.y, inside.z);
        if (!level.getChunkSource().hasChunk(insidePos.getX() >> 4, insidePos.getZ() >> 4)) {
            return false;
        }
        BlockState insideState = level.getBlockState(insidePos);
        SinkingMedium insideMedium = ModBlocks.mediumOf(insideState.getBlock());
        if (insideMedium != expectedMedium
                || !insideMudShape(
                        level, insideState, insideMedium, insidePos, inside)) {
            return false;
        }

        Vec3 outside = point.add(normal.scale(BUBBLE_VALIDATION_OFFSET));
        BlockPos outsidePos = BlockPos.containing(outside.x, outside.y, outside.z);
        BlockState outsideState = level.getBlockState(outsidePos);
        SinkingMedium outsideMedium = ModBlocks.mediumOf(outsideState.getBlock());
        return outsideMedium == null
                || !insideMudShape(
                        level, outsideState, outsideMedium, outsidePos, outside);
    }

    private static boolean farEnoughFromExisting(List<Vec3> points, Vec3 candidate) {
        double minimumDistanceSquared = BUBBLE_MINIMUM_SPACING * BUBBLE_MINIMUM_SPACING;
        for (Vec3 point : points) {
            if (point.distanceToSqr(candidate) < minimumDistanceSquared) {
                return false;
            }
        }
        return true;
    }

    static Reading measure(Level level, Vec3 surfacePoint, Direction surfaceNormal) {
        Vec3 inward = Vec3.atLowerCornerOf(surfaceNormal.getNormal()).scale(-1.0D);
        Set<SinkingMedium> media = EnumSet.noneOf(SinkingMedium.class);
        double continuousDepth = 0.0D;

        for (double distance = SAMPLE_OFFSET;
                distance <= MAX_RANGE + SAMPLE_STEP + SAMPLE_OFFSET;
                distance += SAMPLE_STEP) {
            Vec3 sample = surfacePoint.add(inward.scale(distance));
            BlockPos pos = BlockPos.containing(sample.x, sample.y, sample.z);
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                return new Reading(continuousDepth, true, Set.copyOf(media));
            }

            BlockState state = level.getBlockState(pos);
            SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
            if (medium == null
                    || !insideMudShape(level, state, medium, pos, sample)) {
                break;
            }
            media.add(medium);
            if (distance > MAX_RANGE) {
                return new Reading(MAX_RANGE, true, Set.copyOf(media));
            }
            continuousDepth = distance + (SAMPLE_STEP - SAMPLE_OFFSET);
        }

        double pixelDepth = Math.min(
                MAX_RANGE,
                Math.ceil((continuousDepth - 1.0E-8D) / SAMPLE_STEP) * SAMPLE_STEP);
        return new Reading(Math.max(0.0D, pixelDepth), false, Set.copyOf(media));
    }

    private static Reading measure(Level level, ProbeTarget target) {
        return measure(level, target.surfacePoint(), target.surfaceNormal());
    }

    private static boolean insideMudShape(
            Level level, BlockState state, SinkingMedium medium,
            BlockPos pos, Vec3 point) {
        Vec3 local = point.subtract(pos.getX(), pos.getY(), pos.getZ());
        return MudBlock.containsLocalPoint(
                level, pos, state, medium, local, CONTACT_TOLERANCE);
    }

    private static double reportedDepth(Reading reading, ProbeTarget target, Player player) {
        if (reading.outOfRange()) {
            return MAX_RANGE;
        }
        long seed = player.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 17)
                ^ target.pos().asLong();
        seed ^= seed >>> 30;
        seed *= 0xBF58476D1CE4E5B9L;
        seed ^= seed >>> 27;
        seed *= 0x94D049BB133111EBL;
        seed ^= seed >>> 31;
        return applyDepthError(reading.depth(), seed, MudPhysicsSettings.mudProbeDepthError());
    }

    static double applyDepthError(double depth, long seed, double maximumError) {
        double unit = (seed >>> 11) * 0x1.0p-53;
        double error = (unit * 2.0D - 1.0D) * Math.max(0.0D, maximumError);
        return Math.max(0.0D, Math.min(MAX_RANGE, depth + error));
    }

    private static Component resultMessage(
            Reading reading, Component mediumName, double depth) {
        return resultMessage(reading, mediumName, depth, true);
    }

    private static Component resultMessage(
            Reading reading, Component mediumName, double depth,
            boolean showOutOfRange) {
        if (reading.outOfRange() && showOutOfRange) {
            return Component.translatable(
                    "message.mirebound.mud_probe.out_of_range",
                    mediumName,
                    format(MAX_RANGE));
        }
        return Component.translatable(
                "message.mirebound.mud_probe.result",
                mediumName,
                format(depth));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mirebound.mud_probe.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mirebound.mud_probe.tooltip.2")
                .withStyle(ChatFormatting.DARK_GREEN));
    }

    record Reading(double depth, boolean outOfRange, Set<SinkingMedium> media) {
    }

    private record ProbeTarget(
            SableCompat.RigidTransform transform,
            BlockPos pos,
            Vec3 surfacePoint,
            Direction surfaceNormal,
            Vec3 surfaceTangent,
            BlockState state,
            SinkingMedium medium) {
        private Vec3 toWorldPoint(Vec3 point) {
            return transform == null ? point : transform.toWorld(point);
        }

        private Vec3 toWorldDirection(Vec3 direction) {
            Vec3 world = transform == null ? direction : transform.toWorldDirection(direction);
            return world == null || world.lengthSqr() < 1.0E-8D
                    ? direction.normalize() : world.normalize();
        }

        private Vec3 worldSurfacePoint() {
            Vec3 world = toWorldPoint(surfacePoint);
            return world == null ? surfacePoint : world;
        }

        private Vec3 worldSurfaceNormal() {
            return toWorldDirection(Vec3.atLowerCornerOf(surfaceNormal.getNormal()));
        }

        private Vec3 worldSurfaceTangent() {
            return toWorldDirection(surfaceTangent);
        }
    }
}
