package com.fish.mirebound.tentacle;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded server-side capsule contacts between a procedural chain and nearby entities. */
final class TentacleEntityCollider {
    private static final double EPSILON = 1.0E-10D;

    private TentacleEntityCollider() {
    }

    static List<Entity> queryCandidates(ServerLevel level, TentacleChainSolver chain,
            TentaclePhysicsProfile profile, double extension) {
        double radiusScale = Mth.clamp(extension, 0.0D, 1.0D);
        if (!profile.entityCollisionEnabled() || profile.collisionMaximumEntities() <= 0
                || chain.pointCount() < 2 || radiusScale <= 0.01D) {
            return List.of();
        }
        double broadphasePadding = profile.rootRadius() * radiusScale
                + profile.bodyMaximumDeflection() + profile.collisionMaximumPushSpeed()
                + profile.trackingTipAdvanceSpeed() * profile.entityQueryInterval();
        Vec3 root = chain.point(0);
        double maximumReach = profile.maximumLength() + broadphasePadding + 1.0D;
        AABB physicalBounds = chain.bounds(broadphasePadding);
        AABB safetyBounds = new AABB(root.subtract(maximumReach, maximumReach, maximumReach),
                root.add(maximumReach, maximumReach, maximumReach));
        AABB bounds = physicalBounds.intersect(safetyBounds);
        int[] accepted = {0};
        return level.getEntities((Entity) null, bounds,
                entity -> {
                    if (accepted[0] >= profile.collisionMaximumEntities()
                            || !entity.isAlive() || entity.isSpectator() || entity.noPhysics) {
                        return false;
                    }
                    accepted[0]++;
                    return true;
                });
    }

    static void collide(List<Entity> candidates, TentacleChainSolver chain,
            TentaclePhysicsProfile profile, TentaclePhysicsProfile sourceProfile,
            double extension, Entity ignored) {
        double radiusScale = Mth.clamp(extension, 0.0D, 1.0D);
        if (!profile.entityCollisionEnabled() || profile.collisionMaximumEntities() <= 0
                || profile.collisionResponse() <= 0.0D || chain.pointCount() < 2
                || radiusScale <= 0.01D || candidates.isEmpty()) {
            return;
        }
        int processed = 0;
        for (Entity entity : candidates) {
            if (processed >= profile.collisionMaximumEntities()) {
                break;
            }
            if (!entity.isAlive() || entity.isRemoved() || entity.isSpectator() || entity.noPhysics) {
                continue;
            }
            if (entity == ignored) {
                continue;
            }
            Contact contact = deepestContact(chain, profile, radiusScale, entity.getBoundingBox());
            if (contact == null) {
                continue;
            }
            resolve(entity, chain, profile, sourceProfile, contact);
            processed++;
        }
    }

    private static Contact deepestContact(TentacleChainSolver chain,
            TentaclePhysicsProfile profile, double radiusScale, AABB entityBounds) {
        return deepestContact(chain, profile, radiusScale, entityBounds, 0, 0.0D);
    }

    static GrabContact grabTargetContact(TentacleChainSolver chain,
            TentaclePhysicsProfile profile, double extension, Vec3 targetPoint,
            double targetRadius, int tipSegments, double contactPadding) {
        int firstSegment = Math.max(0,
                chain.pointCount() - 1 - Math.max(1, tipSegments));
        double radiusScale = Mth.clamp(extension, 0.0D, 1.0D);
        double safeTargetRadius = Math.max(0.0D, targetRadius);
        double padding = Math.max(0.0D, contactPadding);
        GrabContact deepest = null;
        for (int segment = firstSegment; segment < chain.pointCount() - 1; segment++) {
            Vec3 from = chain.point(segment);
            Vec3 to = chain.point(segment + 1);
            Vec3 direction = to.subtract(from);
            double amount = direction.lengthSqr() <= EPSILON ? 0.0D
                    : Mth.clamp(targetPoint.subtract(from).dot(direction)
                            / direction.lengthSqr(), 0.0D, 1.0D);
            Vec3 tentaclePoint = from.add(direction.scale(amount));
            Vec3 separation = targetPoint.subtract(tentaclePoint);
            double distance = separation.length();
            double tentacleRadius = Math.max(
                    chain.radiusAt(profile, segment), chain.radiusAt(profile, segment + 1))
                    * radiusScale + padding;
            double penetration = tentacleRadius + safeTargetRadius - distance;
            if (penetration <= 0.0D
                    || deepest != null && penetration <= deepest.penetration()) {
                continue;
            }
            Vec3 normal = distance <= EPSILON
                    ? new Vec3(0.0D, 1.0D, 0.0D) : separation.scale(1.0D / distance);
            Vec3 surfacePoint = targetPoint.subtract(normal.scale(safeTargetRadius));
            deepest = new GrabContact(segment, tentaclePoint, surfacePoint, penetration);
        }
        return deepest;
    }

    private static Contact deepestContact(TentacleChainSolver chain,
            TentaclePhysicsProfile profile, double radiusScale, AABB entityBounds,
            int firstSegment, double contactPadding) {
        Capsule entityCapsule = entityCapsule(entityBounds);
        Contact deepest = null;
        for (int segment = Math.max(0, firstSegment); segment < chain.pointCount() - 1; segment++) {
            double tentacleRadius = Math.max(
                    chain.radiusAt(profile, segment), chain.radiusAt(profile, segment + 1))
                    * radiusScale + contactPadding;
            SegmentPair pair = closestPoints(chain.point(segment), chain.point(segment + 1),
                    entityCapsule.from(), entityCapsule.to());
            Vec3 separation = pair.second().subtract(pair.first());
            double distance = separation.length();
            double penetration = tentacleRadius + entityCapsule.radius() - distance;
            if (penetration <= 0.0D || deepest != null && penetration <= deepest.penetration()) {
                continue;
            }
            Vec3 normal = distance > EPSILON
                    ? separation.scale(1.0D / distance)
                    : fallbackNormal(entityBounds, pair.first(), segment);
            deepest = new Contact(segment, normal, penetration,
                    tentacleRadius + entityCapsule.radius(), pair.first(), pair.second());
        }
        return deepest;
    }

    private static void resolve(Entity entity, TentacleChainSolver chain,
            TentaclePhysicsProfile profile, TentaclePhysicsProfile sourceProfile, Contact contact) {
        double sizeScale = sizeScale(profile, sourceProfile);
        double forceScale = Math.pow(sizeScale, profile.sizeForceExponent());
        double stiffnessScale = Math.pow(sizeScale, profile.sizeStiffnessExponent());
        double correctionLength = Math.min(profile.collisionMaximumPushSpeed(),
                contact.penetration() * profile.collisionResponse() * forceScale);
        boolean synchronizedPlayer = entity instanceof ServerPlayer;
        Vec3 velocityPush = Vec3.ZERO;
        if (correctionLength > EPSILON) {
            Vec3 correction = contact.normal().scale(correctionLength);
            if (synchronizedPlayer) {
                velocityPush = velocityPush.add(correction);
            } else {
                entity.move(MoverType.SELF, correction);
            }
        }

        double penetrationRatio = Mth.clamp(contact.penetration()
                / Math.max(0.01D, contact.combinedRadius()), 0.0D, 1.0D);
        double impulseLength = Math.min(profile.collisionMaximumPushSpeed(),
                profile.collisionImpulse() * forceScale * penetrationRatio);
        if (impulseLength > EPSILON) {
            velocityPush = velocityPush.add(contact.normal().scale(impulseLength));
        }
        if (velocityPush.lengthSqr() > EPSILON) {
            entity.setDeltaMovement(boundedPushVelocity(
                    entity.getDeltaMovement(), velocityPush, profile.collisionMaximumPushSpeed()));
            entity.hasImpulse = true;
            if (entity instanceof ServerPlayer player) {
                // ServerPlayer movement is client-predicted. hurtMarked makes the server
                // send the authoritative velocity instead of letting the next input packet
                // overwrite a post-tick position correction.
                player.hurtMarked = true;
            }
        }

        double reaction = Math.min(profile.bodyMaximumDeflection(),
                contact.penetration() * profile.bodyCompliance() / Math.max(0.20D, stiffnessScale));
        if (reaction > EPSILON) {
            chain.displaceSegment(contact.segment(), contact.normal().scale(-reaction), 1.0D);
        }
    }

    static double sizeScale(TentaclePhysicsProfile profile, TentaclePhysicsProfile sourceProfile) {
        return Mth.clamp(profile.rootRadius() / Math.max(0.01D, sourceProfile.rootRadius()),
                0.20D, 8.0D);
    }

    static double forceMultiplier(double sizeScale, double exponent) {
        return Math.pow(Math.max(0.01D, sizeScale), Math.max(0.0D, exponent));
    }

    static double deflectionMultiplier(double sizeScale, double exponent) {
        return 1.0D / Math.max(0.20D,
                Math.pow(Math.max(0.01D, sizeScale), Math.max(0.0D, exponent)));
    }

    static SegmentPair closestPoints(Vec3 firstStart, Vec3 firstEnd,
            Vec3 secondStart, Vec3 secondEnd) {
        Vec3 firstDirection = firstEnd.subtract(firstStart);
        Vec3 secondDirection = secondEnd.subtract(secondStart);
        Vec3 betweenStarts = firstStart.subtract(secondStart);
        double firstLengthSquared = firstDirection.lengthSqr();
        double secondLengthSquared = secondDirection.lengthSqr();
        double secondProjection = secondDirection.dot(betweenStarts);
        double firstAmount;
        double secondAmount;

        if (firstLengthSquared <= EPSILON && secondLengthSquared <= EPSILON) {
            return new SegmentPair(firstStart, secondStart);
        }
        if (firstLengthSquared <= EPSILON) {
            firstAmount = 0.0D;
            secondAmount = Mth.clamp(secondProjection / secondLengthSquared, 0.0D, 1.0D);
        } else {
            double firstProjection = firstDirection.dot(betweenStarts);
            if (secondLengthSquared <= EPSILON) {
                secondAmount = 0.0D;
                firstAmount = Mth.clamp(-firstProjection / firstLengthSquared, 0.0D, 1.0D);
            } else {
                double directionsDot = firstDirection.dot(secondDirection);
                double denominator = firstLengthSquared * secondLengthSquared
                        - directionsDot * directionsDot;
                firstAmount = denominator <= EPSILON ? 0.0D
                        : Mth.clamp((directionsDot * secondProjection
                                - firstProjection * secondLengthSquared) / denominator, 0.0D, 1.0D);
                secondAmount = (directionsDot * firstAmount + secondProjection) / secondLengthSquared;
                if (secondAmount < 0.0D) {
                    secondAmount = 0.0D;
                    firstAmount = Mth.clamp(-firstProjection / firstLengthSquared, 0.0D, 1.0D);
                } else if (secondAmount > 1.0D) {
                    secondAmount = 1.0D;
                    firstAmount = Mth.clamp((directionsDot - firstProjection)
                            / firstLengthSquared, 0.0D, 1.0D);
                }
            }
        }
        return new SegmentPair(firstStart.add(firstDirection.scale(firstAmount)),
                secondStart.add(secondDirection.scale(secondAmount)));
    }

    private static Capsule entityCapsule(AABB bounds) {
        double radius = Math.max(0.08D, Math.min(
                Math.min(bounds.getXsize(), bounds.getZsize()) * 0.50D,
                bounds.getYsize() * 0.45D));
        double centerX = (bounds.minX + bounds.maxX) * 0.5D;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        double minimumY = bounds.minY + radius;
        double maximumY = Math.max(minimumY, bounds.maxY - radius);
        return new Capsule(new Vec3(centerX, minimumY, centerZ),
                new Vec3(centerX, maximumY, centerZ), radius);
    }

    private static Vec3 fallbackNormal(AABB bounds, Vec3 point, int segment) {
        Vec3 outward = bounds.getCenter().subtract(point);
        if (outward.lengthSqr() > EPSILON) {
            return outward.normalize();
        }
        return (segment & 1) == 0 ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 0.0D, 1.0D);
    }

    static Vec3 boundedPushVelocity(Vec3 current, Vec3 addition, double maximum) {
        double length = addition.length();
        if (length <= EPSILON || maximum <= EPSILON) {
            return current;
        }
        Vec3 direction = addition.scale(1.0D / length);
        double verticalShare = Math.abs(direction.y);
        double verticalLimit = Math.min(maximum * 0.22D, 0.04D);
        double directionalLimit = Mth.lerp(verticalShare, maximum, verticalLimit);
        double existingOutward = Math.max(0.0D, current.dot(direction));
        double allowed = Math.max(0.0D, directionalLimit - existingOutward);
        return current.add(direction.scale(Math.min(length, allowed)));
    }

    record SegmentPair(Vec3 first, Vec3 second) {
    }

    record GrabContact(int segment, Vec3 tentaclePoint, Vec3 entityPoint, double penetration) {
    }

    private record Capsule(Vec3 from, Vec3 to, double radius) {
    }

    private record Contact(int segment, Vec3 normal, double penetration, double combinedRadius,
            Vec3 tentaclePoint, Vec3 entityPoint) {
    }
}
