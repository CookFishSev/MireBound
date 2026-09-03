package com.fish.mirebound.compat.sable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Lazily resolves the optional Sable helper API once per client or server process. */
final class SableReflectionApi {
    private static final String SABLE_CLASS = "dev.ryanhcode.sable.Sable";
    private static final String BOUNDING_BOX_CLASS =
            "dev.ryanhcode.sable.companion.math.BoundingBox3d";
    private static final String SUB_LEVEL_CONTAINER_CLASS =
            "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    private static volatile Api api;
    private static volatile boolean unavailable;

    private SableReflectionApi() {
    }

    static Api api() {
        Api current = api;
        if (current != null || unavailable) {
            return current;
        }
        synchronized (SableReflectionApi.class) {
            current = api;
            if (current != null || unavailable) {
                return current;
            }
            try {
                Class<?> sableClass = Class.forName(SABLE_CLASS);
                Field helperField = sableClass.getField("HELPER");
                Object helper = helperField.get(null);
                current = new Api(
                        helper,
                        findRunIncludingSubLevelsMethod(helper.getClass()),
                        findGetAllIntersectingMethod(helper.getClass()),
                        findEntityMethod(helper.getClass(), "getTrackingSubLevel"),
                        findEntityMethod(helper.getClass(), "getTrackingOrVehicleSubLevel"),
                        findProjectOutOfSubLevelMethod(helper.getClass()),
                        findGetFeetPosMethod(helper.getClass()),
                        findGetEyePositionInterpolatedMethod(helper.getClass()),
                        findBlockEntityMethod(helper.getClass(), "getContaining"),
                        findContainingPositionMethod(helper.getClass()),
                        findContainerMethod(),
                        findSubLevelByIdMethod(),
                        findBoundingBoxConstructor());
                api = current;
                return current;
            } catch (ReflectiveOperationException | LinkageError exception) {
                unavailable = true;
                return null;
            }
        }
    }

    private static Method findRunIncludingSubLevelsMethod(Class<?> helperClass)
            throws NoSuchMethodException {
        for (Method method : helperClass.getMethods()) {
            if (!method.getName().equals("runIncludingSubLevels") || method.getParameterCount() != 5) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters[0].isAssignableFrom(Level.class)
                    && parameters[1].isAssignableFrom(Vec3.class)
                    && parameters[2] == boolean.class
                    && parameters[4].isAssignableFrom(BiFunction.class)) {
                return method;
            }
        }
        throw new NoSuchMethodException(
                "runIncludingSubLevels(Level, Vec3/Position, boolean, SubLevelAccess, BiFunction)");
    }

    private static Method findEntityMethod(Class<?> helperClass, String name) {
        try {
            return helperClass.getMethod(name, Entity.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findProjectOutOfSubLevelMethod(Class<?> helperClass) {
        try {
            return helperClass.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findBlockEntityMethod(Class<?> helperClass, String name) {
        try {
            return helperClass.getMethod(name, BlockEntity.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findGetFeetPosMethod(Class<?> helperClass) {
        try {
            return helperClass.getMethod("getFeetPos", Entity.class, float.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findGetEyePositionInterpolatedMethod(Class<?> helperClass) {
        try {
            return helperClass.getMethod("getEyePositionInterpolated", Entity.class, float.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findGetAllIntersectingMethod(Class<?> helperClass) {
        for (Method method : helperClass.getMethods()) {
            if (method.getName().equals("getAllIntersecting")
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isAssignableFrom(Level.class)) {
                return method;
            }
        }
        return null;
    }

    private static Method findContainingPositionMethod(Class<?> helperClass) {
        try {
            return helperClass.getMethod("getContaining", Level.class, Vec3i.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findContainerMethod() {
        try {
            return Class.forName(SUB_LEVEL_CONTAINER_CLASS).getMethod("getContainer", Level.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findSubLevelByIdMethod() {
        try {
            return Class.forName(SUB_LEVEL_CONTAINER_CLASS).getMethod("getSubLevel", UUID.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Constructor<?> findBoundingBoxConstructor() {
        try {
            return Class.forName(BOUNDING_BOX_CLASS).getConstructor(
                    double.class, double.class, double.class,
                    double.class, double.class, double.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    static final class Api {
        final Object helper;
        final Method runIncludingSubLevels;
        final Method getAllIntersecting;
        final Method getTrackingSubLevel;
        final Method getTrackingOrVehicleSubLevel;
        final Method projectOutOfSubLevel;
        final Method getFeetPos;
        final Method getEyePositionInterpolated;
        final Method getContainingBlockEntity;
        final Method getContainingPosition;
        final Method getContainer;
        final Method getSubLevelById;
        final Constructor<?> boundingBoxConstructor;

        private Api(Object helper, Method runIncludingSubLevels, Method getAllIntersecting,
                Method getTrackingSubLevel, Method getTrackingOrVehicleSubLevel,
                Method projectOutOfSubLevel, Method getFeetPos,
                Method getEyePositionInterpolated, Method getContainingBlockEntity,
                Method getContainingPosition, Method getContainer, Method getSubLevelById,
                Constructor<?> boundingBoxConstructor) {
            this.helper = helper;
            this.runIncludingSubLevels = runIncludingSubLevels;
            this.getAllIntersecting = getAllIntersecting;
            this.getTrackingSubLevel = getTrackingSubLevel;
            this.getTrackingOrVehicleSubLevel = getTrackingOrVehicleSubLevel;
            this.projectOutOfSubLevel = projectOutOfSubLevel;
            this.getFeetPos = getFeetPos;
            this.getEyePositionInterpolated = getEyePositionInterpolated;
            this.getContainingBlockEntity = getContainingBlockEntity;
            this.getContainingPosition = getContainingPosition;
            this.getContainer = getContainer;
            this.getSubLevelById = getSubLevelById;
            this.boundingBoxConstructor = boundingBoxConstructor;
        }
    }
}
