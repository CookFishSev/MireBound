package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Method;
import net.minecraft.client.player.LocalPlayer;

/** Loads the Sodium API-backed implementation only when Sodium is present. */
final class SodiumGeometryCaptureBridge {
    private static final Method WRAP_BODY;
    private static final Method WRAP_CAPE;
    private static final Method NOOP;
    private static VertexConsumer noopConsumer;

    static {
        Method body = null;
        Method cape = null;
        Method noop = null;
        try {
            Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter",
                    false, SodiumGeometryCaptureBridge.class.getClassLoader());
            Class<?> implementation = Class.forName(
                    "com.fish.mirebound.client.SodiumVertexGeometryCapture",
                    true, SodiumGeometryCaptureBridge.class.getClassLoader());
            body = implementation.getMethod("wrapBody", VertexConsumer.class, LocalPlayer.class);
            cape = implementation.getMethod("wrapCape", VertexConsumer.class, LocalPlayer.class);
            noop = implementation.getMethod("noop");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Sodium is optional; model-part capture remains available.
        }
        WRAP_BODY = body;
        WRAP_CAPE = cape;
        NOOP = noop;
    }

    private SodiumGeometryCaptureBridge() {
    }

    static boolean available() {
        return WRAP_BODY != null && WRAP_CAPE != null;
    }

    static VertexConsumer wrapBody(VertexConsumer delegate, LocalPlayer player) {
        return invoke(WRAP_BODY, delegate, player);
    }

    static VertexConsumer wrapCape(VertexConsumer delegate, LocalPlayer player) {
        return invoke(WRAP_CAPE, delegate, player);
    }

    static VertexConsumer noopConsumer(VertexConsumer fallback) {
        if (noopConsumer != null) {
            return noopConsumer;
        }
        if (NOOP == null) {
            return fallback;
        }
        try {
            noopConsumer = (VertexConsumer) NOOP.invoke(null);
            return noopConsumer == null ? fallback : noopConsumer;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fallback;
        }
    }

    private static VertexConsumer invoke(Method method,
            VertexConsumer delegate, LocalPlayer player) {
        if (method == null) {
            return null;
        }
        try {
            return (VertexConsumer) method.invoke(null, delegate, player);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
