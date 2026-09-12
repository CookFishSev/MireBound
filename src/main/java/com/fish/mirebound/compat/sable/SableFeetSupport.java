package com.fish.mirebound.compat.sable;

import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.mud.CoverageDebugLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Resolves solid support before interpreting Sable's small collision corrections as immersion. */
public final class SableFeetSupport {
    private static final double SEARCH_DISTANCE = 0.06D;
    private static final int MAX_BLOCKS = 64;
    private static final double[] FOOTPRINT_POINTS = {0.02D, 0.5D, 0.98D};
    private static final ThreadLocal<Map<Player, Cache>> CACHES = ThreadLocal.withInitial(WeakHashMap::new);

    private SableFeetSupport() {
    }

    public static double feetY(Player player, Object subLevel) {
        if (subLevel == null) return player.getY();
        Map<Player, Cache> caches = CACHES.get();
        AABB bounds = player.getBoundingBox();
        Cache cache = caches.get(player);
        if (cache == null || cache.tick != player.tickCount || !cache.bounds.equals(bounds)) {
            cache = new Cache(player.tickCount, bounds, new WeakHashMap<>());
            caches.put(player, cache);
        }
        Double height = cache.heights.get(subLevel);
        if (height == null) {
            height = capture(player, subLevel, bounds);
            if (cache.heights.size() < 8) cache.heights.put(subLevel, height);
        }
        return Math.max(player.getY(), height);
    }

    private static double capture(Player player, Object subLevel, AABB body) {
        SableCompat.RigidTransform transform = SableCompat.rigidTransform(subLevel);
        SableCompat.AffineTransform inverse = transform == null ? null : transform.resolveLocal();
        SableCompat.AffineTransform forward = transform == null ? null : transform.resolveWorld();
        if (inverse == null || forward == null) return player.getY();
        double sweepTop = Math.max(body.minY, Math.min(body.minY + 0.5D, player.yo)) + SEARCH_DISTANCE;
        AABB localBounds = null;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            Vec3 corner = inverse.toWorld(new Vec3(x == 0 ? body.minX : body.maxX,
                    y == 0 ? body.minY - SEARCH_DISTANCE : sweepTop,
                    z == 0 ? body.minZ : body.maxZ));
            AABB point = new AABB(corner, corner);
            localBounds = localBounds == null ? point : localBounds.minmax(point);
        }
        BlockPos min = BlockPos.containing(localBounds.minX, localBounds.minY, localBounds.minZ);
        BlockPos max = BlockPos.containing(localBounds.maxX, localBounds.maxY, localBounds.maxZ);
        long count = (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (count > MAX_BLOCKS) return player.getY();
        List<AABB> boxes = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = SableCompat.subLevelBlockState(player.level(), subLevel, pos);
            if (state.isAir() || ModBlocks.isSinkingBlock(state.getBlock())
                    || state.is(ModBlocks.MUD_FOOTPRINT.get())) continue;
            for (AABB box : state.getCollisionShape(player.level(), pos).toAabbs()) {
                if (boxes.size() >= 256) return player.getY();
                boxes.add(box.move(pos));
            }
        }
        double height = supportedHeight(body, sweepTop, inverse, forward, boxes);
        if (player instanceof ServerPlayer serverPlayer && CoverageDebugLog.reserve(serverPlayer, "feet-support", 10)) {
            CoverageDebugLog.event(serverPlayer, "feet-support", "feet=" + body.minY + " previous=" + player.yo
                    + " support=" + height + " blocks=" + count + " boxes=" + boxes.size()
                    + " local=" + inverse.toWorld(player.position())
                    + " roundtrip=" + forward.toWorld(inverse.toWorld(player.position()))
                    + " firstBox=" + (boxes.isEmpty() ? "none" : boxes.getFirst()));
        }
        return height;
    }

    static double supportedHeight(AABB body, SableCompat.AffineTransform inverse,
            SableCompat.AffineTransform forward, List<AABB> localBoxes) {
        return supportedHeight(body, body.minY + SEARCH_DISTANCE, inverse, forward, localBoxes);
    }

    static double supportedHeight(AABB body, double sweepTop, SableCompat.AffineTransform inverse,
            SableCompat.AffineTransform forward, List<AABB> localBoxes) {
        double height = body.minY;
        for (double x : FOOTPRINT_POINTS) for (double z : FOOTPRINT_POINTS) {
            double worldX = body.minX + body.getXsize() * x;
            double worldZ = body.minZ + body.getZsize() * z;
            Vec3 start = inverse.toWorld(new Vec3(worldX, sweepTop, worldZ));
            Vec3 end = inverse.toWorld(new Vec3(worldX, body.minY - SEARCH_DISTANCE, worldZ));
            for (AABB box : localBoxes) {
                var hit = box.clip(start, end);
                if (hit.isPresent()) height = Math.max(height, forward.toWorld(hit.get()).y);
            }
        }
        return height;
    }

    private record Cache(int tick, AABB bounds, Map<Object, Double> heights) {
    }
}
