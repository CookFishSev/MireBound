package com.fish.mirebound.client;

import com.fish.mirebound.mud.SinkingMedium;

/** Reuses a bounded set of weighted visual sources while rasterizing screen mud. */
final class ScreenMudSourceMixer {
    private static final int MAX_SOURCES = 16;

    private final SinkingMedium[] media = new SinkingMedium[MAX_SOURCES];
    private final long[] visualSources = new long[MAX_SOURCES];
    private final float[] weights = new float[MAX_SOURCES];
    private int size;

    void reset() {
        size = 0;
    }

    void add(SinkingMedium medium, long visualSource, float weight) {
        if (weight <= 1.0E-5F) {
            return;
        }
        SinkingMedium safeMedium = medium == null ? SinkingMedium.MUD : medium;
        for (int index = 0; index < size; index++) {
            if (media[index] == safeMedium && visualSources[index] == visualSource) {
                weights[index] += weight;
                return;
            }
        }
        if (size < MAX_SOURCES) {
            media[size] = safeMedium;
            visualSources[size] = visualSource;
            weights[size] = weight;
            size++;
            return;
        }

        int weakest = 0;
        for (int index = 1; index < size; index++) {
            if (weights[index] < weights[weakest]) {
                weakest = index;
            }
        }
        if (weight > weights[weakest]) {
            media[weakest] = safeMedium;
            visualSources[weakest] = visualSource;
            weights[weakest] = weight;
        }
    }

    int size() {
        return size;
    }

    SinkingMedium medium(int index) {
        return media[index];
    }

    long visualSource(int index) {
        return visualSources[index];
    }

    float weight(int index) {
        return weights[index];
    }
}
