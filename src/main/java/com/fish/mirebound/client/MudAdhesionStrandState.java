package com.fish.mirebound.client;

import com.fish.mirebound.mud.AdhesionStrandProfile;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Retained state and fixed-cost integration for one mud-to-body adhesion bridge. */
class MudAdhesionStrandState {
    private static final int NODE_COUNT = MudSurfaceEffectManager.ADHESION_BRIDGE_NODE_COUNT;

    final int index;
    boolean active;
    boolean breaking;
    boolean surfaceAnchored;
    boolean geometricAnchor;
    Vec3 previousSurfacePoint = Vec3.ZERO;
    Vec3 surfacePoint = Vec3.ZERO;
    Vec3 previousBodyPoint = Vec3.ZERO;
    Vec3 bodyPoint = Vec3.ZERO;
    final Vec3[] previousBridgeNodes = new Vec3[NODE_COUNT];
    final Vec3[] bridgeNodes = new Vec3[NODE_COUNT];
    final Vec3[] bridgeVelocities = new Vec3[NODE_COUNT];
    boolean bridgeInitialized;
    double previousAttachProgress;
    double attachProgress;
    double previousBreakProgress;
    double breakProgress;
    MudBodyPart part;
    MudSurface surface;
    SinkingMedium medium;
    long visualSource;
    EquipmentSlot armorSlot;
    int row = -1;
    int column = -1;
    double pixelOffsetU;
    double pixelOffsetV;
    double geometryAngle;
    double bodySlideOffset;
    double bodySurfaceOffset;
    int bodyAnchorMissTicks;
    int surfaceAnchorMissTicks;
    int breakCandidateTicks;
    long geometrySurfaceTick = Long.MIN_VALUE;
    long seed;

    MudAdhesionStrandState(int index) {
        this.index = index;
        clearBridge();
    }

    void beginTick() {
        previousSurfacePoint = surfacePoint;
        previousBodyPoint = bodyPoint;
        previousAttachProgress = attachProgress;
        previousBreakProgress = breakProgress;
        if (bridgeInitialized) {
            System.arraycopy(bridgeNodes, 0, previousBridgeNodes, 0, NODE_COUNT);
        }
    }

    void activate(long ownerSeed, int slot) {
        seed = mix(ownerSeed ^ slot * 0x9e3779b97f4a7c15L);
        pixelOffsetU = (((mix(seed ^ 0x36d1f2abL) >>> 11) & 1023L) / 1023.0D - 0.5D) * 0.70D;
        pixelOffsetV = (((mix(seed ^ 0x7a51c3e9L) >>> 11) & 1023L) / 1023.0D - 0.5D) * 0.70D;
        active = true;
        breaking = false;
        surfaceAnchored = false;
        geometricAnchor = false;
        attachProgress = 0.0D;
        previousAttachProgress = 0.0D;
        breakProgress = 0.0D;
        previousBreakProgress = 0.0D;
        surfacePoint = Vec3.ZERO;
        previousSurfacePoint = Vec3.ZERO;
        bodyPoint = Vec3.ZERO;
        previousBodyPoint = Vec3.ZERO;
        geometryAngle = 0.0D;
        bodySlideOffset = 0.0D;
        bodySurfaceOffset = 0.0D;
        bodyAnchorMissTicks = 0;
        surfaceAnchorMissTicks = 0;
        breakCandidateTicks = 0;
        geometrySurfaceTick = Long.MIN_VALUE;
        visualSource = 0L;
        bridgeInitialized = false;
        clearBridge();
    }

    void reset() {
        active = false;
        breaking = false;
        surfaceAnchored = false;
        geometricAnchor = false;
        attachProgress = 0.0D;
        previousAttachProgress = 0.0D;
        breakProgress = 0.0D;
        previousBreakProgress = 0.0D;
        part = null;
        surface = null;
        medium = null;
        visualSource = 0L;
        armorSlot = null;
        row = -1;
        column = -1;
        pixelOffsetU = 0.0D;
        pixelOffsetV = 0.0D;
        geometryAngle = 0.0D;
        bodySlideOffset = 0.0D;
        bodySurfaceOffset = 0.0D;
        bodyAnchorMissTicks = 0;
        surfaceAnchorMissTicks = 0;
        breakCandidateTicks = 0;
        geometrySurfaceTick = Long.MIN_VALUE;
        bridgeInitialized = false;
    }

    void advanceAttachment(AdhesionStrandProfile profile) {
        attachProgress = Math.min(1.0D,
                attachProgress + 1.0D / Math.max(1, profile.attachGrowTicks()));
    }

    void beginBreaking() {
        breaking = true;
        breakCandidateTicks = 0;
    }

    void initializeBridge(AdhesionStrandProfile profile) {
        if (previousSurfacePoint == Vec3.ZERO && previousBodyPoint == Vec3.ZERO) {
            previousSurfacePoint = surfacePoint;
            previousBodyPoint = bodyPoint;
        }
        Vec3 lateral = bridgeLateralAxis();
        double length = surfacePoint.distanceTo(bodyPoint);
        double variation = signedVariation(seed ^ 0x4e6f64654c61674cL);
        for (int node = 0; node < NODE_COUNT; node++) {
            double t = (double) node / (NODE_COUNT - 1);
            double middle = Math.sin(Math.PI * t);
            Vec3 point = lerp(surfacePoint, bodyPoint, t)
                    .add(0.0D, -profile.curve() * Math.min(length, 1.5D)
                            * 0.24D * middle, 0.0D)
                    .add(lateral.scale(variation * profile.curve() * 0.07D * middle));
            bridgeNodes[node] = point;
            previousBridgeNodes[node] = point;
            bridgeVelocities[node] = Vec3.ZERO;
        }
        bridgeInitialized = true;
    }

    void updateBridge(AdhesionStrandProfile profile) {
        if (!bridgeInitialized) {
            initializeBridge(profile);
            return;
        }
        Vec3 bodyMotion = clampVector(bodyPoint.subtract(previousBodyPoint), 0.45D);
        Vec3 lateral = bridgeLateralAxis();
        double length = surfacePoint.distanceTo(bodyPoint);
        double variation = signedVariation(seed ^ 0x4e6f64654c61674cL);
        bridgeNodes[0] = surfacePoint;
        bridgeNodes[NODE_COUNT - 1] = bodyPoint;
        for (int node = 1; node < NODE_COUNT - 1; node++) {
            double t = (double) node / (NODE_COUNT - 1);
            double middle = Math.sin(Math.PI * t);
            Vec3 target = lerp(surfacePoint, bodyPoint, t)
                    .add(0.0D, -profile.curve() * Math.min(length, 1.6D)
                            * 0.30D * middle, 0.0D)
                    .add(lateral.scale(variation * profile.curve() * 0.08D * middle))
                    .subtract(bodyMotion.scale(profile.inertia() * middle * 0.72D));
            Vec3 velocity = bridgeVelocities[node]
                    .scale(profile.inertia())
                    .add(target.subtract(bridgeNodes[node]).scale(profile.response()));
            velocity = clampVector(velocity, 0.28D);
            bridgeVelocities[node] = velocity;
            bridgeNodes[node] = bridgeNodes[node].add(velocity);
        }
        double smoothing = Mth.clamp(profile.response() * 0.24D, 0.02D, 0.22D);
        for (int node = 1; node < NODE_COUNT - 1; node++) {
            Vec3 neighborCenter = bridgeNodes[node - 1].add(bridgeNodes[node + 1]).scale(0.5D);
            bridgeNodes[node] = lerp(bridgeNodes[node], neighborCenter, smoothing);
        }
        bridgeNodes[0] = surfacePoint;
        bridgeNodes[NODE_COUNT - 1] = bodyPoint;
    }

    boolean hasAttachment() {
        return part != null && surface != null && medium != null && row >= 0 && column >= 0;
    }

    boolean matchesMedium(Player player, SinkingMedium expected, AdhesionStrandProfile profile) {
        if (!profile.enabled() || medium != expected || !hasAttachment()) {
            return false;
        }
        ClientAdhesionCoverage.Sample source = ClientAdhesionCoverage.sample(
                player, part, surface, row, column);
        return source.medium() == expected && source.armorSlot() == armorSlot
                && source.visualSource() == visualSource
                && source.coverage() >= profile.minimumCoverage();
    }

    private void clearBridge() {
        for (int node = 0; node < NODE_COUNT; node++) {
            previousBridgeNodes[node] = Vec3.ZERO;
            bridgeNodes[node] = Vec3.ZERO;
            bridgeVelocities[node] = Vec3.ZERO;
        }
    }

    private Vec3 bridgeLateralAxis() {
        Vec3 tangent = bodyPoint.subtract(surfacePoint);
        if (tangent.lengthSqr() <= 1.0E-8D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 lateral = tangent.normalize().cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (lateral.lengthSqr() <= 1.0E-8D) {
            lateral = tangent.normalize().cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        return lateral.normalize();
    }

    private static Vec3 clampVector(Vec3 vector, double maximum) {
        double lengthSquared = vector.lengthSqr();
        return lengthSquared > maximum * maximum
                ? vector.scale(maximum / Math.sqrt(lengthSquared)) : vector;
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double amount) {
        return from.add(to.subtract(from).scale(amount));
    }

    private static double signedVariation(long value) {
        return ((mix(value) >>> 11) & 2047L) / 1023.5D - 1.0D;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
