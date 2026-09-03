package com.fish.mirebound.mud.behavior;

import net.minecraft.util.Mth;

/** Converts movement, view and jump intent into one reusable disturbance sample. */
public final class MudActivityTemplate {
    private MudActivityTemplate() {
    }

    public static Sample sample(Settings settings, double movementStrength,
            double lookDelta, boolean jumpIntent, boolean crouching) {
        double movement = Mth.clamp(movementStrength, 0.0D, 1.0D);
        double look = Mth.clamp(
                lookDelta / Math.max(0.001D, settings.lookActivityThreshold()), 0.0D, 1.0D);
        double strength = Math.max(movement, Math.max(look, jumpIntent ? 1.0D : 0.0D));
        boolean active = strength >= settings.actionThreshold();
        return new Sample(active, active ? strength : 0.0D,
                crouching && !active, crouching || active);
    }

    public interface Settings {
        double actionThreshold();

        double lookActivityThreshold();
    }

    public record Sample(boolean active, double strength,
            boolean quietCrouch, boolean resisting) {
    }
}
