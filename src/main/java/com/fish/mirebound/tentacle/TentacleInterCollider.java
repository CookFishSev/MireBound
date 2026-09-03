package com.fish.mirebound.tentacle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded, velocity-neutral capsule contacts between independent tentacles.
 * Body AABBs and segment AABBs reject the common non-overlapping case before
 * the more expensive closest-point calculation.
 */
final class TentacleInterCollider {
    private static final double EPSILON = 1.0E-10D;
    private static final int MAXIMUM_BODY_PAIRS_PER_TICK = 128;
    private static final int MAXIMUM_CAPSULE_TESTS_PER_TICK = 8192;

    private TentacleInterCollider() {
    }

    static Stats resolve(List<Body> sourceBodies, long gameTime) {
        if (sourceBodies.size() < 2) {
            return Stats.EMPTY;
        }
        List<Body> bodies = new ArrayList<>(sourceBodies.size());
        for (Body body : sourceBodies) {
            if (body.active()) {
                bodies.add(body);
            }
        }
        bodies.sort(Comparator.comparingInt(Body::id));
        if (bodies.size() < 2) {
            return Stats.EMPTY;
        }

        List<BodyPair> candidates = new ArrayList<>();
        for (int first = 0; first < bodies.size() - 1; first++) {
            Body a = bodies.get(first);
            AABB firstBounds = a.bounds();
            for (int second = first + 1; second < bodies.size(); second++) {
                Body b = bodies.get(second);
                if (firstBounds.intersects(b.bounds())) {
                    candidates.add(new BodyPair(a, b));
                }
            }
        }
        if (candidates.isEmpty()) {
            return Stats.EMPTY;
        }

        int pairLimit = Math.min(MAXIMUM_BODY_PAIRS_PER_TICK, candidates.size());
        int start = candidates.size() <= pairLimit ? 0
                : Math.floorMod((int) (gameTime * 31L), candidates.size());
        int capsuleTests = 0;
        int processedPairs = 0;
        int contacts = 0;
        double totalCorrection = 0.0D;
        Set<Body> touched = new HashSet<>();
        for (int offset = 0; offset < pairLimit
                && capsuleTests < MAXIMUM_CAPSULE_TESTS_PER_TICK; offset++) {
            BodyPair pair = candidates.get((start + offset) % candidates.size());
            processedPairs++;
            Search search = deepestContact(pair.first(), pair.second(),
                    MAXIMUM_CAPSULE_TESTS_PER_TICK - capsuleTests);
            capsuleTests += search.tests();
            Contact contact = search.contact();
            if (contact == null) {
                continue;
            }

            double response = Math.min(
                    pair.first().profile().selfCollisionResponse(),
                    pair.second().profile().selfCollisionResponse());
            double maximum = Math.min(0.08D, contact.combinedRadius() * 0.45D);
            double correctionLength = Math.min(maximum,
                    contact.penetration() * Mth.clamp(response, 0.0D, 1.0D));
            if (correctionLength <= EPSILON) {
                continue;
            }
            double firstMobility = pair.first().chain().externalContactMobility(
                    contact.firstSegment(), contact.firstAmount(), pair.first().pinTip());
            double secondMobility = pair.second().chain().externalContactMobility(
                    contact.secondSegment(), contact.secondAmount(), pair.second().pinTip());
            double totalMobility = firstMobility + secondMobility;
            if (totalMobility <= EPSILON) {
                continue;
            }
            Vec3 correction = contact.normal().scale(correctionLength);
            pair.first().chain().displaceContact(
                    contact.firstSegment(), contact.firstAmount(),
                    correction.scale(-firstMobility / totalMobility),
                    pair.first().pinTip());
            pair.second().chain().displaceContact(
                    contact.secondSegment(), contact.secondAmount(),
                    correction.scale(secondMobility / totalMobility),
                    pair.second().pinTip());
            touched.add(pair.first());
            touched.add(pair.second());
            contacts++;
            totalCorrection += correctionLength;
        }

        // Inter-tentacle separation is positional and therefore does not inject
        // Verlet velocity. Terrain projection runs afterward so one tentacle
        // cannot solve another by moving through a wall.
        for (Body body : touched) {
            body.chain().reprojectExternalCorrection(body.profile(), body.collision());
        }
        return new Stats(candidates.size(), processedPairs, capsuleTests,
                contacts, totalCorrection);
    }

    private static Search deepestContact(Body first, Body second, int remainingTests) {
        Contact deepest = null;
        int tests = 0;
        double firstScale = first.radiusScale();
        double secondScale = second.radiusScale();
        for (int firstSegment = 0;
                firstSegment < first.chain().pointCount() - 1; firstSegment++) {
            double firstRadius = segmentRadius(first, firstSegment, firstScale);
            Vec3 firstStart = first.chain().point(firstSegment);
            Vec3 firstEnd = first.chain().point(firstSegment + 1);
            for (int secondSegment = 0;
                    secondSegment < second.chain().pointCount() - 1; secondSegment++) {
                double secondRadius = segmentRadius(second, secondSegment, secondScale);
                double minimum = firstRadius + secondRadius;
                Vec3 secondStart = second.chain().point(secondSegment);
                Vec3 secondEnd = second.chain().point(secondSegment + 1);
                if (!segmentBoundsIntersect(firstStart, firstEnd, secondStart, secondEnd, minimum)) {
                    continue;
                }
                if (tests >= remainingTests) {
                    return new Search(deepest, tests);
                }
                tests++;
                TentacleEntityCollider.SegmentPair closest =
                        TentacleEntityCollider.closestPoints(
                                firstStart, firstEnd, secondStart, secondEnd);
                Vec3 delta = closest.second().subtract(closest.first());
                double distance = delta.length();
                double penetration = minimum - distance;
                if (penetration <= 0.0D
                        || deepest != null && penetration <= deepest.penetration()) {
                    continue;
                }
                Vec3 normal = distance > EPSILON
                        ? delta.scale(1.0D / distance)
                        : fallbackNormal(first, second, firstSegment, secondSegment,
                                firstEnd.subtract(firstStart), secondEnd.subtract(secondStart));
                deepest = new Contact(firstSegment,
                        segmentAmount(firstStart, firstEnd, closest.first()),
                        secondSegment,
                        segmentAmount(secondStart, secondEnd, closest.second()),
                        normal, penetration, minimum);
            }
        }
        return new Search(deepest, tests);
    }

    private static double segmentRadius(Body body, int segment, double radiusScale) {
        double radius = Math.max(
                body.chain().radiusAt(body.profile(), segment),
                body.chain().radiusAt(body.profile(), segment + 1));
        return radius * radiusScale;
    }

    private static boolean segmentBoundsIntersect(Vec3 firstStart, Vec3 firstEnd,
            Vec3 secondStart, Vec3 secondEnd, double padding) {
        return Math.min(firstStart.x, firstEnd.x) - padding
                        <= Math.max(secondStart.x, secondEnd.x)
                && Math.max(firstStart.x, firstEnd.x) + padding
                        >= Math.min(secondStart.x, secondEnd.x)
                && Math.min(firstStart.y, firstEnd.y) - padding
                        <= Math.max(secondStart.y, secondEnd.y)
                && Math.max(firstStart.y, firstEnd.y) + padding
                        >= Math.min(secondStart.y, secondEnd.y)
                && Math.min(firstStart.z, firstEnd.z) - padding
                        <= Math.max(secondStart.z, secondEnd.z)
                && Math.max(firstStart.z, firstEnd.z) + padding
                        >= Math.min(secondStart.z, secondEnd.z);
    }

    private static double segmentAmount(Vec3 start, Vec3 end, Vec3 point) {
        Vec3 direction = end.subtract(start);
        return direction.lengthSqr() <= EPSILON ? 0.0D
                : Mth.clamp(point.subtract(start).dot(direction)
                        / direction.lengthSqr(), 0.0D, 1.0D);
    }

    private static Vec3 fallbackNormal(Body first, Body second,
            int firstSegment, int secondSegment, Vec3 firstTangent, Vec3 secondTangent) {
        Vec3 normal = firstTangent.cross(secondTangent);
        if (normal.lengthSqr() <= EPSILON) {
            normal = second.chain().point(0).subtract(first.chain().point(0));
        }
        if (normal.lengthSqr() <= EPSILON) {
            int axis = Math.floorMod(
                    first.id() * 31 + second.id() * 17
                            + firstSegment * 13 + secondSegment * 7, 3);
            normal = axis == 0 ? new Vec3(1.0D, 0.0D, 0.0D)
                    : axis == 1 ? new Vec3(0.0D, 1.0D, 0.0D)
                            : new Vec3(0.0D, 0.0D, 1.0D);
        }
        return normal.normalize();
    }

    record Body(int id, TentacleChainSolver chain, TentaclePhysicsProfile profile,
            double extension, TentacleCollisionSpace collision, boolean pinTip) {
        boolean active() {
            return chain != null && profile != null && chain.pointCount() >= 2
                    && extension > 0.01D && profile.selfCollisionEnabled()
                    && profile.selfCollisionResponse() > 0.0D;
        }

        double radiusScale() {
            return Mth.clamp(extension, 0.0D, 1.0D)
                    * profile.selfCollisionRadiusScale();
        }

        AABB bounds() {
            return chain.bounds(profile.rootRadius() * radiusScale());
        }
    }

    record Stats(int candidatePairs, int processedPairs, int capsuleTests,
            int contacts, double correction) {
        private static final Stats EMPTY = new Stats(0, 0, 0, 0, 0.0D);
    }

    private record BodyPair(Body first, Body second) {
    }

    private record Search(Contact contact, int tests) {
    }

    private record Contact(int firstSegment, double firstAmount,
            int secondSegment, double secondAmount, Vec3 normal,
            double penetration, double combinedRadius) {
    }
}
