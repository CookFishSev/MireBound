package com.fish.mirebound.mud;

import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.eruption.MudEruptionProfile;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsProfile;
import com.fish.mirebound.mud.harvest.MudHarvestProfile;
import com.fish.mirebound.mud.flow.MudFlowProfile;
import com.fish.mirebound.network.payload.MudLocalProfilesPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Bounded client cache for server-owned per-block mud profiles. */
public final class MudLocalProfileCache {
    private static final int MAX_RETAINED_CHUNKS = 1024;
    private static final Map<PositionKey, Profile> PROFILES = new HashMap<>();
    private static final LinkedHashMap<ChunkKey, Set<PositionKey>> CHUNKS =
            new LinkedHashMap<>(64, 0.75F, true);

    private MudLocalProfileCache() {
    }

    public static synchronized void accept(MudLocalProfilesPayload payload) {
        ChunkKey chunk = new ChunkKey(payload.dimension(), ChunkPos.asLong(payload.chunkX(), payload.chunkZ()));
        if (payload.replaceChunk()) {
            removeChunk(chunk);
        }
        Set<PositionKey> positions = CHUNKS.computeIfAbsent(chunk, ignored -> new HashSet<>());
        Map<PaletteKey, Profile> decodedProfiles = new HashMap<>();
        float[][] paletteValues = new float[payload.palettes().size()][];
        for (MudLocalProfilesPayload.Entry entry : payload.entries()) {
            if (entry.mediumId() < 0 || entry.mediumId() >= SinkingMedium.COUNT
                    || entry.paletteIndex() < 0 || entry.paletteIndex() >= payload.palettes().size()) {
                continue;
            }
            BlockPos pos = BlockPos.of(entry.blockPos());
            if (pos.getX() >> 4 != payload.chunkX() || pos.getZ() >> 4 != payload.chunkZ()) {
                continue;
            }
            float[] packed = paletteValues[entry.paletteIndex()];
            if (packed == null) {
                packed = payload.palettes().get(entry.paletteIndex()).values();
                paletteValues[entry.paletteIndex()] = packed;
            }
            if (packed.length != MudPhysicsParameter.COUNT) {
                continue;
            }
            PaletteKey paletteKey = new PaletteKey(entry.mediumId(), entry.paletteIndex());
            Profile decoded = decodedProfiles.get(paletteKey);
            if (decoded == null) {
                double[] values = new double[packed.length];
                for (int index = 0; index < packed.length; index++) {
                    values[index] = packed[index];
                }
                decoded = new Profile(SinkingMedium.byId(entry.mediumId()), values);
                decodedProfiles.put(paletteKey, decoded);
            }
            PositionKey key = new PositionKey(payload.dimension(), entry.blockPos());
            PROFILES.put(key, decoded);
            positions.add(key);
        }
        trimChunks();
    }

    static synchronized Profile profile(Level level, BlockPos pos, SinkingMedium medium) {
        if (level == null || pos == null || !level.isClientSide()) {
            return null;
        }
        ResourceLocation dimension = level.dimension().location();
        Profile profile = PROFILES.get(new PositionKey(dimension, pos.asLong()));
        if (profile != null) {
            CHUNKS.get(new ChunkKey(dimension,
                    ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)));
        }
        return profile != null && profile.medium == medium ? profile : null;
    }

    public static synchronized boolean hasLocalProfile(
            Level level, BlockPos pos, SinkingMedium medium) {
        return profile(level, pos, medium) != null;
    }

    public static synchronized void reset() {
        PROFILES.clear();
        CHUNKS.clear();
    }

    static synchronized int profileCountForTests() {
        return PROFILES.size();
    }

    static synchronized int uniqueProfileCountForTests() {
        Set<Profile> profiles = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        profiles.addAll(PROFILES.values());
        return profiles.size();
    }

    static synchronized double valueForTests(ResourceLocation dimension, BlockPos pos,
            SinkingMedium medium, MudPhysicsParameter parameter) {
        Profile profile = PROFILES.get(new PositionKey(dimension, pos.asLong()));
        return profile == null || profile.medium != medium
                ? Double.NaN
                : profile.value(parameter);
    }

    private static void trimChunks() {
        while (CHUNKS.size() > MAX_RETAINED_CHUNKS) {
            ChunkKey oldest = CHUNKS.keySet().iterator().next();
            removeChunk(oldest);
        }
    }

    private static void removeChunk(ChunkKey chunk) {
        Set<PositionKey> removed = CHUNKS.remove(chunk);
        if (removed != null) {
            for (PositionKey key : removed) {
                PROFILES.remove(key);
            }
        }
    }

    static final class Profile {
        private final SinkingMedium medium;
        private final double[] values;
        private final SinkingPhysicsProfile ordinary;
        private final LivingSlimePhysicsProfile livingSlime;
        private final SculkMireProfile sculkMire;
        private final TenderFleshProfile tenderFlesh;
        private final AdhesionStrandProfile adhesionStrands;
        private final AssimilationProfile assimilation;
        private final MudEruptionProfile eruption;
        private final MudHarvestProfile harvest;
        private final DroppedItemPhysicsProfile droppedItems;
        private final MudFlowProfile flow;

        private Profile(SinkingMedium medium, double[] values) {
            this.medium = medium;
            this.values = values;
            this.ordinary = SinkingPhysicsProfile.fromValues(values);
            this.livingSlime = LivingSlimePhysicsProfile.fromValues(values);
            this.sculkMire = SculkMireProfile.fromValues(values);
            this.tenderFlesh = TenderFleshProfile.fromValues(values);
            this.adhesionStrands = AdhesionStrandProfile.fromValues(values);
            this.assimilation = AssimilationProfile.fromValues(values);
            this.eruption = MudEruptionProfile.fromValues(values);
            this.harvest = MudHarvestProfile.fromValues(values);
            this.droppedItems = DroppedItemPhysicsProfile.fromValues(values);
            this.flow = MudFlowProfile.fromValues(values);
        }

        double value(MudPhysicsParameter parameter) {
            return values[parameter.ordinal()];
        }

        SinkingPhysicsProfile ordinary() {
            return ordinary;
        }

        LivingSlimePhysicsProfile livingSlime() {
            return livingSlime;
        }

        SculkMireProfile sculkMire() {
            return sculkMire;
        }

        TenderFleshProfile tenderFlesh() {
            return tenderFlesh;
        }

        AdhesionStrandProfile adhesionStrands() {
            return adhesionStrands;
        }

        AssimilationProfile assimilation() {
            return assimilation;
        }

        MudEruptionProfile eruption() {
            return eruption;
        }

        MudHarvestProfile harvest() {
            return harvest;
        }

        DroppedItemPhysicsProfile droppedItems() {
            return droppedItems;
        }

        MudFlowProfile flow() {
            return flow;
        }
    }

    private record PositionKey(ResourceLocation dimension, long blockPos) {
    }

    private record ChunkKey(ResourceLocation dimension, long chunkPos) {
    }

    private record PaletteKey(int mediumId, int paletteIndex) {
    }
}
