package com.fish.mirebound.mud.harvest;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;

/** Immutable, validated harvesting settings used by the break-speed hot path. */
public record MudHarvestProfile(
        double hardness,
        MudHarvestTool preferredTool,
        double handSpeedMultiplier,
        double preferredToolSpeedMultiplier,
        double otherToolSpeedMultiplier) {

    public static MudHarvestProfile fromValues(double[] values) {
        return new MudHarvestProfile(
                value(values, MudPhysicsParameter.HARVEST_HARDNESS),
                MudHarvestTool.byId((int) Math.round(value(
                        values, MudPhysicsParameter.HARVEST_PREFERRED_TOOL))),
                value(values, MudPhysicsParameter.HARVEST_HAND_SPEED_MULTIPLIER),
                value(values, MudPhysicsParameter.HARVEST_PREFERRED_TOOL_SPEED_MULTIPLIER),
                value(values, MudPhysicsParameter.HARVEST_OTHER_TOOL_SPEED_MULTIPLIER));
    }

    public static MudHarvestProfile defaultsFor(SinkingMedium medium) {
        return new MudHarvestProfile(
                defaultHardness(medium),
                defaultTool(medium),
                1.0D,
                1.0D,
                1.0D);
    }

    public void writeTo(double[] values) {
        put(values, MudPhysicsParameter.HARVEST_HARDNESS, hardness);
        put(values, MudPhysicsParameter.HARVEST_PREFERRED_TOOL, preferredTool.ordinal());
        put(values, MudPhysicsParameter.HARVEST_HAND_SPEED_MULTIPLIER, handSpeedMultiplier);
        put(values, MudPhysicsParameter.HARVEST_PREFERRED_TOOL_SPEED_MULTIPLIER,
                preferredToolSpeedMultiplier);
        put(values, MudPhysicsParameter.HARVEST_OTHER_TOOL_SPEED_MULTIPLIER,
                otherToolSpeedMultiplier);
    }

    private static double defaultHardness(SinkingMedium medium) {
        return switch (medium) {
            case THIN_MUD -> 0.75D;
            case SHALLOW_MUD, ASH_QUICKSAND -> 0.90D;
            case TIDAL_MUD, SOFT_QUICKSAND, LIVING_SLIME -> 1.00D;
            case SILT -> 1.10D;
            case JUNGLE_QUICKSAND -> 1.15D;
            case RED_QUICKSAND -> 1.20D;
            case MUD -> 1.25D;
            case INSECT_MOUND -> 1.35D;
            case PEAT_BOG, SOUL_SILT -> 1.40D;
            case PEAT_SILT -> 1.45D;
            case LIME_MUD, END_SILT -> 1.50D;
            case FUNGAL_MIRE, PALE_MIRE -> 1.60D;
            case GRAVEL_SILT -> 1.65D;
            case GEL_CLAY, MIRE -> 1.75D;
            case ASSIMILATION_SLIME -> 1.80D;
            case SCULK_MIRE, TAR -> 2.20D;
            case TENDER_FLESH -> 2.40D;
            case STONE_CLAY -> 3.00D;
        };
    }

    private static MudHarvestTool defaultTool(SinkingMedium medium) {
        return switch (medium) {
            case LIVING_SLIME, ASSIMILATION_SLIME -> MudHarvestTool.NONE;
            case SCULK_MIRE, FUNGAL_MIRE -> MudHarvestTool.HOE;
            case STONE_CLAY -> MudHarvestTool.PICKAXE;
            case TENDER_FLESH -> MudHarvestTool.SWORD;
            default -> MudHarvestTool.SHOVEL;
        };
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        return parameter.sanitize(values[parameter.ordinal()]);
    }

    private static void put(double[] values, MudPhysicsParameter parameter, double value) {
        values[parameter.ordinal()] = parameter.sanitize(value);
    }
}
