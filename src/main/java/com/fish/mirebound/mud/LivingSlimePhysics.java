package com.fish.mirebound.mud;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class LivingSlimePhysics {
    private LivingSlimePhysics() {
    }

    static void apply(Player player, BlockState state, double depth, double depthFactor,
            double availableDepth, MudPlayerData data) {
        apply(player, state, depth, depthFactor, availableDepth, data,
                MudPhysicsProfiles.livingSlime(player));
    }

    static void apply(Player player, BlockState state, double depth, double depthFactor,
            double availableDepth, MudPlayerData data, LivingSlimePhysicsProfile profile) {
        apply(player, depth, availableDepth, data.livingSlimeState, data,
                player.position(), "world", false, profile);
    }

    static void applyInFrame(Player player, BlockState state, double depth, double depthFactor,
            double availableDepth, MudPlayerData data, Vec3 physicsPosition) {
        applyInFrame(player, state, depth, depthFactor, availableDepth, data, physicsPosition,
                MudPhysicsProfiles.livingSlime(player));
    }

    static void applyInFrame(Player player, BlockState state, double depth, double depthFactor,
            double availableDepth, MudPlayerData data, Vec3 physicsPosition,
            LivingSlimePhysicsProfile profile) {
        apply(player, depth, availableDepth, data.livingSlimeState, data,
                physicsPosition, "sable-local", false, profile);
    }

    static void applyClient(Player player, BlockState state, double depth, double depthFactor,
            double availableDepth, LivingSlimeRuntimeState runtimeState,
            boolean struggleCarry) {
        applyClient(player, state, depth, depthFactor, availableDepth, runtimeState,
                struggleCarry, MudPhysicsProfiles.livingSlime(player));
    }

    static void applyClient(Player player, BlockState state, double depth, double depthFactor,
            double availableDepth, LivingSlimeRuntimeState runtimeState,
            boolean struggleCarry, LivingSlimePhysicsProfile profile) {
        apply(player, depth, availableDepth, runtimeState, null,
                player.position(), "client", struggleCarry, profile);
    }

    static void applyClientInFrame(Player player, BlockState state, double depth, double depthFactor,
            double availableDepth, LivingSlimeRuntimeState runtimeState, Vec3 physicsPosition,
            boolean struggleCarry) {
        applyClientInFrame(player, state, depth, depthFactor, availableDepth, runtimeState,
                physicsPosition, struggleCarry, MudPhysicsProfiles.livingSlime(player));
    }

    static void applyClientInFrame(Player player, BlockState state, double depth, double depthFactor,
            double availableDepth, LivingSlimeRuntimeState runtimeState, Vec3 physicsPosition,
            boolean struggleCarry, LivingSlimePhysicsProfile profile) {
        apply(player, depth, availableDepth, runtimeState, null,
                physicsPosition, "client-sable-local", struggleCarry, profile);
    }

    private static void apply(Player player, double depth, double availableDepth,
            LivingSlimeRuntimeState runtimeState, MudPlayerData data,
            Vec3 physicsPosition, String frame, boolean clientStruggleCarry,
            LivingSlimePhysicsProfile profile) {
        Vec3 motion = player.getDeltaMovement();
        double blockDepth = Math.max(0.0D, depth);
        double horizontalSpeed = motion.horizontalDistance();
        boolean struggleCarry = (data != null
                && data.liftTicks > 0
                && motion.y > 0.0D)
                || (clientStruggleCarry && motion.y > 0.0D);

        boolean newContact = runtimeState.touch(
                physicsPosition.x, physicsPosition.y, physicsPosition.z);
        if (newContact) {
            runtimeState.impactEnergy = LivingSlimeSolver.captureImpact(profile, motion.y);
        }
        double impactEnergyBefore = runtimeState.impactEnergy;
        double anchorDeltaX = runtimeState.anchorX - physicsPosition.x;
        double anchorDeltaY = runtimeState.anchorY - physicsPosition.y;
        double anchorDeltaZ = runtimeState.anchorZ - physicsPosition.z;
        MudEnchantmentEffects.Modifiers enchantments = MudEnchantmentEffects.mudWalker(player);
        LivingSlimeSolver.Result result = LivingSlimeSolver.solve(
                profile,
                new LivingSlimeSolver.Input(
                        blockDepth,
                        availableDepth,
                        Math.max(0.10D, player.getDimensions(Pose.STANDING).height()),
                        motion.x,
                        motion.y,
                        motion.z,
                        horizontalSpeed,
                        anchorDeltaX,
                        anchorDeltaY,
                        anchorDeltaZ,
                        impactEnergyBefore,
                        player.isShiftKeyDown(),
                        struggleCarry,
                        enchantments.depthLimitScale(),
                        enchantments.walkRestoration()));

        runtimeState.impactEnergy = result.impactEnergy();

        double x = result.motionX();
        double z = result.motionZ();
        double horizontalLimit = Math.max(0.10D, horizontalSpeed);
        double adjustedSpeed = Math.sqrt(x * x + z * z);
        if (adjustedSpeed > horizontalLimit && adjustedSpeed > 1.0E-8D) {
            double scale = horizontalLimit / adjustedSpeed;
            x *= scale;
            z *= scale;
        }

        runtimeState.follow(
                physicsPosition.x,
                physicsPosition.y,
                physicsPosition.z,
                result.anchorFollow());

        player.setDeltaMovement(x, result.motionY(), z);
        player.hasImpulse = true;
        player.resetFallDistance();
        if (Math.abs(result.motionY()) > 1.0E-6D) {
            player.setOnGround(false);
        }
        boolean compressed = anchorDeltaY > 0.015D;
        boolean rebounding = result.motionY() > 0.012D && motion.y <= 0.0D;
        if (rebounding && player instanceof ServerPlayer serverPlayer) {
            float strength = (float) Mth.clamp(
                    result.motionY() / Math.max(profile.maxUpSpeed, 0.001D),
                    0.0D,
                    1.0D);
            serverPlayer.level().playSound(
                    null,
                    serverPlayer.blockPosition(),
                    SoundEvents.SLIME_SQUISH,
                    SoundSource.BLOCKS,
                    0.22F + strength * 0.28F,
                    0.82F + strength * 0.22F);
        }

        if (data != null) {
            data.debugColumnDepth = availableDepth;
            data.debugSinkLimit = result.columnLimit();
            data.debugRemainingDepth = result.remainingDepth();
            data.debugYBefore = motion.y;
            data.debugYAfter = result.motionY();
            data.debugHorizontalSpeed = horizontalSpeed;
            data.debugSinkStep = Math.max(0.0D, -result.motionY());
            data.debugWalkScale = result.walkScale();
            data.debugVerticalScale = result.verticalRetention();
            data.agitation = 0.0F;
            if (player instanceof ServerPlayer serverPlayer) {
                PhysicsTraceLog.traceLivingSlime(
                        serverPlayer,
                        frame,
                        blockDepth,
                        availableDepth,
                        result.columnLimit(),
                        result.elasticDepth(),
                        motion,
                        player.getDeltaMovement(),
                        new Vec3(anchorDeltaX, anchorDeltaY, anchorDeltaZ),
                        compressed,
                        rebounding,
                        profile.baseSinkBias,
                        result.walkScale(),
                        result.verticalRetention(),
                        impactEnergyBefore,
                        result.impactEnergy(),
                        result.impactReleased(),
                        data.struggleHold,
                        data.liftTicks);
            }
        }
    }

    static double struggleImpulse(Player player, double blockDepth,
            double availableDepth, double playerHeight, double charge) {
        LivingSlimePhysicsProfile profile = MudPhysicsProfiles.livingSlime(player);
        return struggleImpulse(profile, blockDepth, availableDepth, charge);
    }

    static double struggleImpulse(LivingSlimePhysicsProfile profile, double blockDepth,
            double availableDepth, double charge) {
        double columnDepth = Math.max(
                profile.minColumnDepth,
                availableDepth - profile.columnMargin);
        double depthPressure = smooth(Mth.clamp(
                blockDepth / columnDepth, 0.0D, 1.0D));
        double chargeCurve = smooth(Mth.clamp(charge, 0.0D, 1.0D));
        double impulse = Mth.lerp(
                chargeCurve, profile.struggleMin, profile.struggleMax)
                * Mth.lerp(depthPressure, 1.0D, profile.struggleDeepMultiplier);
        return Mth.clamp(impulse, 0.0D, struggleUpCap(profile, charge));
    }

    static double struggleLaunchVelocity(Player player, double currentMotionY, double impulse) {
        return struggleLaunchVelocity(MudPhysicsProfiles.livingSlime(player), currentMotionY, impulse);
    }

    static double struggleLaunchVelocity(LivingSlimePhysicsProfile profile,
            double currentMotionY, double impulse) {
        return Math.max(0.0D, currentMotionY) * profile.struggleExistingUpwardRetention + impulse;
    }

    static int struggleLiftTicks(Player player, double charge) {
        return struggleLiftTicks(MudPhysicsProfiles.livingSlime(player), charge);
    }

    static int struggleLiftTicks(LivingSlimePhysicsProfile profile, double charge) {
        double progress = smooth(Mth.clamp(charge, 0.0D, 1.0D));
        return Mth.clamp(
                (int) Math.round(Mth.lerp(
                        progress,
                        profile.struggleLiftTicksMin,
                        profile.struggleLiftTicksMax)),
                profile.struggleLiftTicksMin,
                profile.struggleLiftTicksMax);
    }

    static double struggleUpCap(Player player, double charge) {
        return struggleUpCap(MudPhysicsProfiles.livingSlime(player), charge);
    }

    private static double struggleUpCap(
            LivingSlimePhysicsProfile profile, double charge) {
        return Mth.clamp(
                profile.struggleMin
                        + charge * (profile.struggleMax - profile.struggleMin),
                profile.struggleMin,
                profile.struggleMax);
    }

    private static double smooth(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }
}
