package com.fish.mirebound.mud;

public enum MudBodyPart {
    LEFT_LEG,
    RIGHT_LEG,
    BODY,
    LEFT_ARM,
    RIGHT_ARM,
    HEAD;

    public static final int BANDS = 6;
    public static final int SURFACE_LANES = 8;
    public static final int VISION_BANDS = 16;
    public static final int VISION_LANES = 16;
    public static final int COUNT = values().length;
    public static final int BAND_COUNT = COUNT * BANDS;
    // Kept as a compatibility alias for code that only needs the array size.
    public static final int SURFACE_COUNT = MudSurfaceLayout.CELL_COUNT;
    public static final int VISION_COUNT = VISION_BANDS * VISION_LANES;
}
