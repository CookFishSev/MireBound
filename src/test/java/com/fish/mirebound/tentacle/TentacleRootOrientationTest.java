package com.fish.mirebound.tentacle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Characterizes the direction of the first chain segment, which is what the renderer uses to
 * orient the root cap.
 *
 * <p>{@code ProceduralTentacleRenderer} builds the root cap along {@code -tangent(points, 0)},
 * where the tangent is {@code points[1] - points[0]}. Nothing in the solver constrains that
 * direction, so if the first segment settles pointing up the cap renders pointing down regardless
 * of where the tentacle is actually reaching.
 */
class TentacleRootOrientationTest {
    @Test
    void rootSegmentFollowsAHorizontalGuideInsteadOfCollapsingDownward() {
        TentaclePhysicsProfile profile = profile();
        Vec3 root = new Vec3(0.0D, 64.0D, 0.0D);
        // A guide that leaves the root essentially horizontally. A healthy root segment should
        // leave along the guide, not perpendicular to it.
        Vec3 goal = root.add(profile.maximumLength(), 0.2D, 0.0D);
        List<Vec3> guide = List.of(root, goal);
        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);
        chain.initializeAlongPath(profile, guide, space(), profile.maximumLength());
        for (int tick = 0; tick < 400; tick++) {
            chain.step(profile, root, 1.0D, 1.0D, guide,
                    profile.trackingTipAdvanceSpeed(), space(), tick, 42L,
                    Vec3.ZERO, 1.0D, 1.0D);
        }

        Vec3 rootDirection = chain.point(1).subtract(chain.point(0)).normalize();
        Vec3 guideDirection = goal.subtract(root).normalize();
        double alignment = rootDirection.dot(guideDirection);
        assertTrue(alignment > 0.5D,
                "root segment must leave along the guide; alignment=" + alignment
                        + " rootDirection=" + rootDirection
                        + " guideDirection=" + guideDirection);
    }

    /**
     * The failing case. When the route is shorter than the chain's rest length, the guide packs
     * every point toward the root end of the route while the length constraint refuses to compress
     * segments. The excess length has to buckle, and because no constraint owns the root direction
     * the first segment folds away from the guide and settles pointing steeply down.
     */
    @Test
    void rootSegmentStillFollowsTheGuideWhenTheRouteIsShorterThanTheChain() {
        TentaclePhysicsProfile profile = profile();
        Vec3 root = new Vec3(0.0D, 64.0D, 0.0D);
        double reach = profile.maximumLength();
        StringBuilder detail = new StringBuilder();
        int followed = 0;
        double[] routeFractions = { 1.0D, 0.8D, 0.6D, 0.45D, 0.35D, 0.25D };
        for (double fraction : routeFractions) {
            // An upward-and-outward idle goal, scaled down so the route gets shorter than the
            // chain while its direction stays constant.
            Vec3 goal = root.add(reach * fraction * 0.6D, reach * fraction * 0.8D, 0.0D);
            List<Vec3> guide = List.of(root, goal);
            TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);
            chain.initializeAlongPath(profile, guide, space(), root.distanceTo(goal));
            for (int tick = 0; tick < 600; tick++) {
                chain.step(profile, root, 1.0D, 1.0D, guide,
                        profile.trackingTipAdvanceSpeed(), space(), tick, 42L,
                        Vec3.ZERO, 1.0D, 0.0D);
            }
            Vec3 rootDirection = chain.point(1).subtract(chain.point(0)).normalize();
            double alignment = rootDirection.dot(goal.subtract(root).normalize());
            detail.append(String.format(
                    "%n  routeFraction=%.2f rootDirection=(%.3f,%.3f,%.3f) alignment=%+.3f",
                    fraction, rootDirection.x, rootDirection.y, rootDirection.z, alignment));
            if (alignment > 0.0D) {
                followed++;
            }
        }
        assertTrue(followed == routeFractions.length,
                "a short route must not invert the root segment; followed "
                        + followed + "/" + routeFractions.length + detail);
    }

    private static TentaclePhysicsProfile profile() {
        return new TentaclePhysicsProfile(
                16, 64.0D,
                8, 0.20D, 0.14D, 0.06D,
                0.008D, 0.92D, 0.0D, 1.18D, 0.0005D, 0.32D, 0.96D, 2.40D,
                true, 0.92D, 0.68D,
                2, 8, 0.08D, 0.10D,
                2.0D, 1.4D, 0.25D, 0.06D, 56,
                0.06D, 0.82D, 0.58D,
                0.0D, 0.0D, 0.48D, 0.65D, 0.0D, 0.0D, 0.08D,
                0.22D, 0.85D, 0.30D, 0.48D, 4, 0.045D, 2.75D, 2.40D,
                0.11D, 0.34D, 1.18D, 0.055D, 0.42D,
                3, 0.34D,
                0.25D, 0.12D, 1.05D, 1.5D, 8, 40, 0.12D, 4, 4096, 0.45D, 3,
                0.45D, 4, 96, 0.97D, 0.002D,
                true, 0.72D, 0.055D, 0.18D, 0.68D, 0.16D, 0.82D, 0.90D, 12, 3, 8192,
                2);
    }

    private static AabbTentacleCollisionSpace space(AABB... boxes) {
        return new AabbTentacleCollisionSpace(List.of(boxes), 0.002D);
    }
}
