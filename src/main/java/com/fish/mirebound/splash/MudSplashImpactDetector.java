package com.fish.mirebound.splash;

import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Emits one impact when authoritative contact crosses from outside to inside. */
public final class MudSplashImpactDetector {
    static final double REQUIRED_CONTACT_DEPTH = 0.012D;
    private static final double MAXIMUM_PLAUSIBLE_TICK_DISPLACEMENT = 4.75D;
    private static final double ABSOLUTE_CORRECTION_DISTANCE = 32.0D;
    private static final double CORRECTION_MINIMUM_SPEED_RATIO = 0.65D;
    private static final double FALL_SPEED_SQUARED_PER_BLOCK = 0.16D;

    private MudSplashImpactDetector() {
    }

    public static void tryImpact(
            ServerPlayer player,
            MudPlayerData data,
            boolean pollutionSuppressed,
            @Nullable ContactFrame contact) {
        Vec3 currentFeet = player.position();
        Vec3 currentVelocity = player.getDeltaMovement();
        boolean currentlyInside = hasQualifiedContact(contact);
        if (!data.mudImpactTrackingInitialized) {
            data.updateMudImpactTracking(currentFeet, currentVelocity, currentlyInside);
            return;
        }

        Vec3 previousFeet = data.mudImpactPreviousFeet;
        Vec3 previousVelocity = data.mudImpactPreviousVelocity;
        boolean previouslyInside = data.mudImpactWasInside;
        Vec3 displacement = currentFeet.subtract(previousFeet);
        data.updateMudImpactTracking(currentFeet, currentVelocity, currentlyInside);

        if (contact == null
                || isCorrectionLike(displacement, previousVelocity, currentVelocity)
                || !isContactEntry(previouslyInside, currentlyInside)
                || pollutionSuppressed) {
            return;
        }

        MudSplashProfile profile = MudPhysicsSettings.mudSplashProfile();
        if (!profile.enabled()
                || !MudSplashSystem.impactCooldownElapsed(
                        player.tickCount,
                        data.lastMudSplashTick,
                        profile.impactCooldownTicks())) {
            return;
        }

        Vec3 incomingVelocity = selectImpactVelocity(
                displacement,
                previousVelocity,
                currentVelocity,
                contact.surfaceNormal(),
                player.fallDistance);
        if (!qualifiesEntry(
                previouslyInside,
                currentlyInside,
                inwardSpeed(incomingVelocity, contact.surfaceNormal()),
                profile.minimumImpactSpeed())) {
            return;
        }

        MudSplashSystem.tryImpact(
                player,
                data,
                contact.medium(),
                contact.surfacePoint(),
                contact.surfaceNormal(),
                contact.surfaceAxisX(),
                contact.surfaceAxisZ(),
                incomingVelocity,
                contact.volumeFraction(),
                contact.createSurfacePile());
    }

    static boolean isContactEntry(boolean previouslyInside, boolean currentlyInside) {
        return !previouslyInside && currentlyInside;
    }

    static boolean hasQualifiedContact(@Nullable ContactFrame contact) {
        return contact != null && contact.depth() >= REQUIRED_CONTACT_DEPTH;
    }

    static boolean qualifiesEntry(
            boolean previouslyInside,
            boolean currentlyInside,
            double inwardSpeed,
            double minimumImpactSpeed) {
        return isContactEntry(previouslyInside, currentlyInside)
                && inwardSpeed >= Math.max(0.0D, minimumImpactSpeed);
    }

    static boolean isCorrectionLike(
            Vec3 displacement, Vec3 previousVelocity, Vec3 currentVelocity) {
        double distance = displacement.length();
        if (distance > ABSOLUTE_CORRECTION_DISTANCE) {
            return true;
        }
        if (distance <= MAXIMUM_PLAUSIBLE_TICK_DISPLACEMENT) {
            return false;
        }
        double observedSpeed = Math.max(
                previousVelocity.length(), currentVelocity.length());
        return observedSpeed < distance * CORRECTION_MINIMUM_SPEED_RATIO;
    }

    static Vec3 selectImpactVelocity(
            Vec3 displacement,
            Vec3 previousVelocity,
            Vec3 currentVelocity,
            Vec3 surfaceNormal,
            double fallDistance) {
        Vec3 normal = safeNormal(surfaceNormal);
        Vec3 selected = strongestInwardVelocity(
                strongestInwardVelocity(previousVelocity, currentVelocity, normal),
                displacement,
                normal);
        if (normal.y < 0.75D) {
            return selected;
        }

        double fallSpeed = fallDistanceImpactSpeed(fallDistance);
        if (fallSpeed <= inwardSpeed(selected, normal)) {
            return selected;
        }
        double normalComponent = selected.dot(normal);
        return selected.subtract(normal.scale(normalComponent + fallSpeed));
    }

    static Vec3 strongestInwardVelocity(
            Vec3 first, Vec3 second, Vec3 surfaceNormal) {
        Vec3 normal = safeNormal(surfaceNormal);
        return first.dot(normal) <= second.dot(normal) ? first : second;
    }

    static double inwardSpeed(Vec3 velocity, Vec3 surfaceNormal) {
        return Math.max(0.0D, -velocity.dot(safeNormal(surfaceNormal)));
    }

    static double fallDistanceImpactSpeed(double fallDistance) {
        return Math.sqrt(Math.max(0.0D, fallDistance)
                * FALL_SPEED_SQUARED_PER_BLOCK);
    }

    private static Vec3 safeNormal(Vec3 normal) {
        return normal.lengthSqr() <= 1.0E-12D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : normal.normalize();
    }

    public record ContactFrame(
            SinkingMedium medium,
            Vec3 surfacePoint,
            Vec3 surfaceNormal,
            Vec3 surfaceAxisX,
            Vec3 surfaceAxisZ,
            double depth,
            double volumeFraction,
            boolean createSurfacePile) {
    }
}
