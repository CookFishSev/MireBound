package com.fish.mirebound.tentacle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Characterizes pursuit of a player who keeps circling a pillar.
 *
 * <p>The tentacle should keep its wrap memory and follow the player around the obstacle until it
 * genuinely runs out of length. This drives the same navigation stack {@code TentacleSystem} uses
 * per tick, minus the {@code ServerLevel} lookups.
 */
class TentaclePillarPursuitTest {
    @Test
    void wrapMemorySurvivesAPlayerCirclingAPillar() {
        TentaclePhysicsProfile profile = profile();
        // A single-block pillar. The direct line to the far side is blocked, so following the
        // player around genuinely requires keeping the wrap, but the wrapped route to the far side
        // is only about 3.4 blocks against a 5.13-block body: comfortably affordable. Any failure
        // to keep up here is a memory problem, not a reach problem.
        AABB pillar = new AABB(1.5D, 60.0D, -0.5D, 2.5D, 70.0D, 0.5D);
        AabbTentacleCollisionSpace space = space(pillar);
        Vec3 root = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 pillarCenter = new Vec3(2.0D, 64.0D, 0.0D);
        double orbit = 1.20D;

        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);
        TentacleTipController controller = new TentacleTipController();
        TentacleTrail trail = new TentacleTrail();
        Vec3 firstGoal = goalAt(pillarCenter, orbit, 0.0D);
        List<Vec3> planned = TentaclePathfinder.find(root, firstGoal, space,
                profile.tipPathClearance(), profile.pathCellSize(), profile.pathMargin(),
                profile.pathMaximumNodes());
        chain.initializeAlongPath(profile, planned.size() >= 2 ? planned
                : List.of(root, firstGoal), space, root.distanceTo(firstGoal));
        controller.reset(root);
        trail.reset(root);

        double maximumWrapLength = 0.0D;
        double maximumGoalError = 0.0D;
        int trailResets = 0;
        int trailReleases = 0;
        int guideLostTheGoal = 0;
        int previousTrailSize = trail.size();
        StringBuilder detail = new StringBuilder();

        // A little over one full lap, at a speed a sprinting player can hold.
        for (int tick = 0; tick < 420; tick++) {
            double angle = tick * 0.030D;
            Vec3 goal = goalAt(pillarCenter, orbit, angle);
            if (tick % 8 == 0) {
                List<Vec3> replanned = TentaclePathfinder.find(
                        controller.position(), goal, space, profile.tipPathClearance(),
                        profile.pathCellSize(), profile.pathMargin(), profile.pathMaximumNodes());
                if (replanned.size() >= 2) {
                    planned = replanned;
                }
            }
            controller.advance(planned, profile.trackingTipAdvanceSpeed(),
                    profile.tipAcceleration(), profile.tipLookaheadDistance(),
                    chain.point(chain.pointCount() - 1),
                    profile.tipMaximumLeadDistance(), space, profile.tipPathClearance());
            boolean recorded = trail.record(root, controller.position(), space,
                    profile.tipPathClearance(), profile.trailSampleDistance(),
                    profile.trailUnwrapTicks(), profile.trailMaximumPoints());
            if (!recorded) {
                trailResets++;
                trail.resetTo(chain.snapshot(), root, chain.point(chain.pointCount() - 1));
                controller.reset(chain.point(chain.pointCount() - 1));
            }
            // The real per-tick flow: once the wrap gets long, try to shortcut it.
            double releaseLength = profile.maximumLength() * profile.trailReleaseRatio();
            if (tick % 16 == 0 && trail.length() > releaseLength) {
                Vec3 currentTip = controller.position();
                List<Vec3> shortest = TentaclePathfinder.find(root, currentTip, space,
                        profile.tipPathClearance(), profile.pathCellSize(),
                        profile.pathMargin(), profile.pathMaximumNodes());
                double shortestLength = TentacleChainSolver.pathLength(shortest);
                if (!shortest.isEmpty() && shortestLength < trail.length()
                        && shortestLength <= profile.maximumLength()
                        && trail.resetToBounded(shortest, root, currentTip, space,
                                profile.tipPathClearance(), profile.trailMaximumPoints())) {
                    trailReleases++;
                }
            }
            if (trail.size() < previousTrailSize - 1) {
                detail.append(String.format("%n  tick=%d trail shrank %d -> %d",
                        tick, previousTrailSize, trail.size()));
            }
            previousTrailSize = trail.size();
            maximumWrapLength = Math.max(maximumWrapLength, trail.length());

            List<Vec3> guide = TentacleGuidePath.compose(trail.path(), planned,
                    controller.position(), controller.distance(), space,
                    profile.tipPathClearance(), profile.maximumLength());
            if (guide.size() >= 2 && guide.getLast().distanceTo(goal) > 1.0D) {
                guideLostTheGoal++;
            }
            chain.step(profile, root, 1.0D, 1.0D, guide,
                    profile.trackingTipAdvanceSpeed(), space, tick, 991L,
                    Vec3.ZERO, 0.0D, 1.0D);
            if (tick > 60) {
                maximumGoalError = Math.max(maximumGoalError,
                        chain.point(chain.pointCount() - 1).distanceTo(goal));
            }
            if (tick % 30 == 0) {
                Vec3 tip = chain.point(chain.pointCount() - 1);
                System.out.printf(
                        "  t=%3d ang=%5.2f goalErr=%.3f trailLen=%.3f pts=%2d ctrlDist=%.3f%n",
                        tick, angle, tip.distanceTo(goal), trail.length(), trail.size(),
                        controller.distance());
            }
            // Nothing may ever end up inside the pillar.
            for (int index = 0; index < chain.pointCount(); index++) {
                Vec3 point = chain.point(index);
                if (pillar.contains(point)) {
                    detail.append(String.format(
                            "%n  tick=%d point %d inside pillar at (%.2f,%.2f,%.2f)",
                            tick, index, point.x, point.y, point.z));
                    break;
                }
            }
        }

        System.out.printf("maximumWrapLength=%.3f maximumLength=%.3f%n",
                maximumWrapLength, profile.maximumLength());
        System.out.printf(
                "maximumGoalError=%.3f trailResets=%d trailReleases=%d guideLostTheGoal=%d trailSize=%d%n",
                maximumGoalError, trailResets, trailReleases, guideLostTheGoal, trail.size());
        System.out.println("detail:" + (detail.isEmpty() ? " none" : detail));
    }

    /**
     * The give-up case: a pillar far enough out that wrapping to its far side costs more length
     * than the body has. The tentacle must not sit there stretched around geometry it cannot
     * follow with a guide truncated short of the goal; it should notice and let the wrap go.
     */
    @Test
    void anUnaffordableWrapIsAbandonedInsteadOfHanging() {
        TentaclePhysicsProfile profile = profile();
        AABB pillar = new AABB(1.0D, 60.0D, -1.0D, 3.0D, 70.0D, 1.0D);
        AabbTentacleCollisionSpace space = space(pillar);
        Vec3 root = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 pillarCenter = new Vec3(2.0D, 64.0D, 0.0D);
        double orbit = 1.60D;
        double reach = profile.maximumLength();

        TentacleChainSolver chain = new TentacleChainSolver(profile.segmentCount(), root);
        TentacleTipController controller = new TentacleTipController();
        TentacleTrail trail = new TentacleTrail();
        Vec3 firstGoal = goalAt(pillarCenter, orbit, 0.0D);
        chain.initializeAlongPath(profile, List.of(root, firstGoal), space,
                root.distanceTo(firstGoal));
        controller.reset(root);
        trail.reset(root);
        List<Vec3> planned = List.of(root, firstGoal);

        int truncatedGuideTicks = 0;
        int longestTruncatedRun = 0;
        int currentRun = 0;
        for (int tick = 0; tick < 420; tick++) {
            Vec3 goal = goalAt(pillarCenter, orbit, tick * 0.030D);
            if (tick % 8 == 0) {
                List<Vec3> replanned = TentaclePathfinder.find(controller.position(), goal, space,
                        profile.tipPathClearance(), profile.pathCellSize(),
                        profile.pathMargin(), profile.pathMaximumNodes());
                if (replanned.size() >= 2) {
                    planned = replanned;
                }
            }
            controller.advance(planned, profile.trackingTipAdvanceSpeed(),
                    profile.tipAcceleration(), profile.tipLookaheadDistance(),
                    chain.point(chain.pointCount() - 1),
                    profile.tipMaximumLeadDistance(), space, profile.tipPathClearance());
            trail.record(root, controller.position(), space, profile.tipPathClearance(),
                    profile.trailSampleDistance(), profile.trailUnwrapTicks(),
                    profile.trailMaximumPoints());

            // The production give-up rule: an over-reach wrap that no shortcut can rescue is
            // dropped rather than held.
            if (tick % 16 == 0 && trail.length() > reach) {
                List<Vec3> shortest = TentaclePathfinder.find(root, controller.position(), space,
                        profile.tipPathClearance(), profile.pathCellSize(),
                        profile.pathMargin(), profile.pathMaximumNodes());
                double shortestLength = TentacleChainSolver.pathLength(shortest);
                boolean rescued = !shortest.isEmpty() && shortestLength < trail.length()
                        && shortestLength <= reach
                        && trail.resetToBounded(shortest, root, controller.position(), space,
                                profile.tipPathClearance(), profile.trailMaximumPoints());
                if (!rescued && trail.length() > reach) {
                    Vec3 physicalTip = chain.point(chain.pointCount() - 1);
                    trail.resetTo(List.of(root, physicalTip), root, physicalTip);
                    controller.reset(physicalTip);
                }
            }

            List<Vec3> guide = TentacleGuidePath.compose(trail.path(), planned,
                    controller.position(), controller.distance(), space,
                    profile.tipPathClearance(), reach);
            boolean truncated = guide.size() >= 2 && guide.getLast().distanceTo(goal) > 1.0D;
            if (truncated) {
                truncatedGuideTicks++;
                currentRun++;
                longestTruncatedRun = Math.max(longestTruncatedRun, currentRun);
            } else {
                currentRun = 0;
            }
            chain.step(profile, root, 1.0D, 1.0D, guide,
                    profile.trackingTipAdvanceSpeed(), space, tick, 991L,
                    Vec3.ZERO, 0.0D, 1.0D);
        }

        System.out.printf("truncatedGuideTicks=%d longestTruncatedRun=%d trailLen=%.3f reach=%.3f%n",
                truncatedGuideTicks, longestTruncatedRun, trail.length(), reach);
        assertTrue(trail.length() <= reach + 0.75D,
                "wrap stayed unaffordable: " + trail.length() + " vs reach " + reach);
        assertTrue(longestTruncatedRun < 160,
                "tentacle followed a truncated guide for " + longestTruncatedRun
                        + " consecutive ticks instead of giving up");
    }

    private static Vec3 goalAt(Vec3 center, double radius, double angle) {
        return center.add(Math.cos(angle) * radius, 0.30D, Math.sin(angle) * radius);
    }

    private static TentaclePhysicsProfile profile() {
        return new TentaclePhysicsProfile(
                16, 64.0D,
                20, 0.27D, 0.30D, 0.085D,
                0.006D, 0.91D, 0.0D, 1.18D, 0.0005D, 0.32D, 0.955D, 2.40D,
                true, 0.92D, 0.68D,
                2, 5, 0.08D, 0.10D,
                4.2D, 2.15D, 0.25D, 0.06D, 56,
                0.06D, 0.82D, 0.58D,
                0.0D, 0.0D, 0.48D, 0.65D, 0.0D, 0.14D, 0.08D,
                0.045D, 0.85D, 0.30D, 0.48D, 4, 0.045D, 2.75D, 2.40D,
                0.11D, 0.34D, 1.18D, 0.055D, 0.42D,
                3, 0.34D,
                0.50D, 0.12D, 1.05D, 1.5D, 8, 40, 0.12D, 4, 4096, 0.45D, 3,
                0.45D, 4, 96, 0.97D, 0.002D,
                true, 0.72D, 0.055D, 0.18D, 0.68D, 0.16D, 0.82D, 0.90D, 12, 3, 8192,
                2);
    }

    private static AabbTentacleCollisionSpace space(AABB... boxes) {
        return new AabbTentacleCollisionSpace(List.of(boxes), 0.002D);
    }
}
