package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.AdhesionStrandProfile;
import com.fish.mirebound.mud.MudEntityGeometry;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudBehaviorContext;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.TenderFleshMechanics;
import com.fish.mirebound.mud.TenderFleshPoolRules;
import com.fish.mirebound.mud.TenderFleshProfile;
import com.fish.mirebound.network.payload.MudDebugSyncPayload;
import com.fish.mirebound.network.payload.MudSurfaceImpactPayload;
import com.fish.mirebound.network.payload.TenderFleshEnclosurePayload;
import com.fish.mirebound.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tick-rate state for lightweight procedural mud surfaces. Rendering never scans
 * blocks and only interpolates these retained instances.
 */
final class MudSurfaceEffectManager {
    private static final double PIXEL = 1.0D / 16.0D;
    private static final double IMPACT_CONTACT_RADIUS_PIXELS = 3.0D;
    static final double SURFACE_CELL_VISUAL_HEIGHT_EPSILON =
            MudSurfaceCellBudget.NEAR_VISUAL_HEIGHT_EPSILON;
    static final int MAX_ADHESION_STRANDS_PER_PLAYER = 16;
    private static final double ADHESION_BODY_SURFACE_GAP = 0.004D;
    private static final int[] ADHESION_RING_SLOT_ORDER = {
            0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15
    };
    static final int ADHESION_BRIDGE_NODE_COUNT = 7;
    private static final int ADHESION_SURFACE_SEARCH_PIXELS = 4;
    private static final Map<Integer, Hole> HOLES = new HashMap<>();
    private static final Map<Integer, TenderFleshEnclosureState> TENDER_FLESH_STATES = new HashMap<>();
    private static final Map<Integer, PendingImpact> PENDING_IMPACTS = new HashMap<>();
    private static int retainedSurfaceCells;
    private static ClientLevel level;
    private static long randomState = 0x4f1bbcdc6765a56dL;

    private MudSurfaceEffectManager() {
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !MudSurfaceClientSettings.enabled()) {
            if (level != minecraft.level || !HOLES.isEmpty()) {
                reset();
            }
            return;
        }
        if (level != minecraft.level) {
            reset();
            level = minecraft.level;
        }
        if ((minecraft.level.getGameTime() & 31L) == 0L) {
            long now = minecraft.level.getGameTime();
            TENDER_FLESH_STATES.entrySet().removeIf(
                    entry -> now - entry.getValue().receivedTick > 100L);
        }
        MudSurfaceBubbleSystem.resizePool();

        for (Hole hole : HOLES.values()) {
            hole.seenThisTick = false;
            hole.beginTick();
        }
        for (Player player : minecraft.level.players()) {
            if (ClientPollutionVisibility.isSuppressed(player)
                    || ClientAssimilationState.isFrozen(player.getId())
                    || ClientPollutionVisibility.isContactSamplingSuppressed(player)) {
                // Detached views stop creating deformation, but retained world
                // geometry must finish its ordinary closure instead of vanishing.
                PENDING_IMPACTS.remove(player.getId());
                continue;
            }
            Contact contact = contactFor(minecraft, player);
            if (contact != null && enabled(contact.profilePos, contact.medium)) {
                updateHole(minecraft, player, contact);
            }
        }
        mergeClosedEruptionVents();
        finishHoles(minecraft);
        tickPendingImpacts();
        mergeImpactHolesIntoPressureHoles();
        MudSideSurfaceEffectManager.tick(minecraft.level);
        MudSurfaceBubbleSystem.tick(minecraft, level);
    }

    static Iterable<Hole> holes() {
        return HOLES.values();
    }

    static Hole holeFor(int entityId) {
        return HOLES.get(entityId);
    }

    static Bubble[] bubbles() {
        return MudSurfaceBubbleSystem.bubbles();
    }

    static void spawnProbeBubble(Vec3 point, Vec3 normal, SinkingMedium medium) {
        MudSurfaceBubbleSystem.spawnProbe(level, point, normal, medium);
    }

    static void acceptImpact(MudSurfaceImpactPayload payload) {
        PendingImpact current = PENDING_IMPACTS.get(payload.entityId());
        if (current == null || current.medium != payload.medium()) {
            PENDING_IMPACTS.put(payload.entityId(), new PendingImpact(
                    payload.medium(),
                    payload.origin(),
                    supportPos(payload.origin(), new Vec3(0.0D, 1.0D, 0.0D)),
                    payload.impactStrength(),
                    payload.volumeFraction(),
                    20));
            return;
        }
        current.impactStrength = Math.max(current.impactStrength, payload.impactStrength());
        current.volumeFraction = Math.max(current.volumeFraction, payload.volumeFraction());
        current.origin = payload.origin();
        current.profilePos = supportPos(payload.origin(), new Vec3(0.0D, 1.0D, 0.0D));
        current.remainingTicks = 20;
    }

    static boolean openEruptionVent(int ventId, SinkingMedium medium,
            Vec3 origin, double radiusPixels, long seed, long visualSource) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ventId <= 0) {
            return false;
        }
        if (level != minecraft.level) {
            reset();
            level = minecraft.level;
        }
        int key = eruptionHoleKey(ventId);
        Hole hole = HOLES.get(key);
        if (hole == null && HOLES.size() >= MudSurfaceClientSettings.maxHoles()) {
            removeFaintestHole();
        }
        if (hole == null && HOLES.size() < MudSurfaceClientSettings.maxHoles()) {
            hole = new Hole(key, seed);
            HOLES.put(key, hole);
        }
        if (hole == null) {
            return false;
        }
        hole.medium = medium;
        hole.visualSource = visualSource;
        hole.profilePos = supportPos(origin, new Vec3(0.0D, 1.0D, 0.0D));
        hole.center = alignedCenter(origin);
        hole.targetCenter = hole.center;
        hole.normal = new Vec3(0.0D, 1.0D, 0.0D);
        hole.targetNormal = hole.normal;
        hole.axisX = new Vec3(1.0D, 0.0D, 0.0D);
        hole.axisZ = new Vec3(0.0D, 0.0D, 1.0D);
        hole.radius = Math.max(PIXEL, radiusPixels * PIXEL);
        hole.visibility = 1.0D;
        hole.targetVisibility = 1.0D;
        hole.active = true;
        hole.persistentSurfaceSource = true;
        prepareSurfaceUpdate(hole, minecraft.level.getGameTime());
        rebuildSurfaceMask(hole);
        stampEruptionDepression(hole, radiusPixels);
        return true;
    }

    static void closeEruptionVent(int ventId, int mergeEntityId) {
        Hole vent = HOLES.get(eruptionHoleKey(ventId));
        if (vent == null) {
            return;
        }
        vent.persistentSurfaceSource = false;
        vent.active = false;
        vent.seenThisTick = false;
        if (mergeEntityId >= 0) {
            vent.pendingMergeEntityId = mergeEntityId;
            Hole player = HOLES.get(mergeEntityId);
            if (player != null) {
                transferSurfaceCells(vent, player);
                removeHole(vent.entityId);
            }
        }
    }

    static void forgetEruptionVent(int ventId) {
        removeHole(eruptionHoleKey(ventId));
    }

    private static void mergeClosedEruptionVents() {
        Iterator<Map.Entry<Integer, Hole>> iterator = HOLES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Hole> entry = iterator.next();
            Hole vent = entry.getValue();
            if (vent.pendingMergeEntityId < 0) {
                continue;
            }
            Hole player = HOLES.get(vent.pendingMergeEntityId);
            if (player != null) {
                transferSurfaceCells(vent, player);
                retainedSurfaceCells -= vent.cells.size();
                iterator.remove();
            }
        }
    }

    static void acceptTenderFleshEnclosure(TenderFleshEnclosurePayload payload) {
        TENDER_FLESH_STATES.put(payload.entityId(), new TenderFleshEnclosureState(
                payload.active(), payload.retreating(), payload.brokenMask(),
                payload.pillarDamagePacked(), payload.pillarRequiredHitsPacked(),
                payload.cooldownTicks(), payload.progress(),
                payload.anchorX(), payload.anchorY(), payload.anchorZ(),
                level == null ? 0L : level.getGameTime()));
    }

    static void scheduleProbeBubble(
            Vec3 point, Vec3 normal, Vec3 preferredTangent,
            SinkingMedium medium, int delayTicks) {
        scheduleProbeBubble(point, normal, preferredTangent, medium,
                supportPos(point, safeNormal(normal)), delayTicks);
    }

    static void scheduleProbeBubble(
            Vec3 point, Vec3 normal, Vec3 preferredTangent,
            SinkingMedium medium, BlockPos profilePos, int delayTicks) {
        MudSurfaceBubbleSystem.schedule(
                level, point, normal, preferredTangent, medium, profilePos, delayTicks);
    }

    static boolean hasCurrentSupport(SurfaceCell cell) {
        SurfaceSupport support = currentSupport(cell);
        return support != null && sameRenderedSurface(
                cell.renderedHit, cell.renderedPatch,
                support.renderedHit(), support.renderedPatch());
    }

    static void reset() {
        HOLES.clear();
        retainedSurfaceCells = 0;
        TENDER_FLESH_STATES.clear();
        PENDING_IMPACTS.clear();
        MudSideSurfaceEffectManager.reset();
        MudSurfaceBubbleSystem.reset();
        level = null;
    }

    private static Contact contactFor(Minecraft minecraft, Player player) {
        if (player == minecraft.player) {
            MudPhysics.ClientSurfaceContact local = MudPhysics.clientSurfaceContact(player);
            if (local != null) {
                Vec3 surfacePoint = local.surfacePoint();
                Vec3 surfaceNormal = local.surfaceNormal();
                Vec3 surfaceAxisX = local.surfaceAxisX();
                Vec3 surfaceAxisZ = local.surfaceAxisZ();
                double contactDepth = local.depth();
                VisualSurface visual = local.physicalized() ? null : visualSurfaceAt(
                        minecraft.level, local.surfaceProfilePos(), local.medium(),
                        surfacePoint.x, surfacePoint.z);
                if (visual != null) {
                    surfacePoint = visual.point();
                    surfaceNormal = visual.hit().normal();
                    surfaceAxisX = visual.hit().axisX();
                    surfaceAxisZ = visual.hit().axisZ();
                    contactDepth = renderedSurfaceDepth(
                            contactDepth, surfacePoint.y,
                            player.getBoundingBox().minY);
                }
                boolean fleshTemplate = MudBehaviorContext.tenderFlesh(
                        minecraft.level, local.surfaceProfilePos(), local.medium());
                Direction visualFace = contactVisualFace(
                        minecraft.level, local.surfaceProfilePos(),
                        surfaceNormal, local.physicalized());
                return new Contact(
                        local.medium(),
                        surfacePoint,
                        surfaceNormal,
                        surfaceAxisX,
                        surfaceAxisZ,
                        local.surfaceProfilePos(),
                        MudSurfaceAppearance.captureVisualSource(
                                minecraft.level, local.surfaceProfilePos(), visualFace),
                        local.physicalized(),
                        contactDepth,
                        local.availableDepth(),
                        local.agitation(),
                        local.horizontalSpeed(),
                        local.walkScale(),
                        fleshTemplate
                                ? MudPhysics.clientTenderFleshContraction(player) : 0.0D,
                        fleshTemplate
                                ? MudPhysics.clientTenderFleshWrap(player) : 0.0D,
                        fleshTemplate
                                ? MudPhysics.clientTenderFleshPressure(player) : 0.0D);
            }
        }
        MudDebugSyncPayload debug = ClientMudDebugState.currentFor(player.getId());
        if (debug == null || !debug.active() || debug.physicalized()) {
            return null;
        }
        Vec3 motion = player.getDeltaMovement();
        Vec3 surfacePoint = new Vec3(player.getX(), player.getY() + debug.depth(), player.getZ());
        BlockPos profilePos = supportPos(surfacePoint, new Vec3(0.0D, 1.0D, 0.0D));
        VisualSurface visual = visualSurfaceAt(
                minecraft.level, profilePos, debug.medium(),
                surfacePoint.x, surfacePoint.z);
        Vec3 surfaceNormal = visual == null
                ? new Vec3(0.0D, 1.0D, 0.0D) : visual.hit().normal();
        Vec3 surfaceAxisX = visual == null
                ? new Vec3(1.0D, 0.0D, 0.0D) : visual.hit().axisX();
        Vec3 surfaceAxisZ = visual == null
                ? new Vec3(0.0D, 0.0D, 1.0D) : visual.hit().axisZ();
        double contactDepth = debug.depth();
        if (visual != null) {
            surfacePoint = visual.point();
            contactDepth = renderedSurfaceDepth(
                    contactDepth, surfacePoint.y,
                    player.getBoundingBox().minY);
        }
        boolean fleshTemplate = MudBehaviorContext.tenderFlesh(
                minecraft.level, profilePos, debug.medium());
        double contraction = fleshTemplate
                ? TenderFleshMechanics.contraction(
                        MudMediumRuntime.tenderFleshProfile(minecraft.level, profilePos),
                        minecraft.level.getGameTime()) : 0.0D;
        return new Contact(
                debug.medium(),
                surfacePoint,
                surfaceNormal,
                surfaceAxisX,
                surfaceAxisZ,
                profilePos,
                MudSurfaceAppearance.captureVisualSource(
                        minecraft.level, profilePos, Direction.UP),
                false,
                contactDepth,
                debug.columnDepth(),
                (float) debug.agitation(),
                Math.sqrt(motion.x * motion.x + motion.z * motion.z),
                debug.walkScale(),
                contraction,
                0.0D,
                0.0D);
    }

    private static Direction contactVisualFace(ClientLevel level, BlockPos profilePos,
            Vec3 worldNormal, boolean physicalized) {
        Vec3 direction = worldNormal;
        if (physicalized && level != null && profilePos != null) {
            Object subLevel = SableCompat.subLevelAtStorage(level, profilePos);
            Vec3 local = SableCompat.toLocalDirection(subLevel, worldNormal);
            if (local != null && local.lengthSqr() > 1.0E-8D) {
                direction = local;
            }
        }
        if (direction == null || direction.lengthSqr() <= 1.0E-8D) {
            return Direction.UP;
        }
        Direction best = Direction.UP;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Direction candidate : Direction.values()) {
            double dot = direction.x * candidate.getStepX()
                    + direction.y * candidate.getStepY()
                    + direction.z * candidate.getStepZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    private static void updateHole(
            Minecraft minecraft, Player player, Contact contact) {
        Hole hole = HOLES.get(player.getId());
        if (hole == null) {
            if (HOLES.size() >= MudSurfaceClientSettings.maxHoles()) {
                removeFaintestHole();
            }
            if (HOLES.size() >= MudSurfaceClientSettings.maxHoles()) {
                removeFarthestEruptionHole(player.position());
            }
            if (HOLES.size() >= MudSurfaceClientSettings.maxHoles()) {
                return;
            }
            hole = new Hole(player.getId(), mix(player.getUUID().getLeastSignificantBits()));
            hole.center = contact.physicalized
                    ? contact.surfacePoint : alignedSurfaceCenter(contact);
            hole.normal = contact.surfaceNormal;
            HOLES.put(player.getId(), hole);
        }
        AdhesionStrandProfile contactProfile = MudMediumRuntime.adhesionStrands(
                level, contact.profilePos, contact.medium);
        if (adhesionSessionShouldReset(
                hole.active,
                hole.inactiveTicks,
                Math.max(2, contactProfile.anchorGraceTicks()),
                hole.medium != contact.medium || hole.physicalized != contact.physicalized)) {
            for (AdhesionStrand strand : hole.adhesionStrands) {
                strand.reset();
            }
            hole.adhesionContactTicks = 0;
            hole.adhesionSession++;
            hole.adhesionProfile = null;
            Arrays.fill(hole.adhesionSessionTouched, false);
        }

        double radiusScale = value(
                contact.profilePos, contact.medium, MudPhysicsParameter.SURFACE_HOLE_RADIUS_SCALE);
        MudEntityGeometry.PlaneSlice slice = surfaceSlice(
                player, contact, radiusScale);

        Vec3 normal = safeNormal(contact.surfaceNormal);
        Vec3 axisX = orthogonalAxis(contact.surfaceAxisX, normal,
                new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 axisZ = orthogonalAxis(contact.surfaceAxisZ, normal, normal.cross(axisX));
        if (axisX.cross(axisZ).dot(normal) < 0.0D) {
            axisZ = axisZ.scale(-1.0D);
        }
        Vec3 planarMotion = reject(player.getDeltaMovement(), normal);
        double planarSpeed = planarMotion.length();
        Vec3 targetCenter = contact.physicalized
                ? contact.surfacePoint : alignedSurfaceCenter(contact);
        hole.seenThisTick = true;
        hole.active = true;
        hole.inactiveTicks = 0;
        hole.medium = contact.medium;
        hole.visualSource = contact.visualSource;
        hole.physicalized = contact.physicalized;
        hole.profilePos = contact.profilePos;
        hole.fleshTemplateEnabled = MudBehaviorContext.tenderFlesh(
                level, contact.profilePos, contact.medium);
        hole.targetCenter = targetCenter;
        hole.targetNormal = normal;
        hole.targetVisibility = slice.empty()
                ? 0.0D
                : Mth.clamp((contact.depth - 0.008D) / 0.055D, 0.0D, 1.0D);
        hole.agitation = contact.agitation;
        hole.speed = contact.horizontalSpeed;
        hole.fleshContraction = contact.fleshContraction;
        hole.fleshWrap = contact.fleshWrap;
        hole.fleshPressure = contact.fleshPressure;
        TenderFleshProfile fleshProfile = hole.fleshTemplateEnabled
                ? MudMediumRuntime.tenderFleshProfile(level, contact.profilePos) : null;
        hole.fleshExposedHeight = fleshProfile == null
                ? exposedBodyHeight(player, contact.depth, 0.0D)
                : exposedBodyHeight(player, contact.depth,
                        fleshProfile.enclosureHeightMarginPixels());
        hole.axisX = axisX;
        hole.axisZ = axisZ;
        hole.center = hole.targetCenter;
        hole.normal = safeNormal(lerp(hole.normal, hole.targetNormal, 0.36D));
        boolean updateSurface = !contact.physicalized
                && surfaceUpdateDue(hole, minecraft);
        if (updateSurface) {
            prepareSurfaceUpdate(hole, minecraft.level.getGameTime());
            hole.surfaceUpdateRequested = true;
        }
        double closeTicks = effectiveCloseTicks(hole.profilePos, hole.medium);
        if (hole.fleshTemplateEnabled) {
            boolean wasEnclosureActive = hole.fleshEnclosureActive;
            TenderFleshEnclosureState synced = syncedTenderFleshState(player.getId());
            if (synced != null) {
                hole.fleshBrokenMask = synced.brokenMask;
                hole.fleshPillarDamagePacked = synced.pillarDamagePacked;
                hole.fleshPillarRequiredHitsPacked = synced.pillarRequiredHitsPacked;
                hole.fleshEnclosureActive = synced.active;
                if (synced.active || synced.retreating) {
                    hole.fleshEnclosureCenter = new Vec3(
                            synced.anchorX, synced.anchorY, synced.anchorZ);
                    hole.fleshEnclosureAnchorSet = true;
                }
            } else {
                boolean poolQualified = TenderFleshPoolRules.qualifies(
                        level, contact.profilePos, fleshProfile, contact.availableDepth,
                        player.getX() - contact.profilePos.getX(),
                        player.getZ() - contact.profilePos.getZ());
                double threshold = fleshProfile.enclosureWalkScaleThreshold();
                if (!poolQualified && !wasEnclosureActive) {
                    hole.fleshEnclosureActive = false;
                } else if (hole.fleshEnclosureActive || contact.walkScale <= threshold) {
                    hole.fleshEnclosureActive = true;
                }
            }
            boolean syncedRetreating = synced != null && synced.retreating;
            double targetPillarProgress = hole.fleshEnclosureActive && !syncedRetreating
                    ? 1.0D : 0.0D;
            boolean withdrawing = targetPillarProgress < hole.fleshPillarProgress;
            if (withdrawing && !hole.fleshPillarWithdrawing) {
                captureFleshRetreatFrame(hole);
            } else if (!withdrawing && targetPillarProgress > 0.0D) {
                hole.fleshRetreatFrameCaptured = false;
            }
            hole.fleshPillarWithdrawing = withdrawing;
            hole.fleshPillarProgress = approachRate(
                    hole.fleshPillarProgress,
                    targetPillarProgress,
                    targetPillarProgress >= hole.fleshPillarProgress
                            ? fleshProfile.enclosureRiseRate()
                            : fleshProfile.enclosureWithdrawRate());
            if (!hole.fleshEnclosureActive && !syncedRetreating
                    && hole.fleshPillarProgress <= 0.002D) {
                hole.fleshEnclosureAnchorSet = false;
            }
            if (!hole.fleshEnclosureAnchorSet && (hole.fleshEnclosureActive || syncedRetreating)) {
                hole.fleshEnclosureCenter = hole.center;
                hole.fleshEnclosureAnchorSet = true;
            }
        } else {
            hole.fleshEnclosureActive = false;
            hole.fleshBrokenMask = 0;
            hole.fleshPillarDamagePacked = 0;
            hole.fleshPillarRequiredHitsPacked = 0;
            hole.fleshPillarWithdrawing = hole.fleshPillarProgress > 0.0D;
        }
        hole.radius = sliceRadius(slice, player.position());
        hole.visibility = approach(hole.visibility, hole.targetVisibility, 0.35D, closeTicks);
        updateAdhesionStrands(player, hole, true);
        if (updateSurface) {
            rebuildSurfaceMask(hole);
            stampSurface(hole, slice);
        } else if (!hole.cells.isEmpty()) {
            if (contact.physicalized) {
                discardSurfaceCells(hole);
            }
        }

        if (!contact.physicalized && !hole.fleshTemplateEnabled
                && hole.visibility > 0.05D) {
            double rate = value(
                    hole.profilePos, hole.medium, MudPhysicsParameter.SURFACE_BUBBLE_RATE);
            hole.bubbleAccumulator += rate * (0.55D + hole.visibility * 0.75D
                    + hole.agitation * 3.5D + Mth.clamp(hole.speed * 12.0D, 0.0D, 2.0D));
            int spawned = 0;
            while (hole.bubbleAccumulator >= 1.0D && spawned < 3) {
                hole.bubbleAccumulator -= 1.0D;
                MudSurfaceBubbleSystem.spawnForHole(level, hole);
                spawned++;
            }
        }
    }

    private static void finishHoles(Minecraft minecraft) {
        long gameTime = minecraft.level.getGameTime();
        Iterator<Hole> iterator = HOLES.values().iterator();
        while (iterator.hasNext()) {
            Hole hole = iterator.next();
            if (hole.persistentSurfaceSource) {
                hole.active = true;
                hole.seenThisTick = true;
                hole.visibility = 1.0D;
            }
            if (!hole.seenThisTick) {
                hole.active = false;
                hole.inactiveTicks++;
                TenderFleshEnclosureState synced = hole.fleshTemplateEnabled
                        ? syncedTenderFleshState(hole.entityId) : null;
                if (synced != null && synced.active) {
                    hole.fleshEnclosureActive = true;
                    hole.fleshBrokenMask = synced.brokenMask;
                    hole.fleshPillarDamagePacked = synced.pillarDamagePacked;
                    hole.fleshPillarRequiredHitsPacked = synced.pillarRequiredHitsPacked;
                    hole.fleshEnclosureCenter = new Vec3(
                            synced.anchorX, synced.anchorY, synced.anchorZ);
                    hole.fleshEnclosureAnchorSet = true;
                    hole.fleshPillarWithdrawing = false;
                    hole.visibility = Math.max(hole.visibility, 1.0D);
                    TenderFleshProfile profile = MudMediumRuntime.tenderFleshProfile(
                            level, hole.profilePos);
                    hole.fleshPillarProgress = approachRate(
                            hole.fleshPillarProgress, 1.0D,
                            profile.enclosureRiseRate());
                } else {
                    double closeTicks = effectiveCloseTicks(hole.profilePos, hole.medium);
                    hole.visibility = Math.max(0.0D, hole.visibility - 1.0D / closeTicks);
                    hole.fleshEnclosureActive = false;
                    boolean withdrawing = hole.fleshPillarProgress > 0.0D;
                    if (withdrawing && !hole.fleshPillarWithdrawing) {
                        captureFleshRetreatFrame(hole);
                    }
                    hole.fleshPillarWithdrawing = withdrawing;
                    if (hole.fleshTemplateEnabled && level != null) {
                        TenderFleshProfile profile = MudMediumRuntime.tenderFleshProfile(
                                level, hole.profilePos);
                        hole.fleshPillarProgress = approachRate(
                                hole.fleshPillarProgress, 0.0D,
                                profile.enclosureWithdrawRate());
                    } else {
                        hole.fleshPillarProgress = 0.0D;
                    }
                }
                hole.hasStampCenter = false;
            }
            Player player = minecraft.level == null
                    ? null : (minecraft.level.getEntity(hole.entityId) instanceof Player found ? found : null);
            if (!hole.seenThisTick) {
                updateAdhesionStrands(player, hole, false);
            }
            if (hole.surfaceUpdateRequested || surfaceUpdateDue(hole, minecraft)) {
                prepareSurfaceUpdate(hole, gameTime);
                if (hole.persistentSurfaceSource) {
                    for (SurfaceCell cell : hole.cells.values()) {
                        if (cell.depression > 0.003D) {
                            cell.refreshed = true;
                        }
                    }
                }
                int elapsedTicks = hole.lastSurfaceUpdateTick == Long.MIN_VALUE
                        ? 1
                        : Mth.clamp((int) (gameTime - hole.lastSurfaceUpdateTick), 1, 4);
                updateSurfaceField(hole, elapsedTicks);
                hole.lastSurfaceUpdateTick = gameTime;
            }
            if (!hole.active && hole.cells.isEmpty()
                    && hole.fleshPillarProgress <= 0.002D
                    && !hasActiveAdhesionStrand(hole)) {
                hole.visibility = 0.0D;
            }
            if (!hole.active && hole.visibility <= 0.002D
                    && hole.fleshPillarProgress <= 0.002D
                    && hole.cells.isEmpty() && !hasActiveAdhesionStrand(hole)) {
                iterator.remove();
            }
        }
    }

    private static Vec3 surfaceFocus(Minecraft minecraft) {
        return minecraft.gameRenderer.getMainCamera() == null
                ? minecraft.player == null ? null : minecraft.player.position()
                : minecraft.gameRenderer.getMainCamera().getPosition();
    }

    private static boolean surfaceUpdateDue(Hole hole, Minecraft minecraft) {
        if (hole.cells.isEmpty() || minecraft.level == null) {
            return true;
        }
        Vec3 focus = surfaceFocus(minecraft);
        double distanceSquared = focus == null
                ? 0.0D : hole.center.distanceToSqr(focus);
        boolean localPlayer = minecraft.player != null
                && hole.entityId == minecraft.player.getId();
        int interval = MudSurfaceCellBudget.updateIntervalTicks(
                localPlayer, distanceSquared);
        return MudSurfaceCellBudget.scheduledUpdate(
                minecraft.level.getGameTime(), hole.entityId, interval);
    }

    private static void prepareSurfaceUpdate(Hole hole, long gameTime) {
        if (hole.lastSurfacePreparedTick == gameTime) {
            return;
        }
        hole.beginSurfaceUpdate();
        hole.lastSurfacePreparedTick = gameTime;
    }

    private static boolean hasActiveAdhesionStrand(Hole hole) {
        for (AdhesionStrand strand : hole.adhesionStrands) {
            if (strand.active) {
                return true;
            }
        }
        return false;
    }

    private static void captureFleshRetreatFrame(Hole hole) {
        hole.fleshRetreatCenter = hole.fleshEnclosureAnchorSet
                ? hole.fleshEnclosureCenter : hole.center;
        hole.fleshRetreatNormal = hole.normal;
        hole.fleshRetreatAxisX = hole.axisX;
        hole.fleshRetreatAxisZ = hole.axisZ;
        hole.fleshRetreatHeight = hole.fleshExposedHeight;
        hole.fleshRetreatFrameCaptured = true;
    }

    static double exposedBodyHeight(Player player, double depth, double marginPixels) {
        double bodyHeight = player == null ? 1.80D : Math.max(0.0D, player.getBbHeight());
        double margin = Math.max(0.0D, marginPixels) * PIXEL;
        return Mth.clamp(bodyHeight - Math.max(0.0D, depth) + margin,
                0.0D, bodyHeight + margin);
    }

    static double exposedBodyHeight(Player player, double depth) {
        return exposedBodyHeight(player, depth, 0.0D);
    }

    private static TenderFleshEnclosureState syncedTenderFleshState(int entityId) {
        TenderFleshEnclosureState state = TENDER_FLESH_STATES.get(entityId);
        if (state == null || level == null
                || level.getGameTime() - state.receivedTick > 40L) {
            return null;
        }
        return state;
    }

    private static void updateAdhesionStrands(Player player, Hole hole, boolean contactingMedium) {
        for (AdhesionStrand strand : hole.adhesionStrands) {
            strand.beginTick();
        }
        AdhesionStrandProfile contactProfile = MudMediumRuntime.adhesionStrands(
                level, hole.profilePos, hole.medium);
        boolean hasRetainedStrands = hasActiveAdhesionStrand(hole);
        if (contactingMedium && contactProfile.enabled()
                && (!hasRetainedStrands || hole.adhesionProfile == null)) {
            // A local block profile may stop being the current contact before its
            // already-created bridges have stretched far enough to break. Freeze
            // the immutable profile used to create this adhesion session so the
            // lifecycle and renderer cannot fall back to a disabled world profile.
            hole.adhesionProfile = contactProfile;
        }
        AdhesionStrandProfile profile = adhesionLifecycleProfile(
                hole.adhesionProfile, contactProfile, hasRetainedStrands);
        boolean presentationEnabled = profile.enabled();
        boolean enabled = contactingMedium && contactProfile.enabled()
                && player != null;
        boolean geometric = enabled && profile.geometricAnchors();
        MudEntityGeometry.SurfacePixelSampler[] geometricSamplers = player == null
                ? null : new MudEntityGeometry.SurfacePixelSampler[MudBodyPart.COUNT];
        hole.adhesionContactTicks = enabled ? hole.adhesionContactTicks + 1 : 0;
        boolean geometricRefresh = geometric && (hole.adhesionContactTicks - 1)
                % Math.max(1, profile.ringRefreshTicks()) == 0;
        if (geometricRefresh) {
            captureAdhesionSessionContact(player, hole, profile, geometricSamplers);
        }
        int desired = 0;
        if (enabled) {
            int minimum = Mth.clamp(profile.minimumCount(), 0, MAX_ADHESION_STRANDS_PER_PLAYER);
            int maximum = Mth.clamp(profile.maximumCount(), minimum, MAX_ADHESION_STRANDS_PER_PLAYER);
            if (profile.sheetEnabled()) {
                minimum = Math.max(minimum, Mth.clamp(
                        profile.sheetMinimumRibs(), 2, MAX_ADHESION_STRANDS_PER_PLAYER));
                maximum = Math.max(maximum, minimum);
            }
            double activity = Mth.clamp(
                    hole.visibility * 0.72D + hole.agitation * 0.20D + hole.speed * 1.8D,
                    0.0D, 1.0D);
            int target = minimum + (int) Math.round((maximum - minimum) * activity);
            desired = adhesionSpawnCapacity(
                    hole.adhesionContactTicks,
                    profile.attachDelayTicks(),
                    profile.spawnIntervalTicks(),
                    profile.initialCount(),
                    target);
        }

        double breakLength = profile.breakLength();
        double retractTicks = Math.max(2.0D, profile.retractTicks());
        AdhesionAnchorRange anchorRange = null;
        List<AdhesionAnchorCandidate> geometricCandidates = null;
        for (int index = 0; index < hole.adhesionStrands.length; index++) {
            AdhesionStrand strand = hole.adhesionStrands[index];
            if (!strand.active && index < desired) {
                if (geometric && !geometricRefresh) {
                    continue;
                }
                strand.activate(hole.seed
                        ^ (long) hole.adhesionSession * 0x632be59bd9b4e019L, index);
                boolean anchored;
                if (geometric) {
                    if (geometricCandidates == null) {
                        geometricCandidates = collectGeometricAnchorCandidates(
                                player, hole, profile, geometricSamplers);
                    }
                    anchored = assignGeometricAnchor(
                                    hole, strand, index, geometricCandidates)
                            && updateGeometricAdhesionEndpoints(
                                    player, hole, strand, profile, geometricSamplers);
                } else {
                    if (anchorRange == null) {
                        anchorRange = adhesionAnchorRange(player, hole, profile);
                    }
                    anchored = chooseAdhesionBodyAnchor(
                                    player, hole, strand, profile, desired, anchorRange)
                            && updateAdhesionStrandEndpoints(player, hole, strand, profile);
                }
                if (!anchored) {
                    strand.reset();
                    continue;
                }
                strand.initializeBridge(profile);
            }
            if (strand.active && !strand.breaking) {
                boolean valid = player != null && presentationEnabled;
                if (valid && strand.geometricAnchor) {
                    valid = updateGeometricAdhesionEndpoints(
                            player, hole, strand, profile, geometricSamplers);
                } else if (valid) {
                    valid = updateAdhesionStrandEndpoints(
                            player, hole, strand, profile);
                }
                if (valid) {
                    strand.advanceAttachment(profile);
                    strand.updateBridge(profile);
                } else if (strand.bridgeInitialized
                        && strand.surfacePoint != Vec3.ZERO
                        && strand.bodyPoint != Vec3.ZERO) {
                    // A transient support/coverage miss is not a break reason.
                    // Keep the last real endpoints and continue integrating the
                    // bridge, rather than leaving the whole strand at its last frame.
                    strand.advanceAttachment(profile);
                    strand.updateBridge(profile);
                }
                boolean overstretched = strand.surfacePoint != Vec3.ZERO
                        && strand.bodyPoint != Vec3.ZERO
                        && strand.surfacePoint.distanceTo(strand.bodyPoint) > breakLength;
                // Adhesion is intentionally length-driven. A transient contact,
                // coverage, or support miss keeps the last bridge state alive;
                // only real endpoint separation may start retraction.
                if (overstretched) {
                    strand.breakCandidateTicks++;
                } else {
                    strand.breakCandidateTicks = 0;
                }
                if (strand.breakCandidateTicks >= Math.max(1, profile.breakConfirmTicks())) {
                    strand.beginBreaking();
                }
            }
            if (strand.active && strand.breaking) {
                if (player != null) {
                    followStoredAdhesionBodyEndpoint(player, strand, geometricSamplers);
                }
                strand.updateBridge(profile);
                strand.breakProgress = Math.min(1.0D,
                        strand.breakProgress + 1.0D / retractTicks);
                if (strand.breakProgress >= 1.0D) {
                    strand.reset();
                }
            }
        }
    }

    static int adhesionSpawnCapacity(
            int contactTicks, int attachDelayTicks, int spawnIntervalTicks,
            int initialCount, int targetCount) {
        int stableTicks = contactTicks - Math.max(0, attachDelayTicks);
        if (stableTicks <= 0 || targetCount <= 0) {
            return 0;
        }
        int interval = Math.max(1, spawnIntervalTicks);
        int initial = Mth.clamp(initialCount, 1, targetCount);
        return Math.min(targetCount, initial + (stableTicks - 1) / interval);
    }

    static int adhesionRingSlot(int strandIndex) {
        return ADHESION_RING_SLOT_ORDER[Math.floorMod(
                strandIndex, ADHESION_RING_SLOT_ORDER.length)];
    }

    static boolean adhesionSessionCandidate(
            boolean touchedThisSession, float coverage,
            double minimumCoverage, boolean matchingMedium) {
        return touchedThisSession && matchingMedium && coverage >= minimumCoverage;
    }

    static boolean adhesionSessionShouldReset(
            boolean active, int inactiveTicks, int graceTicks, boolean mediumChanged) {
        return mediumChanged || (!active && inactiveTicks > Math.max(2, graceTicks));
    }

    static AdhesionStrandProfile adhesionLifecycleProfile(
            AdhesionStrandProfile captured,
            AdhesionStrandProfile current,
            boolean hasRetainedStrands) {
        return hasRetainedStrands && captured != null ? captured : current;
    }

    private static void captureAdhesionSessionContact(
            Player player, Hole hole, AdhesionStrandProfile profile,
            MudEntityGeometry.SurfacePixelSampler[] samplers) {
        hole.adhesionSupportProbeCount = 0;
        double minimumCoverage = Math.max(0.02D, profile.minimumCoverage() * 0.50D);
        for (MudBodyPart part : MudBodyPart.values()) {
            if (part == MudBodyPart.HEAD) {
                continue;
            }
            MudEntityGeometry.SurfacePixelSampler sampler = adhesionSampler(
                    player, samplers, part);
            for (MudSurface surface : MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    for (int column = 0; column < face.width(); column++) {
                        int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                        if (hole.adhesionSessionTouched[cell]) {
                            continue;
                        }
                        ClientAdhesionCoverage.Sample source = ClientAdhesionCoverage.sample(
                                player, part, surface, row, column);
                        if (source.medium() != hole.medium
                                || source.coverage() < minimumCoverage) {
                            continue;
                        }
                        Vec3 point = sampler.point(part, surface, row, column);
                        double height = point.subtract(hole.center).dot(hole.normal);
                        if (height > PIXEL * 0.20D) {
                            continue;
                        }
                        BlockPos supportPos = BlockPos.containing(
                                point.x, hole.center.y - 0.025D, point.z);
                        if (adhesionSessionSupportMatches(hole, supportPos)) {
                            hole.adhesionSessionTouched[cell] = true;
                        }
                    }
                }
            }
        }
    }

    private static boolean adhesionSessionSupportMatches(Hole hole, BlockPos pos) {
        long key = pos.asLong();
        for (int index = 0; index < hole.adhesionSupportProbeCount; index++) {
            if (hole.adhesionSupportProbeBlocks[index] == key) {
                return hole.adhesionSupportProbeMatches[index];
            }
        }
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        double localSurfaceHeight = visualSurfaceHeightAt(
                level, pos, state, medium, hole.center.x, hole.center.z);
        boolean matches = medium == hole.medium
                && Double.isFinite(localSurfaceHeight)
                && Math.abs(pos.getY() + localSurfaceHeight - hole.center.y) <= 0.085D;
        if (hole.adhesionSupportProbeCount < hole.adhesionSupportProbeBlocks.length) {
            int index = hole.adhesionSupportProbeCount++;
            hole.adhesionSupportProbeBlocks[index] = key;
            hole.adhesionSupportProbeMatches[index] = matches;
        }
        return matches;
    }

    private static boolean updateAdhesionStrandEndpoints(Player player, Hole hole,
            AdhesionStrand strand, AdhesionStrandProfile profile) {
        if (!updateAdhesionStrandBodyEndpoint(player, hole, strand, profile)) {
            return false;
        }
        if (strand.surfaceAnchored) {
            int pixelX = Mth.floor(strand.surfacePoint.x / PIXEL);
            int pixelZ = Mth.floor(strand.surfacePoint.z / PIXEL);
            SurfaceSupport support = surfaceSupportAt(
                    pixelX, pixelZ, strand.surfacePoint.y - 0.006D);
            if (support == null || support.medium() != strand.medium) {
                Vec3 recovered = nearestAdhesionSurfacePoint(hole, strand.surfacePoint);
                if (recovered == null) {
                    strand.surfaceAnchorMissTicks++;
                    // Keep the last supported surface endpoint. Only measured
                    // endpoint separation is allowed to break this strand.
                    return strand.surfacePoint != Vec3.ZERO;
                }
                strand.surfacePoint = recovered;
                strand.surfaceAnchorMissTicks = 0;
                return true;
            }
            strand.surfacePoint = new Vec3(
                    strand.surfacePoint.x, support.surfaceY() + 0.006D, strand.surfacePoint.z);
            strand.surfaceAnchorMissTicks = 0;
            return true;
        }
        Vec3 relative = strand.bodyPoint.subtract(hole.center);
        double offsetX = Math.rint(relative.dot(hole.axisX) / PIXEL) * PIXEL;
        double offsetZ = Math.rint(relative.dot(hole.axisZ) / PIXEL) * PIXEL;
        double maximumReach = Math.max(0.18D, hole.radius + PIXEL * 2.0D);
        offsetX = Mth.clamp(offsetX, -maximumReach, maximumReach);
        offsetZ = Mth.clamp(offsetZ, -maximumReach, maximumReach);
        Vec3 projected = hole.center
                .add(hole.axisX.scale(offsetX))
                .add(hole.axisZ.scale(offsetZ));
        Vec3 supported = nearestAdhesionSurfacePoint(hole, projected);
        if (supported == null) {
            return false;
        }
        strand.surfacePoint = supported;
        strand.surfaceAnchored = true;
        strand.surfaceAnchorMissTicks = 0;
        if (strand.previousSurfacePoint == Vec3.ZERO && strand.previousBodyPoint == Vec3.ZERO) {
            strand.previousSurfacePoint = strand.surfacePoint;
            strand.previousBodyPoint = strand.bodyPoint;
        }
        return true;
    }

    private static List<AdhesionAnchorCandidate> collectGeometricAnchorCandidates(
            Player player, Hole hole, AdhesionStrandProfile profile,
            MudEntityGeometry.SurfacePixelSampler[] samplers) {
        List<AdhesionAnchorCandidate> candidates = hole.adhesionCandidateScratch;
        candidates.clear();
        double highestHeight = Double.NEGATIVE_INFINITY;
        for (MudBodyPart part : MudBodyPart.values()) {
            if (part == MudBodyPart.HEAD) {
                continue;
            }
            MudEntityGeometry.SurfacePixelSampler sampler = adhesionSampler(
                    player, samplers, part);
            for (MudSurface surface : MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    for (int column = 0; column < face.width(); column++) {
                        int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                        if (!hole.adhesionSessionTouched[cell]) {
                            continue;
                        }
                        ClientAdhesionCoverage.Sample source = ClientAdhesionCoverage.sample(
                                player, part, surface, row, column);
                        if (!adhesionSessionCandidate(
                                true, source.coverage(),
                                profile.minimumCoverage(), source.medium() == hole.medium)) {
                            continue;
                        }
                        Vec3 point = sampler.point(part, surface, row, column);
                        Vec3 relative = point.subtract(hole.center);
                        double height = relative.dot(hole.normal);
                        if (height >= -PIXEL && height <= profile.spawnHeight()) {
                            highestHeight = Math.max(highestHeight, height);
                            candidates.add(new AdhesionAnchorCandidate(
                                    part, surface, row, column, cell,
                                    source.coverage(), source.visualSource(),
                                    source.armorSlot(), source.surfaceOffset(),
                                    height, Math.atan2(
                                            relative.dot(hole.axisZ), relative.dot(hole.axisX))));
                        }
                    }
                }
            }
        }
        if (!Double.isFinite(highestHeight)) {
            return List.of();
        }
        for (int index = candidates.size() - 1; index >= 0; index--) {
            if (highestHeight - candidates.get(index).height() > PIXEL * 2.0D) {
                candidates.remove(index);
            }
        }
        return candidates;
    }

    private static boolean assignGeometricAnchor(Hole hole,
            AdhesionStrand strand, int index,
            List<AdhesionAnchorCandidate> candidates) {
        long phaseHash = mix(hole.seed ^ 0x4f696c5368656574L);
        double phase = ((phaseHash >>> 11) & 4095L) / 4096.0D * Math.PI * 2.0D;
        double targetAngle = phase + Math.PI * 2.0D
                * adhesionRingSlot(index) / ADHESION_RING_SLOT_ORDER.length;
        double bestScore = Double.NEGATIVE_INFINITY;
        AdhesionAnchorCandidate best = null;
        for (AdhesionAnchorCandidate candidate : candidates) {
            if (bodyAnchorAlreadyUsed(
                    hole, strand, candidate.part(), candidate.surface(),
                    candidate.row(), candidate.column())) {
                continue;
            }
            long hash = mix(strand.seed
                    ^ candidate.cell() * 0x9e3779b97f4a7c15L);
            double variation = ((hash >>> 11) & 1023L) / 1023.0D;
            double directional = Math.cos(candidate.angle() - targetAngle);
            double score = directional * 1.72D
                    + candidate.coverage() * 0.78D
                    + variation * 0.20D;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) {
            return false;
        }
        strand.part = best.part();
        strand.surface = best.surface();
        strand.row = best.row();
        strand.column = best.column();
        strand.medium = hole.medium;
        strand.visualSource = best.visualSource();
        strand.armorSlot = best.armorSlot();
        strand.geometryAngle = best.angle();
        strand.bodySurfaceOffset = best.surfaceOffset();
        strand.bodySlideOffset = 0.0D;
        strand.geometricAnchor = true;
        return true;
    }

    private static boolean updateGeometricAdhesionEndpoints(Player player, Hole hole,
            AdhesionStrand strand, AdhesionStrandProfile profile,
            MudEntityGeometry.SurfacePixelSampler[] samplers) {
        if (!strand.geometricAnchor || strand.part == MudBodyPart.HEAD
                || !strand.hasAttachment()) {
            return false;
        }
        // Geometric tar anchors are validated once when selected. Rechecking
        // interpolated coverage every tick makes foot anchors flicker and break.
        if (!followStoredAdhesionBodyEndpoint(player, strand, samplers)) {
            return false;
        }
        Vec3 attachedPoint = strand.bodyPoint;
        double maximumSlide = Math.max(
                0.0D, profile.bodyAnchorLift() - PIXEL * 0.45D);
        double slideVariation = 0.78D + 0.44D * unit(
                mix(strand.seed ^ 0x426f6479536c6964L));
        double nextSlide = Math.min(
                maximumSlide,
                strand.bodySlideOffset + profile.bodySlideSpeed()
                        * slideVariation * (0.55D + hole.agitation * 0.45D));
        strand.bodySlideOffset = nextSlide;
        strand.bodyPoint = attachedPoint.subtract(hole.normal.scale(nextSlide));
        Vec3 bodyRadial = reject(strand.bodyPoint.subtract(hole.center), hole.normal);
        double bodyRadius = bodyRadial.length();

        long gameTick = level == null ? 0L : level.getGameTime();
        double phase = unit(mix(strand.seed ^ 0x46696c6d44726966L)) * Math.PI * 2.0D;
        double driftTime = gameTick * profile.ringDriftSpeed();
        double surfaceAngle = strand.geometryAngle
                + Math.sin(driftTime + phase) * profile.ringDriftAmount();
        double fixedVariation = signedUnit(
                mix(strand.seed ^ 0x52696e6756617269L))
                * profile.ringVariation() * 0.65D;
        double movingVariation = Math.sin(driftTime * 0.73D + phase * 1.61D)
                * profile.ringVariation() * 0.35D;
        double minimumOuterRadius = bodyRadius + profile.ringClearance();
        double radius = Math.max(
                minimumOuterRadius,
                profile.ringRadius() * (1.0D + fixedVariation + movingVariation));
        int refreshTicks = Math.max(1, profile.ringRefreshTicks());
        boolean refreshSurface = strand.surfacePoint == Vec3.ZERO
                || gameTick - strand.geometrySurfaceTick >= refreshTicks;
        if (refreshSurface) {
            Vec3 supported = outerAdhesionSurfacePoint(
                    hole, surfaceAngle, radius, minimumOuterRadius);
            if (supported == null) {
                int oldPixelX = Mth.floor(strand.surfacePoint.x / PIXEL);
                int oldPixelZ = Mth.floor(strand.surfacePoint.z / PIXEL);
                supported = adhesionSurfacePointAt(hole, oldPixelX, oldPixelZ);
                if (supported == null) {
                    supported = nearestAdhesionSurfacePoint(hole, strand.surfacePoint);
                }
            }
            if (supported == null) {
                strand.surfaceAnchorMissTicks++;
                if (strand.surfacePoint == Vec3.ZERO) {
                    return false;
                }
            } else {
                strand.surfacePoint = supported;
                strand.surfaceAnchored = true;
                strand.surfaceAnchorMissTicks = 0;
                strand.geometrySurfaceTick = gameTick;
            }
        }
        return true;
    }

    private static Vec3 outerAdhesionSurfacePoint(Hole hole, double angle,
            double targetRadius, double minimumRadius) {
        Vec3 direction = hole.axisX.scale(Math.cos(angle))
                .add(hole.axisZ.scale(Math.sin(angle)));
        double step = PIXEL * 0.5D;
        int steps = Mth.clamp(
                (int) Math.ceil(Math.max(0.0D, targetRadius - minimumRadius) / step),
                0, 64);
        for (int index = 0; index <= steps; index++) {
            double radius = Math.max(minimumRadius, targetRadius - index * step);
            Vec3 projected = hole.center.add(direction.scale(radius));
            Vec3 supported = adhesionSurfacePointAt(
                    hole, Mth.floor(projected.x / PIXEL), Mth.floor(projected.z / PIXEL));
            if (supported != null) {
                return supported;
            }
        }
        return null;
    }

    private static Vec3 nearestAdhesionSurfacePoint(Hole hole, Vec3 projected) {
        int targetPixelX = Mth.floor(projected.x / PIXEL);
        int targetPixelZ = Mth.floor(projected.z / PIXEL);
        Vec3 direct = adhesionSurfacePointAt(hole, targetPixelX, targetPixelZ);
        if (direct != null) {
            return direct;
        }
        for (int radius = 1; radius <= ADHESION_SURFACE_SEARCH_PIXELS; radius++) {
            Vec3 best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
                        continue;
                    }
                    Vec3 candidate = adhesionSurfacePointAt(
                            hole, targetPixelX + offsetX, targetPixelZ + offsetZ);
                    if (candidate == null) {
                        continue;
                    }
                    double distance = horizontalDistance(candidate, projected);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static Vec3 adhesionSurfacePointAt(Hole hole, int pixelX, int pixelZ) {
        SurfaceSupport support = surfaceSupportAt(pixelX, pixelZ, hole.center.y);
        if (support == null || support.medium() != hole.medium) {
            return null;
        }
        return new Vec3(
                (pixelX + 0.5D) * PIXEL,
                support.surfaceY() + 0.006D,
                (pixelZ + 0.5D) * PIXEL);
    }

    private static boolean chooseAdhesionBodyAnchor(Player player, Hole hole,
            AdhesionStrand strand, AdhesionStrandProfile profile, int desiredCount,
            AdhesionAnchorRange anchorRange) {
        if (anchorRange.empty()) {
            return false;
        }
        double minimumCoverage = profile.minimumCoverage();
        double bestScore = Double.NEGATIVE_INFINITY;
        long phaseHash = mix(hole.seed ^ 0x4f696c5368656574L);
        double phase = ((phaseHash >>> 11) & 4095L) / 4096.0D * Math.PI * 2.0D;
        double targetAngle = phase + Math.PI * 2.0D
                * strand.index / Math.max(1, desiredCount);
        double targetHeight = adhesionAnchorTargetHeight(
                strand.index, desiredCount,
                anchorRange.minimumHeight(), anchorRange.maximumHeight(), strand.seed);
        double heightRange = Math.max(PIXEL,
                anchorRange.maximumHeight() - anchorRange.minimumHeight());
        for (MudBodyPart part : MudBodyPart.values()) {
            for (MudSurface surface : MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    for (int column = 0; column < face.width(); column++) {
                        ClientAdhesionCoverage.Sample source = ClientAdhesionCoverage.sample(
                                player, part, surface, row, column);
                        float coverage = source.coverage();
                        if (coverage < minimumCoverage
                                || source.medium() != hole.medium
                                || bodyAnchorAlreadyUsed(
                                        hole, strand, part, surface, row, column)) {
                            continue;
                        }
                        Vec3 point = MudEntityGeometry.surfacePixelPoint(
                                player, part, surface, row, column);
                        Vec3 relative = point.subtract(hole.center);
                        double height = relative.dot(hole.normal);
                        if (height < -PIXEL || height > profile.spawnHeight()) {
                            continue;
                        }
                        double angle = Math.atan2(
                                relative.dot(hole.axisZ), relative.dot(hole.axisX));
                        double directional = Math.cos(angle - targetAngle);
                        int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                        long hash = mix(strand.seed ^ cell * 0x9e3779b97f4a7c15L);
                        double variation = ((hash >>> 11) & 1023L) / 1023.0D;
                        double heightMatch = Math.abs(height - targetHeight) / heightRange;
                        double score = directional * 1.58D
                                + variation * 0.26D
                                + coverage * 0.72D
                                - heightMatch * 1.12D;
                        if (score > bestScore) {
                            bestScore = score;
                            strand.part = part;
                            strand.surface = surface;
                            strand.row = row;
                            strand.column = column;
                            strand.medium = hole.medium;
                            strand.visualSource = source.visualSource();
                            strand.armorSlot = source.armorSlot();
                        }
                    }
                }
            }
        }
        return strand.hasAttachment();
    }

    private static AdhesionAnchorRange adhesionAnchorRange(
            Player player, Hole hole, AdhesionStrandProfile profile) {
        double minimumHeight = Double.POSITIVE_INFINITY;
        double maximumHeight = Double.NEGATIVE_INFINITY;
        for (MudBodyPart part : MudBodyPart.values()) {
            for (MudSurface surface : MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    for (int column = 0; column < face.width(); column++) {
                        ClientAdhesionCoverage.Sample source = ClientAdhesionCoverage.sample(
                                player, part, surface, row, column);
                        if (source.coverage() < profile.minimumCoverage()
                                || source.medium() != hole.medium) {
                            continue;
                        }
                        Vec3 point = MudEntityGeometry.surfacePixelPoint(
                                player, part, surface, row, column);
                        double height = point.subtract(hole.center).dot(hole.normal);
                        if (height < -PIXEL || height > profile.spawnHeight()) {
                            continue;
                        }
                        minimumHeight = Math.min(minimumHeight, height);
                        maximumHeight = Math.max(maximumHeight, height);
                    }
                }
            }
        }
        return new AdhesionAnchorRange(minimumHeight, maximumHeight);
    }

    static double adhesionAnchorTargetHeight(
            int strandIndex, int strandCount, double minimumHeight, double maximumHeight, long seed) {
        if (!Double.isFinite(minimumHeight) || !Double.isFinite(maximumHeight)
                || maximumHeight <= minimumHeight) {
            return minimumHeight;
        }
        double distributed = (strandIndex + 0.5D) * 0.6180339887498949D;
        distributed -= Math.floor(distributed);
        double band = 0.54D + distributed * 0.38D;
        double jitter = ((((mix(seed ^ 0x416e63686f724869L) >>> 11) & 1023L) / 1023.0D)
                - 0.5D) * 0.08D;
        double ratio = Mth.clamp(band + jitter, 0.50D, 0.96D);
        return Mth.lerp(ratio, minimumHeight, maximumHeight);
    }

    private static boolean bodyAnchorAlreadyUsed(Hole hole, AdhesionStrand candidate,
            MudBodyPart part, MudSurface surface, int row, int column) {
        for (AdhesionStrand strand : hole.adhesionStrands) {
            if (strand == candidate || !strand.active || !strand.hasAttachment()) {
                continue;
            }
            if (strand.part == part && strand.surface == surface
                    && strand.row == row && strand.column == column) {
                return true;
            }
        }
        return false;
    }

    private static boolean updateAdhesionStrandBodyEndpoint(Player player, Hole hole,
            AdhesionStrand strand, AdhesionStrandProfile profile) {
        if (!strand.hasAttachment()) {
            return false;
        }
        ClientAdhesionCoverage.Sample source = ClientAdhesionCoverage.sample(
                player, strand.part, strand.surface, strand.row, strand.column);
        double recoveryCoverage = Math.max(0.02D, profile.minimumCoverage() * 0.58D);
        boolean exact = source.medium() == strand.medium
                && source.armorSlot() == strand.armorSlot
                && source.coverage() >= recoveryCoverage;
        if (!exact) {
            RecoveredAdhesionAnchor recovered = recoverAdhesionBodyAnchor(
                    player, hole, strand, profile, recoveryCoverage);
            if (recovered != null) {
                strand.row = recovered.row();
                strand.column = recovered.column();
                strand.armorSlot = recovered.source().armorSlot();
                source = recovered.source();
                exact = true;
            }
        }
        if (exact) {
            strand.bodyAnchorMissTicks = 0;
            strand.bodySurfaceOffset = source.surfaceOffset();
            updateStoredAdhesionBodyPoint(player, strand);
            return true;
        }
        strand.bodyAnchorMissTicks++;
        if (strand.bodyPoint == Vec3.ZERO) {
            return false;
        }
        followStoredAdhesionBodyEndpoint(player, strand);
        // Coverage can miss briefly while an animated model is between poses.
        // The stored pixel still follows the model and remains length-checked.
        return true;
    }

    private static RecoveredAdhesionAnchor recoverAdhesionBodyAnchor(
            Player player, Hole hole, AdhesionStrand strand,
            AdhesionStrandProfile profile, double minimumCoverage) {
        int search = Mth.clamp(profile.anchorSearchPixels(), 0, 4);
        if (search <= 0) {
            return null;
        }
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(strand.part, strand.surface);
        Vec3 originalPoint = MudEntityGeometry.surfacePixelPoint(
                player, strand.part, strand.surface, strand.row, strand.column);
        double originalHeight = originalPoint.subtract(hole.center).dot(hole.normal);
        RecoveredAdhesionAnchor best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int rowOffset = -search; rowOffset <= search; rowOffset++) {
            for (int columnOffset = -search; columnOffset <= search; columnOffset++) {
                int distance = Math.max(Math.abs(rowOffset), Math.abs(columnOffset));
                if (distance > search) {
                    continue;
                }
                int row = strand.row + rowOffset;
                int column = strand.column + columnOffset;
                if (row < 0 || row >= face.height() || column < 0 || column >= face.width()
                        || bodyAnchorAlreadyUsed(
                                hole, strand, strand.part, strand.surface, row, column)) {
                    continue;
                }
                ClientAdhesionCoverage.Sample candidate = ClientAdhesionCoverage.sample(
                        player, strand.part, strand.surface, row, column);
                if (candidate.medium() != strand.medium
                        || candidate.coverage() < minimumCoverage) {
                    continue;
                }
                Vec3 candidatePoint = MudEntityGeometry.surfacePixelPoint(
                        player, strand.part, strand.surface, row, column);
                double candidateHeight = candidatePoint.subtract(hole.center).dot(hole.normal);
                if (candidateHeight > originalHeight + PIXEL * 0.30D) {
                    continue;
                }
                double slotMatch = candidate.armorSlot() == strand.armorSlot ? 0.55D : 0.0D;
                double score = slotMatch + candidate.coverage() * 0.55D - distance * 0.28D
                        - Math.abs(rowOffset) * 0.03D;
                if (score > bestScore) {
                    bestScore = score;
                    best = new RecoveredAdhesionAnchor(row, column, candidate);
                }
            }
        }
        return best;
    }

    private static boolean followStoredAdhesionBodyEndpoint(
            Player player, AdhesionStrand strand) {
        return followStoredAdhesionBodyEndpoint(player, strand, null);
    }

    private static boolean followStoredAdhesionBodyEndpoint(
            Player player, AdhesionStrand strand,
            MudEntityGeometry.SurfacePixelSampler[] samplers) {
        if (!strand.hasAttachment()) {
            return false;
        }
        updateStoredAdhesionBodyPoint(player, strand, samplers);
        return true;
    }

    private static void updateStoredAdhesionBodyPoint(Player player, AdhesionStrand strand) {
        updateStoredAdhesionBodyPoint(player, strand, null);
    }

    private static void updateStoredAdhesionBodyPoint(
            Player player, AdhesionStrand strand,
            MudEntityGeometry.SurfacePixelSampler[] samplers) {
        MudEntityGeometry.SurfacePixelSampler sampler = adhesionSampler(
                player, samplers, strand.part);
        Vec3 point = sampler.point(
                strand.part, strand.surface, strand.row, strand.column);
        Vec3 side = sampler.side();
        Vec3 up = sampler.up();
        Vec3 forward = sampler.forward();
        double pixelScale = player.getBbHeight() / MudEntityGeometry.PLAYER_MODEL_HEIGHT_PIXELS;
        Vec3 tangentU;
        Vec3 tangentV;
        if (strand.surface == MudSurface.FRONT || strand.surface == MudSurface.BACK) {
            tangentU = side;
            tangentV = up;
        } else if (strand.surface == MudSurface.LEFT || strand.surface == MudSurface.RIGHT) {
            tangentU = forward;
            tangentV = up;
        } else {
            tangentU = side;
            tangentV = forward;
        }
        point = point
                .add(tangentU.scale(strand.pixelOffsetU * pixelScale))
                .add(tangentV.scale(strand.pixelOffsetV * pixelScale));
        Vec3 normal = sampler.outwardNormal(strand.surface);
        strand.bodyPoint = point.add(normal.scale(
                strand.bodySurfaceOffset + ADHESION_BODY_SURFACE_GAP));
    }

    private static MudEntityGeometry.SurfacePixelSampler adhesionSampler(
            Player player, MudEntityGeometry.SurfacePixelSampler[] samplers,
            MudBodyPart part) {
        if (samplers == null) {
            return MudEntityGeometry.surfacePixelSampler(player, part);
        }
        int index = part.ordinal();
        MudEntityGeometry.SurfacePixelSampler sampler = samplers[index];
        if (sampler == null) {
            sampler = MudEntityGeometry.surfacePixelSampler(player, part);
            samplers[index] = sampler;
        }
        return sampler;
    }

    private static void stampSurface(Hole hole, MudEntityGeometry.PlaneSlice slice) {
        if (hole.visibility <= 0.003D || slice.empty()) {
            hole.hasStampCenter = false;
            hole.previousSlice = null;
            return;
        }
        Map<com.fish.mirebound.mud.MudBodyPart, MudEntityGeometry.SlicePolygon> previous =
                polygonsByPart(hole.previousSlice, hole.previousPolygonScratch);
        double trailScale = value(
                hole.profilePos, hole.medium, MudPhysicsParameter.SURFACE_MOVEMENT_TRAIL);
        double maximumSweep = Mth.lerp(trailScale, 0.72D, 1.45D);
        for (MudEntityGeometry.SlicePolygon polygon : slice.polygons()) {
            List<Vec3> vertices = polygon.vertices();
            MudEntityGeometry.SlicePolygon old = previous.get(polygon.part());
            if (old != null
                    && hole.hasStampCenter
                    && Math.abs(hole.previousSlice.surfaceY() - slice.surfaceY()) <= 0.085D
                    && horizontalDistance(hole.lastStampCenter, hole.center) <= maximumSweep) {
                List<Vec3> swept = new ArrayList<>(
                        old.vertices().size() + polygon.vertices().size());
                swept.addAll(old.vertices());
                swept.addAll(polygon.vertices());
                vertices = MudEntityGeometry.convexHull(swept);
            }
            stampPolygon(hole, vertices, slice.surfaceY());
        }
        hole.lastStampCenter = hole.center;
        hole.hasStampCenter = true;
        hole.previousSlice = slice;
    }

    private static Map<com.fish.mirebound.mud.MudBodyPart, MudEntityGeometry.SlicePolygon>
            polygonsByPart(MudEntityGeometry.PlaneSlice slice,
                    Map<com.fish.mirebound.mud.MudBodyPart,
                            MudEntityGeometry.SlicePolygon> result) {
        result.clear();
        if (slice != null) {
            for (MudEntityGeometry.SlicePolygon polygon : slice.polygons()) {
                result.put(polygon.part(), polygon);
            }
        }
        return result;
    }

    private static void stampPolygon(Hole hole, List<Vec3> polygon, double surfaceY) {
        if (polygon.size() < 3) {
            return;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 point : polygon) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
        }
        int minPixelX = Mth.floor(minX / PIXEL) - 1;
        int maxPixelX = Mth.floor(maxX / PIXEL) + 1;
        int minPixelZ = Mth.floor(minZ / PIXEL) - 1;
        int maxPixelZ = Mth.floor(maxZ / PIXEL) + 1;
        hole.maximumRadiusPixels = Math.max(
                hole.maximumRadiusPixels,
                Math.max(maxPixelX - minPixelX, maxPixelZ - minPixelZ) / 2 + 1);
        for (int pixelX = minPixelX; pixelX <= maxPixelX; pixelX++) {
            for (int pixelZ = minPixelZ; pixelZ <= maxPixelZ; pixelZ++) {
                double worldX = (pixelX + 0.5D) * PIXEL;
                double worldZ = (pixelZ + 0.5D) * PIXEL;
                if (!MudEntityGeometry.containsXZ(polygon, worldX, worldZ)) {
                    continue;
                }
                long hash = mix(hole.seed
                        ^ pixelX * 0x9e3779b97f4a7c15L
                        ^ pixelZ * 0xc2b2ae3d27d4eb4fL);
                SurfaceCell cell = ensureSurfaceCell(
                        hole, pixelX, pixelZ, surfaceY, hole.medium, hash);
                if (cell == null) {
                    continue;
                }
                refreshDepressionCell(hole, cell, hole.visibility);
            }
        }
    }

    private static boolean stampImpactDepression(Hole hole, PendingImpact impact) {
        double configured = value(
                impact.profilePos, impact.medium,
                MudPhysicsParameter.SURFACE_IMPACT_PILE_EXPANSION_PIXELS);
        double impactRadius = MudSurfaceHeightField.impactRadiusPixels(
                IMPACT_CONTACT_RADIUS_PIXELS, configured,
                impact.impactStrength, impact.volumeFraction);
        if (impactRadius <= 0.0D) {
            return false;
        }
        hole.maximumRadiusPixels = Math.max(
                hole.maximumRadiusPixels,
                (int) Math.ceil(impactRadius));
        int centerPixelX = Mth.floor(impact.origin.x / PIXEL);
        int centerPixelZ = Mth.floor(impact.origin.z / PIXEL);
        int searchRadius = Mth.clamp((int) Math.ceil(impactRadius + 1.0D), 1, 18);
        boolean stamped = false;
        for (int offsetX = -searchRadius; offsetX <= searchRadius; offsetX++) {
            for (int offsetZ = -searchRadius; offsetZ <= searchRadius; offsetZ++) {
                int pixelX = centerPixelX + offsetX;
                int pixelZ = centerPixelZ + offsetZ;
                double worldX = (pixelX + 0.5D) * PIXEL;
                double worldZ = (pixelZ + 0.5D) * PIXEL;
                long hash = mix(hole.seed
                        ^ pixelX * 0x9e3779b97f4a7c15L
                        ^ pixelZ * 0xc2b2ae3d27d4eb4fL);
                double depression = MudSurfaceHeightField.impactDepression(
                        (worldX - impact.origin.x) / PIXEL,
                        (worldZ - impact.origin.z) / PIXEL,
                        impactRadius,
                        0.0D,
                        1.0D);
                if (depression <= 0.003D) {
                    continue;
                }
                SurfaceCell cell = ensureSurfaceCell(
                        hole,
                        pixelX,
                        pixelZ,
                        impact.origin.y,
                        impact.medium,
                        hash);
                if (cell == null) {
                    continue;
                }
                refreshDepressionCell(hole, cell, depression);
                stamped = true;
            }
        }
        return stamped;
    }

    private static void stampEruptionDepression(Hole hole, double radiusPixels) {
        double radius = Mth.clamp(radiusPixels, 1.0D, 18.0D);
        hole.maximumRadiusPixels = Math.max(
                hole.maximumRadiusPixels, (int) Math.ceil(radius));
        int centerPixelX = Mth.floor(hole.center.x / PIXEL);
        int centerPixelZ = Mth.floor(hole.center.z / PIXEL);
        int searchRadius = Mth.clamp((int) Math.ceil(radius + 1.0D), 1, 19);
        for (int offsetX = -searchRadius; offsetX <= searchRadius; offsetX++) {
            for (int offsetZ = -searchRadius; offsetZ <= searchRadius; offsetZ++) {
                int pixelX = centerPixelX + offsetX;
                int pixelZ = centerPixelZ + offsetZ;
                long hash = mix(hole.seed
                        ^ pixelX * 0x9e3779b97f4a7c15L
                        ^ pixelZ * 0xc2b2ae3d27d4eb4fL);
                double jitter = (((hash >>> 25) & 255L) / 255.0D - 0.5D) * 0.62D;
                double depression = MudSurfaceHeightField.impactDepression(
                        offsetX, offsetZ, radius, jitter, 0.94D);
                if (depression <= 0.003D) {
                    continue;
                }
                SurfaceCell cell = ensureSurfaceCell(
                        hole, pixelX, pixelZ, hole.center.y, hole.medium, hash);
                if (cell != null) {
                    refreshDepressionCell(hole, cell, depression);
                }
            }
        }
    }

    private static void transferSurfaceCells(Hole source, Hole target) {
        for (SurfaceCell incoming : source.cells.values()) {
            long key = cellKey(incoming.pixelX, incoming.pixelZ);
            SurfaceCell existing = target.cells.get(key);
            if (existing == null) {
                if (target.cells.size() >= MudSurfaceClientSettings.maxSurfaceCells()) {
                    break;
                }
                putSurfaceCell(target, key, incoming);
                target.includeSurfaceCell(incoming);
                target.rimDirty.add(key);
                continue;
            }
            if (incoming.depression > existing.depression) {
                existing.depression = incoming.depression;
                existing.previousDepression = Math.max(
                        existing.previousDepression, incoming.previousDepression);
                existing.medium = incoming.medium;
            }
            existing.pileHeight = Math.max(existing.pileHeight, incoming.pileHeight);
            existing.previousPileHeight = Math.max(
                    existing.previousPileHeight, incoming.previousPileHeight);
            existing.closureProgress = Math.min(
                    existing.closureProgress, incoming.closureProgress);
            target.rimDirty.add(key);
        }
        target.maximumRadiusPixels = Math.max(
                target.maximumRadiusPixels, source.maximumRadiusPixels);
    }

    static void mergeImpactSurfaceCells(Hole impact, Hole pressure) {
        for (SurfaceCell incoming : impact.cells.values()) {
            long key = cellKey(incoming.pixelX, incoming.pixelZ);
            SurfaceCell existing = pressure.cells.get(key);
            if (existing == null) {
                if (pressure.cells.size() >= MudSurfaceClientSettings.maxSurfaceCells()) {
                    break;
                }
                putSurfaceCell(pressure, key, incoming);
                pressure.includeSurfaceCell(incoming);
                pressure.rimDirty.add(key);
                continue;
            }

            if (incoming.depression > existing.depression) {
                existing.depression = incoming.depression;
                existing.previousDepression = Math.max(
                        existing.previousDepression, incoming.previousDepression);
                existing.medium = incoming.medium;
            }
            existing.refreshed |= incoming.refreshed;
            existing.closureProgress = Math.min(
                    existing.closureProgress, incoming.closureProgress);
            if (incoming.depression > 0.003D) {
                existing.previousPileHeight = 0.0D;
                existing.pileHeight = 0.0D;
                existing.targetPileHeight = 0.0D;
                existing.pileWeight = 0.0D;
            }
            pressure.rimDirty.add(key);
        }
        pressure.maximumRadiusPixels = Math.max(
                pressure.maximumRadiusPixels, impact.maximumRadiusPixels);
    }

    private static void refreshDepressionCell(
            Hole hole, SurfaceCell cell, double strength) {
        boolean topologyChanged = cell.depression <= 0.003D;
        boolean reopening = cell.closureProgress > 0.001D;
        double previousStrength = cell.depression;
        cell.refreshed = true;
        cell.depression = Math.max(cell.depression, Mth.clamp(strength, 0.0D, 1.0D));
        cell.closureProgress = 0.0D;
        cell.closureMask = 0;
        cell.closureRimBucket = 0;
        cell.pileHeight *= 0.38D;
        if (topologyChanged) {
            ensureRimHalo(hole, cell);
        }
        if (topologyChanged || reopening
                || cell.depression - previousStrength > 0.045D) {
            markRimDirty(hole, cell);
        }
    }

    private static void ensureRimHalo(Hole hole, SurfaceCell source) {
        double rimWidth = baseRimWidth(hole);
        int searchRadius = rimSearchRadius(rimWidth);
        for (int offsetX = -searchRadius; offsetX <= searchRadius; offsetX++) {
            for (int offsetZ = -searchRadius; offsetZ <= searchRadius; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0
                        || Math.sqrt(offsetX * offsetX + offsetZ * offsetZ) > rimWidth + 1.0D) {
                    continue;
                }
                int pixelX = source.pixelX + offsetX;
                int pixelZ = source.pixelZ + offsetZ;
                long hash = mix(source.seed
                        ^ offsetX * 0x632be59bd9b4e019L
                        ^ offsetZ * 0x94d049bb133111ebL);
                SurfaceCell neighbor = ensureSurfaceCell(
                        hole, pixelX, pixelZ, source.surfaceY, source.medium, hash);
                if (neighbor != null) {
                    hole.rimDirty.add(cellKey(pixelX, pixelZ));
                }
            }
        }
    }

    private static void markRimDirty(Hole hole, SurfaceCell source) {
        double rimWidth = baseRimWidth(hole);
        int searchRadius = rimSearchRadius(rimWidth);
        for (int offsetX = -searchRadius; offsetX <= searchRadius; offsetX++) {
            for (int offsetZ = -searchRadius; offsetZ <= searchRadius; offsetZ++) {
                long key = cellKey(source.pixelX + offsetX, source.pixelZ + offsetZ);
                if (hole.cells.containsKey(key)) {
                    hole.rimDirty.add(key);
                }
            }
        }
    }

    private static void updateSurfaceField(Hole hole, int elapsedTicks) {
        pruneUnsupportedSurfaceCells(hole);
        double closeTicks = effectiveCloseTicks(hole.profilePos, hole.medium);
        double layerRate = Math.max(1.0D, hole.maximumRadiusPixels)
                / closeTicks * Math.max(1, elapsedTicks);
        for (SurfaceCell cell : hole.cells.values()) {
            if (cell.depression <= 0.003D) {
                continue;
            }
            if (cell.refreshed) {
                cell.closureProgress = 0.0D;
                cell.closureMask = 0;
            } else if (isDepressionBoundary(hole, cell)) {
                int closureMask = depressionBoundaryMask(hole, cell);
                double noise = 0.78D + ((cell.seed >>> 23) & 255L) / 255.0D * 0.44D;
                cell.closureProgress = Math.min(
                        1.0D,
                        cell.closureProgress + layerRate * noise);
                cell.closureMask = closureMask;
                int rimBucket = Math.min(4, (int) Math.floor(cell.closureProgress * 4.0D));
                if (rimBucket != cell.closureRimBucket) {
                    cell.closureRimBucket = rimBucket;
                    markRimDirty(hole, cell);
                }
                if (cell.closureProgress >= 1.0D) {
                    cell.depression = 0.0D;
                    cell.closureProgress = 1.0D;
                    cell.closureMask = 0;
                    cell.closureRimBucket = 4;
                    markRimDirty(hole, cell);
                }
            } else {
                cell.closureProgress = 0.0D;
                cell.closureMask = 0;
            }
        }

        recomputeDirtyRimWeights(hole);
        double depressedArea = 0.0D;
        double totalWeight = 0.0D;
        double maximumWeight = 0.0D;
        for (SurfaceCell cell : hole.cells.values()) {
            depressedArea += effectiveDepression(cell);
            totalWeight += cell.pileWeight;
            maximumWeight = Math.max(maximumWeight, cell.pileWeight);
        }
        double displacement = value(
                hole.profilePos, hole.medium, MudPhysicsParameter.SURFACE_DISPLACEMENT_PIXELS);
        double displacedVolume = depressedArea * displacement
                * (0.82D + hole.agitation * 0.18D);
        if (hole.fleshTemplateEnabled && level != null) {
            TenderFleshProfile fleshProfile = MudMediumRuntime.tenderFleshProfile(
                    level, hole.profilePos);
            displacedVolume *= TenderFleshMechanics.surfacePulse(
                    fleshProfile, level.getGameTime());
        }
        double maximumHeight = value(
                hole.profilePos, hole.medium, MudPhysicsParameter.SURFACE_RIM_HEIGHT_PIXELS);
        double unitHeight = MudSurfaceHeightField.normalizedPileHeight(
                displacedVolume,
                totalWeight,
                maximumWeight,
                maximumHeight);
        double response = value(
                hole.profilePos, hole.medium, MudPhysicsParameter.SURFACE_HEIGHT_RESPONSE);
        double settleResponse = Math.min(
                response * 0.62D,
                Math.max(0.015D, 4.0D / closeTicks));
        double effectiveResponse = tickResponse(response, elapsedTicks);
        double effectiveSettleResponse = tickResponse(settleResponse, elapsedTicks);

        Iterator<SurfaceCell> iterator = hole.cells.values().iterator();
        while (iterator.hasNext()) {
            SurfaceCell cell = iterator.next();
            cell.targetPileHeight = cell.depression > 0.003D
                    ? 0.0D
                    : cell.pileWeight * unitHeight * PIXEL;
            double rate = cell.targetPileHeight >= cell.pileHeight
                    ? effectiveResponse
                    : effectiveSettleResponse;
            cell.pileHeight += (cell.targetPileHeight - cell.pileHeight) * rate;
            if (cell.depression <= 0.003D
                    && cell.pileHeight <= SURFACE_CELL_VISUAL_HEIGHT_EPSILON
                    && cell.targetPileHeight <= SURFACE_CELL_VISUAL_HEIGHT_EPSILON
                    && !hole.rimDirty.contains(cellKey(cell.pixelX, cell.pixelZ))) {
                cell.pileHeight = 0.0D;
                cell.previousPileHeight = 0.0D;
                iterator.remove();
                retainedSurfaceCells--;
            }
        }
        if (hole.cells.isEmpty()) {
            hole.resetSurfaceBounds();
        }
    }

    private static double tickResponse(double response, int elapsedTicks) {
        double clamped = Mth.clamp(response, 0.0D, 1.0D);
        return 1.0D - Math.pow(1.0D - clamped, Math.max(1, elapsedTicks));
    }

    private static void recomputeDirtyRimWeights(Hole hole) {
        if (hole.rimDirty.isEmpty()) {
            return;
        }
        double rimWidth = baseRimWidth(hole);
        int searchRadius = rimSearchRadius(rimWidth);
        for (long key : hole.rimDirty) {
            int pixelX = (int) (key >> 32);
            int pixelZ = (int) key;
            SurfaceCell target = hole.cells.get(key);
            if (target == null || target.depression > 0.003D) {
                if (target != null) {
                    target.pileWeight = 0.0D;
                }
                continue;
            }
            double weightSum = 0.0D;
            for (int offsetX = -searchRadius; offsetX <= searchRadius; offsetX++) {
                for (int offsetZ = -searchRadius; offsetZ <= searchRadius; offsetZ++) {
                    if (offsetX == 0 && offsetZ == 0) {
                        continue;
                    }
                    SurfaceCell source = hole.cells.get(
                            cellKey(pixelX + offsetX, pixelZ + offsetZ));
                    if (source == null || effectiveDepression(source) <= 0.003D
                            || !sameContinuousSurface(source, target)) {
                        continue;
                    }
                    double distancePixels = Math.sqrt(
                            offsetX * offsetX + offsetZ * offsetZ);
                    double weight = MudSurfaceHeightField.rimWeight(distancePixels, rimWidth);
                    if (weight <= 0.0D) {
                        continue;
                    }
                    long hash = mix(source.seed
                            ^ offsetX * 0x632be59bd9b4e019L
                            ^ offsetZ * 0x94d049bb133111ebL);
                    double variation = 0.86D
                            + ((hash >>> 29) & 255L) / 255.0D * 0.28D;
                    weightSum += weight * variation * effectiveDepression(source);
                }
            }
            target.pileWeight = weightSum;
        }
        hole.rimDirty.clear();
    }

    private static boolean isDepressionBoundary(Hole hole, SurfaceCell cell) {
        return depressionBoundaryMask(hole, cell) != 0;
    }

    private static int depressionBoundaryMask(Hole hole, SurfaceCell cell) {
        int mask = 0;
        if (!connectedDepression(hole, cell, cell.pixelX - 1, cell.pixelZ)) {
            mask |= 1;
        }
        if (!connectedDepression(hole, cell, cell.pixelX + 1, cell.pixelZ)) {
            mask |= 2;
        }
        if (!connectedDepression(hole, cell, cell.pixelX, cell.pixelZ - 1)) {
            mask |= 4;
        }
        if (!connectedDepression(hole, cell, cell.pixelX, cell.pixelZ + 1)) {
            mask |= 8;
        }
        return mask;
    }

    private static boolean connectedDepression(Hole hole, SurfaceCell origin,
            int pixelX, int pixelZ) {
        SurfaceCell neighbor = hole.cells.get(cellKey(pixelX, pixelZ));
        return neighbor != null
                && effectiveDepression(neighbor) > 0.003D
                && sameContinuousSurface(origin, neighbor);
    }

    static boolean sameContinuousSurface(SurfaceCell first, SurfaceCell second) {
        if (first == null || second == null) {
            return false;
        }
        MudRenderedSurfaceGeometry.SurfaceHit firstHit = first.renderedHit;
        MudRenderedSurfaceGeometry.SurfaceHit secondHit = second.renderedHit;
        if (firstHit == null || secondHit == null) {
            return firstHit == secondHit
                    && Math.abs(first.surfaceY - second.surfaceY) <= 0.085D;
        }
        if (firstHit.normal().dot(secondHit.normal()) < 0.9985D) {
            return false;
        }
        double deltaX = (second.pixelX - first.pixelX) * PIXEL;
        double deltaZ = (second.pixelZ - first.pixelZ) * PIXEL;
        double expectedY = first.surfaceY
                + firstHit.axisX().y * deltaX
                + firstHit.axisZ().y * deltaZ;
        return Math.abs(second.surfaceY - expectedY) <= PIXEL * 0.24D;
    }

    private static double effectiveDepression(SurfaceCell cell) {
        return cell.depression * (1.0D - Mth.clamp(cell.closureProgress, 0.0D, 1.0D));
    }

    private static SurfaceCell ensureSurfaceCell(Hole hole, int pixelX, int pixelZ,
            double surfaceY, SinkingMedium medium, long seed) {
        SurfaceSupport support = surfaceSupportAt(hole, pixelX, pixelZ, surfaceY);
        if (support == null) {
            return null;
        }
        surfaceY = support.surfaceY();
        long key = cellKey(pixelX, pixelZ);
        SurfaceCell existing = hole.cells.get(key);
        if (existing != null
                && existing.supportBlock == support.pos().asLong()
                && existing.supportMedium == support.medium()
                && Math.abs(existing.surfaceY - surfaceY) <= PIXEL * 0.25D
                && sameRenderedSurface(
                        existing.renderedHit, existing.renderedPatch,
                        support.renderedHit(), support.renderedPatch())) {
            return existing;
        }
        if (existing != null) {
            removeSurfaceCell(hole, key, existing);
        }
        long surfaceBlock = support.pos().asLong();
        if (!hole.allowsSurface(surfaceBlock)) {
            return null;
        }
        int maximum = MudSurfaceClientSettings.maxSurfaceCells();
        if (hole.cells.size() >= maximum) {
            pruneSettledSurfaceCells(hole, maximum);
        }
        int softLimit = MudSurfaceCellBudget.globalSoftLimit(
                maximum, MudSurfaceClientSettings.maxHoles());
        int hardLimit = MudSurfaceCellBudget.globalHardLimit(softLimit, maximum);
        Minecraft minecraft = Minecraft.getInstance();
        boolean localPlayer = minecraft.player != null
                && hole.entityId == minecraft.player.getId();
        if (!MudSurfaceCellBudget.canAllocateSurfaceCell(
                localPlayer, hole.cells.size(), maximum,
                retainedSurfaceCells, softLimit, hardLimit)) {
            return null;
        }
        SurfaceCell cell = new SurfaceCell(
                pixelX,
                pixelZ,
                surfaceY,
                blendedSurfaceMedium(pixelX, pixelZ, surfaceY, seed, support.medium()),
                surfaceBlock,
                 support.medium(),
                 seed,
                 support.renderedHit(),
                 support.renderedPatch());
        cell.packedLight = exposedSurfaceLight(
                level,
                new Vec3(
                        (pixelX + 0.5D) * PIXEL,
                        surfaceY,
                        (pixelZ + 0.5D) * PIXEL),
                support.renderedHit() == null
                        ? new Vec3(0.0D, 1.0D, 0.0D)
                        : support.renderedHit().normal());
        putSurfaceCell(hole, key, cell);
        hole.includeSurfaceCell(cell);
        return cell;
    }

    static int retainedSurfaceCellCount() {
        return retainedSurfaceCells;
    }

    static long surfaceSupportKey(SurfaceCell cell) {
        long height = Double.doubleToLongBits(cell.surfaceY);
        long pixel = cellKey(cell.pixelX, cell.pixelZ);
        return mix(cell.supportBlock
                ^ Long.rotateLeft(height, 17)
                ^ Long.rotateLeft(pixel, 39));
    }

    static int exposedSurfaceLight(ClientLevel clientLevel, Vec3 point, Vec3 normal) {
        return LevelRenderer.getLightColor(
                clientLevel, exposedSurfaceLightPosition(point, normal));
    }

    static BlockPos exposedSurfaceLightPosition(Vec3 point, Vec3 normal) {
        Vec3 outward = normal.lengthSqr() <= 1.0E-8D
                ? new Vec3(0.0D, 1.0D, 0.0D) : normal.normalize();
        return BlockPos.containing(point.add(outward.scale(0.01D)));
    }

    private static void pruneUnsupportedSurfaceCells(Hole hole) {
        if (level == null || hole.cells.isEmpty()) {
            return;
        }
        Long2ObjectOpenHashMap<SurfaceSupport> supportCache = hole.supportScratch;
        LongOpenHashSet invalidSupports = hole.invalidSupportScratch;
        supportCache.clear();
        invalidSupports.clear();
        for (SurfaceCell cell : hole.cells.values()) {
            long supportKey = surfaceSupportKey(cell);
            if (supportCache.containsKey(supportKey)
                    || invalidSupports.contains(supportKey)) {
                continue;
            }
            SurfaceSupport support = currentSupport(cell);
            if (support == null) {
                invalidSupports.add(supportKey);
                continue;
            }
            supportCache.put(supportKey, support);
        }

        var iterator = hole.cells.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            SurfaceCell cell = entry.getValue();
            SurfaceSupport support = supportCache.get(surfaceSupportKey(cell));
            if (support != null
                    && support.medium() == cell.supportMedium
                    && Math.abs(support.surfaceY() - cell.surfaceY) <= PIXEL * 0.25D
                    && sameRenderedSurface(
                            cell.renderedHit, cell.renderedPatch,
                            support.renderedHit(), support.renderedPatch())) {
                continue;
            }
            hole.rimDirty.remove(entry.getLongKey());
            iterator.remove();
            retainedSurfaceCells--;
            markAdjacentRimDirty(hole, cell.pixelX, cell.pixelZ);
        }
    }

    private static SurfaceSupport currentSupport(SurfaceCell cell) {
        if (level == null || cell == null) {
            return null;
        }
        BlockPos pos = BlockPos.of(cell.supportBlock);
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null || medium != cell.supportMedium) {
            return null;
        }
        double worldX = (cell.pixelX + 0.5D) * PIXEL;
        double worldZ = (cell.pixelZ + 0.5D) * PIXEL;
        VisualSurface visual = visualSurfaceAt(level, pos, medium, worldX, worldZ);
        double localSurfaceHeight = visual == null
                ? visualSurfaceHeightAt(level, pos, state, medium, worldX, worldZ)
                : visual.point().y - pos.getY();
        if (!Double.isFinite(localSurfaceHeight)) {
            return null;
        }
        double surfaceY = pos.getY() + localSurfaceHeight;
        if (Math.abs(surfaceY - cell.surfaceY) > PIXEL * 0.25D
                || topSurfaceCovered(pos, surfaceY)) {
            return null;
        }
        return new SurfaceSupport(
                pos, medium, surfaceY, visual == null ? null : visual.hit(),
                renderedPatchAt(pos, state, cell.pixelX, cell.pixelZ));
    }

    private static boolean topSurfaceCovered(BlockPos supportPos, double surfaceY) {
        if (surfaceY < supportPos.getY() + 1.0D - 1.0E-4D) {
            return false;
        }
        BlockPos abovePos = supportPos.above();
        BlockState above = level.getBlockState(abovePos);
        if (above.isAir() || above.getBlock() == ModBlocks.MUD_FOOTPRINT.get()) {
            return false;
        }
        if (ModBlocks.isSinkingBlock(above.getBlock())) {
            return true;
        }
        for (net.minecraft.world.phys.AABB box : above.getCollisionShape(level, abovePos).toAabbs()) {
            if (box.minY <= 1.0E-4D
                    && box.minX <= 1.0E-4D && box.maxX >= 1.0D - 1.0E-4D
                    && box.minZ <= 1.0E-4D && box.maxZ >= 1.0D - 1.0E-4D) {
                return true;
            }
        }
        return false;
    }

    private static void markAdjacentRimDirty(Hole hole, int pixelX, int pixelZ) {
        hole.rimDirty.add(cellKey(pixelX - 1, pixelZ));
        hole.rimDirty.add(cellKey(pixelX + 1, pixelZ));
        hole.rimDirty.add(cellKey(pixelX, pixelZ - 1));
        hole.rimDirty.add(cellKey(pixelX, pixelZ + 1));
    }

    private static SinkingMedium blendedSurfaceMedium(int pixelX, int pixelZ,
            double surfaceY, long hash, SinkingMedium fallback) {
        SinkingMedium primary = surfaceMediumAt(pixelX, pixelZ, surfaceY);
        if (primary == null) {
            return fallback;
        }
        int firstDirection = (int) ((hash >>> 9) & 3L);
        for (int distance = 1; distance <= 2; distance++) {
            for (int offset = 0; offset < 4; offset++) {
                int direction = (firstDirection + offset) & 3;
                int stepX = direction == 0 ? -1 : direction == 1 ? 1 : 0;
                int stepZ = direction == 2 ? -1 : direction == 3 ? 1 : 0;
                SinkingMedium neighbor = surfaceMediumAt(
                        pixelX + stepX * distance,
                        pixelZ + stepZ * distance,
                        surfaceY);
                if (neighbor == null || neighbor == primary) {
                    continue;
                }
                double chance = distance == 1 ? 0.38D : 0.14D;
                double roll = ((mix(hash ^ distance * 0x632be59bd9b4e019L) >>> 11) & 1023L) / 1023.0D;
                return roll < chance ? neighbor : primary;
            }
        }
        return primary;
    }

    private static SinkingMedium surfaceMediumAt(int pixelX, int pixelZ, double surfaceY) {
        SurfaceSupport support = surfaceSupportAt(pixelX, pixelZ, surfaceY);
        return support == null ? null : support.medium();
    }

    private static SurfaceSupport surfaceSupportAt(int pixelX, int pixelZ, double surfaceY) {
        double worldX = (pixelX + 0.5D) * PIXEL;
        double worldZ = (pixelZ + 0.5D) * PIXEL;
        BlockPos pos = BlockPos.containing(worldX, surfaceY - 0.025D, worldZ);
        return surfaceSupportAt(pos, pixelX, pixelZ, surfaceY, worldX, worldZ);
    }

    private static SurfaceSupport surfaceSupportAt(Hole hole,
            int pixelX, int pixelZ, double surfaceY) {
        double worldX = (pixelX + 0.5D) * PIXEL;
        double worldZ = (pixelZ + 0.5D) * PIXEL;
        int blockX = Mth.floor(worldX);
        int blockZ = Mth.floor(worldZ);
        SurfaceSupport nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < hole.allowedSurfaceCount; index++) {
            BlockPos candidate = BlockPos.of(hole.allowedSurfaceBlocks[index]);
            if (candidate.getX() != blockX || candidate.getZ() != blockZ) {
                continue;
            }
            SurfaceSupport support = surfaceSupportAt(
                    candidate, pixelX, pixelZ, surfaceY, worldX, worldZ);
            if (support == null) {
                continue;
            }
            double distance = Math.abs(support.surfaceY() - surfaceY);
            if (distance < nearestDistance) {
                nearest = support;
                nearestDistance = distance;
            }
        }
        return nearest != null ? nearest : surfaceSupportAt(pixelX, pixelZ, surfaceY);
    }

    private static SurfaceSupport surfaceSupportAt(BlockPos pos,
            int pixelX, int pixelZ, double referenceSurfaceY,
            double worldX, double worldZ) {
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null) {
            return null;
        }
        VisualSurface visual = visualSurfaceAt(level, pos, medium, worldX, worldZ);
        double localSurfaceHeight = visual == null
                ? visualSurfaceHeightAt(level, pos, state, medium, worldX, worldZ)
                : visual.point().y - pos.getY();
        if (!Double.isFinite(localSurfaceHeight)) {
            return null;
        }
        double actualSurface = pos.getY() + localSurfaceHeight;
        return (state.getBlock() instanceof AdaptiveMudBlock
                || Math.abs(actualSurface - referenceSurfaceY) <= 0.085D)
                ? new SurfaceSupport(
                        pos.immutable(), medium, actualSurface,
                        visual == null ? null : visual.hit(),
                        renderedPatchAt(pos, state, pixelX, pixelZ))
                : null;
    }

    private static MudRenderedSurfaceGeometry.SurfacePatch renderedPatchAt(
            BlockPos pos, BlockState state, int pixelX, int pixelZ) {
        if (!(state.getBlock() instanceof AdaptiveMudBlock)
                || !MudSurfaceClientSettings.preciseModelGeometry()) {
            return null;
        }
        return MudRenderedSurfaceGeometry.surfacePatch(
                level, pos, state, Direction.UP,
                Math.floorMod(pixelX, 16), Math.floorMod(pixelZ, 16));
    }

    private static void pruneSettledSurfaceCells(Hole hole, int maximum) {
        long gameTick = level == null ? 0L : level.getGameTime();
        if (hole.lastCapacityPruneTick == gameTick) {
            return;
        }
        hole.lastCapacityPruneTick = gameTick;
        int target = Math.max(32, maximum / 32);
        var iterator = hole.cells.long2ObjectEntrySet().iterator();
        while (iterator.hasNext() && target > 0) {
            var entry = iterator.next();
            SurfaceCell cell = entry.getValue();
            if (!cell.refreshed
                    && cell.depression <= 0.003D
                    && cell.pileHeight <= SURFACE_CELL_VISUAL_HEIGHT_EPSILON
                    && cell.targetPileHeight <= SURFACE_CELL_VISUAL_HEIGHT_EPSILON) {
                hole.rimDirty.remove(entry.getLongKey());
                iterator.remove();
                retainedSurfaceCells--;
                target--;
            }
        }
    }

    private static void removeSurfaceCell(
            Hole hole, long key, SurfaceCell cell) {
        if (hole.cells.remove(key) != null) {
            retainedSurfaceCells--;
        }
        hole.rimDirty.remove(key);
        markAdjacentRimDirty(hole, cell.pixelX, cell.pixelZ);
        if (hole.cells.isEmpty()) {
            hole.resetSurfaceBounds();
        }
    }

    private static void rebuildSurfaceMask(Hole hole) {
        rebuildSurfaceMask(hole, hole.center);
    }

    private static void rebuildSurfaceMask(Hole hole, Vec3 maskCenter) {
        int centerX = Mth.floor(maskCenter.x);
        int centerY = surfaceMaskLayer(
                maskCenter.y, hole.profilePos,
                hole.profilePos != null
                        && level.getBlockState(hole.profilePos).getBlock()
                                instanceof AdaptiveMudBlock);
        int centerZ = Mth.floor(maskCenter.z);
        double rimReach = baseRimWidth(hole) * PIXEL;
        int radius = Mth.clamp((int) Math.ceil(hole.radius + rimReach) + 1, 1, 3);
        hole.allowedSurfaceCount = 0;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                BlockPos pos = new BlockPos(x, centerY, z);
                BlockState state = level.getBlockState(pos);
                SinkingMedium surfaceMedium = ModBlocks.mediumOf(state.getBlock());
                if (surfaceMedium == null) {
                    continue;
                }
                double localSurfaceHeight = visualSurfaceHeightAt(
                        level, pos, state, surfaceMedium, x + 0.5D, z + 0.5D);
                boolean adaptive = state.getBlock() instanceof AdaptiveMudBlock;
                if (!Double.isFinite(localSurfaceHeight) && adaptive) {
                    localSurfaceHeight = AdaptiveMudBlock.sourceSurfaceHeight(
                            level, pos, state);
                }
                if (!Double.isFinite(localSurfaceHeight)) {
                    continue;
                }
                double surfaceY = pos.getY() + localSurfaceHeight;
                if (!adaptive && Math.abs(surfaceY - maskCenter.y) > 0.085D
                        || hole.allowedSurfaceCount >= hole.allowedSurfaceBlocks.length) {
                    continue;
                }
                hole.allowedSurfaceBlocks[hole.allowedSurfaceCount++] = pos.asLong();
            }
        }
    }

    static double renderedSurfaceDepth(
            double fallbackDepth, double renderedSurfaceY, double feetY) {
        if (!Double.isFinite(renderedSurfaceY) || !Double.isFinite(feetY)) {
            return Math.max(0.0D, fallbackDepth);
        }
        return Math.max(0.0D, renderedSurfaceY - feetY);
    }

    static int surfaceMaskLayer(
            double surfaceY, BlockPos profilePos, boolean adaptiveProfile) {
        return adaptiveProfile && profilePos != null
                ? profilePos.getY() : Mth.floor(surfaceY - 0.025D);
    }

    static double visualSurfaceHeightAt(Level level, BlockPos pos,
            BlockState state, SinkingMedium medium,
            double worldX, double worldZ) {
        if (state != null && state.getBlock() instanceof AdaptiveMudBlock
                && MudSurfaceClientSettings.preciseModelGeometry()) {
            MudRenderedSurfaceGeometry.SurfaceHit rendered =
                    MudRenderedSurfaceGeometry.topSurfaceHit(
                    level, pos, state,
                    worldX - pos.getX(), worldZ - pos.getZ());
            if (rendered != null) {
                return rendered.coordinate();
            }
        }
        return MudMediumRuntime.surfaceHeightAt(
                level, pos, state, medium, worldX, worldZ);
    }

    static VisualSurface visualSurfaceAt(Level level, BlockPos pos,
            SinkingMedium medium, double worldX, double worldZ) {
        if (level == null || pos == null || !MudSurfaceClientSettings.preciseModelGeometry()) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AdaptiveMudBlock)
                || ModBlocks.mediumOf(state.getBlock()) != medium) {
            return null;
        }
        MudRenderedSurfaceGeometry.SurfaceHit hit =
                MudRenderedSurfaceGeometry.topSurfaceHit(
                        level, pos, state,
                        worldX - pos.getX(), worldZ - pos.getZ());
        if (hit == null || hit.normal().y <= 1.0E-4D) {
            return null;
        }
        return new VisualSurface(
                new Vec3(worldX, pos.getY() + hit.coordinate(), worldZ), hit);
    }

    private static void removeFaintestHole() {
        Hole faintest = null;
        for (Hole hole : HOLES.values()) {
            if (!hole.active && (faintest == null || hole.visibility < faintest.visibility)) {
                faintest = hole;
            }
        }
        if (faintest != null) {
            removeHole(faintest.entityId);
        }
    }

    private static void removeFarthestEruptionHole(Vec3 from) {
        Hole farthest = null;
        double farthestDistance = -1.0D;
        for (Hole hole : HOLES.values()) {
            if (!hole.persistentSurfaceSource) {
                continue;
            }
            double distance = hole.center.distanceToSqr(from);
            if (distance > farthestDistance) {
                farthest = hole;
                farthestDistance = distance;
            }
        }
        if (farthest != null) {
            removeHole(farthest.entityId);
        }
    }

    static boolean surfaceMatches(ClientLevel level, Vec3 point, SinkingMedium medium) {
        if (level == null) {
            return false;
        }
        BlockState state = level.getBlockState(BlockPos.containing(point.add(0.0D, -0.025D, 0.0D)));
        return ModBlocks.mediumOf(state.getBlock()) == medium;
    }

    static BlockPos supportPos(Vec3 point, Vec3 surfaceNormal) {
        return BlockPos.containing(point.subtract(safeNormal(surfaceNormal).scale(0.025D)));
    }

    static boolean enabled(BlockPos profilePos, SinkingMedium medium) {
        return MudSurfaceClientSettings.enabled()
                && MudMediumRuntime.enabled(level, profilePos, medium)
                && value(profilePos, medium, MudPhysicsParameter.SURFACE_EFFECTS_ENABLED) >= 0.5D;
    }

    static double value(
            BlockPos profilePos, SinkingMedium medium, MudPhysicsParameter parameter) {
        return MudMediumRuntime.value(level, profilePos, medium, parameter);
    }

    private static double effectiveCloseTicks(BlockPos profilePos, SinkingMedium medium) {
        double configured = Math.max(1.0D,
                value(profilePos, medium, MudPhysicsParameter.SURFACE_CLOSE_TICKS));
        double viscosity = value(profilePos, medium, MudPhysicsParameter.VISCOSITY_SURFACE);
        double viscosityScale = Mth.clamp(2.10D + viscosity * 0.65D, 2.25D, 6.0D);
        return configured * viscosityScale;
    }

    private static void tickPendingImpacts() {
        Iterator<Map.Entry<Integer, PendingImpact>> iterator = PENDING_IMPACTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, PendingImpact> entry = iterator.next();
            PendingImpact impact = entry.getValue();
            int impactKey = impactHoleKey(entry.getKey(), impact.origin);
            Hole hole = HOLES.get(impactKey);
            if (hole == null && level != null) {
                if (HOLES.size() >= MudSurfaceClientSettings.maxHoles()) {
                    removeFaintestImpactHole();
                }
                if (hole == null && HOLES.size() < MudSurfaceClientSettings.maxHoles()) {
                    Vec3 center = alignedCenter(impact.origin);
                    hole = new Hole(impactKey, mix(
                            (long) entry.getKey() * 0x9e3779b97f4a7c15L
                                    ^ BlockPos.containing(center).asLong()));
                    hole.center = center;
                    hole.targetCenter = center;
                    hole.normal = new Vec3(0.0D, 1.0D, 0.0D);
                    hole.targetNormal = hole.normal;
                    hole.axisX = new Vec3(1.0D, 0.0D, 0.0D);
                    hole.axisZ = new Vec3(0.0D, 0.0D, 1.0D);
                    hole.medium = impact.medium;
                    hole.profilePos = impact.profilePos;
                    hole.impactSurfaceSource = true;
                    hole.impactOwnerEntityId = entry.getKey();
                    HOLES.put(impactKey, hole);
                }
            }
            if (hole != null) {
                Vec3 center = alignedCenter(impact.origin);
                hole.medium = impact.medium;
                hole.profilePos = impact.profilePos;
                hole.center = center;
                hole.targetCenter = center;
                prepareSurfaceUpdate(hole, level.getGameTime());
                rebuildSurfaceMask(hole, center);
                if (stampImpactDepression(hole, impact)) {
                    iterator.remove();
                    continue;
                }
            }
            if (--impact.remainingTicks <= 0) {
                iterator.remove();
            }
        }
    }

    private static void mergeImpactHolesIntoPressureHoles() {
        Iterator<Map.Entry<Integer, Hole>> iterator = HOLES.entrySet().iterator();
        while (iterator.hasNext()) {
            Hole impactHole = iterator.next().getValue();
            if (!impactHole.impactSurfaceSource || impactHole.cells.isEmpty()) {
                continue;
            }
            Hole pressureHole = pressureMergeTarget(impactHole);
            if (pressureHole == null) {
                continue;
            }
            mergeImpactSurfaceCells(impactHole, pressureHole);
            retainedSurfaceCells -= impactHole.cells.size();
            iterator.remove();
        }
    }

    private static Hole pressureMergeTarget(Hole impactHole) {
        Hole owner = HOLES.get(impactHole.impactOwnerEntityId);
        if (hasOverlappingPlayerPressure(owner, impactHole)) {
            return owner;
        }
        for (Hole candidate : HOLES.values()) {
            if (candidate != owner
                    && hasOverlappingPlayerPressure(candidate, impactHole)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasOverlappingPlayerPressure(Hole pressureHole, Hole impactHole) {
        if (pressureHole == null
                || pressureHole.entityId < 0
                || !pressureHole.active
                || !pressureHole.seenThisTick
                || pressureHole.physicalized
                || pressureHole.medium != impactHole.medium
                || pressureHole.cells.isEmpty()
                || !pressureHole.surfaceBounds().intersects(impactHole.surfaceBounds())) {
            return false;
        }
        for (SurfaceCell pressureCell : pressureHole.cells.values()) {
            if (!pressureCell.refreshed || effectiveDepression(pressureCell) <= 0.003D) {
                continue;
            }
            SurfaceCell impactCell = impactHole.cells.get(
                    cellKey(pressureCell.pixelX, pressureCell.pixelZ));
            if (impactCell != null
                    && impactCell.supportBlock == pressureCell.supportBlock
                    && sameContinuousSurface(impactCell, pressureCell)) {
                return true;
            }
        }
        return false;
    }

    static int impactHoleKey(int entityId, Vec3 origin) {
        long quantizedX = Mth.floor(origin.x * 4.0D);
        long quantizedY = Mth.floor(origin.y * 4.0D);
        long quantizedZ = Mth.floor(origin.z * 4.0D);
        long hash = mix(quantizedX * 0x632be59bd9b4e019L
                ^ quantizedY * 0x94d049bb133111ebL
                ^ quantizedZ * 0x369dea0f31a53f85L
                ^ (long) entityId * 0x9e3779b97f4a7c15L);
        return Integer.MIN_VALUE | ((int) (hash ^ hash >>> 32) & 0x3FFFFFFF);
    }

    private static int eruptionHoleKey(int ventId) {
        return 0xC0000000 | (ventId & 0x1FFFFFFF);
    }

    private static void removeFaintestImpactHole() {
        Integer selected = null;
        double faintest = Double.POSITIVE_INFINITY;
        for (Map.Entry<Integer, Hole> entry : HOLES.entrySet()) {
            if (entry.getKey() >= 0 || entry.getValue().persistentSurfaceSource) {
                continue;
            }
            Hole hole = entry.getValue();
            double weight = hole.visibility + hole.cells.size() * 0.001D;
            if (weight < faintest) {
                faintest = weight;
                selected = entry.getKey();
            }
        }
        if (selected != null) {
            removeHole(selected);
        }
    }

    private static void putSurfaceCell(Hole hole, long key, SurfaceCell cell) {
        if (hole.cells.put(key, cell) == null) {
            retainedSurfaceCells++;
        }
    }

    private static Hole removeHole(int key) {
        Hole removed = HOLES.remove(key);
        if (removed != null) {
            retainedSurfaceCells -= removed.cells.size();
        }
        return removed;
    }

    private static void discardSurfaceCells(Hole hole) {
        retainedSurfaceCells -= hole.cells.size();
        hole.cells.clear();
        hole.rimDirty.clear();
        hole.resetSurfaceBounds();
    }

    private static double baseRimWidth(Hole hole) {
        return value(hole.profilePos, hole.medium, MudPhysicsParameter.SURFACE_RIM_WIDTH_PIXELS);
    }

    private static int rimSearchRadius(double rimWidth) {
        return Mth.clamp((int) Math.ceil(rimWidth + 1.0D), 1, 9);
    }

    private static MudEntityGeometry.PlaneSlice scaledSlice(
            MudEntityGeometry.PlaneSlice slice, Vec3 center, double scale) {
        if (slice.empty() || Math.abs(scale - 1.0D) <= 1.0E-6D) {
            return slice;
        }
        List<MudEntityGeometry.SlicePolygon> polygons = new ArrayList<>(slice.polygons().size());
        for (MudEntityGeometry.SlicePolygon polygon : slice.polygons()) {
            List<Vec3> vertices = new ArrayList<>(polygon.vertices().size());
            for (Vec3 point : polygon.vertices()) {
                vertices.add(new Vec3(
                        center.x + (point.x - center.x) * scale,
                        point.y,
                        center.z + (point.z - center.z) * scale));
            }
            polygons.add(new MudEntityGeometry.SlicePolygon(
                    polygon.part(), List.copyOf(vertices), polygon.armorOffset()));
        }
        return new MudEntityGeometry.PlaneSlice(slice.surfaceY(), List.copyOf(polygons));
    }

    private static MudEntityGeometry.PlaneSlice surfaceSlice(
            Player player, Contact contact, double scale) {
        if (continuousSlopeFootprint(contact.surfaceNormal)) {
            AABB bounds = player.getBoundingBox();
            return continuousPressureSlice(
                    player.getX(), contact.surfacePoint.y, player.getZ(),
                    Math.max(bounds.getXsize(), bounds.getZsize()), scale);
        }
        return scaledSlice(
                MudEntityGeometry.horizontalSlice(player, contact.surfacePoint.y),
                player.position(), scale);
    }

    private static boolean continuousSlopeFootprint(Vec3 surfaceNormal) {
        Vec3 normal = safeNormal(surfaceNormal);
        return normal.y > 0.10D && normal.y < 0.995D;
    }

    static MudEntityGeometry.PlaneSlice continuousPressureSlice(
            double centerX, double surfaceY, double centerZ,
            double bodyWidth, double scale) {
        double radius = Math.max(PIXEL * 2.0D,
                Math.max(0.0D, bodyWidth) * 0.5D * Math.max(0.0D, scale));
        List<Vec3> vertices = new ArrayList<>(12);
        for (int index = 0; index < 12; index++) {
            double angle = Math.PI * 2.0D * index / 12.0D;
            vertices.add(new Vec3(
                    centerX + Math.cos(angle) * radius,
                    surfaceY,
                    centerZ + Math.sin(angle) * radius));
        }
        return new MudEntityGeometry.PlaneSlice(surfaceY, List.of(
                new MudEntityGeometry.SlicePolygon(
                        MudBodyPart.BODY, List.copyOf(vertices), 0.0D)));
    }

    private static double sliceRadius(MudEntityGeometry.PlaneSlice slice, Vec3 center) {
        double radius = PIXEL * 1.45D;
        for (MudEntityGeometry.SlicePolygon polygon : slice.polygons()) {
            for (Vec3 point : polygon.vertices()) {
                radius = Math.max(radius, horizontalDistance(point, center));
            }
        }
        return radius;
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return Math.sqrt(x * x + z * z);
    }

    static Vec3[] basis(Vec3 normal, Vec3 preferredTangent) {
        Vec3 n = safeNormal(normal);
        Vec3 tangent = reject(preferredTangent, n);
        if (tangent.lengthSqr() < 1.0E-8D) {
            tangent = reject(Math.abs(n.y) < 0.85D
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D), n);
        }
        tangent = tangent.normalize();
        return new Vec3[] {tangent, n.cross(tangent).normalize()};
    }

    private static Vec3 reject(Vec3 vector, Vec3 normal) {
        Vec3 n = safeNormal(normal);
        return vector.subtract(n.scale(vector.dot(n)));
    }

    static Vec3 safeNormal(Vec3 normal) {
        return normal == null || normal.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : normal.normalize();
    }

    private static Vec3 orthogonalAxis(Vec3 candidate, Vec3 normal, Vec3 fallback) {
        Vec3 axis = candidate == null ? Vec3.ZERO : reject(candidate, normal);
        if (axis.lengthSqr() < 1.0E-8D) {
            axis = reject(fallback, normal);
        }
        return axis.lengthSqr() < 1.0E-8D ? new Vec3(1.0D, 0.0D, 0.0D) : axis.normalize();
    }

    static Vec3 alignedCenter(Vec3 point) {
        return new Vec3(pixelCenter(point.x), point.y, pixelCenter(point.z));
    }

    static Vec3 alignedSurfaceCenter(Vec3 point, Vec3 normal) {
        Vec3 n = safeNormal(normal);
        return new Vec3(
                Math.abs(n.x) >= 0.707D ? point.x : pixelCenter(point.x),
                Math.abs(n.y) >= 0.707D ? point.y : pixelCenter(point.y),
                Math.abs(n.z) >= 0.707D ? point.z : pixelCenter(point.z));
    }

    private static Vec3 alignedSurfaceCenter(Contact contact) {
        Vec3 aligned = alignedCenter(contact.surfacePoint);
        VisualSurface visual = visualSurfaceAt(
                level, contact.profilePos, contact.medium, aligned.x, aligned.z);
        return visual == null ? aligned : visual.point();
    }

    private static double pixelCenter(double value) {
        return (Math.floor(value / PIXEL) + 0.5D) * PIXEL;
    }

    private static double approach(double current, double target, double growthRate, double closeTicks) {
        double rate = target >= current
                ? growthRate
                : Math.min(0.20D, 3.0D / Math.max(1.0D, closeTicks));
        return current + (target - current) * rate;
    }

    private static double approachRate(double current, double target, double rate) {
        double next = current + (target - current) * Mth.clamp(rate, 0.0D, 1.0D);
        return Math.abs(target - next) <= 1.0E-4D ? target : next;
    }

    private static double smooth(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - clamped * 2.0D);
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double amount) {
        return from.add(to.subtract(from).scale(amount));
    }

    static double nextUnit() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    private static double unit(long value) {
        return ((mix(value) >>> 11) & 4095L) / 4095.0D;
    }

    private static double signedUnit(long value) {
        return unit(value) * 2.0D - 1.0D;
    }

    static long nextLong() {
        randomState ^= randomState << 13;
        randomState ^= randomState >>> 7;
        randomState ^= randomState << 17;
        return randomState;
    }

    static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }

    static long cellKey(int pixelX, int pixelZ) {
        return ((long) pixelX << 32) ^ (pixelZ & 0xffffffffL);
    }

    private record Contact(
            SinkingMedium medium,
            Vec3 surfacePoint,
            Vec3 surfaceNormal,
            Vec3 surfaceAxisX,
            Vec3 surfaceAxisZ,
            BlockPos profilePos,
            long visualSource,
            boolean physicalized,
            double depth,
            double availableDepth,
            float agitation,
            double horizontalSpeed,
            double walkScale,
            double fleshContraction,
            double fleshWrap,
            double fleshPressure) {
    }

    record VisualSurface(
            Vec3 point, MudRenderedSurfaceGeometry.SurfaceHit hit) {
    }

    private record AdhesionAnchorCandidate(
            MudBodyPart part,
            MudSurface surface,
            int row,
            int column,
            int cell,
            float coverage,
            long visualSource,
            EquipmentSlot armorSlot,
            double surfaceOffset,
            double height,
            double angle) {
    }

    static final class Hole {
        final int entityId;
        final long seed;
        SinkingMedium medium = SinkingMedium.MUD;
        long visualSource;
        BlockPos profilePos = BlockPos.ZERO;
        Vec3 center = Vec3.ZERO;
        Vec3 targetCenter = Vec3.ZERO;
        Vec3 normal = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 targetNormal = normal;
        Vec3 axisX = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 axisZ = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 fleshRetreatCenter = Vec3.ZERO;
        Vec3 fleshRetreatNormal = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 fleshRetreatAxisX = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 fleshRetreatAxisZ = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 fleshEnclosureCenter = Vec3.ZERO;
        Vec3 lastStampCenter = Vec3.ZERO;
        MudEntityGeometry.PlaneSlice previousSlice;
        double radius = 0.10D;
        double previousVisibility;
        double visibility;
        double targetVisibility;
        double speed;
        double bubbleAccumulator;
        float agitation;
        double previousFleshContraction;
        double fleshContraction;
        double previousFleshWrap;
        double fleshWrap;
        double previousFleshPressure;
        double fleshPressure;
        double previousFleshExposedHeight;
        double fleshExposedHeight = 1.80D;
        double fleshRetreatHeight = 1.80D;
        double previousFleshPillarProgress;
        double fleshPillarProgress;
        int fleshBrokenMask;
        int fleshPillarDamagePacked;
        int fleshPillarRequiredHitsPacked;
        boolean fleshEnclosureActive;
        boolean fleshPillarWithdrawing;
        boolean fleshRetreatFrameCaptured;
        boolean fleshEnclosureAnchorSet;
        int inactiveTicks;
        int adhesionContactTicks;
        int adhesionSession;
        int impactOwnerEntityId = -1;
        int pendingMergeEntityId = -1;
        AdhesionStrandProfile adhesionProfile;
        boolean active;
        boolean seenThisTick;
        boolean persistentSurfaceSource;
        boolean impactSurfaceSource;
        boolean physicalized;
        boolean fleshTemplateEnabled;
        boolean hasStampCenter;
        boolean surfaceUpdateRequested;
        final long[] allowedSurfaceBlocks = new long[49];
        final Long2ObjectOpenHashMap<SurfaceCell> cells = new Long2ObjectOpenHashMap<>();
        final LongOpenHashSet rimDirty = new LongOpenHashSet();
        final AdhesionStrand[] adhesionStrands = createAdhesionStrands();
        final boolean[] adhesionSessionTouched = new boolean[MudSurfaceLayout.CELL_COUNT];
        final List<AdhesionAnchorCandidate> adhesionCandidateScratch = new ArrayList<>(128);
        final Map<MudBodyPart, MudEntityGeometry.SlicePolygon> previousPolygonScratch =
                new EnumMap<>(MudBodyPart.class);
        final Long2ObjectOpenHashMap<SurfaceSupport> supportScratch =
                new Long2ObjectOpenHashMap<>();
        final LongOpenHashSet invalidSupportScratch = new LongOpenHashSet();
        final long[] adhesionSupportProbeBlocks = new long[32];
        final boolean[] adhesionSupportProbeMatches = new boolean[32];
        int allowedSurfaceCount;
        int adhesionSupportProbeCount;
        int maximumRadiusPixels = 1;
        long lastCapacityPruneTick = Long.MIN_VALUE;
        long lastSurfacePreparedTick = Long.MIN_VALUE;
        long lastSurfaceUpdateTick = Long.MIN_VALUE;
        int minimumSurfacePixelX = Integer.MAX_VALUE;
        int maximumSurfacePixelX = Integer.MIN_VALUE;
        int minimumSurfacePixelZ = Integer.MAX_VALUE;
        int maximumSurfacePixelZ = Integer.MIN_VALUE;
        double minimumSurfaceY = Double.POSITIVE_INFINITY;
        double maximumSurfaceY = Double.NEGATIVE_INFINITY;

        Hole(int entityId, long seed) {
            this.entityId = entityId;
            this.seed = seed;
        }

        void beginTick() {
            previousVisibility = visibility;
            previousFleshContraction = fleshContraction;
            previousFleshWrap = fleshWrap;
            previousFleshPressure = fleshPressure;
            previousFleshExposedHeight = fleshExposedHeight;
            previousFleshPillarProgress = fleshPillarProgress;
            surfaceUpdateRequested = false;
        }

        void beginSurfaceUpdate() {
            for (SurfaceCell cell : cells.values()) {
                cell.previousDepression = cell.depression;
                cell.previousClosureProgress = cell.closureProgress;
                cell.previousPileHeight = cell.pileHeight;
                cell.refreshed = false;
                cell.targetPileHeight = 0.0D;
            }
        }

        float surfacePartialTick(long gameTime, float partialTick) {
            return lastSurfacePreparedTick == gameTime
                    ? Mth.clamp(partialTick, 0.0F, 1.0F)
                    : 1.0F;
        }

        void includeSurfaceCell(SurfaceCell cell) {
            minimumSurfacePixelX = Math.min(minimumSurfacePixelX, cell.pixelX);
            maximumSurfacePixelX = Math.max(maximumSurfacePixelX, cell.pixelX);
            minimumSurfacePixelZ = Math.min(minimumSurfacePixelZ, cell.pixelZ);
            maximumSurfacePixelZ = Math.max(maximumSurfacePixelZ, cell.pixelZ);
            minimumSurfaceY = Math.min(minimumSurfaceY, cell.surfaceY);
            maximumSurfaceY = Math.max(maximumSurfaceY, cell.surfaceY);
        }

        void resetSurfaceBounds() {
            minimumSurfacePixelX = Integer.MAX_VALUE;
            maximumSurfacePixelX = Integer.MIN_VALUE;
            minimumSurfacePixelZ = Integer.MAX_VALUE;
            maximumSurfacePixelZ = Integer.MIN_VALUE;
            minimumSurfaceY = Double.POSITIVE_INFINITY;
            maximumSurfaceY = Double.NEGATIVE_INFINITY;
        }

        AABB surfaceBounds() {
            if (minimumSurfacePixelX > maximumSurfacePixelX
                    || minimumSurfacePixelZ > maximumSurfacePixelZ
                    || !Double.isFinite(minimumSurfaceY)
                    || !Double.isFinite(maximumSurfaceY)) {
                return new AABB(center, center).inflate(0.25D);
            }
            return new AABB(
                    minimumSurfacePixelX * PIXEL,
                    minimumSurfaceY - 0.04D,
                    minimumSurfacePixelZ * PIXEL,
                    (maximumSurfacePixelX + 1.0D) * PIXEL,
                    maximumSurfaceY + 1.0D,
                    (maximumSurfacePixelZ + 1.0D) * PIXEL);
        }

        boolean allowsSurface(long blockPos) {
            for (int index = 0; index < allowedSurfaceCount; index++) {
                if (allowedSurfaceBlocks[index] == blockPos) {
                    return true;
                }
            }
            return false;
        }
    }

    private record TenderFleshEnclosureState(
            boolean active,
            boolean retreating,
            int brokenMask,
            int pillarDamagePacked,
            int pillarRequiredHitsPacked,
            int cooldownTicks,
            float progress,
            double anchorX,
            double anchorY,
            double anchorZ,
            long receivedTick) {
    }

    static final class AdhesionStrand extends MudAdhesionStrandState {
        AdhesionStrand(int index) {
            super(index);
        }
    }

    private static AdhesionStrand[] createAdhesionStrands() {
        AdhesionStrand[] strands = new AdhesionStrand[MAX_ADHESION_STRANDS_PER_PLAYER];
        for (int index = 0; index < strands.length; index++) {
            strands[index] = new AdhesionStrand(index);
        }
        return strands;
    }


    private static boolean sameRenderedSurface(
            MudRenderedSurfaceGeometry.SurfaceHit first,
            MudRenderedSurfaceGeometry.SurfacePatch firstPatch,
            MudRenderedSurfaceGeometry.SurfaceHit second,
            MudRenderedSurfaceGeometry.SurfacePatch secondPatch) {
        if (first == null || second == null) {
            return first == second && sameRenderedPatch(firstPatch, secondPatch);
        }
        return first.normal().dot(second.normal()) >= 0.999D
                && sameRenderedPatch(firstPatch, secondPatch);
    }

    private static boolean sameRenderedPatch(
            MudRenderedSurfaceGeometry.SurfacePatch first,
            MudRenderedSurfaceGeometry.SurfacePatch second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.full() == second.full()
                && first.geometryKey() == second.geometryKey();
    }

    private record SurfaceSupport(BlockPos pos, SinkingMedium medium,
            double surfaceY, MudRenderedSurfaceGeometry.SurfaceHit renderedHit,
            MudRenderedSurfaceGeometry.SurfacePatch renderedPatch) {
    }

    private record AdhesionAnchorRange(double minimumHeight, double maximumHeight) {
        private boolean empty() {
            return !Double.isFinite(minimumHeight) || !Double.isFinite(maximumHeight);
        }
    }

    private record RecoveredAdhesionAnchor(
            int row, int column, ClientAdhesionCoverage.Sample source) {
    }

    static final class SurfaceCell {
        final int pixelX;
        final int pixelZ;
        final double surfaceY;
        final long supportBlock;
        final SinkingMedium supportMedium;
        final long seed;
        final MudRenderedSurfaceGeometry.SurfaceHit renderedHit;
        final MudRenderedSurfaceGeometry.SurfacePatch renderedPatch;
        SinkingMedium medium;
        double previousDepression;
        double depression;
        double previousClosureProgress;
        double closureProgress;
        int closureMask;
        int closureRimBucket;
        double previousPileHeight;
        double pileHeight;
        double targetPileHeight;
        double pileWeight;
        boolean refreshed;
        int packedLight;

        SurfaceCell(int pixelX, int pixelZ, double surfaceY, SinkingMedium medium,
                long supportBlock, SinkingMedium supportMedium, long seed) {
            this(pixelX, pixelZ, surfaceY, medium,
                    supportBlock, supportMedium, seed, null, null);
        }

        SurfaceCell(int pixelX, int pixelZ, double surfaceY, SinkingMedium medium,
                long supportBlock, SinkingMedium supportMedium, long seed,
                MudRenderedSurfaceGeometry.SurfaceHit renderedHit,
                MudRenderedSurfaceGeometry.SurfacePatch renderedPatch) {
            this.pixelX = pixelX;
            this.pixelZ = pixelZ;
            this.surfaceY = surfaceY;
            this.medium = medium;
            this.supportBlock = supportBlock;
            this.supportMedium = supportMedium;
            this.seed = seed;
            this.renderedHit = renderedHit;
            this.renderedPatch = renderedPatch;
        }
    }

    static final class Bubble {
        boolean active;
        Vec3 center = Vec3.ZERO;
        Vec3 normal = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 tangent = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 bitangent = new Vec3(0.0D, 0.0D, 1.0D);
        SinkingMedium medium = SinkingMedium.MUD;
        BlockPos profilePos = BlockPos.ZERO;
        double radius;
        int ageTicks;
        int lifeTicks;
        long seed;
        boolean ambient;
        boolean soundPlayed;

        void copyFrom(Bubble source) {
            active = source.active;
            center = source.center;
            normal = source.normal;
            tangent = source.tangent;
            bitangent = source.bitangent;
            medium = source.medium;
            profilePos = source.profilePos;
            radius = source.radius;
            ageTicks = source.ageTicks;
            lifeTicks = source.lifeTicks;
            seed = source.seed;
            ambient = source.ambient;
            soundPlayed = source.soundPlayed;
        }
    }

    private static final class PendingImpact {
        private final SinkingMedium medium;
        private Vec3 origin;
        private BlockPos profilePos;
        private float impactStrength;
        private float volumeFraction;
        private int remainingTicks;

        private PendingImpact(
                SinkingMedium medium,
                Vec3 origin,
                BlockPos profilePos,
                float impactStrength,
                float volumeFraction,
                int remainingTicks) {
            this.medium = medium;
            this.origin = origin;
            this.profilePos = profilePos;
            this.impactStrength = impactStrength;
            this.volumeFraction = volumeFraction;
            this.remainingTicks = remainingTicks;
        }
    }
}
