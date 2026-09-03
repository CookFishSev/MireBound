package com.fish.mirebound.client;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Emits one bounded surface burst when a tuning-wand selection is accepted. */
final class MudTuningWandSelectionParticles {
    private static final int PARTICLE_COUNT = 20;
    private static final double MINIMUM_SURFACE_OFFSET = 0.10D;
    private static final double SURFACE_OFFSET_VARIATION = 0.06D;
    private static final double EDGE_INSET = 0.10D;
    private static final Vec3[] FACE_NORMALS = {
            new Vec3(-1.0D, 0.0D, 0.0D),
            new Vec3(1.0D, 0.0D, 0.0D),
            new Vec3(0.0D, -1.0D, 0.0D),
            new Vec3(0.0D, 1.0D, 0.0D),
            new Vec3(0.0D, 0.0D, -1.0D),
            new Vec3(0.0D, 0.0D, 1.0D)
    };

    private MudTuningWandSelectionParticles() {
    }

    static void spawn(Minecraft minecraft, MudTuningAnchor anchor, int color) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Object subLevel = anchor.isSable()
                ? SableCompat.subLevelById(level, anchor.subLevelId()) : null;
        if (anchor.isSable() && subLevel == null) {
            return;
        }

        BlockPos pos = anchor.pos();
        var shape = level.getBlockState(pos).getShape(level, pos, CollisionContext.empty());
        AABB bounds = shape.isEmpty()
                ? new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)
                : shape.bounds();
        ColorParticleOption spark = ColorParticleOption.create(
                ModParticles.TUNING_WAND_SELECTION.get(), 0xFF000000 | color);
        RandomSource random = level.random;
        int faceOffset = random.nextInt(FACE_NORMALS.length);
        for (int index = 0; index < PARTICLE_COUNT; index++) {
            int face = (faceOffset + index) % FACE_NORMALS.length;
            Vec3 localPoint = surfacePoint(pos, bounds, face, random);
            Vec3 localVelocity = surfaceVelocity(face, random);
            Vec3 worldPoint = transformPoint(subLevel, localPoint);
            Vec3 worldVelocity = transformVelocity(subLevel, localPoint, localVelocity);
            if (worldPoint == null || worldVelocity == null) {
                continue;
            }
            level.addParticle(spark,
                    worldPoint.x, worldPoint.y, worldPoint.z,
                    worldVelocity.x, worldVelocity.y, worldVelocity.z);
        }
    }

    private static Vec3 surfacePoint(BlockPos pos, AABB bounds, int face,
            RandomSource random) {
        double x = ranged(random, bounds.minX, bounds.maxX);
        double y = ranged(random, bounds.minY, bounds.maxY);
        double z = ranged(random, bounds.minZ, bounds.maxZ);
        Vec3 normal = FACE_NORMALS[face];
        double surfaceOffset = MINIMUM_SURFACE_OFFSET
                + random.nextDouble() * SURFACE_OFFSET_VARIATION;
        if (normal.x < 0.0D) {
            x = bounds.minX - surfaceOffset;
        } else if (normal.x > 0.0D) {
            x = bounds.maxX + surfaceOffset;
        } else if (normal.y < 0.0D) {
            y = bounds.minY - surfaceOffset;
        } else if (normal.y > 0.0D) {
            y = bounds.maxY + surfaceOffset;
        } else if (normal.z < 0.0D) {
            z = bounds.minZ - surfaceOffset;
        } else {
            z = bounds.maxZ + surfaceOffset;
        }
        return new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
    }

    private static double ranged(RandomSource random, double minimum, double maximum) {
        double span = maximum - minimum;
        if (span <= 1.0E-6D) {
            return (minimum + maximum) * 0.5D;
        }
        double inset = Math.min(EDGE_INSET, span * 0.25D);
        return minimum + inset + random.nextDouble() * Math.max(0.0D, span - inset * 2.0D);
    }

    private static Vec3 surfaceVelocity(int face, RandomSource random) {
        Vec3 normal = FACE_NORMALS[face];
        Vec3 tangent = new Vec3(
                normal.x == 0.0D ? centered(random, 0.035D) : 0.0D,
                normal.y == 0.0D ? centered(random, 0.035D) : 0.0D,
                normal.z == 0.0D ? centered(random, 0.035D) : 0.0D);
        return normal.scale(0.045D + random.nextDouble() * 0.045D).add(tangent);
    }

    private static double centered(RandomSource random, double radius) {
        return (random.nextDouble() * 2.0D - 1.0D) * radius;
    }

    private static Vec3 transformPoint(Object subLevel, Vec3 localPoint) {
        return subLevel == null ? localPoint : SableCompat.toRenderWorld(subLevel, localPoint);
    }

    private static Vec3 transformVelocity(Object subLevel, Vec3 localPoint,
            Vec3 localVelocity) {
        if (subLevel == null) {
            return localVelocity;
        }
        Vec3 start = SableCompat.toRenderWorld(subLevel, localPoint);
        Vec3 end = SableCompat.toRenderWorld(subLevel, localPoint.add(localVelocity));
        return start == null || end == null ? null : end.subtract(start);
    }
}
