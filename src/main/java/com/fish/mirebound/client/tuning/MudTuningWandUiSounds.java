package com.fish.mirebound.client.tuning;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Local feedback for wand controls; these sounds never enter server simulation. */
public final class MudTuningWandUiSounds {
    private MudTuningWandUiSounds() {
    }

    static void playModeSwitch(Minecraft minecraft, MudTuningWandMode mode) {
        float pitch = 0.96F + mode.ordinal() * 0.10F;
        play(minecraft, SoundEvents.COPPER_BULB_TURN_ON, 0.24F, pitch);
    }

    static void playSummonSelection(Minecraft minecraft) {
        play(minecraft, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.30F, 1.42F);
    }

    static void playConversionUnlock(Minecraft minecraft) {
        play(minecraft, SoundEvents.IRON_DOOR_OPEN, 0.32F, 1.22F);
    }

    static void playConversionUnlockStep(
            Minecraft minecraft, boolean unrestricted, float progress) {
        float amount = Mth.clamp(progress, 0.0F, 1.0F);
        if (unrestricted) {
            play(minecraft, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    0.58F, 0.62F + amount * 0.76F);
        } else {
            play(minecraft, SoundEvents.UI_STONECUTTER_SELECT_RECIPE,
                    0.20F, 0.76F + amount * 0.58F);
        }
    }

    static void playUnrestrictedConversionUnlock(Minecraft minecraft) {
        play(minecraft, SoundEvents.END_PORTAL_SPAWN, 0.95F, 1.0F);
    }

    static void playUnrestrictedConversionToggle(
            Minecraft minecraft, boolean enabled) {
        play(minecraft, enabled
                        ? SoundEvents.RESPAWN_ANCHOR_CHARGE
                        : SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                enabled ? 0.66F : 0.48F, enabled ? 0.82F : 0.72F);
    }

    static void playGenerationType(Minecraft minecraft, int type) {
        play(minecraft, SoundEvents.AMETHYST_BLOCK_RESONATE,
                0.28F, 0.96F + Mth.clamp(type, 0, 4) * 0.14F);
    }

    static void playGenerationCenterLock(
            Minecraft minecraft, boolean locked) {
        play(minecraft, locked
                        ? SoundEvents.COPPER_BULB_TURN_ON
                        : SoundEvents.COPPER_BULB_TURN_OFF,
                0.26F, locked ? 1.38F : 0.86F);
    }

    static void playGenerationCenterMove(Minecraft minecraft) {
        play(minecraft, SoundEvents.UI_STONECUTTER_SELECT_RECIPE,
                0.18F, 1.08F);
    }

    static void playGenerationUndo(Minecraft minecraft) {
        play(minecraft, SoundEvents.COPPER_BULB_TURN_OFF,
                0.25F, 0.82F);
    }

    static void playGenerationAxis(
            Minecraft minecraft, Direction.Axis axis) {
        play(minecraft, SoundEvents.UI_STONECUTTER_SELECT_RECIPE,
                0.20F, 0.90F + axis.ordinal() * 0.18F);
    }

    static void playGenerationRotation(Minecraft minecraft) {
        play(minecraft, SoundEvents.AMETHYST_BLOCK_RESONATE,
                0.28F, 1.24F);
    }

    static void playGenerationSeed(Minecraft minecraft) {
        play(minecraft, SoundEvents.AMETHYST_BLOCK_RESONATE,
                0.26F, 1.56F);
    }

    static void playVolumeStep(Minecraft minecraft, int volume) {
        play(minecraft, SoundEvents.UI_STONECUTTER_SELECT_RECIPE,
                0.20F, volumePitch(volume));
    }

    public static void playPresetConfirm(Minecraft minecraft) {
        play(minecraft, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.24F, 1.34F);
    }

    public static void playPresetCancel(Minecraft minecraft) {
        play(minecraft, SoundEvents.COPPER_BULB_TURN_OFF, 0.22F, 0.84F);
    }

    static float volumePitch(int volume) {
        float amount = (Mth.clamp(volume, 1, 50) - 1.0F) / 49.0F;
        return 0.68F * (float) Math.pow(2.0D, amount * 1.30D);
    }

    private static void play(Minecraft minecraft,
            net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        minecraft.level.playLocalSound(
                minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ(),
                sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
