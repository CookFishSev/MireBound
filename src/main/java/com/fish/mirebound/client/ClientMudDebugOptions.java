package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.coverage.MudVisionSamplingLayout;
import com.fish.mirebound.mud.CoverageDebugLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.Mth;

public final class ClientMudDebugOptions {
    private static final List<String> BOOLEAN_OPTION_NAMES = List.of("physics_hud", "physics_log", "screen_overlay", "screen_sampling", "screen_vision_debug", "baked_skin", "skin_layer", "coverage_debug", "contact_geometry", "animated_contact_geometry", "precise_uv_ownership", "tentacle_overlay");
    private static final List<String> NUMBER_OPTION_NAMES = List.of(
            "vision_screen_shift_y",
            "vision_screen_overscan_y",
            "vision_screen_edge_pull_y",
            "vision_face_width",
            "vision_face_height",
            "vision_face_bottom_offset");
    private static final List<String> OPTION_NAMES = combinedOptionNames();
    private static boolean physicsHud;
    private static boolean physicsLog;
    private static boolean screenOverlay = true;
    private static boolean screenSampling = true;
    private static boolean screenVisionDebug;
    private static boolean bakedSkin = true;
    private static boolean skinLayer = true;
    private static boolean contactGeometry;
    private static boolean tentacleOverlay = true;
    private static final float DEFAULT_VISION_SCREEN_SHIFT_Y = 0.0F;
    private static final float DEFAULT_VISION_SCREEN_OVERSCAN_Y = 0.0F;
    private static final float DEFAULT_VISION_SCREEN_EDGE_PULL_Y = 0.0F;
    private static float visionScreenShiftY = DEFAULT_VISION_SCREEN_SHIFT_Y;
    private static float visionScreenOverscanY = DEFAULT_VISION_SCREEN_OVERSCAN_Y;
    private static float visionScreenEdgePullY = DEFAULT_VISION_SCREEN_EDGE_PULL_Y;
    private static SwarmVisualMode swarmVisualMode = SwarmVisualMode.FULL;

    private ClientMudDebugOptions() {
    }

    static List<String> optionNames() {
        return OPTION_NAMES;
    }

    static List<String> booleanOptionNames() {
        return BOOLEAN_OPTION_NAMES;
    }

    static List<String> numberOptionNames() {
        return NUMBER_OPTION_NAMES;
    }

    static boolean physicsHud() {
        return physicsHud;
    }

    public static boolean physicsLog() {
        return physicsLog;
    }

    static boolean screenOverlay() {
        return screenOverlay;
    }

    static boolean screenSampling() {
        return screenSampling;
    }

    static boolean screenVisionDebug() {
        return screenVisionDebug;
    }

    public static boolean bakedSkin() {
        return bakedSkin;
    }

    public static boolean skinLayer() {
        return skinLayer;
    }

    static boolean contactGeometry() {
        return contactGeometry;
    }

    static boolean tentacleOverlay() {
        return tentacleOverlay;
    }

    public static SwarmVisualMode swarmVisualMode() {
        return swarmVisualMode;
    }

    static void setSwarmVisualMode(SwarmVisualMode mode) {
        swarmVisualMode = mode;
    }

    static float visionScreenShiftY() {
        return visionScreenShiftY;
    }

    static float visionScreenOverscanY() {
        return visionScreenOverscanY;
    }

    static float visionScreenEdgePullY() {
        return visionScreenEdgePullY;
    }

    static void resetTuning() {
        visionScreenShiftY = DEFAULT_VISION_SCREEN_SHIFT_Y;
        visionScreenOverscanY = DEFAULT_VISION_SCREEN_OVERSCAN_Y;
        visionScreenEdgePullY = DEFAULT_VISION_SCREEN_EDGE_PULL_Y;
        MudVisionSamplingLayout.reset();
        ScreenMudOverlay.reset();
    }

    static boolean set(String option, boolean value) {
        return switch (canonicalName(option)) {
            case "physics_hud" -> {
                physicsHud = value;
                yield true;
            }
            case "physics_log" -> {
                physicsLog = value;
                yield true;
            }
            case "screen_overlay" -> {
                screenOverlay = value;
                yield true;
            }
            case "screen_sampling" -> {
                screenSampling = value;
                ScreenMudOverlay.reset();
                yield true;
            }
            case "screen_vision_debug" -> {
                screenVisionDebug = value;
                yield true;
            }
            case "baked_skin" -> {
                bakedSkin = value;
                yield true;
            }
            case "skin_layer" -> {
                skinLayer = value;
                yield true;
            }
            case "coverage_debug" -> {
                CoverageDebugLog.setEnabled(value);
                yield true;
            }
            case "contact_geometry" -> {
                contactGeometry = value;
                yield true;
            }
            case "precise_uv_ownership" -> {
                MireboundClientSettings.setPreciseUvOwnership(value);
                ArmorVertexContactCapture.reset();
                ArmorTextureFootprintCache.reset();
                ArmorMudRenderBridge.reset();
                MudCapeTextureCache.reset();
                yield true;
            }
            case "animated_contact_geometry" -> {
                MireboundClientSettings.setAnimatedContactGeometry(value);
                AnimatedPlayerGeometryCapture.reset();
                yield true;
            }
            case "tentacle_overlay" -> {
                tentacleOverlay = value;
                yield true;
            }
            default -> false;
        };
    }

    static boolean setNumber(String option, float value) {
        return switch (canonicalName(option)) {
            case "vision_screen_shift_y" -> {
                visionScreenShiftY = Mth.clamp(value, -0.50F, 0.50F);
                ScreenMudOverlay.reset();
                yield true;
            }
            case "vision_screen_overscan_y" -> {
                visionScreenOverscanY = Mth.clamp(value, 0.0F, 0.75F);
                ScreenMudOverlay.reset();
                yield true;
            }
            case "vision_screen_edge_pull_y" -> {
                visionScreenEdgePullY = Mth.clamp(value, 0.0F, 0.45F);
                ScreenMudOverlay.reset();
                yield true;
            }
            case "vision_face_width" -> {
                MudVisionSamplingLayout.setWidthPixels(value);
                yield true;
            }
            case "vision_face_height" -> {
                MudVisionSamplingLayout.setHeightPixels(value);
                yield true;
            }
            case "vision_face_bottom_offset" -> {
                MudVisionSamplingLayout.setBottomOffsetPixels(value);
                yield true;
            }
            default -> false;
        };
    }

    static boolean value(String option) {
        return switch (canonicalName(option)) {
            case "physics_hud" -> physicsHud;
            case "physics_log" -> physicsLog;
            case "screen_overlay" -> screenOverlay;
            case "screen_sampling" -> screenSampling;
            case "screen_vision_debug" -> screenVisionDebug;
            case "baked_skin" -> bakedSkin;
            case "skin_layer" -> skinLayer;
            case "coverage_debug" -> CoverageDebugLog.enabled();
            case "contact_geometry" -> contactGeometry;
            case "precise_uv_ownership" -> MireboundClientSettings.preciseUvOwnership();
            case "animated_contact_geometry" -> MireboundClientSettings.animatedContactGeometry();
            case "tentacle_overlay" -> tentacleOverlay;
            default -> false;
        };
    }

    static float numberValue(String option) {
        return switch (canonicalName(option)) {
            case "vision_screen_shift_y" -> visionScreenShiftY;
            case "vision_screen_overscan_y" -> visionScreenOverscanY;
            case "vision_screen_edge_pull_y" -> visionScreenEdgePullY;
            case "vision_face_width" -> (float) MudVisionSamplingLayout.widthPixels();
            case "vision_face_height" -> (float) MudVisionSamplingLayout.heightPixels();
            case "vision_face_bottom_offset" -> (float) MudVisionSamplingLayout.bottomOffsetPixels();
            default -> Float.NaN;
        };
    }

    static String canonicalName(String option) {
        String normalized = option.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "hud", "physics", "physics_debug" -> "physics_hud";
            case "physics_trace", "physics_debug_log", "developer", "developer_mode", "dev_mode" -> "physics_log";
            case "screen", "screen_mud" -> "screen_overlay";
            case "sampling", "sample", "sampled", "screen_sample", "screen_sampling_mode", "vision_sampling" -> "screen_sampling";
            case "vision_debug", "screen_debug", "screen_vision" -> "screen_vision_debug";
            case "baked", "skin_bake" -> "baked_skin";
            case "layer", "overlay_layer" -> "skin_layer";
            case "coverage", "coverage_log", "coverage_debug_log", "cover_debug", "mud_debug_log" -> "coverage_debug";
            case "geometry", "contact_boxes", "coverage_geometry", "body_boxes", "cape_boxes" -> "contact_geometry";
            case "precise_uv", "uv_ownership", "mirror_uv", "mirrored_uv" -> "precise_uv_ownership";
            case "animated_geometry", "animated_contact", "pose_geometry", "pose_boxes" -> "animated_contact_geometry";
            case "vision_screen_shift", "vision_shift_y", "screen_shift_y" -> "vision_screen_shift_y";
            case "vision_screen_overscan", "vision_overscan_y", "screen_overscan_y" -> "vision_screen_overscan_y";
            case "vision_edge_pull", "vision_pull_y", "screen_edge_pull_y", "edge_pull_y" -> "vision_screen_edge_pull_y";
            case "vision_width", "face_width" -> "vision_face_width";
            case "vision_height", "face_height" -> "vision_face_height";
            case "vision_bottom", "face_bottom", "vision_bottom_offset" -> "vision_face_bottom_offset";
            default -> normalized;
        };
    }

    private static List<String> combinedOptionNames() {
        List<String> names = new ArrayList<>(BOOLEAN_OPTION_NAMES.size() + NUMBER_OPTION_NAMES.size());
        names.addAll(BOOLEAN_OPTION_NAMES);
        names.addAll(NUMBER_OPTION_NAMES);
        return List.copyOf(names);
    }

    public enum SwarmVisualMode {
        FULL("full"),
        REDUCED("reduced"),
        OFF("off");

        private final String serializedName;

        SwarmVisualMode(String serializedName) {
            this.serializedName = serializedName;
        }

        String serializedName() {
            return serializedName;
        }

        static SwarmVisualMode byName(String name) {
            for (SwarmVisualMode mode : values()) {
                if (mode.serializedName.equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return null;
        }
    }
}
