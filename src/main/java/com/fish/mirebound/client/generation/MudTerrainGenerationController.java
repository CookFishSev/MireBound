package com.fish.mirebound.client.generation;

import com.fish.mirebound.client.tuning.MudTuningClientSettings;
import com.fish.mirebound.client.tuning.MudTuningClientState;
import com.fish.mirebound.client.tuning.MudTuningInputController;
import com.fish.mirebound.client.tuning.MudTuningSpatialPlacement;
import com.fish.mirebound.client.tuning.MudTuningWandMode;
import com.fish.mirebound.generation.MudTerrainGenerationRequest;
import com.fish.mirebound.generation.MudTerrainGenerationSettings;
import com.fish.mirebound.generation.MudTerrainGenerationType;
import com.fish.mirebound.generation.MudTerrainLakeSettings;
import com.fish.mirebound.generation.MudTerrainRotation;
import com.fish.mirebound.network.payload.MudTerrainGenerationPayload;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owns generation-mode targeting, preview refreshes and direct generation intents. */
public final class MudTerrainGenerationController {
    private static final int MAXIMUM_SHORTCUT_SEED = 2_000_000_000;
    private static Status status = Status.INACTIVE;
    private static BlockPos lockedCenter;
    private static Direction.Axis rotationAxis = Direction.Axis.X;
    private static MudTerrainRotation rotation = MudTerrainRotation.IDENTITY;

    private MudTerrainGenerationController() {
    }

    public static void tick(Minecraft minecraft) {
        if (!sessionAvailable(minecraft)) {
            reset();
            return;
        }
        if (!contextActive(minecraft)) {
            status = Status.INACTIVE;
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        BlockPos center = targetCenter(minecraft);
        if (center == null) {
            MudTerrainGenerationPreview.reset();
            return;
        }
        MudTerrainGenerationRequest request =
                MudTuningClientSettings.generationRequest(
                        center, rotation, true);
        MudTerrainGenerationPreview.Preview preview =
                MudTerrainGenerationPreview.refresh(minecraft, request);
        status = preview == null ? Status.EMPTY : Status.READY;
    }

    public static void generate(Minecraft minecraft) {
        MudTerrainGenerationPreview.Preview preview = minecraft.level == null
                ? null : MudTerrainGenerationPreview.active(minecraft.level);
        if (!MudTuningClientSettings.conversionUnlocked()) {
            message(minecraft, "message.mirebound.generation.locked");
            return;
        }
        if (preview == null || status != Status.READY) {
            message(minecraft, "message.mirebound.generation.invalid_target");
            return;
        }
        PacketDistributor.sendToServer(new MudTerrainGenerationPayload(
                MudTerrainGenerationPayload.Action.GENERATE,
                preview.request()));
    }

    public static void cycleType(int direction) {
        MudTerrainGenerationType current = MudTuningClientSettings.generationType();
        MudTuningClientSettings.setGenerationType(current.cycle(direction));
    }

    public static boolean toggleCenterLock(Minecraft minecraft) {
        if (lockedCenter != null) {
            lockedCenter = nextLockedCenter(lockedCenter, null);
            return true;
        }
        BlockPos target = dynamicTargetCenter(minecraft);
        if (target == null) {
            return false;
        }
        lockedCenter = nextLockedCenter(null, target);
        return true;
    }

    public static boolean centerLocked() {
        return lockedCenter != null;
    }

    public static boolean moveLockedCenter(
            Minecraft minecraft, double scrollDelta) {
        if (lockedCenter == null || minecraft.level == null
                || Math.abs(scrollDelta) < 1.0E-6D) {
            return false;
        }
        Vec3 look = MudTuningSpatialPlacement.placementDirection(minecraft);
        if (look == null || look.lengthSqr() < 1.0E-8D) {
            return false;
        }
        Direction direction = Direction.getNearest(look.x, look.y, look.z);
        if (scrollDelta < 0.0D) {
            direction = direction.getOpposite();
        }
        BlockPos moved = lockedCenter.relative(direction);
        if (!minecraft.level.isInWorldBounds(moved)
                || !minecraft.level.getWorldBorder().isWithinBounds(moved)) {
            return false;
        }
        lockedCenter = moved.immutable();
        return true;
    }

    public static void cycleRotationAxis() {
        rotationAxis = nextRotationAxis(rotationAxis);
    }

    public static void rotatePreview() {
        rotation = rotation.rotate(rotationAxis);
    }

    public static Direction.Axis rotationAxis() {
        return rotationAxis;
    }

    public static MudTerrainRotation rotation() {
        return rotation;
    }

    public static void undo() {
        PacketDistributor.sendToServer(MudTerrainGenerationPayload.undo());
    }

    public static void adjustVolume(int direction) {
        GenerationSize adjusted = adjustedSize(
                MudTuningClientSettings.generationType(),
                MudTuningClientSettings.generationRadius(),
                MudTuningClientSettings.generationLakeHorizontalRadius(),
                MudTuningClientSettings.generationLakeVerticalRadius(),
                direction);
        if (MudTuningClientSettings.generationType().usesDepositSettings()) {
            if (adjusted.depositRadius()
                    != MudTuningClientSettings.generationRadius()) {
                MudTuningClientSettings.setGenerationRadius(
                        adjusted.depositRadius());
            }
        } else {
            if (adjusted.lakeHorizontalRadius()
                    != MudTuningClientSettings.generationLakeHorizontalRadius()) {
                MudTuningClientSettings.setGenerationLakeHorizontalRadius(
                        adjusted.lakeHorizontalRadius());
            }
            if (adjusted.lakeVerticalRadius()
                    != MudTuningClientSettings.generationLakeVerticalRadius()) {
                MudTuningClientSettings.setGenerationLakeVerticalRadius(
                        adjusted.lakeVerticalRadius());
            }
        }
    }

    public static int volumeSoundLevel() {
        return MudTuningClientSettings.generationType()
                .usesDepositSettings()
                ? MudTuningClientSettings.generationRadius()
                : MudTuningClientSettings.generationLakeHorizontalRadius() * 2;
    }

    public static void rerollSeed() {
        if (MudTuningClientSettings.generationType().usesDepositSettings()) {
            int current = MudTuningClientSettings.generationSeed();
            MudTuningClientSettings.setGenerationSeed(nextSeed(
                    current, ThreadLocalRandom.current().nextInt(
                            MAXIMUM_SHORTCUT_SEED + 1)));
        } else {
            int current = MudTuningClientSettings.generationLakeSeed();
            MudTuningClientSettings.setGenerationLakeSeed(nextSeed(
                    current, ThreadLocalRandom.current().nextInt(
                            MAXIMUM_SHORTCUT_SEED + 1)));
        }
    }

    public static Vec3 coreTarget(Minecraft minecraft) {
        if (lockedCenter != null || !contextActive(minecraft)
                || minecraft.level == null) {
            return null;
        }
        MudTerrainGenerationPreview.Preview preview =
                MudTerrainGenerationPreview.active(minecraft.level);
        return preview == null ? null : preview.coreTarget();
    }

    public static Status status() {
        return status;
    }

    public static int previewCellCount(Minecraft minecraft) {
        MudTerrainGenerationPreview.Preview preview = minecraft.level == null
                ? null : MudTerrainGenerationPreview.active(minecraft.level);
        return preview == null ? 0 : preview.cellCount();
    }

    public static BlockPos previewCenter(Minecraft minecraft) {
        MudTerrainGenerationPreview.Preview preview = minecraft.level == null
                ? null : MudTerrainGenerationPreview.active(minecraft.level);
        return preview == null ? null : preview.request().center();
    }

    public static void reset() {
        status = Status.INACTIVE;
        lockedCenter = null;
        rotationAxis = Direction.Axis.X;
        rotation = MudTerrainRotation.IDENTITY;
        MudTerrainGenerationPreview.reset();
    }

    private static boolean contextActive(Minecraft minecraft) {
        return sessionAvailable(minecraft)
                && MudTuningInputController.heldWandHand(minecraft.player) != null
                && MudTuningClientState.mode() == MudTuningWandMode.GENERATION;
    }

    private static boolean sessionAvailable(Minecraft minecraft) {
        return minecraft != null
                && minecraft.level != null
                && minecraft.player != null
                && minecraft.getConnection() != null;
    }

    private static BlockPos targetCenter(Minecraft minecraft) {
        if (lockedCenter != null) {
            status = Status.READY;
            return lockedCenter;
        }
        return dynamicTargetCenter(minecraft);
    }

    private static BlockPos dynamicTargetCenter(Minecraft minecraft) {
        Vec3 target = MudTuningSpatialPlacement.cameraTarget(minecraft);
        if (target == null) {
            status = Status.NO_TARGET;
            return null;
        }
        status = Status.READY;
        return BlockPos.containing(target);
    }

    static GenerationSize adjustedSize(
            MudTerrainGenerationType type,
            int depositRadius, int lakeHorizontalRadius,
            int lakeVerticalRadius, int direction) {
        int step = Integer.signum(direction);
        if (step == 0) {
            return new GenerationSize(
                    depositRadius, lakeHorizontalRadius, lakeVerticalRadius);
        }
        if (type.usesDepositSettings()) {
            return new GenerationSize(clamp(
                    depositRadius + step,
                    MudTerrainGenerationSettings.MINIMUM_RADIUS,
                    MudTerrainGenerationSettings.MAXIMUM_RADIUS),
                    lakeHorizontalRadius, lakeVerticalRadius);
        }
        int horizontal = clamp(lakeHorizontalRadius + step,
                MudTerrainLakeSettings.MINIMUM_HORIZONTAL_RADIUS,
                MudTerrainLakeSettings.MAXIMUM_HORIZONTAL_RADIUS);
        int vertical = lakeVerticalRadius;
        if (horizontal != lakeHorizontalRadius) {
            if (step > 0 && horizontal % 2 == 0) {
                vertical++;
            } else if (step < 0 && lakeHorizontalRadius % 2 == 0) {
                vertical--;
            }
        }
        return new GenerationSize(depositRadius, horizontal, clamp(vertical,
                MudTerrainLakeSettings.MINIMUM_VERTICAL_RADIUS,
                MudTerrainLakeSettings.MAXIMUM_VERTICAL_RADIUS));
    }

    static int nextSeed(int current, int candidate) {
        int bounded = clamp(candidate, 0, MAXIMUM_SHORTCUT_SEED);
        return bounded != current ? bounded
                : current == MAXIMUM_SHORTCUT_SEED ? 0 : current + 1;
    }

    static BlockPos nextLockedCenter(BlockPos current, BlockPos target) {
        return current == null && target != null ? target.immutable() : null;
    }

    static Direction.Axis nextRotationAxis(Direction.Axis current) {
        return switch (current) {
            case X -> Direction.Axis.Y;
            case Y -> Direction.Axis.Z;
            case Z -> Direction.Axis.X;
        };
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void message(Minecraft minecraft, String key) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable(key), true);
        }
    }

    public enum Status {
        INACTIVE,
        NO_TARGET,
        EMPTY,
        READY
    }

    record GenerationSize(
            int depositRadius,
            int lakeHorizontalRadius,
            int lakeVerticalRadius) {
    }
}
