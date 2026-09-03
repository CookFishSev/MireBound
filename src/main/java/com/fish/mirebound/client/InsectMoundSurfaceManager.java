package com.fish.mirebound.client;

import com.fish.mirebound.client.swarm.ClientSwarmState;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBehaviorContext;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded client state for grid-aligned insect trails on exposed mound faces. */
final class InsectMoundSurfaceManager {
    private static final int MAX_SCAN_RADIUS = 12;
    private static final int MAX_TRAIL_LENGTH = 24;
    private static final int MAX_TRAILS_PER_FACE = 6;
    private static final double PIXEL = 1.0D / 16.0D;
    private static final Map<Long, Face> FACES = new HashMap<>();
    private static ClientLevel level;
    private static long ticks;
    private static long randomState = 0x1A5EC7L;
    private static int soundCooldown;

    private InsectMoundSurfaceManager() {
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel clientLevel)
                || minecraft.player == null
                || !MudSurfaceClientSettings.enabled()
                || !MudSurfaceClientSettings.insectMoundEnabled()) {
            reset();
            return;
        }
        if (level != clientLevel) {
            reset();
            level = clientLevel;
        }
        if (minecraft.isPaused()) {
            return;
        }

        ticks++;
        for (Face face : FACES.values()) {
            face.updateActivity(minecraft.player);
            face.tickTrails();
        }
        if (ticks == 1L
                || ticks % MudSurfaceClientSettings.insectMoundScanInterval() == 0L) {
            rebuild(minecraft.player);
        }
        if (soundCooldown > 0) {
            soundCooldown--;
        }
        tickSound(minecraft.player);
    }

    static Iterable<Face> faces() {
        return FACES.values();
    }

    static void reset() {
        FACES.clear();
        level = null;
        ticks = 0L;
        randomState = 0x1A5EC7L;
        soundCooldown = 0;
    }

    private static void rebuild(Player player) {
        int maxFaces = MudSurfaceClientSettings.insectMoundMaxPatches();
        if (maxFaces <= 0) {
            FACES.clear();
            return;
        }
        int radius = Math.min(MAX_SCAN_RADIUS,
                Mth.ceil(MudSurfaceClientSettings.renderDistance()));
        BlockPos center = player.blockPosition();
        Map<Long, Boolean> seen = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -4, -radius),
                center.offset(radius, 4, radius))) {
            BlockState state = level.getBlockState(pos);
            SinkingMedium medium = swarmMedium(pos, state);
            if (medium == null) {
                continue;
            }
            AABB bounds = MudBlock.localBounds(level, pos, state, medium);
            for (Direction face : Direction.values()) {
                if (!hasFace(bounds, face) || !isExposed(pos, face, bounds)) {
                    continue;
                }
                long key = pos.asLong() * 7L + face.ordinal();
                seen.put(key, Boolean.TRUE);
                if (FACES.size() >= maxFaces && !FACES.containsKey(key)) {
                    continue;
                }
                Face surface = FACES.computeIfAbsent(key,
                        ignored -> Face.create(pos.immutable(), face, bounds, key));
                surface.refresh(pos.immutable(), face, bounds);
            }
        }
        Iterator<Map.Entry<Long, Face>> iterator = FACES.entrySet().iterator();
        int retained = 0;
        while (iterator.hasNext()) {
            Map.Entry<Long, Face> entry = iterator.next();
            if (!seen.containsKey(entry.getKey()) || retained >= maxFaces) {
                iterator.remove();
            } else {
                retained++;
            }
        }
    }

    private static boolean isExposed(BlockPos pos, Direction face, AABB bounds) {
        boolean inset = switch (face) {
            case DOWN -> bounds.minY > 0.001D;
            case UP -> bounds.maxY < 0.999D;
            case NORTH -> bounds.minZ > 0.001D;
            case SOUTH -> bounds.maxZ < 0.999D;
            case WEST -> bounds.minX > 0.001D;
            case EAST -> bounds.maxX < 0.999D;
        };
        if (inset) {
            return true;
        }
        BlockPos neighborPos = pos.relative(face);
        BlockState neighbor = level.getBlockState(neighborPos);
        return swarmMedium(neighborPos, neighbor) == null
                && (neighbor.isAir()
                        || !neighbor.isSolidRender(level, neighborPos));
    }

    private static SinkingMedium swarmMedium(BlockPos pos, BlockState state) {
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        return medium != null && MudBehaviorContext.swarm(level, pos, medium)
                ? medium : null;
    }

    private static boolean hasFace(AABB bounds, Direction face) {
        return switch (face) {
            case DOWN, UP -> bounds.getXsize() >= PIXEL && bounds.getZsize() >= PIXEL;
            case NORTH, SOUTH -> bounds.getXsize() >= PIXEL && bounds.getYsize() >= PIXEL;
            case WEST, EAST -> bounds.getZsize() >= PIXEL && bounds.getYsize() >= PIXEL;
        };
    }

    private static void tickSound(Player player) {
        float configuredVolume = (float) MudSurfaceClientSettings.insectMoundSoundVolume();
        if (soundCooldown > 0 || FACES.isEmpty() || configuredVolume <= 0.0F) {
            return;
        }
        Face nearest = null;
        double nearestDistance = 64.0D;
        for (Face face : FACES.values()) {
            double distance = face.center.distanceToSqr(player.position());
            if (distance < nearestDistance && face.activity > 0.20F) {
                nearest = face;
                nearestDistance = distance;
            }
        }
        if (nearest == null || unit(nextRandom()) > nearest.activity * 0.15F) {
            return;
        }
        float volume = configuredVolume
                * Mth.clamp(0.25F + nearest.activity * 0.75F, 0.0F, 1.0F);
        float pitch = 0.72F + unit(nextRandom()) * 0.28F;
        level.playLocalSound(nearest.center.x, nearest.center.y, nearest.center.z,
                SoundEvents.SILVERFISH_STEP, SoundSource.AMBIENT,
                volume, pitch, false);
        soundCooldown = 20 + (int) (unit(nextRandom()) * 36.0F);
    }

    private static long nextRandom() {
        randomState ^= randomState >>> 12;
        randomState ^= randomState << 25;
        randomState ^= randomState >>> 27;
        return randomState * 2685821657736338717L;
    }

    private static float unit(long value) {
        return (value & 0xFFFFL) / 65535.0F;
    }

    static final class Face {
        private final long seed;
        private final Trail[] trails = new Trail[MAX_TRAILS_PER_FACE];
        private BlockPos block;
        private Direction direction;
        private Vec3 center;
        private Vec3 axisU;
        private Vec3 axisV;
        private Vec3 normal;
        private int plane;
        private int minU;
        private int maxU;
        private int minV;
        private int maxV;
        private float activity;
        private float previousActivity;

        private Face(long seed) {
            this.seed = seed;
            for (int index = 0; index < trails.length; index++) {
                trails[index] = new Trail(seed
                        + (index + 1L) * 0x9E3779B97F4A7C15L);
            }
        }

        private static Face create(BlockPos block, Direction direction,
                AABB bounds, long key) {
            Face face = new Face(key * 0x9E3779B97F4A7C15L);
            face.refresh(block, direction, bounds);
            return face;
        }

        private void refresh(BlockPos block, Direction direction, AABB bounds) {
            this.block = block;
            this.direction = direction;
            double cx = block.getX() + (bounds.minX + bounds.maxX) * 0.5D;
            double cy = block.getY() + (bounds.minY + bounds.maxY) * 0.5D;
            double cz = block.getZ() + (bounds.minZ + bounds.maxZ) * 0.5D;
            switch (direction) {
                case UP -> {
                    cy = block.getY() + bounds.maxY;
                    axisU = new Vec3(1.0D, 0.0D, 0.0D);
                    axisV = new Vec3(0.0D, 0.0D, 1.0D);
                    normal = new Vec3(0.0D, 1.0D, 0.0D);
                }
                case DOWN -> {
                    cy = block.getY() + bounds.minY;
                    axisU = new Vec3(1.0D, 0.0D, 0.0D);
                    axisV = new Vec3(0.0D, 0.0D, 1.0D);
                    normal = new Vec3(0.0D, -1.0D, 0.0D);
                }
                case NORTH, SOUTH -> {
                    cz = block.getZ()
                            + (direction == Direction.NORTH ? bounds.minZ : bounds.maxZ);
                    axisU = new Vec3(1.0D, 0.0D, 0.0D);
                    axisV = new Vec3(0.0D, 1.0D, 0.0D);
                    normal = direction == Direction.NORTH
                            ? new Vec3(0.0D, 0.0D, -1.0D)
                            : new Vec3(0.0D, 0.0D, 1.0D);
                }
                case WEST, EAST -> {
                    cx = block.getX()
                            + (direction == Direction.WEST ? bounds.minX : bounds.maxX);
                    axisU = new Vec3(0.0D, 0.0D, 1.0D);
                    axisV = new Vec3(0.0D, 1.0D, 0.0D);
                    normal = direction == Direction.WEST
                            ? new Vec3(-1.0D, 0.0D, 0.0D)
                            : new Vec3(1.0D, 0.0D, 0.0D);
                }
            }
            center = new Vec3(cx, cy, cz);
            plane = (int) Math.round(normal.dot(center) * 16.0D);
            double worldMinU = axisU.dot(new Vec3(
                    block.getX() + bounds.minX,
                    block.getY() + bounds.minY,
                    block.getZ() + bounds.minZ));
            double worldMaxU = axisU.dot(new Vec3(
                    block.getX() + bounds.maxX,
                    block.getY() + bounds.maxY,
                    block.getZ() + bounds.maxZ));
            double worldMinV = axisV.dot(new Vec3(
                    block.getX() + bounds.minX,
                    block.getY() + bounds.minY,
                    block.getZ() + bounds.minZ));
            double worldMaxV = axisV.dot(new Vec3(
                    block.getX() + bounds.maxX,
                    block.getY() + bounds.maxY,
                    block.getZ() + bounds.maxZ));
            minU = Mth.floor(worldMinU * 16.0D);
            maxU = Mth.ceil(worldMaxU * 16.0D) - 1;
            minV = Mth.floor(worldMinV * 16.0D);
            maxV = Mth.ceil(worldMaxV * 16.0D) - 1;
            for (Trail trail : trails) {
                trail.clampTo(this);
            }
        }

        private void updateActivity(Player player) {
            double distanceSqr = center.distanceToSqr(player.position());
            double proximity = Mth.clamp(1.0D - distanceSqr / 81.0D, 0.0D, 1.0D);
            float swarm = ClientSwarmState.displayed();
            float contact = Mth.clamp(swarm * (float) proximity * 1.35F,
                    0.0F, 1.0F);
            previousActivity = activity;
            activity = Mth.lerp(0.12F, activity,
                    (float) MudSurfaceClientSettings.insectMoundBaseActivity()
                            * 0.60F + contact * 0.80F);
            int max = Math.min(MAX_TRAILS_PER_FACE,
                    MudSurfaceClientSettings.insectMoundLarvaePerFace());
            int idleCount = (int) (Math.abs(seed) % 3L == 0L ? 1L : 0L);
            int desired = Mth.clamp(idleCount
                    + Mth.ceil(contact * Math.max(0, max - idleCount)), 0, max);
            for (int index = 0; index < trails.length; index++) {
                trails[index].setEnabled(index < desired);
            }
        }

        private void tickTrails() {
            for (Trail trail : trails) {
                trail.tick(this, activity);
            }
        }

        private boolean validCell(int u, int v) {
            Vec3 point = point(u, v);
            BlockPos pos = BlockPos.containing(point.subtract(normal.scale(0.025D)));
            BlockState state = level.getBlockState(pos);
            SinkingMedium currentMedium = swarmMedium(pos, state);
            if (currentMedium == null) {
                return false;
            }
            AABB bounds = MudBlock.localBounds(
                    level, pos, state, currentMedium);
            Vec3 local = point.subtract(pos.getX(), pos.getY(), pos.getZ());
            double tolerance = 0.045D;
            boolean planeMatch = switch (direction) {
                case DOWN -> Math.abs(local.y - bounds.minY) <= tolerance;
                case UP -> Math.abs(local.y - bounds.maxY) <= tolerance;
                case NORTH -> Math.abs(local.z - bounds.minZ) <= tolerance;
                case SOUTH -> Math.abs(local.z - bounds.maxZ) <= tolerance;
                case WEST -> Math.abs(local.x - bounds.minX) <= tolerance;
                case EAST -> Math.abs(local.x - bounds.maxX) <= tolerance;
            };
            if (!planeMatch || !insideTangents(local, bounds, direction, tolerance)) {
                return false;
            }
            if (isBoundaryFace(pos, bounds, direction)) {
                BlockPos neighborPos = pos.relative(direction);
                BlockState neighbor = level.getBlockState(neighborPos);
                if (swarmMedium(neighborPos, neighbor) == null
                        && !neighbor.isAir()
                        && neighbor.isSolidRender(level, neighborPos)) {
                    return false;
                }
            }
            return true;
        }

        private boolean insideTangents(Vec3 local, AABB bounds,
                Direction face, double tolerance) {
            return switch (face) {
                case DOWN, UP -> local.x >= bounds.minX - tolerance
                        && local.x <= bounds.maxX + tolerance
                        && local.z >= bounds.minZ - tolerance
                        && local.z <= bounds.maxZ + tolerance;
                case NORTH, SOUTH -> local.x >= bounds.minX - tolerance
                        && local.x <= bounds.maxX + tolerance
                        && local.y >= bounds.minY - tolerance
                        && local.y <= bounds.maxY + tolerance;
                case WEST, EAST -> local.z >= bounds.minZ - tolerance
                        && local.z <= bounds.maxZ + tolerance
                        && local.y >= bounds.minY - tolerance
                        && local.y <= bounds.maxY + tolerance;
            };
        }

        private boolean isBoundaryFace(BlockPos pos, AABB bounds, Direction face) {
            return switch (face) {
                case DOWN -> bounds.minY <= 0.001D;
                case UP -> bounds.maxY >= 0.999D;
                case NORTH -> bounds.minZ <= 0.001D;
                case SOUTH -> bounds.maxZ >= 0.999D;
                case WEST -> bounds.minX <= 0.001D;
                case EAST -> bounds.maxX >= 0.999D;
            };
        }

        private Vec3 point(int u, int v) {
            return axisU.scale((u + 0.5D) * PIXEL)
                    .add(axisV.scale((v + 0.5D) * PIXEL))
                    .add(normal.scale(plane * PIXEL));
        }

        private int randomU() {
            return minU + (int) (Math.abs(mix(seed + 0x31L))
                    % Math.max(1, maxU - minU + 1));
        }

        private int randomV() {
            return minV + (int) (Math.abs(mix(seed + 0x67L))
                    % Math.max(1, maxV - minV + 1));
        }

        Vec3 pointFor(int u, int v) { return point(u, v); }
        Vec3 axisU() { return axisU; }
        Vec3 axisV() { return axisV; }
        Vec3 normal() { return normal; }
        Vec3 center() { return center; }
        long seed() { return seed; }
        float activity() { return activity; }
        Trail[] trails() { return trails; }

        private static long mix(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdL;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53L;
            return value ^ value >>> 33;
        }
    }

    static final class Trail {
        private final int[] u = new int[MAX_TRAIL_LENGTH];
        private final int[] v = new int[MAX_TRAIL_LENGTH];
        private long randomState;
        private boolean initialized;
        private boolean enabled;
        private int length;
        private int mode;
        private int modeTicks;
        private int modeDuration;
        private int stepTicks;
        private int directionU;
        private int directionV;
        private float emergence;
        private float previousEmergence;

        private Trail(long seed) {
            randomState = seed == 0L ? 0x545241494CL : seed;
        }

        private void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        private void tick(Face face, float activity) {
            previousEmergence = emergence;
            if (!initialized) {
                initialize(face);
            }
            switch (mode) {
                case 0 -> {
                    emergence = 0.0F;
                    if (enabled) {
                        relocate(face);
                        mode = 1;
                        modeDuration = 18 + (int) (nextUnit() * 30.0D);
                        modeTicks = modeDuration;
                    }
                }
                case 1 -> {
                    emergence = Math.min(1.0F, emergence
                            + 1.0F / Math.max(12, modeDuration));
                    move(face, activity);
                    if (--modeTicks <= 0 || emergence >= 0.999F) {
                        emergence = 1.0F;
                        mode = 2;
                        modeTicks = visibleLifetime(activity);
                    }
                }
                case 2 -> {
                    emergence = 1.0F;
                    if (!enabled || --modeTicks <= 0) {
                        mode = 3;
                        modeDuration = 18 + (int) (nextUnit() * 26.0D);
                        modeTicks = modeDuration;
                    } else {
                        move(face, activity);
                    }
                }
                default -> {
                    emergence = Math.max(0.0F, emergence
                            - 1.0F / Math.max(12, modeDuration));
                    if (--modeTicks <= 0 || emergence <= 0.001F) {
                        emergence = 0.0F;
                        mode = 0;
                        modeTicks = 16 + (int) (nextUnit() * 28.0D);
                    }
                }
            }
        }

        private void initialize(Face face) {
            length = chooseLength();
            relocate(face);
            mode = enabled ? 1 : 0;
            modeDuration = enabled ? 18 : 0;
            modeTicks = enabled ? modeDuration : 0;
            emergence = 0.0F;
            previousEmergence = 0.0F;
            initialized = true;
        }

        private void relocate(Face face) {
            length = chooseLength();
            int headU = face.randomU();
            int headV = face.randomV();
            for (int attempt = 0; attempt < 24; attempt++) {
                headU = face.minU + (int) (nextUnit()
                        * Math.max(1, face.maxU - face.minU + 1));
                headV = face.minV + (int) (nextUnit()
                        * Math.max(1, face.maxV - face.minV + 1));
                if (face.validCell(headU, headV)) {
                    break;
                }
            }
            u[0] = headU;
            v[0] = headV;
            directionU = 0;
            directionV = 0;
            for (int index = 1; index < length; index++) {
                u[index] = u[index - 1];
                v[index] = v[index - 1];
            }
            stepTicks = 2;
        }

        private void move(Face face, float activity) {
            if (--stepTicks > 0 || emergence < 0.25F) {
                return;
            }
            int[][] directions = {
                    { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
            };
            int start = (int) (nextUnit() * directions.length);
            int nextU = u[0];
            int nextV = v[0];
            boolean found = false;
            for (int offset = 0; offset < directions.length; offset++) {
                int[] direction = directions[(start + offset) % directions.length];
                if (direction[0] == -directionU && direction[1] == -directionV
                        && nextUnit() < 0.62D) {
                    continue;
                }
                int candidateU = u[0] + direction[0];
                int candidateV = v[0] + direction[1];
                if (face.validCell(candidateU, candidateV)) {
                    nextU = candidateU;
                    nextV = candidateV;
                    directionU = direction[0];
                    directionV = direction[1];
                    found = true;
                    break;
                }
            }
            if (!found) {
                stepTicks = 2 + (int) (nextUnit() * 5.0D);
                return;
            }
            for (int index = Math.min(length, MAX_TRAIL_LENGTH - 1); index > 0; index--) {
                u[index] = u[index - 1];
                v[index] = v[index - 1];
            }
            u[0] = nextU;
            v[0] = nextV;
            stepTicks = Math.max(2, 6 - (int) (activity * 3.0F)
                    + (int) (nextUnit() * 4.0D));
        }

        private int chooseLength() {
            int min = Mth.clamp(MudSurfaceClientSettings.insectMoundMinLength(),
                    2, MAX_TRAIL_LENGTH);
            int max = Mth.clamp(MudSurfaceClientSettings.insectMoundMaxLength(),
                    min, MAX_TRAIL_LENGTH);
            return min + (int) (nextUnit() * (max - min + 1));
        }

        private int visibleLifetime(float activity) {
            int base = 110 + (int) (nextUnit() * 170.0D);
            return Math.max(55, base - (int) (activity * 80.0F));
        }

        private void clampTo(Face face) {
            if (!initialized) {
                return;
            }
            for (int index = 0; index < length; index++) {
                u[index] = Mth.clamp(u[index], face.minU, face.maxU);
                v[index] = Mth.clamp(v[index], face.minV, face.maxV);
            }
        }

        private double nextUnit() {
            randomState ^= randomState >>> 12;
            randomState ^= randomState << 25;
            randomState ^= randomState >>> 27;
            long value = randomState * 2685821657736338717L;
            return (value & 0xFFFFFFL) / 16777215.0D;
        }

        int length() { return length; }
        int u(int index) { return u[index]; }
        int v(int index) { return v[index]; }
        float emergence() { return emergence; }
        float previousEmergence() { return previousEmergence; }
    }
}
