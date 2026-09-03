package com.fish.mirebound.mud;

import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Shared qualification for tender-flesh enclosures on both logical sides. */
public final class TenderFleshPoolRules {
    private static final double PIXEL = 1.0D / 16.0D;

    private TenderFleshPoolRules() {
    }

    /** A stable enclosure anchor that has enough mud around it for every root. */
    public record Anchor(double x, double y, double z) {
    }

    public static boolean qualifies(Level level, BlockPos surfacePos,
            TenderFleshProfile profile, double availableDepth) {
        return findAnchor(level, surfacePos, profile, availableDepth,
                0.5D, 0.5D, 0.30D) != null;
    }

    public static boolean qualifies(Level level, BlockPos surfacePos,
            TenderFleshProfile profile, double availableDepth,
            double localPlayerX, double localPlayerZ) {
        return findAnchor(level, surfacePos, profile, availableDepth,
                localPlayerX, localPlayerZ, 0.30D) != null;
    }

    public static Anchor findAnchor(Level level, BlockPos surfacePos,
            TenderFleshProfile profile, double availableDepth,
            double localPlayerX, double localPlayerZ, double bodyHalfWidth) {
        return findAnchor(level, surfacePos, SinkingMedium.TENDER_FLESH,
                profile, availableDepth, localPlayerX, localPlayerZ, bodyHalfWidth);
    }

    public static Anchor findAnchor(Level level, BlockPos surfacePos,
            SinkingMedium medium, TenderFleshProfile profile, double availableDepth,
            double localPlayerX, double localPlayerZ, double bodyHalfWidth) {
        if (level == null || surfacePos == null || profile == null
                || availableDepth + 1.0E-4D < profile.enclosureMinLayers()) {
            return null;
        }

        int layers = Mth.clamp(profile.enclosureMinLayers(), 1, 4);
        int width = Mth.clamp(profile.enclosureMinPoolWidth(), 1, 4);
        double edgeMargin = Math.min(width * 0.5D - 0.01D,
                Math.max(0.22D, Math.max(0.20D, bodyHalfWidth) + 0.12D));
        double rootMargin = Math.min(width * 0.5D - 0.01D,
                requiredRootClearance(profile, bodyHalfWidth));
        for (int startX = 1 - width; startX <= 0; startX++) {
            for (int startZ = 1 - width; startZ <= 0; startZ++) {
                double playerX = localPlayerX - startX;
                double playerZ = localPlayerZ - startZ;
                double requiredMargin = Math.max(edgeMargin, rootMargin);
                if (playerX < requiredMargin || playerX > width - requiredMargin
                        || playerZ < requiredMargin || playerZ > width - requiredMargin) {
                    continue;
                }
                if (isCompletePool(
                        level, surfacePos, medium, startX, startZ, width, layers)) {
                    BlockState surfaceState = level.getBlockState(surfacePos);
                    double surfaceHeight = MudMediumRuntime.surfaceHeightAt(
                            level, surfacePos, surfaceState, medium,
                            surfacePos.getX() + localPlayerX,
                            surfacePos.getZ() + localPlayerZ);
                    if (!Double.isFinite(surfaceHeight)) {
                        continue;
                    }
                    double rootY = surfacePos.getY() + surfaceHeight;
                    return new Anchor(
                            surfacePos.getX() + localPlayerX,
                            rootY,
                            surfacePos.getZ() + localPlayerZ);
                }
            }
        }
        return null;
    }

    public static double pillarOuterRadius(TenderFleshProfile profile,
            double bodyHalfWidth) {
        double bodyCornerRadius = Math.sqrt(2.0D)
                * (Math.max(0.20D, bodyHalfWidth) + PIXEL * 2.0D);
        double rootReach = profile.tentacleLengthPixels() * PIXEL * 0.32D;
        double requested = Math.max(
                bodyCornerRadius + rootReach,
                bodyCornerRadius + PIXEL * 1.20D);
        double halfTube = Math.max(PIXEL * 2.0D,
                profile.foldWidthPixels() * PIXEL) * 0.5D;
        double poolLimit = profile.enclosureMinPoolWidth() * 0.5D
                - halfTube - PIXEL * 0.50D;
        return Math.max(bodyCornerRadius + PIXEL * 0.75D,
                Math.min(requested, poolLimit));
    }

    private static double requiredRootClearance(TenderFleshProfile profile,
            double bodyHalfWidth) {
        double halfTube = Math.max(PIXEL * 2.0D,
                profile.foldWidthPixels() * PIXEL) * 0.5D;
        return pillarOuterRadius(profile, bodyHalfWidth) + halfTube + PIXEL * 0.50D;
    }

    private static boolean isCompletePool(Level level, BlockPos surfacePos,
            SinkingMedium medium, int startX, int startZ, int width, int layers) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                for (int layer = 0; layer < layers; layer++) {
                    BlockPos pos = surfacePos.offset(startX + x, -layer, startZ + z);
                    BlockState state = level.getBlockState(pos);
                    if (ModBlocks.mediumOf(state.getBlock()) != medium
                            || !MudBlock.supportsVerticalSinking(
                                    state, medium)
                            || !MudMediumRuntime.enabled(
                                    level, pos, medium)
                            || !MudBehaviorContext.tenderFlesh(level, pos, medium)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
