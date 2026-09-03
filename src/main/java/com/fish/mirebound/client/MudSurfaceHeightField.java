package com.fish.mirebound.client;

/** Pure geometry helpers for the fixed 1/16-block mud surface height field. */
final class MudSurfaceHeightField {
    private MudSurfaceHeightField() {
    }

    static double rimWeight(double distancePixels, double widthPixels) {
        if (distancePixels < 0.75D || widthPixels <= 0.0D) {
            return 0.0D;
        }
        double t = 1.0D - (distancePixels - 1.0D) / Math.max(0.25D, widthPixels);
        t = Math.max(0.0D, Math.min(1.0D, t));
        // Preserve a readable middle ring instead of concentrating nearly all
        // displaced volume in the first texel beside the opening.
        return Math.sqrt(t * t * (3.0D - 2.0D * t));
    }

    static double normalizedPileHeight(double displacedVolumePixels,
            double totalWeight, double maximumWeight, double maximumHeightPixels) {
        if (displacedVolumePixels <= 0.0D || totalWeight <= 1.0E-8D
                || maximumWeight <= 1.0E-8D || maximumHeightPixels <= 0.0D) {
            return 0.0D;
        }
        return Math.min(
                displacedVolumePixels / totalWeight,
                maximumHeightPixels / maximumWeight);
    }

    static double impactExpansionPixels(
            double configuredPixels,
            double impactStrength,
            double volumeFraction) {
        double strength = clamp01(impactStrength);
        return Math.max(0.0D, configuredPixels)
                * strength
                * Math.sqrt(clamp01(volumeFraction));
    }

    static double impactRadiusPixels(
            double contactRadiusPixels,
            double configuredPixels,
            double impactStrength,
            double volumeFraction) {
        double expansion = impactExpansionPixels(
                configuredPixels, impactStrength, volumeFraction);
        if (expansion < 0.05D) {
            return 0.0D;
        }
        return Math.max(0.0D, contactRadiusPixels) + expansion;
    }

    static double impactDepression(
            double offsetXPixels,
            double offsetZPixels,
            double radiusPixels,
            double edgeJitterPixels,
            double centralStrength) {
        double distance = Math.sqrt(
                offsetXPixels * offsetXPixels + offsetZPixels * offsetZPixels)
                + edgeJitterPixels;
        if (distance > radiusPixels || radiusPixels <= 0.0D) {
            return 0.0D;
        }
        double edge = clamp01(radiusPixels - distance);
        double softenedEdge = edge * edge * (3.0D - 2.0D * edge);
        return clamp01(centralStrength) * (0.55D + softenedEdge * 0.45D);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

}
