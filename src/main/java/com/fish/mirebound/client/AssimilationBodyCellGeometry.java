package com.fish.mirebound.client;

import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.MudEntityGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/** Shared upright frozen-body projection for rescue cues and owner-side QTE aiming. */
final class AssimilationBodyCellGeometry {
    private AssimilationBodyCellGeometry() {
    }

    static Vec3 worldPoint(ClientAssimilationState.View view, int cell) {
        MudBodyPart part = MudSurfaceLayout.part(cell);
        MudSurface surface = MudSurfaceLayout.surface(cell);
        Minecraft minecraft = Minecraft.getInstance();
        double modelHeight = minecraft.player == null ? 1.8D
                : minecraft.player.getDimensions(Pose.STANDING).height();
        double scale = modelHeight / MudEntityGeometry.PLAYER_MODEL_HEIGHT_PIXELS;
        double pivot = part == MudBodyPart.HEAD ? 24.0D : 0.0D;
        float pitch = part == MudBodyPart.HEAD ? view.frozenPitch() : 0.0F;
        MudEntityGeometry.SamplingBasis basis = MudEntityGeometry.orientedBasis(
                view.anchor(), scale, view.frozenYaw(), pitch, pivot);
        Vec3 point = MudEntityGeometry.surfacePixelPoint(
                basis, part, surface, MudSurfaceLayout.row(cell), MudSurfaceLayout.column(cell));
        Vec3 bodyPivot = view.anchor().add(0.0D, modelHeight * 0.5D, 0.0D);
        Vec3 relative = point.subtract(bodyPivot)
                .xRot((float) Math.toRadians(view.bodyPitch()))
                .zRot((float) Math.toRadians(view.bodyRoll()));
        return bodyPivot.add(relative);
    }
}
