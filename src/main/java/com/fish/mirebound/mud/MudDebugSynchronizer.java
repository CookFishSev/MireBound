package com.fish.mirebound.mud;

import com.fish.mirebound.network.payload.MudDebugSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owns throttling and wire encoding for the optional physics debug HUD. */
final class MudDebugSynchronizer {
    // Compression geometry is presentation-critical. It must not lag behind
    // the server pose like the optional debug values historically did.
    private static final int SYNC_INTERVAL_TICKS = 1;

    private MudDebugSynchronizer() {
    }

    static void sync(ServerPlayer player, MudPlayerData data, boolean active) {
        if (!active && ticksSince(player, data.lastMudTick) > 6L) {
            return;
        }
        if (ticksSince(player, data.lastDebugSyncTick) < SYNC_INTERVAL_TICKS) {
            return;
        }

        data.lastDebugSyncTick = player.tickCount;
        MudCompressionShape compressionShape = MudCompressionShape.EMPTY;
        if (active && !data.debugPhysicalized) {
            Vec3 origin = player.position();
            MudEntityGeometry.PlaneSlice slice = MudEntityGeometry.horizontalSlice(
                    player, player.getY() + data.depth);
            compressionShape = data.previousDebugCompressionTick == player.tickCount - 1
                    ? MudCompressionShape.swept(
                            data.previousDebugCompressionSlice,
                            data.previousDebugCompressionOrigin,
                            slice, origin)
                    : MudCompressionShape.from(slice, origin);
            data.previousDebugCompressionSlice = slice;
            data.previousDebugCompressionOrigin = origin;
            data.previousDebugCompressionTick = player.tickCount;
        } else if (!active) {
            data.previousDebugCompressionSlice = null;
            data.previousDebugCompressionOrigin = Vec3.ZERO;
            data.previousDebugCompressionTick = Integer.MIN_VALUE;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new MudDebugSyncPayload(
                        player.getId(),
                        active,
                        data.physicsMedium.id(),
                        scaleMillis(data.depth),
                        scaleMillis(data.debugColumnDepth),
                        scaleMillis(data.debugSinkLimit),
                        scaleMillis(data.debugRemainingDepth),
                        scaleMicros(data.debugYBefore),
                        scaleMicros(data.debugYAfter),
                        scaleMicros(data.debugHorizontalSpeed),
                        scaleMicros(data.debugSinkStep),
                        Mth.clamp((int) Math.round(
                                data.debugWalkScale * 1000.0D), 0, 1000),
                        Mth.clamp((int) Math.round(
                                data.debugVerticalScale * 1000.0D), 0, 1000),
                        data.struggleHold,
                        data.liftTicks,
                        data.stuckTicks,
                        Mth.clamp(Math.round(data.agitation * 1000.0F), 0, 1000),
                        data.debugPhysicalized,
                        compressionShape));
    }

    private static long ticksSince(ServerPlayer player, int lastTick) {
        return (long) player.tickCount - (long) lastTick;
    }

    private static int scaleMillis(double value) {
        return Mth.clamp(
                (int) Math.round(value * 1000.0D), -1_000_000, 1_000_000);
    }

    private static int scaleMicros(double value) {
        return Mth.clamp(
                (int) Math.round(value * 10000.0D), -1_000_000, 1_000_000);
    }
}
