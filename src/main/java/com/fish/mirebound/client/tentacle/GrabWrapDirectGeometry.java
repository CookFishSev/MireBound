package com.fish.mirebound.client.tentacle;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Turns the primary whole-body coil into the direct continuation of the
 * authoritative physical tip. No independent connector tube is created.
 */
final class GrabWrapDirectGeometry {
    private static final double EPSILON = 1.0E-10D;

    private GrabWrapDirectGeometry() {
    }

    static Strand attach(Vec3 physicalTip, List<Vec3> coil, List<Double> radii,
            Vec3 bodyCenter, Vec3 bodyAxis, double minimumAxial, double maximumAxial,
            double tipRadius) {
        if (coil.size() < 2 || coil.size() != radii.size()
                || bodyAxis.lengthSqr() <= EPSILON) {
            return new Strand(List.copyOf(coil), List.copyOf(radii));
        }

        Vec3 axis = bodyAxis.normalize();
        double low = Math.min(minimumAxial, maximumAxial);
        double high = Math.max(minimumAxial, maximumAxial);
        double entryAxial = Mth.clamp(
                physicalTip.subtract(bodyCenter).dot(axis), low, high);

        List<Vec3> attachedPoints = new ArrayList<>(coil);
        List<Double> attachedRadii = new ArrayList<>(radii);
        attachedPoints.set(0, physicalTip);

        // The next coil point is already on the body-side strand. Moving only its
        // axial coordinate to the nearest clamped body position completes the
        // connection in one full-radius segment without allowing the tip to
        // reshape the remaining stable helix.
        Vec3 originalEntry = coil.get(1);
        double originalAxial = originalEntry.subtract(bodyCenter).dot(axis);
        Vec3 radial = originalEntry.subtract(bodyCenter)
                .subtract(axis.scale(originalAxial));
        attachedPoints.set(1, bodyCenter.add(axis.scale(entryAxial)).add(radial));
        attachedRadii.set(0, Math.max(EPSILON, tipRadius));
        return new Strand(List.copyOf(attachedPoints), List.copyOf(attachedRadii));
    }

    record Strand(List<Vec3> points, List<Double> radii) {
    }
}
