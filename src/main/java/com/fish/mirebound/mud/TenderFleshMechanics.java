package com.fish.mirebound.mud;

import net.minecraft.util.Mth;

/** Deterministic contractile layer composed over the ordinary sinking solver. */
public final class TenderFleshMechanics {
    private static final int PILLAR_COUNT = 4;
    private static final int PILLAR_VALUE_BITS = 3;
    private static final int PILLAR_VALUE_MASK = (1 << PILLAR_VALUE_BITS) - 1;
    private static final int MAXIMUM_PILLAR_HITS = 6;

    private TenderFleshMechanics() {
    }

    public static StepResult step(TenderFleshProfile profile, TenderFleshRuntimeState state, Input input) {
        double contraction = contraction(profile, input.gameTick());
        double relaxation = 1.0D - contraction;
        double activity = Mth.clamp(
                input.horizontalSpeed() * 4.0D
                        + input.lookDelta() / 18.0D
                        + (input.holdingStruggle() ? 0.52D : 0.0D),
                0.0D,
                1.0D);
        boolean active = activity >= profile.activityThreshold();
        double depthPresence = smooth(Mth.clamp(
                (input.depthProgress() - 0.015D) / 0.42D,
                0.0D,
                1.0D));
        double previousPressure = state.pressure;
        if (depthPresence <= 0.0D) {
            state.pressure = Math.max(0.0D,
                    state.pressure - profile.pressureDecay() * 2.0D);
        } else if (active) {
            state.pressure = Math.min(1.0D, state.pressure
                    + profile.pressureGain() * depthPresence * (0.25D + activity * 0.75D));
        } else {
            state.pressure = Math.max(0.0D,
                    state.pressure - profile.pressureDecay() * (0.70D + (1.0D - activity) * 0.30D));
        }
        state.calmness = Mth.clamp(1.0D - state.pressure, 0.0D, 1.0D);
        if (depthPresence <= 0.0D) {
            state.wrap = Math.max(0.0D, state.wrap - profile.wrapDecay() * 2.0D);
        } else if (active) {
            state.wrap = Math.min(1.0D, state.wrap
                    + profile.wrapGain() * depthPresence * (0.35D + activity * 0.65D));
        } else {
            state.wrap = Math.max(0.0D, state.wrap - profile.wrapDecay());
        }

        double opportunity = smooth(Mth.clamp(
                (relaxation - profile.escapeRelaxationThreshold())
                        / Math.max(0.05D, 1.0D - profile.escapeRelaxationThreshold()),
                0.0D,
                1.0D));
        opportunity *= 0.55D + state.calmness * 0.45D;
        double struggleSinkFactor = input.holdingStruggle()
                ? 1.0D - Mth.clamp(profile.struggleSinkSuppression(), 0.0D, 1.0D)
                : 1.0D;
        ReleaseOutcome releaseOutcome = ReleaseOutcome.NONE;
        if (input.releaseCharge() >= 0.0D) {
            double releaseStrength = 0.45D + 0.55D * Mth.clamp(input.releaseCharge(), 0.0D, 1.0D);
            if (opportunity >= profile.releaseOpportunityThreshold()) {
                state.wrap = Math.max(0.0D, state.wrap
                        - profile.goodReleaseWrapLoss() * opportunity * releaseStrength);
                state.pressure = Math.max(0.0D, state.pressure
                        - profile.goodReleaseWrapLoss() * 0.80D * opportunity * releaseStrength);
                state.escapeVelocity = Math.max(
                        state.escapeVelocity,
                        profile.escapePulseSpeed() * releaseStrength);
                state.escapeTicks = profile.escapePulseTicks();
                releaseOutcome = ReleaseOutcome.EFFECTIVE;
            } else {
                state.wrap = Math.min(1.0D, state.wrap
                        + profile.badReleaseWrapGain() * (1.0D - opportunity) * releaseStrength);
                state.pressure = Math.min(1.0D, state.pressure
                        + profile.badReleaseWrapGain() * 0.70D * (1.0D - opportunity)
                        * releaseStrength);
                state.escapeVelocity = 0.0D;
                state.escapeTicks = 0;
                releaseOutcome = ReleaseOutcome.ABSORBED;
            }
        }
        double contractionPressure = contraction * profile.contractionStrength()
                * depthPresence * (0.32D + state.wrap * 0.42D + state.pressure * 0.26D);
        double walkScale = input.baselineWalkScale()
                * (1.0D - state.wrap * profile.wrapMovementResistance()
                        * (0.42D + contractionPressure * 0.58D)
                        - state.pressure * profile.pressureResistance());
        walkScale = Mth.clamp(walkScale, 0.0D, 1.20D);
        updateEnclosure(profile, state, input, walkScale);

        double y = input.baselineMotionY();
        if (input.releaseCharge() >= 0.0D && y > 0.0D) {
            double multiplier = Mth.lerp(
                    opportunity,
                    profile.escapeBadMultiplier(),
                    profile.escapeGoodMultiplier());
            y *= multiplier;
        } else if (input.remainingDepth() > 0.0D && depthPresence > 0.0D && y <= 0.0D) {
            double sink = profile.contractionSinkSpeed() * contractionPressure
                    * (0.45D + activity * 0.55D) * struggleSinkFactor
                    + profile.pressureSinkSpeed() * state.pressure * depthPresence
                    * struggleSinkFactor;
            y = Math.max(-input.remainingDepth(), Math.min(0.0D, y) - sink);
        }
        if (state.escapeTicks > 0) {
            y = Math.max(y, state.escapeVelocity);
            state.escapeVelocity *= profile.escapePulseDamping();
            state.escapeTicks--;
        } else {
            state.escapeVelocity = 0.0D;
        }

        boolean pressureBeat = false;
        if (state.pressure < 0.58D) {
            state.pressureBeatArmed = true;
        } else if (state.pressureBeatArmed && state.pressure >= 0.82D) {
            state.pressureBeatArmed = false;
            pressureBeat = true;
        }
        boolean calmBeat = false;
        if (state.pressure > 0.56D) {
            state.calmBeatArmed = true;
        } else if (state.calmBeatArmed && state.pressure <= 0.22D) {
            state.calmBeatArmed = false;
            calmBeat = true;
        }

        boolean pulseBeat = false;
        if (contraction < 0.35D) {
            state.beatArmed = true;
        } else if (state.beatArmed && contraction >= 0.78D) {
            state.beatArmed = false;
            pulseBeat = true;
        }
        boolean relaxationBeat = false;
        if (opportunity < profile.releaseOpportunityThreshold() * 0.65D) {
            state.relaxationBeatArmed = true;
        } else if (state.relaxationBeatArmed
                && opportunity >= profile.releaseOpportunityThreshold()) {
            state.relaxationBeatArmed = false;
            relaxationBeat = true;
        }
        state.contraction = contraction;
        state.escapeOpportunity = opportunity;
        double baselineWalkScale = input.baselineWalkScale();
        double movementRatio = baselineWalkScale <= 1.0E-6D
                ? 0.0D
                : Mth.clamp(walkScale / baselineWalkScale, 0.0D, 1.20D);
        double motionX = input.baselineMotionX() * movementRatio;
        double motionZ = input.baselineMotionZ() * movementRatio;
        if (state.enclosureActive) {
            // Once all four pillars have closed around the player, the enclosure
            // owns horizontal movement. Vertical sinking and struggle remain
            // available until every distinct pillar has been struck.
            motionX = 0.0D;
            motionZ = 0.0D;
            walkScale = 0.0D;
        }
        return new StepResult(
                motionX,
                y,
                motionZ,
                walkScale,
                state.wrap,
                contraction,
                opportunity,
                pulseBeat,
                relaxationBeat,
                releaseOutcome,
                state.pressure,
                state.calmness,
                pressureBeat,
                calmBeat);
    }

    private static void updateEnclosure(TenderFleshProfile profile,
            TenderFleshRuntimeState state, Input input, double walkScale) {
        if (state.enclosureStrikeCooldownTicks > 0) {
            state.enclosureStrikeCooldownTicks--;
        }
        if (state.enclosureRetreating) {
            tickRetreat(profile, state);
            return;
        }
        if (state.enclosureCooldownTicks > 0) {
            state.enclosureCooldownTicks--;
            state.enclosureActive = false;
            state.enclosureProgress = 0.0D;
            state.enclosureCenterSet = false;
            state.enclosurePlayerCenterSet = false;
            state.enclosureDimension = null;
            return;
        }

        boolean wasActive = state.enclosureActive;
        if (!state.enclosureActive
                && input.enclosureAllowed()
                && walkScale <= profile.enclosureWalkScaleThreshold()) {
            state.enclosureActive = true;
        }

        if (state.enclosureActive && (!wasActive || !state.enclosureCenterSet)) {
            state.enclosureCenterX = input.positionX();
            state.enclosureCenterY = input.positionY();
            state.enclosureCenterZ = input.positionZ();
            state.enclosureCenterSet = true;
            state.enclosurePlayerX = input.playerX();
            state.enclosurePlayerZ = input.playerZ();
            state.enclosurePlayerCenterSet = true;
        }
        if (state.enclosureActive && !wasActive) {
            long durabilitySeed = input.gameTick()
                    ^ Double.doubleToLongBits(input.positionX())
                    ^ Long.rotateLeft(Double.doubleToLongBits(input.positionY()), 21)
                    ^ Long.rotateLeft(Double.doubleToLongBits(input.positionZ()), 42);
            initializePillarDurability(profile, state, durabilitySeed);
        }
        double target = state.enclosureActive ? 1.0D : 0.0D;
        double rate = target >= state.enclosureProgress
                ? profile.enclosureRiseRate()
                : profile.enclosureWithdrawRate();
        state.enclosureProgress = approach(
                state.enclosureProgress, target, rate);
        if (!state.enclosureActive && state.enclosureProgress <= 1.0E-4D) {
            state.enclosureProgress = 0.0D;
            clearPillarDurability(state);
            state.enclosureCenterSet = false;
            state.enclosurePlayerCenterSet = false;
            state.enclosureDimension = null;
        }
    }

    static boolean strikePillar(TenderFleshProfile profile,
            TenderFleshRuntimeState state, int pillarIndex) {
        if (pillarIndex < 0 || pillarIndex >= 4
                || !state.enclosureActive
                || state.enclosureRetreating
                || state.enclosureCooldownTicks > 0
                || state.enclosureStrikeCooldownTicks > 0) {
            return false;
        }
        double hitStart = Math.max(0.12D,
                Math.min(0.60D, profile.enclosureCollisionStart() * 0.50D));
        if (state.enclosureProgress < hitStart) {
            return false;
        }
        int bit = 1 << pillarIndex;
        if ((state.enclosureBrokenMask & bit) != 0) {
            return false;
        }
        if (state.enclosurePillarRequiredHitsPacked == 0) {
            initializePillarDurability(profile, state, 0L);
        }
        int requiredHits = pillarPackedValue(
                state.enclosurePillarRequiredHitsPacked, pillarIndex);
        int damage = Math.min(requiredHits, pillarPackedValue(
                state.enclosurePillarDamagePacked, pillarIndex) + 1);
        state.enclosurePillarDamagePacked = setPillarPackedValue(
                state.enclosurePillarDamagePacked, pillarIndex, damage);
        if (damage >= requiredHits) {
            state.enclosureBrokenMask |= bit;
        }
        state.enclosureStrikeCooldownTicks = profile.enclosureStrikeCooldownTicks();
        if (state.enclosureBrokenMask == 0x0F) {
            state.enclosureActive = false;
            state.enclosureRetreating = true;
        }
        return true;
    }

    static boolean beginForcedRetreat(TenderFleshRuntimeState state) {
        if (!state.enclosureActive || state.enclosureRetreating) {
            return false;
        }
        state.enclosureActive = false;
        state.enclosureRetreating = true;
        return true;
    }

    static void tickRetreat(TenderFleshProfile profile,
            TenderFleshRuntimeState state) {
        if (!state.enclosureRetreating) {
            return;
        }
        state.enclosureActive = false;
        state.enclosureProgress = approach(
                state.enclosureProgress, 0.0D, profile.enclosureWithdrawRate());
        if (state.enclosureProgress > 1.0E-4D) {
            return;
        }
        state.enclosureProgress = 0.0D;
        state.enclosureRetreating = false;
        clearPillarDurability(state);
        state.enclosureCooldownTicks = profile.enclosureCooldownTicks();
        state.enclosureCenterSet = false;
        state.enclosurePlayerCenterSet = false;
        state.enclosureDimension = null;
    }

    public static double enclosureHeight(TenderFleshProfile profile,
            double exposedHeight) {
        double minimum = profile.enclosureMinHeightPixels() / 16.0D;
        double maximum = Math.max(minimum,
                profile.enclosureMaxHeightPixels() / 16.0D);
        return Mth.clamp(exposedHeight, minimum, maximum);
    }

    static void initializePillarDurability(TenderFleshProfile profile,
            TenderFleshRuntimeState state, long seed) {
        int minimum = Mth.clamp(Math.min(
                profile.enclosureMinPillarHits(), profile.enclosureMaxPillarHits()),
                1, MAXIMUM_PILLAR_HITS);
        int maximum = Mth.clamp(Math.max(
                profile.enclosureMinPillarHits(), profile.enclosureMaxPillarHits()),
                minimum, MAXIMUM_PILLAR_HITS);
        int span = maximum - minimum + 1;
        int[] requirements = new int[PILLAR_COUNT];
        for (int index = 0; index < PILLAR_COUNT; index++) {
            requirements[index] = minimum + index % span;
        }
        long random = mixDurabilitySeed(seed);
        for (int index = PILLAR_COUNT - 1; index > 0; index--) {
            random = mixDurabilitySeed(random + index);
            int swapIndex = (int) Math.floorMod(random, index + 1L);
            int swap = requirements[index];
            requirements[index] = requirements[swapIndex];
            requirements[swapIndex] = swap;
        }
        state.enclosureBrokenMask = 0;
        state.enclosurePillarDamagePacked = 0;
        state.enclosurePillarRequiredHitsPacked = 0;
        for (int index = 0; index < PILLAR_COUNT; index++) {
            state.enclosurePillarRequiredHitsPacked = setPillarPackedValue(
                    state.enclosurePillarRequiredHitsPacked, index, requirements[index]);
        }
    }

    public static int pillarPackedValue(int packed, int pillarIndex) {
        if (pillarIndex < 0 || pillarIndex >= PILLAR_COUNT) {
            return 0;
        }
        return packed >>> (pillarIndex * PILLAR_VALUE_BITS) & PILLAR_VALUE_MASK;
    }

    public static double pillarDamageFraction(
            int damagePacked, int requiredHitsPacked, int pillarIndex) {
        int requiredHits = pillarPackedValue(requiredHitsPacked, pillarIndex);
        if (requiredHits <= 0) {
            return 0.0D;
        }
        return Mth.clamp((double) pillarPackedValue(damagePacked, pillarIndex)
                / requiredHits, 0.0D, 1.0D);
    }

    private static int setPillarPackedValue(int packed, int pillarIndex, int value) {
        int shift = pillarIndex * PILLAR_VALUE_BITS;
        int mask = PILLAR_VALUE_MASK << shift;
        return packed & ~mask | (Mth.clamp(value, 0, PILLAR_VALUE_MASK) << shift);
    }

    private static void clearPillarDurability(TenderFleshRuntimeState state) {
        state.enclosureBrokenMask = 0;
        state.enclosurePillarDamagePacked = 0;
        state.enclosurePillarRequiredHitsPacked = 0;
    }

    private static long mixDurabilitySeed(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    public static double contraction(TenderFleshProfile profile, long gameTick) {
        int period = Math.max(1, profile.pulsePeriodTicks());
        double phase = Math.floorMod(gameTick, period) / (double) period;
        double wave = 0.5D - 0.5D * Math.cos(phase * Math.PI * 2.0D);
        return smooth(wave);
    }

    public static double surfacePulse(TenderFleshProfile profile, long gameTick) {
        return 1.0D + contraction(profile, gameTick) * profile.surfacePulseStrength();
    }

    private static double smooth(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static double approach(double current, double target, double rate) {
        double next = current + (target - current) * Mth.clamp(rate, 0.0D, 1.0D);
        return Math.abs(target - next) <= 1.0E-4D ? target : next;
    }

    record Input(
            long gameTick,
            double depthProgress,
            double remainingDepth,
            double horizontalSpeed,
            double lookDelta,
            boolean holdingStruggle,
            double releaseCharge,
            double baselineMotionX,
            double baselineMotionY,
            double baselineMotionZ,
            double baselineWalkScale,
            boolean enclosureAllowed,
            double positionX,
            double positionY,
            double positionZ,
            double playerX,
            double playerZ) {
        Input(
                long gameTick,
                double depthProgress,
                double remainingDepth,
                double horizontalSpeed,
                double lookDelta,
                boolean holdingStruggle,
                double releaseCharge,
                double baselineMotionX,
                double baselineMotionY,
                double baselineMotionZ,
                double baselineWalkScale) {
            this(gameTick, depthProgress, remainingDepth, horizontalSpeed, lookDelta,
                    holdingStruggle, releaseCharge,
                    baselineMotionX, baselineMotionY, baselineMotionZ, baselineWalkScale,
                    false, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    record StepResult(
            double motionX,
            double motionY,
            double motionZ,
            double walkScale,
            double wrap,
            double contraction,
            double escapeOpportunity,
            boolean pulseBeat,
            boolean relaxationBeat,
            ReleaseOutcome releaseOutcome,
            double pressure,
            double calmness,
            boolean pressureBeat,
            boolean calmBeat) {
    }

    public enum ReleaseOutcome {
        NONE,
        EFFECTIVE,
        ABSORBED
    }
}
