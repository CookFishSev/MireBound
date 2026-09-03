package com.fish.mirebound.physics;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudPlayerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Owns mud-specific movement attributes and gravity-control leases. */
public final class MudMovementControl {
    private static final ResourceLocation MOVEMENT_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "mud_movement_speed");
    private static final ResourceLocation ASSIMILATION_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "assimilation_movement_speed");
    private static final ResourceLocation STEP_HEIGHT_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "mud_step_height");

    private MudMovementControl() {
    }

    public static double movementFovCorrection(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return 1.0D;
        }
        AttributeModifier mudModifier = movementSpeed.getModifier(MOVEMENT_SPEED_MODIFIER);
        AttributeModifier assimilationModifier = movementSpeed.getModifier(ASSIMILATION_SPEED_MODIFIER);
        double walkingSpeed = player.getAbilities().getWalkingSpeed();
        if ((mudModifier == null && assimilationModifier == null) || walkingSpeed <= 0.0D) {
            return 1.0D;
        }

        return fovCorrection(
                movementSpeed.getValue(),
                movementSpeedWithoutMudModifier(movementSpeed),
                walkingSpeed);
    }

    static double fovCorrection(
            double modifiedSpeed, double speedWithoutMud, double walkingSpeed) {
        if (walkingSpeed <= 0.0D) {
            return 1.0D;
        }
        double modifiedComponent =
                (modifiedSpeed / walkingSpeed + 1.0D) * 0.5D;
        double withoutMudComponent =
                (speedWithoutMud / walkingSpeed + 1.0D) * 0.5D;
        if (!Double.isFinite(modifiedComponent) || modifiedComponent <= 0.0D
                || !Double.isFinite(withoutMudComponent)) {
            return 1.0D;
        }
        return Mth.clamp(withoutMudComponent / modifiedComponent, 0.25D, 4.0D);
    }

    public static void enableMudGravityControl(Player player, MudPlayerData data) {
        if (!data.gravityOverrideActive) {
            data.previousNoGravity = PlayerGravityControl.acquire(
                    player, PlayerGravityControl.Owner.MUD);
            data.gravityOverrideActive = true;
        } else {
            PlayerGravityControl.acquire(player, PlayerGravityControl.Owner.MUD);
        }
    }

    public static void restoreMudGravity(Player player, MudPlayerData data) {
        if (!data.gravityOverrideActive) {
            return;
        }
        PlayerGravityControl.release(player, PlayerGravityControl.Owner.MUD);
        data.gravityOverrideActive = false;
        data.previousNoGravity = false;
    }

    public static void updateMudMovementSpeed(Player player, double walkScale) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }
        double amount = movementModifierAmount(walkScale);
        AttributeModifier current = movementSpeed.getModifier(MOVEMENT_SPEED_MODIFIER);
        if (current != null && Math.abs(current.amount() - amount) < 0.005D) {
            return;
        }
        movementSpeed.addOrUpdateTransientModifier(new AttributeModifier(
                MOVEMENT_SPEED_MODIFIER,
                amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    public static void updateMudMovement(
            Player player, double walkScale, double stepHeight) {
        updateMudMovementSpeed(player, walkScale);
        AttributeInstance attribute = player.getAttribute(Attributes.STEP_HEIGHT);
        if (attribute == null) {
            return;
        }
        double amount = stepHeightModifierAmount(
                attributeValueWithoutModifiers(attribute, STEP_HEIGHT_MODIFIER, null),
                stepHeight);
        AttributeModifier current = attribute.getModifier(STEP_HEIGHT_MODIFIER);
        if (current != null && Math.abs(current.amount() - amount) < 0.005D) {
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(
                STEP_HEIGHT_MODIFIER,
                amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    public static void clearMudMovement(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(MOVEMENT_SPEED_MODIFIER);
        }
        AttributeInstance stepHeight = player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight != null) {
            stepHeight.removeModifier(STEP_HEIGHT_MODIFIER);
        }
    }

    public static void updateAssimilationMovementSpeed(Player player, double scale) {
        updateScaleModifier(player, ASSIMILATION_SPEED_MODIFIER, scale);
    }

    public static void clearAssimilationMovementSpeed(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(ASSIMILATION_SPEED_MODIFIER);
        }
    }

    private static void updateScaleModifier(Player player, ResourceLocation id, double scale) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }
        double amount = movementModifierAmount(scale);
        AttributeModifier current = movementSpeed.getModifier(id);
        if (current != null && Math.abs(current.amount() - amount) < 0.005D) {
            return;
        }
        movementSpeed.addOrUpdateTransientModifier(new AttributeModifier(
                id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    static double movementModifierAmount(double walkScale) {
        return Mth.clamp(walkScale, 0.0D, 1.20D) - 1.0D;
    }

    static double stepHeightModifierAmount(
            double heightWithoutMud, double targetHeight) {
        double target = Mth.clamp(targetHeight, 0.0D, 2.0D);
        if (!Double.isFinite(heightWithoutMud) || heightWithoutMud <= 1.0E-9D) {
            return 0.0D;
        }
        return target / heightWithoutMud - 1.0D;
    }

    private static double movementSpeedWithoutMudModifier(
            AttributeInstance movementSpeed) {
        return attributeValueWithoutModifiers(
                movementSpeed,
                MOVEMENT_SPEED_MODIFIER,
                ASSIMILATION_SPEED_MODIFIER);
    }

    private static double attributeValueWithoutModifiers(
            AttributeInstance attribute,
            ResourceLocation firstExcluded,
            ResourceLocation secondExcluded) {
        double base = attribute.getBaseValue();
        double added = base;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (!isExcluded(modifier, firstExcluded, secondExcluded)
                    && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                added += modifier.amount();
            }
        }
        double multipliedBase = added;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (!isExcluded(modifier, firstExcluded, secondExcluded)
                    && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                multipliedBase += added * modifier.amount();
            }
        }
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (!isExcluded(modifier, firstExcluded, secondExcluded)
                    && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                multipliedBase *= 1.0D + modifier.amount();
            }
        }
        return Math.max(0.0D, multipliedBase);
    }

    private static boolean isExcluded(
            AttributeModifier modifier,
            ResourceLocation first,
            ResourceLocation second) {
        return modifier.id().equals(first)
                || second != null && modifier.id().equals(second);
    }
}
