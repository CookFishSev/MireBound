package com.fish.mirebound.mud;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** The vanilla cape pose expressed as the same 10x16 plane used by mud coverage. */
public final class MudCapeGeometry {
    private static final double MODEL_ORIGIN_HEIGHT_PIXELS = 24.0D;
    private static final double CAPE_CENTER_Z_PIXELS = -0.5D;
    private static final double LAYER_BACK_OFFSET_PIXELS = 2.0D;
    private static final double HALF_THICKNESS_PIXELS = 0.52D;

    private MudCapeGeometry() {
    }

    public static CapePose pose(Player player, float partialTick) {
        double cloakX = Mth.lerp(partialTick, player.xCloakO, player.xCloak)
                - Mth.lerp(partialTick, player.xo, player.getX());
        double cloakY = Mth.lerp(partialTick, player.yCloakO, player.yCloak)
                - Mth.lerp(partialTick, player.yo, player.getY());
        double cloakZ = Mth.lerp(partialTick, player.zCloakO, player.zCloak)
                - Mth.lerp(partialTick, player.zo, player.getZ());
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double sin = Mth.sin(bodyYaw * Mth.DEG_TO_RAD);
        double negativeCos = -Mth.cos(bodyYaw * Mth.DEG_TO_RAD);
        float vertical = Mth.clamp((float) cloakY * 10.0F, -6.0F, 32.0F);
        float backward = Mth.clamp(
                (float) (cloakX * sin + cloakZ * negativeCos) * 100.0F, 0.0F, 150.0F);
        float sideways = Mth.clamp(
                (float) (cloakX * negativeCos - cloakZ * sin) * 100.0F, -20.0F, 20.0F);
        float bob = Mth.lerp(partialTick, player.oBob, player.bob);
        vertical += Mth.sin(Mth.lerp(partialTick, player.walkDistO, player.walkDist) * 6.0F)
                * 32.0F * bob;
        if (player.isCrouching()) {
            vertical += 25.0F;
        }

        float modelY;
        float modelZ;
        if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            modelY = player.isCrouching() ? 1.85F : 0.0F;
            modelZ = player.isCrouching() ? 1.4F : 0.0F;
        } else {
            modelY = player.isCrouching() ? 0.8F : -0.85F;
            modelZ = player.isCrouching() ? 0.3F : -1.1F;
        }
        return new CapePose(
                6.0F + backward * 0.5F + vertical,
                sideways * 0.5F,
                180.0F - sideways * 0.5F,
                bodyYaw,
                modelY,
                modelZ);
    }

    public static CapeBasis basis(Player player) {
        return basis(player.position(), player.getBbHeight() / MudEntityGeometry.PLAYER_MODEL_HEIGHT_PIXELS,
                pose(player, 1.0F));
    }

    public static CapeBasis basis(Vec3 feet, double modelScale, CapePose pose) {
        MudEntityGeometry.SamplingBasis body = MudEntityGeometry.orientedBasis(
                feet, modelScale, pose.bodyYawDegrees(), 0.0F, 0.0D);
        Vec3 topCenter = transformPoint(body, pose, 0.0D, 0.0D, CAPE_CENTER_Z_PIXELS);
        Vec3 side = transformVector(body, pose, new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        Vec3 down = transformVector(body, pose, new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 normal = side.cross(down).normalize();
        Vec3 expectedBack = body.forward().scale(-1.0D);
        if (normal.dot(expectedBack) < 0.0D) {
            normal = normal.scale(-1.0D);
        }
        return new CapeBasis(topCenter, side, down, normal, modelScale);
    }

    public static Vec3 centerPoint(CapeBasis basis, int row, int column) {
        double sidePixels = column + 0.5D - MudCapeLayout.COLUMNS * 0.5D;
        double downPixels = row + 0.5D;
        return basis.root()
                .add(basis.side().scale(sidePixels * basis.scale()))
                .add(basis.down().scale(downPixels * basis.scale()));
    }

    public static Vec3 frontProbe(CapeBasis basis, int row, int column) {
        return centerPoint(basis, row, column)
                .add(basis.normal().scale(HALF_THICKNESS_PIXELS * basis.scale()));
    }

    public static Vec3 backProbe(CapeBasis basis, int row, int column) {
        return centerPoint(basis, row, column)
                .add(basis.normal().scale(-HALF_THICKNESS_PIXELS * basis.scale()));
    }

    public static Vec3 gridPoint(CapeBasis basis, int rowBoundary, int columnBoundary,
            boolean front) {
        double sidePixels = Mth.clamp(columnBoundary, 0, MudCapeLayout.COLUMNS)
                - MudCapeLayout.COLUMNS * 0.5D;
        double downPixels = Mth.clamp(rowBoundary, 0, MudCapeLayout.ROWS);
        double normalOffset = (front ? HALF_THICKNESS_PIXELS : -HALF_THICKNESS_PIXELS)
                * basis.scale();
        return basis.root()
                .add(basis.side().scale(sidePixels * basis.scale()))
                .add(basis.down().scale(downPixels * basis.scale()))
                .add(basis.normal().scale(normalOffset));
    }

    private static Vec3 transformPoint(MudEntityGeometry.SamplingBasis body,
            CapePose pose, double x, double y, double z) {
        Vec3 transformed = rotate(pose, new Vec3(
                x,
                y + pose.modelOffsetY(),
                z + pose.modelOffsetZ())).add(0.0D, 0.0D, LAYER_BACK_OFFSET_PIXELS);
        return body.pivot()
                .add(body.up().scale(MODEL_ORIGIN_HEIGHT_PIXELS * body.scale()))
                .add(body.side().scale(transformed.x * body.scale()))
                .add(body.up().scale(-transformed.y * body.scale()))
                .add(body.forward().scale(-transformed.z * body.scale()));
    }

    private static Vec3 transformVector(MudEntityGeometry.SamplingBasis body,
            CapePose pose, Vec3 vector) {
        Vec3 transformed = rotate(pose, vector);
        return body.side().scale(transformed.x)
                .add(body.up().scale(-transformed.y))
                .add(body.forward().scale(-transformed.z));
    }

    private static Vec3 rotate(CapePose pose, Vec3 vector) {
        Vec3 yRotated = rotateY(vector, pose.yDegrees() * Mth.DEG_TO_RAD);
        Vec3 zRotated = rotateZ(yRotated, pose.zDegrees() * Mth.DEG_TO_RAD);
        return rotateX(zRotated, pose.xDegrees() * Mth.DEG_TO_RAD);
    }

    private static Vec3 rotateX(Vec3 value, float angle) {
        double sin = Mth.sin(angle);
        double cos = Mth.cos(angle);
        return new Vec3(value.x, value.y * cos - value.z * sin,
                value.y * sin + value.z * cos);
    }

    private static Vec3 rotateY(Vec3 value, float angle) {
        double sin = Mth.sin(angle);
        double cos = Mth.cos(angle);
        return new Vec3(value.x * cos + value.z * sin, value.y,
                -value.x * sin + value.z * cos);
    }

    private static Vec3 rotateZ(Vec3 value, float angle) {
        double sin = Mth.sin(angle);
        double cos = Mth.cos(angle);
        return new Vec3(value.x * cos - value.y * sin,
                value.x * sin + value.y * cos, value.z);
    }

    public record CapePose(float xDegrees, float zDegrees, float yDegrees,
            float bodyYawDegrees, float modelOffsetY, float modelOffsetZ) {
    }

    public record CapeBasis(Vec3 root, Vec3 side, Vec3 down, Vec3 normal, double scale) {
    }
}
