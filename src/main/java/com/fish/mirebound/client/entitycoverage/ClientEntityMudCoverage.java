package com.fish.mirebound.client.entitycoverage;

import com.fish.mirebound.entitycoverage.EntityMudCoverageState;
import com.fish.mirebound.entitycoverage.EntityMudCoverageSpot;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.EntityMudCoveragePayload;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Client interpolation and lifecycle cache for non-player entity mud. */
public final class ClientEntityMudCoverage {
    private static final float INTERPOLATION = 0.24F;
    private static final float REMOVE_EPSILON = 0.001F;
    private static final int CLEANUP_INTERVAL_TICKS = 100;
    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static int cleanupTicks;

    private ClientEntityMudCoverage() {
    }

    public static void accept(EntityMudCoveragePayload payload) {
        float primary = strength(payload.primaryStrength());
        float secondary = strength(payload.secondaryStrength());
        float fadeScale = payload.automaticFadeScale() / 255.0F;
        List<SpotView> decodedSpots = spots(payload);
        Entry existing = ENTRIES.get(payload.entityId());
        if (existing != null && !existing.uuid.equals(payload.entityUuid())) {
            ENTRIES.remove(payload.entityId());
            existing = null;
        }
        if (existing != null && payload.revision() < existing.revision) {
            return;
        }
        if (existing == null) {
            if (!payload.fullSnapshot()) {
                return;
            }
            if (primary <= REMOVE_EPSILON && secondary <= REMOVE_EPSILON
                    && decodedSpots.isEmpty()) {
                return;
            }
            ENTRIES.put(payload.entityId(), new Entry(
                    payload.entityUuid(), payload.revision(), payload.patternSeed(),
                    medium(payload.primaryMediumId(), primary),
                    payload.primaryVisualSource(), primary, primary,
                    medium(payload.secondaryMediumId(), secondary),
                    payload.secondaryVisualSource(), secondary, secondary,
                    fadeScale, fadeScale, decodedSpots, 1));
            return;
        }

        List<SpotView> updatedSpots = applySpotUpdate(
                existing.spots, decodedSpots, payload.removedSpotIds(),
                payload.fullSnapshot());
        if (existing.patternSeed != payload.patternSeed()
                || !existing.spots.equals(updatedSpots)) {
            existing.textureRevision = nextRevision(existing.textureRevision);
        }
        existing.revision = payload.revision();
        existing.patternSeed = payload.patternSeed();
        if (primary > REMOVE_EPSILON) {
            existing.primaryMedium = medium(payload.primaryMediumId(), primary);
            existing.primaryVisualSource = payload.primaryVisualSource();
        }
        if (secondary > REMOVE_EPSILON) {
            existing.secondaryMedium = medium(payload.secondaryMediumId(), secondary);
            existing.secondaryVisualSource = payload.secondaryVisualSource();
        }
        existing.targetPrimary = primary;
        existing.targetSecondary = secondary;
        existing.targetFadeScale = fadeScale;
        existing.spots = updatedSpots;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            reset();
            return;
        }
        ENTRIES.values().removeIf(entry -> {
            entry.displayPrimary = approach(
                    entry.displayPrimary, entry.targetPrimary);
            entry.displaySecondary = approach(
                    entry.displaySecondary, entry.targetSecondary);
            entry.displayFadeScale = approach(
                    entry.displayFadeScale, entry.targetFadeScale);
            return entry.targetPrimary <= REMOVE_EPSILON
                    && entry.targetSecondary <= REMOVE_EPSILON
                    && entry.displayPrimary <= REMOVE_EPSILON
                    && entry.displaySecondary <= REMOVE_EPSILON
                    && entry.spots.isEmpty();
        });
        if (++cleanupTicks < CLEANUP_INTERVAL_TICKS) {
            return;
        }
        cleanupTicks = 0;
        ENTRIES.entrySet().removeIf(stored -> {
            var entity = minecraft.level.getEntity(stored.getKey());
            return !(entity instanceof LivingEntity living)
                    || living.isRemoved()
                    || !stored.getValue().uuid.equals(living.getUUID());
        });
    }

    public static View view(LivingEntity entity) {
        Entry entry = ENTRIES.get(entity.getId());
        if (entry == null || !entry.uuid.equals(entity.getUUID())) {
            return null;
        }
        return new View(
                entry.primaryMedium, entry.primaryVisualSource,
                entry.displayPrimary,
                entry.secondaryMedium, entry.secondaryVisualSource,
                entry.displaySecondary, entry.displayFadeScale,
                entry.patternSeed,
                entry.revision, entry.textureRevision, entry.spots);
    }

    public static void reset() {
        ENTRIES.clear();
        cleanupTicks = 0;
    }

    private static float strength(int encoded) {
        return Mth.clamp(encoded / 255.0F,
                0.0F, EntityMudCoverageState.MAXIMUM_COVERAGE);
    }

    private static SinkingMedium medium(int mediumId, float strength) {
        return strength <= REMOVE_EPSILON || mediumId >= SinkingMedium.COUNT
                ? null : SinkingMedium.byId(mediumId);
    }

    private static float approach(float current, float target) {
        float next = Mth.lerp(INTERPOLATION, current, target);
        return Math.abs(next - target) <= REMOVE_EPSILON ? target : next;
    }

    private static List<SpotView> spots(EntityMudCoveragePayload payload) {
        java.util.ArrayList<SpotView> decoded = new java.util.ArrayList<>(
                payload.spots().size());
        for (EntityMudCoveragePayload.Spot spot : payload.spots()) {
            if (spot.mediumId() >= SinkingMedium.COUNT || spot.strength() == 0) {
                continue;
            }
            decoded.add(new SpotView(
                    spot.id(),
                    EntityMudCoverageSpot.Shape.byId(spot.shapeId()),
                    spot.x() / 127.5F - 1.0F,
                    spot.y() / 255.0F,
                    spot.z() / 127.5F - 1.0F,
                    spot.radius() / 255.0F,
                    spot.strength() / 255.0F,
                    SinkingMedium.byId(spot.mediumId()),
                    spot.visualSource()));
        }
        return List.copyOf(decoded);
    }

    static List<SpotView> applySpotUpdate(
            List<SpotView> current, List<SpotView> changed,
            List<Integer> removedIds, boolean fullSnapshot) {
        if (fullSnapshot) {
            return List.copyOf(changed);
        }
        if (changed.isEmpty() && removedIds.isEmpty()) {
            return current;
        }
        Map<Integer, SpotView> merged = new LinkedHashMap<>();
        for (SpotView spot : current) {
            merged.put(spot.id(), spot);
        }
        for (int id : removedIds) {
            merged.remove(id);
        }
        for (SpotView spot : changed) {
            merged.put(spot.id(), spot);
        }
        return List.copyOf(merged.values());
    }

    private static int nextRevision(int current) {
        return current == Integer.MAX_VALUE ? 1 : current + 1;
    }

    public record View(
            SinkingMedium primaryMedium, long primaryVisualSource,
            float primaryStrength, SinkingMedium secondaryMedium,
            long secondaryVisualSource, float secondaryStrength,
            float automaticFadeScale,
            int patternSeed, int revision, int textureRevision,
            List<SpotView> spots) {
        public float totalCoverage() {
            float aggregate = Math.min(EntityMudCoverageState.MAXIMUM_COVERAGE,
                    primaryStrength + secondaryStrength);
            float visible = spots.isEmpty()
                    ? aggregate : Math.max(aggregate, 0.002F);
            return visible * automaticFadeScale;
        }

        public long visualSignature() {
            return textureRevision;
        }
    }

    public record SpotView(
            int id, EntityMudCoverageSpot.Shape shape,
            float localX, float localY, float localZ,
            float radius, float strength,
            SinkingMedium medium, long visualSource) {
        public long geometrySignature() {
            long value = shape.ordinal();
            value = value * 31L + Float.floatToIntBits(localX);
            value = value * 31L + Float.floatToIntBits(localY);
            value = value * 31L + Float.floatToIntBits(localZ);
            return value * 31L + Float.floatToIntBits(radius);
        }
    }

    private static final class Entry {
        private final UUID uuid;
        private int revision;
        private int patternSeed;
        private SinkingMedium primaryMedium;
        private long primaryVisualSource;
        private float targetPrimary;
        private float displayPrimary;
        private SinkingMedium secondaryMedium;
        private long secondaryVisualSource;
        private float targetSecondary;
        private float displaySecondary;
        private float targetFadeScale;
        private float displayFadeScale;
        private List<SpotView> spots;
        private int textureRevision;

        private Entry(UUID uuid, int revision, int patternSeed,
                SinkingMedium primaryMedium, long primaryVisualSource,
                float targetPrimary, float displayPrimary,
                SinkingMedium secondaryMedium, long secondaryVisualSource,
                float targetSecondary, float displaySecondary,
                float targetFadeScale, float displayFadeScale,
                List<SpotView> spots, int textureRevision) {
            this.uuid = uuid;
            this.revision = revision;
            this.patternSeed = patternSeed;
            this.primaryMedium = primaryMedium;
            this.primaryVisualSource = primaryVisualSource;
            this.targetPrimary = targetPrimary;
            this.displayPrimary = displayPrimary;
            this.secondaryMedium = secondaryMedium;
            this.secondaryVisualSource = secondaryVisualSource;
            this.targetSecondary = targetSecondary;
            this.displaySecondary = displaySecondary;
            this.targetFadeScale = targetFadeScale;
            this.displayFadeScale = displayFadeScale;
            this.spots = spots;
            this.textureRevision = textureRevision;
        }
    }
}
