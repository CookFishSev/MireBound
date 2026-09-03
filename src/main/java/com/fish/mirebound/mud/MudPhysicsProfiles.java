package com.fish.mirebound.mud;

import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsProfile;
import com.fish.mirebound.mud.flow.MudBlockMotionMode;
import com.fish.mirebound.mud.flow.MudFlowProfile;
import com.fish.mirebound.mud.harvest.MudHarvestProfile;
import java.util.Arrays;
import net.minecraft.world.entity.player.Player;

public final class MudPhysicsProfiles {
    private static final double[][] CLIENT_VALUES = new double[SinkingMedium.COUNT][];
    private static final SinkingPhysicsProfile[] CLIENT_ORDINARY = new SinkingPhysicsProfile[SinkingMedium.COUNT];
    private static final AdhesionStrandProfile[] CLIENT_ADHESION_STRANDS =
            new AdhesionStrandProfile[SinkingMedium.COUNT];
    private static final MudHarvestProfile[] CLIENT_HARVEST =
            new MudHarvestProfile[SinkingMedium.COUNT];
    private static final DroppedItemPhysicsProfile[] CLIENT_DROPPED_ITEMS =
            new DroppedItemPhysicsProfile[SinkingMedium.COUNT];
    private static final MudFlowProfile[] CLIENT_FLOW =
            new MudFlowProfile[SinkingMedium.COUNT];
    private static final SculkMireProfile[] CLIENT_SCULK_MIRE =
            new SculkMireProfile[SinkingMedium.COUNT];
    private static final TenderFleshProfile[] CLIENT_TENDER_FLESH =
            new TenderFleshProfile[SinkingMedium.COUNT];
    private static LivingSlimePhysicsProfile clientLivingSlime;
    private static SculkMireProfile clientSculkMire;
    private static TenderFleshProfile clientTenderFlesh;

    static {
        resetClient();
    }

    private MudPhysicsProfiles() {
    }

    static SinkingPhysicsProfile ordinary(Player player, SinkingMedium medium) {
        if (!player.level().isClientSide) {
            return MudPhysicsSettings.ordinaryProfile(medium);
        }
        return CLIENT_ORDINARY[medium.id()];
    }

    static LivingSlimePhysicsProfile livingSlime(Player player) {
        if (!player.level().isClientSide) {
            return MudPhysicsSettings.livingSlimeProfile();
        }
        return clientLivingSlime;
    }

    static SculkMireProfile sculkMire(Player player) {
        return sculkMire(player, SinkingMedium.SCULK_MIRE);
    }

    static SculkMireProfile sculkMire(Player player, SinkingMedium medium) {
        return player.level().isClientSide()
                ? CLIENT_SCULK_MIRE[medium.id()]
                : MudPhysicsSettings.sculkMireProfile(medium);
    }

    static TenderFleshProfile tenderFlesh(Player player) {
        return tenderFlesh(player, SinkingMedium.TENDER_FLESH);
    }

    static TenderFleshProfile tenderFlesh(Player player, SinkingMedium medium) {
        return player.level().isClientSide()
                ? CLIENT_TENDER_FLESH[medium.id()]
                : MudPhysicsSettings.tenderFleshProfile(medium);
    }

    static TenderFleshProfile tenderFleshClient() {
        return clientTenderFlesh;
    }

    static TenderFleshProfile tenderFleshClient(SinkingMedium medium) {
        return CLIENT_TENDER_FLESH[medium.id()];
    }

    static AdhesionStrandProfile adhesionStrands(Player player, SinkingMedium medium) {
        return player.level().isClientSide()
                ? CLIENT_ADHESION_STRANDS[medium.id()]
                : MudPhysicsSettings.adhesionStrandProfile(medium);
    }

    static AdhesionStrandProfile adhesionStrandsClient(SinkingMedium medium) {
        return CLIENT_ADHESION_STRANDS[medium.id()];
    }

    static MudHarvestProfile harvestClient(SinkingMedium medium) {
        return CLIENT_HARVEST[medium.id()];
    }

    static DroppedItemPhysicsProfile droppedItemsClient(SinkingMedium medium) {
        return CLIENT_DROPPED_ITEMS[medium.id()];
    }

    static MudFlowProfile flowClient(SinkingMedium medium) {
        return CLIENT_FLOW[medium.id()];
    }

    public static double[] defaultValues(SinkingMedium medium) {
        double[] values = new double[MudPhysicsParameter.COUNT];
        SinkingPhysicsProfile.forMedium(medium).writeTo(values);
        LivingSlimePhysicsProfile.DEFAULT.writeTo(values);
        SculkMireProfile.DEFAULT.writeTo(values);
        TenderFleshProfile.DEFAULT.writeTo(values);
        AssimilationProfile.DEFAULT.writeTo(values);
        put(values, MudPhysicsParameter.ASSIMILATION_ENABLED,
                medium == SinkingMedium.ASSIMILATION_SLIME ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.ENABLED, 1.0D);
        put(values, MudPhysicsParameter.SHAPE_TYPE, MudShapeType.FULL.ordinal());
        put(values, MudPhysicsParameter.BEHAVIOR_PROFILE, medium.defaultBehaviorType().ordinal());
        put(values, MudPhysicsParameter.COVERAGE_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.TENTACLE_ENABLED, 0.0D);
        put(values, MudPhysicsParameter.SWARM_ENABLED,
                medium == SinkingMedium.INSECT_MOUND ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.SCULK_ENABLED,
                medium == SinkingMedium.SCULK_MIRE ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.FLESH_ENABLED,
                medium == SinkingMedium.TENDER_FLESH ? 1.0D : 0.0D);
        put(values, MudPhysicsParameter.STRUGGLE_MAX_COOLDOWN_TICKS, 30.0D);
        put(values, MudPhysicsParameter.SLIME_STRUGGLE_MAX_COOLDOWN_TICKS, 30.0D);
        put(values, MudPhysicsParameter.SURFACE_HEIGHT, 1.0D);
        put(values, MudPhysicsParameter.POLLUTION_MULTIPLIER, 1.0D);
        put(values, MudPhysicsParameter.WATER_WASH_MULTIPLIER, 1.0D);
        put(values, MudPhysicsParameter.RAIN_WASH_MULTIPLIER, 1.0D);
        put(values, MudPhysicsParameter.COVERAGE_MAXIMUM, defaultCoverageMaximum(medium));
        put(values, MudPhysicsParameter.COVERAGE_OPACITY, defaultCoverageOpacity(medium));
        put(values, MudPhysicsParameter.COVERAGE_OPACITY_VARIATION,
                defaultCoverageOpacityVariation(medium));
        put(values, MudPhysicsParameter.COVERAGE_NATURAL_FADE_TICKS,
                medium.defaultCoverageNaturalFadeTicks());
        put(values, MudPhysicsParameter.ADAPTIVE_COVERAGE_SMOOTHING_RADIUS, 0.0D);
        put(values, MudPhysicsParameter.ADAPTIVE_COVERAGE_TEXTURE_DETAIL, 0.90D);
        put(values, MudPhysicsParameter.COVERAGE_BRIGHTNESS_VARIATION, 0.08D);
        put(values, MudPhysicsParameter.AUTO_STACK_FILL, 1.0D);
        put(values, MudPhysicsParameter.GRAVITY_FALLING_ENABLED, 0.0D);
        put(values, MudPhysicsParameter.TENTACLE_MAX_INSTANCES, 24.0D);
        put(values, MudPhysicsParameter.TENTACLE_MAX_VOLUME, 64.0D);
        put(values, MudPhysicsParameter.TENTACLE_SEGMENTS, 20.0D);
        put(values, MudPhysicsParameter.TENTACLE_SEGMENT_LENGTH, 0.27D);
        put(values, MudPhysicsParameter.TENTACLE_ROOT_RADIUS, 0.30D);
        put(values, MudPhysicsParameter.TENTACLE_TIP_RADIUS, 0.085D);
        put(values, MudPhysicsParameter.TENTACLE_GRAVITY, 0.006D);
        put(values, MudPhysicsParameter.TENTACLE_DAMPING, 0.91D);
        put(values, MudPhysicsParameter.TENTACLE_STRETCH_COMPLIANCE, 0.0D);
        put(values, MudPhysicsParameter.TENTACLE_SOLVER_STRETCH_LIMIT, 1.0D);
        put(values, MudPhysicsParameter.TENTACLE_BEND_COMPLIANCE, 0.0035D);
        put(values, MudPhysicsParameter.TENTACLE_CURVATURE_SMOOTHING, 0.32D);
        put(values, MudPhysicsParameter.TENTACLE_BEND_REST_RATIO, 0.955D);
        put(values, MudPhysicsParameter.TENTACLE_TIP_BEND_FLEXIBILITY, 2.40D);
        put(values, MudPhysicsParameter.TENTACLE_SELF_COLLISION_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.TENTACLE_SELF_COLLISION_RADIUS_SCALE, 0.92D);
        put(values, MudPhysicsParameter.TENTACLE_SELF_COLLISION_RESPONSE, 0.68D);
        put(values, MudPhysicsParameter.TENTACLE_SUBSTEPS, 2.0D);
        put(values, MudPhysicsParameter.TENTACLE_ITERATIONS, 5.0D);
        put(values, MudPhysicsParameter.TENTACLE_EMERGE_SPEED, 0.055D);
        put(values, MudPhysicsParameter.TENTACLE_RETRACT_SPEED, 0.085D);
        put(values, MudPhysicsParameter.TENTACLE_IDLE_REACH, 4.2D);
        put(values, MudPhysicsParameter.TENTACLE_IDLE_HEIGHT, 2.15D);
        put(values, MudPhysicsParameter.TENTACLE_IDLE_SWAY, 0.22D);
        put(values, MudPhysicsParameter.TENTACLE_IDLE_SWAY_SPEED, 0.035D);
        put(values, MudPhysicsParameter.TENTACLE_IDLE_DECISION_TICKS, 100.0D);
        put(values, MudPhysicsParameter.TENTACLE_IDLE_MIN_REACH, 0.25D);
        put(values, MudPhysicsParameter.TENTACLE_IDLE_MAX_REACH, 0.92D);
        put(values, MudPhysicsParameter.TENTACLE_IDLE_REST_RATIO, 0.58D);
        put(values, MudPhysicsParameter.TENTACLE_LENGTH_VARIATION, 0.14D);
        put(values, MudPhysicsParameter.TENTACLE_THICKNESS_VARIATION, 0.12D);
        put(values, MudPhysicsParameter.TENTACLE_VOLUME_LENGTH_EXPONENT, 0.48D);
        put(values, MudPhysicsParameter.TENTACLE_THICKNESS_LENGTH_COUPLING, 0.65D);
        put(values, MudPhysicsParameter.TENTACLE_MOTION_VARIATION, 0.16D);
        put(values, MudPhysicsParameter.TENTACLE_MUSCLE_AMPLITUDE, 0.14D);
        put(values, MudPhysicsParameter.TENTACLE_MUSCLE_SPEED, 0.075D);
        put(values, MudPhysicsParameter.TENTACLE_GUIDE_STRENGTH, 0.045D);
        put(values, MudPhysicsParameter.TENTACLE_GUIDE_DEAD_ZONE_SCALE, 0.85D);
        put(values, MudPhysicsParameter.TENTACLE_GUIDE_INERTIA_TRANSFER, 0.30D);
        put(values, MudPhysicsParameter.TENTACLE_TIP_ORIENTATION_STRENGTH, 0.34D);
        put(values, MudPhysicsParameter.TENTACLE_TIP_ORIENTATION_SEGMENTS, 4.0D);
        put(values, MudPhysicsParameter.TENTACLE_TIP_ACCELERATION, 0.065D);
        put(values, MudPhysicsParameter.TENTACLE_TIP_LOOKAHEAD_SEGMENTS, 2.75D);
        put(values, MudPhysicsParameter.TENTACLE_TIP_MAX_LEAD_SEGMENTS, 2.40D);
        put(values, MudPhysicsParameter.TENTACLE_TIP_ADVANCE_SPEED, 0.11D);
        put(values, MudPhysicsParameter.TENTACLE_TRACK_TIP_ADVANCE_SPEED, 0.34D);
        put(values, MudPhysicsParameter.TENTACLE_TRACK_MAX_STRETCH, 1.0D);
        put(values, MudPhysicsParameter.TENTACLE_LENGTH_RESPONSE, 0.055D);
        put(values, MudPhysicsParameter.TENTACLE_SLACK_CURVE, 0.42D);
        put(values, MudPhysicsParameter.TENTACLE_CURVE_WAVES, 3.0D);
        put(values, MudPhysicsParameter.TENTACLE_CURVE_DETAIL, 0.34D);
        put(values, MudPhysicsParameter.TENTACLE_PATH_CELL_SIZE, 0.50D);
        put(values, MudPhysicsParameter.TENTACLE_PATH_CLEARANCE, 0.14D);
        put(values, MudPhysicsParameter.TENTACLE_PATH_TIP_CLEARANCE_SCALE, 1.05D);
        put(values, MudPhysicsParameter.TENTACLE_PATH_MARGIN, 2.0D);
        put(values, MudPhysicsParameter.TENTACLE_PATH_REPLAN_TICKS, 8.0D);
        put(values, MudPhysicsParameter.TENTACLE_STUCK_REPLAN_TICKS, 40.0D);
        put(values, MudPhysicsParameter.TENTACLE_STUCK_PROGRESS_DISTANCE, 0.12D);
        put(values, MudPhysicsParameter.TENTACLE_STUCK_CLEARANCE_STEPS, 4.0D);
        put(values, MudPhysicsParameter.TENTACLE_PATH_MAX_NODES, 2048.0D);
        put(values, MudPhysicsParameter.TENTACLE_PATH_GOAL_TOLERANCE, 0.45D);
        put(values, MudPhysicsParameter.TENTACLE_PATH_REPAIR_LOOKAHEAD, 3.0D);
        put(values, MudPhysicsParameter.TENTACLE_TRAIL_SAMPLE_DISTANCE_SCALE, 0.45D);
        put(values, MudPhysicsParameter.TENTACLE_TRAIL_UNWRAP_TICKS, 4.0D);
        put(values, MudPhysicsParameter.TENTACLE_TRAIL_MAX_POINTS, 96.0D);
        put(values, MudPhysicsParameter.TENTACLE_TRAIL_RELEASE_RATIO, 0.97D);
        put(values, MudPhysicsParameter.TENTACLE_COLLISION_SLOP, 0.006D);
        put(values, MudPhysicsParameter.TENTACLE_ENTITY_COLLISION_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.TENTACLE_COLLISION_RESPONSE, 0.72D);
        put(values, MudPhysicsParameter.TENTACLE_COLLISION_IMPULSE, 0.055D);
        put(values, MudPhysicsParameter.TENTACLE_COLLISION_MAX_PUSH_SPEED, 0.18D);
        put(values, MudPhysicsParameter.TENTACLE_BODY_COMPLIANCE, 0.68D);
        put(values, MudPhysicsParameter.TENTACLE_BODY_MAX_DEFLECTION, 0.16D);
        put(values, MudPhysicsParameter.TENTACLE_SIZE_FORCE_EXPONENT, 0.82D);
        put(values, MudPhysicsParameter.TENTACLE_SIZE_STIFFNESS_EXPONENT, 0.90D);
        put(values, MudPhysicsParameter.TENTACLE_COLLISION_MAX_ENTITIES, 12.0D);
        put(values, MudPhysicsParameter.TENTACLE_ENTITY_QUERY_INTERVAL, 3.0D);
        put(values, MudPhysicsParameter.TENTACLE_COLLISION_MAX_BLOCK_SAMPLES, 8192.0D);
        put(values, MudPhysicsParameter.TENTACLE_SYNC_INTERVAL_TICKS, 2.0D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_TIP_SEGMENTS, 3.0D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_CONTACT_PADDING, 0.16D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_TARGET_PADDING_SCALE, 0.25D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_ATTACH_TICKS, 12.0D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_SPRING, 0.22D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_DAMPING, 0.68D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_MAX_ACCELERATION, 0.16D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_MAX_SPEED, 0.72D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_BREAK_DISTANCE, 12.0D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_MAX_TICKS, 0.0D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_WHOLE_BODY_TIP_RATIO, 0.52D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_SURFACE_CLEARANCE_SCALE, 1.0D);
        put(values, MudPhysicsParameter.TENTACLE_GRAB_FLIGHT_CONTROL_SCALE, 0.42D);
        put(values, MudPhysicsParameter.TENTACLE_HOLD_WRAP_TICKS, 18.0D);
        put(values, MudPhysicsParameter.TENTACLE_HOLD_LIFT_TICKS, 30.0D);
        put(values, MudPhysicsParameter.TENTACLE_HOLD_LIFT_HEIGHT, 3.0D);
        put(values, MudPhysicsParameter.TENTACLE_HOLD_CONTROL_SCALE, 0.48D);
        put(values, MudPhysicsParameter.TENTACLE_HOLD_POSITION_RADIUS, 1.75D);
        put(values, MudPhysicsParameter.TENTACLE_HOLD_HEIGHT_VARIATION, 0.75D);
        put(values, MudPhysicsParameter.TENTACLE_HOLD_POSITION_TICKS, 120.0D);
        put(values, MudPhysicsParameter.TENTACLE_HOLD_MOVE_TICKS, 30.0D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_GRAVITY, 0.035D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_ARM_GRAVITY_SCALE, 2.40D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_ARM_TORSO_CLEARANCE_SCALE, 1.0D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_DAMPING, 0.86D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_INERTIA, 0.58D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_GRIP_STIFFNESS, 0.78D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_GRIP_ORIENTATION_STIFFNESS, 0.92D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_JOINT_STIFFNESS, 0.88D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_SOLVER_ITERATIONS, 6.0D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_COLLISION_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_COLLISION_RADIUS, 0.12D);
        put(values, MudPhysicsParameter.TENTACLE_RAGDOLL_MAX_NODE_SPEED, 0.55D);
        put(values, MudPhysicsParameter.TENTACLE_THRASH_RADIUS, 2.75D);
        put(values, MudPhysicsParameter.TENTACLE_THRASH_VERTICAL_RANGE, 1.75D);
        put(values, MudPhysicsParameter.TENTACLE_THRASH_SPEED, 0.19D);
        put(values, MudPhysicsParameter.TENTACLE_THRASH_JITTER, 0.60D);
        put(values, MudPhysicsParameter.TENTACLE_WRAP_RADIUS, 0.70D);
        put(values, MudPhysicsParameter.TENTACLE_WRAP_SPEED, 0.16D);
        put(values, MudPhysicsParameter.SWARM_RADIUS, 2.75D);
        put(values, MudPhysicsParameter.SWARM_BUILDUP, 0.018D);
        put(values, MudPhysicsParameter.SWARM_DECAY, 0.008D);
        put(values, MudPhysicsParameter.SWARM_WATER_DECAY, 0.075D);
        put(values, MudPhysicsParameter.SWARM_MOVE_SCALE, 0.78D);
        put(values, MudPhysicsParameter.SWARM_STRUGGLE_SCALE, 0.82D);
        put(values, MudPhysicsParameter.SWARM_MAX_SCREEN_INSECTS, 24.0D);
        put(values, MudPhysicsParameter.SWARM_SCREEN_OPACITY, 0.72D);
        put(values, MudPhysicsParameter.SWARM_SCREEN_SPEED, 1.0D);
        put(values, MudPhysicsParameter.SWARM_CLEAR_ON_DEATH, 1.0D);
        put(values, MudPhysicsParameter.SWARM_CLEAR_ON_RECONNECT, 1.0D);
        put(values, MudPhysicsParameter.SWARM_INSECT_SCALE, 1.90D);
        put(values, MudPhysicsParameter.SWARM_INSECT_WANDER, 0.90D);
        put(values, MudPhysicsParameter.SWARM_SILK_DENSITY, 0.78D);
        put(values, MudPhysicsParameter.SWARM_SILK_OPACITY, 0.84D);
        put(values, MudPhysicsParameter.SWARM_SILK_REACH, 0.46D);
        put(values, MudPhysicsParameter.SWARM_SHAKE_THRESHOLD, 30.0D);
        put(values, MudPhysicsParameter.SWARM_SHAKE_REMOVAL, 0.0010D);
        put(values, MudPhysicsParameter.SWARM_SCREEN_LIFETIME, 80.0D);
        put(values, MudPhysicsParameter.SWARM_DROP_ACCELERATION, 0.034D);
        put(values, MudPhysicsParameter.SURFACE_EFFECTS_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.SURFACE_HOLE_RADIUS_SCALE, 1.10D);
        put(values, MudPhysicsParameter.SURFACE_HOLE_DARKENING,
                medium == SinkingMedium.LIVING_SLIME ? 0.18D
                        : medium == SinkingMedium.TENDER_FLESH ? 0.26D : 0.38D);
        put(values, MudPhysicsParameter.SURFACE_RIM_WIDTH_PIXELS, 2.50D);
        put(values, MudPhysicsParameter.SURFACE_RIM_HEIGHT_PIXELS,
                medium == SinkingMedium.LIVING_SLIME ? 0.80D
                        : medium == SinkingMedium.TENDER_FLESH ? 1.55D : 1.25D);
        put(values, MudPhysicsParameter.SURFACE_CLOSE_TICKS,
                medium == SinkingMedium.TENDER_FLESH ? 45.0D : 35.0D);
        put(values, MudPhysicsParameter.SURFACE_MOVEMENT_TRAIL, 0.22D);
        put(values, MudPhysicsParameter.SURFACE_BUBBLE_RATE, defaultBubbleRate(medium));
        put(values, MudPhysicsParameter.SURFACE_BUBBLE_MIN_PIXELS, defaultBubbleMinimum(medium));
        put(values, MudPhysicsParameter.SURFACE_BUBBLE_MAX_PIXELS, defaultBubbleMaximum(medium));
        put(values, MudPhysicsParameter.SURFACE_BUBBLE_SOUND_VOLUME,
                medium == SinkingMedium.LIVING_SLIME ? 0.13D
                        : medium == SinkingMedium.TENDER_FLESH ? 0.16D : 0.10D);
        put(values, MudPhysicsParameter.SURFACE_BUBBLE_SOUND_PITCH, switch (medium) {
            case TAR -> 0.62D;
            case LIVING_SLIME -> 0.82D;
            case TENDER_FLESH -> 0.58D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    SILT, SOUL_SILT, JUNGLE_QUICKSAND -> 1.08D;
            default -> 0.76D;
        });
        put(values, MudPhysicsParameter.SURFACE_DISPLACEMENT_PIXELS,
                medium == SinkingMedium.LIVING_SLIME ? 0.76D
                        : medium == SinkingMedium.TENDER_FLESH ? 1.25D : 1.05D);
        put(values, MudPhysicsParameter.SURFACE_HEIGHT_RESPONSE,
                medium == SinkingMedium.TAR ? 0.10D : 0.18D);
        put(values, MudPhysicsParameter.SURFACE_IMPACT_PILE_EXPANSION_PIXELS, switch (medium) {
            case TAR -> 3.0D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    SILT, SOUL_SILT, JUNGLE_QUICKSAND -> 4.75D;
            case LIVING_SLIME -> 4.25D;
            case TENDER_FLESH -> 4.50D;
            default -> 4.0D;
        });
        put(values, MudPhysicsParameter.FLESH_TENTACLE_LENGTH_PIXELS, 5.0D);
        put(values, MudPhysicsParameter.FLESH_TENTACLE_SWAY_PIXELS, 1.50D);
        put(values, MudPhysicsParameter.FLESH_TENTACLE_SEGMENTS, 3.0D);
        put(values, MudPhysicsParameter.FLESH_TENTACLE_HEIGHT_SCALE, 1.0D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_WALK_SCALE_THRESHOLD, 0.08D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_RISE_RATE, 0.018D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_WITHDRAW_RATE, 0.025D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_LAYERS, 2.0D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_POOL_WIDTH, 2.0D);
        put(values, MudPhysicsParameter.FLESH_MEMBRANE_OPACITY, 0.48D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_COLLISION_START, 0.35D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_OPEN_RADIUS, 0.42D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_CLOSED_RADIUS, 0.07D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_HEIGHT_MARGIN_PIXELS, 5.0D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MAX_HEIGHT_PIXELS, 36.0D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_COOLDOWN_TICKS, 600.0D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_STRIKE_COOLDOWN_TICKS, 4.0D);
        put(values, MudPhysicsParameter.FLESH_MEMBRANE_OPAQUE, 1.0D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_HEIGHT_PIXELS, 18.0D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_FORCED_RELEASE_DISTANCE, 0.85D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MIN_PILLAR_HITS, 3.0D);
        put(values, MudPhysicsParameter.FLESH_ENCLOSURE_MAX_PILLAR_HITS, 6.0D);
        put(values, MudPhysicsParameter.ERUPTION_ENABLED, 0.0D);
        put(values, MudPhysicsParameter.ERUPTION_SPAWN_CHANCE, 0.18D);
        put(values, MudPhysicsParameter.ERUPTION_SPAWN_INTERVAL_TICKS, 120.0D);
        put(values, MudPhysicsParameter.ERUPTION_SPAWN_ATTEMPTS, 2.0D);
        put(values, MudPhysicsParameter.ERUPTION_SEARCH_RADIUS, 10.0D);
        put(values, MudPhysicsParameter.ERUPTION_MIN_RADIUS_PIXELS, 3.5D);
        put(values, MudPhysicsParameter.ERUPTION_MAX_RADIUS_PIXELS, 7.0D);
        put(values, MudPhysicsParameter.ERUPTION_MIN_LIFETIME_TICKS, 360.0D);
        put(values, MudPhysicsParameter.ERUPTION_MAX_LIFETIME_TICKS, 1000.0D);
        put(values, MudPhysicsParameter.ERUPTION_MIN_BURST_INTERVAL_TICKS, 18.0D);
        put(values, MudPhysicsParameter.ERUPTION_MAX_BURST_INTERVAL_TICKS, 64.0D);
        put(values, MudPhysicsParameter.ERUPTION_MIN_HEIGHT, 0.34D);
        put(values, MudPhysicsParameter.ERUPTION_MAX_HEIGHT, 1.20D);
        put(values, MudPhysicsParameter.ERUPTION_MIN_DROPLETS, 5.0D);
        put(values, MudPhysicsParameter.ERUPTION_MAX_DROPLETS, 20.0D);
        put(values, MudPhysicsParameter.ERUPTION_MAX_ACTIVE, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_MIN_SPACING, 2.25D);
        put(values, MudPhysicsParameter.ERUPTION_CONTINUOUS_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_SURGES_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_INTERVAL_TICKS, 3.0D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_MIN_DROPLETS, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_MAX_DROPLETS, 3.0D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_HEIGHT_SCALE, 0.82D);
        put(values, MudPhysicsParameter.ERUPTION_POWER_SCALE, 2.10D);
        put(values, MudPhysicsParameter.ERUPTION_VOLUME_SCALE, 2.40D);
        put(values, MudPhysicsParameter.ERUPTION_JET_COHESION, 0.72D);
        put(values, MudPhysicsParameter.ERUPTION_TOP_SPREAD_SCALE, 1.05D);
        put(values, MudPhysicsParameter.ERUPTION_SPREAD_TRIGGER_RATIO, 0.24D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_MIN_HEIGHT, 0.70D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_MAX_HEIGHT, 2.80D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_VOLUME_SCALE, 2.40D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_JET_COHESION, 0.80D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_TOP_SPREAD_SCALE, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_SPREAD_TRIGGER_RATIO, 0.22D);
        put(values, MudPhysicsParameter.ERUPTION_SURGE_DURATION_TICKS, 6.0D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_VARIATION_INTERVAL_TICKS, 40.0D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_SPREAD_DURATION_TICKS, 4.0D);
        put(values, MudPhysicsParameter.ERUPTION_SPREAD_DURATION_TICKS, 3.0D);
        put(values, MudPhysicsParameter.ERUPTION_FACE_DOWN_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_FACE_UP_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_FACE_NORTH_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_FACE_SOUTH_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_FACE_WEST_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_FACE_EAST_ENABLED, 1.0D);
        put(values, MudPhysicsParameter.ERUPTION_FLOW_PARTICLE_LIFETIME_TICKS, 80.0D);
        AdhesionStrandProfile.defaultsFor(medium).writeTo(values);
        MudHarvestProfile.defaultsFor(medium).writeTo(values);
        DroppedItemPhysicsProfile.defaultsFor(medium).writeTo(values);
        MudFlowProfile.DEFAULT.writeTo(values);
        return values;
    }

    public static double[] tentacleDefaultValues() {
        double[] values = defaultValues(SinkingMedium.MUD);
        put(values, MudPhysicsParameter.BEHAVIOR_PROFILE,
                MudBehaviorType.TENTACLE.ordinal());
        put(values, MudPhysicsParameter.TENTACLE_ENABLED, 1.0D);
        return values;
    }

    public static double[] sanitizeTentacle(double[] source) {
        double[] values = tentacleDefaultValues();
        if (source != null) {
            int limit = Math.min(source.length, values.length);
            for (int index = 0; index < limit; index++) {
                MudPhysicsParameter parameter = MudPhysicsParameter.values()[index];
                if (parameter.appliesToTentacle()) {
                    values[index] = parameter.sanitize(source[index]);
                }
            }
        }
        return values;
    }

    static double clientValue(SinkingMedium medium, MudPhysicsParameter parameter) {
        double[] values = CLIENT_VALUES[medium.id()];
        return values == null ? defaultValues(medium)[parameter.ordinal()] : values[parameter.ordinal()];
    }

    static double[] clientValues(SinkingMedium medium) {
        return Arrays.copyOf(CLIENT_VALUES[medium.id()], MudPhysicsParameter.COUNT);
    }

    public static boolean acceptClientProfile(SinkingMedium medium, double[] source) {
        double oldMaximum = clientValue(medium, MudPhysicsParameter.COVERAGE_MAXIMUM);
        double oldOpacity = clientValue(medium, MudPhysicsParameter.COVERAGE_OPACITY);
        double oldVariation = clientValue(medium, MudPhysicsParameter.COVERAGE_OPACITY_VARIATION);
        double oldBrightnessVariation = clientValue(
                medium, MudPhysicsParameter.COVERAGE_BRIGHTNESS_VARIATION);
        double oldAdaptiveSmoothing = clientValue(
                medium, MudPhysicsParameter.ADAPTIVE_COVERAGE_SMOOTHING_RADIUS);
        double oldAdaptiveDetail = clientValue(
                medium, MudPhysicsParameter.ADAPTIVE_COVERAGE_TEXTURE_DETAIL);
        double[] values = sanitize(medium, source);
        CLIENT_VALUES[medium.id()] = values;
        CLIENT_ORDINARY[medium.id()] = SinkingPhysicsProfile.fromValues(values);
        CLIENT_ADHESION_STRANDS[medium.id()] = AdhesionStrandProfile.fromValues(values);
        CLIENT_HARVEST[medium.id()] = MudHarvestProfile.fromValues(values);
        CLIENT_DROPPED_ITEMS[medium.id()] = DroppedItemPhysicsProfile.fromValues(values);
        CLIENT_FLOW[medium.id()] = MudFlowProfile.fromValues(values);
        CLIENT_SCULK_MIRE[medium.id()] = SculkMireProfile.fromValues(values);
        CLIENT_TENDER_FLESH[medium.id()] = TenderFleshProfile.fromValues(values);
        if (medium == SinkingMedium.LIVING_SLIME) {
            clientLivingSlime = LivingSlimePhysicsProfile.fromValues(values);
        }
        if (medium == SinkingMedium.SCULK_MIRE) {
            clientSculkMire = SculkMireProfile.fromValues(values);
        }
        if (medium == SinkingMedium.TENDER_FLESH) {
            clientTenderFlesh = TenderFleshProfile.fromValues(values);
        }
        return Math.abs(oldMaximum - values[MudPhysicsParameter.COVERAGE_MAXIMUM.ordinal()]) > 1.0E-6D
                || Math.abs(oldOpacity - values[MudPhysicsParameter.COVERAGE_OPACITY.ordinal()]) > 1.0E-6D
                || Math.abs(oldVariation - values[MudPhysicsParameter.COVERAGE_OPACITY_VARIATION.ordinal()]) > 1.0E-6D
                || Math.abs(oldBrightnessVariation
                        - values[MudPhysicsParameter.COVERAGE_BRIGHTNESS_VARIATION.ordinal()]) > 1.0E-6D
                || Math.abs(oldAdaptiveSmoothing
                        - values[MudPhysicsParameter.ADAPTIVE_COVERAGE_SMOOTHING_RADIUS.ordinal()]) > 1.0E-6D
                || Math.abs(oldAdaptiveDetail
                        - values[MudPhysicsParameter.ADAPTIVE_COVERAGE_TEXTURE_DETAIL.ordinal()]) > 1.0E-6D;
    }

    public static void resetClient() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            double[] values = defaultValues(medium);
            CLIENT_VALUES[medium.id()] = values;
            CLIENT_ORDINARY[medium.id()] = SinkingPhysicsProfile.fromValues(values);
            CLIENT_ADHESION_STRANDS[medium.id()] = AdhesionStrandProfile.fromValues(values);
            CLIENT_HARVEST[medium.id()] = MudHarvestProfile.fromValues(values);
            CLIENT_DROPPED_ITEMS[medium.id()] = DroppedItemPhysicsProfile.fromValues(values);
            CLIENT_FLOW[medium.id()] = MudFlowProfile.fromValues(values);
            CLIENT_SCULK_MIRE[medium.id()] = SculkMireProfile.fromValues(values);
            CLIENT_TENDER_FLESH[medium.id()] = TenderFleshProfile.fromValues(values);
        }
        clientLivingSlime = LivingSlimePhysicsProfile.fromValues(CLIENT_VALUES[SinkingMedium.LIVING_SLIME.id()]);
        clientSculkMire = SculkMireProfile.fromValues(CLIENT_VALUES[SinkingMedium.SCULK_MIRE.id()]);
        clientTenderFlesh = TenderFleshProfile.fromValues(CLIENT_VALUES[SinkingMedium.TENDER_FLESH.id()]);
    }

    static double[] sanitize(SinkingMedium medium, double[] source) {
        double[] defaults = defaultValues(medium);
        double[] values = Arrays.copyOf(defaults, defaults.length);
        if (source != null) {
            int limit = Math.min(source.length, values.length);
            for (int i = 0; i < limit; i++) {
                MudPhysicsParameter parameter = MudPhysicsParameter.values()[i];
                values[i] = parameter.sanitize(source[i]);
            }
        }
        MudBlockMotionMode.enforceExclusive(values);
        MudSinkingDepthControl.enforceSimpleBounds(values);

        enforceOrdered(values, MudPhysicsParameter.STRUGGLE_MIN, MudPhysicsParameter.STRUGGLE_MAX);
        enforceOrdered(values, MudPhysicsParameter.SLIME_STRUGGLE_MIN, MudPhysicsParameter.SLIME_STRUGGLE_MAX);
        enforceOrdered(values, MudPhysicsParameter.SLIME_STRUGGLE_LIFT_TICKS_MIN,
                MudPhysicsParameter.SLIME_STRUGGLE_LIFT_TICKS_MAX);
        enforceOrdered(values, MudPhysicsParameter.SCULK_CLAMP_DURATION_TICKS,
                MudPhysicsParameter.SCULK_CLAMP_MAXIMUM_TICKS);
        enforceOrdered(values, MudPhysicsParameter.ADHESION_STRAND_MIN_COUNT,
                MudPhysicsParameter.ADHESION_STRAND_MAX_COUNT);
        enforceOrdered(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_CURSOR_MIN_ONE_WAY_TICKS,
                MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_CURSOR_MAX_ONE_WAY_TICKS);
        enforceOrdered(values, MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ZONE_MIN_WIDTH,
                MudPhysicsParameter.ASSIMILATION_PARTIAL_PURGE_ZONE_MAX_WIDTH);
        enforceOrdered(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MIN_INTERVAL_TICKS,
                MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_INTERVAL_TICKS);
        enforceOrdered(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MIN_LENGTH,
                MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_LENGTH);
        enforceOrdered(values, MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MIN_WIDTH,
                MudPhysicsParameter.ASSIMILATION_SCREEN_CRACK_MAX_WIDTH);
        enforceOrdered(values, MudPhysicsParameter.ERUPTION_MIN_RADIUS_PIXELS,
                MudPhysicsParameter.ERUPTION_MAX_RADIUS_PIXELS);
        enforceOrdered(values, MudPhysicsParameter.ERUPTION_MIN_LIFETIME_TICKS,
                MudPhysicsParameter.ERUPTION_MAX_LIFETIME_TICKS);
        enforceOrdered(values, MudPhysicsParameter.ERUPTION_MIN_BURST_INTERVAL_TICKS,
                MudPhysicsParameter.ERUPTION_MAX_BURST_INTERVAL_TICKS);
        enforceOrdered(values, MudPhysicsParameter.ERUPTION_MIN_HEIGHT,
                MudPhysicsParameter.ERUPTION_MAX_HEIGHT);
        enforceOrdered(values, MudPhysicsParameter.ERUPTION_MIN_DROPLETS,
                MudPhysicsParameter.ERUPTION_MAX_DROPLETS);
        enforceOrdered(values, MudPhysicsParameter.ERUPTION_FLOW_MIN_DROPLETS,
                MudPhysicsParameter.ERUPTION_FLOW_MAX_DROPLETS);
        enforceOrdered(values, MudPhysicsParameter.ERUPTION_FLOW_MIN_HEIGHT,
                MudPhysicsParameter.ERUPTION_FLOW_MAX_HEIGHT);
        enforceOrdered(values, MudPhysicsParameter.TENTACLE_IDLE_MIN_REACH,
                MudPhysicsParameter.TENTACLE_IDLE_MAX_REACH);
        enforceWalkDepthOrder(values);
        int rootRadius = MudPhysicsParameter.TENTACLE_ROOT_RADIUS.ordinal();
        int tipRadius = MudPhysicsParameter.TENTACLE_TIP_RADIUS.ordinal();
        values[tipRadius] = Math.min(values[tipRadius], values[rootRadius]);
        return values;
    }

    private static void enforceOrdered(double[] values, MudPhysicsParameter minimum, MudPhysicsParameter maximum) {
        int minIndex = minimum.ordinal();
        int maxIndex = maximum.ordinal();
        if (values[maxIndex] < values[minIndex]) {
            values[maxIndex] = values[minIndex];
        }
    }

    private static void enforceWalkDepthOrder(double[] values) {
        int knee = MudPhysicsParameter.WALK_KNEE_DEPTH.ordinal();
        int thigh = MudPhysicsParameter.WALK_THIGH_DEPTH.ordinal();
        int waist = MudPhysicsParameter.WALK_WAIST_DEPTH.ordinal();
        values[thigh] = Math.max(values[knee] + 0.01D, values[thigh]);
        values[waist] = Math.max(values[thigh] + 0.01D, values[waist]);
        values[waist] = MudPhysicsParameter.WALK_WAIST_DEPTH.sanitize(values[waist]);
        values[thigh] = Math.min(values[thigh], values[waist] - 0.01D);
        values[knee] = Math.min(values[knee], values[thigh] - 0.01D);
    }

    private static void put(double[] values, MudPhysicsParameter parameter, double value) {
        values[parameter.ordinal()] = parameter.sanitize(value);
    }

    private static double defaultCoverageMaximum(SinkingMedium medium) {
        // Every medium may reach every canonical body/armor pixel by default.
        // Material differences belong to opacity, variation, wash and physics,
        // not to an artificial unpaintable fraction of the surface.
        return 1.0D;
    }

    private static double defaultCoverageOpacity(SinkingMedium medium) {
        return switch (medium) {
            case TENDER_FLESH -> 0.70D;
            case ASH_QUICKSAND -> 0.62D;
            case SOUL_SILT -> 0.68D;
            default -> medium.defaultCoverageOpacity();
        };
    }

    private static double defaultCoverageOpacityVariation(SinkingMedium medium) {
        return switch (medium) {
            case TENDER_FLESH -> 0.08D;
            case RED_QUICKSAND -> 0.14D;
            case ASH_QUICKSAND -> 0.20D;
            case SOUL_SILT -> 0.16D;
            case GEL_CLAY, LIME_MUD -> 0.08D;
            default -> 0.0D;
        };
    }

    private static double defaultBubbleRate(SinkingMedium medium) {
        return switch (medium) {
            case TAR -> 0.006D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    SILT, SOUL_SILT, JUNGLE_QUICKSAND -> 0.008D;
            case LIVING_SLIME -> 0.028D;
            case TENDER_FLESH -> 0.0D;
            default -> 0.016D;
        };
    }

    private static double defaultBubbleMinimum(SinkingMedium medium) {
        return switch (medium) {
            case TAR -> 2.0D;
            case TENDER_FLESH -> 1.50D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    SILT, SOUL_SILT, JUNGLE_QUICKSAND -> 0.75D;
            default -> 1.25D;
        };
    }

    private static double defaultBubbleMaximum(SinkingMedium medium) {
        return switch (medium) {
            case TAR -> 5.5D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    SILT, SOUL_SILT, JUNGLE_QUICKSAND -> 2.25D;
            case LIVING_SLIME -> 5.0D;
            case TENDER_FLESH -> 4.25D;
            default -> 4.0D;
        };
    }
}
