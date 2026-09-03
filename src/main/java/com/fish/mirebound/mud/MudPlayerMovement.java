package com.fish.mirebound.mud;

import static com.fish.mirebound.physics.MudMovementControl.clearMudMovement;
import static com.fish.mirebound.physics.MudMovementControl.enableMudGravityControl;
import static com.fish.mirebound.physics.MudMovementControl.updateMudMovement;

import com.fish.mirebound.registry.ModCriteria;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.rope.RopeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Shared server/client sinking solver application and Sable frame transforms. */
final class MudPlayerMovement {
    private static final double ZERO_DEPTH_EPSILON = 1.0E-6D;

    private MudPlayerMovement() {
    }

    static void applySablePlayerMovement(ServerPlayer player, MudPlayerData data, MudContact contact) {
        Vec3 frameMotion = toSurfaceFrame(player.getDeltaMovement(), contact);
        if (frameMotion == null) {
            applyPlayerMovement(player, contact.state(), contact.depth(), contact.depthFactor(),
                    contact.horizontalCoverage(), contact.availableDepth(),
                    contact.layerTopDepth(), contact.layerDepth(), contact.hasDeeperLayer(), data,
                    contact.physicsMedium(), contact.physicsProfilePos(), "world-fallback");
            return;
        }

        RopeRuntime.RescuePull worldRescuePull = RopeRuntime.rescuePull(
                player, data != null && data.holdingStruggle);
        RopeRuntime.RescuePull frameRescuePull = toSurfaceFrame(
                worldRescuePull, contact);

        Vec3 originalWorldMotion = player.getDeltaMovement();
        player.setDeltaMovement(frameMotion);
        if (contact.physicsMedium() == SinkingMedium.LIVING_SLIME) {
            LivingSlimePhysicsProfile slimeProfile = resolveLivingSlimeProfile(player, contact.physicsProfilePos());
            if (data.livingSlimeState.anchorActive
                    && !data.livingSlimeState.localFrame) {
                data.resetLivingSlimeState();
            }
            data.livingSlimeState.localFrame = true;
            enableMudGravityControl(player, data);
            prepareLivingSlimeStruggle(player, contact.depth(), contact.availableDepth(), data, slimeProfile);
            data.physicsMedium = SinkingMedium.LIVING_SLIME;
            LivingSlimePhysics.applyInFrame(player, contact.state(), contact.depth(), contact.depthFactor(),
                    contact.availableDepth(), data, new Vec3(0.0D, -contact.depth(), 0.0D), slimeProfile);
        } else {
            applyPlayerMovement(player, contact.state(), contact.depth(), contact.depthFactor(),
                    contact.horizontalCoverage(), contact.availableDepth(),
                    contact.layerTopDepth(), contact.layerDepth(), contact.hasDeeperLayer(), data,
                    contact.physicsMedium(), contact.physicsProfilePos(), "sable-gravity-frame",
                    frameRescuePull);
        }

        Vec3 adjustedFrameMotion = player.getDeltaMovement();
        Vec3 adjustedWorldMotion = fromSurfaceFrame(adjustedFrameMotion, contact);
        player.setDeltaMovement(adjustedWorldMotion == null ? originalWorldMotion : adjustedWorldMotion);
        if (contact.physicsMedium() != SinkingMedium.LIVING_SLIME
                || Math.abs(adjustedFrameMotion.y) <= 1.0E-6D) {
            player.setOnGround(true);
        } else {
            player.setOnGround(false);
        }
        player.hasImpulse = true;
    }

    static void applyClientSablePlayerMovement(
            Player player, MudContact contact, MudClientPhysics.State clientState) {
        Vec3 frameMotion = toSurfaceFrame(player.getDeltaMovement(), contact);
        if (frameMotion == null) {
            return;
        }

        Vec3 originalWorldMotion = player.getDeltaMovement();
        player.setDeltaMovement(frameMotion);
        applyClientPlayerMovement(
                player,
                contact.state(),
                contact.depth(),
                contact.depthFactor(),
                contact.horizontalCoverage(),
                contact.availableDepth(),
                contact.layerTopDepth(),
                contact.layerDepth(),
                contact.hasDeeperLayer(),
                contact.physicsMedium(),
                contact.physicsProfilePos(),
                clientState,
                new Vec3(0.0D, -contact.depth(), 0.0D),
                true);
        Vec3 adjustedFrameMotion = player.getDeltaMovement();
        Vec3 adjustedWorldMotion = fromSurfaceFrame(adjustedFrameMotion, contact);
        player.setDeltaMovement(adjustedWorldMotion == null ? originalWorldMotion : adjustedWorldMotion);
        if (contact.physicsMedium() != SinkingMedium.LIVING_SLIME
                || Math.abs(adjustedFrameMotion.y) <= 1.0E-6D) {
            player.setOnGround(true);
        } else {
            player.setOnGround(false);
        }
        player.hasImpulse = true;
    }

    private static Vec3 toSurfaceFrame(Vec3 worldVector, MudContact contact) {
        Vec3 axisX = contact.surfaceAxisX();
        Vec3 normal = contact.surfaceNormal();
        Vec3 axisZ = contact.surfaceAxisZ();
        if (axisX == null || normal == null || axisZ == null) {
            return null;
        }
        return new Vec3(
                worldVector.dot(axisX),
                worldVector.dot(normal),
                worldVector.dot(axisZ));
    }

    private static Vec3 fromSurfaceFrame(Vec3 frameVector, MudContact contact) {
        Vec3 axisX = contact.surfaceAxisX();
        Vec3 normal = contact.surfaceNormal();
        Vec3 axisZ = contact.surfaceAxisZ();
        if (axisX == null || normal == null || axisZ == null) {
            return null;
        }
        return axisX.scale(frameVector.x)
                .add(normal.scale(frameVector.y))
                .add(axisZ.scale(frameVector.z));
    }

    private static RopeRuntime.RescuePull toSurfaceFrame(
            RopeRuntime.RescuePull worldPull, MudContact contact) {
        if (worldPull == null || !worldPull.active()) {
            return RopeRuntime.RescuePull.NONE;
        }
        Vec3 frameMotion = toSurfaceFrame(worldPull.motion(), contact);
        return frameMotion == null
                ? RopeRuntime.RescuePull.NONE
                : new RopeRuntime.RescuePull(true, frameMotion,
                        worldPull.sinkRelief(), worldPull.tautness());
    }

    static boolean correctZeroDepthPenetration(Player player, MudContact contact) {
        if (contact.physicsMedium() == SinkingMedium.LIVING_SLIME) {
            return false;
        }
        SinkingPhysicsProfile profile = MudMediumRuntime.ordinaryProfile(
                player.level(), contact.physicsProfilePos(), contact.physicsMedium(),
                MudPhysicsProfiles.ordinary(player, contact.physicsMedium()));
        double correction = zeroDepthCorrection(profile, contact.depth(),
                contact.availableDepth(), contact.layerTopDepth(), contact.layerDepth(),
                contact.hasDeeperLayer());
        Vec3 normal = contact.surfaceNormal();
        if (correction <= ZERO_DEPTH_EPSILON || normal == null
                || normal.lengthSqr() <= ZERO_DEPTH_EPSILON) {
            return false;
        }
        normal = normal.normalize();
        player.move(MoverType.SELF, normal.scale(correction));
        Vec3 motion = player.getDeltaMovement();
        double inwardMotion = motion.dot(normal);
        if (inwardMotion < 0.0D) {
            player.setDeltaMovement(motion.subtract(normal.scale(inwardMotion)));
        }
        player.hasImpulse = true;
        return true;
    }

    static double zeroDepthCorrection(
            SinkingPhysicsProfile profile, double depth, double availableDepth,
            double layerTopDepth, double layerDepth, boolean hasDeeperLayer) {
        if (SinkingPhysicsSolver.configuredDepth(profile) > ZERO_DEPTH_EPSILON) {
            return 0.0D;
        }
        double limit = SinkingPhysicsSolver.sinkLimit(
                profile, availableDepth, layerTopDepth, layerDepth, hasDeeperLayer, 1.0D);
        return Math.max(0.0D, depth - limit);
    }

    static void applyPlayerMovement(Player player, BlockState state, double depth,
            double depthFactor, double horizontalCoverage, double availableDepth,
            double layerTopDepth, double layerDepth, boolean hasDeeperLayer,
            MudPlayerData data, SinkingMedium medium, BlockPos physicsProfilePos, String frame) {
        RopeRuntime.RescuePull rescuePull = player instanceof ServerPlayer serverPlayer
                ? RopeRuntime.rescuePull(
                        serverPlayer, data != null && data.holdingStruggle)
                : RopeRuntime.RescuePull.NONE;
        applyPlayerMovement(player, state, depth, depthFactor, horizontalCoverage,
                availableDepth, layerTopDepth, layerDepth, hasDeeperLayer, data,
                medium, physicsProfilePos, frame, rescuePull);
    }

    static void applyPlayerMovement(Player player, BlockState state, double depth,
            double depthFactor, double horizontalCoverage, double availableDepth,
            double layerTopDepth, double layerDepth, boolean hasDeeperLayer,
            MudPlayerData data, SinkingMedium medium, BlockPos physicsProfilePos, String frame,
            RopeRuntime.RescuePull rescuePull) {
        if (player.tickCount == 0) {
            return;
        }
        if (medium == SinkingMedium.LIVING_SLIME) {
            LivingSlimePhysicsProfile slimeProfile = resolveLivingSlimeProfile(player, physicsProfilePos);
            clearMudMovement(player);
            if (data != null) {
                if (data.livingSlimeState.anchorActive
                        && data.livingSlimeState.localFrame) {
                    data.resetLivingSlimeState();
                }
                enableMudGravityControl(player, data);
                prepareLivingSlimeStruggle(player, depth, availableDepth, data, slimeProfile);
                data.physicsMedium = medium;
            }
            LivingSlimePhysics.apply(player, state, depth, depthFactor, availableDepth, data, slimeProfile);
            return;
        }
        boolean sculkEnabled = MudBehaviorContext.sculk(
                player.level(), physicsProfilePos, medium);
        boolean fleshEnabled = MudBehaviorContext.tenderFlesh(
                player.level(), physicsProfilePos, medium);
        if (data != null) {
            enableMudGravityControl(player, data);
        }

        Vec3 motion = player.getDeltaMovement();
        double horizontalSpeed = motion.horizontalDistance();
        double look = 0.0D;
        if (data != null) {
            data.agitation = Mth.clamp(data.agitation + medium.movementAgitation(horizontalSpeed), 0.0F, 1.0F);
            look = lookDelta(player, data);
            data.agitation = Mth.clamp(data.agitation + medium.lookAgitation(look), 0.0F, 1.0F);
        }
        double blockDepth = Math.max(0.0D, depth);
        double playerHeight = canonicalStandingHeight(player);
        double agitation = data == null ? 0.0D : data.agitation;
        if (player.isShiftKeyDown() && data != null && !sculkEnabled) {
            data.agitation = Mth.clamp(data.agitation + medium.crouchAgitation(), 0.0F, 1.0F);
            agitation = data.agitation;
        }
        if (data != null && data.holdingStruggle && !sculkEnabled) {
            data.agitation = Mth.clamp(data.agitation + medium.struggleAgitation() * 0.28F, 0.0F, 1.0F);
            agitation = data.agitation;
        }
        SinkingPhysicsProfile profile = resolvePhysicsProfile(player, data, medium, physicsProfilePos);
        if (SinkingPhysicsSolver.configuredDepth(profile) <= ZERO_DEPTH_EPSILON) {
            applyZeroDepthMovement(player, data, rescuePull);
            return;
        }
        MudEnchantmentEffects.Modifiers enchantments = MudEnchantmentEffects.mudWalker(player);
        double estimatedLimit = SinkingPhysicsSolver.sinkLimit(
                profile, availableDepth, layerTopDepth, layerDepth,
                hasDeeperLayer, enchantments.depthLimitScale());
        double depthProgress = Mth.clamp(blockDepth / Math.max(estimatedLimit, 0.08D), 0.0D, 1.0D);
        double wobble = medium.wobbleHorizontal(depthProgress);
        double phase = player.tickCount * 0.23D + player.getId() * 1.73D;
        double wobbleX = Math.sin(phase) * wobble;
        double wobbleZ = Math.cos(phase * 0.77D) * wobble;
        double slurpImpulse = data == null || sculkEnabled
                ? 0.0D : rollSlurpImpulse(player, medium, depthFactor, horizontalSpeed);
        double pendingCharge = data == null || sculkEnabled
                ? -1.0D : data.pendingStruggleCharge;
        if (data != null && sculkEnabled) {
            data.pendingStruggleCharge = -1.0F;
            data.liftTicks = 0;
        }
        boolean carryingStruggle = data != null && data.liftTicks > 0 && motion.y > 0.0D;
        SinkingPhysicsSolver.Input input = new SinkingPhysicsSolver.Input(
                blockDepth,
                availableDepth,
                playerHeight,
                motion.x,
                motion.y,
                motion.z,
                data == null ? 0.0D : data.settlingVelocity,
                agitation,
                look,
                player.isShiftKeyDown(),
                data != null && data.holdingStruggle,
                pendingCharge,
                carryingStruggle,
                slurpImpulse,
                wobbleX,
                wobbleZ,
                Mth.clamp(depthFactor, 0.0D, 1.0D),
                Mth.clamp(horizontalCoverage, 0.0D, 1.0D),
                enchantments.depthLimitScale(),
                enchantments.walkRestoration(),
                layerTopDepth,
                layerDepth,
                hasDeeperLayer,
                rescuePull.motion().x,
                rescuePull.motion().y,
                rescuePull.motion().z,
                rescuePull.sinkRelief());
        SinkingPhysicsSolver.Result result = SinkingPhysicsSolver.solve(profile, input);
        double finalMotionX = result.motionX();
        double finalMotionY = result.motionY();
        double finalMotionZ = result.motionZ();
        double finalWalkScale = result.walkScale();
        TenderFleshMechanics.StepResult fleshResult = null;
        TenderFleshProfile fleshProfile = null;
        if (fleshEnabled && data != null) {
            boolean enclosureWasActive = data.tenderFleshState.enclosureActive;
            fleshProfile = resolveTenderFleshProfile(player, physicsProfilePos, medium);
            TenderFleshPoolRules.Anchor enclosureAnchor = null;
            if ("world".equals(frame)) {
                BlockPos surfacePos = MudColumnResolver.findTop(player.level(), physicsProfilePos);
                enclosureAnchor = TenderFleshPoolRules.findAnchor(
                        player.level(), surfacePos, medium, fleshProfile, availableDepth,
                        player.getX() - surfacePos.getX(), player.getZ() - surfacePos.getZ(),
                        player.getBbWidth() * 0.5D);
            }
            fleshResult = TenderFleshMechanics.step(
                    fleshProfile,
                    data.tenderFleshState,
                    new TenderFleshMechanics.Input(
                            player.level().getGameTime(),
                            result.depthProgress(),
                            result.remainingDepth(),
                            horizontalSpeed,
                            look,
                            data.holdingStruggle,
                            pendingCharge,
                            result.motionX(),
                            result.motionY(),
                            result.motionZ(),
                            result.walkScale(),
                            enclosureAnchor != null,
                            enclosureAnchor == null ? player.getX() : enclosureAnchor.x(),
                            enclosureAnchor == null
                                    ? player.getY() : enclosureAnchor.y(),
                            enclosureAnchor == null ? player.getZ() : enclosureAnchor.z(),
                            enclosureAnchor == null ? player.getX() : enclosureAnchor.x(),
                            enclosureAnchor == null ? player.getZ() : enclosureAnchor.z()));
            if (data.tenderFleshState.enclosureActive
                    && data.tenderFleshState.enclosureDimension == null) {
                data.tenderFleshState.enclosureDimension = player.level().dimension();
            }
            if (!enclosureWasActive && data.tenderFleshState.enclosureActive
                    && player instanceof ServerPlayer serverPlayer) {
                ModCriteria.tenderFleshEnclosed(serverPlayer);
            }
            finalMotionX = fleshResult.motionX();
            finalMotionY = fleshResult.motionY();
            finalMotionZ = fleshResult.motionZ();
            finalWalkScale = fleshResult.walkScale();
            if (data.tenderFleshState.enclosureActive
                    && fleshResult.releaseOutcome() != TenderFleshMechanics.ReleaseOutcome.EFFECTIVE) {
                // The enclosure must keep the player in the pool until all four
                // pillars are broken. Only a successful struggle release may
                // move upward; ordinary gravity cannot lift the player out.
                finalMotionY = Math.min(finalMotionY, 0.0D);
            }
            if (data.tenderFleshState.enclosureActive) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS, 120, 0, false, false, false));
                player.addEffect(new MobEffectInstance(
                        MobEffects.WEAKNESS, 120, 0, false, false, false));
                player.addEffect(new MobEffectInstance(
                        MobEffects.DIG_SLOWDOWN, 120, 0, false, false, false));
            }
        }
        SculkMireMechanics.StepResult sculkResult = null;
        SculkMireProfile sculkProfile = null;
        if (sculkEnabled && data != null) {
            sculkProfile = resolveSculkMireProfile(player, physicsProfilePos, medium);
            boolean freshInput = data.sculkInputAge <= 8;
            double movementIntent = freshInput ? data.sculkMovementIntent : horizontalSpeed;
            boolean jumpIntent = freshInput && data.sculkJumpIntent;
            boolean crouchIntent = freshInput ? data.sculkCrouchIntent : player.isShiftKeyDown();
            sculkResult = SculkMireMechanics.step(sculkProfile, data.sculkMireState,
                    new SculkMireMechanics.Input(
                            blockDepth,
                            result.remainingDepth(),
                            motion.x, motion.y, motion.z,
                            result.motionX(), result.motionY(), result.motionZ(),
                            result.walkScale(),
                            movementIntent,
                            look,
                            jumpIntent || data.holdingStruggle,
                            crouchIntent));
            data.sculkInputAge = Math.min(20, data.sculkInputAge + 1);
            finalMotionX = sculkResult.motionX();
            finalMotionY = sculkResult.motionY();
            finalMotionZ = sculkResult.motionZ();
            finalWalkScale = sculkResult.walkScale();
        }
        if (fleshEnabled
                && data != null && data.tenderFleshState.enclosureActive) {
            TenderFleshEnclosureSystem.suppressFlight(player);
            TenderFleshEnclosureSystem.anchorPlayer(player, data.tenderFleshState);
            finalMotionX = 0.0D;
            finalMotionZ = 0.0D;
            finalMotionY = TenderFleshEnclosureSystem.clampVerticalMotion(
                    player, data.tenderFleshState, finalMotionY);
            finalWalkScale = 0.0D;
        }
        updateMudMovement(player, finalWalkScale, profile.stepHeight);
        if ("sable-gravity-frame".equals(frame)) {
            player.setDeltaMovement(finalMotionX, finalMotionY, finalMotionZ);
        } else {
            applyWorldVerticalMotion(
                    player, finalMotionX, finalMotionY, finalMotionZ);
        }
        player.hasImpulse = true;
        player.resetFallDistance();
        if (finalMotionY <= 0.0D) {
            player.setOnGround(true);
        }
        if (data != null) {
            if (pendingCharge >= 0.0D) {
                data.pendingStruggleCharge = -1.0F;
                data.liftTicks = ordinaryStruggleLiftTicks(profile, pendingCharge);
            }
            data.debugColumnDepth = result.columnDepth();
            data.debugSinkLimit = result.sinkLimit();
            data.debugRemainingDepth = result.remainingDepth();
            data.debugYBefore = motion.y;
            data.debugYAfter = finalMotionY;
            data.debugHorizontalSpeed = result.horizontalSpeed();
            data.debugSinkStep = result.sinkStep();
            data.debugWalkScale = finalWalkScale;
            data.debugVerticalScale = result.verticalScale();
            data.settlingVelocity = sculkResult != null
                    && sculkResult.resetSettlingVelocity()
                    ? 0.0D : result.settlingVelocity();
            if (fleshEnabled
                    && fleshResult != null
                    && fleshResult.releaseOutcome() == TenderFleshMechanics.ReleaseOutcome.EFFECTIVE) {
                data.settlingVelocity = 0.0D;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                PhysicsTraceLog.trace(serverPlayer, frame, data.medium, medium, input, result, data.struggleHold, data.liftTicks);
            }
        }
        if (sculkResult != null && player instanceof ServerPlayer serverPlayer) {
            if (sculkResult.emitNoise()) {
                SculkMireMechanics.emitResonance(serverPlayer, sculkProfile, data.sculkMireState);
            }
            if (sculkResult.clampStarted()) {
                ModCriteria.sculkRestrained(serverPlayer);
                serverPlayer.serverLevel().playSound(null, serverPlayer.blockPosition(),
                        SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 0.72F, 0.68F);
                SculkMireMechanics.syncClamp(serverPlayer, sculkProfile, data.sculkMireState, true,
                        MudVisualSource.capture(serverPlayer.level(), physicsProfilePos));
            } else if (sculkResult.clampReleased()) {
                serverPlayer.serverLevel().playSound(null, serverPlayer.blockPosition(),
                        SoundEvents.SCULK_CLICKING_STOP, SoundSource.BLOCKS, 0.52F, 0.82F);
                SculkMireMechanics.syncClamp(serverPlayer, sculkProfile, data.sculkMireState, false,
                        MudVisualSource.capture(serverPlayer.level(), physicsProfilePos));
            } else if (data.sculkMireState.clampActive()
                    && serverPlayer.tickCount % sculkProfile.clampSyncIntervalTicks() == 0) {
                SculkMireMechanics.syncClamp(serverPlayer, sculkProfile, data.sculkMireState, true,
                        MudVisualSource.capture(serverPlayer.level(), physicsProfilePos));
            }
        }
        if (fleshResult != null && player instanceof ServerPlayer serverPlayer) {
            TenderFleshEnclosureSystem.sync(serverPlayer, data, false);
        }
        if (fleshResult != null
                && result.depthProgress() > 0.04D
                && fleshProfile != null
                && player instanceof ServerPlayer serverPlayer) {
            if (fleshResult.pulseBeat() && fleshProfile.soundVolume() > 0.001D) {
                float pitch = 0.62F + (float) (data.tenderFleshState.wrap * 0.10D);
                serverPlayer.serverLevel().playSound(
                        null,
                        serverPlayer.blockPosition(),
                        SoundEvents.HONEY_BLOCK_PLACE,
                        SoundSource.BLOCKS,
                        (float) fleshProfile.soundVolume(),
                        pitch);
            }
            if (fleshResult.pressureBeat() && fleshProfile.soundVolume() > 0.001D) {
                serverPlayer.serverLevel().playSound(
                        null,
                        serverPlayer.blockPosition(),
                        SoundEvents.HONEY_BLOCK_PLACE,
                        SoundSource.BLOCKS,
                        (float) (fleshProfile.soundVolume() * 0.82D),
                        0.48F);
            }
            if (fleshResult.relaxationBeat() && fleshProfile.soundVolume() > 0.001D) {
                serverPlayer.serverLevel().playSound(
                        null,
                        serverPlayer.blockPosition(),
                        SoundEvents.HONEY_BLOCK_SLIDE,
                        SoundSource.BLOCKS,
                        (float) (fleshProfile.soundVolume() * 0.72D),
                        1.08F);
            }
            if (fleshResult.calmBeat() && fleshProfile.soundVolume() > 0.001D) {
                serverPlayer.serverLevel().playSound(
                        null,
                        serverPlayer.blockPosition(),
                        SoundEvents.HONEY_BLOCK_SLIDE,
                        SoundSource.BLOCKS,
                        (float) (fleshProfile.soundVolume() * 0.56D),
                        1.20F);
            }
            if (fleshResult.releaseOutcome() == TenderFleshMechanics.ReleaseOutcome.EFFECTIVE) {
                if (!data.tenderFleshEffectiveHintShown) {
                    data.tenderFleshEffectiveHintShown = true;
                    serverPlayer.displayClientMessage(Component.translatable(
                            "message.mirebound.tender_flesh.effective"), true);
                }
                serverPlayer.serverLevel().playSound(
                        null, serverPlayer.blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE,
                        SoundSource.BLOCKS, (float) (fleshProfile.soundVolume() * 0.90D), 1.22F);
            } else if (fleshResult.releaseOutcome() == TenderFleshMechanics.ReleaseOutcome.ABSORBED
                    && data.tenderFleshAbsorbedFeedbacks < 2) {
                data.tenderFleshAbsorbedFeedbacks++;
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.mirebound.tender_flesh.absorbed"), true);
                serverPlayer.serverLevel().playSound(
                        null, serverPlayer.blockPosition(), SoundEvents.HONEY_BLOCK_PLACE,
                        SoundSource.BLOCKS, (float) (fleshProfile.soundVolume() * 0.78D), 0.52F);
            }
        }
        if (slurpImpulse > 0.0D && player instanceof ServerPlayer serverPlayer) {
            playSlurp(serverPlayer, medium, slurpImpulse);
        }
    }

    static void applyClientPlayerMovement(Player player, BlockState state, double depth,
            double depthFactor, double horizontalCoverage, double availableDepth,
            double layerTopDepth, double layerDepth, boolean hasDeeperLayer,
            SinkingMedium medium, BlockPos physicsProfilePos, MudClientPhysics.State clientState) {
        applyClientPlayerMovement(
                player,
                state,
                depth,
                depthFactor,
                horizontalCoverage,
                availableDepth,
                layerTopDepth,
                layerDepth,
                hasDeeperLayer,
                medium,
                physicsProfilePos,
                clientState,
                player.position(),
                false);
    }

    private static void applyClientPlayerMovement(Player player, BlockState state, double depth,
            double depthFactor, double horizontalCoverage, double availableDepth,
            double layerTopDepth, double layerDepth, boolean hasDeeperLayer, SinkingMedium medium,
            BlockPos physicsProfilePos, MudClientPhysics.State clientState,
            Vec3 physicsPosition, boolean localFrame) {
        if (player.tickCount == 0) {
            return;
        }

        if (medium == SinkingMedium.LIVING_SLIME) {
            LivingSlimePhysicsProfile slimeProfile = MudMediumRuntime.livingSlimeProfile(
                    player.level(), physicsProfilePos, MudPhysicsProfiles.livingSlime(player));
            clearMudMovement(player);
            if (clientState.livingSlimeState.anchorActive
                    && clientState.livingSlimeState.localFrame != localFrame) {
                clientState.livingSlimeState.reset();
            }
            clientState.livingSlimeState.localFrame = localFrame;
            if (clientState.pendingStruggleCharge >= 0.0D) {
                double charge = clientState.pendingStruggleCharge;
                Vec3 motion = player.getDeltaMovement();
                double impulse = LivingSlimePhysics.struggleImpulse(
                        slimeProfile,
                        depth,
                        availableDepth,
                        charge);
                player.setDeltaMovement(motion.x,
                        LivingSlimePhysics.struggleLaunchVelocity(slimeProfile, motion.y, impulse), motion.z);
                clientState.pendingStruggleCharge = -1.0D;
                clientState.liftTicks = LivingSlimePhysics.struggleLiftTicks(slimeProfile, charge);
            }
            if (localFrame) {
                LivingSlimePhysics.applyClientInFrame(
                        player,
                        state,
                        depth,
                        depthFactor,
                        availableDepth,
                        clientState.livingSlimeState,
                        physicsPosition,
                        clientState.liftTicks > 0,
                        slimeProfile);
            } else {
                LivingSlimePhysics.applyClient(
                        player,
                        state,
                        depth,
                        depthFactor,
                        availableDepth,
                        clientState.livingSlimeState,
                        clientState.liftTicks > 0,
                        slimeProfile);
            }
            if (clientState.liftTicks > 0) {
                clientState.liftTicks--;
            }
            return;
        }
        boolean sculkEnabled = MudBehaviorContext.sculk(
                player.level(), physicsProfilePos, medium);
        boolean fleshEnabled = MudBehaviorContext.tenderFlesh(
                player.level(), physicsProfilePos, medium);
        Vec3 motion = player.getDeltaMovement();
        double look = clientState.lookDelta(player);
        clientState.agitation = Mth.clamp(
                clientState.agitation - medium.agitationDecay()
                        + medium.movementAgitation(motion.horizontalDistance())
                        + medium.lookAgitation(look)
                        + (player.isShiftKeyDown() ? medium.crouchAgitation() : 0.0F),
                0.0F,
                1.0F);
        double charge = sculkEnabled
                ? -1.0D : clientState.pendingStruggleCharge;
        if (sculkEnabled) {
            clientState.pendingStruggleCharge = -1.0D;
            clientState.liftTicks = 0;
        }
        SinkingPhysicsProfile profile = MudMediumRuntime.ordinaryProfile(
                player.level(), physicsProfilePos, medium,
                MudPhysicsProfiles.ordinary(player, medium));
        if (SinkingPhysicsSolver.configuredDepth(profile) <= ZERO_DEPTH_EPSILON) {
            applyZeroDepthMovement(player, clientState);
            return;
        }
        MudEnchantmentEffects.Modifiers enchantments = MudEnchantmentEffects.mudWalker(player);
        boolean holdingStruggle = clientState.jumpingInput;
        SinkingPhysicsSolver.Input input = new SinkingPhysicsSolver.Input(
                Math.max(0.0D, depth),
                availableDepth,
                canonicalStandingHeight(player),
                motion.x,
                motion.y,
                motion.z,
                clientState.settlingVelocity,
                clientState.agitation,
                look,
                player.isShiftKeyDown(),
                holdingStruggle,
                charge,
                clientState.liftTicks > 0 && motion.y > 0.0D,
                0.0D,
                0.0D,
                0.0D,
                Mth.clamp(depthFactor, 0.0D, 1.0D),
                Mth.clamp(horizontalCoverage, 0.0D, 1.0D),
                enchantments.depthLimitScale(),
                enchantments.walkRestoration(),
                layerTopDepth,
                layerDepth,
                hasDeeperLayer);
        SinkingPhysicsSolver.Result result = SinkingPhysicsSolver.solve(profile, input);
        double finalMotionX = result.motionX();
        double finalMotionY = result.motionY();
        double finalMotionZ = result.motionZ();
        double finalWalkScale = result.walkScale();
        TenderFleshMechanics.StepResult fleshResult = null;
        if (fleshEnabled) {
            TenderFleshProfile fleshProfile = resolveTenderFleshProfile(
                    player, physicsProfilePos, medium);
            TenderFleshPoolRules.Anchor enclosureAnchor = null;
            if (!localFrame) {
                BlockPos surfacePos = MudColumnResolver.findTop(player.level(), physicsProfilePos);
                enclosureAnchor = TenderFleshPoolRules.findAnchor(
                        player.level(), surfacePos, medium, fleshProfile, availableDepth,
                        physicsPosition.x - surfacePos.getX(), physicsPosition.z - surfacePos.getZ(),
                        player.getBbWidth() * 0.5D);
            }
            fleshResult = TenderFleshMechanics.step(
                    fleshProfile,
                    clientState.tenderFleshState,
                    new TenderFleshMechanics.Input(
                            player.level().getGameTime(),
                            result.depthProgress(),
                            result.remainingDepth(),
                            motion.horizontalDistance(),
                            look,
                            holdingStruggle,
                            charge,
                            result.motionX(),
                            result.motionY(),
                            result.motionZ(),
                            result.walkScale(),
                            enclosureAnchor != null,
                            enclosureAnchor == null ? physicsPosition.x : enclosureAnchor.x(),
                            enclosureAnchor == null
                                    ? physicsPosition.y : enclosureAnchor.y(),
                            enclosureAnchor == null ? physicsPosition.z : enclosureAnchor.z(),
                            enclosureAnchor == null ? physicsPosition.x : enclosureAnchor.x(),
                            enclosureAnchor == null ? physicsPosition.z : enclosureAnchor.z()));
            finalMotionX = fleshResult.motionX();
            finalMotionY = fleshResult.motionY();
            finalMotionZ = fleshResult.motionZ();
            finalWalkScale = fleshResult.walkScale();
            if (clientState.tenderFleshState.enclosureActive
                    && fleshResult.releaseOutcome() != TenderFleshMechanics.ReleaseOutcome.EFFECTIVE) {
                finalMotionY = Math.min(finalMotionY, 0.0D);
            }
        }
        SculkMireMechanics.StepResult sculkResult = null;
        if (sculkEnabled) {
            SculkMireProfile sculkProfile = resolveSculkMireProfile(
                    player, physicsProfilePos, medium);
            double movementIntent = Math.sqrt(player.xxa * player.xxa + player.zza * player.zza);
            sculkResult = SculkMireMechanics.step(sculkProfile, clientState.sculkMireState,
                    new SculkMireMechanics.Input(
                            Math.max(0.0D, depth),
                            result.remainingDepth(),
                            motion.x, motion.y, motion.z,
                            result.motionX(), result.motionY(), result.motionZ(),
                            result.walkScale(),
                            movementIntent,
                            look,
                            clientState.jumpingInput,
                            player.isShiftKeyDown()));
            finalMotionX = sculkResult.motionX();
            finalMotionY = sculkResult.motionY();
            finalMotionZ = sculkResult.motionZ();
            finalWalkScale = sculkResult.walkScale();
        }
        if (fleshEnabled
                && clientState.tenderFleshState.enclosureActive) {
            TenderFleshEnclosureSystem.suppressFlight(player);
            TenderFleshEnclosureSystem.anchorPlayer(player, clientState.tenderFleshState);
            finalMotionX = 0.0D;
            finalMotionZ = 0.0D;
            finalMotionY = TenderFleshEnclosureSystem.clampVerticalMotion(
                    player, clientState.tenderFleshState, finalMotionY);
            finalWalkScale = 0.0D;
        }
        updateMudMovement(player, finalWalkScale, profile.stepHeight);
        clientState.walkScale = finalWalkScale;
        if (localFrame) {
            player.setDeltaMovement(finalMotionX, finalMotionY, finalMotionZ);
        } else {
            applyWorldVerticalMotion(
                    player, finalMotionX, finalMotionY, finalMotionZ);
        }
        clientState.settlingVelocity = sculkResult != null
                && sculkResult.resetSettlingVelocity()
                ? 0.0D : result.settlingVelocity();
        if (fleshEnabled
                && fleshResult != null
                && fleshResult.releaseOutcome() == TenderFleshMechanics.ReleaseOutcome.EFFECTIVE) {
            clientState.settlingVelocity = 0.0D;
        }
        if (charge >= 0.0D) {
            clientState.pendingStruggleCharge = -1.0D;
            clientState.liftTicks = ordinaryStruggleLiftTicks(profile, charge);
        } else if (clientState.liftTicks > 0) {
            clientState.liftTicks--;
        }
        player.hasImpulse = true;
        player.resetFallDistance();
    }

    private static void applyWorldVerticalMotion(
            Player player, double motionX, double motionY, double motionZ) {
        VerticalMotionPlan plan = verticalMotionPlan(motionY);
        if (plan.immediateY() != 0.0D) {
            player.move(MoverType.SELF, new Vec3(0.0D, plan.immediateY(), 0.0D));
        }
        player.setDeltaMovement(motionX, plan.retainedY(), motionZ);
    }

    static VerticalMotionPlan verticalMotionPlan(double motionY) {
        if (motionY < 0.0D
                && -motionY < LivingEntity.MIN_MOVEMENT_DISTANCE) {
            return new VerticalMotionPlan(motionY, 0.0D);
        }
        return new VerticalMotionPlan(0.0D, motionY);
    }

    record VerticalMotionPlan(double immediateY, double retainedY) {
    }

    private static void applyZeroDepthMovement(
            Player player, MudPlayerData data, RopeRuntime.RescuePull rescuePull) {
        if (data != null) {
            data.pendingStruggleCharge = -1.0F;
            data.liftTicks = 0;
            data.settlingVelocity = 0.0D;
            data.holdingStruggle = false;
            data.struggleHold = 0;
            data.struggleCharge = 0.0F;
            data.resetSculkMireState();
        }
        updateMudMovement(player, 1.0D, 0.0D);
        Vec3 motion = player.getDeltaMovement();
        Vec3 pull = rescuePull != null && rescuePull.active()
                ? rescuePull.motion() : Vec3.ZERO;
        player.setDeltaMovement(motion.x + pull.x, Math.max(0.0D, pull.y), motion.z + pull.z);
        player.setOnGround(true);
        player.hasImpulse = true;
        player.resetFallDistance();
    }

    private static void applyZeroDepthMovement(
            Player player, MudClientPhysics.State clientState) {
        clientState.pendingStruggleCharge = -1.0D;
        clientState.liftTicks = 0;
        clientState.settlingVelocity = 0.0D;
        clientState.sculkMireState.reset();
        updateMudMovement(player, 1.0D, 0.0D);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, 0.0D, motion.z);
        player.setOnGround(true);
        player.hasImpulse = true;
        player.resetFallDistance();
    }

    private static int ordinaryStruggleLiftTicks(SinkingPhysicsProfile profile, double charge) {
        double normalizedCharge = Mth.clamp(charge, 0.0D, 1.0D);
        return Math.max(1, (int) Math.round(
                profile.struggleLiftTicks * (0.60D + normalizedCharge * 0.40D)));
    }

    private static SinkingPhysicsProfile resolvePhysicsProfile(Player player, MudPlayerData data,
            SinkingMedium targetMedium, BlockPos physicsProfilePos) {
        SinkingPhysicsProfile target = MudMediumRuntime.ordinaryProfile(
                player.level(), physicsProfilePos, targetMedium,
                MudPhysicsProfiles.ordinary(player, targetMedium));
        if (data == null) {
            return target;
        }
        if (!data.inMud || data.stuckTicks <= 0) {
            data.physicsMedium = targetMedium;
            data.physicsMediumFrom = targetMedium;
            data.physicsMediumBlend = 1.0F;
            return target;
        }
        if (data.physicsMedium != targetMedium) {
            data.physicsMediumFrom = data.physicsMedium;
            data.physicsMedium = targetMedium;
            data.physicsMediumBlend = 0.0F;
        }
        if (data.physicsMediumBlend >= 1.0F) {
            return target;
        }
        data.physicsMediumBlend = Math.min(1.0F, data.physicsMediumBlend + 0.20F);
        return SinkingPhysicsProfile.blend(
                MudPhysicsProfiles.ordinary(player, data.physicsMediumFrom),
                target,
                data.physicsMediumBlend);
    }

    private static LivingSlimePhysicsProfile resolveLivingSlimeProfile(Player player, BlockPos physicsProfilePos) {
        return MudMediumRuntime.livingSlimeProfile(
                player.level(), physicsProfilePos, MudPhysicsProfiles.livingSlime(player));
    }

    static SculkMireProfile resolveSculkMireProfile(Player player, BlockPos physicsProfilePos) {
        return resolveSculkMireProfile(
                player, physicsProfilePos, SinkingMedium.SCULK_MIRE);
    }

    static SculkMireProfile resolveSculkMireProfile(
            Player player, BlockPos physicsProfilePos, SinkingMedium medium) {
        return MudMediumRuntime.sculkMireProfile(
                player.level(), physicsProfilePos, medium,
                MudPhysicsProfiles.sculkMire(player, medium));
    }

    static TenderFleshProfile resolveTenderFleshProfile(Player player, BlockPos physicsProfilePos) {
        return MudMediumRuntime.tenderFleshProfile(player.level(), physicsProfilePos);
    }

    static TenderFleshProfile resolveTenderFleshProfile(
            Player player, BlockPos physicsProfilePos, SinkingMedium medium) {
        return MudMediumRuntime.tenderFleshProfile(
                player.level(), physicsProfilePos, medium,
                MudPhysicsProfiles.tenderFlesh(player, medium));
    }

    private static void prepareLivingSlimeStruggle(Player player, double depth, double availableDepth,
            MudPlayerData data, LivingSlimePhysicsProfile profile) {
        if (data.pendingStruggleCharge < 0.0F) {
            return;
        }
        double charge = data.pendingStruggleCharge;
        Vec3 motion = player.getDeltaMovement();
        double impulse = LivingSlimePhysics.struggleImpulse(profile, depth, availableDepth, charge);
        player.setDeltaMovement(motion.x,
                LivingSlimePhysics.struggleLaunchVelocity(profile, motion.y, impulse), motion.z);
        data.liftTicks = LivingSlimePhysics.struggleLiftTicks(profile, charge);
        data.pendingStruggleCharge = -1.0F;
    }

    static double canonicalStandingHeight(Player player) {
        return Math.max(0.10D, player.getDimensions(Pose.STANDING).height());
    }

    private static double rollSlurpImpulse(Player player, SinkingMedium medium, double depthFactor, double horizontalSpeed) {
        double chance = medium.slurpChance(depthFactor, horizontalSpeed);
        if (chance <= 0.0D || player.level().getRandom().nextDouble() >= chance) {
            return 0.0D;
        }
        return medium.slurpStrength() * (0.35D + player.level().getRandom().nextDouble() * 0.55D);
    }

    private static void playSlurp(ServerPlayer player, SinkingMedium medium, double pull) {
        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.HONEY_BLOCK_SLIDE,
                SoundSource.BLOCKS,
                0.18F + (float) Mth.clamp(pull * 4.0D, 0.0D, 0.35D),
                0.42F + player.level().getRandom().nextFloat() * 0.14F);
        MudSurfaceFeedback.spawn(
                player.serverLevel(), player.position(), medium,
                4, 0.25D, 0.020D);
    }

    private static double smoothStep(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static double lookDelta(Player player, MudPlayerData data) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (!data.hasLookSample) {
            data.lastLookYaw = yaw;
            data.lastLookPitch = pitch;
            data.hasLookSample = true;
            return 0.0D;
        }

        float yawDelta = Mth.wrapDegrees(yaw - data.lastLookYaw);
        float pitchDelta = pitch - data.lastLookPitch;
        data.lastLookYaw = yaw;
        data.lastLookPitch = pitch;
        return Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
    }

}
