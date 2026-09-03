package com.fish.mirebound.content.mudwork;

import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.entitycoverage.EntityMudCoverageService;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.MudClodScreenImpactPayload;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.registry.ModMudworkContent;
import com.fish.mirebound.splash.MudSplashProfile;
import com.fish.mirebound.splash.MudSplashSystem;
import com.fish.mirebound.stain.MudWallStainSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** A visible mud ball that becomes bounded splash droplets only on impact. */
public final class MudBallProjectile extends ThrowableItemProjectile {
    private float chargePower;
    private int impactFragments = 3;
    private SinkingMedium medium = SinkingMedium.MUD;
    private Vec3 tickStart = Vec3.ZERO;

    public MudBallProjectile(
            EntityType<? extends MudBallProjectile> type, Level level) {
        super(type, level);
    }

    public MudBallProjectile(Level level, LivingEntity owner) {
        super(ModMudworkContent.MUD_BALL_PROJECTILE.get(), owner, level);
    }

    public void setChargePower(float chargePower) {
        this.chargePower = Mth.clamp(chargePower, 0.0F, 1.0F);
    }

    public void setImpactFragments(int impactFragments) {
        this.impactFragments = Mth.clamp(impactFragments, 1, 8);
    }

    public void setMedium(SinkingMedium medium) {
        this.medium = medium == null ? SinkingMedium.MUD : medium;
    }

    @Override
    protected Item getDefaultItem() {
        return ModBlocks.MUD_BALL.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.045D;
    }

    @Override
    public void tick() {
        tickStart = position();
        super.tick();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        MudSplashProfile profile = MudPhysicsSettings.mudSplashProfile();
        Vec3 impact = hit.getLocation();
        Vec3 incoming = getDeltaMovement();
        Vec3 normal = incoming.lengthSqr() > 1.0E-8D
                ? incoming.normalize().scale(-1.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        int ignoredEntityId = getOwner() == null ? -1 : getOwner().getId();
        if (hit instanceof BlockHitResult blockHit) {
            normal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
        } else if (hit instanceof EntityHitResult entityHit) {
            ignoredEntityId = entityHit.getEntity().getId();
        }

        if (getOwner() instanceof ServerPlayer owner) {
            MudSplashSystem.spawnClodImpact(
                    owner, impact.add(normal.scale(0.035D)),
                    normal, incoming, ignoredEntityId,
                    impactFragments, chargePower, medium);
        }
        serverLevel.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.MUD_PLACE, SoundSource.PLAYERS,
                0.72F + chargePower * 0.18F,
                0.82F + serverLevel.random.nextFloat() * 0.14F);
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel
                && hit.getEntity() instanceof LivingEntity target) {
            Vec3 impact = MudBallHitGeometry.resolveEntityImpact(
                    target.getBoundingBox().inflate(getBbWidth() * 0.5D),
                    tickStart,
                    tickStart.add(getDeltaMovement()),
                    hit.getLocation());
            MudSplashProfile profile = MudPhysicsSettings.mudSplashProfile();
            if (target instanceof ServerPlayer player) {
                applyPlayerImpact(player, impact, profile);
            } else {
                EntityMudCoverageService.applySplash(
                        target, medium, MudVisualSource.NONE,
                        impact, Math.max(profile.playerHitRadius(),
                                0.30F + chargePower * 0.10F),
                        profile.playerStainStrength()
                                * (0.55F + chargePower * 0.25F),
                        true);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (level() instanceof ServerLevel serverLevel) {
            placeBlockStain(
                    serverLevel, hit,
                    MudPhysicsSettings.mudSplashProfile());
        }
    }

    private void placeBlockStain(
            ServerLevel level, BlockHitResult hit,
            MudSplashProfile profile) {
        BlockPos supportPos = hit.getBlockPos();
        Direction face = hit.getDirection();
        BlockPos containerPos = supportPos.relative(face);
        Vec3 point = hit.getLocation();
        float radius = Math.max(profile.stainRadius() * 1.55F,
                0.19F + chargePower * 0.07F);
        float strength = profile.stainStrength()
                * (0.72F + chargePower * 0.18F);
        MudWallStainSystem.placeMudSplashStain(
                level, null, supportPos, containerPos, face,
                point.subtract(containerPos.getX(),
                        containerPos.getY(), containerPos.getZ()),
                point, radius, strength, medium,
                MudVisualSource.NONE);
    }

    private void applyPlayerImpact(
            ServerPlayer target, Vec3 impact,
            MudSplashProfile profile) {
        float radius = Math.max(profile.playerHitRadius(),
                0.30F + chargePower * 0.10F);
        float strength = profile.playerStainStrength()
                * (0.55F + chargePower * 0.25F);
        MudPhysics.applyMudClodToPlayer(
                target, impact, radius, strength,
                medium, MudVisualSource.NONE, true);
        if (MudBallHitGeometry.strikesFrontHead(
                impact, getDeltaMovement(), target.position(),
                target.getEyeY(), target.getYHeadRot(),
                target.getXRot())) {
            long seed = target.getUUID().getLeastSignificantBits()
                    ^ target.serverLevel().getGameTime()
                    ^ getId() * 0x9E3779B97F4A7C15L;
            PacketDistributor.sendToPlayer(target,
                    new MudClodScreenImpactPayload(
                            0.78F + chargePower * 0.20F,
                            seed, medium.id()));
        }
    }
}
