package com.fish.mirebound.content.mudwork;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class MudClodLauncher {
    private MudClodLauncher() {
    }

    static boolean launch(
            ServerPlayer player, ItemStack ammunition,
            float chargePower, boolean sling) {
        if (!(ammunition.getItem() instanceof MudBallItem mudBall)) {
            return false;
        }
        MudBallProjectile projectile = new MudBallProjectile(
                player.serverLevel(), player);
        projectile.setMedium(mudBall.medium());
        projectile.setItem(ammunition.copyWithCount(1));
        projectile.setChargePower(chargePower);
        projectile.setImpactFragments(
                MudSlingMechanics.fragmentCount(chargePower, sling));
        projectile.shootFromRotation(
                player, player.getXRot(), player.getYRot(), 0.0F,
                (float) MudSlingMechanics.launchSpeed(chargePower, sling),
                sling ? 0.35F : 0.75F);
        return player.serverLevel().addFreshEntity(projectile);
    }
}
