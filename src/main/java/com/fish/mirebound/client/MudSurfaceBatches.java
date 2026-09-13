package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Frame-local buffer access and completion shared by all mud surface layers. */
final class MudSurfaceBatches<T> {
    private final Set<T> used = new LinkedHashSet<>();

    void begin() { used.clear(); }

    VertexConsumer buffer(T type, Function<T, VertexConsumer> source) {
        used.add(type);
        // Switching a shared render type or flushing it ends its old builder,
        // even within the same frame. Only the source knows which one is live.
        return source.apply(type);
    }

    void end(Consumer<T> finish) {
        try { used.forEach(finish); }
        finally { used.clear(); }
    }
}
