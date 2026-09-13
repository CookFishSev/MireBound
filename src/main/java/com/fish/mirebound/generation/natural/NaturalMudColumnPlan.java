package com.fish.mirebound.generation.natural;

/** Fixed terrain thickness and independent per-layer sinking limits. */
record NaturalMudColumnPlan(int layerCount, int sinkingPixels) {
    static NaturalMudColumnPlan create(int replacementLayers, int availableLayers, double sinkingDepth) {
        int layers = Math.clamp(availableLayers, 0, replacementLayers);
        int pixels = Math.clamp((int) Math.round(sinkingDepth * 16), 0, layers * 16);
        return new NaturalMudColumnPlan(layers, pixels);
    }

    int depthPixels(int layer) {
        return Math.clamp(sinkingPixels - layer * 16, 1, 16);
    }

    boolean terminalLayer(int layer) {
        return layer >= Math.max(0, (sinkingPixels - 1) / 16);
    }
}
