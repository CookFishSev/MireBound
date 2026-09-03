package com.fish.mirebound.compat.sable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Owns the reflected Sable pose API and its class-level method caches. */
final class SablePoseTransform {
    private static final Map<Class<?>, Method> LOGICAL_POSE_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> RENDER_POSE_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, PoseMethods> POSE_METHODS = new ConcurrentHashMap<>();

    private SablePoseTransform() {
    }

    static Vec3 position(Object subLevel, Vec3 point, boolean inverse) {
        return position(subLevel, point, inverse, false);
    }

    static Vec3 renderPosition(Object subLevel, Vec3 point) {
        return position(subLevel, point, false, true);
    }

    private static Vec3 position(Object subLevel, Vec3 point, boolean inverse, boolean renderPose) {
        if (subLevel == null) {
            return null;
        }
        try {
            Object pose = renderPose ? renderPose(subLevel) : logicalPose(subLevel);
            if (pose == null) {
                return null;
            }
            PoseMethods methods = methods(pose);
            Method transform = inverse ? methods.inversePosition() : methods.position();
            return apply(pose, transform, point);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    static Vec3 direction(Object subLevel, Vec3 direction, boolean inverse) {
        if (subLevel == null) {
            return null;
        }
        try {
            Object pose = logicalPose(subLevel);
            if (pose == null) {
                return null;
            }
            PoseMethods methods = methods(pose);
            Method transform = inverse ? methods.inverseNormal() : methods.normal();
            return apply(pose, transform, direction);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    static SableCompat.RigidTransform rigid(Object subLevel) {
        if (subLevel == null) {
            return null;
        }
        try {
            Object pose = logicalPose(subLevel);
            if (pose == null) {
                return null;
            }
            PoseMethods methods = methods(pose);
            if (methods.position() == null || methods.inversePosition() == null) {
                return null;
            }
            return new SableCompat.RigidTransform(
                    pose,
                    methods.position(),
                    methods.inversePosition(),
                    methods.normal(),
                    methods.inverseNormal());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object logicalPose(Object subLevel) throws ReflectiveOperationException {
        Method logicalPose = LOGICAL_POSE_METHODS.computeIfAbsent(
                subLevel.getClass(), SablePoseTransform::findLogicalPoseMethod);
        return logicalPose.invoke(subLevel);
    }

    private static Object renderPose(Object subLevel) throws ReflectiveOperationException {
        Optional<Method> renderPose = RENDER_POSE_METHODS.computeIfAbsent(
                subLevel.getClass(), SablePoseTransform::findRenderPoseMethod);
        return renderPose.isPresent() ? renderPose.get().invoke(subLevel) : logicalPose(subLevel);
    }

    private static PoseMethods methods(Object pose) {
        return POSE_METHODS.computeIfAbsent(pose.getClass(), SablePoseTransform::findPoseMethods);
    }

    private static Vec3 apply(Object pose, Method transform, Vec3 point)
            throws ReflectiveOperationException {
        if (transform == null) {
            return null;
        }
        Vector3d vector = new Vector3d(point.x, point.y, point.z);
        transform.invoke(pose, vector);
        return new Vec3(vector.x, vector.y, vector.z);
    }

    private static Method findLogicalPoseMethod(Class<?> subLevelClass) {
        try {
            return subLevelClass.getMethod("logicalPose");
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Optional<Method> findRenderPoseMethod(Class<?> subLevelClass) {
        try {
            return Optional.of(subLevelClass.getMethod("renderPose"));
        } catch (NoSuchMethodException ignored) {
            return Optional.empty();
        }
    }

    private static PoseMethods findPoseMethods(Class<?> poseClass) {
        Method position = null;
        Method inversePosition = null;
        Method normal = null;
        Method inverseNormal = null;
        for (Method method : poseClass.getMethods()) {
            if (method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isAssignableFrom(Vector3d.class)) {
                continue;
            }
            switch (method.getName()) {
                case "transformPosition" -> position = method;
                case "transformPositionInverse" -> inversePosition = method;
                case "transformNormal" -> normal = method;
                case "transformNormalInverse" -> inverseNormal = method;
                default -> {
                }
            }
        }
        return new PoseMethods(position, inversePosition, normal, inverseNormal);
    }

    private record PoseMethods(Method position, Method inversePosition, Method normal, Method inverseNormal) {
    }
}
