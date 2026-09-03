package com.fish.mirebound.coverage;

/** Decides whether a medium may spread into one canonical coverage cell. */
@FunctionalInterface
public interface MudCoveragePaintPredicate {
    boolean test(int cell, byte mediumId);
}
