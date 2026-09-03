package com.fish.mirebound.client;

import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/** Applies the probe's view-facing arm pose to the third-person player model. */
public final class MudProbePlayerAnimation {
    private static final float PROBE_ARM_ROTATION = (float) (Math.PI / 2.0D);
    private static final float CROUCH_ADJUSTMENT = (float) (Math.PI / 12.0D);
    private static final float SIDE_OFFSET = (float) (Math.PI / 12.0D);

    private MudProbePlayerAnimation() {
    }

    public static void applyAfterSetup(
            AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model) {
        if (!player.isUsingItem()
                || player.getUseItem().getItem() != ModBlocks.MUD_PROBE.get()) {
            return;
        }

        HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        ModelPart armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        armPart.xRot = Mth.clamp(
                model.head.xRot - PROBE_ARM_ROTATION
                        - (player.isCrouching() ? CROUCH_ADJUSTMENT : 0.0F),
                -2.4F, 3.3F);
        armPart.yRot = model.head.yRot
                + (arm == HumanoidArm.RIGHT ? -SIDE_OFFSET : SIDE_OFFSET);
        armPart.zRot = 0.0F;
    }
}
