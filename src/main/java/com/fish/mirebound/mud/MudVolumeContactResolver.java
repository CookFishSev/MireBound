package com.fish.mirebound.mud;

import static com.fish.mirebound.mud.MudContactRules.effectiveVolumeImmersion;
import static com.fish.mirebound.mud.MudContactRules.volumeResistance;
import static com.fish.mirebound.physics.MudMovementControl.updateMudMovement;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded side-volume snapshots and resistance for contacts without a sink surface. */
final class MudVolumeContactResolver {
    private static final int FACE_SAMPLES = 4;
    private static final double SURFACE_OUTSET = 0.006D;
    private static final double CONTACT_TOLERANCE = 0.003D;
    private static final MudBodyPart[] BODY_PARTS = MudBodyPart.values();
    private static final MudSurface[] SURFACES = MudSurface.values();
    private static final SinkingMedium[] MEDIA = SinkingMedium.values();

    private MudVolumeContactResolver() {
    }

    static MudVolumeSnapshot nearbySnapshot(Player player, boolean includeSable) {
        AABB bounds = player.getBoundingBox().inflate(0.28D);
        WorldMudVolumeProbe worldProbe = nearbyOrdinaryVolumes(
                player.level(), bounds);
        SableCompat.MudVolumeProbe sableProbe =
                includeSable && SableCompat.isLoaded()
                        ? SableCompat.mudVolumeProbe(
                                player.level(), bounds, player)
                        : null;
        return new MudVolumeSnapshot(worldProbe, sableProbe);
    }

    static VolumePhysicsContact findPhysicsContact(
            Player player, MudVolumeSnapshot snapshot) {
        if (snapshot.isEmpty()) {
            return null;
        }
        WorldVolumeImmersion worldImmersion =
                snapshot.worldProbe().immersion(player.getBoundingBox());
        double[] touchedByMedium = new double[SinkingMedium.COUNT];
        double totalWeight = 0.0D;
        double touchedWeight = 0.0D;

        for (MudBodyPart part : BODY_PARTS) {
            MudEntityGeometry.SurfacePixelSampler geometry =
                    MudEntityGeometry.surfacePixelSampler(player, part);
            for (MudSurface surface : SURFACES) {
                Vec3 outward = geometry.outwardNormal(surface);
                if (outward.y < -0.75D) {
                    continue;
                }
                MudSurfaceLayout.Face face =
                        MudSurfaceLayout.face(part, surface);
                double sampleWeight = face.cellCount()
                        / (double) (FACE_SAMPLES * FACE_SAMPLES);
                Vec3 inward = outward.scale(-SURFACE_OUTSET);
                for (int sampleRow = 0; sampleRow < FACE_SAMPLES; sampleRow++) {
                    int row = sampleIndex(sampleRow, face.height());
                    for (int sampleColumn = 0;
                            sampleColumn < FACE_SAMPLES; sampleColumn++) {
                        int column = sampleIndex(sampleColumn, face.width());
                        totalWeight += sampleWeight;
                        Vec3 point = geometry.point(
                                part, surface, row, column).add(inward);
                        SinkingMedium medium = snapshot.mediumAt(
                                point, CONTACT_TOLERANCE);
                        if (medium == null) {
                            continue;
                        }
                        touchedWeight += sampleWeight;
                        touchedByMedium[medium.id()] += sampleWeight;
                    }
                }
            }
        }
        double sampledImmersion = totalWeight <= 0.0D
                ? 0.0D : touchedWeight / totalWeight;
        if (worldImmersion.immersion() > sampledImmersion) {
            return new VolumePhysicsContact(
                    worldImmersion.medium(),
                    worldImmersion.immersion(),
                    worldImmersion.immersedVolume(),
                    worldImmersion.bodyVolume());
        }
        if (touchedWeight <= 0.0D || totalWeight <= 0.0D) {
            return null;
        }
        SinkingMedium strongest = SinkingMedium.MUD;
        double strongestWeight = -1.0D;
        for (SinkingMedium medium : MEDIA) {
            double weight = touchedByMedium[medium.id()];
            if (weight > strongestWeight) {
                strongest = medium;
                strongestWeight = weight;
            }
        }
        return new VolumePhysicsContact(
                strongest,
                effectiveVolumeImmersion(touchedWeight / totalWeight),
                touchedWeight,
                totalWeight);
    }

    static void applyResistance(
            Player player, VolumePhysicsContact contact) {
        SinkingPhysicsProfile profile =
                MudPhysicsProfiles.ordinary(player, contact.medium());
        MudContactRules.VolumeResistance resistance =
                volumeResistance(profile, contact.immersion());
        double walkScale = MudEnchantmentEffects.mudWalker(player)
                .applyWalkScale(resistance.walkScale());
        double stepHeight = SinkingPhysicsSolver.configuredDepth(profile) <= 1.0E-6D
                ? 0.0D : profile.stepHeight;
        updateMudMovement(player, walkScale, stepHeight);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(
                motion.x * walkScale,
                motion.y * resistance.verticalScale(),
                motion.z * walkScale);
        player.hasImpulse = true;
    }

    private static int sampleIndex(int sample, int size) {
        if (size <= 1 || FACE_SAMPLES <= 1) {
            return 0;
        }
        return Mth.clamp(
                (int) Math.round(sample * (size - 1.0D)
                        / (FACE_SAMPLES - 1.0D)),
                0, size - 1);
    }

    private static WorldMudVolumeProbe nearbyOrdinaryVolumes(
            Level level, AABB bounds) {
        List<WorldMudVolume> volumes = new ArrayList<>();
        BlockPos minimum = BlockPos.containing(
                bounds.minX, bounds.minY, bounds.minZ);
        BlockPos maximum = BlockPos.containing(
                bounds.maxX, bounds.maxY, bounds.maxZ);
        for (BlockPos cursor : BlockPos.betweenClosed(minimum, maximum)) {
            BlockState state = level.getBlockState(cursor);
            SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
            if (medium == null
                    || !MudMediumRuntime.enabled(level, cursor, medium)) {
                continue;
            }
            for (AABB local : MudBlock.localShape(
                    level, cursor, state, medium).toAabbs()) {
                AABB worldBounds = local.move(cursor);
                if (worldBounds.inflate(0.015D).intersects(bounds)) {
                    volumes.add(new WorldMudVolume(
                            cursor.immutable(), state, medium, worldBounds));
                }
            }
        }
        return volumes.isEmpty()
                ? WorldMudVolumeProbe.EMPTY
                : new WorldMudVolumeProbe(List.copyOf(volumes));
    }

    static double worldBodyImmersion(Level level, AABB bodyBounds) {
        return nearbyOrdinaryVolumes(level, bodyBounds)
                .immersion(bodyBounds)
                .immersion();
    }
}

record WorldVolumeSample(
        BlockPos pos, BlockState state, SinkingMedium medium) {
}

record WorldMudVolume(
        BlockPos pos, BlockState state, SinkingMedium medium, AABB bounds) {
}

record MudVolumeSnapshot(
        WorldMudVolumeProbe worldProbe,
        SableCompat.MudVolumeProbe sableProbe) {
    boolean isEmpty() {
        return worldProbe.isEmpty()
                && (sableProbe == null || sableProbe.isEmpty());
    }

    SinkingMedium mediumAt(Vec3 point, double tolerance) {
        WorldVolumeSample world = worldProbe.sample(point, tolerance);
        if (world != null) {
            return world.medium();
        }
        SableCompat.MudVolumeSample sable = sableProbe == null
                ? null : sableProbe.sample(point, tolerance);
        return sable == null ? null : sable.medium();
    }
}

record VolumePhysicsContact(
        SinkingMedium medium,
        double immersion,
        double touchedWeight,
        double totalWeight) {
}

final class WorldMudVolumeProbe {
    static final WorldMudVolumeProbe EMPTY = new WorldMudVolumeProbe(List.of());
    private static final SinkingMedium[] MEDIA = SinkingMedium.values();
    private final List<WorldMudVolume> volumes;

    WorldMudVolumeProbe(List<WorldMudVolume> volumes) {
        this.volumes = volumes;
    }

    boolean isEmpty() {
        return volumes.isEmpty();
    }

    WorldVolumeImmersion immersion(AABB bodyBounds) {
        double bodyVolume = Math.max(1.0E-9D,
                (bodyBounds.maxX - bodyBounds.minX)
                        * (bodyBounds.maxY - bodyBounds.minY)
                        * (bodyBounds.maxZ - bodyBounds.minZ));
        double[] volumeByMedium = new double[SinkingMedium.COUNT];
        double immersedVolume = 0.0D;
        for (WorldMudVolume volume : volumes) {
            double overlap = intersectionVolume(bodyBounds, volume.bounds());
            if (overlap <= 0.0D) {
                continue;
            }
            immersedVolume += overlap;
            volumeByMedium[volume.medium().id()] += overlap;
        }
        SinkingMedium strongest = SinkingMedium.MUD;
        double strongestVolume = 0.0D;
        for (SinkingMedium medium : MEDIA) {
            double volume = volumeByMedium[medium.id()];
            if (volume > strongestVolume) {
                strongest = medium;
                strongestVolume = volume;
            }
        }
        return new WorldVolumeImmersion(
                strongest,
                Mth.clamp(immersedVolume / bodyVolume, 0.0D, 1.0D),
                Math.min(immersedVolume, bodyVolume),
                bodyVolume);
    }

    WorldVolumeSample sample(Vec3 point, double tolerance) {
        for (WorldMudVolume volume : volumes) {
            AABB bounds = volume.bounds();
            if (point.x >= bounds.minX - tolerance
                    && point.x <= bounds.maxX + tolerance
                    && point.y >= bounds.minY - tolerance
                    && point.y <= bounds.maxY + tolerance
                    && point.z >= bounds.minZ - tolerance
                    && point.z <= bounds.maxZ + tolerance) {
                return new WorldVolumeSample(
                        volume.pos(), volume.state(), volume.medium());
            }
        }
        return null;
    }

    private static double intersectionVolume(AABB first, AABB second) {
        double sizeX = Math.min(first.maxX, second.maxX)
                - Math.max(first.minX, second.minX);
        double sizeY = Math.min(first.maxY, second.maxY)
                - Math.max(first.minY, second.minY);
        double sizeZ = Math.min(first.maxZ, second.maxZ)
                - Math.max(first.minZ, second.minZ);
        return sizeX <= 0.0D || sizeY <= 0.0D || sizeZ <= 0.0D
                ? 0.0D : sizeX * sizeY * sizeZ;
    }
}

record WorldVolumeImmersion(
        SinkingMedium medium,
        double immersion,
        double immersedVolume,
        double bodyVolume) {
}
