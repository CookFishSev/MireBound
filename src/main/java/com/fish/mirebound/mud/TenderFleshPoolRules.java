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
        if (level == null || surfacePos == null || profile == null) {
            return null;
        }
        if (!supportsFootprint(profile, availableDepth, localPlayerX, localPlayerZ, bodyHalfWidth,
                (x, z, layers) -> isCompleteColumn(level, surfacePos, medium, x, z, layers))) {
            return null;
        }
        BlockState surfaceState = level.getBlockState(surfacePos);
        double surfaceHeight = MudMediumRuntime.surfaceHeightAt(
                level, surfacePos, surfaceState, medium,
                surfacePos.getX() + localPlayerX, surfacePos.getZ() + localPlayerZ);
        return Double.isFinite(surfaceHeight)
                ? new Anchor(surfacePos.getX() + localPlayerX,
                        surfacePos.getY() + surfaceHeight, surfacePos.getZ() + localPlayerZ)
                : null;
    }

    static boolean supportsFootprint(TenderFleshProfile profile, double availableDepth,
            double localX, double localZ, double bodyHalfWidth, ColumnSupport support) {
        if (!Double.isFinite(availableDepth) || !Double.isFinite(localX)
                || !Double.isFinite(localZ) || !Double.isFinite(bodyHalfWidth)
                || availableDepth + 1.0E-4D < profile.enclosureMinLayers()) {
            return false;
        }
        int layers = Mth.clamp(profile.enclosureMinLayers(), 1, 4);
        int width = Mth.clamp(profile.enclosureMinPoolWidth(), 1, 4);
        double clearance = requiredRootClearance(profile, bodyHalfWidth);
        // Include actual roots across block seams instead of fitting the player
        // into one grid-aligned square, which rejects even the middle of a large pool.
        int minX = Math.min(Mth.floor(localX - clearance + 1.0E-6D),
                Mth.floor(localX - (width - 1) * 0.5D));
        int minZ = Math.min(Mth.floor(localZ - clearance + 1.0E-6D),
                Mth.floor(localZ - (width - 1) * 0.5D));
        int maxX = Math.max(Mth.floor(localX + clearance - 1.0E-6D), minX + width - 1);
        int maxZ = Math.max(Mth.floor(localZ + clearance - 1.0E-6D), minZ + width - 1);
        if (maxX - minX > 4 || maxZ - minZ > 4) {
            return false;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!support.contains(x, z, layers)) {
                    return false;
                }
            }
        }
        return true;
    }

    @FunctionalInterface
    interface ColumnSupport {
        boolean contains(int x, int z, int layers);
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

    private static boolean isCompleteColumn(Level level, BlockPos surfacePos,
            SinkingMedium medium, int x, int z, int layers) {
        for (int layer = 0; layer < layers; layer++) {
            BlockPos pos = surfacePos.offset(x, -layer, z);
            BlockState state = level.getBlockState(pos);
            if (ModBlocks.mediumOf(state.getBlock()) != medium
                    || !MudBlock.supportsVerticalSinking(state, medium)
                    || !MudMediumRuntime.enabled(level, pos, medium)
                    || !MudBehaviorContext.tenderFlesh(level, pos, medium)) {
                return false;
            }
        }
        return true;
    }
}
