package com.fish.mirebound.splash;

/** Immutable, validated-at-config-load settings used by splash hot paths. */
public record MudSplashProfile(
        boolean enabled,
        double minimumImpactSpeed,
        double maximumImpactSpeed,
        int baseDroplets,
        int maximumDropletsPerImpact,
        int maximumActiveDroplets,
        double launchSpeed,
        double gravity,
        double drag,
        int lifetimeTicks,
        int impactCooldownTicks,
        float stainRadius,
        float stainStrength,
        float playerHitRadius,
        float playerStainStrength,
        double renderDistance) {
    public static final MudSplashProfile DEFAULT = new MudSplashProfile(
            true,
            0.30D,
            4.50D,
            10,
            64,
            512,
            0.44D,
            0.040D,
            0.965D,
            100,
            8,
            0.13F,
            0.84F,
            0.12F,
            0.72F,
            48.0D);

    public MudSplashProfile {
        maximumImpactSpeed = Math.max(minimumImpactSpeed + 0.01D, maximumImpactSpeed);
    }
}
