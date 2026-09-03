package com.fish.mirebound.mud;

import com.fish.mirebound.mud.behavior.HiddenThreatTemplate;
import com.fish.mirebound.mud.behavior.QuietCrouchEscapeTemplate;
import com.fish.mirebound.mud.behavior.ResonancePulseTemplate;
import com.fish.mirebound.mud.behavior.TimedRestraintTemplate;
import net.minecraft.world.phys.Vec3;

/** Ephemeral per-player state for one sculk mire contact. It is never persisted. */
public final class SculkMireRuntimeState {
    private final QuietCrouchEscapeTemplate.State escape = new QuietCrouchEscapeTemplate.State();
    private final HiddenThreatTemplate.State threat = new HiddenThreatTemplate.State();
    private final TimedRestraintTemplate.State restraint = new TimedRestraintTemplate.State();
    private final ResonancePulseTemplate.State resonance = new ResonancePulseTemplate.State();
    private Vec3 surfacePoint = Vec3.ZERO;
    private Vec3 surfaceNormal = new Vec3(0.0D, 1.0D, 0.0D);
    private Vec3 surfaceAxisX = new Vec3(1.0D, 0.0D, 0.0D);
    private Vec3 surfaceAxisZ = new Vec3(0.0D, 0.0D, 1.0D);

    public boolean sunk() {
        return escape.sunk();
    }

    public boolean clampActive() {
        return restraint.active();
    }

    public int quietCrouchTicks() {
        return escape.quietTicks();
    }

    public int clampTicks() {
        return restraint.remainingTicks();
    }

    public int resonanceCooldown() {
        return resonance.cooldown();
    }

    public double hiddenValue() {
        return threat.value();
    }

    public Vec3 surfacePoint() {
        return surfacePoint;
    }

    public Vec3 surfaceNormal() {
        return surfaceNormal;
    }

    public Vec3 surfaceAxisX() {
        return surfaceAxisX;
    }

    public Vec3 surfaceAxisZ() {
        return surfaceAxisZ;
    }

    public QuietCrouchEscapeTemplate.State escape() {
        return escape;
    }

    public HiddenThreatTemplate.State threat() {
        return threat;
    }

    public TimedRestraintTemplate.State restraint() {
        return restraint;
    }

    public ResonancePulseTemplate.State resonance() {
        return resonance;
    }

    public void setSurfaceFrame(Vec3 point, Vec3 normal, Vec3 axisX, Vec3 axisZ) {
        if (point != null) {
            surfacePoint = point;
        }
        if (normal != null && normal.lengthSqr() > 1.0E-8D) {
            surfaceNormal = normal.normalize();
        }
        if (axisX != null && axisX.lengthSqr() > 1.0E-8D) {
            surfaceAxisX = axisX.normalize();
        }
        if (axisZ != null && axisZ.lengthSqr() > 1.0E-8D) {
            surfaceAxisZ = axisZ.normalize();
        }
    }

    public void forceClamp(boolean active, int remainingTicks) {
        TimedRestraintTemplate.force(restraint, active, remainingTicks);
    }

    public void reset() {
        escape.reset();
        threat.clear();
        restraint.reset();
        resonance.reset();
        surfacePoint = Vec3.ZERO;
        surfaceNormal = new Vec3(0.0D, 1.0D, 0.0D);
        surfaceAxisX = new Vec3(1.0D, 0.0D, 0.0D);
        surfaceAxisZ = new Vec3(0.0D, 0.0D, 1.0D);
    }
}
