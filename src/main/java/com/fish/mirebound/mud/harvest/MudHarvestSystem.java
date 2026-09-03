package com.fish.mirebound.mud.harvest;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysicsParameter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Applies configurable medium hardness without polling config or scanning terrain. */
public final class MudHarvestSystem {
    private static final float MAXIMUM_SAFE_BREAK_SPEED = 1024.0F;

    private MudHarvestSystem() {
    }

    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getState().getBlock() instanceof MudBlock mudBlock)) {
            return;
        }
        BlockPos pos = event.getPosition().orElse(null);
        if (mudBlock instanceof AdaptiveMudBlock
                && MudMediumRuntime.value(event.getEntity().level(), pos, mudBlock.medium(),
                        MudPhysicsParameter.HARVEST_OVERRIDE_SOURCE_ENABLED) < 0.5D) {
            return;
        }
        MudHarvestProfile profile = MudMediumRuntime.harvestProfile(
                event.getEntity().level(), pos, mudBlock.medium());
        ItemStack held = event.getEntity().getMainHandItem();
        boolean preferred = profile.preferredTool().matches(held);
        float targetItemSpeed = preferred
                ? profile.preferredTool().referenceDestroySpeed(held)
                : 1.0F;
        float observedItemSpeed = preferred
                ? targetItemSpeed
                : Math.max(1.0F, held.getDestroySpeed(event.getState()));
        double categoryMultiplier = held.isEmpty()
                ? profile.handSpeedMultiplier()
                : preferred
                        ? profile.preferredToolSpeedMultiplier()
                        : profile.otherToolSpeedMultiplier();
        event.setNewSpeed(scaledBreakSpeed(
                event.getNewSpeed(),
                mudBlock.harvestBaselineHardness(),
                profile.hardness(),
                observedItemSpeed,
                targetItemSpeed,
                categoryMultiplier));
    }

    static float scaledBreakSpeed(float originalSpeed, float baselineHardness,
            double configuredHardness, float observedItemSpeed,
            float targetItemSpeed, double categoryMultiplier) {
        if (!Float.isFinite(originalSpeed) || originalSpeed <= 0.0F
                || !Double.isFinite(configuredHardness) || configuredHardness <= 0.0D
                || !Double.isFinite(categoryMultiplier) || categoryMultiplier <= 0.0D) {
            return 0.0F;
        }
        double speed = originalSpeed
                * Math.max(0.001D, baselineHardness)
                / configuredHardness
                * Math.max(1.0D, targetItemSpeed)
                / Math.max(1.0D, observedItemSpeed)
                * categoryMultiplier;
        if (!Double.isFinite(speed)) {
            return MAXIMUM_SAFE_BREAK_SPEED;
        }
        return (float) Math.max(0.0D, Math.min(MAXIMUM_SAFE_BREAK_SPEED, speed));
    }
}
