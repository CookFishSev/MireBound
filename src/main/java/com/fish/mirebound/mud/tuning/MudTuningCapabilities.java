package com.fish.mirebound.mud.tuning;

/** Compact capability mask supplied by the server for one tuning object. */
public record MudTuningCapabilities(int bits) {
    public static final int EDIT_PARAMETERS = 1;
    public static final int EDIT_SHAPE = 1 << 1;
    public static final int FINITE_FLOW = 1 << 2;
    public static final int LIVING_SLIME = 1 << 3;
    public static final int CONVERT = 1 << 4;
    public static final int RESTORE = 1 << 5;
    public static final int SOURCE_APPEARANCE = 1 << 6;
    public static final int SABLE_SCOPE = 1 << 7;
    public static final int HARVEST_OVERRIDE = 1 << 8;

    public boolean has(int capability) {
        return (bits & capability) != 0;
    }

    public static MudTuningCapabilities of(int... capabilities) {
        int bits = 0;
        for (int capability : capabilities) {
            bits |= capability;
        }
        return new MudTuningCapabilities(bits);
    }
}
