package com.fish.mirebound.mud;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Short-lived player geometry captured after the client renderer applies its
 * animation. Static server geometry remains the fallback when no valid frame is
 * available.
 */
public final class AnimatedPlayerGeometry {
    public static final int MAX_FRAME_AGE_TICKS = 6;
    private static final int PRUNE_INTERVAL_TICKS = 20;
    private static final int PRUNE_SIZE_THRESHOLD = 64;
    // Client and integrated-server players share an entity id and Entity equality,
    // but their animated geometry belongs to different coordinate/tick domains.
    private static final Map<Player, Snapshot> SNAPSHOTS = new IdentityHashMap<>();
    private static int lastPruneTick = Integer.MIN_VALUE;

    private AnimatedPlayerGeometry() {
    }

    public static synchronized void updateBody(Player player, PartPose[] poses) {
        updateBody(player, poses, Source.REMOTE);
    }

    public static synchronized void updateBody(Player player, PartPose[] poses, Source source) {
        if (player == null || poses == null || poses.length != MudBodyPart.COUNT) {
            return;
        }
        prune(player.tickCount);
        Snapshot previous = SNAPSHOTS.get(player);
        CapePose cape = previous == null ? null : previous.cape;
        int capeTick = previous == null ? Integer.MIN_VALUE : previous.capeTick;
        Vec3 capeOrigin = previous == null ? null : previous.capeOrigin;
        Source capeSource = previous == null ? Source.NONE : previous.capeSource;
        SNAPSHOTS.put(player, new Snapshot(player.tickCount, player.position(),
                poses.clone(), source, capeTick, capeOrigin, cape, capeSource));
    }

    public static synchronized void updateCape(Player player, CapePose cape) {
        updateCape(player, cape, Source.REMOTE);
    }

    public static synchronized void updateCape(Player player, CapePose cape, Source source) {
        if (player == null || cape == null) {
            return;
        }
        prune(player.tickCount);
        Snapshot previous = SNAPSHOTS.get(player);
        PartPose[] body = previous == null ? null : previous.body;
        int bodyTick = previous == null ? Integer.MIN_VALUE : previous.bodyTick;
        Vec3 bodyOrigin = previous == null ? null : previous.bodyOrigin;
        Source bodySource = previous == null ? Source.NONE : previous.bodySource;
        SNAPSHOTS.put(player, new Snapshot(bodyTick, bodyOrigin,
                body == null ? null : body.clone(), bodySource,
                player.tickCount, player.position(), cape, source));
    }

    public static synchronized PartPose part(Player player, MudBodyPart part) {
        Snapshot snapshot = SNAPSHOTS.get(player);
        if (snapshot == null || !fresh(player, snapshot.bodyTick) || snapshot.body == null) {
            return null;
        }
        PartPose pose = snapshot.body[part.ordinal()];
        return pose == null || snapshot.bodyOrigin == null
                ? pose
                : pose.withCenter(reanchor(
                        pose.center(), snapshot.bodyOrigin, player.position()));
    }

    public static synchronized CapePose cape(Player player) {
        Snapshot snapshot = SNAPSHOTS.get(player);
        if (snapshot == null || !fresh(player, snapshot.capeTick)) {
            return null;
        }
        return snapshot.cape == null || snapshot.capeOrigin == null
                ? snapshot.cape
                : snapshot.cape.withRoot(reanchor(
                        snapshot.cape.root(), snapshot.capeOrigin, player.position()));
    }

    public static synchronized boolean hasFreshBody(Player player) {
        Snapshot snapshot = SNAPSHOTS.get(player);
        return snapshot != null && fresh(player, snapshot.bodyTick) && snapshot.body != null
                && Arrays.stream(snapshot.body).allMatch(java.util.Objects::nonNull);
    }

    public static synchronized Source bodySource(Player player) {
        Snapshot snapshot = SNAPSHOTS.get(player);
        return snapshot == null || !fresh(player, snapshot.bodyTick)
                ? Source.NONE : snapshot.bodySource;
    }

    public static synchronized Source capeSource(Player player) {
        Snapshot snapshot = SNAPSHOTS.get(player);
        return snapshot == null || !fresh(player, snapshot.capeTick)
                ? Source.NONE : snapshot.capeSource;
    }

    public static synchronized void clear(Player player) {
        SNAPSHOTS.remove(player);
    }

    public static synchronized void clearAll() {
        SNAPSHOTS.clear();
        lastPruneTick = Integer.MIN_VALUE;
    }

    private static void prune(int currentTick) {
        int elapsed = currentTick - lastPruneTick;
        if (SNAPSHOTS.size() < PRUNE_SIZE_THRESHOLD
                && elapsed >= 0 && elapsed < PRUNE_INTERVAL_TICKS) {
            return;
        }
        lastPruneTick = currentTick;
        Iterator<Map.Entry<Player, Snapshot>> iterator = SNAPSHOTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, Snapshot> entry = iterator.next();
            Player player = entry.getKey();
            Snapshot snapshot = entry.getValue();
            if (player == null || player.isRemoved()
                    || !fresh(player, snapshot.bodyTick) && !fresh(player, snapshot.capeTick)) {
                iterator.remove();
            }
        }
    }

    private static boolean fresh(Player player, int tick) {
        int age = player.tickCount - tick;
        return age >= 0 && age <= MAX_FRAME_AGE_TICKS;
    }

    static Vec3 reanchor(Vec3 capturedPoint, Vec3 capturedOrigin, Vec3 currentOrigin) {
        return capturedPoint.add(currentOrigin.subtract(capturedOrigin));
    }

    public record PartPose(Vec3 center, Vec3 halfSide, Vec3 halfUp, Vec3 halfForward) {
        public PartPose withCenter(Vec3 nextCenter) {
            return new PartPose(nextCenter, halfSide, halfUp, halfForward);
        }

        public Vec3 side() {
            return halfSide.normalize();
        }

        public Vec3 up() {
            return halfUp.normalize();
        }

        public Vec3 forward() {
            return halfForward.normalize();
        }

        public double halfWidth() {
            return halfSide.length();
        }

        public double halfHeight() {
            return halfUp.length();
        }

        public double halfDepth() {
            return halfForward.length();
        }
    }

    public record CapePose(Vec3 root, Vec3 side, Vec3 down, Vec3 normal, double scale) {
        public CapePose withRoot(Vec3 nextRoot) {
            return new CapePose(nextRoot, side, down, normal, scale);
        }

        public MudCapeGeometry.CapeBasis basis() {
            return new MudCapeGeometry.CapeBasis(root, side, down, normal, scale);
        }
    }

    public enum Source {
        NONE,
        MODEL_PART,
        SODIUM_VERTICES,
        REMOTE
    }

    private record Snapshot(int bodyTick, Vec3 bodyOrigin, PartPose[] body, Source bodySource,
            int capeTick, Vec3 capeOrigin, CapePose cape, Source capeSource) {
    }
}
