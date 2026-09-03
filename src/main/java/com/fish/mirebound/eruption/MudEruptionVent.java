package com.fish.mirebound.eruption;

import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.network.payload.MudEruptionVentPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Mutable runtime state for one server-owned eruption vent. */
final class MudEruptionVent {
    final int id;
    final ResourceLocation dimension;
    final MudEruptionSurfaceSampler.Surface surface;
    MudEruptionProfile profile;
    final double radiusPixels;
    final int lifetimeTicks;
    final long seed;
    MudVisualPalette visualPalette;
    double continuousFlow;
    double continuousTargetFlow;
    int continuousCooldown;
    int continuousVariationCooldown;
    int nextFlowSoundTick;
    int burstCooldown;
    int surgeDropletsRemaining;
    int surgeTicksRemaining;
    double surgeHeight;
    int surgeSoundDroplets;
    boolean surgeSoundPending;
    int ageTicks;

    MudEruptionVent(int id, ResourceLocation dimension,
            MudEruptionSurfaceSampler.Surface surface,
            MudEruptionProfile profile, double radiusPixels,
            int lifetimeTicks, long seed, MudVisualPalette visualPalette, double continuousFlow,
            int continuousCooldown, int continuousVariationCooldown,
            int burstCooldown) {
        this.id = id;
        this.dimension = dimension;
        this.surface = surface;
        this.profile = profile;
        this.radiusPixels = radiusPixels;
        this.lifetimeTicks = lifetimeTicks;
        this.seed = seed;
        this.visualPalette = visualPalette;
        this.continuousFlow = continuousFlow;
        this.continuousTargetFlow = continuousFlow;
        this.continuousCooldown = continuousCooldown;
        this.continuousVariationCooldown = continuousVariationCooldown;
        this.burstCooldown = burstCooldown;
    }

    void startSurge(MudEruptionDynamics.Burst surge) {
        surgeDropletsRemaining = surge.droplets();
        surgeTicksRemaining = Math.min(
                profile.surges().durationTicks(), surgeDropletsRemaining);
        surgeHeight = surge.height();
        surgeSoundDroplets = surge.droplets();
        surgeSoundPending = true;
    }

    boolean surgeActive() {
        return surgeDropletsRemaining > 0 && surgeTicksRemaining > 0;
    }

    MudEruptionDynamics.Burst nextSurgeSlice() {
        if (!surgeActive()) {
            return null;
        }
        int droplets = Mth.ceil(surgeDropletsRemaining / (double) surgeTicksRemaining);
        surgeDropletsRemaining -= droplets;
        surgeTicksRemaining--;
        return new MudEruptionDynamics.Burst(surgeHeight, droplets);
    }

    MudEruptionDynamics.Burst surgeSoundBurst() {
        return new MudEruptionDynamics.Burst(surgeHeight, surgeSoundDroplets);
    }

    MudEruptionVentPayload payload(boolean active, int mergeEntityId) {
        Vec3 localOrigin = surface.localOrigin();
        return new MudEruptionVentPayload(
                dimension, id, active, surface.medium(),
                localOrigin.x, localOrigin.y, localOrigin.z,
                (float) radiusPixels, seed, mergeEntityId, surface.visualSource(),
                surface.subLevelId(), surface.pos().asLong(), surface.face());
    }

    Vec3 worldOrigin() {
        return surface.worldOrigin();
    }

    Vec3 worldNormal() {
        return surface.worldNormal();
    }
}
