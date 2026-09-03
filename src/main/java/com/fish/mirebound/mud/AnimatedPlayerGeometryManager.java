package com.fish.mirebound.mud;

import com.fish.mirebound.network.payload.PlayerGeometryPayload;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Validates client-rendered player geometry before exposing it to server contact systems. */
public final class AnimatedPlayerGeometryManager {
    private static final double MAX_ORIGIN_ERROR_SQR = 16.0D;
    private static final double MAX_CENTER_OFFSET_SQR = 16.0D;
    private static final double MAX_AXIS_DOT = 0.36D;

    private AnimatedPlayerGeometryManager() {
    }

    public static void handle(ServerPlayer player, PlayerGeometryPayload payload) {
        if (!player.isAlive() || player.isSpectator() || MudPhysics.isPollutionSuppressed(player)
                || payload.origin() == null || !finite(payload.origin())
                || player.position().distanceToSqr(payload.origin()) > MAX_ORIGIN_ERROR_SQR) {
            return;
        }

        Vec3 currentOrigin = player.position();
        if (!payload.parts().isEmpty()) {
            AnimatedPlayerGeometry.PartPose[] poses = validateBody(payload, currentOrigin);
            if (poses != null) {
                AnimatedPlayerGeometry.updateBody(player, poses);
            }
        }
        if (payload.cape() != null) {
            AnimatedPlayerGeometry.CapePose cape = validateCape(currentOrigin, payload.cape());
            if (cape != null) {
                AnimatedPlayerGeometry.updateCape(player, cape);
            }
        }
    }

    private static AnimatedPlayerGeometry.PartPose[] validateBody(
            PlayerGeometryPayload payload, Vec3 currentOrigin) {
        if (payload.parts().size() != MudBodyPart.COUNT) {
            return null;
        }
        AnimatedPlayerGeometry.PartPose[] poses = new AnimatedPlayerGeometry.PartPose[MudBodyPart.COUNT];
        EnumSet<MudBodyPart> seen = EnumSet.noneOf(MudBodyPart.class);
        for (PlayerGeometryPayload.Part encoded : payload.parts()) {
            if (encoded.partId() < 0 || encoded.partId() >= MudBodyPart.COUNT) {
                return null;
            }
            MudBodyPart part = MudBodyPart.values()[encoded.partId()];
            if (!seen.add(part) || !validOffset(encoded.centerOffset())
                    || !validPartAxes(part, encoded.halfSide(), encoded.halfUp(), encoded.halfForward())) {
                return null;
            }
            poses[part.ordinal()] = new AnimatedPlayerGeometry.PartPose(
                    currentOrigin.add(encoded.centerOffset()),
                    encoded.halfSide(), encoded.halfUp(), encoded.halfForward());
        }
        return poses;
    }

    private static AnimatedPlayerGeometry.CapePose validateCape(Vec3 origin,
            PlayerGeometryPayload.Cape encoded) {
        if (!validOffset(encoded.rootOffset()) || !finite(encoded.side())
                || !finite(encoded.down()) || !finite(encoded.normal())
                || !Float.isFinite(encoded.scale()) || encoded.scale() < 0.025F
                || encoded.scale() > 0.12F
                || !unit(encoded.side()) || !unit(encoded.down()) || !unit(encoded.normal())
                || !orthogonal(encoded.side(), encoded.down(), encoded.normal())) {
            return null;
        }
        return new AnimatedPlayerGeometry.CapePose(origin.add(encoded.rootOffset()),
                encoded.side(), encoded.down(), encoded.normal(), encoded.scale());
    }

    private static boolean validPartAxes(MudBodyPart part, Vec3 side, Vec3 up, Vec3 forward) {
        if (!finite(side) || !finite(up) || !finite(forward)
                || !orthogonal(side, up, forward)) {
            return false;
        }
        double width = side.length();
        double height = up.length();
        double depth = forward.length();
        double expectedWidthMaximum = part == MudBodyPart.HEAD || part == MudBodyPart.BODY ? 0.42D : 0.28D;
        double expectedHeightMaximum = part == MudBodyPart.HEAD ? 0.42D : 0.62D;
        double expectedDepthMaximum = part == MudBodyPart.HEAD ? 0.42D : 0.28D;
        return width >= 0.035D && width <= expectedWidthMaximum
                && height >= 0.12D && height <= expectedHeightMaximum
                && depth >= 0.035D && depth <= expectedDepthMaximum;
    }

    private static boolean orthogonal(Vec3 side, Vec3 up, Vec3 forward) {
        if (side.lengthSqr() < 1.0E-8D || up.lengthSqr() < 1.0E-8D
                || forward.lengthSqr() < 1.0E-8D) {
            return false;
        }
        Vec3 s = side.normalize();
        Vec3 u = up.normalize();
        Vec3 f = forward.normalize();
        return Math.abs(s.dot(u)) <= MAX_AXIS_DOT
                && Math.abs(s.dot(f)) <= MAX_AXIS_DOT
                && Math.abs(u.dot(f)) <= MAX_AXIS_DOT;
    }

    private static boolean unit(Vec3 value) {
        return Math.abs(value.lengthSqr() - 1.0D) <= 0.20D;
    }

    private static boolean validOffset(Vec3 offset) {
        return finite(offset) && offset.lengthSqr() <= MAX_CENTER_OFFSET_SQR;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
