package com.fish.mirebound.adaptive;

import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Compact identity of the source face represented by an adaptive mud proxy. */
public final class MudVisualSource {
    public static final long NONE = 0L;
    public static final int DEFAULT_SMOOTHING_RADIUS = 0;
    public static final float DEFAULT_TEXTURE_DETAIL = 0.90F;
    private static final int FACE_SHIFT = 32;
    private static final int COLOR_SHIFT = 35;
    private static final int MODERN_GREEN_SHIFT = 42;
    private static final int MODERN_BLUE_SHIFT = 49;
    private static final int SMOOTHING_SHIFT = 56;
    private static final int DETAIL_SHIFT = 58;
    private static final long STATE_MASK = 0xFFFFFFFFL;
    private static final long FACE_MASK = 0x7L;
    private static final long COLOR_MASK = 0xFFFFFFL;
    private static final long SEVEN_BIT_MASK = 0x7FL;
    private static final long DETAIL_MASK = 0x1FL;
    private static final long MODERN_FORMAT_MARKER = Long.MIN_VALUE;
    private static final long POSITION_FORMAT_MASK = 3L << 62;
    private static final long POSITION_FORMAT_MARKER = 1L << 62;
    private static final int POSITION_X_BITS = 18;
    private static final int POSITION_Z_BITS = 18;
    private static final int POSITION_Y_BITS = 11;
    private static final int POSITION_X_SHIFT = 0;
    private static final int POSITION_Z_SHIFT = POSITION_X_SHIFT + POSITION_X_BITS;
    private static final int POSITION_Y_SHIFT = POSITION_Z_SHIFT + POSITION_Z_BITS;
    private static final int POSITION_FACE_SHIFT = POSITION_Y_SHIFT + POSITION_Y_BITS;
    private static final int POSITION_COLOR_SHIFT = POSITION_FACE_SHIFT + 3;
    private static final int POSITION_Y_MIN = -64;
    private static final int POSITION_X_MIN = -(1 << (POSITION_X_BITS - 1));
    private static final int POSITION_X_MAX = (1 << (POSITION_X_BITS - 1)) - 1;
    private static final int POSITION_Z_MIN = -(1 << (POSITION_Z_BITS - 1));
    private static final int POSITION_Z_MAX = (1 << (POSITION_Z_BITS - 1)) - 1;
    private static final int POSITION_Y_MAX = POSITION_Y_MIN + (1 << POSITION_Y_BITS) - 1;
    private static final long POSITION_X_MASK = (1L << POSITION_X_BITS) - 1L;
    private static final long POSITION_Z_MASK = (1L << POSITION_Z_BITS) - 1L;
    private static final long POSITION_Y_MASK = (1L << POSITION_Y_BITS) - 1L;
    private static final long POSITION_COLOR_MASK = 0xFFFL;

    private MudVisualSource() {
    }

    public static long capture(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return NONE;
        }
        BlockState proxyState = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(proxyState.getBlock());
        Direction face = medium == null
                ? Direction.UP
                : MudBlock.surfaceDirection(proxyState, medium);
        return capture(level, pos, face);
    }

    public static long capture(Level level, BlockPos pos, Direction face) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return NONE;
        }
        BlockState proxyState = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(proxyState.getBlock());
        if (!(proxyState.getBlock() instanceof AdaptiveMudBlock)) {
            return NONE;
        }
        BlockState source = AdaptiveMudSourceStore.get(serverLevel).sourceState(pos);
        if (source == null || source.isAir()
                || source.getBlock() instanceof AdaptiveMudBlock) {
            return NONE;
        }
        BlockGetter sourceLevel = AdaptiveMudSourceView.wrap(level, pos, source);
        int color = source.getMapColor(sourceLevel, pos).col;
        int smoothingRadius = medium == null
                ? DEFAULT_SMOOTHING_RADIUS
                : MudMediumRuntime.adaptiveCoverageSmoothingRadius(
                        level, pos, medium);
        float textureDetail = medium == null
                ? DEFAULT_TEXTURE_DETAIL
                : MudMediumRuntime.adaptiveCoverageTextureDetail(
                        level, pos, medium);
        long positioned = position(pos, face, color);
        if (positioned != NONE) {
            return positioned;
        }
        return pack(source, face == null ? Direction.UP : face, color,
                smoothingRadius, textureDetail);
    }

    /**
     * Preserves the proxy position so the client can rebuild dynamic model data,
     * block-entity colors, and resource-pack tints instead of using a lossy state.
     */
    public static long position(BlockPos pos, Direction face, int color) {
        if (pos == null || pos.getX() < POSITION_X_MIN || pos.getX() > POSITION_X_MAX
                || pos.getZ() < POSITION_Z_MIN || pos.getZ() > POSITION_Z_MAX
                || pos.getY() < POSITION_Y_MIN || pos.getY() > POSITION_Y_MAX) {
            return NONE;
        }
        int packedColor = quantizeFour(color >> 16 & 0xFF) << 8
                | quantizeFour(color >> 8 & 0xFF) << 4
                | quantizeFour(color & 0xFF);
        return POSITION_FORMAT_MARKER
                | (pos.getX() & POSITION_X_MASK) << POSITION_X_SHIFT
                | (pos.getZ() & POSITION_Z_MASK) << POSITION_Z_SHIFT
                | (long) (pos.getY() - POSITION_Y_MIN) << POSITION_Y_SHIFT
                | ((long) (face == null ? Direction.UP : face).ordinal() & FACE_MASK)
                        << POSITION_FACE_SHIFT
                | (long) packedColor << POSITION_COLOR_SHIFT;
    }

    public static boolean positionBacked(long source) {
        return (source & POSITION_FORMAT_MASK) == POSITION_FORMAT_MARKER;
    }

    @Nullable
    public static BlockPos position(long source) {
        if (!positionBacked(source)) {
            return null;
        }
        int x = signExtend((int) (source >>> POSITION_X_SHIFT & POSITION_X_MASK),
                POSITION_X_BITS);
        int z = signExtend((int) (source >>> POSITION_Z_SHIFT & POSITION_Z_MASK),
                POSITION_Z_BITS);
        int y = (int) (source >>> POSITION_Y_SHIFT & POSITION_Y_MASK)
                + POSITION_Y_MIN;
        return new BlockPos(x, y, z);
    }

    public static long pack(BlockState source, Direction face, int color) {
        return pack(source, face, color,
                DEFAULT_SMOOTHING_RADIUS, DEFAULT_TEXTURE_DETAIL);
    }

    public static long pack(BlockState source, Direction face, int color,
            int smoothingRadius, float textureDetail) {
        if (source == null || source.isAir()) {
            return NONE;
        }
        long state = Integer.toUnsignedLong(Block.getId(source) + 1);
        int red = quantizeSeven(color >> 16 & 0xFF);
        int green = quantizeSeven(color >> 8 & 0xFF);
        int blue = quantizeSeven(color & 0xFF);
        int smoothing = Mth.clamp(smoothingRadius, 0, 3);
        int detail = Mth.clamp(Math.round(
                Mth.clamp(textureDetail, 0.0F, 1.0F) * DETAIL_MASK),
                0, (int) DETAIL_MASK);
        return state
                | ((long) face.ordinal() & FACE_MASK) << FACE_SHIFT
                | (long) red << COLOR_SHIFT
                | (long) green << MODERN_GREEN_SHIFT
                | (long) blue << MODERN_BLUE_SHIFT
                | (long) smoothing << SMOOTHING_SHIFT
                | (long) detail << DETAIL_SHIFT
                | MODERN_FORMAT_MARKER;
    }

    public static BlockState state(long source) {
        if (source == NONE || positionBacked(source)) {
            return null;
        }
        int encoded = (int) (source & STATE_MASK);
        return encoded == 0 ? null : Block.stateById(encoded - 1);
    }

    public static Direction face(long source) {
        if (source == NONE) {
            return Direction.UP;
        }
        int shift = positionBacked(source) ? POSITION_FACE_SHIFT : FACE_SHIFT;
        int ordinal = (int) (source >>> shift & FACE_MASK);
        Direction[] directions = Direction.values();
        return ordinal < directions.length ? directions[ordinal] : Direction.UP;
    }

    public static int color(long source) {
        if (source == NONE) {
            return 0xFFFFFF;
        }
        if (positionBacked(source)) {
            int packed = (int) (source >>> POSITION_COLOR_SHIFT & POSITION_COLOR_MASK);
            return expandFour(packed >> 8 & 0xF) << 16
                    | expandFour(packed >> 4 & 0xF) << 8
                    | expandFour(packed & 0xF);
        }
        if (!modern(source)) {
            return (int) (source >>> COLOR_SHIFT & COLOR_MASK);
        }
        return expandSeven((int) (source >>> COLOR_SHIFT & SEVEN_BIT_MASK)) << 16
                | expandSeven((int) (source >>> MODERN_GREEN_SHIFT & SEVEN_BIT_MASK)) << 8
                | expandSeven((int) (source >>> MODERN_BLUE_SHIFT & SEVEN_BIT_MASK));
    }

    public static int smoothingRadius(long source) {
        return source != NONE && modern(source)
                ? (int) (source >>> SMOOTHING_SHIFT & 0x3L)
                : DEFAULT_SMOOTHING_RADIUS;
    }

    public static float textureDetail(long source) {
        return source != NONE && modern(source)
                ? (source >>> DETAIL_SHIFT & DETAIL_MASK) / (float) DETAIL_MASK
                : DEFAULT_TEXTURE_DETAIL;
    }

    public static Vector3f particleColor(long source, Vector3f fallback) {
        if (source == NONE) {
            return fallback;
        }
        int color = color(source);
        return new Vector3f(
                (color >> 16 & 0xFF) / 255.0F,
                (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F);
    }

    private static boolean modern(long source) {
        return (source & MODERN_FORMAT_MARKER) != 0L;
    }

    private static int signExtend(int value, int bits) {
        int shift = Integer.SIZE - bits;
        return value << shift >> shift;
    }

    private static int quantizeFour(int component) {
        return Mth.clamp(Math.round(component * 15.0F / 255.0F), 0, 15);
    }

    private static int expandFour(int component) {
        return Mth.clamp(component, 0, 15) * 17;
    }

    private static int quantizeSeven(int component) {
        return Mth.clamp(Math.round(component * 127.0F / 255.0F), 0, 127);
    }

    private static int expandSeven(int component) {
        return Mth.clamp(Math.round(component * 255.0F / 127.0F), 0, 255);
    }
}
