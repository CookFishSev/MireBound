package com.fish.mirebound.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shared centerlines for the enclosure skin and pillar hit testing. */
final class TenderFleshEnclosureGeometry {
    private static final double PIXEL = 1.0D / 16.0D;

    private TenderFleshEnclosureGeometry() {
    }

    static Vec3 pillarPoint(Vec3 center, Vec3 radial, Vec3 normal,
            double outerRadius, double innerRadius, double height,
            double emergence, double closure, double breathContraction,
            double outwardBulge, double progress) {
        double eased = smooth(progress);
        double enclosureProgress = smooth(Mth.clamp(progress / 0.72D, 0.0D, 1.0D));
        double enclosureRadius = Mth.lerp(
                closure * enclosureProgress, outerRadius, innerRadius);
        double wave = progress >= 1.0D ? 0.0D : Math.sin(progress * Math.PI);
        enclosureRadius *= 1.0D - breathContraction * wave * wave;
        double topJoin = closure
                * smooth(Mth.clamp((progress - 0.70D) / 0.30D, 0.0D, 1.0D));
        double radius = Mth.lerp(topJoin, enclosureRadius, 0.0D) + wave * outwardBulge;
        double heightProgress = height * eased - height * (1.0D - emergence)
                - PIXEL * 0.45D;
        return center.add(radial.scale(radius)).add(normal.scale(heightProgress + 0.006D));
    }

    static Vec3 membranePoint(Vec3 from, Vec3 to, Vec3 center, Vec3 normal,
            double progress, double curve) {
        if (progress <= 0.0D) {
            return from;
        }
        if (progress >= 1.0D) {
            return to;
        }
        Vec3 point = from.lerp(to, progress);
        Vec3 radial = point.subtract(center);
        Vec3 planar = radial.subtract(normal.scale(radial.dot(normal)));
        // The bend shrinks with its span, including the shared roof endpoint.
        double bend = Math.min(Math.max(0.0D, curve), from.distanceTo(to) * 0.25D);
        double planarLength = planar.length();
        if (planarLength > 0.0D && bend > 0.0D) {
            point = point.add(planar.scale(Math.sin(progress * Math.PI) * bend / planarLength));
        }
        return point;
    }

    private static double smooth(double progress) {
        return progress * progress * (3.0D - 2.0D * progress);
    }
}
