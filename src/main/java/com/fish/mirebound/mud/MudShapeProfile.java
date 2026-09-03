package com.fish.mirebound.mud;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One authoritative surface definition shared by physics, rendering bounds and compatibility probes. */
public record MudShapeProfile(MudShapeType type, double surfaceHeight) {
    private static final VoxelShape[] HEIGHT_SHAPES = buildHeightShapes();

    public MudShapeProfile {
        type = type == null ? MudShapeType.FULL : type;
        surfaceHeight = Mth.clamp(surfaceHeight, 1.0D / 16.0D, 1.0D);
    }

    public int heightPixels() {
        return Mth.clamp((int) Math.round(surfaceHeight * 16.0D), 1, 16);
    }

    public VoxelShape visualShape() {
        int height = heightPixels();
        return switch (type) {
            case IRREGULAR_PILE -> irregularPile(height);
            case SPECIAL_MODEL -> specialModel(height);
            default -> HEIGHT_SHAPES[height];
        };
    }

    public static boolean supportsSpecial(SinkingMedium medium) {
        return switch (medium) {
            case THIN_MUD, SHALLOW_MUD, TIDAL_MUD, LIVING_SLIME,
                    PEAT_BOG, JUNGLE_QUICKSAND -> true;
            default -> false;
        };
    }

    public static MudShapeProfile special(SinkingMedium medium) {
        return switch (medium) {
            case THIN_MUD -> new MudShapeProfile(MudShapeType.STATIC_HEIGHT, 4.0D / 16.0D);
            case SHALLOW_MUD -> new MudShapeProfile(MudShapeType.STATIC_HEIGHT, 8.0D / 16.0D);
            case TIDAL_MUD, LIVING_SLIME ->
                    new MudShapeProfile(MudShapeType.STATIC_HEIGHT, 14.0D / 16.0D);
            case PEAT_BOG ->
                    new MudShapeProfile(MudShapeType.IRREGULAR_PILE, 10.0D / 16.0D);
            case JUNGLE_QUICKSAND ->
                    new MudShapeProfile(MudShapeType.SPECIAL_MODEL, 14.0D / 16.0D);
            default -> new MudShapeProfile(MudShapeType.FULL, 1.0D);
        };
    }

    private static VoxelShape irregularPile(int height) {
        double top = height / 16.0D;
        double shoulder = Math.max(1, height - 2) / 16.0D;
        return Shapes.or(
                Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, shoulder, 1.0D),
                Shapes.box(2.0D / 16.0D, 0.0D, 1.0D / 16.0D, 14.0D / 16.0D, top, 15.0D / 16.0D),
                Shapes.box(1.0D / 16.0D, 0.0D, 3.0D / 16.0D, 15.0D / 16.0D, top, 12.0D / 16.0D));
    }

    private static VoxelShape specialModel(int height) {
        double top = height / 16.0D;
        double edge = Math.max(1, height - 1) / 16.0D;
        return Shapes.or(
                Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, edge, 1.0D),
                Shapes.box(1.0D / 16.0D, 0.0D, 2.0D / 16.0D, 15.0D / 16.0D, top, 14.0D / 16.0D));
    }

    private static VoxelShape[] buildHeightShapes() {
        VoxelShape[] shapes = new VoxelShape[17];
        shapes[0] = Shapes.empty();
        for (int pixel = 1; pixel <= 16; pixel++) {
            shapes[pixel] = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, pixel / 16.0D, 1.0D);
        }
        return shapes;
    }
}
