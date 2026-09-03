package com.fish.mirebound.adaptive;

import com.fish.mirebound.client.AdaptiveMudClientCache;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPlantSupport;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

/** Position-owned mud proxy whose appearance and safe queries come from a stored source state. */
public final class AdaptiveMudBlock extends MudBlock implements EntityBlock {
    public static final BooleanProperty SOURCE_BLOCK_ENTITY =
            BooleanProperty.create("source_block_entity");

    public AdaptiveMudBlock(BlockBehaviour.Properties properties, SinkingMedium medium) {
        super(properties, medium, 1.0F);
        registerDefaultState(defaultBlockState().setValue(SOURCE_BLOCK_ENTITY, false));
    }

    @Override
    protected boolean managesVolumeLifecycle() {
        return true;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SOURCE_BLOCK_ENTITY)
                ? new AdaptiveMudBlockEntity(pos, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SOURCE_BLOCK_ENTITY);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getShape(state, level, pos, context)
                : deformedSourceShape(
                        state, source, source.getShape(
                                sourceView(level, pos, source), pos, context));
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getVisualShape(state, level, pos, context)
                : deformedSourceShape(
                        state, source, source.getVisualShape(
                                sourceView(level, pos, source), pos, context));
    }

    @Override
    protected VoxelShape getBlockSupportShape(
            BlockState state, BlockGetter level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getBlockSupportShape(state, level, pos)
                : deformedSourceShape(
                        state, source, source.getBlockSupportShape(
                                sourceView(level, pos, source), pos));
    }

    @Override
    protected VoxelShape getInteractionShape(
            BlockState state, BlockGetter level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getInteractionShape(state, level, pos)
                : deformedSourceShape(
                        state, source, source.getInteractionShape(
                                sourceView(level, pos, source), pos));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        BlockState source = sourceState(level, pos);
        if (source == null) {
            return super.getCollisionShape(state, level, pos, context);
        }
        BlockGetter sourceLevel = sourceView(level, pos, source);
        VoxelShape sourceCollision = deformedSourceShape(
                state, source, source.getCollisionShape(sourceLevel, pos, context));
        VoxelShape mudCollision = super.getCollisionShape(state, level, pos, context);
        return AdaptiveMudCollision.synchronize(
                mudCollision, sourceCollision, context instanceof EntityCollisionContext);
    }

    @Override
    protected VoxelShape getOcclusionShape(
            BlockState state, BlockGetter level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getOcclusionShape(state, level, pos)
                : deformedSourceShape(
                        state, source, source.getOcclusionShape(
                                sourceView(level, pos, source), pos));
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getLightBlock(state, level, pos)
                : source.getLightBlock(sourceView(level, pos, source), pos);
    }

    @Override
    protected float getShadeBrightness(
            BlockState state, BlockGetter level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getShadeBrightness(state, level, pos)
                : source.getShadeBrightness(sourceView(level, pos, source), pos);
    }

    @Override
    protected boolean propagatesSkylightDown(
            BlockState state, BlockGetter level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.propagatesSkylightDown(state, level, pos)
                : source.propagatesSkylightDown(sourceView(level, pos, source), pos);
    }

    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    @Override
    public int getLightEmission(
            BlockState state, BlockGetter level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null ? 0
                : source.getLightEmission(sourceView(level, pos, source), pos);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        // The real answer is position-owned, so allow the positional signal hooks below.
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level,
            BlockPos pos, Direction direction) {
        BlockState source = sourceState(level, pos);
        return source == null ? 0
                : source.getSignal(sourceView(level, pos, source), pos, direction);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level,
            BlockPos pos, Direction direction) {
        BlockState source = sourceState(level, pos);
        return source == null ? 0
                : source.getDirectSignal(
                        sourceView(level, pos, source), pos, direction);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        // As with weak power, the stored source is only known once level and position arrive.
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null ? 0 : source.getAnalogOutputSignal(level, pos);
    }

    @Override
    public String getDescriptionId() {
        return "block.mirebound.adaptive_medium";
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player,
            BlockGetter level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null || sourceMiningOverridden(level, pos)
                ? super.getDestroyProgress(state, player, level, pos)
                : source.getDestroyProgress(
                        player, sourceView(level, pos, source), pos);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level,
            BlockPos pos, Player player) {
        BlockState source = sourceState(level, pos);
        return source == null || sourceMiningOverridden(level, pos)
                ? super.canHarvestBlock(state, level, pos, player)
                : source.canHarvestBlock(
                        sourceView(level, pos, source), pos, player);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level,
            BlockPos pos, @Nullable Entity entity) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getSoundType(state, level, pos, entity)
                : source.getSoundType(level, pos, entity);
    }

    @Override
    public float getFriction(BlockState state, LevelReader level,
            BlockPos pos, @Nullable Entity entity) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getFriction(state, level, pos, entity)
                : source.getFriction(level, pos, entity);
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level,
            BlockPos pos, LivingEntity entity) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.isLadder(state, level, pos, entity)
                : source.isLadder(level, pos, entity);
    }

    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level,
            BlockPos pos, Direction facing, BlockState plant) {
        // A converted block must retain the sinking surface as a valid place
        // for ordinary plants, even when its source block has no plant rule.
        if (MudPlantSupport.canSustain(state, facing, plant)) {
            return TriState.TRUE;
        }
        BlockState source = sourceState(level, pos);
        if (source == null) {
            return super.canSustainPlant(state, level, pos, facing, plant);
        }
        TriState decision = source.canSustainPlant(
                sourceView(level, pos, source), pos, facing, plant);
        if (!decision.isDefault()) {
            return decision;
        }
        // Vanilla crops fall back to an instanceof FarmBlock check, which a
        // universal proxy cannot satisfy even though its stored source can.
        return facing == Direction.UP
                && source.getBlock() instanceof FarmBlock
                && plant.is(BlockTags.MAINTAINS_FARMLAND)
                        ? TriState.TRUE : TriState.DEFAULT;
    }

    @Override
    public boolean isFertile(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.isFertile(state, level, pos)
                : source.isFertile(sourceView(level, pos, source), pos);
    }

    @Override
    public boolean canBeHydrated(BlockState state, BlockGetter level,
            BlockPos pos, FluidState fluid, BlockPos fluidPos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.canBeHydrated(state, level, pos, fluid, fluidPos)
                : source.canBeHydrated(
                        sourceView(level, pos, source), pos, fluid, fluidPos);
    }

    @Override
    public boolean isConduitFrame(BlockState state, LevelReader level,
            BlockPos pos, BlockPos conduit) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.isConduitFrame(state, level, pos, conduit)
                : source.isConduitFrame(level, pos, conduit);
    }

    @Override
    public boolean isPortalFrame(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.isPortalFrame(state, level, pos)
                : source.isPortalFrame(sourceView(level, pos, source), pos);
    }

    @Override
    public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getEnchantPowerBonus(state, level, pos)
                : source.getEnchantPowerBonus(level, pos);
    }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, SignalGetter level,
            BlockPos pos, Direction side) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.shouldCheckWeakPower(state, level, pos, side)
                : source.shouldCheckWeakPower(level, pos, side);
    }

    @Override
    @Nullable
    public Integer getBeaconColorMultiplier(BlockState state, LevelReader level,
            BlockPos pos, BlockPos beaconPos) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getBeaconColorMultiplier(state, level, pos, beaconPos)
                : source.getBeaconColorMultiplier(level, pos, beaconPos);
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level,
            BlockPos pos, Direction direction) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getFlammability(state, level, pos, direction)
                : source.getFlammability(
                        sourceView(level, pos, source), pos, direction);
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level,
            BlockPos pos, Direction direction) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.isFlammable(state, level, pos, direction)
                : source.isFlammable(
                        sourceView(level, pos, source), pos, direction);
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level,
            BlockPos pos, Direction direction) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getFireSpreadSpeed(state, level, pos, direction)
                : source.getFireSpreadSpeed(
                        sourceView(level, pos, source), pos, direction);
    }

    @Override
    public boolean isFireSource(BlockState state, LevelReader level,
            BlockPos pos, Direction direction) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.isFireSource(state, level, pos, direction)
                : source.isFireSource(level, pos, direction);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level,
            BlockPos pos, Entity entity) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.canEntityDestroy(state, level, pos, entity)
                : source.canEntityDestroy(
                        sourceView(level, pos, source), pos, entity);
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level,
            BlockPos pos, Explosion explosion) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.canDropFromExplosion(state, level, pos, explosion)
                : source.canDropFromExplosion(
                        sourceView(level, pos, source), pos, explosion);
    }

    @Override
    public boolean isScaffolding(BlockState state, LevelReader level,
            BlockPos pos, LivingEntity entity) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.isScaffolding(state, level, pos, entity)
                : source.isScaffolding(entity);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level,
            BlockPos pos, @Nullable Direction direction) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.canConnectRedstone(state, level, pos, direction)
                : source.canRedstoneConnectTo(
                        sourceView(level, pos, source), pos, direction);
    }

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos,
            BlockState state, BlockState neighborState, Direction direction) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.hidesNeighborFace(level, pos, state, neighborState, direction)
                : source.hidesNeighborFace(
                        sourceView(level, pos, source), pos,
                        neighborState, direction);
    }

    @Override
    public MapColor getMapColor(BlockState state, BlockGetter level,
            BlockPos pos, MapColor defaultColor) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getMapColor(state, level, pos, defaultColor)
                : source.getMapColor(sourceView(level, pos, source), pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level,
            BlockPos pos, Explosion explosion) {
        BlockState source = sourceState(level, pos);
        return source == null
                ? super.getExplosionResistance(state, level, pos, explosion)
                : source.getExplosionResistance(
                        sourceView(level, pos, source), pos, explosion);
    }

    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos,
            Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        BlockState source = sourceState(level, pos);
        if (source != null) {
            source.onExplosionHit(level, pos, explosion, dropConsumer);
            return;
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
            BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (level instanceof ServerLevel serverLevel
                && state.getBlock() != newState.getBlock()
                && !AdaptiveMudService.mutationActive()) {
            AdaptiveMudService.forgetRemovedProxy(serverLevel, pos);
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target,
            LevelReader level, BlockPos pos, Player player) {
        BlockState source = sourceState(level, pos);
        if (source != null) {
            return source.getBlock().getCloneItemStack(
                    source, target, level, pos, player);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState source = sourceState(level, pos);
        if (source != null) {
            // ServerPlayerGameMode uses this returned state for harvest checks,
            // tool durability and the eventual playerDestroy call.
            return source.getBlock().playerWillDestroy(level, pos, source, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos,
            BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (state.getBlock() != this && !(state.getBlock() instanceof AdaptiveMudBlock)) {
            state.getBlock().playerDestroy(level, player, pos, state, blockEntity, tool);
            return;
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Nullable
    public static BlockState sourceState(BlockGetter level, BlockPos pos) {
        SableCompat.StorageAccess access = SableCompat.storageAccess(level, pos);
        if (access == null) {
            return null;
        }
        BlockState source = null;
        if (access.level() instanceof ServerLevel serverLevel) {
            source = AdaptiveMudSourceStore.get(serverLevel).sourceState(access.pos());
        } else if (access.level().isClientSide()) {
            source = AdaptiveMudClientCache.sourceState(access.level(), access.pos());
        }
        return source == null || source.getBlock() instanceof AdaptiveMudBlock ? null : source;
    }

    public static double sourceSurfaceHeight(
            BlockGetter level, BlockPos pos, BlockState adaptiveState) {
        VoxelShape shape = sourceShape(level, pos, adaptiveState);
        return shape.isEmpty() ? Double.NaN : shape.bounds().maxY;
    }

    public static double sourceSurfaceHeightAt(
            BlockGetter level, BlockPos pos, BlockState adaptiveState,
            double localX, double localZ) {
        return AdaptiveMudDeformation.topSurfaceAt(
                sourceShape(level, pos, adaptiveState),
                localX, localZ);
    }

    public static VoxelShape sourceShape(
            BlockGetter level, BlockPos pos, BlockState adaptiveState) {
        if (!(adaptiveState.getBlock() instanceof AdaptiveMudBlock adaptive)) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        BlockState source = sourceState(level, pos);
        if (source == null) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        BlockGetter sourceLevel = sourceView(level, pos, source);
        VoxelShape shape = source.getCollisionShape(
                sourceLevel, pos, CollisionContext.empty());
        if (shape.isEmpty()) {
            shape = source.getShape(sourceLevel, pos, CollisionContext.empty());
        }
        return adaptive.deformedSourceShape(adaptiveState, source, shape);
    }

    private static BlockGetter sourceView(
            BlockGetter level, BlockPos pos, BlockState source) {
        return AdaptiveMudSourceView.wrap(level, pos, source);
    }

    private boolean sourceMiningOverridden(BlockGetter level, BlockPos pos) {
        SableCompat.StorageAccess access = SableCompat.storageAccess(level, pos);
        return access != null
                && MudMediumRuntime.value(access.level(), access.pos(), medium(),
                        MudPhysicsParameter.HARVEST_OVERRIDE_SOURCE_ENABLED) >= 0.5D;
    }

    private VoxelShape deformedSourceShape(
            BlockState state, BlockState source, VoxelShape shape) {
        return AdaptiveMudDeformation.deform(
                source,
                shape,
                MudBlock.heightPixels(state, medium()) / 16.0F,
                MudBlock.surfaceDirection(state, medium()));
    }
}
