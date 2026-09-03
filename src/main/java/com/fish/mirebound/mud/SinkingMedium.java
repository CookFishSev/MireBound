package com.fish.mirebound.mud;

import com.fish.mirebound.Mirebound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public enum SinkingMedium {
    MUD(
            0,
            "mud",
            blockTexture("mud"),
            0.92D,
            0.30D,
            -0.025D,
            0.48D,
            0.09D,
            0.64D,
            0.22D,
            -0.038D,
            -0.018D,
            0.018D,
            0.030D,
            0.25D,
            0.14D,
            0.065D,
            0.032D,
            0.045D,
            0.72F,
            3,
            10,
            14,
            new Vec3(0.42D, 0.82D, 0.42D)),
    SOFT_QUICKSAND(
            1,
            "soft_quicksand",
            blockTexture("soft_quicksand"),
            0.91D,
            0.22D,
            -0.034D,
            0.58D,
            0.02D,
            0.54D,
            0.10D,
            -0.031D,
            -0.006D,
            0.014D,
            0.030D,
            0.24D,
            0.11D,
            0.054D,
            0.018D,
            0.068D,
            0.66F,
            4,
            9,
            14,
            new Vec3(0.30D, 0.68D, 0.30D)),
    SILT(
            2,
            "silt",
            blockTexture("silt"),
            0.90D,
            0.28D,
            -0.025D,
            0.62D,
            0.06D,
            0.62D,
            0.16D,
            -0.030D,
            -0.012D,
            0.014D,
            0.028D,
            0.22D,
            0.12D,
            0.058D,
            0.026D,
            0.052D,
            0.70F,
            3,
            9,
            13,
            new Vec3(0.38D, 0.76D, 0.38D)),
    THIN_MUD(
            3,
            "thin_mud",
            blockTexture("thin_mud"),
            0.91D,
            0.40D,
            -0.010D,
            0.82D,
            0.34D,
            0.84D,
            0.45D,
            -0.010D,
            0.003D,
            0.020D,
            0.038D,
            0.26D,
            0.18D,
            0.075D,
            0.048D,
            0.018D,
            0.55F,
            2,
            8,
            10,
            new Vec3(0.62D, 0.92D, 0.62D)),
    SHALLOW_MUD(
            4,
            "shallow_mud",
            blockTexture("shallow_mud"),
            0.91D,
            0.34D,
            -0.018D,
            0.66D,
            0.18D,
            0.70D,
            0.30D,
            -0.022D,
            -0.004D,
            0.019D,
            0.034D,
            0.26D,
            0.16D,
            0.070D,
            0.038D,
            0.032D,
            0.62F,
            3,
            9,
            12,
            new Vec3(0.48D, 0.86D, 0.48D)),
    TIDAL_MUD(
            5,
            "tidal_mud",
            blockTexture("tidal_mud"),
            0.93D,
            0.34D,
            -0.018D,
            0.74D,
            0.08D,
            0.76D,
            0.20D,
            -0.018D,
            0.002D,
            0.020D,
            0.036D,
            0.24D,
            0.14D,
            0.072D,
            0.034D,
            0.038D,
            0.64F,
            3,
            9,
            12,
            new Vec3(0.46D, 0.88D, 0.46D)),
    PEAT_BOG(
            6,
            "peat_bog",
            blockTexture("peat_bog"),
            0.89D,
            0.22D,
            -0.030D,
            0.40D,
            0.06D,
            0.48D,
            0.14D,
            -0.033D,
            -0.009D,
            0.010D,
            0.024D,
            0.18D,
            0.09D,
            0.048D,
            0.020D,
            0.064D,
            0.82F,
            4,
            10,
            15,
            new Vec3(0.32D, 0.72D, 0.32D)),
    LIVING_SLIME(
            7,
            "living_slime",
            blockTexture("living_slime"),
            0.93D,
            0.64D,
            -0.012D,
            0.92D,
            0.56D,
            0.96D,
            0.76D,
            -0.010D,
            0.008D,
            0.030D,
            0.060D,
            0.34D,
            0.28D,
            0.090D,
            0.070D,
            0.018D,
            0.54F,
            2,
            7,
            10,
            new Vec3(0.70D, 0.96D, 0.70D)),
    TAR(
            8,
            "tar",
            blockTexture("tar"),
            0.87D,
            0.12D,
            -0.020D,
            0.22D,
            0.01D,
            0.34D,
            0.06D,
            -0.024D,
            -0.004D,
            0.006D,
            0.016D,
            0.12D,
            0.05D,
            0.030D,
            0.010D,
            0.092D,
            0.94F,
            6,
            13,
            18,
            new Vec3(0.20D, 0.58D, 0.20D)),
    JUNGLE_QUICKSAND(
            9,
            "jungle_quicksand",
            blockTexture("jungle_quicksand"),
            0.89D,
            0.22D,
            -0.032D,
            0.46D,
            0.04D,
            0.56D,
            0.13D,
            -0.035D,
            -0.008D,
            0.012D,
            0.026D,
            0.20D,
            0.09D,
            0.050D,
            0.018D,
            0.074D,
            0.78F,
            5,
            10,
            15,
            new Vec3(0.30D, 0.70D, 0.30D)),
    INSECT_MOUND(
            10,
            "insect_mound",
            blockTexture("insect_mound"),
            0.625D,
            0.26D,
            -0.022D,
            0.50D,
            0.08D,
            0.58D,
            0.16D,
            -0.024D,
            -0.004D,
            0.014D,
            0.028D,
            0.20D,
            0.11D,
            0.055D,
            0.022D,
            0.058D,
            0.82F,
            4,
            9,
            14,
            new Vec3(0.36D, 0.74D, 0.36D)),
    RED_QUICKSAND(
            11,
            "red_quicksand",
            blockTexture("red_quicksand"),
            0.88D,
            0.23D,
            -0.031D,
            0.68D,
            0.025D,
            0.62D,
            0.11D,
            -0.026D,
            -0.005D,
            0.011D,
            0.027D,
            0.20D,
            0.09D,
            0.058D,
            0.019D,
            0.062D,
            0.64F,
            4,
            8,
            13,
            new Vec3(0.33D, 0.70D, 0.33D)),
    ASH_QUICKSAND(
            12,
            "ash_quicksand",
            blockTexture("ash_quicksand"),
            0.90D,
            0.25D,
            -0.032D,
            0.66D,
            0.025D,
            0.60D,
            0.11D,
            -0.028D,
            -0.005D,
            0.012D,
            0.028D,
            0.20D,
            0.09D,
            0.058D,
            0.019D,
            0.064D,
            0.62F,
            4,
            8,
            13,
            new Vec3(0.34D, 0.70D, 0.34D)),
    SOUL_SILT(
            13,
            "soul_silt",
            blockTexture("soul_silt"),
            0.90D,
            0.21D,
            -0.031D,
            0.52D,
            0.018D,
            0.52D,
            0.09D,
            -0.032D,
            -0.008D,
            0.011D,
            0.025D,
            0.18D,
            0.075D,
            0.050D,
            0.016D,
            0.074D,
            0.70F,
            5,
            10,
            15,
            new Vec3(0.29D, 0.68D, 0.29D)),
    GEL_CLAY(
            14,
            "gel_clay",
            blockTexture("gel_clay"),
            0.92D,
            0.17D,
            -0.018D,
            0.40D,
            0.012D,
            0.46D,
            0.07D,
            -0.020D,
            -0.004D,
            0.016D,
            0.030D,
            0.20D,
            0.09D,
            0.048D,
            0.014D,
            0.082D,
            0.82F,
            5,
            10,
            14,
            new Vec3(0.28D, 0.66D, 0.28D)),
    LIME_MUD(
            15,
            "lime_mud",
            blockTexture("lime_mud"),
            0.93D,
            0.35D,
            -0.016D,
            0.72D,
            0.14D,
            0.76D,
            0.25D,
            -0.018D,
            0.001D,
            0.021D,
            0.037D,
            0.27D,
            0.16D,
            0.072D,
            0.038D,
            0.034D,
            0.72F,
            3,
            9,
            12,
            new Vec3(0.50D, 0.86D, 0.50D)),
    END_SILT(
            16,
            "end_silt",
            blockTexture("end_silt"),
            0.90D,
            0.22D,
            -0.030D,
            0.54D,
            0.018D,
            0.54D,
            0.10D,
            -0.031D,
            -0.007D,
            0.012D,
            0.027D,
            0.20D,
            0.085D,
            0.052D,
            0.017D,
            0.072D,
            0.70F,
            5,
            10,
            15,
            new Vec3(0.30D, 0.69D, 0.30D)),
    SCULK_MIRE(
            17,
            "sculk_mire",
            blockTexture("sculk_mire"),
            0.92D,
            0.16D,
            -0.019D,
            0.38D,
            0.010D,
            0.43D,
            0.065D,
            -0.021D,
            -0.004D,
            0.016D,
            0.029D,
            0.19D,
            0.080D,
            0.046D,
            0.014D,
            0.080D,
            0.86F,
            5,
            10,
            14,
            new Vec3(0.27D, 0.64D, 0.27D)),
    GRAVEL_SILT(
            18,
            "gravel_silt",
            blockTexture("gravel_silt"),
            0.90D,
            0.24D,
            -0.029D,
            0.50D,
            0.016D,
            0.51D,
            0.095D,
            -0.030D,
            -0.007D,
            0.012D,
            0.026D,
            0.19D,
            0.080D,
            0.050D,
            0.016D,
            0.070D,
            0.68F,
            5,
            10,
            15,
            new Vec3(0.31D, 0.70D, 0.31D)),
    FUNGAL_MIRE(
            19,
            "fungal_mire",
            blockTexture("fungal_mire"),
            0.92D,
            0.15D,
            -0.018D,
            0.36D,
            0.010D,
            0.41D,
            0.065D,
            -0.020D,
            -0.004D,
            0.015D,
            0.028D,
            0.18D,
            0.075D,
            0.044D,
            0.013D,
            0.078D,
            0.84F,
            5,
            10,
            14,
            new Vec3(0.26D, 0.63D, 0.26D)),
    STONE_CLAY(
            20,
            "stone_clay",
            blockTexture("stone_clay"),
            0.91D,
            0.18D,
            -0.019D,
            0.42D,
            0.012D,
            0.47D,
            0.075D,
            -0.021D,
            -0.004D,
            0.016D,
            0.030D,
            0.20D,
            0.090D,
            0.047D,
            0.014D,
            0.081D,
            0.80F,
            5,
            10,
            14,
            new Vec3(0.28D, 0.66D, 0.28D)),
    PALE_MIRE(
            21,
            "pale_mire",
            blockTexture("pale_mire"),
            0.93D,
            0.18D,
            -0.020D,
            0.42D,
            0.012D,
            0.47D,
            0.075D,
            -0.022D,
            -0.004D,
            0.015D,
            0.029D,
            0.19D,
            0.085D,
            0.048D,
            0.014D,
            0.080D,
            0.82F,
            5,
            10,
            14,
            new Vec3(0.28D, 0.66D, 0.28D)),
    PEAT_SILT(
            22,
            "peat_silt",
            blockTexture("peat_silt"),
            0.91D,
            0.16D,
            -0.018D,
            0.38D,
            0.010D,
            0.42D,
            0.065D,
            -0.020D,
            -0.004D,
            0.014D,
            0.028D,
            0.18D,
            0.075D,
            0.045D,
            0.013D,
            0.078D,
            0.84F,
            5,
            10,
            14,
            new Vec3(0.26D, 0.63D, 0.26D)),
    TENDER_FLESH(
            23,
            "tender_flesh",
            blockTexture("tender_flesh"),
            0.92D,
            0.18D,
            -0.020D,
            0.40D,
            0.012D,
            0.45D,
            0.075D,
            -0.022D,
            -0.004D,
            0.015D,
            0.029D,
            0.19D,
            0.085D,
            0.048D,
            0.014D,
            0.080D,
            0.82F,
            5,
            10,
            14,
            new Vec3(0.28D, 0.66D, 0.28D)),
    MIRE(
            24,
            "mire",
            blockTexture("mire"),
            0.92D,
            0.16D,
            -0.018D,
            0.38D,
            0.010D,
            0.42D,
            0.065D,
            -0.020D,
            -0.004D,
            0.014D,
            0.028D,
            0.18D,
            0.075D,
            0.045D,
            0.013D,
            0.078D,
            0.84F,
            5,
            10,
            14,
            new Vec3(0.26D, 0.63D, 0.26D)),
    ASSIMILATION_SLIME(
            25,
            "assimilation_slime",
            blockTexture("assimilation_slime"),
            0.92D,
            0.22D,
            -0.022D,
            0.44D,
            0.014D,
            0.48D,
            0.080D,
            -0.024D,
            -0.004D,
            0.015D,
            0.030D,
            0.20D,
            0.090D,
            0.050D,
            0.015D,
            0.082D,
            0.84F,
            5,
            10,
            14,
            new Vec3(0.27D, 0.64D, 0.27D));

    public static final int COUNT = values().length;
    private static final SinkingMedium[] BY_ID = createByIdLookup();

    private final int id;
    private final String serializedName;
    private final ResourceLocation coverTexture;
    private final double surfaceOffset;
    private final double entityHorizontalScale;
    private final double entitySinkSpeed;
    private final double horizontalShallow;
    private final double horizontalDeep;
    private final double liftedHorizontalShallow;
    private final double liftedHorizontalDeep;
    private final double sinkSpeedShallow;
    private final double sinkSpeedDeep;
    private final double deepLiftAcceleration;
    private final double deepLiftMaxSpeed;
    private final double struggleUpShallow;
    private final double struggleUpDeep;
    private final double struggleHorizontalShallow;
    private final double struggleHorizontalDeep;
    private final double struggleSuction;
    private final float coverageScale;
    private final int struggleCooldownTicks;
    private final int liftTicksDeep;
    private final int liftTicksShallow;
    private final Vec3 stuckSpeedMultiplier;

    SinkingMedium(int id, String serializedName, ResourceLocation coverTexture, double surfaceOffset,
            double entityHorizontalScale, double entitySinkSpeed, double horizontalShallow, double horizontalDeep,
            double liftedHorizontalShallow, double liftedHorizontalDeep, double sinkSpeedShallow, double sinkSpeedDeep,
            double deepLiftAcceleration, double deepLiftMaxSpeed, double struggleUpShallow, double struggleUpDeep,
            double struggleHorizontalShallow, double struggleHorizontalDeep, double struggleSuction, float coverageScale,
            int struggleCooldownTicks, int liftTicksDeep, int liftTicksShallow, Vec3 stuckSpeedMultiplier) {
        this.id = id;
        this.serializedName = serializedName;
        this.coverTexture = coverTexture;
        this.surfaceOffset = surfaceOffset;
        this.entityHorizontalScale = entityHorizontalScale;
        this.entitySinkSpeed = entitySinkSpeed;
        this.horizontalShallow = horizontalShallow;
        this.horizontalDeep = horizontalDeep;
        this.liftedHorizontalShallow = liftedHorizontalShallow;
        this.liftedHorizontalDeep = liftedHorizontalDeep;
        this.sinkSpeedShallow = sinkSpeedShallow;
        this.sinkSpeedDeep = sinkSpeedDeep;
        this.deepLiftAcceleration = deepLiftAcceleration;
        this.deepLiftMaxSpeed = deepLiftMaxSpeed;
        this.struggleUpShallow = struggleUpShallow;
        this.struggleUpDeep = struggleUpDeep;
        this.struggleHorizontalShallow = struggleHorizontalShallow;
        this.struggleHorizontalDeep = struggleHorizontalDeep;
        this.struggleSuction = struggleSuction;
        this.coverageScale = coverageScale;
        this.struggleCooldownTicks = struggleCooldownTicks;
        this.liftTicksDeep = liftTicksDeep;
        this.liftTicksShallow = liftTicksShallow;
        this.stuckSpeedMultiplier = stuckSpeedMultiplier;
    }

    public static SinkingMedium byId(int id) {
        return id >= 0 && id < BY_ID.length && BY_ID[id] != null ? BY_ID[id] : MUD;
    }

    private static SinkingMedium[] createByIdLookup() {
        SinkingMedium[] lookup = new SinkingMedium[COUNT];
        for (SinkingMedium medium : values()) {
            if (medium.id >= 0 && medium.id < lookup.length) {
                lookup[medium.id] = medium;
            }
        }
        return lookup;
    }

    public int id() {
        return id;
    }

    public String serializedName() {
        return serializedName;
    }

    public ResourceLocation coverTexture() {
        return coverTexture;
    }

    /**
     * Texture sampled when painting player skin, armor, and cape.
     * Most media intentionally reuse their block texture; tender flesh uses a
     * pale saliva texture so the block and the residue have different materials.
     */
    public ResourceLocation skinCoverageTexture() {
        return this == TENDER_FLESH
                ? coverageTexture("tender_flesh_saliva")
                : coverTexture;
    }

    public double surfaceOffset() {
        return surfaceOffset;
    }

    public MudShapeType defaultShapeType() {
        return MudShapeType.FULL;
    }

    public MudBehaviorType defaultBehaviorType() {
        return switch (this) {
            case LIVING_SLIME -> MudBehaviorType.ELASTIC;
            case TAR -> MudBehaviorType.ADHESIVE;
            case INSECT_MOUND -> MudBehaviorType.SWARM;
            case TENDER_FLESH -> MudBehaviorType.CONTRACTILE;
            default -> MudBehaviorType.ORDINARY;
        };
    }

    public double entityHorizontalScale() {
        return entityHorizontalScale;
    }

    public double entitySinkSpeed() {
        return entitySinkSpeed;
    }

    public double horizontalScale(double depthFactor) {
        return lerp(depthFactor, horizontalShallow, horizontalDeep);
    }

    public double liftedHorizontalScale(double depthFactor) {
        return lerp(depthFactor, liftedHorizontalShallow, liftedHorizontalDeep);
    }

    public double sinkSpeed(double depthFactor) {
        return lerp(depthFactor, sinkSpeedShallow, sinkSpeedDeep);
    }

    public double deepLiftAcceleration() {
        return deepLiftAcceleration;
    }

    public double deepLiftMaxSpeed() {
        return deepLiftMaxSpeed;
    }

    public double struggleUp(double depthFactor) {
        return lerp(depthFactor, struggleUpShallow, struggleUpDeep);
    }

    public double struggleHorizontal(double depthFactor) {
        return lerp(depthFactor, struggleHorizontalShallow, struggleHorizontalDeep);
    }

    public double struggleSuction() {
        return struggleSuction;
    }

    public float coverageScale() {
        return coverageScale;
    }

    public int struggleCooldownTicks() {
        return struggleCooldownTicks;
    }

    public int liftTicks(double depthFactor) {
        return depthFactor > 1.05D ? liftTicksDeep : liftTicksShallow;
    }

    public Vec3 stuckSpeedMultiplier() {
        return stuckSpeedMultiplier;
    }

    public double verticalDrag(double depthFactor) {
        return switch (this) {
            case LIVING_SLIME -> lerp(depthFactor, 0.88D, 0.56D);
            case TAR -> lerp(depthFactor, 0.28D, 0.06D);
            case THIN_MUD -> lerp(depthFactor, 0.82D, 0.36D);
            case TIDAL_MUD -> lerp(depthFactor, 0.68D, 0.18D);
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SOUL_SILT ->
                    lerp(depthFactor, 0.42D, 0.08D);
            case PEAT_BOG -> lerp(depthFactor, 0.32D, 0.07D);
            default -> lerp(depthFactor, 0.48D, 0.12D);
        };
    }

    public double movementSinkScale(double depthFactor) {
        return switch (this) {
            case RED_QUICKSAND -> lerp(depthFactor, 0.035D, 0.115D);
            case ASH_QUICKSAND -> lerp(depthFactor, 0.052D, 0.142D);
            case SOUL_SILT -> lerp(depthFactor, 0.034D, 0.092D);
            case SOFT_QUICKSAND -> lerp(depthFactor, 0.050D, 0.135D);
            case JUNGLE_QUICKSAND -> lerp(depthFactor, 0.045D, 0.125D);
            case PEAT_BOG -> lerp(depthFactor, 0.030D, 0.085D);
            case TAR, GEL_CLAY -> lerp(depthFactor, 0.018D, 0.060D);
            case LIVING_SLIME -> lerp(depthFactor, 0.010D, 0.030D);
            case THIN_MUD, LIME_MUD -> lerp(depthFactor, 0.010D, 0.040D);
            default -> lerp(depthFactor, 0.020D, 0.070D);
        };
    }

    public double buoyancyDepthFactor() {
        return switch (this) {
            case THIN_MUD, TIDAL_MUD -> 0.18D;
            case MUD, SHALLOW_MUD, RED_QUICKSAND,
                    ASH_QUICKSAND, LIVING_SLIME, LIME_MUD -> 0.34D;
            case SOFT_QUICKSAND, PEAT_BOG, JUNGLE_QUICKSAND, SOUL_SILT,
                    ASSIMILATION_SLIME -> 0.55D;
            case TAR, GEL_CLAY -> 0.68D;
            case SILT, INSECT_MOUND, TENDER_FLESH -> 0.42D;
            case END_SILT -> 0.55D;
            case SCULK_MIRE -> 0.68D;
            case GRAVEL_SILT -> 0.55D;
            case FUNGAL_MIRE, STONE_CLAY, MIRE -> 0.68D;
            case PALE_MIRE -> 0.68D;
            case PEAT_SILT -> 0.55D;
        };
    }

    public double resurfaceForce() {
        return switch (this) {
            case LIVING_SLIME -> 0.052D;
            case THIN_MUD -> 0.045D;
            case TIDAL_MUD, SOFT_QUICKSAND -> 0.036D;
            case MUD, SHALLOW_MUD, LIME_MUD -> 0.026D;
            case RED_QUICKSAND, ASH_QUICKSAND, JUNGLE_QUICKSAND -> 0.028D;
            case PEAT_BOG, INSECT_MOUND -> 0.018D;
            case TAR, GEL_CLAY -> 0.006D;
            case SILT, SOUL_SILT -> 0.024D;
            case END_SILT -> 0.024D;
            case SCULK_MIRE -> 0.006D;
            case GRAVEL_SILT -> 0.024D;
            case FUNGAL_MIRE, TENDER_FLESH, ASSIMILATION_SLIME -> 0.010D;
            case STONE_CLAY -> 0.014D;
            case PALE_MIRE -> 0.012D;
            case PEAT_SILT, MIRE -> 0.010D;
        };
    }

    public double maxComfortDepthFactor() {
        return switch (this) {
            case THIN_MUD -> 0.20D;
            case SHALLOW_MUD -> 0.42D;
            case TIDAL_MUD -> 0.38D;
            case TAR -> 1.15D;
            default -> 0.95D;
        };
    }

    public double restDepthFactor() {
        return switch (this) {
            case THIN_MUD -> 0.15D;
            case TIDAL_MUD -> 0.23D;
            case LIVING_SLIME -> 0.28D;
            case MUD, RED_QUICKSAND, ASH_QUICKSAND, LIME_MUD -> 0.36D;
            case SHALLOW_MUD, SILT -> 0.42D;
            case INSECT_MOUND -> 0.58D;
            case SOFT_QUICKSAND, PEAT_BOG, JUNGLE_QUICKSAND -> 0.64D;
            case SOUL_SILT, ASSIMILATION_SLIME -> 0.78D;
            case TAR, GEL_CLAY -> 0.82D;
            case END_SILT -> 0.64D;
            case SCULK_MIRE -> 0.82D;
            case GRAVEL_SILT -> 0.64D;
            case FUNGAL_MIRE -> 0.78D;
            case STONE_CLAY -> 0.64D;
            case PALE_MIRE -> 0.72D;
            case PEAT_SILT, TENDER_FLESH -> 0.68D;
            case MIRE -> 0.78D;
        };
    }

    public double maxSinkDepthFactor() {
        return switch (this) {
            case THIN_MUD -> 0.18D;
            case MUD -> 0.32D;
            case SHALLOW_MUD -> 0.42D;
            case TIDAL_MUD -> 0.48D;
            case LIVING_SLIME -> 0.70D;
            case SILT -> 0.86D;
            case RED_QUICKSAND, ASH_QUICKSAND -> 1.10D;
            case SOFT_QUICKSAND -> 1.16D;
            case PEAT_BOG, INSECT_MOUND -> 1.22D;
            case JUNGLE_QUICKSAND -> 1.20D;
            case SOUL_SILT, ASSIMILATION_SLIME -> 1.45D;
            case TAR, GEL_CLAY -> 1.32D;
            case LIME_MUD -> 0.62D;
            case END_SILT -> 1.08D;
            case SCULK_MIRE -> 1.32D;
            case GRAVEL_SILT -> 1.08D;
            case FUNGAL_MIRE -> 1.38D;
            case STONE_CLAY -> 1.20D;
            case PALE_MIRE -> 1.20D;
            case PEAT_SILT, TENDER_FLESH -> 1.26D;
            case MIRE -> 1.45D;
        };
    }

    public double sinkSpring() {
        return switch (this) {
            case LIVING_SLIME -> 0.090D;
            case THIN_MUD, TIDAL_MUD -> 0.070D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SOUL_SILT -> 0.060D;
            case TAR, GEL_CLAY -> 0.038D;
            case PEAT_BOG -> 0.048D;
            default -> 0.055D;
        };
    }

    public double riseSpring() {
        return switch (this) {
            case LIVING_SLIME -> 0.105D;
            case THIN_MUD, TIDAL_MUD -> 0.082D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SOUL_SILT -> 0.070D;
            case TAR, GEL_CLAY -> 0.045D;
            case PEAT_BOG -> 0.054D;
            default -> 0.064D;
        };
    }

    public double movementDepthBonus(double horizontalSpeed) {
        double scale = switch (this) {
            case SOFT_QUICKSAND -> 3.10D;
            case RED_QUICKSAND, JUNGLE_QUICKSAND -> 2.70D;
            case ASH_QUICKSAND -> 3.20D;
            case SOUL_SILT -> 2.25D;
            case PEAT_BOG -> 2.10D;
            case TAR, GEL_CLAY -> 1.35D;
            case LIVING_SLIME -> 0.95D;
            case THIN_MUD, TIDAL_MUD, LIME_MUD -> 1.30D;
            default -> 1.80D;
        };
        double maxBonus = switch (this) {
            case THIN_MUD -> 0.08D;
            case LIVING_SLIME -> 0.10D;
            case TIDAL_MUD -> 0.16D;
            case TAR -> 0.18D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SOUL_SILT -> 0.34D;
            default -> 0.24D;
        };
        return Mth.clamp(horizontalSpeed * scale, 0.0D, maxBonus);
    }

    public double crouchDepthBonus() {
        return switch (this) {
            case THIN_MUD, LIVING_SLIME -> 0.06D;
            case TIDAL_MUD, SILT -> 0.10D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SOUL_SILT -> 0.22D;
            case TAR -> 0.12D;
            default -> 0.16D;
        };
    }

    public double agitationDepthBonus() {
        return switch (this) {
            case SOFT_QUICKSAND -> 0.34D;
            case RED_QUICKSAND, JUNGLE_QUICKSAND, SOUL_SILT -> 0.28D;
            case ASH_QUICKSAND -> 0.36D;
            case SILT -> 0.16D;
            default -> 0.08D;
        };
    }

    public double maxSinkSpeed() {
        return switch (this) {
            case THIN_MUD -> 0.060D;
            case LIVING_SLIME -> 0.055D;
            case TIDAL_MUD, SILT -> 0.080D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SOUL_SILT -> 0.105D;
            case TAR -> 0.070D;
            default -> 0.095D;
        };
    }

    public double qsrWalkSpeed(double depth) {
        double normal = Mth.clamp(depth / walkLockDepth(), 0.0D, 1.0D);
        double[] curve = switch (this) {
            case LIVING_SLIME -> new double[] {0.98D, 0.92D, 0.82D, 0.68D, 0.52D, 0.36D, 0.24D, 0.16D, 0.10D};
            case THIN_MUD -> new double[] {0.96D, 0.86D, 0.70D, 0.48D, 0.26D, 0.10D, 0.030D, 0.0D, 0.0D};
            case TAR -> new double[] {0.58D, 0.40D, 0.25D, 0.13D, 0.050D, 0.012D, 0.0D, 0.0D, 0.0D};
            case PEAT_BOG -> new double[] {0.82D, 0.70D, 0.52D, 0.32D, 0.14D, 0.034D, 0.004D, 0.0D, 0.0D};
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SILT, SOUL_SILT ->
                    new double[] {1.0D, 0.94D, 0.82D, 0.62D, 0.36D, 0.14D, 0.032D, 0.0D, 0.0D};
            default -> new double[] {0.90D, 0.78D, 0.60D, 0.38D, 0.18D, 0.054D, 0.006D, 0.0D, 0.0D};
        };
        return interpolate(normal, curve);
    }

    public double qsrRestDepthRatio() {
        return switch (this) {
            case THIN_MUD -> 0.58D;
            case SHALLOW_MUD -> 0.50D;
            case MUD -> 0.48D;
            case TIDAL_MUD -> 0.46D;
            case LIVING_SLIME -> 0.42D;
            case SILT -> 0.42D;
            case RED_QUICKSAND, ASH_QUICKSAND -> 0.44D;
            case SOFT_QUICKSAND, JUNGLE_QUICKSAND -> 0.48D;
            case PEAT_BOG, INSECT_MOUND -> 0.52D;
            case SOUL_SILT, ASSIMILATION_SLIME -> 0.56D;
            case TAR, GEL_CLAY -> 0.60D;
            case LIME_MUD -> 0.50D;
            case END_SILT -> 0.48D;
            case SCULK_MIRE -> 0.60D;
            case GRAVEL_SILT -> 0.48D;
            case FUNGAL_MIRE -> 0.56D;
            case STONE_CLAY -> 0.54D;
            case PALE_MIRE -> 0.54D;
            case PEAT_SILT, TENDER_FLESH -> 0.54D;
            case MIRE -> 0.56D;
        };
    }

    private double walkLockDepth() {
        return switch (this) {
            case LIVING_SLIME -> 1.85D;
            case THIN_MUD, TIDAL_MUD -> 1.35D;
            case TAR -> 1.18D;
            case PEAT_BOG -> 1.42D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SILT, SOUL_SILT -> 1.50D;
            default -> 1.38D;
        };
    }

    public double qsrVerticalSpeed(double depth, double agitation) {
        double normal = smooth(Mth.clamp(depth / 2.0D, 0.0D, 1.0D));
        if (liquefies()) {
            return Mth.clamp(Mth.lerp(normal, 0.18D, 0.065D) + 0.210D * Mth.clamp(agitation, 0.0D, 1.0D), 0.050D, 0.310D);
        }
        return switch (this) {
            case LIVING_SLIME -> Mth.lerp(normal, 0.46D, 0.22D);
            case THIN_MUD, TIDAL_MUD -> Mth.lerp(normal, 0.30D, 0.10D);
            case TAR -> Mth.lerp(normal, 0.10D, 0.025D);
            case PEAT_BOG -> Mth.lerp(normal, 0.16D, 0.045D);
            default -> Mth.lerp(normal, 0.22D, 0.065D);
        };
    }

    public double qsrSinkSpeed(double depth, double agitation) {
        if (liquefies()) {
            double normal = Mth.clamp(depth / Math.max(qsrMaxDepthFactor() * 1.8D, 0.35D), 0.0D, 1.0D);
            double base = switch (this) {
                case SOFT_QUICKSAND -> 0.0074D;
                case JUNGLE_QUICKSAND -> 0.0068D;
                case SILT -> 0.0056D;
                default -> 0.0062D;
            };
            double agitationBoost = switch (this) {
                case SOFT_QUICKSAND -> 0.0036D;
                case JUNGLE_QUICKSAND -> 0.0032D;
                case SILT -> 0.0018D;
                default -> 0.0028D;
            } * Mth.clamp(agitation, 0.0D, 1.0D);
            return (base + agitationBoost) * (1.0D - smooth(normal) * 0.84D);
        }

        double normal = Mth.clamp(depth / Math.max(qsrMaxDepthFactor() * 1.8D, 0.1D), 0.0D, 1.0D);
        double base = switch (this) {
            case THIN_MUD -> 0.0035D;
            case SHALLOW_MUD, TIDAL_MUD -> 0.0055D;
            case LIVING_SLIME -> 0.0022D;
            case TAR -> 0.0040D;
            case PEAT_BOG -> 0.0068D;
            default -> 0.0060D;
        };
        return base * (1.0D - smooth(normal) * 0.78D);
    }

    public double qsrMovementSinkScale(double depth) {
        double pressure = smooth(Mth.clamp(depth / 2.0D, 0.0D, 1.0D));
        return switch (this) {
            case LIVING_SLIME -> Mth.lerp(pressure, 0.011D, 0.003D);
            case TAR -> Mth.lerp(pressure, 0.040D, 0.014D);
            case PEAT_BOG -> Mth.lerp(pressure, 0.080D, 0.026D);
            case RED_QUICKSAND, SOFT_QUICKSAND, JUNGLE_QUICKSAND, SILT ->
                    Mth.lerp(pressure, 0.110D, 0.030D);
            default -> Mth.lerp(pressure, 0.070D, 0.020D);
        };
    }

    public double qsrCrouchSink(double depth) {
        return switch (this) {
            case RED_QUICKSAND, SOFT_QUICKSAND, JUNGLE_QUICKSAND -> 0.008D;
            case TAR -> 0.002D;
            case LIVING_SLIME -> 0.0005D;
            default -> 0.004D;
        } * (1.0D - smooth(Mth.clamp(depth / 2.2D, 0.0D, 1.0D)) * 0.65D);
    }

    public double qsrBuoyancyDepth() {
        return switch (this) {
            case THIN_MUD -> 0.38D;
            case SHALLOW_MUD, TIDAL_MUD -> 0.70D;
            case RED_QUICKSAND, ASH_QUICKSAND, SILT -> 0.92D;
            case SOFT_QUICKSAND, JUNGLE_QUICKSAND, SOUL_SILT -> 1.14D;
            case LIVING_SLIME -> 0.66D;
            case TAR -> 1.52D;
            case PEAT_BOG -> 1.56D;
            default -> 1.12D;
        };
    }

    public double qsrMaxDepthFactor() {
        return maxSinkDepthFactor();
    }

    public double qsrBuoyancyStrength(double depth) {
        return switch (this) {
            case LIVING_SLIME -> 0.080D;
            case THIN_MUD, TIDAL_MUD -> 0.060D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SILT, SOUL_SILT -> 0.050D;
            case TAR -> 0.020D;
            case PEAT_BOG -> 0.028D;
            default -> 0.040D;
        };
    }

    public double qsrPassiveFloat(double depth) {
        return switch (this) {
            case LIVING_SLIME -> 0.018D;
            case THIN_MUD, TIDAL_MUD -> 0.010D;
            case TAR -> 0.003D;
            case PEAT_BOG -> 0.006D;
            default -> 0.008D;
        };
    }

    public double qsrStruggleCarry(double depth) {
        return switch (this) {
            case LIVING_SLIME -> 0.010D;
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND, SOUL_SILT -> 0.007D;
            case TAR -> 0.002D;
            default -> 0.004D;
        };
    }

    public Vector3f particleColor() {
        return switch (this) {
            case RED_QUICKSAND -> rgb(178, 76, 43);
            case ASH_QUICKSAND -> rgb(108, 105, 102);
            case SOUL_SILT -> rgb(87, 65, 54);
            case SOFT_QUICKSAND -> rgb(202, 184, 121);
            case SILT -> rgb(126, 122, 108);
            case JUNGLE_QUICKSAND -> rgb(112, 105, 62);
            case THIN_MUD -> rgb(116, 82, 53);
            case SHALLOW_MUD -> rgb(88, 63, 43);
            case TIDAL_MUD -> rgb(82, 89, 82);
            case GEL_CLAY -> rgb(93, 118, 123);
            case LIME_MUD -> rgb(177, 172, 143);
            case END_SILT -> rgb(201, 194, 126);
            case SCULK_MIRE -> rgb(18, 62, 68);
            case GRAVEL_SILT -> rgb(132, 128, 125);
            case FUNGAL_MIRE -> rgb(91, 78, 76);
            case STONE_CLAY -> rgb(133, 132, 127);
            case PALE_MIRE -> rgb(171, 166, 153);
            case PEAT_SILT -> rgb(65, 50, 39);
            case PEAT_BOG -> rgb(48, 37, 25);
            case LIVING_SLIME -> rgb(84, 165, 67);
            case TAR -> rgb(14, 13, 13);
            default -> rgb(88, 67, 39);
        };
    }

    public float particleScale() {
        return switch (this) {
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    SILT, SOUL_SILT, JUNGLE_QUICKSAND -> 0.62F;
            case TAR -> 0.78F;
            default -> 0.70F;
        };
    }

    public double controlledSinkSpeed(double depthFactor) {
        return switch (this) {
            case THIN_MUD -> lerp(depthFactor, 0.010D, 0.022D);
            case SHALLOW_MUD, TIDAL_MUD -> lerp(depthFactor, 0.012D, 0.030D);
            case LIVING_SLIME -> lerp(depthFactor, 0.012D, 0.034D);
            case RED_QUICKSAND, SILT -> lerp(depthFactor, 0.014D, 0.038D);
            case SOFT_QUICKSAND, JUNGLE_QUICKSAND -> lerp(depthFactor, 0.016D, 0.046D);
            case PEAT_BOG -> lerp(depthFactor, 0.014D, 0.040D);
            case TAR -> lerp(depthFactor, 0.006D, 0.024D);
            default -> lerp(depthFactor, 0.012D, 0.034D);
        };
    }

    public double controlledRiseSpeed(double depthFactor) {
        return switch (this) {
            case THIN_MUD -> lerp(depthFactor, 0.024D, 0.046D);
            case LIVING_SLIME -> lerp(depthFactor, 0.038D, 0.070D);
            case RED_QUICKSAND, SOFT_QUICKSAND, JUNGLE_QUICKSAND, SILT ->
                    lerp(depthFactor, 0.022D, 0.048D);
            case PEAT_BOG -> lerp(depthFactor, 0.014D, 0.034D);
            case TAR -> lerp(depthFactor, 0.006D, 0.018D);
            default -> lerp(depthFactor, 0.018D, 0.040D);
        };
    }

    public double equilibriumLiftSpeed(double depthFactor) {
        return switch (this) {
            case LIVING_SLIME -> lerp(depthFactor, 0.060D, 0.080D);
            case THIN_MUD, TIDAL_MUD -> lerp(depthFactor, 0.050D, 0.066D);
            case TAR -> lerp(depthFactor, 0.024D, 0.040D);
            case PEAT_BOG -> lerp(depthFactor, 0.040D, 0.056D);
            case RED_QUICKSAND, SOFT_QUICKSAND, JUNGLE_QUICKSAND, SILT ->
                    lerp(depthFactor, 0.050D, 0.070D);
            default -> lerp(depthFactor, 0.045D, 0.064D);
        };
    }

    public double struggleReliefDepth() {
        return switch (this) {
            case RED_QUICKSAND, SOFT_QUICKSAND, JUNGLE_QUICKSAND -> 0.046D;
            case LIVING_SLIME -> 0.060D;
            case TAR -> 0.016D;
            case PEAT_BOG -> 0.028D;
            default -> 0.036D;
        };
    }

    public boolean liquefies() {
        return this == RED_QUICKSAND || this == ASH_QUICKSAND
                || this == SOFT_QUICKSAND || this == JUNGLE_QUICKSAND
                || this == SILT || this == SOUL_SILT || this == END_SILT || this == GRAVEL_SILT;
    }

    public boolean opaqueCoverage() {
        return switch (this) {
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    SILT, SOUL_SILT, JUNGLE_QUICKSAND, END_SILT, GRAVEL_SILT,
                    INSECT_MOUND, ASSIMILATION_SLIME -> false;
            default -> true;
        };
    }

    public boolean opaqueBlock() {
        return this != LIVING_SLIME && this != ASSIMILATION_SLIME;
    }

    public boolean translucentSkinCoverage() {
        return this == TENDER_FLESH || !opaqueCoverage()
                || this == LIVING_SLIME;
    }

    public double defaultCoverageOpacity() {
        if (this == LIVING_SLIME || this == ASSIMILATION_SLIME) {
            return 0.60D;
        }
        return opaqueCoverage() ? 1.0D : 0.70D;
    }

    /**
     * Default time for coverage to fade after contact ends. Zero keeps the
     * familiar persistent behavior; each medium can opt in without changing
     * the shared coverage implementation.
     */
    public int defaultCoverageNaturalFadeTicks() {
        return this == TENDER_FLESH ? 600 : 0;
    }

    public float agitationDecay() {
        return switch (this) {
            case RED_QUICKSAND, ASH_QUICKSAND, SOFT_QUICKSAND,
                    JUNGLE_QUICKSAND -> 0.010F;
            case SILT, SOUL_SILT -> 0.018F;
            default -> 0.026F;
        };
    }

    public float movementAgitation(double horizontalSpeed) {
        if (!liquefies()) {
            return (float) Mth.clamp(horizontalSpeed * 0.30D, 0.0D, 0.026D);
        }
        double scale = switch (this) {
            case SOFT_QUICKSAND -> 0.90D;
            case ASH_QUICKSAND -> 0.98D;
            case JUNGLE_QUICKSAND -> 0.78D;
            case SILT, SOUL_SILT -> 0.46D;
            default -> 0.64D;
        };
        return (float) Mth.clamp(horizontalSpeed * scale, 0.0D, 0.08D);
    }

    public float lookAgitation(double angleDelta) {
        double scale = switch (this) {
            case SOFT_QUICKSAND -> 0.0016D;
            case RED_QUICKSAND, JUNGLE_QUICKSAND -> 0.00135D;
            case ASH_QUICKSAND -> 0.00155D;
            case SILT, SOUL_SILT -> 0.0010D;
            case PEAT_BOG -> 0.0009D;
            case TAR -> 0.00045D;
            case LIVING_SLIME -> 0.0007D;
            default -> 0.00075D;
        };
        double cap = liquefies() ? 0.050D : 0.032D;
        return (float) Mth.clamp(angleDelta * scale, 0.0D, cap);
    }

    public float crouchAgitation() {
        return switch (this) {
            case SOFT_QUICKSAND -> 0.045F;
            case RED_QUICKSAND, JUNGLE_QUICKSAND -> 0.038F;
            case ASH_QUICKSAND -> 0.044F;
            case SILT, SOUL_SILT -> 0.026F;
            case PEAT_BOG -> 0.024F;
            case TAR -> 0.010F;
            case LIVING_SLIME -> 0.014F;
            default -> 0.018F;
        };
    }

    public float struggleAgitation() {
        return switch (this) {
            case SOFT_QUICKSAND -> 0.18F;
            case RED_QUICKSAND, JUNGLE_QUICKSAND, SOUL_SILT -> 0.15F;
            case ASH_QUICKSAND -> 0.19F;
            case SILT -> 0.08F;
            default -> 0.03F;
        };
    }

    public double agitationSinkBoost() {
        return switch (this) {
            case SOFT_QUICKSAND -> 0.038D;
            case JUNGLE_QUICKSAND -> 0.034D;
            case RED_QUICKSAND, SOUL_SILT -> 0.030D;
            case ASH_QUICKSAND -> 0.040D;
            case SILT -> 0.016D;
            default -> 0.0D;
        };
    }

    public double struggleRhythmScale() {
        return switch (this) {
            case PEAT_BOG -> 0.035D;
            case RED_QUICKSAND, SOFT_QUICKSAND, JUNGLE_QUICKSAND -> 0.050D;
            case LIVING_SLIME -> 0.095D;
            case TAR -> 0.018D;
            default -> 0.040D;
        };
    }

    public double struggleBurstChance(double depthFactor) {
        double base = switch (this) {
            case RED_QUICKSAND, SOFT_QUICKSAND, JUNGLE_QUICKSAND -> 0.20D;
            case PEAT_BOG -> 0.08D;
            case LIVING_SLIME -> 0.24D;
            case TAR -> 0.04D;
            default -> 0.06D;
        };
        return base * Mth.clamp(0.45D + depthFactor * 0.55D, 0.35D, 1.2D);
    }

    public double struggleBurstUp() {
        return switch (this) {
            case RED_QUICKSAND, SOFT_QUICKSAND, JUNGLE_QUICKSAND -> 0.18D;
            case LIVING_SLIME -> 0.30D;
            case TAR -> 0.035D;
            default -> 0.060D;
        };
    }

    public double slurpChance(double depthFactor, double horizontalSpeed) {
        double depthPressure = Mth.clamp(depthFactor / Math.max(maxComfortDepthFactor(), 0.1D), 0.0D, 1.0D);
        double motionPressure = Mth.clamp(horizontalSpeed * 8.0D, 0.0D, 1.0D);
        double base = switch (this) {
            case PEAT_BOG -> 0.014D;
            case JUNGLE_QUICKSAND -> 0.012D;
            case TAR -> 0.006D;
            default -> 0.0D;
        };
        return base * (0.35D + motionPressure * 0.65D) * (1.0D - depthPressure * 0.55D);
    }

    public double slurpStrength() {
        return switch (this) {
            case PEAT_BOG -> 0.044D;
            case JUNGLE_QUICKSAND -> 0.038D;
            case TAR -> 0.026D;
            default -> 0.0D;
        };
    }

    public double wobbleHorizontal(double depthFactor) {
        return switch (this) {
            case LIVING_SLIME -> lerp(depthFactor, 0.004D, 0.018D);
            case PEAT_BOG -> lerp(depthFactor, 0.001D, 0.006D);
            case JUNGLE_QUICKSAND -> lerp(depthFactor, 0.001D, 0.004D);
            default -> 0.0D;
        };
    }

    private static ResourceLocation blockTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "textures/block/" + name + ".png");
    }

    private static ResourceLocation coverageTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "textures/coverage/" + name + ".png");
    }

    private static double lerp(double depthFactor, double shallow, double deep) {
        double clamped = Math.max(0.0D, Math.min(depthFactor, 1.0D));
        return shallow + (deep - shallow) * clamped;
    }

    private static double interpolate(double value, double[] points) {
        if (points.length == 0) {
            return 0.0D;
        }
        if (points.length == 1 || value <= 0.0D) {
            return points[0];
        }
        if (value >= 1.0D) {
            return points[points.length - 1];
        }

        double scaled = value * (points.length - 1);
        int left = Mth.floor(scaled);
        int right = Math.min(points.length - 1, left + 1);
        return Mth.lerp(scaled - left, points[left], points[right]);
    }

    private static double smooth(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static Vector3f rgb(int red, int green, int blue) {
        return new Vector3f(red / 255.0F, green / 255.0F, blue / 255.0F);
    }
}
