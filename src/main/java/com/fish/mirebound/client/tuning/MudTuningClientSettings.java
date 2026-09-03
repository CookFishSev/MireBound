package com.fish.mirebound.client.tuning;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.generation.MudTerrainGenerationRequest;
import com.fish.mirebound.generation.MudTerrainGenerationSettings;
import com.fish.mirebound.generation.MudTerrainGenerationType;
import com.fish.mirebound.generation.MudTerrainLakeSettings;
import com.fish.mirebound.generation.MudTerrainRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.Locale;

/** Client tuning-wand preferences plus server-synchronized session state. */
public final class MudTuningClientSettings {
    private static boolean conversionUnlocked;
    private static boolean unrestrictedConversionUnlocked;
    private static boolean unrestrictedConversionEnabled;
    private static int cachedColorRevision = Integer.MIN_VALUE;
    private static final int[] cachedColors = new int[HudColor.values().length];

    private MudTuningClientSettings() {
    }

    public static float hudOpacity() {
        return (float) MireboundClientSettings.tuningHudOpacity();
    }

    public static void setHudOpacity(double value) {
        MireboundClientSettings.setTuningHudOpacity(value);
    }

    public static float controlsHudOpacity() {
        return (float) MireboundClientSettings.tuningHudControlsOpacity();
    }

    public static void setControlsHudOpacity(double value) {
        MireboundClientSettings.setTuningHudControlsOpacity(value);
    }

    public static boolean hudElementEnabled(MudTuningHudElement element) {
        return MireboundClientSettings.tuningHudElementEnabled(element);
    }

    public static void setHudElementEnabled(
            MudTuningHudElement element, boolean enabled) {
        MireboundClientSettings.setTuningHudElementEnabled(element, enabled);
    }

    public static double hudElementX(MudTuningHudElement element) {
        return MireboundClientSettings.tuningHudElementX(element);
    }

    public static double hudElementY(MudTuningHudElement element) {
        return MireboundClientSettings.tuningHudElementY(element);
    }

    public static double hudElementScale(MudTuningHudElement element) {
        return MireboundClientSettings.tuningHudElementScale(element);
    }

    public static void setHudElementLayout(
            MudTuningHudElement element, double x, double y, double scale) {
        MireboundClientSettings.setTuningHudElementLayout(element, x, y, scale);
    }

    public static int color(HudColor color) {
        if (color == null) {
            return 0xFFFFFF;
        }
        refreshColorCache();
        return cachedColors[color.ordinal()];
    }

    private static void refreshColorCache() {
        int revision = MireboundClientSettings.tuningColorRevision();
        if (cachedColorRevision == revision) {
            return;
        }
        cachedColors[HudColor.TARGET.ordinal()] = parseHexColor(
                MireboundClientSettings.tuningTargetColor(), 0xF5C542);
        cachedColors[HudColor.POINT_ONE.ordinal()] = parseHexColor(
                MireboundClientSettings.tuningPointOneColor(), 0xFF291F);
        cachedColors[HudColor.POINT_TWO.ordinal()] = parseHexColor(
                MireboundClientSettings.tuningPointTwoColor(), 0x2E7AFF);
        cachedColors[HudColor.MODIFIED.ordinal()] = parseHexColor(
                MireboundClientSettings.tuningModifiedColor(), 0xFF34A8);
        cachedColors[HudColor.FLOW.ordinal()] = parseHexColor(
                MireboundClientSettings.tuningFlowColor(), 0x123DB8);
        cachedColors[HudColor.INCOMPATIBLE.ordinal()] = parseHexColor(
                MireboundClientSettings.tuningIncompatibleColor(), 0xFF241A);
        cachedColors[HudColor.CONVERTED_DEFAULT.ordinal()] = parseHexColor(
                MireboundClientSettings.tuningConvertedDefaultColor(), 0xFFD02A);
        cachedColors[HudColor.CONVERTED_MODIFIED.ordinal()] = parseHexColor(
                MireboundClientSettings.tuningConvertedModifiedColor(), 0xFF6B16);
        cachedColorRevision = revision;
    }

    static int parseHexColor(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.matches("[0-9a-fA-F]{6}")) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static String formatHexColor(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0xFFFFFF);
    }

    public static MudTuningWandMode savedMode() {
        MudTuningWandMode[] modes = MudTuningWandMode.values();
        return modes[Math.max(0, Math.min(modes.length - 1,
                MireboundClientSettings.tuningLastMode()))];
    }

    public static void saveMode(MudTuningWandMode mode) {
        MireboundClientSettings.setTuningLastMode(mode.ordinal());
    }

    public static double spatialPlacementDistance() {
        return MireboundClientSettings.tuningSpatialPlacementDistance();
    }

    public static void setSpatialPlacementDistance(double distance) {
        MireboundClientSettings.setTuningSpatialPlacementDistance(distance);
    }

    public static boolean tentacleAutoSnap() {
        return MireboundClientSettings.tuningTentacleAutoSnap();
    }

    public static void setTentacleAutoSnap(boolean enabled) {
        MireboundClientSettings.setTuningTentacleAutoSnap(enabled);
    }

    public static boolean preciseModelGeometry() {
        return MireboundClientSettings.mudSurfacePreciseModelGeometry();
    }

    public static void setPreciseModelGeometry(boolean enabled) {
        MireboundClientSettings.setMudSurfacePreciseModelGeometry(enabled);
    }

    public static int generationRadius() {
        return MireboundClientSettings.tuningGenerationRadius();
    }

    public static void setGenerationRadius(int value) {
        MireboundClientSettings.setTuningGenerationRadius(value);
    }

    public static int generationThickness() {
        return MireboundClientSettings.tuningGenerationThickness();
    }

    public static void setGenerationThickness(int value) {
        MireboundClientSettings.setTuningGenerationThickness(value);
    }

    public static double generationEdgeRoughness() {
        return MireboundClientSettings.tuningGenerationEdgeRoughness();
    }

    public static void setGenerationEdgeRoughness(double value) {
        MireboundClientSettings.setTuningGenerationEdgeRoughness(value);
    }

    public static int generationHeightTolerance() {
        return MireboundClientSettings.tuningGenerationHeightTolerance();
    }

    public static void setGenerationHeightTolerance(int value) {
        MireboundClientSettings.setTuningGenerationHeightTolerance(value);
    }

    public static int generationSeed() {
        return MireboundClientSettings.tuningGenerationSeed();
    }

    public static void setGenerationSeed(int value) {
        MireboundClientSettings.setTuningGenerationSeed(value);
    }

    public static boolean generationSameSourceOnly() {
        return MireboundClientSettings.tuningGenerationSameSourceOnly();
    }

    public static void setGenerationSameSourceOnly(boolean enabled) {
        MireboundClientSettings.setTuningGenerationSameSourceOnly(enabled);
    }

    public static MudTerrainGenerationType generationType() {
        return MudTerrainGenerationType.byId(
                MireboundClientSettings.tuningGenerationType());
    }

    public static void setGenerationType(MudTerrainGenerationType type) {
        MireboundClientSettings.setTuningGenerationType(
                type == null
                        ? MudTerrainGenerationType.defaultType().ordinal()
                        : type.ordinal());
    }

    public static int generationLakeHorizontalRadius() {
        return MireboundClientSettings.tuningGenerationLakeHorizontalRadius();
    }

    public static void setGenerationLakeHorizontalRadius(int value) {
        MireboundClientSettings.setTuningGenerationLakeHorizontalRadius(value);
    }

    public static int generationLakeVerticalRadius() {
        return MireboundClientSettings.tuningGenerationLakeVerticalRadius();
    }

    public static void setGenerationLakeVerticalRadius(int value) {
        MireboundClientSettings.setTuningGenerationLakeVerticalRadius(value);
    }

    public static int generationLakeSurfaceHeightPixels() {
        return MireboundClientSettings.tuningGenerationLakeSurfaceHeightPixels();
    }

    public static void setGenerationLakeSurfaceHeightPixels(int value) {
        MireboundClientSettings.setTuningGenerationLakeSurfaceHeightPixels(value);
    }

    public static int generationLakeSeed() {
        return MireboundClientSettings.tuningGenerationLakeSeed();
    }

    public static void setGenerationLakeSeed(int value) {
        MireboundClientSettings.setTuningGenerationLakeSeed(value);
    }

    public static ResourceLocation generationLakeShellBlock() {
        return parseId(MireboundClientSettings.tuningGenerationLakeShellBlock());
    }

    public static void setGenerationLakeShellBlock(ResourceLocation value) {
        MireboundClientSettings.setTuningGenerationLakeShellBlock(
                value == null ? MudTerrainLakeSettings.AIR.toString() : value.toString());
    }

    public static ResourceLocation generationLakeInnerBlock() {
        return parseId(MireboundClientSettings.tuningGenerationLakeInnerBlock());
    }

    public static void setGenerationLakeInnerBlock(ResourceLocation value) {
        MireboundClientSettings.setTuningGenerationLakeInnerBlock(
                value == null ? MudTerrainLakeSettings.AIR.toString() : value.toString());
    }

    public static MudTerrainGenerationRequest generationRequest(
            BlockPos center, MudTerrainRotation rotation,
            boolean spatialPlacement) {
        return new MudTerrainGenerationRequest(
                generationType(), center, spatialPlacement,
                new MudTerrainGenerationSettings(
                        generationRadius(), generationThickness(),
                        generationEdgeRoughness(), generationHeightTolerance(),
                        generationSeed(), generationSameSourceOnly()),
                new MudTerrainLakeSettings(
                        generationLakeHorizontalRadius(),
                        generationLakeVerticalRadius(), generationLakeSeed(),
                        generationLakeShellBlock(), generationLakeInnerBlock(),
                        generationLakeSurfaceHeightPixels(), true),
                rotation);
    }

    public static boolean conversionUnlocked() {
        return conversionUnlocked;
    }

    public static boolean unrestrictedConversionUnlocked() {
        return unrestrictedConversionUnlocked;
    }

    public static boolean unrestrictedConversionEnabled() {
        return unrestrictedConversionEnabled;
    }

    static void acceptConversionSafety(boolean unlocked,
            boolean unrestrictedUnlocked, boolean unrestrictedEnabled) {
        conversionUnlocked = unlocked;
        unrestrictedConversionUnlocked = unlocked && unrestrictedUnlocked;
        unrestrictedConversionEnabled = unrestrictedConversionUnlocked
                && unrestrictedEnabled;
    }

    static void resetServerState() {
        conversionUnlocked = false;
        unrestrictedConversionUnlocked = false;
        unrestrictedConversionEnabled = false;
    }

    private static ResourceLocation parseId(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null ? MudTerrainLakeSettings.AIR : parsed;
    }

    public enum HudColor {
        TARGET("target", 0xF5C542),
        POINT_ONE("point_one", 0xFF291F),
        POINT_TWO("point_two", 0x2E7AFF),
        MODIFIED("modified", 0xFF34A8),
        FLOW("flow", 0x123DB8),
        INCOMPATIBLE("incompatible", 0xFF241A),
        CONVERTED_DEFAULT("converted_default", 0xFFD02A),
        CONVERTED_MODIFIED("converted_modified", 0xFF6B16);

        private final String name;
        private final int defaultColor;

        HudColor(String name, int defaultColor) {
            this.name = name;
            this.defaultColor = defaultColor;
        }

        public int color() {
            return MudTuningClientSettings.color(this);
        }

        public int defaultColor() {
            return defaultColor;
        }

        public String hex() {
            return formatHexColor(color());
        }

        public String translationKey() {
            return "gui.mirebound.tuning.settings.color." + name;
        }

        public void setHex(String value) {
            int parsed = parseHexColor(value, -1);
            if (parsed < 0) {
                return;
            }
            switch (this) {
                case TARGET -> MireboundClientSettings.setTuningTargetColor(
                        formatHexColor(parsed));
                case POINT_ONE -> MireboundClientSettings.setTuningPointOneColor(
                        formatHexColor(parsed));
                case POINT_TWO -> MireboundClientSettings.setTuningPointTwoColor(
                        formatHexColor(parsed));
                case MODIFIED -> MireboundClientSettings.setTuningModifiedColor(
                        formatHexColor(parsed));
                case FLOW -> MireboundClientSettings.setTuningFlowColor(
                        formatHexColor(parsed));
                case INCOMPATIBLE -> MireboundClientSettings.setTuningIncompatibleColor(
                        formatHexColor(parsed));
                case CONVERTED_DEFAULT -> MireboundClientSettings
                        .setTuningConvertedDefaultColor(formatHexColor(parsed));
                case CONVERTED_MODIFIED -> MireboundClientSettings
                        .setTuningConvertedModifiedColor(formatHexColor(parsed));
            }
        }
    }
}
