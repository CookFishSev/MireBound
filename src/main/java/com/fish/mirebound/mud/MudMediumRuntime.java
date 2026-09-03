package com.fish.mirebound.mud;

import com.fish.mirebound.adaptive.AdaptiveMudBehaviorSettings;
import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.eruption.MudEruptionProfile;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsProfile;
import com.fish.mirebound.mud.harvest.MudHarvestProfile;
import com.fish.mirebound.mud.flow.MudFlowProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Side-aware access to the server-synchronized medium profile. */
public final class MudMediumRuntime {
    private MudMediumRuntime() {
    }

    public static double value(Level level, SinkingMedium medium, MudPhysicsParameter parameter) {
        return level != null && level.isClientSide()
                ? MudPhysicsProfiles.clientValue(medium, parameter)
                : MudPhysicsSettings.value(medium, parameter);
    }

    public static double value(Level level, BlockPos pos, SinkingMedium medium,
            MudPhysicsParameter parameter) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.value(serverLevel, pos, medium, parameter);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(level, pos, medium);
        if (local != null) {
            return local.value(parameter);
        }
        MudBlockProfileStore.Profile adaptive = adaptiveClientProfile(level, pos);
        if (adaptive != null) {
            return adaptive.value(parameter);
        }
        return value(level, medium, parameter);
    }

    public static boolean enabled(Level level, SinkingMedium medium) {
        return value(level, medium, MudPhysicsParameter.ENABLED) >= 0.5D;
    }

    public static boolean enabled(Level level, BlockPos pos, SinkingMedium medium) {
        return value(level, pos, medium, MudPhysicsParameter.ENABLED) >= 0.5D;
    }

    public static double surfaceHeight(Level level, SinkingMedium medium) {
        return 1.0D;
    }

    public static double surfaceHeight(Level level, BlockState state, SinkingMedium medium) {
        if (state != null && state.getBlock() instanceof MudBlock) {
            return MudBlock.surfaceHeight(state, medium);
        }
        return 1.0D;
    }

    public static double surfaceHeight(
            Level level, BlockPos pos, BlockState state, SinkingMedium medium) {
        if (level != null && pos != null
                && state != null && state.getBlock() instanceof AdaptiveMudBlock) {
            double sourceHeight = AdaptiveMudBlock.sourceSurfaceHeight(level, pos, state);
            if (Double.isFinite(sourceHeight)) {
                return sourceHeight;
            }
        }
        return surfaceHeight(level, state, medium);
    }

    public static double surfaceHeightAt(
            Level level, BlockPos pos, BlockState state, SinkingMedium medium,
            double worldX, double worldZ) {
        if (level != null && pos != null
                && state != null && state.getBlock() instanceof AdaptiveMudBlock) {
            double sourceHeight = AdaptiveMudBlock.sourceSurfaceHeightAt(
                    level, pos, state,
                    worldX - pos.getX(), worldZ - pos.getZ());
            if (Double.isFinite(sourceHeight)) {
                return sourceHeight;
            }
            return Double.NaN;
        }
        return surfaceHeight(level, state, medium);
    }

    /** Highest real local surface overlapping a world-space horizontal footprint. */
    public static double surfaceHeightOver(
            Level level, BlockPos pos, BlockState state, SinkingMedium medium,
            AABB worldBounds) {
        if (level == null || pos == null || state == null || worldBounds == null) {
            return Double.NaN;
        }
        double highest = Double.NaN;
        for (AABB local : MudBlock.localShape(level, pos, state, medium).toAabbs()) {
            double overlapX = Math.min(worldBounds.maxX, pos.getX() + local.maxX)
                    - Math.max(worldBounds.minX, pos.getX() + local.minX);
            double overlapZ = Math.min(worldBounds.maxZ, pos.getZ() + local.maxZ)
                    - Math.max(worldBounds.minZ, pos.getZ() + local.minZ);
            if (overlapX > 1.0E-5D && overlapZ > 1.0E-5D) {
                highest = Double.isNaN(highest)
                        ? local.maxY : Math.max(highest, local.maxY);
            }
        }
        return highest;
    }

    public static MudShapeProfile shape(Level level, SinkingMedium medium) {
        return new MudShapeProfile(MudShapeType.FULL, 1.0D);
    }

    public static MudShapeProfile shape(Level level, BlockState state, SinkingMedium medium) {
        return state != null && state.getBlock() instanceof MudBlock
                ? MudBlock.shapeProfile(state, medium)
                : shape(level, medium);
    }

    public static MudBehaviorType behavior(Level level, SinkingMedium medium) {
        int id = Mth.clamp((int) Math.round(value(level, medium, MudPhysicsParameter.BEHAVIOR_PROFILE)),
                0, MudBehaviorType.values().length - 1);
        MudBehaviorType behavior = MudBehaviorType.values()[id];
        return behavior == MudBehaviorType.RESERVED ? MudBehaviorType.ORDINARY : behavior;
    }

    public static MudBehaviorType behavior(Level level, BlockPos pos, SinkingMedium medium) {
        int id = Mth.clamp((int) Math.round(value(
                level, pos, medium, MudPhysicsParameter.BEHAVIOR_PROFILE)),
                0, MudBehaviorType.values().length - 1);
        MudBehaviorType behavior = MudBehaviorType.values()[id];
        return behavior == MudBehaviorType.RESERVED ? MudBehaviorType.ORDINARY : behavior;
    }

    public static float pollutionMultiplier(Level level, SinkingMedium medium) {
        return (float) value(level, medium, MudPhysicsParameter.POLLUTION_MULTIPLIER);
    }

    public static float pollutionMultiplier(Level level, BlockPos pos, SinkingMedium medium) {
        return (float) value(level, pos, medium, MudPhysicsParameter.POLLUTION_MULTIPLIER);
    }

    public static float coverageMaximum(Level level, SinkingMedium medium) {
        return (float) value(level, medium, MudPhysicsParameter.COVERAGE_MAXIMUM);
    }

    public static float coverageMaximum(Level level, BlockPos pos, SinkingMedium medium) {
        return (float) value(level, pos, medium, MudPhysicsParameter.COVERAGE_MAXIMUM);
    }

    public static float coverageOpacityVariation(Level level, SinkingMedium medium) {
        return (float) value(level, medium, MudPhysicsParameter.COVERAGE_OPACITY_VARIATION);
    }

    public static float coverageOpacityVariation(Level level, BlockPos pos, SinkingMedium medium) {
        return (float) value(level, pos, medium, MudPhysicsParameter.COVERAGE_OPACITY_VARIATION);
    }

    public static float coverageBrightnessVariation(Level level, SinkingMedium medium) {
        return (float) value(level, medium, MudPhysicsParameter.COVERAGE_BRIGHTNESS_VARIATION);
    }

    public static float coverageBrightnessVariation(Level level, BlockPos pos, SinkingMedium medium) {
        return (float) value(level, pos, medium, MudPhysicsParameter.COVERAGE_BRIGHTNESS_VARIATION);
    }

    public static float coverageOpacity(Level level, SinkingMedium medium) {
        return (float) value(level, medium, MudPhysicsParameter.COVERAGE_OPACITY);
    }

    public static int coverageNaturalFadeTicks(Level level, SinkingMedium medium) {
        return Mth.clamp((int) Math.round(value(
                level, medium, MudPhysicsParameter.COVERAGE_NATURAL_FADE_TICKS)), 0, 72000);
    }

    public static float coverageOpacity(Level level, BlockPos pos, SinkingMedium medium) {
        return (float) value(level, pos, medium, MudPhysicsParameter.COVERAGE_OPACITY);
    }

    public static int adaptiveCoverageSmoothingRadius(
            Level level, BlockPos pos, SinkingMedium medium) {
        return Mth.clamp((int) Math.round(value(
                level, pos, medium,
                MudPhysicsParameter.ADAPTIVE_COVERAGE_SMOOTHING_RADIUS)), 0, 3);
    }

    public static float adaptiveCoverageTextureDetail(
            Level level, BlockPos pos, SinkingMedium medium) {
        return Mth.clamp((float) value(
                level, pos, medium,
                MudPhysicsParameter.ADAPTIVE_COVERAGE_TEXTURE_DETAIL), 0.0F, 1.0F);
    }

    public static boolean autoStackFill(Level level, BlockPos pos, SinkingMedium medium) {
        if (level != null && pos != null
                && level.getBlockState(pos).getBlock() instanceof AdaptiveMudBlock) {
            return false;
        }
        return value(level, pos, medium, MudPhysicsParameter.AUTO_STACK_FILL) >= 0.5D;
    }

    public static float clientCoverageMaximum(SinkingMedium medium) {
        return (float) MudPhysicsProfiles.clientValue(
                medium, MudPhysicsParameter.COVERAGE_MAXIMUM);
    }

    public static float clientCoverageOpacity(SinkingMedium medium) {
        return (float) MudPhysicsProfiles.clientValue(
                medium, MudPhysicsParameter.COVERAGE_OPACITY);
    }

    public static float clientCoverageOpacityVariation(SinkingMedium medium) {
        return (float) MudPhysicsProfiles.clientValue(
                medium, MudPhysicsParameter.COVERAGE_OPACITY_VARIATION);
    }

    public static float clientCoverageBrightnessVariation(SinkingMedium medium) {
        return (float) MudPhysicsProfiles.clientValue(
                medium, MudPhysicsParameter.COVERAGE_BRIGHTNESS_VARIATION);
    }

    public static float waterWashMultiplier(Level level, SinkingMedium medium) {
        return (float) value(level, medium, MudPhysicsParameter.WATER_WASH_MULTIPLIER);
    }

    public static float rainWashMultiplier(Level level, SinkingMedium medium) {
        return (float) value(level, medium, MudPhysicsParameter.RAIN_WASH_MULTIPLIER);
    }

    public static AdhesionStrandProfile adhesionStrands(Level level, SinkingMedium medium) {
        return level != null && level.isClientSide()
                ? MudPhysicsProfiles.adhesionStrandsClient(medium)
                : MudPhysicsSettings.adhesionStrandProfile(medium);
    }

    public static AdhesionStrandProfile adhesionStrands(
            Level level, BlockPos pos, SinkingMedium medium) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.adhesion(serverLevel, pos, medium);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(level, pos, medium);
        if (local != null) {
            return local.adhesionStrands();
        }
        MudBlockProfileStore.Profile adaptive = adaptiveClientProfile(level, pos);
        return adaptive == null ? adhesionStrands(level, medium) : adaptive.adhesionStrands();
    }

    public static AssimilationProfile assimilationProfile(Level level, SinkingMedium medium) {
        return level != null && level.isClientSide()
                ? AssimilationProfile.fromValues(MudPhysicsProfiles.clientValues(medium))
                : MudPhysicsSettings.assimilationProfile(medium);
    }

    public static AssimilationProfile assimilationProfile(
            Level level, BlockPos pos, SinkingMedium medium) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.assimilation(serverLevel, pos, medium);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(level, pos, medium);
        if (local != null) {
            return local.assimilation();
        }
        MudBlockProfileStore.Profile adaptive = adaptiveClientProfile(level, pos);
        return adaptive == null ? assimilationProfile(level, medium) : adaptive.assimilation();
    }

    public static MudEruptionProfile eruptionProfile(
            Level level, BlockPos pos, SinkingMedium medium) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.eruption(serverLevel, pos, medium);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(level, pos, medium);
        if (local != null) {
            return local.eruption();
        }
        MudBlockProfileStore.Profile adaptive = adaptiveClientProfile(level, pos);
        return adaptive == null
                ? MudEruptionProfile.fromValues(MudPhysicsProfiles.clientValues(medium))
                : adaptive.eruption();
    }

    public static MudHarvestProfile harvestProfile(
            Level level, BlockPos pos, SinkingMedium medium) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.harvest(serverLevel, pos, medium);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(level, pos, medium);
        if (local != null) {
            return local.harvest();
        }
        MudBlockProfileStore.Profile adaptive = adaptiveClientProfile(level, pos);
        if (adaptive != null) {
            return adaptive.harvest();
        }
        return level != null && level.isClientSide()
                ? MudPhysicsProfiles.harvestClient(medium)
                : MudPhysicsSettings.harvestProfile(medium);
    }

    public static DroppedItemPhysicsProfile droppedItemProfile(
            Level level, BlockPos pos, SinkingMedium medium) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.droppedItems(serverLevel, pos, medium);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(level, pos, medium);
        if (local != null) {
            return local.droppedItems();
        }
        MudBlockProfileStore.Profile adaptive = adaptiveClientProfile(level, pos);
        if (adaptive != null) {
            return adaptive.droppedItems();
        }
        return level != null && level.isClientSide()
                ? MudPhysicsProfiles.droppedItemsClient(medium)
                : MudPhysicsSettings.droppedItemProfile(medium);
    }

    public static MudFlowProfile flowProfile(Level level, BlockPos pos, SinkingMedium medium) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.flow(serverLevel, pos, medium);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(level, pos, medium);
        if (local != null) {
            return local.flow();
        }
        MudBlockProfileStore.Profile adaptive = adaptiveClientProfile(level, pos);
        if (adaptive != null) {
            return adaptive.flow();
        }
        return level != null && level.isClientSide()
                ? MudPhysicsProfiles.flowClient(medium)
                : MudPhysicsSettings.flowProfile(medium);
    }

    static SinkingPhysicsProfile ordinaryProfile(
            Level level, BlockPos pos, SinkingMedium medium, SinkingPhysicsProfile fallback) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.ordinary(serverLevel, pos, medium);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(level, pos, medium);
        if (local != null) {
            return local.ordinary();
        }
        MudBlockProfileStore.Profile adaptive = adaptiveClientProfile(level, pos);
        return adaptive == null ? fallback : adaptive.ordinary();
    }

    static LivingSlimePhysicsProfile livingSlimeProfile(
            Level level, BlockPos pos, LivingSlimePhysicsProfile fallback) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.livingSlime(serverLevel, pos);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(
                level, pos, SinkingMedium.LIVING_SLIME);
        return local == null ? fallback : local.livingSlime();
    }

    static SculkMireProfile sculkMireProfile(
            Level level, BlockPos pos, SinkingMedium medium, SculkMireProfile fallback) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.sculkMire(serverLevel, pos, medium);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(
                level, pos, medium);
        if (local != null) {
            return local.sculkMire();
        }
        MudBlockProfileStore.Profile adaptive = adaptiveClientProfile(level, pos);
        return adaptive == null ? fallback : adaptive.sculkMire();
    }

    static TenderFleshProfile tenderFleshProfile(
            Level level, BlockPos pos, SinkingMedium medium, TenderFleshProfile fallback) {
        if (level instanceof ServerLevel serverLevel && pos != null) {
            return MudBlockProfileStore.tenderFlesh(serverLevel, pos, medium);
        }
        MudLocalProfileCache.Profile local = MudLocalProfileCache.profile(
                level, pos, medium);
        if (local != null) {
            return local.tenderFlesh();
        }
        MudBlockProfileStore.Profile adaptive = adaptiveClientProfile(level, pos);
        return adaptive == null ? fallback : adaptive.tenderFlesh();
    }

    public static TenderFleshProfile tenderFleshProfile(Level level, BlockPos pos) {
        SinkingMedium medium = mediumAt(level, pos, SinkingMedium.TENDER_FLESH);
        TenderFleshProfile fallback = level != null && level.isClientSide()
                ? MudPhysicsProfiles.tenderFleshClient(medium)
                : MudPhysicsSettings.tenderFleshProfile(medium);
        return tenderFleshProfile(level, pos, medium, fallback);
    }

    private static SinkingMedium mediumAt(
            Level level, BlockPos pos, SinkingMedium fallback) {
        if (level == null || pos == null) {
            return fallback;
        }
        SinkingMedium medium = com.fish.mirebound.registry.ModBlocks.mediumOf(
                level.getBlockState(pos).getBlock());
        return medium == null ? fallback : medium;
    }

    private static MudBlockProfileStore.Profile adaptiveClientProfile(Level level, BlockPos pos) {
        if (level == null || !level.isClientSide() || pos == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof AdaptiveMudBlock adaptive
                && adaptive.medium() == SinkingMedium.MUD
                ? AdaptiveMudBehaviorSettings.clientProfile()
                : null;
    }

}
