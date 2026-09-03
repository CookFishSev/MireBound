package com.fish.mirebound.mud.flow;

/** Pure integer-volume rules shared by the runtime and unit tests. */
final class MudFlowTransfer {
    private MudFlowTransfer() {
    }

    static int downward(int sourcePixels, int targetPixels, int transferLimit) {
        return Math.min(Math.max(0, sourcePixels),
                Math.min(Math.max(0, 16 - targetPixels), Math.max(1, transferLimit)));
    }

    static int horizontal(int sourcePixels, int targetPixels, MudFlowProfile profile) {
        if (sourcePixels < profile.horizontalMinimumPixels()) {
            return 0;
        }
        int difference = sourcePixels - targetPixels;
        if (difference < profile.horizontalLevelDifference()) {
            return 0;
        }
        int equilibriumTransfer = difference / 2;
        if (equilibriumTransfer <= 0) {
            return 0;
        }
        return Math.min(sourcePixels,
                Math.min(16 - targetPixels,
                        Math.min(profile.pixelsPerTransfer(), equilibriumTransfer)));
    }
}
