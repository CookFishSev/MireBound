package com.fish.mirebound.mud.flow;

/** Pure ordering rules for one finite-volume flow step. */
final class MudFlowStepRules {
    private MudFlowStepRules() {
    }

    static boolean needsVerticalResolution(
            boolean belowReadable, boolean belowAcceptsMud, int belowPixels) {
        return !belowReadable || belowAcceptsMud && belowPixels < 16;
    }

    static boolean canDisplaceDecoration(
            boolean redstoneComponent, boolean hasBlockEntity, boolean containsFluid,
            boolean replaceable, boolean fragileDecoration) {
        return !redstoneComponent && !hasBlockEntity && !containsFluid
                && (replaceable || fragileDecoration);
    }
}
