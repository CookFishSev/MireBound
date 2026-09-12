package com.fish.mirebound.rope;

/** Stable endpoint identity, independent of a rope's segment count. */
public record RopeEndpoint(int ropeId, boolean start) {
    public int point(RopeChain chain) {
        return start ? 0 : chain.segmentCount();
    }

    public int segment(RopeChain chain) {
        return start ? 0 : chain.segmentCount() - 1;
    }
}
