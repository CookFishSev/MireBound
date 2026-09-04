package com.fish.mirebound.client.rope;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/** Applies the local player's two-arm rope handling pose in third person. */
public final class RopePlayerAnimation {
    private static final float ARM_LIFT = (float) (Math.PI / 2.0D);
    private static final float ARM_SPREAD = (float) (Math.PI / 12.0D);

    private RopePlayerAnimation() {
    }

    public static void applyAfterSetup(
            AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model) {
        if (!ClientRopes.isDragging(player)) {
            return;
        }
        float armRotation = Mth.clamp(
                model.head.xRot - ARM_LIFT, -2.4F, 3.3F);
        applyArm(model.rightArm, armRotation, model.head.yRot - ARM_SPREAD);
        applyArm(model.leftArm, armRotation, model.head.yRot + ARM_SPREAD);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    private static void applyArm(ModelPart arm, float xRotation, float yRotation) {
        arm.xRot = xRotation;
        arm.yRot = yRotation;
        arm.zRot = 0.0F;
    }
}
