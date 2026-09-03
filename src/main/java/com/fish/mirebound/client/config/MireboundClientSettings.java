package com.fish.mirebound.client.config;

import com.fish.mirebound.client.ContactGeometryMode;
import com.fish.mirebound.client.tuning.MudTuningHudElement;
import com.fish.mirebound.generation.MudTerrainGenerationType;
import com.fish.mirebound.generation.MudTerrainLakeSettings;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class MireboundClientSettings {
    private static final String FILE_NAME = "mirebound/mirebound-client.toml";
    public static final double DEFAULT_TUNING_HUD_OPACITY = 0.82D;
    public static final double DEFAULT_TUNING_CONTROLS_OPACITY = 0.82D;
    private static final ModConfigSpec.IntValue CROSS_SECTION_SIDES;
    private static final ModConfigSpec.DoubleValue RENDER_DISTANCE;
    private static final ModConfigSpec.DoubleValue LOD_DISTANCE;
    private static final ModConfigSpec.IntValue LOD_STRIDE;
    private static final ModConfigSpec.IntValue INTERPOLATION_TICKS;
    private static final ModConfigSpec.DoubleValue SURFACE_VARIATION;
    private static final ModConfigSpec.DoubleValue PULSE_AMPLITUDE;
    private static final ModConfigSpec.DoubleValue PULSE_SPEED;
    private static final ModConfigSpec.DoubleValue TIP_TAPER_START;
    private static final ModConfigSpec.DoubleValue TIP_RING_SCALE;
    private static final ModConfigSpec.DoubleValue TIP_LENGTH_SCALE;
    private static final ModConfigSpec.IntValue ROOT_CAP_RINGS;
    private static final ModConfigSpec.DoubleValue ROOT_CAP_LENGTH_SCALE;
    private static final ModConfigSpec.BooleanValue CAST_SHADER_SHADOWS;
    private static final ModConfigSpec.ConfigValue<String> GRAB_CAMERA_MODE;
    private static final ModConfigSpec.DoubleValue GRAB_CAMERA_STRENGTH;
    private static final ModConfigSpec.BooleanValue GRAB_WRAP_ENABLED;
    private static final ModConfigSpec.IntValue GRAB_WRAP_SEGMENTS;
    private static final ModConfigSpec.DoubleValue GRAB_WRAP_TURNS;
    private static final ModConfigSpec.DoubleValue GRAB_WRAP_RADIUS_SCALE;
    private static final ModConfigSpec.DoubleValue GRAB_WRAP_LENGTH_SCALE;
    private static final ModConfigSpec.DoubleValue GRAB_WRAP_STRAND_RADIUS_SCALE;
    private static final ModConfigSpec.DoubleValue GRAB_WRAP_WHOLE_BODY_TURNS_SCALE;
    private static final ModConfigSpec.IntValue GRAB_WRAP_STRANDS;
    // Kept as valid config keys so existing files migrate without a correction loop.
    private static final ModConfigSpec.IntValue GRAB_WRAP_LOCAL_STRANDS;
    private static final ModConfigSpec.IntValue GRAB_WRAP_WHOLE_BODY_STRANDS;
    private static final ModConfigSpec.BooleanValue MUD_SURFACE_ENABLED;
    private static final ModConfigSpec.BooleanValue MUD_SURFACE_PRECISE_MODEL_GEOMETRY;
    private static final ModConfigSpec.DoubleValue MUD_SURFACE_RENDER_DISTANCE;
    private static final ModConfigSpec.IntValue MUD_SURFACE_MAX_HOLES;
    private static final ModConfigSpec.IntValue MUD_SURFACE_MAX_TRENCH_CELLS;
    private static final ModConfigSpec.IntValue MUD_SURFACE_MAX_SIDE_IMPRINTS;
    private static final ModConfigSpec.IntValue MUD_SURFACE_MAX_SIDE_CELLS;
    private static final ModConfigSpec.IntValue MUD_SURFACE_SCHEMA_VERSION;
    private static final ModConfigSpec.IntValue MUD_SURFACE_MAX_BUBBLES;
    private static final ModConfigSpec.IntValue MUD_SURFACE_AMBIENT_PROBES;
    private static final ModConfigSpec.IntValue MUD_SURFACE_AMBIENT_INTERVAL;
    private static final ModConfigSpec.DoubleValue MUD_SURFACE_ARMOR_RADIUS_BONUS;
    private static final ModConfigSpec.DoubleValue MUD_SURFACE_ARMOR_RADIUS_MAXIMUM;
    private static final ModConfigSpec.BooleanValue INSECT_MOUND_SURFACE_ENABLED;
    private static final ModConfigSpec.IntValue INSECT_MOUND_MAX_PATCHES;
    private static final ModConfigSpec.IntValue INSECT_MOUND_SCAN_INTERVAL;
    private static final ModConfigSpec.IntValue INSECT_MOUND_LARVAE_PER_FACE;
    private static final ModConfigSpec.IntValue INSECT_MOUND_MIN_LENGTH;
    private static final ModConfigSpec.IntValue INSECT_MOUND_MAX_LENGTH;
    private static final ModConfigSpec.DoubleValue INSECT_MOUND_BASE_ACTIVITY;
    private static final ModConfigSpec.DoubleValue INSECT_MOUND_SOUND_VOLUME;
    private static final ModConfigSpec.BooleanValue PRECISE_UV_OWNERSHIP;
    private static final ModConfigSpec.BooleanValue ANIMATED_CONTACT_GEOMETRY;
    private static final ModConfigSpec.ConfigValue<String> CONTACT_GEOMETRY_MODE;
    private static final ModConfigSpec.BooleanValue SPLASH_EFFECTS;
    private static final ModConfigSpec.BooleanValue ERUPTION_EFFECTS;
    private static final ModConfigSpec.BooleanValue SURFACE_DECALS;
    private static final ModConfigSpec.BooleanValue PLAYER_COVERAGE_RENDERING;
    private static final ModConfigSpec.BooleanValue ENTITY_COVERAGE_RENDERING;
    private static final ModConfigSpec.BooleanValue MUD_SCREEN_EFFECTS;
    private static final ModConfigSpec.BooleanValue ASSIMILATION_SCREEN_EFFECTS;
    private static final ModConfigSpec.BooleanValue SWARM_SCREEN_EFFECTS;
    private static final ModConfigSpec.BooleanValue TENTACLE_RENDERING;
    private static final ModConfigSpec.DoubleValue TUNING_HUD_OPACITY;
    private static final ModConfigSpec.DoubleValue TUNING_HUD_CONTROLS_OPACITY;
    private static final EnumMap<MudTuningHudElement, ModConfigSpec.BooleanValue>
            TUNING_HUD_ELEMENT_ENABLED = new EnumMap<>(MudTuningHudElement.class);
    private static final EnumMap<MudTuningHudElement, ModConfigSpec.DoubleValue>
            TUNING_HUD_ELEMENT_X = new EnumMap<>(MudTuningHudElement.class);
    private static final EnumMap<MudTuningHudElement, ModConfigSpec.DoubleValue>
            TUNING_HUD_ELEMENT_Y = new EnumMap<>(MudTuningHudElement.class);
    private static final EnumMap<MudTuningHudElement, ModConfigSpec.DoubleValue>
            TUNING_HUD_ELEMENT_SCALE = new EnumMap<>(MudTuningHudElement.class);
    private static final ModConfigSpec.ConfigValue<String> TUNING_TARGET_COLOR;
    private static final ModConfigSpec.ConfigValue<String> TUNING_POINT_ONE_COLOR;
    private static final ModConfigSpec.ConfigValue<String> TUNING_POINT_TWO_COLOR;
    private static final ModConfigSpec.ConfigValue<String> TUNING_MODIFIED_COLOR;
    private static final ModConfigSpec.ConfigValue<String> TUNING_FLOW_COLOR;
    private static final ModConfigSpec.ConfigValue<String> TUNING_INCOMPATIBLE_COLOR;
    private static final ModConfigSpec.ConfigValue<String> TUNING_CONVERTED_DEFAULT_COLOR;
    private static final ModConfigSpec.ConfigValue<String> TUNING_CONVERTED_MODIFIED_COLOR;
    private static final ModConfigSpec.IntValue TUNING_LAST_MODE;
    private static final ModConfigSpec.DoubleValue TUNING_SPATIAL_PLACEMENT_DISTANCE;
    private static final ModConfigSpec.BooleanValue TUNING_TENTACLE_AUTO_SNAP;
    private static final ModConfigSpec.IntValue TUNING_GENERATION_RADIUS;
    private static final ModConfigSpec.IntValue TUNING_GENERATION_THICKNESS;
    private static final ModConfigSpec.DoubleValue TUNING_GENERATION_EDGE_ROUGHNESS;
    private static final ModConfigSpec.IntValue TUNING_GENERATION_HEIGHT_TOLERANCE;
    private static final ModConfigSpec.IntValue TUNING_GENERATION_SEED;
    private static final ModConfigSpec.BooleanValue TUNING_GENERATION_SAME_SOURCE_ONLY;
    private static final ModConfigSpec.IntValue TUNING_GENERATION_TYPE;
    private static final ModConfigSpec.IntValue TUNING_GENERATION_LAKE_HORIZONTAL_RADIUS;
    private static final ModConfigSpec.IntValue TUNING_GENERATION_LAKE_VERTICAL_RADIUS;
    private static final ModConfigSpec.IntValue TUNING_GENERATION_LAKE_SURFACE_HEIGHT_PIXELS;
    private static final ModConfigSpec.IntValue TUNING_GENERATION_LAKE_SEED;
    private static final ModConfigSpec.ConfigValue<String> TUNING_GENERATION_LAKE_SHELL_BLOCK;
    private static final ModConfigSpec.ConfigValue<String> TUNING_GENERATION_LAKE_INNER_BLOCK;
    private static final ModConfigSpec SPEC;
    private static int tuningColorRevision;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("procedural_tentacles");
        CROSS_SECTION_SIDES = builder
                .comment("Tube cross-section sides. Eight gives a clearly volumetric silhouette.")
                .defineInRange("cross_section_sides", 8, 6, 12);
        RENDER_DISTANCE = builder
                .comment("Maximum distance in blocks from the nearest part of a tentacle, not only its root.")
                .defineInRange("render_distance", 96.0D, 8.0D, 512.0D);
        LOD_DISTANCE = builder
                .comment("Distance where the renderer starts reducing tube cross-section detail.")
                .defineInRange("lod_distance", 20.0D, 4.0D, 96.0D);
        LOD_STRIDE = builder
                .comment("Cross-section detail divisor used beyond the LOD distance. Centerline points are preserved.")
                .defineInRange("lod_stride", 2, 1, 4);
        INTERPOLATION_TICKS = builder
                .comment("Minimum client interpolation window for networked control points.")
                .defineInRange("interpolation_ticks", 2, 1, 10);
        SURFACE_VARIATION = builder
                .comment("Stable axial radius variation used to avoid a perfectly machined tube.")
                .defineInRange("surface_variation", 0.055D, 0.0D, 0.20D);
        PULSE_AMPLITUDE = builder
                .comment("Subtle radial breathing amplitude.")
                .defineInRange("pulse_amplitude", 0.025D, 0.0D, 0.15D);
        PULSE_SPEED = builder
                .comment("Radial breathing speed in radians per game tick.")
                .defineInRange("pulse_speed", 0.065D, 0.0D, 0.40D);
        TIP_TAPER_START = builder
                .comment("Normalized body position where the final visual taper begins.")
                .defineInRange("tip_taper_start", 0.72D, 0.40D, 0.95D);
        TIP_RING_SCALE = builder
                .comment("Radius multiplier of the last tube ring before the pointed cap.")
                .defineInRange("tip_ring_scale", 0.42D, 0.10D, 1.0D);
        TIP_LENGTH_SCALE = builder
                .comment("Pointed cap length as a multiple of the configured physical tip radius.")
                .defineInRange("tip_length_scale", 2.2D, 0.25D, 5.0D);
        ROOT_CAP_RINGS = builder
                .comment("Latitude rings used by the low-poly hemispherical root cap.")
                .defineInRange("root_cap_rings", 2, 1, 4);
        ROOT_CAP_LENGTH_SCALE = builder
                .comment("Root hemisphere length along the backward first-segment direction.")
                .defineInRange("root_cap_length_scale", 1.0D, 0.50D, 1.75D);
        CAST_SHADER_SHADOWS = builder
                .comment("Submit procedural tentacles to compatible shader shadow-map passes.")
                .define("cast_shader_shadows", true);
        GRAB_CAMERA_MODE = builder
                .comment("Grab camera response: immersive, smooth, or off.")
                .define("grab_camera_mode", "immersive", value -> value instanceof String text
                        && (text.equalsIgnoreCase("immersive")
                                || text.equalsIgnoreCase("smooth") || text.equalsIgnoreCase("off")));
        GRAB_CAMERA_STRENGTH = builder
                .comment("Overall multiplier for camera motion while the local player is grabbed.")
                .defineInRange("grab_camera_strength", 1.0D, 0.0D, 2.0D);
        GRAB_WRAP_ENABLED = builder
                .comment("Render a tapered continuation of the tip around the exact grabbed body region.")
                .define("grab_wrap_enabled", true);
        GRAB_WRAP_SEGMENTS = builder
                .comment("Control points used by the local grab wrap. Only active grabs allocate them.")
                .defineInRange("grab_wrap_segments", 12, 4, 32);
        GRAB_WRAP_TURNS = builder
                .comment("Turns made by the visual tip around the grabbed body region.")
                .defineInRange("grab_wrap_turns", 1.15D, 0.25D, 3.0D);
        GRAB_WRAP_RADIUS_SCALE = builder
                .comment("Multiplier for the wrap radius derived from player and tentacle size.")
                .defineInRange("grab_wrap_radius_scale", 1.0D, 0.35D, 2.5D);
        GRAB_WRAP_LENGTH_SCALE = builder
                .comment("Axial travel of the wrap along the grabbed body region.")
                .defineInRange("grab_wrap_length_scale", 1.0D, 0.0D, 3.0D);
        GRAB_WRAP_STRAND_RADIUS_SCALE = builder
                .comment("Thickness of the wrapping strand after it separates from the physical tip.")
                .defineInRange("grab_wrap_strand_radius_scale", 0.92D, 0.25D, 1.75D);
        GRAB_WRAP_WHOLE_BODY_TURNS_SCALE = builder
                .comment("Extra turns used when a large tip wraps the whole player instead of one limb.")
                .defineInRange("grab_wrap_whole_body_turns_scale", 1.85D, 1.0D, 3.5D);
        GRAB_WRAP_STRANDS = builder
                .comment("Terminal strands used by a grab wrap. One keeps the physical tip visually continuous.")
                .defineInRange("grab_wrap_strands", 1, 1, 3);
        GRAB_WRAP_LOCAL_STRANDS = builder
                .comment("Legacy key retained for config migration; grab_wrap_strands controls rendering.")
                .defineInRange("grab_wrap_local_strands", 1, 1, 4);
        GRAB_WRAP_WHOLE_BODY_STRANDS = builder
                .comment("Legacy key retained for config migration; grab_wrap_strands controls rendering.")
                .defineInRange("grab_wrap_whole_body_strands", 1, 1, 6);
        builder.pop();
        builder.push("surface_effects");
        MUD_SURFACE_ENABLED = builder
                .comment("Render sinking holes, raised rims, and procedural bubbles.")
                .define("enabled", true);
        MUD_SURFACE_PRECISE_MODEL_GEOMETRY = builder
                .comment("Fit adaptive mud effects and surface decals to final rendered quads and texture alpha "
                        + "instead of the low-cost voxel/rectangle fallback. This is client-only and optional.")
                .define("precise_model_geometry", true);
        MUD_SURFACE_RENDER_DISTANCE = builder
                .comment("Maximum distance in blocks for procedural mud surface effects.")
                .defineInRange("render_distance", 32.0D, 8.0D, 128.0D);
        MUD_SURFACE_MAX_HOLES = builder
                .comment("Maximum simultaneously retained player sinking holes.")
                .defineInRange("max_holes", 32, 1, 128);
        MUD_SURFACE_MAX_TRENCH_CELLS = builder
                .comment("Maximum retained 1/16-grid surface cells per independent player imprint.")
                .defineInRange("max_trench_cells_per_hole", 12288, 64, 32768);
        MUD_SURFACE_MAX_SIDE_IMPRINTS = builder
                .comment("Maximum retained player imprints on exposed side and underside mud faces.")
                .defineInRange("max_side_imprints", 96, 0, 512);
        MUD_SURFACE_MAX_SIDE_CELLS = builder
                .comment("Global retained 1/16-grid cell budget for side and underside mud-face deformation.")
                .defineInRange("max_side_cells", 8192, 0, 65536);
        MUD_SURFACE_MAX_BUBBLES = builder
                .comment("Maximum simultaneously retained procedural bubbles.")
                .defineInRange("max_bubbles", 160, 0, 1024);
        MUD_SURFACE_AMBIENT_PROBES = builder
                .comment("Sparse nearby block probes per ambient update. Set to 0 to disable ambient bubbles.")
                .defineInRange("ambient_probes", 24, 0, 128);
        MUD_SURFACE_AMBIENT_INTERVAL = builder
                .comment("Ticks between sparse nearby searches for ambient mud bubbles.")
                .defineInRange("ambient_interval_ticks", 10, 2, 100);
        MUD_SURFACE_ARMOR_RADIUS_BONUS = builder
                .comment("Extra hole-radius bonus added after measuring the submerged armor shell.")
                .defineInRange("armor_radius_bonus_per_piece", 0.025D, 0.0D, 0.15D);
        MUD_SURFACE_ARMOR_RADIUS_MAXIMUM = builder
                .comment("Maximum hole-radius bonus from the armor region intersecting the mud surface.")
                .defineInRange("armor_radius_maximum_bonus", 0.18D, 0.0D, 0.50D);
        MUD_SURFACE_SCHEMA_VERSION = builder
                .comment("Internal migration marker for mud-surface visual budgets.")
                .defineInRange("_schema_version", 0, 0, 16);
        builder.push("insect_mound");
        INSECT_MOUND_SURFACE_ENABLED = builder
                .comment("Render bounded procedural larvae and breathing patches on exposed insect-mound faces.")
                .define("enabled", true);
        INSECT_MOUND_MAX_PATCHES = builder
                .comment("Maximum exposed insect-mound faces retained around the local player.")
                .defineInRange("max_surface_patches", 96, 0, 512);
        INSECT_MOUND_SCAN_INTERVAL = builder
                .comment("Ticks between nearby insect-mound surface scans.")
                .defineInRange("scan_interval_ticks", 8, 2, 40);
        INSECT_MOUND_LARVAE_PER_FACE = builder
                .comment("Maximum procedural pixel trails allowed to start on one exposed block face.")
                .defineInRange("larvae_per_face", 4, 0, 6);
        INSECT_MOUND_MIN_LENGTH = builder
                .comment("Minimum length in 1/16 surface pixels for one crawling trail.")
                .defineInRange("min_length_pixels", 3, 2, 16);
        INSECT_MOUND_MAX_LENGTH = builder
                .comment("Maximum length in 1/16 surface pixels for one crawling trail.")
                .defineInRange("max_length_pixels", 11, 3, 24);
        INSECT_MOUND_BASE_ACTIVITY = builder
                .comment("Idle surface activity before a nearby player accumulates swarm strength.")
                .defineInRange("base_activity", 0.35D, 0.0D, 1.0D);
        INSECT_MOUND_SOUND_VOLUME = builder
                .comment("Maximum volume of throttled insect-mound rustling. Set to 0 to disable it.")
                .defineInRange("sound_volume", 0.12D, 0.0D, 1.0D);
        builder.pop();
        builder.pop();
        builder.push("coverage");
        PRECISE_UV_OWNERSHIP = builder
                .comment("Track physical face ownership for reused or mirrored equipment UV pixels. "
                        + "Disable to restore the legacy any-contact texture-pixel behavior.")
                .define("precise_uv_ownership", true);
        ANIMATED_CONTACT_GEOMETRY = builder
                .comment("Client visuals only: fit local pollution, washing, armor, cape, and mud-surface effects "
                        + "to the final animated player model. Disabling this does not change server contact rules.")
                .define("animated_contact_geometry", true);
        CONTACT_GEOMETRY_MODE = builder
                .comment("Animated contact source: model_part (default and fastest), sodium_vertices (exact rendered vertices), or auto.")
                .define("contact_geometry_mode", ContactGeometryMode.MODEL_PART.serializedName(),
                        value -> value instanceof String text
                                && ContactGeometryMode.byName(text) != null);
        builder.pop();
        builder.push("visual_effects");
        SPLASH_EFFECTS = builder
                .comment("Client visuals only: animate mud droplets, fountain columns, and trails. "
                        + "Server hit and pollution rules are unaffected.")
                .define("splashes", true);
        ERUPTION_EFFECTS = builder
                .comment("Render synchronized mud eruption vents and their surface deformation.")
                .define("eruption_vents", true);
        SURFACE_DECALS = builder
                .comment("Render decorative wall stains, footprints, sliding flows, and ceiling strands.")
                .define("surface_decals", true);
        PLAYER_COVERAGE_RENDERING = builder
                .comment("Render mud and assimilation coverage on player skins, capes, and equipment.")
                .define("player_coverage", true);
        ENTITY_COVERAGE_RENDERING = builder
                .comment("Render mud coverage on non-player living entities.")
                .define("entity_coverage", true);
        MUD_SCREEN_EFFECTS = builder
                .comment("Render first-person mud masks and mud-clod screen impacts.")
                .define("mud_screen", true);
        ASSIMILATION_SCREEN_EFFECTS = builder
                .comment("Render assimilation masks and soul-state screen pulses.")
                .define("assimilation_screen", true);
        SWARM_SCREEN_EFFECTS = builder
                .comment("Render insect crawlers and silk on the screen.")
                .define("swarm_screen", true);
        TENTACLE_RENDERING = builder
                .comment("Render procedural tentacle bodies and selection highlights.")
                .define("tentacles", true);
        builder.pop();
        builder.push("tuning_wand");
        TUNING_HUD_OPACITY = builder
                .comment("Background opacity of tuning wand HUD panels.")
                .defineInRange("hud_opacity", DEFAULT_TUNING_HUD_OPACITY, 0.20D, 1.0D);
        TUNING_HUD_CONTROLS_OPACITY = builder
                .comment("Background opacity of tuning wand control hints.")
                .defineInRange("controls_opacity", DEFAULT_TUNING_CONTROLS_OPACITY,
                        0.20D, 1.0D);
        for (MudTuningHudElement element : MudTuningHudElement.values()) {
            builder.push(element.id());
            TUNING_HUD_ELEMENT_ENABLED.put(element, builder
                    .comment("Show this tuning-wand HUD group.")
                    .define("enabled", true));
            TUNING_HUD_ELEMENT_X.put(element, builder
                    .comment("Horizontal normalized position for this HUD group.")
                    .defineInRange("x", element.defaultX(), 0.0D, 1.0D));
            TUNING_HUD_ELEMENT_Y.put(element, builder
                    .comment("Vertical normalized position for this HUD group.")
                    .defineInRange("y", element.defaultY(), 0.0D, 1.0D));
            TUNING_HUD_ELEMENT_SCALE.put(element, builder
                    .comment("Independent scale for this HUD group.")
                    .defineInRange("scale", element.defaultScale(), 0.50D, 2.0D));
            builder.pop();
        }
        TUNING_TARGET_COLOR = builder
                .comment("Six-digit RGB color for the current target outline.")
                .define("target_color", "F5C542", MireboundClientSettings::validHexColor);
        TUNING_POINT_ONE_COLOR = builder
                .comment("Six-digit RGB color for selection point one.")
                .define("point_one_color", "FF291F", MireboundClientSettings::validHexColor);
        TUNING_POINT_TWO_COLOR = builder
                .comment("Six-digit RGB color for selection point two.")
                .define("point_two_color", "2E7AFF", MireboundClientSettings::validHexColor);
        TUNING_MODIFIED_COLOR = builder
                .comment("Six-digit RGB color for modified native mud.")
                .define("modified_color", "FF34A8", MireboundClientSettings::validHexColor);
        TUNING_FLOW_COLOR = builder
                .comment("Six-digit RGB color for finite-flow mud.")
                .define("flow_color", "123DB8", MireboundClientSettings::validHexColor);
        TUNING_INCOMPATIBLE_COLOR = builder
                .comment("Six-digit RGB color for incompatible blocks.")
                .define("incompatible_color", "FF241A", MireboundClientSettings::validHexColor);
        TUNING_CONVERTED_DEFAULT_COLOR = builder
                .comment("Six-digit RGB color for converted blocks using default behavior.")
                .define("converted_default_color", "FFD02A", MireboundClientSettings::validHexColor);
        TUNING_CONVERTED_MODIFIED_COLOR = builder
                .comment("Six-digit RGB color for converted blocks with local overrides.")
                .define("converted_modified_color", "FF6B16", MireboundClientSettings::validHexColor);
        TUNING_LAST_MODE = builder
                .comment("Last selected tuning wand mode. Stored client-side only.")
                .defineInRange("last_mode", 0, 0, 5);
        TUNING_SPATIAL_PLACEMENT_DISTANCE = builder
                .comment("Camera-relative placement distance for wand-spawned world objects.")
                .defineInRange("spatial_placement_distance", 8.0D, 1.0D, 64.0D);
        TUNING_TENTACLE_AUTO_SNAP = builder
                .comment("Snap the wand beam and selection highlight to a tentacle under the crosshair.")
                .define("tentacle_auto_snap", true);
        builder.push("terrain_generation");
        TUNING_GENERATION_TYPE = builder
                .comment("Selected terrain generation shape. Stored client-side only.")
                .defineInRange("type", 1, 0,
                        MudTerrainGenerationType.values().length - 1);
        TUNING_GENERATION_RADIUS = builder
                .comment("Radius of the tuning wand's experimental surface deposit preview.")
                .defineInRange("radius", 12, 2, 48);
        TUNING_GENERATION_THICKNESS = builder
                .comment("Maximum converted layers at the center of a generated deposit.")
                .defineInRange("thickness", 3, 1, 8);
        TUNING_GENERATION_EDGE_ROUGHNESS = builder
                .comment("Continuous erosion applied to the edge of generated deposits.")
                .defineInRange("edge_roughness", 0.55D, 0.0D, 1.0D);
        TUNING_GENERATION_HEIGHT_TOLERANCE = builder
                .comment("Maximum terrain height difference from the targeted center.")
                .defineInRange("height_tolerance", 6, 0, 24);
        TUNING_GENERATION_SEED = builder
                .comment("Deterministic seed shared by preview and server generation.")
                .defineInRange("seed", 92821, 0, 2_000_000_000);
        TUNING_GENERATION_SAME_SOURCE_ONLY = builder
                .comment("Only convert blocks matching the targeted source block type.")
                .define("same_source_only", false);
        builder.push("lake_pool");
        TUNING_GENERATION_LAKE_HORIZONTAL_RADIUS = builder
                .comment("Horizontal radius of the enclosed lake-style volume.")
                .defineInRange("horizontal_radius", 8, 2, 24);
        TUNING_GENERATION_LAKE_VERTICAL_RADIUS = builder
                .comment("Vertical radius of the enclosed lake-style volume.")
                .defineInRange("vertical_radius", 4, 1, 12);
        TUNING_GENERATION_LAKE_SURFACE_HEIGHT_PIXELS = builder
                .comment("Height in pixels of the uppermost mud cell in each pool column.")
                .defineInRange(
                        "surface_height_pixels",
                        MudTerrainLakeSettings.DEFAULT_SURFACE_HEIGHT_PIXELS,
                        MudTerrainLakeSettings.MINIMUM_SURFACE_HEIGHT_PIXELS,
                        MudTerrainLakeSettings.MAXIMUM_SURFACE_HEIGHT_PIXELS);
        TUNING_GENERATION_LAKE_SEED = builder
                .comment("Deterministic lake shape seed shared by preview and server generation.")
                .defineInRange("seed", 92821, 0, 2_000_000_000);
        TUNING_GENERATION_LAKE_SHELL_BLOCK = builder
                .comment("Outer shell block id. minecraft:air preserves existing blocks.")
                .define("shell_block", "minecraft:air",
                        MireboundClientSettings::validResourceLocation);
        TUNING_GENERATION_LAKE_INNER_BLOCK = builder
                .comment("Inner mud block id. minecraft:air converts compatible existing blocks.")
                .define("inner_block", "minecraft:air",
                        MireboundClientSettings::validResourceLocation);
        builder.pop();
        builder.pop();
        builder.pop();
        SPEC = builder.build();
    }

    private MireboundClientSettings() {
    }

    public static void register(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC, FILE_NAME);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (ignored, parent) -> new MireboundClientConfigScreen(parent));
        modBus.addListener(MireboundClientSettings::onConfigLoading);
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getType() != ModConfig.Type.CLIENT
                || !FILE_NAME.equals(event.getConfig().getFileName())
                || MUD_SURFACE_SCHEMA_VERSION.get() >= 2) {
            return;
        }
        int schemaVersion = MUD_SURFACE_SCHEMA_VERSION.get();
        if (schemaVersion < 1 && MUD_SURFACE_MAX_TRENCH_CELLS.get() == 768) {
            MUD_SURFACE_MAX_TRENCH_CELLS.set(6144);
        }
        if (schemaVersion < 1
                && Math.abs(MUD_SURFACE_ARMOR_RADIUS_MAXIMUM.get() - 0.10D) <= 1.0E-9D) {
            MUD_SURFACE_ARMOR_RADIUS_MAXIMUM.set(0.18D);
        }
        if (schemaVersion < 2 && MUD_SURFACE_MAX_TRENCH_CELLS.get() == 6144) {
            MUD_SURFACE_MAX_TRENCH_CELLS.set(12288);
        }
        MUD_SURFACE_SCHEMA_VERSION.set(2);
        tuningColorRevision++;
        SPEC.save();
    }

    public static boolean mudSurfaceEnabled() {
        return MUD_SURFACE_ENABLED.get();
    }

    public static boolean clientOptionEnabled(ClientOption option) {
        return optionValue(option).get();
    }

    public static EnumMap<ClientOption, Boolean> clientOptions() {
        EnumMap<ClientOption, Boolean> options = new EnumMap<>(ClientOption.class);
        for (ClientOption option : ClientOption.values()) {
            options.put(option, clientOptionEnabled(option));
        }
        return options;
    }

    public static void applyClientOptions(Map<ClientOption, Boolean> options) {
        for (ClientOption option : ClientOption.values()) {
            Boolean value = options.get(option);
            if (value != null) {
                optionValue(option).set(value);
            }
        }
        SPEC.save();
    }

    private static ModConfigSpec.BooleanValue optionValue(ClientOption option) {
        return switch (option) {
            case SURFACE_EFFECTS -> MUD_SURFACE_ENABLED;
            case SPLASH_EFFECTS -> SPLASH_EFFECTS;
            case ERUPTION_EFFECTS -> ERUPTION_EFFECTS;
            case SURFACE_DECALS -> SURFACE_DECALS;
            case INSECT_SURFACE -> INSECT_MOUND_SURFACE_ENABLED;
            case TENTACLES -> TENTACLE_RENDERING;
            case PLAYER_COVERAGE -> PLAYER_COVERAGE_RENDERING;
            case ENTITY_COVERAGE -> ENTITY_COVERAGE_RENDERING;
            case MUD_SCREEN -> MUD_SCREEN_EFFECTS;
            case ASSIMILATION_SCREEN -> ASSIMILATION_SCREEN_EFFECTS;
            case SWARM_SCREEN -> SWARM_SCREEN_EFFECTS;
            case PRECISE_MODEL_GEOMETRY -> MUD_SURFACE_PRECISE_MODEL_GEOMETRY;
            case ANIMATED_CONTACT_GEOMETRY -> ANIMATED_CONTACT_GEOMETRY;
            case TENTACLE_SHADER_SHADOWS -> CAST_SHADER_SHADOWS;
        };
    }

    public static boolean mudSurfacePreciseModelGeometry() {
        return MUD_SURFACE_PRECISE_MODEL_GEOMETRY.get();
    }

    public static void setMudSurfacePreciseModelGeometry(boolean enabled) {
        MUD_SURFACE_PRECISE_MODEL_GEOMETRY.set(enabled);
        SPEC.save();
    }

    public static boolean preciseUvOwnership() {
        return PRECISE_UV_OWNERSHIP.get();
    }

    public static boolean animatedContactGeometry() {
        return ANIMATED_CONTACT_GEOMETRY.get();
    }

    public static ContactGeometryMode contactGeometryMode() {
        ContactGeometryMode mode = ContactGeometryMode.byName(CONTACT_GEOMETRY_MODE.get());
        return mode == null ? ContactGeometryMode.MODEL_PART : mode;
    }

    public static void setContactGeometryMode(ContactGeometryMode mode) {
        CONTACT_GEOMETRY_MODE.set(mode.serializedName());
        SPEC.save();
    }

    public static void setAnimatedContactGeometry(boolean enabled) {
        ANIMATED_CONTACT_GEOMETRY.set(enabled);
        SPEC.save();
    }

    public static void setPreciseUvOwnership(boolean enabled) {
        PRECISE_UV_OWNERSHIP.set(enabled);
        SPEC.save();
    }

    public static boolean tuningHudElementEnabled(MudTuningHudElement element) {
        return element == MudTuningHudElement.CENTER
                || value(TUNING_HUD_ELEMENT_ENABLED, element).get();
    }

    public static double tuningHudOpacity() {
        return TUNING_HUD_OPACITY.get();
    }

    public static double tuningHudControlsOpacity() {
        return TUNING_HUD_CONTROLS_OPACITY.get();
    }

    public static void setTuningHudOpacity(double value) {
        TUNING_HUD_OPACITY.set(clamp(value, 0.20D, 1.0D));
        SPEC.save();
    }

    public static void setTuningHudControlsOpacity(double value) {
        TUNING_HUD_CONTROLS_OPACITY.set(clamp(value, 0.20D, 1.0D));
        SPEC.save();
    }

    public static void setTuningHudElementEnabled(
            MudTuningHudElement element, boolean enabled) {
        if (element != MudTuningHudElement.CENTER) {
            value(TUNING_HUD_ELEMENT_ENABLED, element).set(enabled);
        }
    }

    public static double tuningHudElementX(MudTuningHudElement element) {
        double x = value(TUNING_HUD_ELEMENT_X, element).get();
        // The previous semantic layout stored the controls default as 0.14.
        return element == MudTuningHudElement.CONTROLS
                && Math.abs(x - 0.14D) < 1.0E-6D ? 0.0D : x;
    }

    public static double tuningHudElementY(MudTuningHudElement element) {
        double y = value(TUNING_HUD_ELEMENT_Y, element).get();
        if (element == MudTuningHudElement.CENTER
                && Math.abs(y - 0.64D) < 1.0E-6D) {
            return MudTuningHudElement.CENTER.defaultY();
        }
        if (element == MudTuningHudElement.CONTROLS
                && Math.abs(y - 0.84D) < 1.0E-6D) {
            return MudTuningHudElement.CONTROLS.defaultY();
        }
        return y;
    }

    public static double tuningHudElementScale(MudTuningHudElement element) {
        return value(TUNING_HUD_ELEMENT_SCALE, element).get();
    }

    public static void setTuningHudElementLayout(
            MudTuningHudElement element, double x, double y, double scale) {
        value(TUNING_HUD_ELEMENT_X, element).set(clamp(x, 0.0D, 1.0D));
        value(TUNING_HUD_ELEMENT_Y, element).set(clamp(y, 0.0D, 1.0D));
        value(TUNING_HUD_ELEMENT_SCALE, element).set(clamp(scale, 0.50D, 2.0D));
    }

    public static void saveTuningHudSettings() {
        SPEC.save();
    }

    private static <T> T value(EnumMap<MudTuningHudElement, T> values,
            MudTuningHudElement element) {
        MudTuningHudElement key = element == null
                ? MudTuningHudElement.CENTER : element;
        return values.get(key);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static int tuningColorRevision() {
        return tuningColorRevision;
    }

    public static String tuningTargetColor() {
        return normalizedHexColor(TUNING_TARGET_COLOR.get());
    }

    public static void setTuningTargetColor(String color) {
        setTuningColor(TUNING_TARGET_COLOR, color);
    }

    public static String tuningPointOneColor() {
        return normalizedHexColor(TUNING_POINT_ONE_COLOR.get());
    }

    public static void setTuningPointOneColor(String color) {
        setTuningColor(TUNING_POINT_ONE_COLOR, color);
    }

    public static String tuningPointTwoColor() {
        return normalizedHexColor(TUNING_POINT_TWO_COLOR.get());
    }

    public static void setTuningPointTwoColor(String color) {
        setTuningColor(TUNING_POINT_TWO_COLOR, color);
    }

    public static String tuningModifiedColor() {
        return normalizedHexColor(TUNING_MODIFIED_COLOR.get());
    }

    public static void setTuningModifiedColor(String color) {
        setTuningColor(TUNING_MODIFIED_COLOR, color);
    }

    public static String tuningFlowColor() {
        return normalizedHexColor(TUNING_FLOW_COLOR.get());
    }

    public static void setTuningFlowColor(String color) {
        setTuningColor(TUNING_FLOW_COLOR, color);
    }

    public static String tuningIncompatibleColor() {
        return normalizedHexColor(TUNING_INCOMPATIBLE_COLOR.get());
    }

    public static void setTuningIncompatibleColor(String color) {
        setTuningColor(TUNING_INCOMPATIBLE_COLOR, color);
    }

    public static String tuningConvertedDefaultColor() {
        return normalizedHexColor(TUNING_CONVERTED_DEFAULT_COLOR.get());
    }

    public static void setTuningConvertedDefaultColor(String color) {
        setTuningColor(TUNING_CONVERTED_DEFAULT_COLOR, color);
    }

    public static String tuningConvertedModifiedColor() {
        return normalizedHexColor(TUNING_CONVERTED_MODIFIED_COLOR.get());
    }

    public static void setTuningConvertedModifiedColor(String color) {
        setTuningColor(TUNING_CONVERTED_MODIFIED_COLOR, color);
    }

    public static int tuningLastMode() {
        return TUNING_LAST_MODE.get();
    }

    public static void setTuningLastMode(int mode) {
        TUNING_LAST_MODE.set(Math.max(0, Math.min(5, mode)));
        SPEC.save();
    }

    public static double tuningSpatialPlacementDistance() {
        return TUNING_SPATIAL_PLACEMENT_DISTANCE.get();
    }

    public static void setTuningSpatialPlacementDistance(double distance) {
        TUNING_SPATIAL_PLACEMENT_DISTANCE.set(Math.max(1.0D, Math.min(64.0D, distance)));
        SPEC.save();
    }

    public static boolean tuningTentacleAutoSnap() {
        return TUNING_TENTACLE_AUTO_SNAP.get();
    }

    public static void setTuningTentacleAutoSnap(boolean enabled) {
        TUNING_TENTACLE_AUTO_SNAP.set(enabled);
        SPEC.save();
    }

    public static int tuningGenerationRadius() {
        return TUNING_GENERATION_RADIUS.get();
    }

    public static void setTuningGenerationRadius(int value) {
        TUNING_GENERATION_RADIUS.set(Math.max(2, Math.min(48, value)));
        SPEC.save();
    }

    public static int tuningGenerationThickness() {
        return TUNING_GENERATION_THICKNESS.get();
    }

    public static void setTuningGenerationThickness(int value) {
        TUNING_GENERATION_THICKNESS.set(Math.max(1, Math.min(8, value)));
        SPEC.save();
    }

    public static double tuningGenerationEdgeRoughness() {
        return TUNING_GENERATION_EDGE_ROUGHNESS.get();
    }

    public static void setTuningGenerationEdgeRoughness(double value) {
        TUNING_GENERATION_EDGE_ROUGHNESS.set(
                Math.max(0.0D, Math.min(1.0D, value)));
        SPEC.save();
    }

    public static int tuningGenerationHeightTolerance() {
        return TUNING_GENERATION_HEIGHT_TOLERANCE.get();
    }

    public static void setTuningGenerationHeightTolerance(int value) {
        TUNING_GENERATION_HEIGHT_TOLERANCE.set(Math.max(0, Math.min(24, value)));
        SPEC.save();
    }

    public static int tuningGenerationSeed() {
        return TUNING_GENERATION_SEED.get();
    }

    public static void setTuningGenerationSeed(int value) {
        TUNING_GENERATION_SEED.set(Math.max(0, value));
        SPEC.save();
    }

    public static boolean tuningGenerationSameSourceOnly() {
        return TUNING_GENERATION_SAME_SOURCE_ONLY.get();
    }

    public static void setTuningGenerationSameSourceOnly(boolean enabled) {
        TUNING_GENERATION_SAME_SOURCE_ONLY.set(enabled);
        SPEC.save();
    }

    public static int tuningGenerationType() {
        return TUNING_GENERATION_TYPE.get();
    }

    public static void setTuningGenerationType(int value) {
        TUNING_GENERATION_TYPE.set(Math.max(0, Math.min(
                MudTerrainGenerationType.values().length - 1, value)));
        SPEC.save();
    }

    public static int tuningGenerationLakeHorizontalRadius() {
        return TUNING_GENERATION_LAKE_HORIZONTAL_RADIUS.get();
    }

    public static void setTuningGenerationLakeHorizontalRadius(int value) {
        TUNING_GENERATION_LAKE_HORIZONTAL_RADIUS.set(
                Math.max(2, Math.min(24, value)));
        SPEC.save();
    }

    public static int tuningGenerationLakeVerticalRadius() {
        return TUNING_GENERATION_LAKE_VERTICAL_RADIUS.get();
    }

    public static void setTuningGenerationLakeVerticalRadius(int value) {
        TUNING_GENERATION_LAKE_VERTICAL_RADIUS.set(
                Math.max(1, Math.min(12, value)));
        SPEC.save();
    }

    public static int tuningGenerationLakeSurfaceHeightPixels() {
        return TUNING_GENERATION_LAKE_SURFACE_HEIGHT_PIXELS.get();
    }

    public static void setTuningGenerationLakeSurfaceHeightPixels(int value) {
        TUNING_GENERATION_LAKE_SURFACE_HEIGHT_PIXELS.set(
                Math.max(MudTerrainLakeSettings.MINIMUM_SURFACE_HEIGHT_PIXELS,
                        Math.min(MudTerrainLakeSettings.MAXIMUM_SURFACE_HEIGHT_PIXELS,
                                value)));
        SPEC.save();
    }

    public static int tuningGenerationLakeSeed() {
        return TUNING_GENERATION_LAKE_SEED.get();
    }

    public static void setTuningGenerationLakeSeed(int value) {
        TUNING_GENERATION_LAKE_SEED.set(Math.max(0, value));
        SPEC.save();
    }

    public static String tuningGenerationLakeShellBlock() {
        return TUNING_GENERATION_LAKE_SHELL_BLOCK.get();
    }

    public static void setTuningGenerationLakeShellBlock(String value) {
        if (validResourceLocation(value)) {
            TUNING_GENERATION_LAKE_SHELL_BLOCK.set(value);
            SPEC.save();
        }
    }

    public static String tuningGenerationLakeInnerBlock() {
        return TUNING_GENERATION_LAKE_INNER_BLOCK.get();
    }

    public static void setTuningGenerationLakeInnerBlock(String value) {
        if (validResourceLocation(value)) {
            TUNING_GENERATION_LAKE_INNER_BLOCK.set(value);
            SPEC.save();
        }
    }

    private static boolean validResourceLocation(Object value) {
        return value instanceof String text
                && ResourceLocation.tryParse(text) != null;
    }

    private static boolean validHexColor(Object value) {
        return value instanceof String text
                && text.trim().replace("#", "").matches("[0-9a-fA-F]{6}");
    }

    private static String normalizedHexColor(String value) {
        return value == null ? "000000"
                : value.trim().replace("#", "").toUpperCase(Locale.ROOT);
    }

    private static void setTuningColor(ModConfigSpec.ConfigValue<String> value,
            String color) {
        if (!validHexColor(color)) {
            return;
        }
        value.set(normalizedHexColor(color));
        tuningColorRevision++;
        SPEC.save();
    }

    public static double mudSurfaceRenderDistance() {
        return MUD_SURFACE_RENDER_DISTANCE.get();
    }

    public static int mudSurfaceMaxHoles() {
        return MUD_SURFACE_MAX_HOLES.get();
    }

    public static int mudSurfaceMaxCells() {
        return MUD_SURFACE_MAX_TRENCH_CELLS.get();
    }

    public static int mudSurfaceMaxSideImprints() {
        return MUD_SURFACE_MAX_SIDE_IMPRINTS.get();
    }

    public static int mudSurfaceMaxSideCells() {
        return MUD_SURFACE_MAX_SIDE_CELLS.get();
    }

    public static int mudSurfaceMaxBubbles() {
        return MUD_SURFACE_MAX_BUBBLES.get();
    }

    public static int mudSurfaceAmbientProbes() {
        return MUD_SURFACE_AMBIENT_PROBES.get();
    }

    public static int mudSurfaceAmbientIntervalTicks() {
        return MUD_SURFACE_AMBIENT_INTERVAL.get();
    }

    public static double mudSurfaceArmorRadiusBonus() {
        return MUD_SURFACE_ARMOR_RADIUS_BONUS.get();
    }

    public static double mudSurfaceArmorRadiusMaximum() {
        return MUD_SURFACE_ARMOR_RADIUS_MAXIMUM.get();
    }

    public static boolean insectMoundSurfaceEnabled() {
        return INSECT_MOUND_SURFACE_ENABLED.get();
    }

    public static int insectMoundMaxPatches() {
        return INSECT_MOUND_MAX_PATCHES.get();
    }

    public static int insectMoundScanInterval() {
        return INSECT_MOUND_SCAN_INTERVAL.get();
    }

    public static int insectMoundLarvaePerFace() {
        return INSECT_MOUND_LARVAE_PER_FACE.get();
    }

    public static int insectMoundMinLength() {
        return INSECT_MOUND_MIN_LENGTH.get();
    }

    public static int insectMoundMaxLength() {
        return Math.max(INSECT_MOUND_MIN_LENGTH.get(), INSECT_MOUND_MAX_LENGTH.get());
    }

    public static double insectMoundBaseActivity() {
        return INSECT_MOUND_BASE_ACTIVITY.get();
    }

    public static double insectMoundSoundVolume() {
        return INSECT_MOUND_SOUND_VOLUME.get();
    }

    public static int crossSectionSides() {
        return CROSS_SECTION_SIDES.get();
    }

    public static double renderDistance() {
        return RENDER_DISTANCE.get();
    }

    public static double lodDistance() {
        return LOD_DISTANCE.get();
    }

    public static int lodStride() {
        return LOD_STRIDE.get();
    }

    public static int interpolationTicks() {
        return INTERPOLATION_TICKS.get();
    }

    public static double surfaceVariation() {
        return SURFACE_VARIATION.get();
    }

    public static double pulseAmplitude() {
        return PULSE_AMPLITUDE.get();
    }

    public static double pulseSpeed() {
        return PULSE_SPEED.get();
    }

    public static double tipTaperStart() {
        return TIP_TAPER_START.get();
    }

    public static double tipRingScale() {
        return TIP_RING_SCALE.get();
    }

    public static double tipLengthScale() {
        return TIP_LENGTH_SCALE.get();
    }

    public static int rootCapRings() {
        return ROOT_CAP_RINGS.get();
    }

    public static double rootCapLengthScale() {
        return ROOT_CAP_LENGTH_SCALE.get();
    }

    public static boolean castShaderShadows() {
        return CAST_SHADER_SHADOWS.get();
    }

    public static GrabCameraMode grabCameraMode() {
        return switch (GRAB_CAMERA_MODE.get().toLowerCase(java.util.Locale.ROOT)) {
            case "off" -> GrabCameraMode.OFF;
            case "smooth" -> GrabCameraMode.SMOOTH;
            default -> GrabCameraMode.IMMERSIVE;
        };
    }

    public static double grabCameraStrength() {
        return GRAB_CAMERA_STRENGTH.get();
    }

    public static boolean grabWrapEnabled() {
        return GRAB_WRAP_ENABLED.get();
    }

    public static int grabWrapSegments() {
        return GRAB_WRAP_SEGMENTS.get();
    }

    public static double grabWrapTurns() {
        return GRAB_WRAP_TURNS.get();
    }

    public static double grabWrapRadiusScale() {
        return GRAB_WRAP_RADIUS_SCALE.get();
    }

    public static double grabWrapLengthScale() {
        return GRAB_WRAP_LENGTH_SCALE.get();
    }

    public static double grabWrapStrandRadiusScale() {
        return GRAB_WRAP_STRAND_RADIUS_SCALE.get();
    }

    public static double grabWrapWholeBodyTurnsScale() {
        return GRAB_WRAP_WHOLE_BODY_TURNS_SCALE.get();
    }

    public static int grabWrapStrands() {
        return GRAB_WRAP_STRANDS.get();
    }

    public enum GrabCameraMode {
        OFF,
        SMOOTH,
        IMMERSIVE
    }

    public enum ClientOption {
        SURFACE_EFFECTS(true),
        SPLASH_EFFECTS(true),
        ERUPTION_EFFECTS(true),
        SURFACE_DECALS(true),
        INSECT_SURFACE(true),
        TENTACLES(true),
        PLAYER_COVERAGE(true),
        ENTITY_COVERAGE(true),
        MUD_SCREEN(true),
        ASSIMILATION_SCREEN(true),
        SWARM_SCREEN(true),
        PRECISE_MODEL_GEOMETRY(false),
        ANIMATED_CONTACT_GEOMETRY(true),
        TENTACLE_SHADER_SHADOWS(true);

        private final boolean defaultEnabled;

        ClientOption(boolean defaultEnabled) {
            this.defaultEnabled = defaultEnabled;
        }

        public boolean defaultEnabled() {
            return defaultEnabled;
        }
    }
}
