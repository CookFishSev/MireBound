package com.fish.mirebound.stain;

import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.CoverageDebugLog;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.PhysicsTraceLog;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
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

/** Owns footprint cadence, source transfer, placement, and visual feedback. */
public final class MudFootprintSystem {
    private static final double LEG_SIDE_OFFSET = 0.13D;
    private static final float COVERAGE_THRESHOLD = 0.070F;
    private static final float BARE_FOOTPRINT_SIZE = 0.25F;
    private static final float BOOT_FOOTPRINT_SIZE = 0.43F;
    private static final double TELEPORT_DISTANCE = 1.50D;
    private static final int MIN_INTERVAL_TICKS = 3;

    private MudFootprintSystem() {
    }

    public static void tick(ServerPlayer player, MudPlayerData data) {
        double x = player.getX();
        double z = player.getZ();
        SourceSample leftSample = sourceSample(player, data, MudBodyPart.LEFT_LEG);
        SourceSample rightSample = sourceSample(player, data, MudBodyPart.RIGHT_LEG);
        data.leftFootprintResidue = leftSample.strength();
        data.rightFootprintResidue = rightSample.strength();
        if (data.footprintTrackingInitialized
                && !data.inMud
                && leftSample.strength() <= COVERAGE_THRESHOLD
                && rightSample.strength() <= COVERAGE_THRESHOLD) {
            data.footprintLastX = x;
            data.footprintLastZ = z;
            data.footprintTravel = 0.0D;
            return;
        }

        if (!data.footprintTrackingInitialized) {
            data.footprintTrackingInitialized = true;
            data.footprintLastX = x;
            data.footprintLastZ = z;
            data.leftFootprintResidue = leftSample.strength();
            data.rightFootprintResidue = rightSample.strength();
            return;
        }

        double dx = x - data.footprintLastX;
        double dz = z - data.footprintLastZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        data.footprintLastX = x;
        data.footprintLastZ = z;
        if (distance > 1.0E-4D && distance <= TELEPORT_DISTANCE) {
            data.footprintDirectionX = dx / distance;
            data.footprintDirectionZ = dz / distance;
            data.footprintDirectionInitialized = true;
        }

        if (data.inMud || distance > TELEPORT_DISTANCE) {
            data.footprintTravel = 0.0D;
            return;
        }
        if (!canLeaveFootprints(player)) {
            return;
        }

        boolean leftDirty = data.leftFootprintResidue > COVERAGE_THRESHOLD;
        boolean rightDirty = data.rightFootprintResidue > COVERAGE_THRESHOLD;
        if (!leftDirty && !rightDirty) {
            data.footprintTravel = 0.0D;
            return;
        }

        double stride = stride(player);
        data.footprintTravel = Math.min(stride * 1.5D, data.footprintTravel + distance);
        if (data.footprintTravel < stride
                || ticksSince(player.tickCount, data.lastFootprintTick) < MIN_INTERVAL_TICKS) {
            return;
        }

        boolean left = data.nextFootprintLeft;
        if (left && !leftDirty) {
            left = false;
        } else if (!left && !rightDirty) {
            left = true;
        }
        SourceSample sample = left ? leftSample : rightSample;
        float residue = left ? data.leftFootprintResidue : data.rightFootprintResidue;
        float imprintStrength = Mth.clamp(
                (residue - COVERAGE_THRESHOLD) / (1.0F - COVERAGE_THRESHOLD),
                0.0F,
                1.0F);
        if (!data.footprintDirectionInitialized
                || !tryPlace(player, left, sample.medium(), sample.visualSource(),
                        sample.size(), imprintStrength,
                        new Vec3(data.footprintDirectionX, 0.0D, data.footprintDirectionZ))) {
            return;
        }

        float residueCost = residueCost(sample.medium(), stride);
        consumeSource(player, data, sample, residueCost);
        if (left) {
            data.leftFootprintResidue = Math.max(0.0F, data.leftFootprintResidue - residueCost);
        } else {
            data.rightFootprintResidue = Math.max(0.0F, data.rightFootprintResidue - residueCost);
        }
        data.footprintTravel = Math.max(0.0D, data.footprintTravel - stride);
        data.lastFootprintTick = player.tickCount;
        data.nextFootprintLeft = !left;
    }

    private static SourceSample sourceSample(
            ServerPlayer player, MudPlayerData data, MudBodyPart part) {
        EquipmentSlot armorSlot = outerFootArmorSlot(player, part);
        if (armorSlot != null) {
            ArmorMudData armorData = ArmorMudManager.data(player.getItemBySlot(armorSlot));
            MudFootprintSampler.Sample sample = MudFootprintSampler.sample(
                    player.level(), armorData, part, data.footprintMediumWeightsScratch());
            return new SourceSample(part, armorSlot, sample.strength(),
                    sample.medium(), sample.visualSource(), BOOT_FOOTPRINT_SIZE);
        }
        MudFootprintSampler.Sample sample = MudFootprintSampler.sample(player.level(), data, part);
        return new SourceSample(part, null, sample.strength(),
                sample.medium(), sample.visualSource(), BARE_FOOTPRINT_SIZE);
    }

    private static EquipmentSlot outerFootArmorSlot(ServerPlayer player, MudBodyPart part) {
        EquipmentSlot slot = EquipmentSlot.FEET;
        var stack = player.getItemBySlot(slot);
        return ArmorMudManager.validArmor(stack, slot)
                && ArmorMudManager.slotOwnsSurface(slot, part, MudSurface.BOTTOM, 0)
                        ? slot
                        : null;
    }

    private static void consumeSource(
            ServerPlayer player, MudPlayerData data, SourceSample source, float amount) {
        if (source.slot() == null) {
            MudFootprintSampler.fadeSkinSource(data, source.part(), amount);
            return;
        }
        var stack = player.getItemBySlot(source.slot());
        if (!ArmorMudManager.validArmor(stack, source.slot())) {
            return;
        }
        ArmorMudData.Builder builder = ArmorMudManager.data(stack).toBuilder();
        MudFootprintSampler.fadeArmorSource(builder, source.part(), amount);
        if (builder.changed()) {
            ArmorMudManager.store(stack, builder.build());
        }
    }

    private static boolean canLeaveFootprints(ServerPlayer player) {
        return (player.onGround() || SableCompat.isTracking(player))
                && !player.isPassenger()
                && !player.isFallFlying()
                && !player.isSwimming()
                && !player.isInWaterOrBubble()
                && !player.isSpectator()
                && !player.getAbilities().flying;
    }

    private static double stride(ServerPlayer player) {
        if (player.isCrouching()) {
            return 0.38D;
        }
        return player.isSprinting() ? 0.72D : 0.56D;
    }

    private static float residueCost(SinkingMedium medium, double stride) {
        float materialScale = switch (medium) {
            case TAR -> 0.76F;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    SILT, SOUL_SILT, JUNGLE_QUICKSAND -> 1.18F;
            case LIVING_SLIME -> 1.08F;
            default -> 1.0F;
        };
        float fullResidue = 1.0F - COVERAGE_THRESHOLD;
        return fullResidue * (float) (stride * 2.0D)
                / MudPhysicsSettings.footprintTrailDistanceBlocks()
                * materialScale;
    }

    private static boolean tryPlace(
            ServerPlayer player, boolean left, SinkingMedium medium,
            long visualSource,
            float footprintSize, float imprintStrength, Vec3 movementDirection) {
        double side = left ? LEG_SIDE_OFFSET : -LEG_SIDE_OFFSET;
        Vec3 footPoint = movementPoint(player, movementDirection, side, 0.02D, 0.07D);
        Placement placement = findPlacement(player, footPoint, movementDirection);
        if (placement == null) {
            spawnParticles(player.serverLevel(), footPoint, medium,
                    visualSource, imprintStrength);
            if (PhysicsTraceLog.enabled(player)) {
                PhysicsTraceLog.traceDecal(player, "footprint result=no-surface foot="
                        + (left ? "left" : "right")
                        + " point=" + CoverageDebugLog.vec(footPoint));
            }
            return false;
        }

        ServerLevel level = player.serverLevel();
        BlockPos footprintPos = placement.containerPos();
        BlockState current = MudDecalAccess.state(level, placement.subLevel(), footprintPos);
        boolean createdContainer = false;
        if (current.getBlock() == ModBlocks.MUD_FOOTPRINT.get()
                && !(MudDecalAccess.blockEntity(level, placement.subLevel(), footprintPos)
                        instanceof MudFootprintBlockEntity)) {
            MudDecalAccess.removeContainer(level, placement.subLevel(), footprintPos);
            current = MudDecalAccess.state(level, placement.subLevel(), footprintPos);
        }
        if (current.isAir()) {
            if (!MudDecalAccess.placeContainer(level, placement.subLevel(), footprintPos)) {
                if (PhysicsTraceLog.enabled(player)) {
                    PhysicsTraceLog.traceDecal(player, "footprint result=set-block-failed sable="
                            + (placement.subLevel() != null)
                            + " pos=" + blockPosString(footprintPos));
                }
                return false;
            }
            createdContainer = true;
        } else if (current.getBlock() != ModBlocks.MUD_FOOTPRINT.get()) {
            return false;
        }

        if (!(MudDecalAccess.blockEntity(level, placement.subLevel(), footprintPos)
                instanceof MudFootprintBlockEntity blockEntity)) {
            if (createdContainer) {
                MudDecalAccess.removeContainer(level, placement.subLevel(), footprintPos);
            }
            if (PhysicsTraceLog.enabled(player)) {
                PhysicsTraceLog.traceDecal(player, "footprint result=no-block-entity sable="
                        + (placement.subLevel() != null)
                        + " pos=" + blockPosString(footprintPos));
            }
            return false;
        }
        boolean added = blockEntity.addSurfaceFootprint(
                level,
                placement.localX(),
                placement.localY(),
                placement.localZ(),
                placement.rotationDegrees(),
                placement.face(),
                footprintSize,
                imprintStrength,
                medium,
                visualSource);
        if (!added && createdContainer) {
            MudDecalAccess.removeContainer(level, placement.subLevel(), footprintPos);
        }
        spawnParticles(level, footPoint, medium, visualSource, imprintStrength);
        if (PhysicsTraceLog.enabled(player)) {
            PhysicsTraceLog.traceDecal(player,
                    "footprint result=" + (added ? "placed" : "rejected")
                            + " sable=" + (placement.subLevel() != null)
                            + " face=" + placement.face()
                            + " pos=" + blockPosString(footprintPos));
        }
        return added || MudPhysicsSettings.maximumFootprints() <= 0;
    }

    private static Placement findPlacement(
            ServerPlayer player, Vec3 footPoint, Vec3 movementDirection) {
        Level level = player.level();
        SableCompat.SurfaceProbe probe = SableCompat.surfaceProbe(
                level, new AABB(footPoint, footPoint).inflate(0.46D), player);
        SableCompat.SurfaceContact physical = SableCompat.findSurface(
                probe, footPoint, new Vec3(0.0D, 1.0D, 0.0D), 0.42D, 0.42D);
        if (physical != null) {
            Vec3 local = physical.localPoint();
            BlockPos container = physical.containerPos();
            return new Placement(
                    physical.subLevel(),
                    container,
                    (float) (local.x - container.getX()),
                    (float) (local.y - container.getY()),
                    (float) (local.z - container.getZ()),
                    physical.face(),
                    localRotation(physical.subLevel(), physical.face(), movementDirection));
        }
        double feetY = player.position().y;
        int startY = Mth.floor(feetY + 0.10D);
        for (int y = startY; y >= startY - 2; y--) {
            BlockPos supportPos = BlockPos.containing(footPoint.x, y, footPoint.z);
            BlockState support = level.getBlockState(supportPos);
            if (!MudFootprintBlock.isValidSupport(support, level, supportPos)) {
                continue;
            }
            double localX = footPoint.x - supportPos.getX();
            double localZ = footPoint.z - supportPos.getZ();
            double surfaceY = Double.NEGATIVE_INFINITY;
            for (AABB box : MudFootprintBlock.supportShape(
                    support, level, supportPos).toAabbs()) {
                if (localX < box.minX - 0.015D || localX > box.maxX + 0.015D
                        || localZ < box.minZ - 0.015D || localZ > box.maxZ + 0.015D) {
                    continue;
                }
                double candidateY = supportPos.getY() + box.maxY;
                if (candidateY <= feetY + 0.085D && candidateY >= feetY - 0.30D) {
                    surfaceY = Math.max(surfaceY, candidateY);
                }
            }
            if (!Double.isFinite(surfaceY)) {
                continue;
            }

            BlockPos containerPos = supportPos.above();
            BlockState container = level.getBlockState(containerPos);
            if (!container.isAir() && container.getBlock() != ModBlocks.MUD_FOOTPRINT.get()) {
                continue;
            }
            return new Placement(
                    null,
                    containerPos,
                    (float) (footPoint.x - containerPos.getX()),
                    (float) (surfaceY - containerPos.getY() + 0.006D),
                    (float) (footPoint.z - containerPos.getZ()),
                    Direction.UP,
                    movementYaw(movementDirection));
        }
        return null;
    }

    private static float localRotation(
            Object subLevel, Direction face, Vec3 movementDirection) {
        Vec3 worldRight = new Vec3(movementDirection.z, 0.0D, -movementDirection.x);
        Vec3 localRight = SableCompat.toLocalDirection(subLevel, worldRight);
        if (localRight == null || localRight.lengthSqr() <= 1.0E-8D) {
            return movementYaw(movementDirection);
        }
        Vec3 tangentU = face.getAxis() == Direction.Axis.X
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 tangentV = face.getAxis() == Direction.Axis.Y
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        return (float) (Math.atan2(localRight.dot(tangentV), localRight.dot(tangentU)) * Mth.RAD_TO_DEG);
    }

    private static float movementYaw(Vec3 movementDirection) {
        return (float) (Math.atan2(-movementDirection.x, movementDirection.z) * Mth.RAD_TO_DEG);
    }

    private static Vec3 movementPoint(
            ServerPlayer player, Vec3 movementDirection,
            double sideOffset, double yOffset, double forwardOffset) {
        Vec3 feet = player.position();
        double rightX = movementDirection.z;
        double rightZ = -movementDirection.x;
        return new Vec3(
                feet.x + rightX * sideOffset + movementDirection.x * forwardOffset,
                feet.y + yOffset,
                feet.z + rightZ * sideOffset + movementDirection.z * forwardOffset);
    }

    private static void spawnParticles(
            ServerLevel level, Vec3 footPoint, SinkingMedium medium,
            long visualSource, float imprintStrength) {
        DustParticleOptions dust = new DustParticleOptions(
                MudVisualSource.particleColor(visualSource, medium.particleColor()),
                medium.particleScale() * 0.82F);
        int count = Math.max(1, Math.round(5.0F * Mth.clamp(imprintStrength, 0.0F, 1.0F)));
        level.sendParticles(
                dust,
                footPoint.x,
                footPoint.y + 0.035D,
                footPoint.z,
                count,
                0.055D,
                0.006D,
                0.10D,
                0.002D);
    }

    private static long ticksSince(int currentTick, int previousTick) {
        return (long) currentTick - (long) previousTick;
    }

    private static String blockPosString(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private record Placement(
            Object subLevel, BlockPos containerPos,
            float localX, float localY, float localZ,
            Direction face, float rotationDegrees) {
    }

    private record SourceSample(
            MudBodyPart part, EquipmentSlot slot,
            float strength, SinkingMedium medium, long visualSource,
            float size) {
    }
}
