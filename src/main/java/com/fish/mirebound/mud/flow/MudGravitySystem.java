package com.fish.mirebound.mud.flow;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockProfileStore;
import com.fish.mirebound.mud.MudLocalProfileSync;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Optional vanilla-style gravity for native sinking-medium blocks. */
public final class MudGravitySystem {
    private static final int FALL_DELAY_TICKS = 2;
    private static final String PROFILE_TAG = "mirebound_falling_profile";
    private static final String MEDIUM_TAG = "medium";
    private static final String VALUES_TAG = "values";

    private MudGravitySystem() {
    }

    public static void wake(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (eligible(level, pos, state)
                && FallingBlock.isFree(level.getBlockState(pos.below()))) {
            level.scheduleTick(pos, state.getBlock(), FALL_DELAY_TICKS);
        }
    }

    public static void wakeAll(ServerLevel level, Iterable<BlockPos> positions) {
        for (BlockPos pos : positions) {
            wake(level, pos);
        }
    }

    public static void tick(
            BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (eligible(level, pos, state)
                && pos.getY() >= level.getMinBuildHeight()
                && FallingBlock.isFree(level.getBlockState(pos.below()))) {
            fall(level, pos, state);
        }
    }

    public static void onLand(ServerLevel level, BlockPos pos,
            BlockState state, FallingBlockEntity entity) {
        if (!(state.getBlock() instanceof MudBlock mudBlock)) {
            return;
        }
        MudBlockProfileStore store = MudBlockProfileStore.get(level);
        store.trackShapeState(level, pos, state);
        CompoundTag entityData = entity.blockData;
        if (entityData == null || !entityData.contains(PROFILE_TAG)) {
            return;
        }
        CompoundTag profileTag = entityData.getCompound(PROFILE_TAG);
        if (profileTag.getInt(MEDIUM_TAG) != mudBlock.medium().id()) {
            return;
        }
        double[] values = unpack(profileTag.getLongArray(VALUES_TAG));
        if (values.length == 0) {
            return;
        }
        store.put(level, pos, mudBlock.medium(), values);
        MudLocalProfileSync.broadcastChunk(level,
                new net.minecraft.world.level.ChunkPos(pos));
    }

    private static void fall(ServerLevel level, BlockPos pos, BlockState state) {
        MudBlock mudBlock = (MudBlock) state.getBlock();
        SinkingMedium medium = mudBlock.medium();
        MudBlockProfileStore store = MudBlockProfileStore.get(level);
        MudBlockProfileStore.Profile profile = store.profile(level, pos, medium);
        double[] values = profile == null ? null : profile.values();
        store.removeAll(pos);
        FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, state);
        if (values != null) {
            CompoundTag profileTag = new CompoundTag();
            profileTag.putInt(MEDIUM_TAG, medium.id());
            profileTag.putLongArray(VALUES_TAG, pack(values));
            if (entity.blockData == null) {
                entity.blockData = new CompoundTag();
            }
            entity.blockData.put(PROFILE_TAG, profileTag);
        }
    }

    static long[] pack(double[] values) {
        int count = Math.min(values == null ? 0 : values.length,
                MudPhysicsParameter.COUNT);
        long[] packed = new long[count];
        for (int index = 0; index < count; index++) {
            packed[index] = Double.doubleToRawLongBits(values[index]);
        }
        return packed;
    }

    static double[] unpack(long[] packed) {
        int count = Math.min(packed == null ? 0 : packed.length,
                MudPhysicsParameter.COUNT);
        double[] values = new double[count];
        for (int index = 0; index < count; index++) {
            values[index] = Double.longBitsToDouble(packed[index]);
        }
        return values;
    }

    private static boolean eligible(
            ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getServer().getLevel(level.dimension()) != level
                || SableCompat.subLevelAtStorage(level, pos) != null
                || !(state.getBlock() instanceof MudBlock mudBlock)
                || mudBlock instanceof AdaptiveMudBlock) {
            return false;
        }
        return MudMediumRuntime.value(level, pos, mudBlock.medium(),
                        MudPhysicsParameter.GRAVITY_FALLING_ENABLED) >= 0.5D
                && !MudMediumRuntime.flowProfile(level, pos, mudBlock.medium()).enabled();
    }
}
