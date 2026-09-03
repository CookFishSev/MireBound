package com.fish.mirebound.mud;

import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.eruption.MudEruptionProfile;
import com.fish.mirebound.assimilation.AssimilationSystem;
import com.fish.mirebound.splash.MudSplashProfile;
import com.fish.mirebound.tentacle.TentacleGrabProfile;
import com.fish.mirebound.tentacle.TentaclePhysicsProfile;
import com.fish.mirebound.water.WaterGunProfile;
import com.fish.mirebound.water.WaterGunSystem;
import com.fish.mirebound.Mirebound;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsProfile;
import com.fish.mirebound.mud.harvest.MudHarvestProfile;
import com.fish.mirebound.mud.flow.MudFlowProfile;
import com.fish.mirebound.network.payload.MudPhysicsProfileSyncPayload;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class MudPhysicsSettings {
    private static final String FILE_NAME = "mirebound-physics.toml";
    public static final double MUD_TUNING_WAND_MINIMUM_INTERACTION_RANGE = 4.5D;
    public static final double MUD_TUNING_WAND_DEFAULT_INTERACTION_RANGE = 64.0D;
    public static final double MUD_TUNING_WAND_MAXIMUM_INTERACTION_RANGE = 128.0D;
    public static final int ENTITY_COVERAGE_MAXIMUM_FADE_SECONDS = 3600;
    private static final Map<SinkingMedium, Map<MudPhysicsParameter, ModConfigSpec.DoubleValue>> CONFIG_VALUES =
            new EnumMap<>(SinkingMedium.class);
    private static final Map<MudPhysicsParameter, ModConfigSpec.DoubleValue>
            TENTACLE_CONFIG_VALUES = new EnumMap<>(MudPhysicsParameter.class);
    private static final double[][] RUNTIME_VALUES = new double[SinkingMedium.COUNT][];
    private static double[] tentacleValues = MudPhysicsProfiles.tentacleDefaultValues();
    private static final SinkingPhysicsProfile[] ORDINARY_PROFILES =
            new SinkingPhysicsProfile[SinkingMedium.COUNT];
    private static final AdhesionStrandProfile[] ADHESION_STRAND_PROFILES =
            new AdhesionStrandProfile[SinkingMedium.COUNT];
    private static final MudHarvestProfile[] HARVEST_PROFILES =
            new MudHarvestProfile[SinkingMedium.COUNT];
    private static final DroppedItemPhysicsProfile[] DROPPED_ITEM_PROFILES =
            new DroppedItemPhysicsProfile[SinkingMedium.COUNT];
    private static final AssimilationProfile[] ASSIMILATION_PROFILES =
            new AssimilationProfile[SinkingMedium.COUNT];
    private static final MudEruptionProfile[] ERUPTION_PROFILES =
            new MudEruptionProfile[SinkingMedium.COUNT];
    private static final MudFlowProfile[] FLOW_PROFILES =
            new MudFlowProfile[SinkingMedium.COUNT];
    private static final SculkMireProfile[] SCULK_MIRE_PROFILES =
            new SculkMireProfile[SinkingMedium.COUNT];
    private static final TenderFleshProfile[] TENDER_FLESH_PROFILES =
            new TenderFleshProfile[SinkingMedium.COUNT];
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue FOOTPRINT_PERMANENT;
    private static final ModConfigSpec.IntValue FOOTPRINT_MAXIMUM;
    private static final ModConfigSpec.BooleanValue FOOTPRINT_RAIN_WASH;
    private static final ModConfigSpec.IntValue FOOTPRINT_LIFETIME_SECONDS;
    private static final ModConfigSpec.IntValue SURFACE_STAIN_LIFETIME_SECONDS;
    private static final ModConfigSpec.IntValue FOOTPRINT_RAIN_WASH_SECONDS;
    private static final ModConfigSpec.DoubleValue FOOTPRINT_TRAIL_DISTANCE_BLOCKS;
    private static final ModConfigSpec.DoubleValue COVERAGE_EDGE_BLEND_MINIMUM_SOURCE;
    private static final ModConfigSpec.DoubleValue COVERAGE_EDGE_BLEND_MINIMUM_DIFFERENCE;
    private static final ModConfigSpec.DoubleValue COVERAGE_EDGE_BLEND_FIRST_RETENTION;
    private static final ModConfigSpec.DoubleValue COVERAGE_EDGE_BLEND_SECOND_CHANCE;
    private static final ModConfigSpec.DoubleValue COVERAGE_EDGE_BLEND_SECOND_RETENTION;
    private static final ModConfigSpec.IntValue WALL_STAIN_UPDATE_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_TRANSFER_AMOUNT;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_MINIMUM_SOURCE_COVERAGE;
    private static final ModConfigSpec.DoubleValue WALL_TRANSFER_EDGE_FADE_FIRST_CONTRAST_RETENTION;
    private static final ModConfigSpec.DoubleValue WALL_TRANSFER_EDGE_FADE_SECOND_CONTRAST_RETENTION;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_IMPRINT_OPACITY_SCALE;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_OVERLAP_BLEND;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_EDGE_SPREAD_CHANCE;
    private static final ModConfigSpec.IntValue WALL_STAIN_CORNER_WRAP_MAX_PIXELS;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_CORNER_WRAP_RETENTION;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_CORNER_WRAP_ROUGHNESS;
    private static final ModConfigSpec.IntValue WALL_STAIN_DRIP_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_DRIP_CHANCE;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_DRIP_RETENTION;
    private static final ModConfigSpec.IntValue WALL_STAIN_FLOW_DURATION_TICKS;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_FLOW_PREVIEW_CELLS;
    private static final ModConfigSpec.IntValue WALL_STAIN_CEILING_DRIP_COUNT;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_CEILING_DRIP_MAX_LENGTH;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_CEILING_DRIP_LENGTH_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_CEILING_DRIP_MIN_LENGTH_FACTOR;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_CEILING_DRIP_WIDTH;
    private static final ModConfigSpec.DoubleValue WALL_STAIN_CEILING_DRIP_DETACH_CHANCE;
    private static final ModConfigSpec.IntValue WALL_STAIN_CEILING_DRIP_CYCLE_TICKS;
    private static final ModConfigSpec.IntValue WALL_STAIN_FADE_IN_TICKS;
    private static final ModConfigSpec.BooleanValue MUD_SPLASH_ENABLED;
    private static final ModConfigSpec.DoubleValue MUD_SPLASH_MINIMUM_IMPACT_SPEED;
    private static final ModConfigSpec.DoubleValue MUD_SPLASH_MAXIMUM_IMPACT_SPEED;
    private static final ModConfigSpec.IntValue MUD_SPLASH_BASE_DROPLETS;
    private static final ModConfigSpec.IntValue MUD_SPLASH_MAXIMUM_DROPLETS_PER_IMPACT;
    private static final ModConfigSpec.IntValue MUD_SPLASH_MAXIMUM_ACTIVE_DROPLETS;
    private static final ModConfigSpec.DoubleValue MUD_SPLASH_LAUNCH_SPEED;
    private static final ModConfigSpec.DoubleValue MUD_SPLASH_GRAVITY;
    private static final ModConfigSpec.DoubleValue MUD_SPLASH_DRAG;
    private static final ModConfigSpec.IntValue MUD_SPLASH_LIFETIME_TICKS;
    private static final ModConfigSpec.IntValue MUD_SPLASH_IMPACT_COOLDOWN_TICKS;
    private static final ModConfigSpec.DoubleValue MUD_SPLASH_STAIN_RADIUS;
    private static final ModConfigSpec.DoubleValue MUD_SPLASH_STAIN_STRENGTH;
    private static final ModConfigSpec.DoubleValue MUD_SPLASH_PLAYER_HIT_RADIUS;
    private static final ModConfigSpec.DoubleValue MUD_SPLASH_PLAYER_STAIN_STRENGTH;
    private static final ModConfigSpec.DoubleValue MUD_SPLASH_RENDER_DISTANCE;
    private static final ModConfigSpec.IntValue WATER_GUN_CAPACITY;
    private static final ModConfigSpec.IntValue WATER_GUN_WATER_PER_TICK;
    private static final ModConfigSpec.IntValue WATER_GUN_INPUT_TIMEOUT_TICKS;
    private static final ModConfigSpec.IntValue WATER_GUN_SYNC_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue WATER_GUN_WASH_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue WATER_GUN_PRESSURE;
    private static final ModConfigSpec.DoubleValue WATER_GUN_GRAVITY;
    private static final ModConfigSpec.DoubleValue WATER_GUN_MAXIMUM_RANGE;
    private static final ModConfigSpec.DoubleValue WATER_GUN_SEGMENT_LENGTH;
    private static final ModConfigSpec.DoubleValue WATER_GUN_STREAM_WIDTH;
    private static final ModConfigSpec.DoubleValue WATER_GUN_FIRING_MOVEMENT_SCALE;
    private static final ModConfigSpec.DoubleValue WATER_GUN_RECOIL_DEGREES;
    private static final ModConfigSpec.DoubleValue WATER_GUN_BASE_WASH_RADIUS;
    private static final ModConfigSpec.DoubleValue WATER_GUN_DISTANCE_SPREAD;
    private static final ModConfigSpec.DoubleValue WATER_GUN_MAX_WASH_RADIUS;
    private static final ModConfigSpec.DoubleValue WATER_GUN_WASH_AMOUNT;
    private static final ModConfigSpec.DoubleValue WATER_GUN_RENDER_DISTANCE;
    private static final ModConfigSpec.IntValue WATER_GUN_SABLE_MAX_BLOCK_SAMPLES;
    private static final ModConfigSpec.IntValue MUD_PROBE_COOLDOWN_TICKS;
    private static final ModConfigSpec.IntValue MUD_PROBE_MINIMUM_BUBBLES;
    private static final ModConfigSpec.IntValue MUD_PROBE_MAXIMUM_BUBBLES;
    private static final ModConfigSpec.DoubleValue MUD_PROBE_BUBBLE_RADIUS;
    private static final ModConfigSpec.IntValue MUD_PROBE_MINIMUM_BUBBLE_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue MUD_PROBE_MAXIMUM_BUBBLE_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue MUD_PROBE_DEPTH_ERROR;
    private static final ModConfigSpec.DoubleValue MUD_PROBE_MOVEMENT_SCALE;
    private static final ModConfigSpec.DoubleValue MUD_TUNING_WAND_INTERACTION_RANGE;
    private static final ModConfigSpec.DoubleValue ROPE_MAXIMUM_DRAG_DISTANCE;
    private static final ModConfigSpec.IntValue ERUPTION_MAXIMUM_ACTIVE_PER_LEVEL;
    private static final ModConfigSpec.BooleanValue ENTITY_COVERAGE_ENABLED;
    private static final ModConfigSpec.IntValue ENTITY_COVERAGE_AUTOMATIC_FADE_SECONDS;
    private static final ModConfigSpec.IntValue SURFACE_VISUAL_PROFILE_VERSION;
    private static final ModConfigSpec.IntValue MUD_SPLASH_PROFILE_VERSION;
    private static final ModConfigSpec.IntValue ERUPTION_GLOBAL_PROFILE_VERSION;
    private static final ModConfigSpec.IntValue LIVING_SLIME_PROFILE_VERSION;
    private static final ModConfigSpec.IntValue COVERAGE_APPEARANCE_PROFILE_VERSION;
    private static final ModConfigSpec.IntValue ADHESION_VISUAL_PROFILE_VERSION;
    private static final ModConfigSpec.IntValue WATER_GUN_PROFILE_VERSION;
    private static final ModConfigSpec.IntValue DROPPED_ITEM_PROFILE_VERSION;
    private static final ModConfigSpec.IntValue WALL_STAIN_PROFILE_VERSION;
    private static final ModConfigSpec.IntValue MUD_TUNING_WAND_PROFILE_VERSION;
    private static final ModConfigSpec.IntValue SINKING_DEPTH_PROFILE_VERSION;

    private static boolean footprintPermanent;
    private static int maximumFootprints = 768;
    private static boolean footprintRainWash = true;
    private static int footprintLifetimeTicks = 1800 * 20;
    private static float footprintRainWashStep = 1.0F / 7.0F;
    private static float footprintTrailDistanceBlocks = 0.62F;
    private static double mudTuningWandInteractionRange =
            MUD_TUNING_WAND_DEFAULT_INTERACTION_RANGE;
    private static double ropeMaximumDragDistance = 6.0D;
    private static int wallStainUpdateIntervalTicks = 1;
    private static float wallStainTransferAmount = 0.012F;
    private static float wallStainMinimumSourceCoverage = 0.35F;
    private static float wallTransferEdgeFadeFirstContrastRetention = 0.38F;
    private static float wallTransferEdgeFadeSecondContrastRetention = 0.72F;
    private static float wallStainImprintOpacityScale = 0.95F;
    private static float wallStainOverlapBlend = 0.65F;
    private static float wallStainEdgeSpreadChance = 0.72F;
    private static int wallStainCornerWrapMaxPixels = 3;
    private static float wallStainCornerWrapRetention = 0.80F;
    private static float wallStainCornerWrapRoughness = 0.72F;
    private static int wallStainDripIntervalTicks = 16;
    private static float wallStainDripChance = 0.24F;
    private static float wallStainDripRetention = 0.84F;
    private static int wallStainFlowDurationTicks = 48;
    private static float wallStainFlowPreviewCells = 2.75F;
    private static int wallStainCeilingDripCount = 4;
    private static float wallStainCeilingDripMaxLength = 0.62F;
    private static float wallStainCeilingDripLengthMultiplier = 2.0F;
    private static float wallStainCeilingDripMinLengthFactor = 0.18F;
    private static float wallStainCeilingDripWidth = 0.040F;
    private static float wallStainCeilingDripDetachChance = 0.30F;
    private static int wallStainCeilingDripCycleTicks = 90;
    private static int wallStainFadeInTicks = 6;
    private static float coverageEdgeBlendMinimumSource = 0.16F;
    private static float coverageEdgeBlendMinimumDifference = 0.08F;
    private static float coverageEdgeBlendFirstRetention = 0.76F;
    private static float coverageEdgeBlendSecondChance = 0.20F;
    private static float coverageEdgeBlendSecondRetention = 0.44F;
    private static int eruptionMaximumActivePerLevel = 48;
    private static boolean entityCoverageEnabled;
    private static int entityCoverageAutomaticFadeTicks = 30 * 20;

    private static LivingSlimePhysicsProfile livingSlimeProfile;
    private static SculkMireProfile sculkMireProfile;
    private static TenderFleshProfile tenderFleshProfile;
    private static TentaclePhysicsProfile tentacleProfile;
    private static TentacleGrabProfile tentacleGrabProfile;
    private static MudSplashProfile mudSplashProfile = MudSplashProfile.DEFAULT;
    private static WaterGunProfile waterGunProfile = WaterGunProfile.DEFAULT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "Mirebound: Sinking Depths per-world physics tuning.",
                "Values changed with the Mud Tuning Wand are saved here.");
        builder.push("_schema");
        SURFACE_VISUAL_PROFILE_VERSION = builder
                .comment("Internal migration marker for procedural mud-surface defaults.")
                .defineInRange("surface_visual_profile_version", 0, 0, 3);
        MUD_SPLASH_PROFILE_VERSION = builder
                .comment("Internal migration marker for procedural mud-splash defaults.")
                .defineInRange("mud_splash_profile_version", 0, 0, 3);
        ERUPTION_GLOBAL_PROFILE_VERSION = builder
                .comment("Internal migration marker for global eruption-vent defaults.")
                .defineInRange("eruption_global_profile_version", 0, 0, 1);
        LIVING_SLIME_PROFILE_VERSION = builder
                .comment("Internal migration marker for living-slime viscoelastic physics defaults.")
                .defineInRange("living_slime_profile_version", 0, 0, 5);
        COVERAGE_APPEARANCE_PROFILE_VERSION = builder
                .comment("Internal migration marker for final-opacity coverage settings.")
                .defineInRange("coverage_appearance_profile_version", 0, 0, 2);
        ADHESION_VISUAL_PROFILE_VERSION = builder
                .comment("Internal migration marker for adhesive-sheet presentation defaults.")
                .defineInRange("adhesion_visual_profile_version", 0, 0, 5);
        WATER_GUN_PROFILE_VERSION = builder
                .comment("Internal migration marker for pressure-water defaults.")
                .defineInRange("water_gun_profile_version", 0, 0, 5);
        DROPPED_ITEM_PROFILE_VERSION = builder
                .comment("Internal migration marker for dropped-item settling defaults.")
                .defineInRange("dropped_item_profile_version", 0, 0, 2);
        WALL_STAIN_PROFILE_VERSION = builder
                .comment("Internal migration marker for body-to-wall transfer defaults.")
                .defineInRange("wall_stain_profile_version", 0, 0, 2);
        MUD_TUNING_WAND_PROFILE_VERSION = builder
                .comment("Internal migration marker for mud tuning wand defaults.")
                .defineInRange("mud_tuning_wand_profile_version", 0, 0, 1);
        SINKING_DEPTH_PROFILE_VERSION = builder
                .comment("Internal migration marker for independent simple and advanced depth controls.")
                .defineInRange("sinking_depth_profile_version", 0, 0, 3);
        builder.pop();
        builder.push("footprints");
        FOOTPRINT_PERMANENT = builder
                .comment("Keep footprints until rain, support removal, or the global limit removes them.")
                .define("permanent", false);
        FOOTPRINT_MAXIMUM = builder
                .comment("Maximum footprints across all dimensions in this world. Set to 0 to disable decals.")
                .defineInRange("maximum", 768, 0, 4096);
        FOOTPRINT_RAIN_WASH = builder
                .comment("Allow exposed rain to fade and remove footprints.")
                .define("rain_wash", true);
        FOOTPRINT_LIFETIME_SECONDS = builder
                .comment("Legacy footprint lifetime. Kept for existing configurations.")
                .defineInRange("lifetime_seconds", 90, 5, 86400);
        SURFACE_STAIN_LIFETIME_SECONDS = builder
                .comment("Lifetime shared by footprints and wall stains when permanent is false.")
                .defineInRange("surface_stain_lifetime_seconds", 1800, 30, 86400);
        FOOTPRINT_RAIN_WASH_SECONDS = builder
                .comment("Approximate continuous-rain time needed to wash a footprint away.")
                .defineInRange("rain_wash_seconds", 7, 2, 120);
        FOOTPRINT_TRAIL_DISTANCE_BLOCKS = builder
                .comment("Approximate distance a fully dirty pair of feet leaves progressively fading prints.")
                .defineInRange("trail_distance_blocks", 32.0D, 4.0D, 128.0D);
        builder.pop();
        builder.push("coverage_edge_blend");
        COVERAGE_EDGE_BLEND_MINIMUM_SOURCE = builder
                .comment("Minimum dirty coverage that can blend around a skin or armor cube edge.")
                .defineInRange("minimum_source_coverage", 0.16D, 0.0D, 0.90D);
        COVERAGE_EDGE_BLEND_MINIMUM_DIFFERENCE = builder
                .comment("Minimum coverage contrast required before a cleaner adjacent face is filled.")
                .defineInRange("minimum_difference", 0.08D, 0.0D, 0.75D);
        COVERAGE_EDGE_BLEND_FIRST_RETENTION = builder
                .comment("Maximum source coverage retained by the first pixel across an adjacent face.")
                .defineInRange("first_pixel_retention", 0.76D, 0.10D, 1.0D);
        COVERAGE_EDGE_BLEND_SECOND_CHANCE = builder
                .comment("Stable fraction of edge positions that extend to a second pixel.")
                .defineInRange("second_pixel_chance", 0.20D, 0.0D, 1.0D);
        COVERAGE_EDGE_BLEND_SECOND_RETENTION = builder
                .comment("Maximum source coverage retained by the occasional second pixel.")
                .defineInRange("second_pixel_retention", 0.44D, 0.05D, 1.0D);
        builder.pop();
        builder.push("wall_stains");
        WALL_STAIN_UPDATE_INTERVAL_TICKS = builder
                .comment("Ticks between exact body-to-wall transfer updates. 1 gives continuous rubbing.")
                .defineInRange("update_interval_ticks", 1, 1, 20);
        WALL_STAIN_TRANSFER_AMOUNT = builder
                .comment("Coverage removed from each contacting dirty body pixel per transfer update.")
                .defineInRange("transfer_amount", 0.012D, 0.001D, 0.20D);
        WALL_STAIN_MINIMUM_SOURCE_COVERAGE = builder
                .comment("Minimum body-pixel coverage retained after repeated transfers to walls.")
                .defineInRange("minimum_source_coverage", 0.35D, 0.0D, 0.90D);
        WALL_TRANSFER_EDGE_FADE_FIRST_CONTRAST_RETENTION = builder
                .comment("Contrast retained by the first skin or armor pixel across a model edge after wall transfer.")
                .defineInRange("body_edge_fade_first_contrast_retention", 0.38D, 0.0D, 1.0D);
        WALL_TRANSFER_EDGE_FADE_SECOND_CONTRAST_RETENTION = builder
                .comment("Contrast retained by the second skin or armor pixel across a model edge after wall transfer.")
                .defineInRange("body_edge_fade_second_contrast_retention", 0.72D, 0.0D, 1.0D);
        WALL_STAIN_IMPRINT_OPACITY_SCALE = builder
                .comment("Opacity copied from a body pixel to its matching wall pixel.")
                .defineInRange("imprint_opacity_scale", 0.95D, 0.10D, 1.0D);
        WALL_STAIN_OVERLAP_BLEND = builder
                .comment("How strongly a more opaque new contact is fused into an existing wall pixel.")
                .defineInRange("overlap_blend", 0.65D, 0.0D, 1.0D);
        WALL_STAIN_EDGE_SPREAD_CHANCE = builder
                .comment("Chance for each exact contact pixel to seep into each neighboring wall pixel.")
                .defineInRange("edge_spread_chance", 0.72D, 0.0D, 1.0D);
        WALL_STAIN_CORNER_WRAP_MAX_PIXELS = builder
                .comment("Maximum pixels a stain can wrap around an exposed outer block edge. Set to 0 to disable.")
                .defineInRange("corner_wrap_max_pixels", 3, 0, 4);
        WALL_STAIN_CORNER_WRAP_RETENTION = builder
                .comment("Opacity retained by each successive pixel after an outer-edge wrap.")
                .defineInRange("corner_wrap_retention", 0.80D, 0.20D, 1.0D);
        WALL_STAIN_CORNER_WRAP_ROUGHNESS = builder
                .comment("Stable contour variation across an outer-edge wrap.")
                .defineInRange("corner_wrap_roughness", 0.72D, 0.0D, 1.0D);
        WALL_STAIN_DRIP_INTERVAL_TICKS = builder
                .comment("Ticks between low-frequency gravity updates for wall and ceiling stains.")
                .defineInRange("drip_interval_ticks", 16, 2, 100);
        WALL_STAIN_DRIP_CHANCE = builder
                .comment("Chance that an exposed stain pixel flows down or releases a ceiling droplet per update.")
                .defineInRange("drip_chance", 0.24D, 0.0D, 1.0D);
        WALL_STAIN_DRIP_RETENTION = builder
                .comment("Strength retained by a newly formed lower drip pixel.")
                .defineInRange("drip_retention", 0.84D, 0.10D, 1.0D);
        WALL_STAIN_FLOW_DURATION_TICKS = builder
                .comment("Ticks used by the client to extend a mud flow smoothly down a wall.")
                .defineInRange("flow_duration_ticks", 48, 8, 240);
        WALL_STAIN_FLOW_PREVIEW_CELLS = builder
                .comment("Maximum logical mud-pixel distance previewed continuously before server flow catches up.")
                .defineInRange("flow_preview_cells", 2.75D, 0.50D, 4.0D);
        WALL_STAIN_CEILING_DRIP_COUNT = builder
                .comment("Maximum hanging strands rendered for one stained block underside.")
                .defineInRange("ceiling_drip_count", 4, 1, 12);
        WALL_STAIN_CEILING_DRIP_MAX_LENGTH = builder
                .comment("Maximum block length of a hanging mud strand under a stained ceiling.")
                .defineInRange("ceiling_drip_max_length", 0.62D, 0.05D, 1.50D);
        WALL_STAIN_CEILING_DRIP_LENGTH_MULTIPLIER = builder
                .comment("Multiplier applied to hanging mud strand length after random variation.")
                .defineInRange("ceiling_drip_length_multiplier", 2.0D, 0.25D, 4.0D);
        WALL_STAIN_CEILING_DRIP_MIN_LENGTH_FACTOR = builder
                .comment("Shortest strand length as a fraction of the maximum length.")
                .defineInRange("ceiling_drip_min_length_factor", 0.18D, 0.05D, 0.95D);
        WALL_STAIN_CEILING_DRIP_WIDTH = builder
                .comment("Base block width of a hanging mud strand.")
                .defineInRange("ceiling_drip_width", 0.040D, 0.010D, 0.120D);
        WALL_STAIN_CEILING_DRIP_DETACH_CHANCE = builder
                .comment("Fraction of hanging strands that periodically stretch, detach, and fall.")
                .defineInRange("ceiling_drip_detach_chance", 0.30D, 0.0D, 1.0D);
        WALL_STAIN_CEILING_DRIP_CYCLE_TICKS = builder
                .comment("Average ticks for one stretch-and-drop ceiling strand cycle.")
                .defineInRange("ceiling_drip_cycle_ticks", 90, 30, 300);
        WALL_STAIN_FADE_IN_TICKS = builder
                .comment("Client-side fade-in time for newly transferred or newly flowed wall pixels.")
                .defineInRange("fade_in_ticks", 6, 1, 40);
        builder.pop();
        builder.push("mud_splash");
        MUD_SPLASH_ENABLED = builder
                .comment("Create lightweight procedural mud splashes when an entity strikes mud at speed.")
                .define("enabled", true);
        MUD_SPLASH_MINIMUM_IMPACT_SPEED = builder
                .comment("Minimum inward impact speed in blocks per tick before mud splashes.")
                .defineInRange("minimum_impact_speed", 0.30D, 0.05D, 4.0D);
        MUD_SPLASH_MAXIMUM_IMPACT_SPEED = builder
                .comment("Impact speeds above this value no longer increase splash amount or velocity.")
                .defineInRange("maximum_impact_speed", 4.50D, 0.10D, 16.0D);
        MUD_SPLASH_BASE_DROPLETS = builder
                .comment("Droplets produced near the minimum impact speed before mud-volume scaling.")
                .defineInRange("base_droplets", 10, 1, 64);
        MUD_SPLASH_MAXIMUM_DROPLETS_PER_IMPACT = builder
                .comment("Hard visual and simulation cap for one impact.")
                .defineInRange("maximum_droplets_per_impact", 64, 1, 128);
        MUD_SPLASH_MAXIMUM_ACTIVE_DROPLETS = builder
                .comment("Fixed server/client droplet pool capacity per dimension.")
                .defineInRange("maximum_active_droplets", 512, 16, 2048);
        MUD_SPLASH_LAUNCH_SPEED = builder
                .comment("Outward launch speed multiplier after impact and mud-volume scaling.")
                .defineInRange("launch_speed", 0.44D, 0.05D, 1.50D);
        MUD_SPLASH_GRAVITY = builder
                .comment("Downward acceleration applied to procedural droplets each tick.")
                .defineInRange("gravity", 0.040D, 0.005D, 0.20D);
        MUD_SPLASH_DRAG = builder
                .comment("Velocity retained by a procedural droplet each tick.")
                .defineInRange("drag", 0.965D, 0.75D, 1.0D);
        MUD_SPLASH_LIFETIME_TICKS = builder
                .comment("Maximum airborne lifetime of a droplet.")
                .defineInRange("lifetime_ticks", 100, 6, 120);
        MUD_SPLASH_IMPACT_COOLDOWN_TICKS = builder
                .comment("Minimum ticks between splash bursts from the same player.")
                .defineInRange("impact_cooldown_ticks", 8, 1, 60);
        MUD_SPLASH_STAIN_RADIUS = builder
                .comment("Base block-space radius of the surface stain left by one droplet.")
                .defineInRange("stain_radius", 0.13D, 0.03D, 0.45D);
        MUD_SPLASH_STAIN_STRENGTH = builder
                .comment("Maximum opacity of stains deposited on blocks.")
                .defineInRange("stain_strength", 0.84D, 0.05D, 1.0D);
        MUD_SPLASH_PLAYER_HIT_RADIUS = builder
                .comment("Collision radius used when a droplet can stain a nearby player.")
                .defineInRange("player_hit_radius", 0.12D, 0.03D, 0.40D);
        MUD_SPLASH_PLAYER_STAIN_STRENGTH = builder
                .comment("Maximum opacity added to player or armor pixels struck by a droplet.")
                .defineInRange("player_stain_strength", 0.72D, 0.05D, 1.0D);
        MUD_SPLASH_RENDER_DISTANCE = builder
                .comment("Maximum distance at which clients receive and render a splash burst.")
                .defineInRange("render_distance", 48.0D, 8.0D, 128.0D);
        builder.pop();
        builder.push("water_gun");
        WATER_GUN_CAPACITY = builder
                .comment("Water stored by one water gun in internal units.")
                .defineInRange("capacity", 1000, 100, 10000);
        WATER_GUN_WATER_PER_TICK = builder
                .comment("Water consumed for each tick of continuous spraying.")
                .defineInRange("water_per_tick", 3, 1, 1000);
        WATER_GUN_INPUT_TIMEOUT_TICKS = builder
                .comment("Server safety timeout if a client stops refreshing its held-fire input.")
                .defineInRange("input_timeout_ticks", 16, 4, 60);
        WATER_GUN_SYNC_INTERVAL_TICKS = builder
                .comment("Ticks between authoritative stream snapshots sent to nearby clients.")
                .defineInRange("sync_interval_ticks", 2, 1, 10);
        WATER_GUN_WASH_INTERVAL_TICKS = builder
                .comment("Ticks between bounded cleaning passes while the stream remains active.")
                .defineInRange("wash_interval_ticks", 2, 1, 10);
        WATER_GUN_PRESSURE = builder
                .comment("Initial water velocity in blocks per tick.")
                .defineInRange("pressure", 2.20D, 0.25D, 4.0D);
        WATER_GUN_GRAVITY = builder
                .comment("Downward acceleration applied to the water stream per tick squared.")
                .defineInRange("gravity", 0.06D, 0.0D, 0.30D);
        WATER_GUN_MAXIMUM_RANGE = builder
                .comment("Maximum spatial distance of the pressure stream from its nozzle.")
                .defineInRange("maximum_range", 18.0D, 2.0D, 48.0D);
        WATER_GUN_SEGMENT_LENGTH = builder
                .comment("Maximum distance between collision samples along the water stream.")
                .defineInRange("segment_length", 0.62D, 0.20D, 1.0D);
        WATER_GUN_STREAM_WIDTH = builder
                .comment("Collision-capture radius used for water streams on Sable structures.")
                .defineInRange("stream_width", 0.045D, 0.01D, 0.20D);
        WATER_GUN_FIRING_MOVEMENT_SCALE = builder
                .comment("Player movement-speed multiplier while continuously firing the water gun.")
                .defineInRange("firing_movement_scale", 0.82D, 0.10D, 1.0D);
        WATER_GUN_RECOIL_DEGREES = builder
                .comment("Maximum first-person upward water-gun recoil angle in degrees.")
                .defineInRange("recoil_degrees", 3.5D, 0.0D, 12.0D);
        WATER_GUN_BASE_WASH_RADIUS = builder
                .comment("Impact wash radius at the nozzle.")
                .defineInRange("base_wash_radius", 0.26D, 0.10D, 2.0D);
        WATER_GUN_DISTANCE_SPREAD = builder
                .comment("Additional wash radius gained per block traveled.")
                .defineInRange("distance_spread", 0.048D, 0.0D, 0.20D);
        WATER_GUN_MAX_WASH_RADIUS = builder
                .comment("Maximum impact wash radius.")
                .defineInRange("max_wash_radius", 1.12D, 0.10D, 3.0D);
        WATER_GUN_WASH_AMOUNT = builder
                .comment("Maximum opacity removed from a directly hit mud pixel per spray tick.")
                .defineInRange("wash_amount_per_tick", 0.075D, 0.001D, 0.25D);
        WATER_GUN_RENDER_DISTANCE = builder
                .comment("Maximum distance at which clients receive the stream visual.")
                .defineInRange("render_distance", 64.0D, 8.0D, 128.0D);
        WATER_GUN_SABLE_MAX_BLOCK_SAMPLES = builder
                .comment("Hard per-stream budget for Sable collision blocks sampled along the path.")
                .defineInRange("sable_max_block_samples", 256, 32, 1024);
        builder.pop();
        builder.push("mud_probe");
        MUD_PROBE_COOLDOWN_TICKS = builder
                .comment("Server-enforced minimum ticks between successful mud probes.")
                .defineInRange("cooldown_ticks", 12, 1, 100);
        MUD_PROBE_MINIMUM_BUBBLES = builder
                .comment("Minimum bubbles produced around a successful probe point.")
                .defineInRange("minimum_bubbles", 2, 1, 8);
        MUD_PROBE_MAXIMUM_BUBBLES = builder
                .comment("Maximum bubbles produced around a successful probe point.")
                .defineInRange("maximum_bubbles", 4, 1, 8);
        MUD_PROBE_BUBBLE_RADIUS = builder
                .comment("Maximum tangent-plane spread of a probe bubble cluster in blocks.")
                .defineInRange("bubble_radius", 0.50D, 0.0D, 0.75D);
        MUD_PROBE_MINIMUM_BUBBLE_INTERVAL_TICKS = builder
                .comment("Minimum delay between consecutive bubbles in one probe cluster.")
                .defineInRange("minimum_bubble_interval_ticks", 2, 1, 20);
        MUD_PROBE_MAXIMUM_BUBBLE_INTERVAL_TICKS = builder
                .comment("Maximum delay between consecutive bubbles in one probe cluster.")
                .defineInRange("maximum_bubble_interval_ticks", 6, 1, 20);
        MUD_PROBE_DEPTH_ERROR = builder
                .comment("Maximum absolute depth error shown by the mud probe, in blocks.")
                .defineInRange("depth_error", 0.15D, 0.0D, 1.0D);
        MUD_PROBE_MOVEMENT_SCALE = builder
                .comment("Horizontal movement retained while actively probing mud.")
                .defineInRange("movement_scale", 0.75D, 0.0D, 1.0D);
        builder.pop();
        builder.push("mud_tuning_wand");
        MUD_TUNING_WAND_INTERACTION_RANGE = builder
                .comment("Block interaction range while a mud tuning wand is held.")
                .defineInRange(
                        "interaction_range",
                        MUD_TUNING_WAND_DEFAULT_INTERACTION_RANGE,
                        MUD_TUNING_WAND_MINIMUM_INTERACTION_RANGE,
                        MUD_TUNING_WAND_MAXIMUM_INTERACTION_RANGE);
        builder.pop();
        builder.push("rope");
        ROPE_MAXIMUM_DRAG_DISTANCE = builder
                .comment("Maximum distance between a player and the rope segment being dragged.")
                .defineInRange("maximum_drag_distance", 6.0D, 1.0D, 32.0D);
        builder.pop();
        builder.push("eruption_vents");
        ERUPTION_MAXIMUM_ACTIVE_PER_LEVEL = builder
                .comment("Maximum active eruption vents in one dimension. Set to 0 to stop new vents.")
                .defineInRange("maximum_active_per_dimension", 48, 0, 96);
        builder.pop();
        builder.push("entity_coverage");
        ENTITY_COVERAGE_ENABLED = builder
                .comment("Allow non-player living entities to receive new mud coverage.")
                .define("enabled", false);
        ENTITY_COVERAGE_AUTOMATIC_FADE_SECONDS = builder
                .comment("Seconds for non-player entity mud coverage to fade after contact ends. Set to 0 to disable.")
                .defineInRange("automatic_fade_seconds", 30, 0,
                        ENTITY_COVERAGE_MAXIMUM_FADE_SECONDS);
        builder.pop();
        double[] tentacleDefaults = MudPhysicsProfiles.tentacleDefaultValues();
        builder.push("procedural_tentacle");
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            if (!parameter.appliesToTentacle()) {
                continue;
            }
            builder.comment("Range: " + parameter.minimum() + " .. " + parameter.maximum());
            TENTACLE_CONFIG_VALUES.put(parameter, builder.defineInRange(
                    parameter.serializedName(),
                    tentacleDefaults[parameter.ordinal()],
                    parameter.minimum(), parameter.maximum()));
        }
        builder.pop();
        for (SinkingMedium medium : SinkingMedium.values()) {
            double[] defaults = MudPhysicsProfiles.defaultValues(medium);
            Map<MudPhysicsParameter, ModConfigSpec.DoubleValue> mediumValues =
                    new EnumMap<>(MudPhysicsParameter.class);
            CONFIG_VALUES.put(medium, mediumValues);
            builder.push(medium.serializedName());
            for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
                if (!parameter.appliesTo(medium)) {
                    continue;
                }
                builder.comment("Range: " + parameter.minimum() + " .. " + parameter.maximum());
                ModConfigSpec.DoubleValue value = builder.defineInRange(
                        parameter.serializedName(),
                        defaults[parameter.ordinal()],
                        parameter.minimum(),
                        parameter.maximum());
                mediumValues.put(parameter, value);
            }
            builder.pop();
        }
        SPEC = builder.build();
        rebuildRuntimeProfiles(false);
    }

    private MudPhysicsSettings() {
    }

    public static boolean footprintPermanent() {
        return footprintPermanent;
    }

    public static int maximumFootprints() {
        return maximumFootprints;
    }

    public static boolean footprintRainWash() {
        return footprintRainWash;
    }

    public static int footprintLifetimeTicks() {
        return footprintLifetimeTicks;
    }

    public static float footprintRainWashStep() {
        return footprintRainWashStep;
    }

    public static float footprintTrailDistanceBlocks() {
        return footprintTrailDistanceBlocks;
    }

    public static int wallStainUpdateIntervalTicks() {
        return wallStainUpdateIntervalTicks;
    }

    public static float coverageEdgeBlendMinimumSource() {
        return coverageEdgeBlendMinimumSource;
    }

    public static float coverageEdgeBlendMinimumDifference() {
        return coverageEdgeBlendMinimumDifference;
    }

    public static float coverageEdgeBlendFirstRetention() {
        return coverageEdgeBlendFirstRetention;
    }

    public static float coverageEdgeBlendSecondChance() {
        return coverageEdgeBlendSecondChance;
    }

    public static float coverageEdgeBlendSecondRetention() {
        return coverageEdgeBlendSecondRetention;
    }

    public static float wallStainTransferAmount() {
        return wallStainTransferAmount;
    }

    public static float wallStainMinimumSourceCoverage() {
        return wallStainMinimumSourceCoverage;
    }

    public static float wallTransferEdgeFadeFirstContrastRetention() {
        return wallTransferEdgeFadeFirstContrastRetention;
    }

    public static float wallTransferEdgeFadeSecondContrastRetention() {
        return wallTransferEdgeFadeSecondContrastRetention;
    }

    public static float wallStainImprintOpacityScale() {
        return wallStainImprintOpacityScale;
    }

    public static float wallStainOverlapBlend() {
        return wallStainOverlapBlend;
    }

    public static float wallStainEdgeSpreadChance() {
        return wallStainEdgeSpreadChance;
    }

    public static int wallStainCornerWrapMaxPixels() {
        return wallStainCornerWrapMaxPixels;
    }

    public static float wallStainCornerWrapRetention() {
        return wallStainCornerWrapRetention;
    }

    public static float wallStainCornerWrapRoughness() {
        return wallStainCornerWrapRoughness;
    }

    public static int wallStainDripIntervalTicks() {
        return wallStainDripIntervalTicks;
    }

    public static float wallStainDripChance() {
        return wallStainDripChance;
    }

    public static float wallStainDripRetention() {
        return wallStainDripRetention;
    }

    public static int wallStainFlowDurationTicks() {
        return wallStainFlowDurationTicks;
    }

    public static float wallStainFlowPreviewCells() {
        return wallStainFlowPreviewCells;
    }

    public static int wallStainCeilingDripCount() {
        return wallStainCeilingDripCount;
    }

    public static float wallStainCeilingDripMaxLength() {
        return wallStainCeilingDripMaxLength;
    }

    public static float wallStainCeilingDripLengthMultiplier() {
        return wallStainCeilingDripLengthMultiplier;
    }

    public static float wallStainCeilingDripMinLengthFactor() {
        return wallStainCeilingDripMinLengthFactor;
    }

    public static float wallStainCeilingDripWidth() {
        return wallStainCeilingDripWidth;
    }

    public static float wallStainCeilingDripDetachChance() {
        return wallStainCeilingDripDetachChance;
    }

    public static int wallStainCeilingDripCycleTicks() {
        return wallStainCeilingDripCycleTicks;
    }

    public static int wallStainFadeInTicks() {
        return wallStainFadeInTicks;
    }

    public static boolean mudSplashEnabled() {
        return mudSplashProfile.enabled();
    }

    public static double mudSplashMinimumImpactSpeed() {
        return mudSplashProfile.minimumImpactSpeed();
    }

    public static double mudSplashMaximumImpactSpeed() {
        return mudSplashProfile.maximumImpactSpeed();
    }

    public static int mudSplashBaseDroplets() {
        return mudSplashProfile.baseDroplets();
    }

    public static int mudSplashMaximumDropletsPerImpact() {
        return mudSplashProfile.maximumDropletsPerImpact();
    }

    public static int mudSplashMaximumActiveDroplets() {
        return mudSplashProfile.maximumActiveDroplets();
    }

    public static double mudSplashLaunchSpeed() {
        return mudSplashProfile.launchSpeed();
    }

    public static double mudSplashGravity() {
        return mudSplashProfile.gravity();
    }

    public static double mudSplashDrag() {
        return mudSplashProfile.drag();
    }

    public static int mudSplashLifetimeTicks() {
        return mudSplashProfile.lifetimeTicks();
    }

    public static int mudSplashImpactCooldownTicks() {
        return mudSplashProfile.impactCooldownTicks();
    }

    public static float mudSplashStainRadius() {
        return mudSplashProfile.stainRadius();
    }

    public static float mudSplashStainStrength() {
        return mudSplashProfile.stainStrength();
    }

    public static float mudSplashPlayerHitRadius() {
        return mudSplashProfile.playerHitRadius();
    }

    public static float mudSplashPlayerStainStrength() {
        return mudSplashProfile.playerStainStrength();
    }

    public static double mudSplashRenderDistance() {
        return mudSplashProfile.renderDistance();
    }

    public static MudSplashProfile mudSplashProfile() {
        return mudSplashProfile;
    }

    public static int waterGunCapacity() {
        return waterGunProfile.capacity();
    }

    public static WaterGunProfile waterGunProfile() {
        return waterGunProfile;
    }

    public static int mudProbeCooldownTicks() {
        return MUD_PROBE_COOLDOWN_TICKS.get();
    }

    public static int mudProbeMinimumBubbles() {
        return Math.min(MUD_PROBE_MINIMUM_BUBBLES.get(), mudProbeMaximumBubbles());
    }

    public static int mudProbeMaximumBubbles() {
        return MUD_PROBE_MAXIMUM_BUBBLES.get();
    }

    public static double mudProbeBubbleRadius() {
        return MUD_PROBE_BUBBLE_RADIUS.get();
    }

    public static int mudProbeMinimumBubbleIntervalTicks() {
        return Math.min(
                MUD_PROBE_MINIMUM_BUBBLE_INTERVAL_TICKS.get(),
                mudProbeMaximumBubbleIntervalTicks());
    }

    public static int mudProbeMaximumBubbleIntervalTicks() {
        return MUD_PROBE_MAXIMUM_BUBBLE_INTERVAL_TICKS.get();
    }

    public static double mudProbeDepthError() {
        return MUD_PROBE_DEPTH_ERROR.get();
    }

    public static double mudProbeMovementScale() {
        return MUD_PROBE_MOVEMENT_SCALE.get();
    }

    public static double mudTuningWandInteractionRange() {
        return mudTuningWandInteractionRange;
    }

    public static double ropeMaximumDragDistance() {
        return ropeMaximumDragDistance;
    }

    public static void updateMudTuningWandInteractionRange(double range) {
        if (!Double.isFinite(range)) {
            return;
        }
        MUD_TUNING_WAND_INTERACTION_RANGE.set(Math.max(
                MUD_TUNING_WAND_MINIMUM_INTERACTION_RANGE,
                Math.min(MUD_TUNING_WAND_MAXIMUM_INTERACTION_RANGE, range)));
        mudTuningWandInteractionRange = MUD_TUNING_WAND_INTERACTION_RANGE.get();
        SPEC.save();
    }

    public static int eruptionMaximumActivePerLevel() {
        return eruptionMaximumActivePerLevel;
    }

    public static void updateEruptionMaximumActivePerLevel(int maximum) {
        int sanitized = Math.max(0, Math.min(96, maximum));
        ERUPTION_MAXIMUM_ACTIVE_PER_LEVEL.set(sanitized);
        SPEC.save();
        eruptionMaximumActivePerLevel = sanitized;
    }

    public static int entityCoverageAutomaticFadeSeconds() {
        return entityCoverageAutomaticFadeTicks / 20;
    }

    public static boolean entityCoverageEnabled() {
        return entityCoverageEnabled;
    }

    public static void updateEntityCoverageEnabled(boolean enabled) {
        ENTITY_COVERAGE_ENABLED.set(enabled);
        SPEC.save();
        entityCoverageEnabled = enabled;
    }

    public static int entityCoverageAutomaticFadeTicks() {
        return entityCoverageAutomaticFadeTicks;
    }

    public static void updateEntityCoverageAutomaticFadeSeconds(int seconds) {
        int sanitized = Math.max(0,
                Math.min(ENTITY_COVERAGE_MAXIMUM_FADE_SECONDS, seconds));
        ENTITY_COVERAGE_AUTOMATIC_FADE_SECONDS.set(sanitized);
        SPEC.save();
        entityCoverageAutomaticFadeTicks = sanitized * 20;
    }

    public static void register(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, FILE_NAME);
        modBus.addListener(MudPhysicsSettings::onConfigLoading);
        modBus.addListener(MudPhysicsSettings::onConfigReloading);
        modBus.addListener(MudPhysicsSettings::onConfigUnloading);
    }

    public static double[] values(SinkingMedium medium) {
        return Arrays.copyOf(RUNTIME_VALUES[medium.id()], MudPhysicsParameter.COUNT);
    }

    public static double value(SinkingMedium medium, MudPhysicsParameter parameter) {
        double[] values = RUNTIME_VALUES[medium.id()];
        return values == null
                ? MudPhysicsProfiles.defaultValues(medium)[parameter.ordinal()]
                : values[parameter.ordinal()];
    }

    public static boolean customized(SinkingMedium medium) {
        double[] current = RUNTIME_VALUES[medium.id()];
        double[] defaults = MudPhysicsProfiles.defaultValues(medium);
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            if (parameter.appliesTo(medium)
                    && Math.abs(current[parameter.ordinal()] - defaults[parameter.ordinal()]) > 1.0E-9D) {
                return true;
            }
        }
        return false;
    }

    public static void update(SinkingMedium medium, double[] candidate) {
        double[] sanitized = MudPhysicsProfiles.sanitize(medium, candidate);
        for (Map.Entry<MudPhysicsParameter, ModConfigSpec.DoubleValue> entry : CONFIG_VALUES.get(medium).entrySet()) {
            entry.getValue().set(sanitized[entry.getKey().ordinal()]);
        }
        SPEC.save();
        rebuildRuntimeProfiles(true);
        AssimilationSystem.syncProfiles();
    }

    public static void reset(SinkingMedium medium) {
        for (ModConfigSpec.DoubleValue value : CONFIG_VALUES.get(medium).values()) {
            value.set(value.getDefault());
        }
        SPEC.save();
        rebuildRuntimeProfiles(true);
        AssimilationSystem.syncProfiles();
    }

    public static double[] tentacleValues() {
        return Arrays.copyOf(tentacleValues, MudPhysicsParameter.COUNT);
    }

    public static void updateTentacle(double[] candidate) {
        double[] sanitized = MudPhysicsProfiles.sanitizeTentacle(candidate);
        for (Map.Entry<MudPhysicsParameter, ModConfigSpec.DoubleValue> entry
                : TENTACLE_CONFIG_VALUES.entrySet()) {
            entry.getValue().set(sanitized[entry.getKey().ordinal()]);
        }
        SPEC.save();
        rebuildRuntimeProfiles(true);
    }

    public static void resetTentacle() {
        for (ModConfigSpec.DoubleValue value : TENTACLE_CONFIG_VALUES.values()) {
            value.set(value.getDefault());
        }
        SPEC.save();
        rebuildRuntimeProfiles(true);
    }

    static SinkingPhysicsProfile ordinaryProfile(SinkingMedium medium) {
        return ORDINARY_PROFILES[medium.id()];
    }

    static LivingSlimePhysicsProfile livingSlimeProfile() {
        return livingSlimeProfile;
    }

    static SculkMireProfile sculkMireProfile() {
        return sculkMireProfile(SinkingMedium.SCULK_MIRE);
    }

    static SculkMireProfile sculkMireProfile(SinkingMedium medium) {
        return SCULK_MIRE_PROFILES[medium.id()];
    }

    static TenderFleshProfile tenderFleshProfile() {
        return tenderFleshProfile(SinkingMedium.TENDER_FLESH);
    }

    static TenderFleshProfile tenderFleshProfile(SinkingMedium medium) {
        return TENDER_FLESH_PROFILES[medium.id()];
    }

    static AdhesionStrandProfile adhesionStrandProfile(SinkingMedium medium) {
        return ADHESION_STRAND_PROFILES[medium.id()];
    }

    public static MudFlowProfile flowProfile(SinkingMedium medium) {
        return FLOW_PROFILES[medium.id()];
    }

    public static AssimilationProfile assimilationProfile(SinkingMedium medium) {
        return medium == null
                ? AssimilationProfile.DEFAULT
                : ASSIMILATION_PROFILES[medium.id()];
    }

    public static MudEruptionProfile eruptionProfile(SinkingMedium medium) {
        return ERUPTION_PROFILES[medium.id()];
    }

    public static TentaclePhysicsProfile tentacleProfile() {
        return tentacleProfile;
    }

    public static TentacleGrabProfile tentacleGrabProfile() {
        return tentacleGrabProfile;
    }

    public static MudHarvestProfile harvestProfile(SinkingMedium medium) {
        return HARVEST_PROFILES[medium.id()];
    }

    public static DroppedItemPhysicsProfile droppedItemProfile(SinkingMedium medium) {
        return DROPPED_ITEM_PROFILES[medium.id()];
    }

    public static void syncAll(ServerPlayer player) {
        for (SinkingMedium medium : SinkingMedium.values()) {
            sync(player, medium, false, false);
        }
    }

    public static void sync(ServerPlayer player, SinkingMedium medium, boolean openScreen, boolean editable) {
        PacketDistributor.sendToPlayer(player, payload(medium, openScreen, editable));
    }

    public static void broadcast(ServerLevel level, SinkingMedium medium) {
        MudPhysicsProfileSyncPayload payload = payload(medium, false, false);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static MudPhysicsProfileSyncPayload payload(SinkingMedium medium, boolean openScreen, boolean editable) {
        return new MudPhysicsProfileSyncPayload(
                medium.id(),
                openScreen,
                editable,
                customized(medium),
                BlockPos.ZERO,
                MudBlockVariant.DEFAULT.ordinal(),
                16,
                values(medium));
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (isOurServerConfig(event.getConfig())) {
            migrateSurfaceVisualDefaults();
            migrateMudSplashDefaults();
            migrateEruptionGlobalDefaults();
            migrateLivingSlimeDefaults();
            migrateCoverageAppearanceDefaults();
            migrateAdhesionVisualDefaults();
            migrateWaterGunDefaults();
            migrateDroppedItemDefaults();
            migrateWallStainDefaults();
            migrateMudTuningWandDefaults();
            migrateSinkingDepthModes();
            rebuildRuntimeProfiles(true);
        }
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (isOurServerConfig(event.getConfig())) {
            rebuildRuntimeProfiles(true);
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    syncAll(player);
                    WaterGunSystem.syncProfile(player);
                }
                AssimilationSystem.syncProfiles();
            }
        }
    }

    private static void onConfigUnloading(ModConfigEvent.Unloading event) {
        if (isOurServerConfig(event.getConfig())) {
            rebuildRuntimeProfiles(false);
        }
    }

    private static boolean isOurServerConfig(ModConfig config) {
        return config.getType() == ModConfig.Type.SERVER
                && Mirebound.MOD_ID.equals(config.getModId())
                && FILE_NAME.equals(config.getFileName());
    }

    private static void migrateSurfaceVisualDefaults() {
        if (!SPEC.isLoaded() || SURFACE_VISUAL_PROFILE_VERSION.get() >= 3) {
            return;
        }
        int version = SURFACE_VISUAL_PROFILE_VERSION.get();
        for (SinkingMedium medium : SinkingMedium.values()) {
            Map<MudPhysicsParameter, ModConfigSpec.DoubleValue> values = CONFIG_VALUES.get(medium);
            if (version < 1) {
                migrateDefault(values, MudPhysicsParameter.SURFACE_HOLE_RADIUS_SCALE, 1.0D, 1.10D);
                migrateDefault(values, MudPhysicsParameter.SURFACE_RIM_WIDTH_PIXELS, 1.75D, 2.50D);
                migrateDefault(values, MudPhysicsParameter.SURFACE_RIM_HEIGHT_PIXELS,
                        medium == SinkingMedium.LIVING_SLIME ? 0.60D : 0.90D,
                        medium == SinkingMedium.LIVING_SLIME ? 0.80D : 1.25D);
                migrateDefault(values, MudPhysicsParameter.SURFACE_DISPLACEMENT_PIXELS,
                        medium == SinkingMedium.LIVING_SLIME ? 0.62D : 0.82D,
                        medium == SinkingMedium.LIVING_SLIME ? 0.76D : 1.05D);
            }
            if (version < 2) {
                migrateDefault(
                        values,
                        MudPhysicsParameter.SURFACE_IMPACT_PILE_EXPANSION_PIXELS,
                        previousImpactExpansion(medium),
                        MudPhysicsProfiles.defaultValues(medium)[
                                MudPhysicsParameter.SURFACE_IMPACT_PILE_EXPANSION_PIXELS.ordinal()]);
            }
            if (version < 3) {
                migrateDefault(
                        values,
                        MudPhysicsParameter.SURFACE_CLOSE_TICKS,
                        medium == SinkingMedium.TENDER_FLESH ? 120.0D : 90.0D,
                        MudPhysicsProfiles.defaultValues(medium)[
                                MudPhysicsParameter.SURFACE_CLOSE_TICKS.ordinal()]);
            }
        }
        SURFACE_VISUAL_PROFILE_VERSION.set(3);
        SPEC.save();
    }

    private static void migrateMudTuningWandDefaults() {
        if (!SPEC.isLoaded() || MUD_TUNING_WAND_PROFILE_VERSION.get() >= 1) {
            return;
        }
        migrateValue(MUD_TUNING_WAND_INTERACTION_RANGE, 16.0D, 64.0D);
        MUD_TUNING_WAND_PROFILE_VERSION.set(1);
        SPEC.save();
    }

    private static void migrateSinkingDepthModes() {
        if (!SPEC.isLoaded() || SINKING_DEPTH_PROFILE_VERSION.get() >= 3) {
            return;
        }
        if (SINKING_DEPTH_PROFILE_VERSION.get() < 1) {
            for (SinkingMedium medium : SinkingMedium.values()) {
                Map<MudPhysicsParameter, ModConfigSpec.DoubleValue> values = CONFIG_VALUES.get(medium);
                if (!values.containsKey(MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH)
                        || MudSinkingDepthControl.mode(
                                values.get(MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE).get())
                                != MudSinkingDepthControl.Mode.SIMPLE) {
                    continue;
                }
                double maximumDepth = MudSinkingDepthControl.maximumDepth(
                        values.get(MudPhysicsParameter.MAX_DEPTH_FACTOR).get(),
                        values.get(MudPhysicsParameter.COLUMN_MARGIN).get());
                values.get(MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH).set(maximumDepth);
            }
        }
        if (SINKING_DEPTH_PROFILE_VERSION.get() < 3) {
            for (SinkingMedium medium : SinkingMedium.values()) {
                migrateDefault(
                        CONFIG_VALUES.get(medium),
                        MudPhysicsParameter.STEP_HEIGHT,
                        0.20D,
                        0.35D);
            }
        }
        SINKING_DEPTH_PROFILE_VERSION.set(3);
        SPEC.save();
    }

    private static double previousImpactExpansion(SinkingMedium medium) {
        return switch (medium) {
            case TAR -> 1.75D;
            case RED_QUICKSAND, SOFT_QUICKSAND, SILT, JUNGLE_QUICKSAND -> 2.75D;
            case LIVING_SLIME -> 2.25D;
            default -> 2.50D;
        };
    }

    private static void migrateMudSplashDefaults() {
        if (!SPEC.isLoaded() || MUD_SPLASH_PROFILE_VERSION.get() >= 3) {
            return;
        }
        int version = MUD_SPLASH_PROFILE_VERSION.get();
        if (version < 1) {
            if (Math.abs(MUD_SPLASH_MINIMUM_IMPACT_SPEED.get() - 0.48D) <= 1.0E-9D) {
                MUD_SPLASH_MINIMUM_IMPACT_SPEED.set(0.30D);
            }
            if (MUD_SPLASH_BASE_DROPLETS.get() == 5) {
                MUD_SPLASH_BASE_DROPLETS.set(6);
            }
        }
        if (version < 2) {
            if (Math.abs(MUD_SPLASH_MAXIMUM_IMPACT_SPEED.get() - 2.40D) <= 1.0E-9D) {
                MUD_SPLASH_MAXIMUM_IMPACT_SPEED.set(4.50D);
            }
            if (MUD_SPLASH_BASE_DROPLETS.get() == 6) {
                MUD_SPLASH_BASE_DROPLETS.set(10);
            }
            if (MUD_SPLASH_MAXIMUM_DROPLETS_PER_IMPACT.get() == 24) {
                MUD_SPLASH_MAXIMUM_DROPLETS_PER_IMPACT.set(64);
            }
            if (MUD_SPLASH_MAXIMUM_ACTIVE_DROPLETS.get() == 192) {
                MUD_SPLASH_MAXIMUM_ACTIVE_DROPLETS.set(512);
            }
        }
        if (version < 3 && MUD_SPLASH_LIFETIME_TICKS.get() == 34) {
            MUD_SPLASH_LIFETIME_TICKS.set(100);
        }
        MUD_SPLASH_PROFILE_VERSION.set(3);
        SPEC.save();
    }

    private static void migrateEruptionGlobalDefaults() {
        if (!SPEC.isLoaded() || ERUPTION_GLOBAL_PROFILE_VERSION.get() >= 1) {
            return;
        }
        if (ERUPTION_MAXIMUM_ACTIVE_PER_LEVEL.get() == 24) {
            ERUPTION_MAXIMUM_ACTIVE_PER_LEVEL.set(48);
        }
        ERUPTION_GLOBAL_PROFILE_VERSION.set(1);
        SPEC.save();
    }

    private static void migrateLivingSlimeDefaults() {
        if (!SPEC.isLoaded()) {
            return;
        }
        int version = LIVING_SLIME_PROFILE_VERSION.get();
        Map<MudPhysicsParameter, ModConfigSpec.DoubleValue> values =
                CONFIG_VALUES.get(SinkingMedium.LIVING_SLIME);
        if (version < 1) {
            migrateDefault(values, MudPhysicsParameter.SLIME_REST_HEIGHT_FACTOR, 0.38D, 0.52D);
            migrateDefault(values, MudPhysicsParameter.SLIME_REST_COLUMN_FACTOR, 0.48D, 0.62D);
            migrateDefault(values, MudPhysicsParameter.SLIME_VERTICAL_SPRING, 0.018D, 0.035D);
            migrateDefault(values, MudPhysicsParameter.SLIME_VERTICAL_DAMPING, 0.36D, 0.28D);
            migrateDefault(values, MudPhysicsParameter.SLIME_BASE_SINK_BIAS, 0.0012D, 0.0016D);
            migrateDefault(values, MudPhysicsParameter.SLIME_MOVEMENT_SINK_SCALE, 0.010D, 0.006D);
            migrateDefault(values, MudPhysicsParameter.SLIME_CROUCH_SINK, 0.0020D, 0.0030D);
            migrateDefault(values, MudPhysicsParameter.SLIME_MAX_DOWN_SPEED, 0.060D, 0.045D);
            migrateDefault(values, MudPhysicsParameter.SLIME_MAX_UP_SPEED, 0.155D, 0.190D);
            migrateDefault(values, MudPhysicsParameter.SLIME_IMPACT_THRESHOLD, 0.055D, 0.045D);
            migrateDefault(values, MudPhysicsParameter.SLIME_IMPACT_RESTITUTION, 0.42D, 0.72D);
            migrateDefault(values, MudPhysicsParameter.SLIME_WALK_SHALLOW, 0.985D, 0.995D);
            migrateDefault(values, MudPhysicsParameter.SLIME_WALK_DEEP, 0.62D, 0.48D);
            migrateDefault(values, MudPhysicsParameter.SLIME_ANCHOR_TUG_SHALLOW, 0.085D, 0.020D);
            migrateDefault(values, MudPhysicsParameter.SLIME_ANCHOR_TUG_DEEP, 0.040D, 0.055D);
            migrateDefault(values, MudPhysicsParameter.SLIME_ANCHOR_FOLLOW_SHALLOW, 0.035D, 0.20D);
            migrateDefault(values, MudPhysicsParameter.SLIME_ANCHOR_FOLLOW_DEEP, 0.007D, 0.065D);
            migrateDefault(values, MudPhysicsParameter.SLIME_STRUGGLE_MIN, 0.060D, 0.075D);
            migrateDefault(values, MudPhysicsParameter.SLIME_STRUGGLE_MAX, 0.225D, 0.260D);
            migrateDefault(values, MudPhysicsParameter.SLIME_STRUGGLE_DEEP_MULTIPLIER, 0.70D, 0.72D);
        }
        if (version < 2) {
            migrateDefault(values, MudPhysicsParameter.SLIME_VERTICAL_SPRING, 0.035D, 0.10D);
            migrateDefault(values, MudPhysicsParameter.SLIME_VERTICAL_DAMPING, 0.28D, 0.85D);
            migrateDefault(values, MudPhysicsParameter.SLIME_BASE_SINK_BIAS, 0.0016D, 0.0010D);
            migrateDefault(values, MudPhysicsParameter.SLIME_MOVEMENT_SINK_SCALE, 0.006D, 0.0D);
            migrateDefault(values, MudPhysicsParameter.SLIME_CROUCH_SINK, 0.0030D, 0.0015D);
            migrateDefault(values, MudPhysicsParameter.SLIME_MAX_DOWN_SPEED, 0.045D, 0.180D);
            migrateDefault(values, MudPhysicsParameter.SLIME_MAX_UP_SPEED, 0.190D, 0.220D);
            migrateDefault(values, MudPhysicsParameter.SLIME_WALK_SHALLOW, 0.995D, 1.0D);
            migrateDefault(values, MudPhysicsParameter.SLIME_WALK_DEEP, 0.48D, 0.75D);
            migrateDefault(values, MudPhysicsParameter.SLIME_ANCHOR_TUG_SHALLOW, 0.020D, 0.10D);
            migrateDefault(values, MudPhysicsParameter.SLIME_ANCHOR_TUG_DEEP, 0.055D, 0.10D);
            migrateDefault(values, MudPhysicsParameter.SLIME_ANCHOR_FOLLOW_SHALLOW, 0.20D, 0.010D);
            migrateDefault(values, MudPhysicsParameter.SLIME_ANCHOR_FOLLOW_DEEP, 0.065D, 0.001D);
        }
        if (version < 3) {
            migrateDefault(values, MudPhysicsParameter.SLIME_MAX_UP_SPEED, 0.220D, 0.450D);
            migrateDefault(values, MudPhysicsParameter.SLIME_STRUGGLE_MIN, 0.075D, 0.100D);
            migrateDefault(values, MudPhysicsParameter.SLIME_STRUGGLE_MAX, 0.260D, 0.440D);
        }
        if (version < 4) {
            migrateDefault(values, MudPhysicsParameter.SLIME_MAX_UP_SPEED, 0.450D, 1.500D);
            migrateDefault(values, MudPhysicsParameter.SLIME_IMPACT_RESTITUTION, 0.72D, 0.82D);
        }
        if (version < 5) {
            migrateDefault(values, MudPhysicsParameter.SLIME_STRUGGLE_MAX, 0.440D, 0.720D);
            migrateDefault(values, MudPhysicsParameter.SLIME_STRUGGLE_DEEP_MULTIPLIER, 0.72D, 0.85D);
        }
        LIVING_SLIME_PROFILE_VERSION.set(5);
        SPEC.save();
    }

    private static void migrateCoverageAppearanceDefaults() {
        if (!SPEC.isLoaded() || COVERAGE_APPEARANCE_PROFILE_VERSION.get() >= 2) {
            return;
        }
        int version = COVERAGE_APPEARANCE_PROFILE_VERSION.get();
        if (version < 1) {
            for (SinkingMedium medium : SinkingMedium.values()) {
                migrateDefault(
                        CONFIG_VALUES.get(medium),
                        MudPhysicsParameter.COVERAGE_OPACITY,
                        1.0D,
                        medium.defaultCoverageOpacity());
            }
        }
        if (version < 2) {
            migrateDefault(
                    CONFIG_VALUES.get(SinkingMedium.ASSIMILATION_SLIME),
                    MudPhysicsParameter.COVERAGE_OPACITY,
                    1.0D,
                    SinkingMedium.ASSIMILATION_SLIME.defaultCoverageOpacity());
        }
        COVERAGE_APPEARANCE_PROFILE_VERSION.set(2);
        SPEC.save();
    }

    private static void migrateAdhesionVisualDefaults() {
        if (!SPEC.isLoaded() || ADHESION_VISUAL_PROFILE_VERSION.get() >= 5) {
            return;
        }
        int version = ADHESION_VISUAL_PROFILE_VERSION.get();
        Map<MudPhysicsParameter, ModConfigSpec.DoubleValue> values =
                CONFIG_VALUES.get(SinkingMedium.TAR);
        if (version < 1) {
            migrateDefault(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT, 2.0D, 6.0D);
            migrateDefault(values, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT, 5.0D, 8.0D);
            migrateDefault(values, MudPhysicsParameter.ADHESION_STRAND_SPAWN_HEIGHT, 0.62D, 1.45D);
            migrateDefault(values, MudPhysicsParameter.ADHESION_SHEET_MIN_RIBS, 4.0D, 6.0D);
            migrateDefault(values, MudPhysicsParameter.ADHESION_SHEET_MAX_SPAN, 0.92D, 1.20D);
        }
        if (version < 2) {
            migrateDefault(values, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH, 2.90D, 4.20D);
        }
        if (version < 3) {
            migrateDefault(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT, 6.0D, 10.0D);
            migrateDefault(values, MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT, 8.0D, 16.0D);
            migrateDefault(values, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH, 4.20D, 5.50D);
            migrateDefault(values, MudPhysicsParameter.ADHESION_SPAWN_INTERVAL_TICKS, 3.0D, 2.0D);
            migrateDefault(values, MudPhysicsParameter.ADHESION_BREAK_CONFIRM_TICKS, 6.0D, 10.0D);
        }
        if (version < 4) {
            migrateDefault(values, MudPhysicsParameter.ADHESION_STRAND_BREAK_LENGTH, 5.50D, 2.00D);
        }
        if (version < 5) {
            for (SinkingMedium medium : SinkingMedium.values()) {
                if (medium == SinkingMedium.TENDER_FLESH) {
                    continue;
                }
                Map<MudPhysicsParameter, ModConfigSpec.DoubleValue> mediumValues =
                        CONFIG_VALUES.get(medium);
                double[] previousDefaults = new double[MudPhysicsParameter.COUNT];
                double[] templateDefaults = new double[MudPhysicsParameter.COUNT];
                AdhesionStrandProfile.defaultsBeforeSharedTarTemplate(medium)
                        .writeTo(previousDefaults);
                AdhesionStrandProfile.defaultsFor(medium).writeTo(templateDefaults);
                for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
                    if (parameter.category() != MudPhysicsParameter.Category.ADHESION_STRANDS
                            || AdhesionStrandProfile.isFeatureSwitch(parameter)) {
                        continue;
                    }
                    migrateDefault(
                            mediumValues,
                            parameter,
                            previousDefaults[parameter.ordinal()],
                            templateDefaults[parameter.ordinal()]);
                }
            }
        }
        ADHESION_VISUAL_PROFILE_VERSION.set(5);
        SPEC.save();
    }

    private static void migrateWaterGunDefaults() {
        if (!SPEC.isLoaded() || WATER_GUN_PROFILE_VERSION.get() >= 5) {
            return;
        }
        int version = WATER_GUN_PROFILE_VERSION.get();
        if (version < 1) {
            migrateValue(WATER_GUN_BASE_WASH_RADIUS, 0.30D, 0.26D);
            migrateValue(WATER_GUN_DISTANCE_SPREAD, 0.020D, 0.048D);
            migrateValue(WATER_GUN_MAX_WASH_RADIUS, 0.78D, 1.12D);
            migrateValue(WATER_GUN_WASH_AMOUNT, 0.035D, 0.050D);
        }
        if (version < 2) {
            migrateValue(WATER_GUN_PRESSURE, 1.35D, 2.60D);
            migrateValue(WATER_GUN_GRAVITY, 0.055D, 0.018D);
            migrateValue(WATER_GUN_WASH_AMOUNT, 0.050D, 0.075D);
        }
        if (version < 3) {
            migrateValue(WATER_GUN_PRESSURE, 2.60D, 2.20D);
            migrateValue(WATER_GUN_GRAVITY, 0.018D, 0.045D);
        }
        if (version < 4) {
            migrateValue(WATER_GUN_GRAVITY, 0.045D, 0.27D);
        }
        if (version < 5) {
            migrateValue(WATER_GUN_GRAVITY, 0.27D, 0.06D);
        }
        WATER_GUN_PROFILE_VERSION.set(5);
        SPEC.save();
    }

    private static void migrateDroppedItemDefaults() {
        if (!SPEC.isLoaded() || DROPPED_ITEM_PROFILE_VERSION.get() >= 2) {
            return;
        }
        int version = DROPPED_ITEM_PROFILE_VERSION.get();
        if (version < 1) {
            for (SinkingMedium medium : SinkingMedium.values()) {
                DroppedItemPhysicsProfile previous =
                        DroppedItemPhysicsProfile.defaultsBeforeVisibleSettling(medium);
                DroppedItemPhysicsProfile current = DroppedItemPhysicsProfile.defaultsFor(medium);
                Map<MudPhysicsParameter, ModConfigSpec.DoubleValue> values =
                        CONFIG_VALUES.get(medium);
                migrateDefault(
                        values,
                        MudPhysicsParameter.ITEM_MAXIMUM_SINK_DEPTH,
                        previous.maximumSinkDepth(),
                        current.maximumSinkDepth());
                migrateDefault(
                        values,
                        MudPhysicsParameter.ITEM_MAXIMUM_IMPACT_PENETRATION,
                        previous.maximumImpactPenetration(),
                        current.maximumImpactPenetration());
            }
        }
        if (version < 2) {
            for (SinkingMedium medium : SinkingMedium.values()) {
                migrateDefault(
                        CONFIG_VALUES.get(medium),
                        MudPhysicsParameter.ITEM_PRESENTATION_MAXIMUM_TILT_DEGREES,
                        24.0D,
                        DroppedItemPhysicsProfile.defaultsFor(medium)
                                .presentationMaximumTiltDegrees());
            }
        }
        DROPPED_ITEM_PROFILE_VERSION.set(2);
        SPEC.save();
    }

    private static void migrateWallStainDefaults() {
        if (!SPEC.isLoaded() || WALL_STAIN_PROFILE_VERSION.get() >= 2) {
            return;
        }
        int version = WALL_STAIN_PROFILE_VERSION.get();
        if (version < 1) {
            migrateValue(WALL_STAIN_MINIMUM_SOURCE_COVERAGE, 0.18D, 0.25D);
        }
        if (version < 2) {
            migrateValue(WALL_STAIN_MINIMUM_SOURCE_COVERAGE, 0.25D, 0.35D);
        }
        WALL_STAIN_PROFILE_VERSION.set(2);
        SPEC.save();
    }

    private static void migrateValue(
            ModConfigSpec.DoubleValue configured, double previousDefault, double nextDefault) {
        if (Math.abs(configured.get() - previousDefault) <= 1.0E-9D) {
            configured.set(nextDefault);
        }
    }

    private static void migrateDefault(
            Map<MudPhysicsParameter, ModConfigSpec.DoubleValue> values,
            MudPhysicsParameter parameter,
            double previousDefault,
            double nextDefault) {
        ModConfigSpec.DoubleValue configured = values.get(parameter);
        if (configured != null && Math.abs(configured.get() - previousDefault) <= 1.0E-9D) {
            configured.set(nextDefault);
        }
    }

    private static void rebuildRuntimeProfiles(boolean readConfig) {
        boolean loaded = readConfig && SPEC.isLoaded();
        footprintPermanent = loaded && FOOTPRINT_PERMANENT.get();
        maximumFootprints = loaded ? FOOTPRINT_MAXIMUM.get() : 768;
        footprintRainWash = !loaded || FOOTPRINT_RAIN_WASH.get();
        footprintLifetimeTicks = (loaded
                ? SURFACE_STAIN_LIFETIME_SECONDS.get() : 1800) * 20;
        footprintRainWashStep = 1.0F / (loaded
                ? FOOTPRINT_RAIN_WASH_SECONDS.get() : 7);
        footprintTrailDistanceBlocks = loaded
                ? FOOTPRINT_TRAIL_DISTANCE_BLOCKS.get().floatValue() : 0.62F;
        mudTuningWandInteractionRange = loaded
                ? MUD_TUNING_WAND_INTERACTION_RANGE.get()
                : MUD_TUNING_WAND_DEFAULT_INTERACTION_RANGE;
        ropeMaximumDragDistance = loaded
                ? ROPE_MAXIMUM_DRAG_DISTANCE.get() : 6.0D;
        eruptionMaximumActivePerLevel = loaded
                ? ERUPTION_MAXIMUM_ACTIVE_PER_LEVEL.get() : 48;
        entityCoverageEnabled = loaded && ENTITY_COVERAGE_ENABLED.get();
        entityCoverageAutomaticFadeTicks = (loaded
                ? ENTITY_COVERAGE_AUTOMATIC_FADE_SECONDS.get() : 30) * 20;
        coverageEdgeBlendMinimumSource = loaded
                ? COVERAGE_EDGE_BLEND_MINIMUM_SOURCE.get().floatValue()
                : 0.16F;
        coverageEdgeBlendMinimumDifference = loaded
                ? COVERAGE_EDGE_BLEND_MINIMUM_DIFFERENCE.get().floatValue()
                : 0.08F;
        coverageEdgeBlendFirstRetention = loaded
                ? COVERAGE_EDGE_BLEND_FIRST_RETENTION.get().floatValue()
                : 0.76F;
        coverageEdgeBlendSecondChance = loaded
                ? COVERAGE_EDGE_BLEND_SECOND_CHANCE.get().floatValue()
                : 0.20F;
        coverageEdgeBlendSecondRetention = loaded
                ? COVERAGE_EDGE_BLEND_SECOND_RETENTION.get().floatValue()
                : 0.44F;
        wallStainUpdateIntervalTicks = loaded ? WALL_STAIN_UPDATE_INTERVAL_TICKS.get() : 1;
        wallStainTransferAmount = loaded ? WALL_STAIN_TRANSFER_AMOUNT.get().floatValue() : 0.012F;
        wallStainMinimumSourceCoverage = loaded
                ? WALL_STAIN_MINIMUM_SOURCE_COVERAGE.get().floatValue()
                : 0.35F;
        wallTransferEdgeFadeFirstContrastRetention = loaded
                ? WALL_TRANSFER_EDGE_FADE_FIRST_CONTRAST_RETENTION.get().floatValue()
                : 0.38F;
        wallTransferEdgeFadeSecondContrastRetention = Math.max(
                wallTransferEdgeFadeFirstContrastRetention,
                loaded
                        ? WALL_TRANSFER_EDGE_FADE_SECOND_CONTRAST_RETENTION.get().floatValue()
                        : 0.72F);
        wallStainImprintOpacityScale = loaded
                ? WALL_STAIN_IMPRINT_OPACITY_SCALE.get().floatValue()
                : 0.95F;
        wallStainOverlapBlend = loaded ? WALL_STAIN_OVERLAP_BLEND.get().floatValue() : 0.65F;
        wallStainEdgeSpreadChance = loaded ? WALL_STAIN_EDGE_SPREAD_CHANCE.get().floatValue() : 0.72F;
        wallStainCornerWrapMaxPixels = loaded ? WALL_STAIN_CORNER_WRAP_MAX_PIXELS.get() : 3;
        wallStainCornerWrapRetention = loaded
                ? WALL_STAIN_CORNER_WRAP_RETENTION.get().floatValue()
                : 0.80F;
        wallStainCornerWrapRoughness = loaded
                ? WALL_STAIN_CORNER_WRAP_ROUGHNESS.get().floatValue()
                : 0.72F;
        wallStainDripIntervalTicks = loaded ? WALL_STAIN_DRIP_INTERVAL_TICKS.get() : 16;
        wallStainDripChance = loaded ? WALL_STAIN_DRIP_CHANCE.get().floatValue() : 0.24F;
        wallStainDripRetention = loaded ? WALL_STAIN_DRIP_RETENTION.get().floatValue() : 0.84F;
        wallStainFlowDurationTicks = loaded ? WALL_STAIN_FLOW_DURATION_TICKS.get() : 48;
        wallStainFlowPreviewCells = loaded ? WALL_STAIN_FLOW_PREVIEW_CELLS.get().floatValue() : 2.75F;
        wallStainCeilingDripCount = loaded ? WALL_STAIN_CEILING_DRIP_COUNT.get() : 4;
        wallStainCeilingDripMaxLength = loaded
                ? WALL_STAIN_CEILING_DRIP_MAX_LENGTH.get().floatValue()
                : 0.62F;
        wallStainCeilingDripLengthMultiplier = loaded
                ? WALL_STAIN_CEILING_DRIP_LENGTH_MULTIPLIER.get().floatValue()
                : 2.0F;
        wallStainCeilingDripMinLengthFactor = loaded
                ? WALL_STAIN_CEILING_DRIP_MIN_LENGTH_FACTOR.get().floatValue()
                : 0.18F;
        wallStainCeilingDripWidth = loaded ? WALL_STAIN_CEILING_DRIP_WIDTH.get().floatValue() : 0.040F;
        wallStainCeilingDripDetachChance = loaded
                ? WALL_STAIN_CEILING_DRIP_DETACH_CHANCE.get().floatValue()
                : 0.30F;
        wallStainCeilingDripCycleTicks = loaded ? WALL_STAIN_CEILING_DRIP_CYCLE_TICKS.get() : 90;
        wallStainFadeInTicks = loaded ? WALL_STAIN_FADE_IN_TICKS.get() : 6;
        mudSplashProfile = loaded
                ? new MudSplashProfile(
                        MUD_SPLASH_ENABLED.get(),
                        MUD_SPLASH_MINIMUM_IMPACT_SPEED.get(),
                        MUD_SPLASH_MAXIMUM_IMPACT_SPEED.get(),
                        MUD_SPLASH_BASE_DROPLETS.get(),
                        MUD_SPLASH_MAXIMUM_DROPLETS_PER_IMPACT.get(),
                        MUD_SPLASH_MAXIMUM_ACTIVE_DROPLETS.get(),
                        MUD_SPLASH_LAUNCH_SPEED.get(),
                        MUD_SPLASH_GRAVITY.get(),
                        MUD_SPLASH_DRAG.get(),
                        MUD_SPLASH_LIFETIME_TICKS.get(),
                        MUD_SPLASH_IMPACT_COOLDOWN_TICKS.get(),
                        MUD_SPLASH_STAIN_RADIUS.get().floatValue(),
                        MUD_SPLASH_STAIN_STRENGTH.get().floatValue(),
                        MUD_SPLASH_PLAYER_HIT_RADIUS.get().floatValue(),
                        MUD_SPLASH_PLAYER_STAIN_STRENGTH.get().floatValue(),
                        MUD_SPLASH_RENDER_DISTANCE.get())
                : MudSplashProfile.DEFAULT;
        waterGunProfile = loaded
                ? new WaterGunProfile(
                        WATER_GUN_CAPACITY.get(),
                        WATER_GUN_WATER_PER_TICK.get(),
                        WATER_GUN_INPUT_TIMEOUT_TICKS.get(),
                        WATER_GUN_SYNC_INTERVAL_TICKS.get(),
                        WATER_GUN_WASH_INTERVAL_TICKS.get(),
                        WATER_GUN_PRESSURE.get(),
                        WATER_GUN_GRAVITY.get(),
                        WATER_GUN_MAXIMUM_RANGE.get(),
                        WATER_GUN_SEGMENT_LENGTH.get(),
                        WATER_GUN_STREAM_WIDTH.get(),
                        WATER_GUN_FIRING_MOVEMENT_SCALE.get(),
                        WATER_GUN_RECOIL_DEGREES.get(),
                        WATER_GUN_BASE_WASH_RADIUS.get().floatValue(),
                        WATER_GUN_DISTANCE_SPREAD.get().floatValue(),
                        WATER_GUN_MAX_WASH_RADIUS.get().floatValue(),
                        WATER_GUN_WASH_AMOUNT.get().floatValue(),
                        WATER_GUN_RENDER_DISTANCE.get(),
                        WATER_GUN_SABLE_MAX_BLOCK_SAMPLES.get())
                : WaterGunProfile.DEFAULT;
        for (SinkingMedium medium : SinkingMedium.values()) {
            double[] values = MudPhysicsProfiles.defaultValues(medium);
            if (loaded) {
                for (Map.Entry<MudPhysicsParameter, ModConfigSpec.DoubleValue> entry : CONFIG_VALUES.get(medium).entrySet()) {
                    values[entry.getKey().ordinal()] = entry.getValue().get();
                }
            }
            values = MudPhysicsProfiles.sanitize(medium, values);
            RUNTIME_VALUES[medium.id()] = values;
            ORDINARY_PROFILES[medium.id()] = SinkingPhysicsProfile.fromValues(values);
            ADHESION_STRAND_PROFILES[medium.id()] = AdhesionStrandProfile.fromValues(values);
            HARVEST_PROFILES[medium.id()] = MudHarvestProfile.fromValues(values);
            DROPPED_ITEM_PROFILES[medium.id()] = DroppedItemPhysicsProfile.fromValues(values);
            ASSIMILATION_PROFILES[medium.id()] = AssimilationProfile.fromValues(values);
            ERUPTION_PROFILES[medium.id()] = MudEruptionProfile.fromValues(values);
            FLOW_PROFILES[medium.id()] = MudFlowProfile.fromValues(values);
            SCULK_MIRE_PROFILES[medium.id()] = SculkMireProfile.fromValues(values);
            TENDER_FLESH_PROFILES[medium.id()] = TenderFleshProfile.fromValues(values);
            if (medium == SinkingMedium.LIVING_SLIME) {
                livingSlimeProfile = LivingSlimePhysicsProfile.fromValues(values);
            }
            if (medium == SinkingMedium.SCULK_MIRE) {
                sculkMireProfile = SCULK_MIRE_PROFILES[medium.id()];
            }
            if (medium == SinkingMedium.TENDER_FLESH) {
                tenderFleshProfile = TENDER_FLESH_PROFILES[medium.id()];
            }
        }
        double[] resolvedTentacleValues = MudPhysicsProfiles.tentacleDefaultValues();
        if (loaded) {
            for (Map.Entry<MudPhysicsParameter, ModConfigSpec.DoubleValue> entry
                    : TENTACLE_CONFIG_VALUES.entrySet()) {
                resolvedTentacleValues[entry.getKey().ordinal()] = entry.getValue().get();
            }
        }
        tentacleValues = MudPhysicsProfiles.sanitizeTentacle(resolvedTentacleValues);
        tentacleProfile = TentaclePhysicsProfile.fromValues(tentacleValues);
        tentacleGrabProfile = TentacleGrabProfile.fromValues(tentacleValues);
    }
}
