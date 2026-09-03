package com.fish.mirebound.entitycoverage;

import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

/** Compact persistent mud coverage for non-player living entities. */
public final class EntityMudCoverageState {
    public static final float MAXIMUM_COVERAGE = 1.0F;
    public static final int MAXIMUM_SPOTS = 96;
    private static final int FORMAT_VERSION = 2;
    private static final float EPSILON = 0.0005F;
    private static final float MINIMUM_SPOT_STRENGTH = 0.025F;
    private static final int CONTACT_THROTTLE_SLOTS = 16;

    private final Slot primary = new Slot();
    private final Slot secondary = new Slot();
    private final List<EntityMudCoverageSpot> spots = new ArrayList<>(MAXIMUM_SPOTS);
    private final int[] contactMediumIds = new int[CONTACT_THROTTLE_SLOTS];
    private final long[] contactVisualSources = new long[CONTACT_THROTTLE_SLOTS];
    private final long[] contactSpatialKeys = new long[CONTACT_THROTTLE_SLOTS];
    private final long[] contactUpdateTicks = new long[CONTACT_THROTTLE_SLOTS];
    private final EntityMudCoverageSyncTracker syncTracker =
            new EntityMudCoverageSyncTracker();
    private final int patternSeed;
    private int nextSpotId = 1;
    private int revision;
    private boolean synchronizationPending;
    private boolean persistencePending;
    private boolean legacyContactVolumesPresent;
    private long automaticFadeLastTick = Long.MIN_VALUE;
    private float automaticFadeProgress;

    public EntityMudCoverageState(int patternSeed) {
        this.patternSeed = patternSeed;
        Arrays.fill(contactMediumIds, -1);
        Arrays.fill(contactUpdateTicks, Long.MIN_VALUE);
    }

    public boolean add(SinkingMedium medium, long visualSource, float amount,
            boolean forceReplacement) {
        if (medium == null || !Float.isFinite(amount) || amount <= EPSILON) {
            return false;
        }
        Slot target = matching(primary, medium, visualSource)
                ? primary
                : matching(secondary, medium, visualSource) ? secondary : null;
        float accepted;
        if (target == null) {
            if (primary.empty()) {
                target = primary;
            } else if (secondary.empty()) {
                target = secondary;
            } else {
                Slot weaker = primary.strength <= secondary.strength ? primary : secondary;
                if (!forceReplacement && weaker.strength > Math.max(0.08F, amount * 3.0F)) {
                    return false;
                }
                float retainedStrength = weaker.strength;
                weaker.clear();
                target = weaker;
                amount = Math.max(amount, retainedStrength * 0.70F);
            }
            accepted = Math.min(amount, MAXIMUM_COVERAGE - totalCoverage());
            if (accepted <= EPSILON && forceReplacement) {
                Slot donor = target == primary ? secondary : primary;
                accepted = Math.min(amount, donor.strength);
                donor.strength -= accepted;
                if (donor.strength <= EPSILON) {
                    donor.clear();
                }
            }
            if (accepted <= EPSILON) {
                return false;
            }
            target.medium = medium;
            target.visualSource = visualSource;
        } else {
            accepted = Math.min(amount, MAXIMUM_COVERAGE - totalCoverage());
            if (accepted <= EPSILON) {
                return false;
            }
        }
        target.strength = Math.min(MAXIMUM_COVERAGE,
                target.strength + accepted);
        revision = nextRevision(revision);
        return true;
    }

    public boolean wash(float amount) {
        float total = Math.max(totalCoverage(), maximumSpotStrength());
        if (!Float.isFinite(amount) || amount <= EPSILON || total <= EPSILON) {
            return false;
        }
        float retained = Math.max(0.0F, total - amount) / total;
        return scaleCoverage(retained);
    }

    public boolean washVisible(float amount) {
        float visibleScale = automaticFadeScale();
        if (visibleScale <= EPSILON) {
            return scaleCoverage(0.0F);
        }
        return wash(amount / visibleScale);
    }

    public boolean refreshAutomaticFade(long gameTime) {
        boolean changed = false;
        if (automaticFadeProgress > EPSILON && dirty()) {
            changed = scaleCoverage(1.0F - automaticFadeProgress);
        }
        automaticFadeLastTick = gameTime;
        automaticFadeProgress = 0.0F;
        return changed;
    }

    public boolean advanceAutomaticFade(long gameTime, int durationTicks) {
        if (!dirty()) {
            automaticFadeLastTick = Long.MIN_VALUE;
            automaticFadeProgress = 0.0F;
            return false;
        }
        if (durationTicks <= 0) {
            return refreshAutomaticFade(gameTime);
        }
        if (automaticFadeLastTick == Long.MIN_VALUE
                || gameTime <= automaticFadeLastTick) {
            automaticFadeLastTick = gameTime;
            return false;
        }

        long elapsedTicks = gameTime - automaticFadeLastTick;
        automaticFadeLastTick = gameTime;
        automaticFadeProgress = Math.min(1.0F,
                automaticFadeProgress + elapsedTicks / (float) durationTicks);
        if (automaticFadeProgress >= 1.0F - EPSILON) {
            return scaleCoverage(0.0F);
        }
        revision = nextRevision(revision);
        return true;
    }

    private float automaticFadeScale() {
        return Mth.clamp(1.0F - automaticFadeProgress, 0.0F, 1.0F);
    }

    private boolean scaleCoverage(float retained) {
        retained = Mth.clamp(retained, 0.0F, 1.0F);
        if (retained >= 1.0F || !dirty()) {
            return false;
        }
        primary.strength *= retained;
        secondary.strength *= retained;
        for (int index = spots.size() - 1; index >= 0; index--) {
            EntityMudCoverageSpot spot = spots.get(index);
            float strength = spot.strength() * retained;
            if (strength <= MINIMUM_SPOT_STRENGTH) {
                spots.remove(index);
            } else {
                spots.set(index, new EntityMudCoverageSpot(
                        spot.id(),
                        spot.shape(),
                        spot.localX(), spot.localY(), spot.localZ(),
                        spot.radius(), strength,
                        spot.medium(), spot.visualSource()));
            }
        }
        if (primary.strength <= EPSILON) {
            primary.clear();
        }
        if (secondary.strength <= EPSILON) {
            secondary.clear();
        }
        if (!dirty()) {
            automaticFadeLastTick = Long.MIN_VALUE;
        }
        revision = nextRevision(revision);
        return true;
    }

    public boolean addSpot(float localX, float localY, float localZ,
            float radius, float strength, SinkingMedium medium,
            long visualSource, boolean forceReplacement) {
        if (medium == null || !Float.isFinite(strength)
                || strength <= MINIMUM_SPOT_STRENGTH) {
            return false;
        }
        EntityMudCoverageSpot incoming = new EntityMudCoverageSpot(
                nextSpotId,
                EntityMudCoverageSpot.Shape.RADIAL,
                localX, localY, localZ, radius, strength,
                medium, visualSource);
        return addSpot(incoming, forceReplacement);
    }

    public boolean addVerticalVolume(float boundary, boolean lowerVolume,
            float strength, SinkingMedium medium, long visualSource) {
        if (medium == null || !Float.isFinite(strength)
                || strength <= MINIMUM_SPOT_STRENGTH) {
            return false;
        }
        EntityMudCoverageSpot incoming = new EntityMudCoverageSpot(
                nextSpotId,
                lowerVolume
                        ? EntityMudCoverageSpot.Shape.LOWER_VOLUME
                        : EntityMudCoverageSpot.Shape.UPPER_VOLUME,
                0.0F, Mth.clamp(boundary, 0.0F, 1.0F), 0.0F,
                1.0F, strength, medium, visualSource);
        boolean added = addSpot(incoming, false);
        legacyContactVolumesPresent |= added;
        return added;
    }

    public boolean addContactVolume(
            float localX, float boundary, float localZ, float radius,
            boolean lowerVolume, float strength, SinkingMedium medium,
            long visualSource) {
        if (medium == null || !Float.isFinite(strength)
                || strength <= MINIMUM_SPOT_STRENGTH) {
            return false;
        }
        boolean removedLegacyVolumes = legacyContactVolumesPresent
                && spots.removeIf(spot ->
                        spot.shape() == EntityMudCoverageSpot.Shape.LOWER_VOLUME
                                || spot.shape()
                                        == EntityMudCoverageSpot.Shape.UPPER_VOLUME);
        legacyContactVolumesPresent = false;
        EntityMudCoverageSpot incoming = new EntityMudCoverageSpot(
                nextSpotId,
                lowerVolume
                        ? EntityMudCoverageSpot.Shape.LOWER_CONTACT_VOLUME
                        : EntityMudCoverageSpot.Shape.UPPER_CONTACT_VOLUME,
                localX, Mth.clamp(boundary, 0.0F, 1.0F), localZ,
                radius, strength, medium, visualSource);
        boolean added = addSpot(incoming, false);
        if (removedLegacyVolumes && !added) {
            revision = nextRevision(revision);
        }
        return removedLegacyVolumes || added;
    }

    private boolean addSpot(
            EntityMudCoverageSpot incoming, boolean forceReplacement) {
        int mergeIndex = nearestMatchingSpot(incoming);
        if (mergeIndex >= 0) {
            EntityMudCoverageSpot current = spots.get(mergeIndex);
            EntityMudCoverageSpot merged = merge(current, incoming);
            if (merged.equals(current)) {
                return false;
            }
            spots.set(mergeIndex, merged);
        } else if (spots.size() < MAXIMUM_SPOTS) {
            spots.add(incoming);
            nextSpotId = nextSpotId(nextSpotId);
        } else {
            int overlapIndex = forceReplacement
                    ? nearestOverlappingSpot(incoming) : -1;
            if (overlapIndex < 0) {
                return false;
            }
            EntityMudCoverageSpot current = spots.get(overlapIndex);
            EntityMudCoverageSpot replacement = replaceSource(current, incoming);
            if (replacement.equals(current)) {
                return false;
            }
            spots.set(overlapIndex, replacement);
        }
        revision = nextRevision(revision);
        return true;
    }

    public boolean contactUpdateDue(
            SinkingMedium medium, long visualSource,
            long gameTime, int intervalTicks) {
        return contactUpdateDue(
                medium, visualSource, 0L, gameTime, intervalTicks);
    }

    public boolean contactUpdateDue(
            SinkingMedium medium, long visualSource, long spatialKey,
            long gameTime, int intervalTicks) {
        int selected = 0;
        long oldestTick = Long.MAX_VALUE;
        for (int index = 0; index < CONTACT_THROTTLE_SLOTS; index++) {
            if (contactMediumIds[index] == medium.id()
                    && contactVisualSources[index] == visualSource
                    && contactSpatialKeys[index] == spatialKey) {
                long previous = contactUpdateTicks[index];
                if (previous <= gameTime
                        && gameTime - previous < intervalTicks) {
                    return false;
                }
                contactUpdateTicks[index] = gameTime;
                return true;
            }
            if (contactMediumIds[index] < 0) {
                selected = index;
                oldestTick = Long.MIN_VALUE;
                break;
            }
            if (contactUpdateTicks[index] < oldestTick) {
                oldestTick = contactUpdateTicks[index];
                selected = index;
            }
        }
        contactMediumIds[selected] = medium.id();
        contactVisualSources[selected] = visualSource;
        contactSpatialKeys[selected] = spatialKey;
        contactUpdateTicks[selected] = gameTime;
        return true;
    }

    public float totalCoverage() {
        return Math.min(MAXIMUM_COVERAGE, primary.strength + secondary.strength);
    }

    public boolean dirty() {
        return totalCoverage() > EPSILON || !spots.isEmpty();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                primary.medium, primary.visualSource, primary.strength,
                secondary.medium, secondary.visualSource, secondary.strength,
                1.0F - automaticFadeProgress,
                patternSeed, revision, List.copyOf(spots));
    }

    public long synchronizationSignature() {
        long value = coverageVisualSignature();
        value = value * 37L + spots.size();
        value = value * 31L + EntityMudCoverageEncoding.unit(
                1.0F - automaticFadeProgress);
        for (EntityMudCoverageSpot spot : spots) {
            value = value * 31L
                    + EntityMudCoverageEncoding.spotSignature(spot);
        }
        return value;
    }

    private long coverageVisualSignature() {
        long value = EntityMudCoverageEncoding.unit(primary.strength);
        value = value * 37L
                + EntityMudCoverageEncoding.mediumId(primary.medium);
        value = value * 31L + primary.visualSource;
        value = value * 37L
                + EntityMudCoverageEncoding.unit(secondary.strength);
        value = value * 37L
                + EntityMudCoverageEncoding.mediumId(secondary.medium);
        value = value * 31L + secondary.visualSource;
        return value;
    }

    public void markSynchronizationPending() {
        synchronizationPending = true;
    }

    public boolean synchronizationPending() {
        return synchronizationPending;
    }

    public void clearSynchronizationPending() {
        synchronizationPending = false;
    }

    public void markPersistencePending() {
        persistencePending = true;
    }

    public boolean persistencePending() {
        return persistencePending;
    }

    public void clearPersistencePending() {
        persistencePending = false;
    }

    long lastBroadcastSignature() {
        return syncTracker.lastSignature();
    }

    EntityMudCoverageSyncTracker.Delta synchronizationDelta() {
        return syncTracker.delta(spots);
    }

    void markBroadcast(long signature) {
        syncTracker.mark(signature, spots);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Format", FORMAT_VERSION);
        tag.putInt("Seed", patternSeed);
        tag.putInt("Revision", revision);
        tag.putInt("NextSpotId", nextSpotId);
        saveSlot(tag, "Primary", primary);
        saveSlot(tag, "Secondary", secondary);
        if (!spots.isEmpty()) {
            ListTag savedSpots = new ListTag();
            for (EntityMudCoverageSpot spot : spots) {
                CompoundTag saved = new CompoundTag();
                saved.putInt("Id", spot.id());
                saved.putInt("Shape", spot.shape().ordinal());
                saved.putFloat("X", spot.localX());
                saved.putFloat("Y", spot.localY());
                saved.putFloat("Z", spot.localZ());
                saved.putFloat("Radius", spot.radius());
                saved.putFloat("Strength", spot.strength());
                saved.putInt("Medium", spot.medium().id());
                saved.putLong("VisualSource", spot.visualSource());
                savedSpots.add(saved);
            }
            tag.put("Spots", savedSpots);
        }
        return tag;
    }

    public static EntityMudCoverageState load(CompoundTag tag, int fallbackSeed) {
        int seed = tag.contains("Seed") ? tag.getInt("Seed") : fallbackSeed;
        EntityMudCoverageState state = new EntityMudCoverageState(seed);
        if (tag.getInt("Format") < FORMAT_VERSION) {
            state.markPersistencePending();
            return state;
        }
        state.revision = Math.max(0, tag.getInt("Revision"));
        state.nextSpotId = Math.max(1, tag.getInt("NextSpotId"));
        loadSlot(tag, "Primary", state.primary);
        loadSlot(tag, "Secondary", state.secondary);
        ListTag savedSpots = tag.getList("Spots", Tag.TAG_COMPOUND);
        Set<Integer> loadedIds = new HashSet<>();
        for (int index = 0;
                index < savedSpots.size() && state.spots.size() < MAXIMUM_SPOTS;
                index++) {
            CompoundTag saved = savedSpots.getCompound(index);
            float strength = Mth.clamp(saved.getFloat("Strength"), 0.0F, 1.0F);
            if (strength <= MINIMUM_SPOT_STRENGTH) {
                continue;
            }
            int id = saved.contains("Id", Tag.TAG_INT)
                    ? Math.max(1, saved.getInt("Id"))
                    : state.claimNextSpotId(loadedIds);
            if (!loadedIds.add(id)) {
                id = state.claimNextSpotId(loadedIds);
                loadedIds.add(id);
            }
            EntityMudCoverageSpot loaded = new EntityMudCoverageSpot(
                    id,
                    EntityMudCoverageSpot.Shape.byId(saved.getInt("Shape")),
                    saved.getFloat("X"), saved.getFloat("Y"),
                    saved.getFloat("Z"), saved.getFloat("Radius"),
                    strength, SinkingMedium.byId(saved.getInt("Medium")),
                    saved.getLong("VisualSource"));
            state.spots.add(loaded);
            state.legacyContactVolumesPresent |=
                    loaded.shape() == EntityMudCoverageSpot.Shape.LOWER_VOLUME
                            || loaded.shape()
                                    == EntityMudCoverageSpot.Shape.UPPER_VOLUME;
            state.advancePast(id);
        }
        float total = state.primary.strength + state.secondary.strength;
        if (total > MAXIMUM_COVERAGE) {
            float scale = MAXIMUM_COVERAGE / total;
            state.primary.strength *= scale;
            state.secondary.strength *= scale;
        }
        return state;
    }

    private float maximumSpotStrength() {
        float maximum = 0.0F;
        for (EntityMudCoverageSpot spot : spots) {
            maximum = Math.max(maximum, spot.strength());
        }
        return maximum;
    }

    private int nearestMatchingSpot(EntityMudCoverageSpot incoming) {
        int selected = -1;
        float nearest = Float.POSITIVE_INFINITY;
        for (int index = 0; index < spots.size(); index++) {
            EntityMudCoverageSpot spot = spots.get(index);
            if (spot.shape() != incoming.shape()
                    || !spot.sameSource(
                            incoming.medium(), incoming.visualSource())) {
                continue;
            }
            if (!spot.shape().localized()) {
                return index;
            }
            float distance = spot.shape() == EntityMudCoverageSpot.Shape.RADIAL
                    ? spot.distanceSquared(
                            incoming.localX(), incoming.localY(), incoming.localZ())
                    : spot.horizontalDistanceSquared(
                            incoming.localX(), incoming.localZ());
            float reach = spot.shape() == EntityMudCoverageSpot.Shape.RADIAL
                    ? Math.max(0.012F,
                            Math.min(spot.radius(), incoming.radius()) * 0.18F)
                    : Math.max(0.08F,
                            Math.min(spot.radius(), incoming.radius()) * 0.58F);
            if (distance <= reach * reach && distance < nearest) {
                nearest = distance;
                selected = index;
            }
        }
        return selected;
    }

    private int nearestOverlappingSpot(EntityMudCoverageSpot incoming) {
        int selected = -1;
        float nearest = Float.POSITIVE_INFINITY;
        for (int index = 0; index < spots.size(); index++) {
            EntityMudCoverageSpot spot = spots.get(index);
            if (spot.shape() != incoming.shape()) {
                continue;
            }
            float distance = spot.distanceSquared(
                    incoming.localX(), incoming.localY(), incoming.localZ());
            float reach = (spot.radius() + incoming.radius()) * 0.72F;
            if (distance <= reach * reach && distance < nearest) {
                nearest = distance;
                selected = index;
            }
        }
        return selected;
    }

    private static EntityMudCoverageSpot merge(
            EntityMudCoverageSpot current, EntityMudCoverageSpot incoming) {
        float strength = 1.0F
                - (1.0F - current.strength())
                * (1.0F - incoming.strength() * 0.58F);
        float localY = switch (current.shape()) {
            case LOWER_VOLUME, LOWER_CONTACT_VOLUME ->
                    Math.max(current.localY(), incoming.localY());
            case UPPER_VOLUME, UPPER_CONTACT_VOLUME ->
                    Math.min(current.localY(), incoming.localY());
            case RADIAL -> current.localY();
        };
        return new EntityMudCoverageSpot(
                current.id(), current.shape(),
                current.localX(), localY, current.localZ(),
                current.shape() != EntityMudCoverageSpot.Shape.RADIAL
                        && current.shape().localized()
                        ? Math.max(current.radius(), incoming.radius())
                        : current.radius(),
                strength,
                current.medium(), current.visualSource());
    }

    private static EntityMudCoverageSpot replaceSource(
            EntityMudCoverageSpot current, EntityMudCoverageSpot incoming) {
        return new EntityMudCoverageSpot(
                current.id(), current.shape(),
                current.localX(), current.localY(), current.localZ(),
                current.radius(), Math.max(current.strength(), incoming.strength()),
                incoming.medium(), incoming.visualSource());
    }

    private int claimNextSpotId(Set<Integer> usedIds) {
        int candidate = nextSpotId;
        while (usedIds.contains(candidate)) {
            candidate = nextSpotId(candidate);
        }
        nextSpotId = nextSpotId(candidate);
        return candidate;
    }

    private void advancePast(int id) {
        if (id >= nextSpotId) {
            nextSpotId = nextSpotId(id);
        }
    }

    private static void saveSlot(CompoundTag root, String name, Slot slot) {
        if (slot.empty()) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("Medium", slot.medium.id());
        tag.putLong("VisualSource", slot.visualSource);
        tag.putFloat("Strength", slot.strength);
        root.put(name, tag);
    }

    private static void loadSlot(CompoundTag root, String name, Slot slot) {
        if (!root.contains(name, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag tag = root.getCompound(name);
        float strength = Mth.clamp(tag.getFloat("Strength"),
                0.0F, MAXIMUM_COVERAGE);
        if (strength <= EPSILON) {
            return;
        }
        slot.medium = SinkingMedium.byId(tag.getInt("Medium"));
        slot.visualSource = tag.getLong("VisualSource");
        slot.strength = strength;
    }

    private static boolean matching(Slot slot, SinkingMedium medium,
            long visualSource) {
        return slot.medium == medium && slot.visualSource == visualSource;
    }

    private static int nextRevision(int current) {
        return current == Integer.MAX_VALUE ? 1 : current + 1;
    }

    private static int nextSpotId(int current) {
        return current == Integer.MAX_VALUE ? 1 : current + 1;
    }

    private static final class Slot {
        private SinkingMedium medium;
        private long visualSource = MudVisualSource.NONE;
        private float strength;

        private boolean empty() {
            return medium == null || strength <= EPSILON;
        }

        private void clear() {
            medium = null;
            visualSource = MudVisualSource.NONE;
            strength = 0.0F;
        }
    }

    public record Snapshot(
            SinkingMedium primaryMedium, long primaryVisualSource,
            float primaryStrength, SinkingMedium secondaryMedium,
            long secondaryVisualSource, float secondaryStrength,
            float automaticFadeScale,
            int patternSeed, int revision,
            List<EntityMudCoverageSpot> spots) {
    }
}
