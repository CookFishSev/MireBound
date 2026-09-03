package com.fish.mirebound.mud;

import com.fish.mirebound.Mirebound;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class PhysicsTraceLog {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_MOB_RATE_KEYS = 4096;
    private static final long STALE_MOB_RATE_TICKS = 1200L;
    private static final int FLUSH_BATCH_LINES = 32;
    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Long> MOB_TRACE_TICKS = new ConcurrentHashMap<>();
    private static BufferedWriter writer;
    private static Path activePath;
    private static int pendingFlushLines;

    private PhysicsTraceLog() {
    }

    public static boolean enabled(ServerPlayer player) {
        return ENABLED_PLAYERS.contains(player.getUUID());
    }

    public static boolean anyEnabled() {
        return !ENABLED_PLAYERS.isEmpty();
    }

    /**
     * Temporary, rate-limited diagnostics for non-player movement. This shares
     * the existing physics log so reproduction does not require another toggle.
     */
    public static void traceMob(Mob mob, String frame, String message) {
        if (!anyEnabled() || mob.level().isClientSide()) {
            return;
        }
        long tick = mob.level().getGameTime();
        String key = mob.level().dimension().location() + ":" + mob.getId() + ":" + frame;
        if (!allowMobTrace(key, tick)) {
            return;
        }
        synchronized (PhysicsTraceLog.class) {
            writeLine(String.format(Locale.ROOT,
                    "tick=%d frame=mob-%s mob=%s id=%d pos=(%.3f,%.3f,%.3f) bb=(%.3f,%.3f,%.3f,%.3f,%.3f,%.3f) %s",
                    tick,
                    frame,
                    mob.getType(),
                    mob.getId(),
                    mob.getX(), mob.getY(), mob.getZ(),
                    mob.getBoundingBox().minX, mob.getBoundingBox().minY, mob.getBoundingBox().minZ,
                    mob.getBoundingBox().maxX, mob.getBoundingBox().maxY, mob.getBoundingBox().maxZ,
                    message));
        }
    }

    private static synchronized boolean allowMobTrace(String key, long tick) {
        Long previous = MOB_TRACE_TICKS.put(key, tick);
        if (previous != null && tick >= previous && tick - previous < 4L) {
            return false;
        }
        if (MOB_TRACE_TICKS.size() <= MAX_MOB_RATE_KEYS) {
            return true;
        }
        MOB_TRACE_TICKS.entrySet().removeIf(
                entry -> entry.getValue() > tick
                        || tick - entry.getValue() > STALE_MOB_RATE_TICKS);
        int overflow = MOB_TRACE_TICKS.size() - MAX_MOB_RATE_KEYS;
        if (overflow > 0) {
            for (var entry : MOB_TRACE_TICKS.entrySet()) {
                if (overflow <= 0) {
                    break;
                }
                if (!entry.getKey().equals(key)
                        && MOB_TRACE_TICKS.remove(entry.getKey(), entry.getValue())) {
                    overflow--;
                }
            }
        }
        return true;
    }

    public static synchronized void setEnabled(ServerPlayer player, boolean enabled) {
        UUID playerId = player.getUUID();
        if (enabled) {
            ENABLED_PLAYERS.add(playerId);
            ensureWriter();
            writeLine("event=enabled player=" + player.getGameProfile().getName() + " uuid=" + playerId);
            player.sendSystemMessage(Component.translatable(
                    "commands.mirebound.physics_log.enabled", debugLogPath()));
        } else {
            if (ENABLED_PLAYERS.remove(playerId)) {
                writeLine("event=disabled player=" + player.getGameProfile().getName() + " uuid=" + playerId);
            }
            player.sendSystemMessage(Component.translatable(
                    "commands.mirebound.physics_log.disabled"));
            closeIfIdle();
        }
    }

    static void playerLoggedOut(ServerPlayer player) {
        synchronized (PhysicsTraceLog.class) {
            ENABLED_PLAYERS.remove(player.getUUID());
            closeIfIdle();
        }
    }

    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        ENABLED_PLAYERS.clear();
        MOB_TRACE_TICKS.clear();
        closeWriter();
    }

    static void trace(ServerPlayer player, String frame, SinkingMedium coverageMedium, SinkingMedium physicsMedium,
            SinkingPhysicsSolver.Input input, SinkingPhysicsSolver.Result result, int holdTicks, int liftTicks) {
        if (!enabled(player)) {
            return;
        }

        String line = String.format(Locale.ROOT,
                "tick=%d player=%s frame=%s coverage=%s physics=%s posY=%.5f liveY=%.5f depth=%.5f column=%.5f naturalLimit=%.5f limit=%.5f remaining=%.5f progress=%.5f "
                        + "motionIn=(%.5f,%.5f,%.5f) motionOut=(%.5f,%.5f,%.5f) horizontal=%.5f walk=%.5f vertical=%.5f "
                        + "sinkNatural=%.6f sinkMovement=%.6f sinkDisturbance=%.6f yield=%.6f sinkTarget=%.6f "
                        + "sinkPrevious=%.6f sinkFinal=%.6f agitation=%.5f look=%.3f "
                        + "crouch=%s holding=%s charge=%.5f struggle=%.6f holdTicks=%d liftTicks=%d onGround=%s",
                player.tickCount,
                player.getGameProfile().getName(),
                frame,
                coverageMedium.serializedName(),
                physicsMedium.serializedName(),
                player.getY(),
                player.getDeltaMovement().y,
                input.depth(),
                result.columnDepth(),
                result.naturalSinkLimit(),
                result.sinkLimit(),
                result.remainingDepth(),
                result.depthProgress(),
                input.motionX(), input.motionY(), input.motionZ(),
                result.motionX(), result.motionY(), result.motionZ(),
                result.horizontalSpeed(),
                result.walkScale(),
                result.verticalScale(),
                result.naturalSink(),
                result.movementSink(),
                result.disturbanceSink(),
                result.yieldResistance(),
                result.targetSinkSpeed(),
                input.settlingVelocity(),
                result.sinkStep(),
                input.agitation(),
                input.lookDelta(),
                input.crouching(),
                input.holdingStruggle(),
                input.struggleCharge(),
                result.struggleImpulse(),
                holdTicks,
                liftTicks,
                player.onGround());
        synchronized (PhysicsTraceLog.class) {
            writeLine(line);
        }
    }

    static void traceLivingSlime(ServerPlayer player, String frame, double depth, double columnDepth, double sinkLimit,
            double elasticDepth, Vec3 motionIn, Vec3 motionOut, Vec3 anchorDelta,
            boolean compressed, boolean rebounding, double sinkBias, double walkScale,
            double verticalRetention, double impactEnergyIn, double impactEnergyOut,
            boolean impactReleased, int holdTicks, int liftTicks) {
        if (!enabled(player)) {
            return;
        }
        String line = String.format(Locale.ROOT,
                "tick=%d player=%s frame=%s coverage=living_slime physics=living_slime depth=%.5f column=%.5f limit=%.5f "
                        + "remaining=%.5f elasticDepth=%.5f motionIn=(%.5f,%.5f,%.5f) motionOut=(%.5f,%.5f,%.5f) "
                        + "anchorDelta=(%.5f,%.5f,%.5f) compressed=%s rebounding=%s sinkBias=%.6f "
                        + "walk=%.5f verticalRetention=%.5f impactIn=%.5f impactOut=%.5f impactReleased=%s "
                        + "holdTicks=%d liftTicks=%d onGround=%s",
                player.tickCount,
                player.getGameProfile().getName(),
                frame,
                depth,
                columnDepth,
                sinkLimit,
                sinkLimit - depth,
                elasticDepth,
                motionIn.x, motionIn.y, motionIn.z,
                motionOut.x, motionOut.y, motionOut.z,
                anchorDelta.x, anchorDelta.y, anchorDelta.z,
                compressed,
                rebounding,
                sinkBias,
                walkScale,
                verticalRetention,
                impactEnergyIn,
                impactEnergyOut,
                impactReleased,
                holdTicks,
                liftTicks,
                player.onGround());
        synchronized (PhysicsTraceLog.class) {
            writeLine(line);
        }
    }

    static void traceExit(ServerPlayer player, SinkingMedium coverageMedium, SinkingMedium physicsMedium,
            Vec3 motion, boolean gravityOverride, float pendingCharge, int liftTicks, boolean onGround) {
        if (!enabled(player)) {
            return;
        }
        String line = String.format(Locale.ROOT,
                "tick=%d player=%s frame=exit coverage=%s physics=%s motion=(%.5f,%.5f,%.5f) "
                        + "gravityOverride=%s pendingCharge=%.5f liftTicks=%d onGround=%s noGravity=%s",
                player.tickCount,
                player.getGameProfile().getName(),
                coverageMedium.serializedName(),
                physicsMedium.serializedName(),
                motion.x, motion.y, motion.z,
                gravityOverride,
                pendingCharge,
                liftTicks,
                onGround,
                player.isNoGravity());
        synchronized (PhysicsTraceLog.class) {
            writeLine(line);
        }
    }

    public static void traceDecal(ServerPlayer player, String message) {
        if (!enabled(player)) {
            return;
        }
        synchronized (PhysicsTraceLog.class) {
            writeLine("tick=" + player.tickCount
                    + " player=" + player.getGameProfile().getName()
                    + " frame=decal " + message);
        }
    }

    static void traceSableProbe(ServerPlayer player, int candidates, int samples, int mudHits,
            SinkingMedium medium, double depth) {
        if (!enabled(player)) {
            return;
        }
        String line = String.format(Locale.ROOT,
                "tick=%d player=%s frame=sable-probe candidates=%d samples=%d mudHits=%d contact=%s depth=%.5f",
                player.tickCount,
                player.getGameProfile().getName(),
                candidates,
                samples,
                mudHits,
                medium == null ? "none" : medium.serializedName(),
                depth);
        synchronized (PhysicsTraceLog.class) {
            writeLine(line);
        }
    }

    public static void traceTentacle(ServerPlayer player, String message) {
        if (!enabled(player)) {
            return;
        }
        synchronized (PhysicsTraceLog.class) {
            writeLine("tick=" + player.tickCount
                    + " player=" + player.getGameProfile().getName()
                    + " frame=tentacle " + message);
        }
    }

    public static synchronized String debugLogPath() {
        return (activePath == null ? logDirectory() : activePath).toAbsolutePath().toString();
    }

    private static void ensureWriter() {
        if (writer != null) {
            return;
        }
        try {
            Path directory = logDirectory();
            Files.createDirectories(directory);
            activePath = directory.resolve("physics-" + LocalDateTime.now().format(FILE_TIME) + ".log");
            writer = Files.newBufferedWriter(
                    activePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE);
            writeLine("=== Mirebound: Sinking Depths physics trace ===");
        } catch (IOException | RuntimeException exception) {
            Mirebound.LOGGER.warn("Failed to open Mirebound: Sinking Depths physics trace log", exception);
            writer = null;
        }
    }

    private static void writeLine(String message) {
        ensureWriter();
        if (writer == null) {
            return;
        }
        try {
            writer.write(LocalDateTime.now().format(LINE_TIME));
            writer.write(' ');
            writer.write(message);
            writer.newLine();
            pendingFlushLines++;
            if (pendingFlushLines >= FLUSH_BATCH_LINES) {
                writer.flush();
                pendingFlushLines = 0;
            }
        } catch (IOException exception) {
            Mirebound.LOGGER.warn("Failed to write Mirebound: Sinking Depths physics trace log: {}", activePath, exception);
        }
    }

    private static void closeIfIdle() {
        if (!ENABLED_PLAYERS.isEmpty() || writer == null) {
            return;
        }
        MOB_TRACE_TICKS.clear();
        closeWriter();
    }

    private static void closeWriter() {
        if (writer == null) {
            pendingFlushLines = 0;
            return;
        }
        try {
            writer.close();
        } catch (IOException exception) {
            Mirebound.LOGGER.warn("Failed to close Mirebound: Sinking Depths physics trace log: {}", activePath, exception);
        } finally {
            writer = null;
            pendingFlushLines = 0;
        }
    }

    private static Path logDirectory() {
        return FMLPaths.GAMEDIR.get().resolve("logs").resolve("Fmud");
    }
}
