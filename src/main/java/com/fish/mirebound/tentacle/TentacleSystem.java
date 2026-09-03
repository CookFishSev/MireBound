package com.fish.mirebound.tentacle;

import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.PhysicsTraceLog;
import com.fish.mirebound.network.payload.TentacleStateSyncPayload;
import com.fish.mirebound.physics.PlayerGravityControl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;

/** Server-authoritative procedural tentacles created and controlled through commands. */
public final class TentacleSystem {
    private static final double BASE_TRACKING_DISTANCE = 96.0D;
    /**
     * How long a wrap may exceed the body's reach before the tentacle gives up on it. Long enough
     * that a brief overshoot while rounding a corner is absorbed, short enough that a genuinely
     * impossible pursuit visibly ends rather than hanging.
     */
    private static final int WRAP_ABANDON_TICKS = 45;
    /** Grace period after abandoning a wrap, so the same route is not re-committed immediately. */
    private static final int WRAP_ABANDON_COOLDOWN_TICKS = 30;
    public static final double MINIMUM_GRAB_VOLUME = 5.0D;
    private static final Map<ServerLevel, LevelState> LEVELS = new WeakHashMap<>();

    private TentacleSystem() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelState state = LEVELS.get(level);
            if (state != null) {
                state.tick(level);
                if (state.instances.isEmpty()) {
                    LEVELS.remove(level);
                }
            }
        }
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            restoreLevel(level);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelState state = LEVELS.remove(level);
        if (state != null) {
            state.persist(level);
            state.releaseTransientInteractions();
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelState state = LEVELS.get(level);
            if (state != null) {
                state.persist(level);
                state.releaseTransientInteractions();
            }
        }
        LEVELS.clear();
    }

    public static int spawn(ServerLevel level, Vec3 rootAnchor, double volume) {
        TentaclePhysicsProfile profile = MudPhysicsSettings.tentacleProfile();
        LevelState state = LEVELS.computeIfAbsent(level, ignored -> new LevelState());
        if (profile == null || state.instances.size() >= profile.maximumInstances()) {
            return -1;
        }
        int id = state.allocateId();
        double clampedVolume = Mth.clamp(volume, 0.015625D, profile.maximumVolume());
        state.instances.put(id, new TentacleInstance(
                state, id, rootAnchor, clampedVolume, level.random.nextLong()));
        state.persist(level);
        return id;
    }

    public static boolean setIdle(ServerLevel level, int id) {
        TentacleInstance instance = instance(level, id);
        if (instance == null) {
            return false;
        }
        instance.setIdle();
        return true;
    }

    public static boolean setTracking(ServerLevel level, int id, UUID playerId) {
        TentacleInstance instance = instance(level, id);
        if (instance == null) {
            return false;
        }
        instance.setTracking(playerId);
        return true;
    }

    public static GrabEnableResult setGrabEnabled(ServerLevel level, int id, boolean enabled) {
        TentacleInstance instance = instance(level, id);
        if (instance == null) {
            return GrabEnableResult.UNKNOWN_INSTANCE;
        }
        return instance.setGrabEnabled(enabled);
    }

    public static boolean setGrabMode(ServerLevel level, int id, TentacleGrabMode mode) {
        TentacleInstance instance = instance(level, id);
        if (instance == null) {
            return false;
        }
        instance.setGrabMode(mode);
        return true;
    }

    public static boolean releaseGrab(ServerLevel level, int id) {
        TentacleInstance instance = instance(level, id);
        if (instance == null) {
            return false;
        }
        instance.releaseGrab(40);
        return true;
    }

    public static boolean emerge(ServerLevel level, int id) {
        TentacleInstance instance = instance(level, id);
        if (instance == null) {
            return false;
        }
        instance.beginEmerging();
        return true;
    }

    public static boolean retract(ServerLevel level, int id) {
        TentacleInstance instance = instance(level, id);
        if (instance == null) {
            return false;
        }
        instance.beginRetracting();
        return true;
    }

    public static boolean remove(ServerLevel level, int id) {
        LevelState state = LEVELS.get(level);
        if (state == null) {
            return false;
        }
        TentacleInstance removed = state.instances.remove(id);
        if (removed == null) {
            return false;
        }
        removed.releaseGrab(0);
        removed.sendRemoval(level);
        TentacleSavedData.get(level).remove(id);
        if (state.instances.isEmpty()) {
            LEVELS.remove(level);
        }
        return true;
    }

    public static int clear(ServerLevel level) {
        LevelState state = LEVELS.remove(level);
        TentacleSavedData.get(level).clear();
        if (state == null) {
            return 0;
        }
        int count = state.instances.size();
        state.instances.values().forEach(instance -> {
            instance.releaseGrab(0);
            instance.sendRemoval(level);
        });
        state.instances.clear();
        state.grabOwners.clear();
        return count;
    }

    public static List<String> describe(ServerLevel level) {
        LevelState state = LEVELS.get(level);
        if (state == null || state.instances.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(state.instances.size());
        state.instances.values().stream()
                .sorted(java.util.Comparator.comparingInt(instance -> instance.id))
                .forEach(instance -> result.add(instance.description()));
        return List.copyOf(result);
    }

    public static Vec3 root(ServerLevel level, int id) {
        TentacleInstance instance = instance(level, id);
        return instance == null ? null : instance.rootAnchor;
    }

    public static boolean containsPoint(
            ServerLevel level, int id, Vec3 point, double padding) {
        TentacleInstance instance = instance(level, id);
        return instance != null && instance.containsPoint(point, padding);
    }

    private static TentacleInstance instance(ServerLevel level, int id) {
        LevelState state = LEVELS.get(level);
        return state == null ? null : state.instances.get(id);
    }

    private static boolean finiteVector(Vec3 point) {
        return point != null && Double.isFinite(point.x)
                && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static void restoreLevel(ServerLevel level) {
        if (LEVELS.containsKey(level)) {
            return;
        }
        TentacleSavedData saved = TentacleSavedData.get(level);
        if (saved.states().isEmpty()) {
            return;
        }
        LevelState state = new LevelState(saved.nextId());
        for (TentacleSavedData.State entry : saved.states()) {
            state.restore(entry, level);
        }
        if (!state.instances.isEmpty()) {
            LEVELS.put(level, state);
        } else {
            saved.replace(state.nextInstanceId, List.of());
        }
    }

    private static final class LevelState {
        private final Map<Integer, TentacleInstance> instances = new HashMap<>();
        private final Map<UUID, Integer> grabOwners = new HashMap<>();
        private final List<TentacleInterCollider.Body> interCollisionBodies = new ArrayList<>();
        private int nextInstanceId;

        private LevelState() {
            this(1);
        }

        private LevelState(int nextInstanceId) {
            this.nextInstanceId = Math.max(1, nextInstanceId);
        }

        private void restore(TentacleSavedData.State saved, ServerLevel level) {
            TentaclePhysicsProfile profile = MudPhysicsSettings.tentacleProfile();
            if (profile == null || saved == null || saved.id() <= 0
                    || saved.root() == null || !finiteVector(saved.root())) {
                return;
            }
            if (!Double.isFinite(saved.volume())) {
                return;
            }
            double volume = Mth.clamp(saved.volume(), 0.015625D, profile.maximumVolume());
            if (instances.size() >= profile.maximumInstances()) {
                return;
            }
            instances.put(saved.id(), new TentacleInstance(
                    this, saved.id(), saved.root(), volume, saved.visualSeed(), saved));
            nextInstanceId = Math.max(nextInstanceId, saved.id() + 1);
        }

        void tick(ServerLevel level) {
            TentaclePhysicsProfile baseProfile = MudPhysicsSettings.tentacleProfile();
            TentacleGrabProfile grabProfile = MudPhysicsSettings.tentacleGrabProfile();
            if (baseProfile == null || grabProfile == null) {
                return;
            }
            Iterator<TentacleInstance> iterator = instances.values().iterator();
            while (iterator.hasNext()) {
                TentacleInstance instance = iterator.next();
                if (!instance.tick(level, baseProfile, grabProfile)) {
                    instance.releaseGrab(0);
                    instance.sendRemoval(level);
                    TentacleSavedData.get(level).remove(instance.id);
                    iterator.remove();
                }
            }
            interCollisionBodies.clear();
            for (TentacleInstance instance : instances.values()) {
                TentacleInterCollider.Body body = instance.interCollisionBody();
                if (body != null) {
                    interCollisionBodies.add(body);
                }
            }
            TentacleInterCollider.resolve(interCollisionBodies, level.getGameTime());
            for (TentacleInstance instance : instances.values()) {
                instance.sendStateIfDue(level, grabProfile);
            }
            if (level.getGameTime() % 5L == 0L) {
                persist(level);
            }
        }

        void persist(ServerLevel level) {
            List<TentacleSavedData.State> saved = new ArrayList<>(instances.size());
            for (TentacleInstance instance : instances.values()) {
                saved.add(instance.savedState());
            }
            TentacleSavedData.get(level).replace(nextInstanceId, saved);
        }

        void releaseTransientInteractions() {
            for (TentacleInstance instance : instances.values()) {
                instance.releaseGrab(0);
            }
        }

        int allocateId() {
            while (instances.containsKey(nextInstanceId)) {
                nextInstanceId++;
                if (nextInstanceId <= 0) {
                    nextInstanceId = 1;
                }
            }
            return nextInstanceId++;
        }

        boolean claimGrab(UUID playerId, int instanceId) {
            Integer owner = grabOwners.get(playerId);
            if (owner != null && owner != instanceId) {
                return false;
            }
            grabOwners.put(playerId, instanceId);
            return true;
        }

        void releaseGrab(UUID playerId, int instanceId) {
            if (playerId != null) {
                grabOwners.remove(playerId, instanceId);
            }
        }
    }

    private enum MotionMode {
        IDLE,
        TRACKING
    }

    public enum GrabEnableResult {
        UPDATED,
        UNKNOWN_INSTANCE,
        VOLUME_TOO_SMALL
    }

    private static final class TentacleInstance {
        private final LevelState levelState;
        private final int id;
        private final Vec3 rootAnchor;
        private final double volume;
        private final long visualSeed;
        private TentacleSavedData.State restoredState;
        private TentaclePhysicsProfile morphology;
        private TentaclePhysicsProfile sourceProfile;
        private TentacleChainSolver chain;
        private TentaclePhase phase = TentaclePhase.EMERGING;
        private MotionMode motionMode = MotionMode.IDLE;
        private UUID trackedPlayer;
        private int age;
        private int phaseTicks;
        private double extension;
        private double lengthScale = 1.0D;
        private Vec3 smoothedGoal;
        private List<Vec3> guidePath = List.of();
        private List<Vec3> plannedPath = List.of();
        private Vec3 plannedGoal;
        private boolean plannedPathIncomplete;
        private final TentacleTipController tipController = new TentacleTipController();
        private final TentacleTrail trail = new TentacleTrail();
        private boolean chainNeedsInitialization;
        private TentacleCollisionSpace collision;
        private List<Entity> collisionCandidates = List.of();
        private int nextEntityQueryTick;
        private int nextPathTick;
        private int nextTrailReleaseTick;
        private Vec3 progressAnchor;
        private int progressAnchorTick;
        private double progressAnchorGoalDistance = Double.NaN;
        private double progressAnchorPathDistance;
        private int recoveryClearanceStep;
        private int wrapOverBudgetTicks;
        private boolean grabEnabled;
        private TentacleGrabMode grabMode = TentacleGrabMode.THRASH;
        private UUID grabbedPlayer;
        private int grabTicks;
        private int grabModeTicks;
        private int grabCooldownTicks;
        private Vec3 previousGrabTip;
        private TentacleRagdollBody grabBody;
        private ServerPlayer grabbedReference;
        private boolean restoreFlight;
        private boolean holdNoGravityApplied;
        private TentacleCollisionSpace grabCollision;
        private int nextGrabCollisionTick;
        private Vec3 grabAnchor;
        private Vec3 lastRequestedGrabVelocity = Vec3.ZERO;
        private Vec3 lastCollisionSafeGrabVelocity = Vec3.ZERO;
        private double lastGrabVelocityClip;
        private double lastGrabCollisionReach;
        private double lastGrabGoalLead;
        private double lastGrabGoalCorrection;
        private double lastGrabGoalCollisionClip;
        private Vec3 previousTraceRootDirection;
        private Vec3 previousTraceTip;
        private Quaterniond previousTraceHeadOrientation;

        TentacleInstance(LevelState levelState, int id, Vec3 rootAnchor,
                double volume, long visualSeed) {
            this(levelState, id, rootAnchor, volume, visualSeed, null);
        }

        TentacleInstance(LevelState levelState, int id, Vec3 rootAnchor,
                double volume, long visualSeed, TentacleSavedData.State saved) {
            this.levelState = levelState;
            this.id = id;
            this.rootAnchor = rootAnchor;
            this.volume = volume;
            this.visualSeed = visualSeed;
            if (saved != null) {
                this.restoredState = saved;
                this.phase = saved.phase();
                this.motionMode = saved.tracking() ? MotionMode.TRACKING : MotionMode.IDLE;
                this.trackedPlayer = saved.trackedPlayer();
                this.age = saved.age();
                this.phaseTicks = saved.phaseTicks();
                this.extension = saved.extension();
                this.lengthScale = saved.lengthScale();
                this.grabEnabled = saved.grabEnabled();
                this.grabMode = saved.grabMode();
            }
        }

        boolean tick(ServerLevel level, TentaclePhysicsProfile baseProfile,
                TentacleGrabProfile grabProfile) {
            age++;
            phaseTicks++;
            if (grabCooldownTicks > 0) {
                grabCooldownTicks--;
            }
            updateMorphology(baseProfile);
            Vec3 root = rootPosition();
            if (chain == null || chain.pointCount() != morphology.segmentCount()) {
                chain = new TentacleChainSolver(morphology.segmentCount(), root);
                guidePath = List.of();
                plannedPath = List.of();
                plannedGoal = null;
                plannedPathIncomplete = false;
                tipController.reset(root);
                trail.reset(root);
                chainNeedsInitialization = true;
                if (restoredState != null && chain.restoreState(
                        restoredState.points(), restoredState.previous())) {
                    chainNeedsInitialization = false;
                }
                restoredState = null;
                collision = null;
                collisionCandidates = List.of();
                nextEntityQueryTick = 0;
                resetProgressWatch(root, true);
            }

            updatePhase();
            ServerPlayer target = trackedPlayer(level);
            boolean tracking = motionMode == MotionMode.TRACKING && target != null;
            boolean traceEnabled = target != null && PhysicsTraceLog.enabled(target);
            chain.setDiagnosticsEnabled(traceEnabled);
            boolean grabbingAllowed = grabEnabled && grabEligible(volume);
            if (grabbedPlayer != null && (!grabbingAllowed || target == null
                    || !grabbedPlayer.equals(target.getUUID())
                    || grabbedEntityWasReplaced(grabbedReference, target))) {
                releaseGrab(20);
            }
            boolean grabbing = grabbedPlayer != null && target != null;
            double grabSizeScale = TentacleEntityCollider.sizeScale(morphology, sourceProfile);
            boolean anchoredBehavior = grabbing && grabAnchor != null
                    && (grabMode == TentacleGrabMode.HOLD
                            || grabMode == TentacleGrabMode.WRAP);
            Vec3 behaviorCenter = anchoredBehavior
                    ? grabAnchor : target == null ? root : target.getBoundingBox().getCenter();
            Vec3 desiredGoal = grabbing
                    ? TentacleGrabController.behaviorGoal(grabMode, root, behaviorCenter,
                            morphology, grabProfile, grabModeTicks, visualSeed, grabSizeScale)
                    : tracking ? trackingGoal(target) : idleGoal(root);
            if (smoothedGoal == null) {
                smoothedGoal = desiredGoal;
            } else {
                double follow = motionMode == MotionMode.TRACKING
                        ? Mth.clamp(morphology.guideStrength() * 2.1D, 0.07D, 0.42D)
                        : Mth.clamp(morphology.guideStrength() * 1.15D, 0.025D, 0.18D);
                smoothedGoal = smoothedGoal.lerp(desiredGoal, follow);
            }
            if (grabbing && grabBody != null) {
                TentacleCollisionSpace tetherCollision = captureGrabCollision(
                        level, target, grabProfile, grabSizeScale);
                Vec3 currentGrip = target.getBoundingBox().getCenter()
                        .add(grabBody.pose().gripOffset());
                double maximumLead = TentacleGrabTether.maximumLead(
                        target.getBbWidth(), morphology.tipRadius(),
                        grabProfile.ragdollCollisionRadius(),
                        grabProfile.maximumSpeed(), grabSizeScale);
                TentacleGrabTether.Result tether = TentacleGrabTether.constrain(
                        currentGrip, smoothedGoal, tetherCollision, maximumLead,
                        Math.max(morphology.tipRadius(),
                                grabProfile.ragdollCollisionRadius()));
                smoothedGoal = tether.goal();
                lastGrabGoalLead = tether.requestedLead();
                lastGrabGoalCorrection = tether.correction();
                lastGrabGoalCollisionClip = tether.collisionCorrection();
            }
            double maximumReach = morphology.maximumLength();
            boolean activeTargeting = tracking || grabbing;
            double maximumLengthScale = activeTargeting
                    ? Math.max(1.0D, morphology.trackingMaximumStretch()) : 1.0D;
            double activeMaximumReach = maximumReach * maximumLengthScale;
            double unclampedGoalDistance = root.distanceTo(smoothedGoal);
            boolean reachLimited = unclampedGoalDistance > activeMaximumReach + 1.0E-6D;
            smoothedGoal = clampToLength(root, smoothedGoal,
                    activeMaximumReach);
            updatePath(level, root, smoothedGoal);
            advanceGuidePath(root);
            releaseTrailIfNeeded(level, root, activeMaximumReach);
            if (guidePath.size() < 2) {
                Vec3 fallback = root.add(0.0D,
                        Math.min(morphology.maximumLength() * 0.70D, morphology.idleHeight()), 0.0D);
                collision = captureRuntimeCollision(level, root, fallback, List.of(root, fallback));
                guidePath = List.of(root, collision.move(root, fallback, tipPathClearance()));
            }

            double requiredLength = Math.max(root.distanceTo(smoothedGoal),
                    TentacleChainSolver.pathLength(guidePath));
            double requestedLengthScale = activeTargeting
                    ? trackingLengthScale(maximumReach, requiredLength, maximumLengthScale)
                    : 1.0D;
            // Retraction is a pursuit-only tool. Once captured, keep the previous
            // full-body rest length so HOLD/WRAP/THRASH presentation is unchanged.
            double targetLengthScale = grabbing
                    ? Math.max(1.0D, requestedLengthScale)
                    : requestedLengthScale;
            lengthScale = approachLengthScale(lengthScale, targetLengthScale,
                    morphology.lengthResponse(), maximumLengthScale);
            if (chainNeedsInitialization && guidePath.size() >= 2) {
                double initialLength = Math.min(TentacleChainSolver.pathLength(guidePath),
                        morphology.maximumLength() * lengthScale * Math.max(0.02D, extension));
                chain.initializeAlongPath(morphology, guidePath, collision, initialLength);
                chainNeedsInitialization = false;
            }

            double actuatorSpeed = activeTipSpeed(tracking);
            double attachmentProgress = grabbing
                    ? smootherStep((grabTicks + 1.0D) / Math.max(1, grabProfile.attachTicks()))
                    : 0.0D;
            Vec3 terminalDirection = grabbing && grabMode == TentacleGrabMode.HOLD
                    ? new Vec3(0.0D, -attachmentProgress, 0.0D) : Vec3.ZERO;
            // Pursuit is an actively driven state: the guide supplies the intended
            // direction and the chain should not sag against it. Grab modes retain
            // their previous gravity behavior (HOLD suspended, WRAP/THRASH dynamic).
            double chainGravityScale = (tracking && !grabbing)
                    || (grabbing && grabMode == TentacleGrabMode.HOLD) ? 0.0D : 1.0D;
            double trackingGuideScale = tracking && !grabbing ? 1.0D : 0.0D;
            chain.step(morphology, root, extension, lengthScale,
                    guidePath, actuatorSpeed, collision, age, visualSeed,
                    terminalDirection, chainGravityScale, trackingGuideScale);
            if (chain.stepRejected()) {
                synchronizeControllerToPhysicalChain(root);
            }
            updateStuckRecovery(tracking, desiredGoal);
            if (age >= nextEntityQueryTick) {
                collisionCandidates = TentacleEntityCollider.queryCandidates(
                        level, chain, morphology, extension);
                nextEntityQueryTick = staggeredTick(
                        morphology.entityQueryInterval(), 0x31);
            }
            if (grabbingAllowed && !grabbing && tracking && grabCooldownTicks <= 0
                    && extension >= 0.90D && tryAcquireGrab(target, grabProfile)) {
                grabbing = true;
            }
            ServerPlayer heldPlayer = grabbing ? target : null;
            List<Vec3> beforeEntityCollision = traceEnabled ? chain.snapshot() : List.of();
            TentacleEntityCollider.collide(
                    collisionCandidates, chain, morphology, sourceProfile, extension, heldPlayer);
            double entityCollisionCorrection = traceEnabled
                    ? totalPointDisplacement(beforeEntityCollision, chain.snapshot()) : 0.0D;
            if (heldPlayer != null) {
                applyGrab(heldPlayer, grabProfile);
            }
            if (traceEnabled) {
                traceMotion(target, root, desiredGoal, activeMaximumReach,
                        unclampedGoalDistance, reachLimited, entityCollisionCorrection);
            }
            return phase != TentaclePhase.RETRACTING || extension > 0.001D;
        }

        private TentacleInterCollider.Body interCollisionBody() {
            return chain == null || morphology == null || collision == null ? null
                    : new TentacleInterCollider.Body(
                            id, chain, morphology, extension, collision, grabbedPlayer != null);
        }

        private void sendStateIfDue(ServerLevel level, TentacleGrabProfile grabProfile) {
            if (age % morphology.syncIntervalTicks() == 0 || phaseTicks == 1) {
                sendState(level, rootPosition(), grabProfile);
            }
        }

        private void traceMotion(ServerPlayer target, Vec3 root, Vec3 desiredGoal,
                double activeMaximumReach, double unclampedGoalDistance,
                boolean reachLimited, double entityCollisionCorrection) {
            TentacleChainSolver.StepDiagnostics diagnostics = chain.diagnostics();
            Vec3 tip = chain.point(chain.pointCount() - 1);
            Vec3 rootDirection = chain.pointCount() < 2
                    ? Vec3.ZERO : chain.point(1).subtract(root);
            double rootTurn = angleDegrees(previousTraceRootDirection, rootDirection);
            double tipStep = previousTraceTip == null ? 0.0D : tip.distanceTo(previousTraceTip);
            previousTraceRootDirection = rootDirection.lengthSqr() <= 1.0E-12D
                    ? previousTraceRootDirection : rootDirection.normalize();
            previousTraceTip = tip;

            TentacleRagdollPose ragdoll = grabBody == null ? null : grabBody.pose();
            double headTurn = 0.0D;
            if (ragdoll != null) {
                headTurn = quaternionAngleDegrees(
                        previousTraceHeadOrientation, ragdoll.headOrientation());
                previousTraceHeadOrientation = new Quaterniond(ragdoll.headOrientation());
            } else {
                previousTraceHeadOrientation = null;
            }

            boolean anomaly = diagnostics.stepRejected()
                    || diagnostics.maximumStretchRatio() > 1.05D
                    || rootTurn > 12.0D
                    || headTurn > 35.0D
                    || lastGrabVelocityClip > 0.01D
                    || lastGrabGoalCollisionClip > 0.01D
                    || entityCollisionCorrection > 0.02D
                    || diagnostics.terrainCorrection() > 0.10D;
            if (!anomaly && age % 5 != 0) {
                return;
            }

            double plannedLength = TentacleChainSolver.pathLength(plannedPath);
            double guideLength = TentacleChainSolver.pathLength(guidePath);
            Vec3 controller = tipController.initialized() ? tipController.position() : tip;
            double goalError = tip.distanceTo(desiredGoal);
            double controllerLead = controller.distanceTo(tip);
            Vec3 controllerVelocity = tipController.velocity();
            Vec3 targetVelocity = target.getDeltaMovement();
            double watchProgress = progressAnchor == null
                    ? 0.0D : progressAnchor.distanceTo(tip);
            double watchGoalImprovement = progressAnchor == null
                    || !Double.isFinite(progressAnchorGoalDistance)
                    ? 0.0D : progressAnchorGoalDistance - tip.distanceTo(desiredGoal);
            String poseText = ragdoll == null ? "ragdoll=none"
                    : String.format(Locale.ROOT,
                            "ragdoll=headTurn:%.3f,headQ:(%.5f,%.5f,%.5f,%.5f),headOffset:(%.4f,%.4f,%.4f) "
                                    + "grabMotion=requested:(%.5f,%.5f,%.5f),safe:(%.5f,%.5f,%.5f),clip:%.5f,captureReach:%.3f "
                                    + "grabTether=lead:%.5f,correction:%.5f,collisionClip:%.5f",
                            headTurn,
                            ragdoll.headOrientation().x, ragdoll.headOrientation().y,
                            ragdoll.headOrientation().z, ragdoll.headOrientation().w,
                            ragdoll.headOffset().x, ragdoll.headOffset().y,
                            ragdoll.headOffset().z,
                            lastRequestedGrabVelocity.x, lastRequestedGrabVelocity.y,
                            lastRequestedGrabVelocity.z,
                            lastCollisionSafeGrabVelocity.x, lastCollisionSafeGrabVelocity.y,
                            lastCollisionSafeGrabVelocity.z,
                            lastGrabVelocityClip, lastGrabCollisionReach,
                            lastGrabGoalLead, lastGrabGoalCorrection,
                            lastGrabGoalCollisionClip);
            PhysicsTraceLog.traceTentacle(target, String.format(Locale.ROOT,
                    "id=%d phase=%s mode=%s grab=%s anomaly=%s "
                            + "reach=goal:%.4f,raw:%.4f,max:%.4f,limited:%s,error:%.4f,lengthScale:%.4f "
                            + "path=planned:%.4f,guide:%.4f,trail:%.4f,incomplete:%s,plannedPts:%d,trailPts:%d,recovery:%d "
                            + "tip=step:%.5f,controllerLead:%.5f,controllerSpeed:%.5f,"
                            + "controllerV:(%.5f,%.5f,%.5f),controllerPath:%.4f,rootTurn:%.3f "
                            + "watch=age:%d,progress:%.5f,goalImprovement:%.5f "
                            + "target=yaw:%.3f,pitch:%.3f,v:(%.5f,%.5f,%.5f),candidates:%d "
                            + "solver=arc:%.4f,rest:%.5f,maxStretch:%.5f,guideAct:%.5f,tipAct:%.5f,"
                            + "rootAct:%.5f,"
                            + "lengthCorr:%.5f,bendGuard:%.5f,curveCorr:%.5f,selfCorr:%.5f,selfHits:%d,"
                            + "terrainCorr:%.5f,terrainHits:%d,stepLimit:%.5f,entityCorr:%.5f,"
                            + "slackCurve:%.5f,muscleScale:%.4f,rejected:%s %s",
                    id, phase, motionMode, grabbedPlayer != null, anomaly,
                    root.distanceTo(desiredGoal), unclampedGoalDistance,
                    activeMaximumReach, reachLimited, goalError, lengthScale,
                    plannedLength, guideLength, trail.length(), plannedPathIncomplete,
                    plannedPath.size(), trail.size(), recoveryClearanceStep,
                    tipStep, controllerLead, tipController.speed(),
                    controllerVelocity.x, controllerVelocity.y, controllerVelocity.z,
                    tipController.distance(), rootTurn,
                    age - progressAnchorTick, watchProgress, watchGoalImprovement,
                    target.getYRot(), target.getXRot(),
                    targetVelocity.x, targetVelocity.y, targetVelocity.z,
                    collisionCandidates.size(),
                    diagnostics.arcLength(), diagnostics.restLength(),
                    diagnostics.maximumStretchRatio(),
                    diagnostics.guideCorrection(), diagnostics.tipOrientationCorrection(),
                    diagnostics.rootOrientationCorrection(),
                    diagnostics.lengthCorrection(), diagnostics.bendCorrection(),
                    diagnostics.curvatureCorrection(), diagnostics.selfCollisionCorrection(),
                    diagnostics.selfCollisionContacts(),
                    diagnostics.terrainCorrection(), diagnostics.terrainContacts(),
                    diagnostics.stepLimitCorrection(), entityCollisionCorrection,
                    diagnostics.curveAmplitude(), diagnostics.muscleScale(),
                    diagnostics.stepRejected(), poseText));
        }

        private static double totalPointDisplacement(List<Vec3> before, List<Vec3> after) {
            if (before.size() != after.size()) {
                return Double.NaN;
            }
            double total = 0.0D;
            for (int index = 0; index < before.size(); index++) {
                total += before.get(index).distanceTo(after.get(index));
            }
            return total;
        }

        private static double angleDegrees(Vec3 first, Vec3 second) {
            if (first == null || second == null
                    || first.lengthSqr() <= 1.0E-12D || second.lengthSqr() <= 1.0E-12D) {
                return 0.0D;
            }
            double cosine = Mth.clamp(first.normalize().dot(second.normalize()), -1.0D, 1.0D);
            return Math.toDegrees(Math.acos(cosine));
        }

        private static double quaternionAngleDegrees(Quaterniond first, Quaterniond second) {
            if (first == null || second == null) {
                return 0.0D;
            }
            double dot = Math.abs(first.dot(second));
            return Math.toDegrees(2.0D * Math.acos(Mth.clamp(dot, 0.0D, 1.0D)));
        }

        private void updateMorphology(TentaclePhysicsProfile baseProfile) {
            if (morphology == null || !baseProfile.equals(sourceProfile)) {
                sourceProfile = baseProfile;
                morphology = baseProfile.scaledForVolume(volume, visualSeed);
            }
        }

        private void updatePhase() {
            switch (phase) {
                case EMERGING -> {
                    extension = Math.min(1.0D, extension + morphology.emergeSpeed());
                    if (extension >= 1.0D) {
                        transition(TentaclePhase.IDLE);
                    }
                }
                case IDLE -> extension = 1.0D;
                case RETRACTING -> extension = Math.max(0.0D, extension - morphology.retractSpeed());
            }
        }

        private ServerPlayer trackedPlayer(ServerLevel level) {
            if (trackedPlayer == null) {
                return null;
            }
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(trackedPlayer);
            if (target == null || target.serverLevel() != level || !target.isAlive()) {
                setIdle();
                return null;
            }
            return target;
        }

        private boolean tryAcquireGrab(ServerPlayer target, TentacleGrabProfile profile) {
            if (!grabEligible(volume)) {
                return false;
            }
            TentacleGrabTarget selectedTarget = TentacleGrabTarget.WHOLE_BODY;
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 targetPoint = targetCenter.add(TentacleRagdollBody.targetOffset(
                    target.getBbHeight(), target.getBbWidth(), target.getYRot(), selectedTarget));
            Vec3 tip = chain.point(chain.pointCount() - 1);
            double targetRadius = TentacleRagdollBody.targetRadius(
                    target.getBbWidth(), selectedTarget);
            TentacleEntityCollider.GrabContact contact =
                    TentacleEntityCollider.grabTargetContact(
                            chain, morphology, extension, targetPoint, targetRadius,
                            profile.tipSegments(),
                            profile.contactPadding() * profile.targetPaddingScale());
            if (contact == null) {
                return false;
            }
            double gripClearance = Math.max(
                    morphology.collisionSlop(),
                    Math.min(morphology.tipRadius(), targetRadius) * 0.20D);
            TentacleCollisionSpace contactCollision = captureGrabContactCollision(
                    target.serverLevel(), contact, targetRadius);
            if (!TentacleGrabTether.contactClear(
                    contact.tentaclePoint(), contact.entityPoint(),
                    contactCollision, gripClearance)) {
                return false;
            }
            if (!levelState.claimGrab(target.getUUID(), id)) {
                return false;
            }
            grabbedPlayer = target.getUUID();
            grabTicks = 0;
            grabModeTicks = 0;
            previousGrabTip = tip;
            // HOLD must lift from the actual contact point. Using the entity center here
            // makes the tip jump by roughly half a player height on the next tick.
            grabAnchor = tip;
            grabbedReference = target;
            restoreFlight = target.getAbilities().flying;
            holdNoGravityApplied = false;
            grabCollision = null;
            nextGrabCollisionTick = 0;
            suspendFlight(target);
            grabBody = new TentacleRagdollBody(target, targetPoint,
                    tip, morphology.tipRadius(), profile,
                    selectedTarget, false);
            beginGrabNavigation(tip);
            return true;
        }

        private void applyGrab(ServerPlayer target, TentacleGrabProfile profile) {
            Vec3 tip = chain.point(chain.pointCount() - 1);
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            double sizeScale = TentacleEntityCollider.sizeScale(morphology, sourceProfile);
            double holdScale = Math.pow(sizeScale, 0.45D);
            boolean durationExpired = profile.maximumTicks() > 0
                    && grabTicks >= Math.round(profile.maximumTicks()
                            * Mth.clamp(holdScale, 0.65D, 2.0D));
            if (durationExpired
                    || tip.distanceTo(targetCenter) > profile.breakDistance() * holdScale) {
                releaseGrab(40);
                return;
            }
            Vec3 tipVelocity = previousGrabTip == null ? Vec3.ZERO : tip.subtract(previousGrabTip);
            if (grabBody == null) {
                Vec3 fallbackContact = targetCenter.add(0.0D,
                        target.getBbHeight() * 0.35D, 0.0D);
                grabBody = new TentacleRagdollBody(target, fallbackContact,
                        tip, morphology.tipRadius(), profile,
                        TentacleGrabTarget.WHOLE_BODY, false);
            }
            suspendFlight(target);
            updateGrabGravity(target);
            Vec3 tipDirection = tip.subtract(chain.point(chain.pointCount() - 2));
            double attachmentProgress = smootherStep(
                    (grabTicks + 1.0D) / Math.max(1, profile.attachTicks()));
            double controlScale = (restoreFlight ? profile.flightControlScale() : 1.0D)
                    * (grabMode == TentacleGrabMode.HOLD ? profile.holdControlScale() : 1.0D)
                    * (0.25D + attachmentProgress * 0.75D);
            Vec3 currentVelocity = target.getDeltaMovement();
            if (grabMode == TentacleGrabMode.HOLD && currentVelocity.y < 0.0D) {
                currentVelocity = new Vec3(currentVelocity.x, 0.0D, currentVelocity.z);
            }
            TentacleCollisionSpace bodyCollision = captureGrabCollision(
                    target.serverLevel(), target, profile, sizeScale);
            TentacleRagdollBody.Update update = grabBody.update(
                    currentVelocity, targetCenter, tip, tipVelocity,
                    bodyCollision, profile, sizeScale, tipDirection,
                    controlScale, attachmentProgress, grabMode);
            Vec3 collisionSafeVelocity = TentacleHeldPlayerCollision.constrainVelocity(
                    target.getBoundingBox(), update.velocity(), bodyCollision,
                    profile.ragdollCollisionRadius());
            lastRequestedGrabVelocity = update.velocity();
            lastCollisionSafeGrabVelocity = collisionSafeVelocity;
            lastGrabVelocityClip = update.velocity().distanceTo(collisionSafeVelocity);
            target.setDeltaMovement(collisionSafeVelocity);
            target.hasImpulse = true;
            target.hurtMarked = true;
            target.resetFallDistance();
            previousGrabTip = tip;
            grabTicks++;
            grabModeTicks++;
        }

        private void releaseGrab(int cooldownTicks) {
            restoreGrabGravity();
            if (restoreFlight && grabbedReference != null && !grabbedReference.isRemoved()
                    && grabbedReference.mayFly()) {
                grabbedReference.getAbilities().flying = true;
                grabbedReference.onUpdateAbilities();
            }
            levelState.releaseGrab(grabbedPlayer, id);
            grabbedPlayer = null;
            grabTicks = 0;
            grabModeTicks = 0;
            previousGrabTip = null;
            grabBody = null;
            grabbedReference = null;
            restoreFlight = false;
            holdNoGravityApplied = false;
            grabCollision = null;
            nextGrabCollisionTick = 0;
            grabAnchor = null;
            lastRequestedGrabVelocity = Vec3.ZERO;
            lastCollisionSafeGrabVelocity = Vec3.ZERO;
            lastGrabVelocityClip = 0.0D;
            lastGrabCollisionReach = 0.0D;
            lastGrabGoalLead = 0.0D;
            lastGrabGoalCorrection = 0.0D;
            lastGrabGoalCollisionClip = 0.0D;
            grabCooldownTicks = Math.max(grabCooldownTicks, cooldownTicks);
        }

        private static void suspendFlight(ServerPlayer player) {
            if (!player.getAbilities().flying) {
                return;
            }
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        private void updateGrabGravity(ServerPlayer player) {
            if (grabMode == TentacleGrabMode.HOLD) {
                PlayerGravityControl.acquire(player, PlayerGravityControl.Owner.TENTACLE_HOLD);
                holdNoGravityApplied = true;
            } else if (holdNoGravityApplied) {
                PlayerGravityControl.release(player, PlayerGravityControl.Owner.TENTACLE_HOLD);
                holdNoGravityApplied = false;
            }
        }

        private void restoreGrabGravity() {
            if (holdNoGravityApplied && grabbedReference != null) {
                PlayerGravityControl.release(
                        grabbedReference, PlayerGravityControl.Owner.TENTACLE_HOLD);
            }
        }

        private Vec3 trackingGoal(ServerPlayer target) {
            return target.getBoundingBox().getCenter().add(TentacleRagdollBody.targetOffset(
                    target.getBbHeight(), target.getBbWidth(), target.getYRot(),
                    TentacleGrabTarget.WHOLE_BODY));
        }

        private void beginGrabNavigation(Vec3 physicalTip) {
            // A successful contact changes the controller from pursuit to an anchored
            // behavior. Do not let a blocked pursuit plan keep steering HOLD/WRAP.
            // The swept trail remains authoritative so an obstacle route is preserved.
            smoothedGoal = physicalTip;
            plannedPath = List.of();
            plannedGoal = null;
            plannedPathIncomplete = true;
            nextPathTick = 0;
            tipController.reset(physicalTip);
            guidePath = trail.path();
            collision = null;
            resetProgressWatch(physicalTip, true);
        }

        private Vec3 idleGoal(Vec3 root) {
            int decisionTicks = Math.max(1, morphology.idleDecisionTicks());
            long decision = Math.floorDiv(age, decisionTicks);
            double cycle = Math.floorMod(age, decisionTicks) / (double) decisionTicks;
            double movingRatio = Math.max(0.05D, 1.0D - morphology.idleRestRatio());
            double progress = smootherStep(Math.min(1.0D, cycle / movingRatio));
            double angle = interpolateAngle(
                    sample(decision, 0x1F123BB5L) * Math.PI * 2.0D,
                    sample(decision + 1L, 0x1F123BB5L) * Math.PI * 2.0D,
                    progress);
            double reachRatio = Mth.lerp(progress,
                    idleReachRatio(decision), idleReachRatio(decision + 1L));
            double reach = morphology.idleReach() * reachRatio;
            double liftVariation = Mth.lerp(progress,
                    0.35D + sample(decision, 0x6D2B79F5L) * 0.65D,
                    0.35D + sample(decision + 1L, 0x6D2B79F5L) * 0.65D);
            double lift = morphology.idleHeight() * reachRatio * liftVariation;
            double breathe = Math.sin(age * morphology.idleSwaySpeed()
                    + (visualSeed & 0xFFFFL) * 0.0007D) * morphology.idleSway()
                    * (1.0D - morphology.idleRestRatio());
            Vec3 forward = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
            return root.add(forward.scale(reach)).add(side.scale(breathe)).add(0.0D, lift, 0.0D);
        }

        private double idleReachRatio(long decision) {
            double distributed = Math.pow(sample(decision, 0x42A57C19L), 0.62D);
            return Mth.lerp(distributed,
                    morphology.idleMinimumReach(), morphology.idleMaximumReach());
        }

        private static double interpolateAngle(double from, double to, double amount) {
            double difference = Math.atan2(Math.sin(to - from), Math.cos(to - from));
            return from + difference * amount;
        }

        private static double smootherStep(double value) {
            double clamped = Mth.clamp(value, 0.0D, 1.0D);
            return clamped * clamped * clamped * (clamped * (clamped * 6.0D - 15.0D) + 10.0D);
        }

        private double sample(long decision, long salt) {
            long value = visualSeed ^ salt ^ (decision * 0x9E3779B97F4A7C15L);
            value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
            value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
            value ^= value >>> 31;
            return (value >>> 11) * 0x1.0p-53;
        }

        private void updatePath(ServerLevel level, Vec3 root, Vec3 goal) {
            Vec3 currentTip = tipController.initialized()
                    ? tipController.position() : chain.point(chain.pointCount() - 1);
            if (collision == null || plannedPath.size() < 2) {
                if (collision != null && age < nextPathTick) {
                    return;
                }
                collision = captureSearchCollision(
                        level, root, currentTip, goal, List.of(currentTip, goal));
                RoutePlan initial = findPathOrReachablePrefix(currentTip, goal, collision);
                installPath(initial.path(), currentTip, goal, initial.complete(), false);
                nextPathTick = staggeredTick(morphology.pathReplanTicks(), 0x53);
                return;
            }

            // Keep the unobstructed final leg attached to a moving target between A* refreshes.
            // This uses the cached collision corridor and does not scan world geometry every tick.
            if (motionMode == MotionMode.TRACKING && !plannedPathIncomplete && plannedGoal != null
                    && plannedGoal.distanceToSqr(goal) > 1.0E-8D) {
                Vec3 penultimate = plannedPath.get(plannedPath.size() - 2);
                double quickRetargetRange = Math.max(morphology.pathGoalTolerance(),
                        morphology.trackingTipAdvanceSpeed() * morphology.pathReplanTicks() * 1.5D);
                if (plannedGoal.distanceToSqr(goal) <= quickRetargetRange * quickRetargetRange
                        && collision.clear(penultimate, goal, tipPathClearance())) {
                    installPath(replaceLast(plannedPath, goal), currentTip, goal, true, false);
                }
            }
            if (age < nextPathTick) {
                return;
            }

            TentacleCollisionSpace refreshed = captureRuntimeCollision(level, root, goal, plannedPath);
            if (trail.size() >= 2 && firstBlockedSegmentAhead(
                    trail.path(), refreshed, tipPathClearance(), 0.0D) >= 1) {
                TentacleCollisionSpace search = captureSearchCollision(
                        level, root, root, currentTip, trail.path());
                List<Vec3> replacement = findPath(root, currentTip, search);
                if (replacement.size() >= 2 && trail.resetToBounded(
                        replacement, root, currentTip, search, tipPathClearance(),
                        morphology.trailMaximumPoints())) {
                    guidePath = trail.path();
                    refreshed = search;
                }
            }
            int blockedSegment = firstBlockedSegmentAhead(
                    plannedPath, refreshed, tipPathClearance(), tipController.distance());
            double goalMovement = plannedGoal == null
                    ? Double.POSITIVE_INFINITY : plannedGoal.distanceTo(goal);
            boolean targetMoved = goalMovement > morphology.pathGoalTolerance();
            boolean reachedOldGoal = tipController.distance()
                    >= TentacleChainSolver.pathLength(plannedPath) - morphology.tipRadius();
            if (blockedSegment >= 1 || plannedPathIncomplete || targetMoved || reachedOldGoal) {
                refreshed = captureSearchCollision(level, root, currentTip, goal, plannedPath);
                RoutePlan replacement = findPathOrReachablePrefix(currentTip, goal, refreshed);
                if (replacement.path().size() >= 2) {
                    installPath(replacement.path(), currentTip, goal, replacement.complete(), false);
                } else {
                    plannedPathIncomplete = true;
                }
            }
            collision = refreshed;
            nextPathTick = staggeredTick(morphology.pathReplanTicks(), 0x71);
        }

        private void advanceGuidePath(Vec3 root) {
            if (plannedPath.size() < 2 || collision == null) {
                return;
            }
            if (phase != TentaclePhase.RETRACTING) {
                double advanceSpeed = activeTipSpeed(motionMode == MotionMode.TRACKING);
                double acceleration = phase == TentaclePhase.EMERGING
                        ? Math.max(morphology.tipAcceleration(), advanceSpeed * 0.35D)
                        : morphology.tipAcceleration();
                tipController.advance(plannedPath, advanceSpeed, acceleration,
                        morphology.tipLookaheadDistance(), chain.point(chain.pointCount() - 1),
                        morphology.tipMaximumLeadDistance(), collision, tipPathClearance());
            }
            boolean recorded = trail.record(root, tipController.position(), collision,
                    tipPathClearance(), morphology.trailSampleDistance(),
                    morphology.trailUnwrapTicks(), morphology.trailMaximumPoints());
            if (!recorded) {
                resetNavigationFromPhysicalChain(root);
                return;
            }
            rebuildGuidePath();
        }

        private void resetNavigationFromPhysicalChain(Vec3 root) {
            Vec3 physicalTip = chain.point(chain.pointCount() - 1);
            tipController.reset(physicalTip);
            trail.resetTo(chain.snapshot(), root, physicalTip);
            guidePath = trail.path();
            plannedPath = List.of();
            plannedGoal = null;
            plannedPathIncomplete = true;
            nextPathTick = 0;
            resetProgressWatch(physicalTip, false);
        }

        /**
         * Keeps the wrap memory affordable, and gives up when it stops being affordable.
         *
         * <p>The trail is the tentacle's only memory of how it got around an obstacle, and nothing
         * bounds its <em>length</em> — {@code trailMaximumPoints} bounds only its point count. A
         * player circling a pillar therefore grows it past what the body can physically span. At
         * that point {@link TentacleGuidePath#compose} falls back to
         * {@link TentacleChainSolver#trimPath}, which keeps the root end and discards the goal end,
         * so the tentacle follows a route that stops in mid-air: it holds a wrap it cannot pay for
         * and stops pursuing, while every other signal still reports a valid route. That reads as
         * the tentacle losing interest for no reason.
         *
         * <p>So an over-budget wrap is treated as a decision point rather than a steady state.
         * First try to buy it back by shortcutting to the shortest collision-free route, which
         * preserves detours that are still genuinely required — A* only returns a shorter path when
         * the wrap has actual slack in it. If that keeps failing, the wrap really is unaffordable:
         * abandon it and re-approach instead of hanging on to a route the body cannot follow.
         */
        private void releaseTrailIfNeeded(ServerLevel level, Vec3 root, double maximumReach) {
            double releaseLength = maximumReach * morphology.trailReleaseRatio();
            boolean overBudget = trail.length() > maximumReach;
            if (!overBudget) {
                wrapOverBudgetTicks = 0;
            }
            if (age < nextTrailReleaseTick || trail.length() <= releaseLength) {
                return;
            }
            Vec3 tip = tipController.position();
            TentacleCollisionSpace search = captureSearchCollision(
                    level, root, root, tip, trail.path());
            List<Vec3> shortest = findPath(root, tip, search);
            double shortestLength = TentacleChainSolver.pathLength(shortest);
            boolean shortcut = !shortest.isEmpty() && shortestLength < trail.length()
                    && shortestLength <= maximumReach
                    && trail.resetToBounded(shortest, root, tip, search,
                            tipPathClearance(), morphology.trailMaximumPoints());
            if (shortcut) {
                collision = search;
                rebuildGuidePath();
            }
            if (trail.length() > maximumReach) {
                wrapOverBudgetTicks += Math.max(1, morphology.pathReplanTicks());
                if (wrapOverBudgetTicks >= WRAP_ABANDON_TICKS) {
                    abandonWrap(root);
                }
            } else {
                wrapOverBudgetTicks = 0;
            }
            nextTrailReleaseTick = staggeredTick(
                    Math.max(2, morphology.pathReplanTicks() * 2), 0x19);
        }

        /**
         * Drops an unaffordable wrap and starts the approach over.
         *
         * <p>This is the "it stopped being worth it" branch: the committed route around the
         * obstacle costs more length than the body has, and no shorter route exists. Rather than
         * stay stretched around geometry it cannot follow, the tentacle lets the wrap go and
         * re-plans from where it actually is. The cooldown stops it from immediately re-committing
         * to the same impossible route on the next replan.
         */
        private void abandonWrap(Vec3 root) {
            Vec3 physicalTip = chain.point(chain.pointCount() - 1);
            tipController.reset(physicalTip);
            trail.resetTo(List.of(root, physicalTip), root, physicalTip);
            guidePath = trail.path();
            requestNavigationReplan();
            resetProgressWatch(physicalTip, false);
            wrapOverBudgetTicks = 0;
            nextTrailReleaseTick = age + WRAP_ABANDON_COOLDOWN_TICKS;
        }

        private double activeTipSpeed(boolean tracking) {
            double regular = tracking
                    ? morphology.trackingTipAdvanceSpeed() : morphology.tipAdvanceSpeed();
            return phase == TentaclePhase.EMERGING
                    ? Math.max(regular, morphology.maximumLength() * morphology.emergeSpeed())
                    : regular;
        }

        private List<Vec3> findPath(Vec3 start, Vec3 goal, TentacleCollisionSpace space) {
            return TentaclePathfinder.find(start, goal, space,
                    activePathClearance(), morphology.pathCellSize(), activePathMargin(),
                    morphology.pathMaximumNodes());
        }

        private double activePathClearance() {
            return recoveryPathClearance(chain, morphology, recoveryClearanceStep);
        }

        private double activePathMargin() {
            double recoveryExpansion = recoveryClearanceStep
                    * Math.max(0.50D, morphology.pathCellSize());
            return morphology.pathMargin() + recoveryExpansion;
        }

        private void updateStuckRecovery(boolean tracking, Vec3 goal) {
            Vec3 physicalTip = chain.point(chain.pointCount() - 1);
            if (!tracking || physicalTip.distanceTo(goal) <= morphology.pathGoalTolerance()) {
                resetProgressWatch(physicalTip, true);
                return;
            }
            if (progressAnchor == null) {
                resetProgressWatch(physicalTip, false);
                return;
            }
            if (age - progressAnchorTick < morphology.stuckReplanTicks()) {
                return;
            }
            double progress = progressAnchor.distanceTo(physicalTip);
            double goalDistance = physicalTip.distanceTo(goal);
            double routeDistance = plannedPath.size() < 2 ? 0.0D
                    : TentacleTipController.closestDistanceAlong(plannedPath, physicalTip);
            double watchedGoalDistance = Double.isFinite(progressAnchorGoalDistance)
                    ? progressAnchorGoalDistance : progressAnchor.distanceTo(goal);
            double goalProgress = watchedGoalDistance - goalDistance;
            double routeProgress = routeDistance - progressAnchorPathDistance;
            progressAnchor = physicalTip;
            progressAnchorTick = age;
            progressAnchorGoalDistance = goalDistance;
            progressAnchorPathDistance = routeDistance;
            if (goalProgress >= morphology.stuckProgressDistance() * 0.50D
                    || routeProgress >= morphology.stuckProgressDistance()
                    || (plannedPath.size() < 2
                            && progress >= morphology.stuckProgressDistance())) {
                return;
            }

            recoveryClearanceStep = Math.min(morphology.stuckClearanceSteps(),
                    recoveryClearanceStep + 1);
            requestNavigationReplan();
        }

        private void synchronizeControllerToPhysicalChain(Vec3 root) {
            Vec3 physicalTip = chain.point(chain.pointCount() - 1);
            tipController.reset(physicalTip);
            if (plannedPath.size() >= 2) {
                tipController.rebind(plannedPath, physicalTip, false);
            }
            trail.resetTo(chain.snapshot(), root, physicalTip);
            rebuildGuidePath();
        }

        private void requestNavigationReplan() {
            // Preserve the authoritative swept trail and controller position.
            // Replacing them with an already coiled physical chain makes the
            // recovery itself a steering impulse and repeats at max clearance.
            plannedPath = List.of();
            plannedGoal = null;
            plannedPathIncomplete = true;
            nextPathTick = 0;
        }

        private void rebuildGuidePath() {
            guidePath = TentacleGuidePath.compose(
                    trail.path(), plannedPath, tipController.position(),
                    tipController.distance(), collision, tipPathClearance(),
                    morphology.maximumLength() * (motionMode == MotionMode.TRACKING
                            ? Math.max(1.0D, morphology.trackingMaximumStretch()) : 1.0D));
        }

        private void resetProgressWatch(Vec3 point, boolean resetClearance) {
            progressAnchor = point;
            progressAnchorTick = age;
            progressAnchorGoalDistance = Double.NaN;
            progressAnchorPathDistance = plannedPath.size() < 2 ? 0.0D
                    : TentacleTipController.closestDistanceAlong(plannedPath, point);
            if (resetClearance) {
                recoveryClearanceStep = 0;
            }
        }

        private RoutePlan findPathOrReachablePrefix(Vec3 start, Vec3 goal,
                TentacleCollisionSpace space) {
            List<Vec3> path = findPath(start, goal, space);
            if (!path.isEmpty()) {
                return new RoutePlan(path, true);
            }
            Vec3 reachable = space.move(start, goal, activePathClearance());
            return new RoutePlan(reachable.distanceToSqr(start) > 1.0E-6D
                    ? List.of(start, reachable) : List.of(), false);
        }

        private void installPath(List<Vec3> candidate, Vec3 currentTip,
                Vec3 goal, boolean complete, boolean snapController) {
            if (candidate.size() < 2) {
                return;
            }
            plannedPath = List.copyOf(candidate);
            plannedGoal = goal;
            plannedPathIncomplete = !complete
                    || plannedPath.getLast().distanceToSqr(goal) > 1.0E-6D;
            tipController.rebind(plannedPath, currentTip, snapController);
            resetProgressWatch(currentTip, false);
        }

        private double tipPathClearance() {
            return morphology.tipPathClearance();
        }

        private TentacleCollisionSpace captureRuntimeCollision(ServerLevel level, Vec3 root,
                Vec3 goal, List<Vec3> path) {
            List<List<Vec3>> corridors = new ArrayList<>(4);
            if (chain != null) {
                corridors.add(chain.snapshot());
            }
            if (trail.size() >= 2) {
                corridors.add(trail.path());
            }
            corridors.add(path.isEmpty() ? List.of(root, goal) : path);
            if (!path.isEmpty() && path.getLast().distanceToSqr(goal) > 1.0E-8D) {
                corridors.add(List.of(path.getLast(), goal));
            }
            double padding = morphology.rootRadius() + morphology.pathClearance()
                    + morphology.collisionSlop() * 2.0D;
            TentacleCollisionSpace world = AabbTentacleCollisionSpace.captureAlongPaths(
                    level, corridors, padding, morphology.collisionSlop(), rootAnchor,
                    morphology.collisionMaximumBlockSamples());
            return SableTentacleCollisionSpace.capture(level, corridors, padding,
                    morphology.collisionMaximumBlockSamples(), world);
        }

        private TentacleCollisionSpace captureGrabCollision(ServerLevel level,
                ServerPlayer target, TentacleGrabProfile profile, double sizeScale) {
            if (grabCollision != null && age < nextGrabCollisionTick) {
                return grabCollision;
            }
            Vec3 center = target.getBoundingBox().getCenter();
            int cacheTicks = Math.max(1, morphology.entityQueryInterval());
            double reach = TentacleHeldPlayerCollision.captureReach(
                    target.getBbHeight(), profile.ragdollCollisionRadius(),
                    profile.maximumSpeed(), sizeScale, cacheTicks);
            lastGrabCollisionReach = reach;
            AABB bounds = new AABB(center, center).inflate(reach);
            TentacleCollisionSpace world = AabbTentacleCollisionSpace.capture(
                    level, bounds, morphology.collisionSlop());
            List<List<Vec3>> probes = List.of(
                    List.of(center.add(-reach, 0.0D, 0.0D), center.add(reach, 0.0D, 0.0D)),
                    List.of(center.add(0.0D, -reach, 0.0D), center.add(0.0D, reach, 0.0D)),
                    List.of(center.add(0.0D, 0.0D, -reach), center.add(0.0D, 0.0D, reach)),
                    List.of(center.add(-reach, -reach, -reach), center.add(reach, reach, reach)),
                    List.of(center.add(-reach, reach, reach), center.add(reach, -reach, -reach)));
            double padding = Math.max(
                    Math.sqrt(2.0D) * target.getBbWidth() * 0.5D
                            + profile.ragdollCollisionRadius(),
                    profile.ragdollCollisionRadius() * 2.0D);
            grabCollision = SableTentacleCollisionSpace.capture(level, probes, padding,
                    morphology.collisionMaximumBlockSamples(), world);
            nextGrabCollisionTick = staggeredTick(
                    morphology.entityQueryInterval(), 0x2B);
            return grabCollision;
        }

        private TentacleCollisionSpace captureGrabContactCollision(
                ServerLevel level, TentacleEntityCollider.GrabContact contact,
                double targetRadius) {
            List<List<Vec3>> corridor = List.of(List.of(
                    contact.tentaclePoint(), contact.entityPoint()));
            double padding = morphology.tipRadius() + targetRadius
                    + morphology.collisionSlop() * 2.0D;
            int narrowBudget = Math.min(
                    morphology.collisionMaximumBlockSamples(), 768);
            TentacleCollisionSpace world =
                    AabbTentacleCollisionSpace.captureAlongPaths(
                            level, corridor, padding, morphology.collisionSlop(),
                            rootAnchor, narrowBudget);
            return SableTentacleCollisionSpace.capture(
                    level, corridor, padding, narrowBudget, world);
        }

        private int staggeredTick(int interval, int salt) {
            return nextStaggeredTick(age, id, interval, salt);
        }

        private TentacleCollisionSpace captureSearchCollision(ServerLevel level, Vec3 root,
                Vec3 start, Vec3 goal, List<Vec3> path) {
            List<List<Vec3>> corridors = new ArrayList<>(4);
            if (chain != null) {
                corridors.add(chain.snapshot());
            }
            if (trail.size() >= 2) {
                corridors.add(trail.path());
            }
            if (!path.isEmpty()) {
                corridors.add(path);
            }
            if (path.isEmpty() || path.size() != 2
                    || !path.getFirst().equals(start) || !path.getLast().equals(goal)) {
                corridors.add(List.of(start, goal));
            }
            double padding = activePathMargin() + morphology.rootRadius()
                    + morphology.pathClearance();
            TentacleCollisionSpace world = AabbTentacleCollisionSpace.captureAlongPaths(
                    level, corridors, padding, morphology.collisionSlop(), rootAnchor,
                    morphology.collisionMaximumBlockSamples());
            return SableTentacleCollisionSpace.capture(level, corridors, padding,
                    morphology.collisionMaximumBlockSamples(), world);
        }

        private static int firstBlockedSegmentAhead(List<Vec3> path,
                TentacleCollisionSpace collision, double radius, double pathDistance) {
            double traversed = 0.0D;
            for (int index = 1; index < path.size(); index++) {
                double segmentLength = path.get(index - 1).distanceTo(path.get(index));
                if (traversed + segmentLength + 1.0E-6D < pathDistance) {
                    traversed += segmentLength;
                    continue;
                }
                if (!collision.clear(path.get(index - 1), path.get(index), radius)) {
                    return index;
                }
                traversed += segmentLength;
            }
            return -1;
        }

        private static List<Vec3> replaceLast(List<Vec3> path, Vec3 replacement) {
            List<Vec3> result = new ArrayList<>(path);
            result.set(result.size() - 1, replacement);
            return List.copyOf(result);
        }

        private void sendState(ServerLevel level, Vec3 root, TentacleGrabProfile grabProfile) {
            ServerPlayer grabbed = grabbedPlayer == null ? null
                    : level.getServer().getPlayerList().getPlayer(grabbedPlayer);
            TentacleRagdollPose ragdollPose = grabBody == null
                    ? TentacleRagdollPose.IDENTITY : grabBody.pose();
            PacketDistributor.sendToPlayersNear(level, null,
                    root.x, root.y, root.z, trackingDistance(),
                    new TentacleStateSyncPayload(
                            id, false, phase, age, morphology.syncIntervalTicks(), visualSeed,
                            root.x, root.y, root.z,
                            (float) morphology.rootRadius(), (float) morphology.tipRadius(),
                            grabbed == null ? -1 : grabbed.getId(),
                            grabMode,
                            grabbedPlayer == null ? 0.0F : (float) Mth.clamp(
                                    (grabTicks / (double) Math.max(1, grabProfile.attachTicks())) * Math.pow(
                                            TentacleEntityCollider.sizeScale(morphology, sourceProfile), 0.35D),
                                    0.0D, 1.5D),
                            ragdollPose,
                            chain.snapshot()));
        }

        private TentacleSavedData.State savedState() {
            List<Vec3> points = chain == null ? List.of() : chain.snapshot();
            List<Vec3> previous = chain == null ? List.of() : chain.previousSnapshot();
            return new TentacleSavedData.State(id, rootAnchor, volume, visualSeed,
                    phase, motionMode == MotionMode.TRACKING, trackedPlayer,
                    age, phaseTicks, extension, lengthScale, grabEnabled, grabMode,
                    points, previous);
        }

        private void sendRemoval(ServerLevel level) {
            PacketDistributor.sendToPlayersNear(level, null,
                    rootAnchor.x, rootAnchor.y, rootAnchor.z, trackingDistance(),
                    TentacleStateSyncPayload.removed(id));
        }

        private double trackingDistance() {
            return BASE_TRACKING_DISTANCE + (morphology == null ? 0.0D
                    : morphology.maximumLength() + morphology.rootRadius());
        }

        private Vec3 rootPosition() {
            return rootAnchor.add(0.0D, morphology.collisionSlop() * 1.5D, 0.0D);
        }

        private void setIdle() {
            releaseGrab(20);
            motionMode = MotionMode.IDLE;
            trackedPlayer = null;
            extension = 1.0D;
            plannedGoal = null;
            plannedPathIncomplete = false;
            plannedPath = List.of();
            collision = null;
            nextPathTick = 0;
            resetProgressWatch(chain == null ? rootPosition() : chain.point(chain.pointCount() - 1), true);
            transition(TentaclePhase.IDLE);
        }

        private void setTracking(UUID playerId) {
            if (trackedPlayer == null || !trackedPlayer.equals(playerId)) {
                releaseGrab(10);
            }
            motionMode = MotionMode.TRACKING;
            trackedPlayer = playerId;
            extension = 1.0D;
            collision = null;
            plannedPath = List.of();
            plannedGoal = null;
            plannedPathIncomplete = true;
            nextPathTick = 0;
            resetProgressWatch(chain == null ? rootPosition() : chain.point(chain.pointCount() - 1), true);
            transition(TentaclePhase.IDLE);
        }

        private void beginEmerging() {
            releaseGrab(20);
            extension = 0.0D;
            chain = null;
            smoothedGoal = null;
            plannedPath = List.of();
            guidePath = List.of();
            plannedGoal = null;
            plannedPathIncomplete = false;
            tipController.reset();
            progressAnchor = null;
            progressAnchorTick = 0;
            recoveryClearanceStep = 0;
            chainNeedsInitialization = true;
            lengthScale = 1.0D;
            transition(TentaclePhase.EMERGING);
        }

        private void beginRetracting() {
            releaseGrab(20);
            if (phase != TentaclePhase.RETRACTING) {
                transition(TentaclePhase.RETRACTING);
            }
        }

        private void transition(TentaclePhase next) {
            phase = next;
            phaseTicks = 0;
        }

        private GrabEnableResult setGrabEnabled(boolean enabled) {
            if (enabled && !grabEligible(volume)) {
                grabEnabled = false;
                releaseGrab(20);
                return GrabEnableResult.VOLUME_TOO_SMALL;
            }
            grabEnabled = enabled;
            if (!enabled) {
                releaseGrab(20);
            }
            return GrabEnableResult.UPDATED;
        }

        private void setGrabMode(TentacleGrabMode mode) {
            if (mode == null || mode == grabMode) {
                return;
            }
            if (grabMode == TentacleGrabMode.HOLD && mode != TentacleGrabMode.HOLD) {
                restoreGrabGravity();
                holdNoGravityApplied = false;
            }
            grabMode = mode;
            grabModeTicks = 0;
            Vec3 currentTip = chain == null
                    ? rootPosition() : chain.point(chain.pointCount() - 1);
            smoothedGoal = currentTip;
            plannedGoal = null;
            plannedPathIncomplete = true;
            nextPathTick = 0;
            resetProgressWatch(currentTip, true);
        }

        private String description() {
            String target = trackedPlayer == null ? "-" : trackedPlayer.toString();
            return "id=" + id + " volume=" + String.format(java.util.Locale.ROOT, "%.3f", volume)
                    + " phase=" + phase.name().toLowerCase(java.util.Locale.ROOT)
                    + " mode=" + motionMode.name().toLowerCase(java.util.Locale.ROOT)
                    + " grab=" + grabEnabled + "/" + grabMode.serializedName()
                    + "/eligible=" + grabEligible(volume)
                    + (grabbedPlayer == null ? "" : "/active:"
                            + (grabBody == null ? "unknown"
                                    : grabBody.pose().grabTarget().name().toLowerCase(java.util.Locale.ROOT)))
                    + " target=" + target
                    + " root=" + String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f",
                            rootAnchor.x, rootAnchor.y, rootAnchor.z);
        }

        private boolean containsPoint(Vec3 point, double padding) {
            if (point == null) {
                return false;
            }
            if (chain == null || morphology == null) {
                double radius = Math.max(0.25D, padding);
                return rootPosition().distanceToSqr(point) <= radius * radius;
            }
            return TentacleRaycast.containsPoint(
                    chain.snapshot(), point, morphology::radiusAt, padding);
        }

        private static Vec3 clampToLength(Vec3 root, Vec3 goal, double maximumLength) {
            Vec3 offset = goal.subtract(root);
            double length = offset.length();
            return length <= maximumLength || length < 1.0E-8D
                    ? goal : root.add(offset.scale(maximumLength / length));
        }

        private record RoutePlan(List<Vec3> path, boolean complete) {
        }
    }

    static boolean grabEligible(double volume) {
        return Double.isFinite(volume) && volume >= MINIMUM_GRAB_VOLUME;
    }

    static double recoveryPathClearance(TentacleChainSolver chain,
            TentaclePhysicsProfile profile, int recoveryStep) {
        if (chain == null || recoveryStep <= 0) {
            return profile.tipPathClearance();
        }
        int lookback = recoveryStep * Math.max(1, profile.tipOrientationSegments());
        int follower = Math.max(1, chain.pointCount() - 1 - lookback);
        return Math.max(profile.tipPathClearance(),
                chain.radiusAt(profile, follower) * profile.pathTipClearanceScale());
    }

    static int nextStaggeredTick(int age, int instanceId, int interval, int salt) {
        int safeInterval = Math.max(1, interval);
        int phase = Math.floorMod(instanceId * 31 + salt, safeInterval);
        int delta = Math.floorMod(phase - age, safeInterval);
        return age + (delta == 0 ? safeInterval : delta);
    }

    static double trackingLengthScale(double baseLength, double requiredLength,
            double maximumScale) {
        if (!Double.isFinite(baseLength) || baseLength <= 1.0E-8D
                || !Double.isFinite(requiredLength)) {
            return 1.0D;
        }
        return Mth.clamp(requiredLength / baseLength,
                TentacleChainSolver.MINIMUM_TRACKING_LENGTH_SCALE,
                Math.max(1.0D, maximumScale));
    }

    static double approachLengthScale(double currentScale, double targetScale,
            double response, double maximumScale) {
        double upper = Math.max(1.0D, maximumScale);
        double current = Double.isFinite(currentScale)
                ? Mth.clamp(currentScale,
                        TentacleChainSolver.MINIMUM_TRACKING_LENGTH_SCALE, upper) : 1.0D;
        double target = Double.isFinite(targetScale)
                ? Mth.clamp(targetScale,
                        TentacleChainSolver.MINIMUM_TRACKING_LENGTH_SCALE, upper) : 1.0D;
        return Mth.lerp(Mth.clamp(response, 0.0D, 1.0D), current, target);
    }

    static boolean grabbedEntityWasReplaced(Object previousEntity, Object currentEntity) {
        return previousEntity != null && currentEntity != null
                && previousEntity != currentEntity;
    }
}
