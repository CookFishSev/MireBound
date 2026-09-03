package com.fish.mirebound.client.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;

/** Retries adaptive Sable mesh invalidation while sub-level render data initializes. */
public final class SableAdaptiveMudInvalidationQueue {
    private static final int[] RETRY_DELAYS = {0, 1, 3, 7, 15, 31};
    private static final int MAX_PENDING_SECTIONS = 8192;
    private static final int PER_TICK_BUDGET = 96;
    private static final RetryQueue<SectionRequest> PENDING =
            new RetryQueue<>(RETRY_DELAYS, MAX_PENDING_SECTIONS);
    private static long clientTick;

    private SableAdaptiveMudInvalidationQueue() {
    }

    public static synchronized void schedule(
            ResourceLocation dimension, Collection<BlockPos> positions) {
        if (dimension == null || positions == null || positions.isEmpty()) {
            return;
        }
        for (BlockPos pos : positions) {
            if (pos != null) {
                PENDING.schedule(new SectionRequest(
                        dimension, SectionPos.asLong(pos), pos.immutable()), clientTick);
            }
        }
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        ResourceLocation dimension = minecraft.level.dimension().location();
        List<SectionRequest> due;
        synchronized (SableAdaptiveMudInvalidationQueue.class) {
            due = PENDING.pollDue(
                    clientTick++, PER_TICK_BUDGET,
                    request -> request.dimension.equals(dimension));
        }
        for (SectionRequest request : due) {
            if (SableAdaptiveMudRenderInvalidation.markSectionDirty(
                    minecraft.level, request.representativePos)) {
                synchronized (SableAdaptiveMudInvalidationQueue.class) {
                    PENDING.cancel(request);
                }
            }
        }
    }

    public static synchronized void reset() {
        PENDING.clear();
        clientTick = 0L;
    }

    static final class RetryQueue<K> {
        private final int[] retryDelays;
        private final int maxEntries;
        private final Map<K, RetryState> entries = new LinkedHashMap<>();

        RetryQueue(int[] retryDelays, int maxEntries) {
            if (retryDelays == null || retryDelays.length == 0 || maxEntries <= 0) {
                throw new IllegalArgumentException("Retry schedule and capacity must be positive");
            }
            this.retryDelays = retryDelays.clone();
            this.maxEntries = maxEntries;
        }

        void schedule(K key, long tick) {
            entries.remove(key);
            while (entries.size() >= maxEntries) {
                Iterator<K> iterator = entries.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
            entries.put(key, new RetryState(tick));
        }

        List<K> pollDue(long tick, int budget, Predicate<K> eligible) {
            if (budget <= 0 || entries.isEmpty()) {
                return List.of();
            }
            List<K> due = new ArrayList<>(Math.min(budget, entries.size()));
            Map<K, RetryState> rescheduled = new LinkedHashMap<>();
            Iterator<Map.Entry<K, RetryState>> iterator = entries.entrySet().iterator();
            while (iterator.hasNext() && due.size() < budget) {
                Map.Entry<K, RetryState> entry = iterator.next();
                RetryState state = entry.getValue();
                if (!eligible.test(entry.getKey())
                        || tick < state.startTick + retryDelays[state.attempt]) {
                    continue;
                }
                due.add(entry.getKey());
                iterator.remove();
                state.attempt++;
                if (state.attempt < retryDelays.length) {
                    rescheduled.put(entry.getKey(), state);
                }
            }
            entries.putAll(rescheduled);
            return List.copyOf(due);
        }

        int size() {
            return entries.size();
        }

        void cancel(K key) {
            entries.remove(key);
        }

        void clear() {
            entries.clear();
        }

        private static final class RetryState {
            private final long startTick;
            private int attempt;

            private RetryState(long startTick) {
                this.startTick = startTick;
            }
        }
    }

    private static final class SectionRequest {
        private final ResourceLocation dimension;
        private final long sectionPos;
        private final BlockPos representativePos;

        private SectionRequest(
                ResourceLocation dimension, long sectionPos, BlockPos representativePos) {
            this.dimension = dimension;
            this.sectionPos = sectionPos;
            this.representativePos = representativePos;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof SectionRequest request
                    && sectionPos == request.sectionPos
                    && dimension.equals(request.dimension);
        }

        @Override
        public int hashCode() {
            return 31 * dimension.hashCode() + Long.hashCode(sectionPos);
        }
    }
}
