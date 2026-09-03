package com.fish.mirebound.client.compat;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.compat.sable.SableCompat;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Invalidates the Sable-owned terrain mesh after adaptive source data arrives. */
public final class SableAdaptiveMudRenderInvalidation {
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> RENDER_DATA_METHODS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> DIRTY_METHODS =
            new ConcurrentHashMap<>();
    private static final AtomicBoolean LOGGED_SUCCESS = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FAILURE = new AtomicBoolean();

    private SableAdaptiveMudRenderInvalidation() {
    }

    public static boolean markSectionDirty(Level level, BlockPos pos) {
        if (level == null || !level.isClientSide() || pos == null) {
            return false;
        }
        Object subLevel = SableCompat.subLevelAtStorage(level, pos);
        if (subLevel == null) {
            return false;
        }
        boolean invalidated = false;
        try {
            Object renderData = invokeNoArgs(subLevel, RENDER_DATA_METHODS, "getRenderData");
            if (renderData != null) {
                invalidated = invokeDirty(renderData, pos);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            invalidated = false;
        }
        if (invalidated && LOGGED_SUCCESS.compareAndSet(false, true)) {
            Mirebound.LOGGER.info("Sable adaptive terrain invalidation is active");
        } else if (!invalidated && LOGGED_FAILURE.compareAndSet(false, true)) {
            Mirebound.LOGGER.warn("Could not invalidate a Sable adaptive terrain section");
        }
        return invalidated;
    }

    private static boolean invokeDirty(Object renderData, BlockPos pos)
            throws ReflectiveOperationException {
        Optional<Method> method = DIRTY_METHODS.computeIfAbsent(renderData.getClass(), type ->
                findMethod(type, "setDirty", int.class, int.class, int.class, boolean.class));
        if (method.isEmpty()) {
            return false;
        }
        method.get().invoke(renderData,
                pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4, false);
        return true;
    }

    private static Object invokeNoArgs(Object target,
            ConcurrentHashMap<Class<?>, Optional<Method>> methods, String name)
            throws ReflectiveOperationException {
        Optional<Method> method = methods.computeIfAbsent(
                target.getClass(), type -> findMethod(type, name));
        return method.isEmpty() ? null : method.get().invoke(target);
    }

    private static Optional<Method> findMethod(
            Class<?> type, String name, Class<?>... parameters) {
        try {
            return Optional.of(type.getMethod(name, parameters));
        } catch (NoSuchMethodException | LinkageError ignored) {
            return Optional.empty();
        }
    }
}
