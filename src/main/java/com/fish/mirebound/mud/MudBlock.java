package com.fish.mirebound.mud;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsSystem;
import com.fish.mirebound.mud.flow.MudFlowSystem;
import com.fish.mirebound.mud.flow.MudGravitySystem;
import com.fish.mirebound.mud.tuning.MudTuningManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.common.util.TriState;

public class MudBlock extends Block implements Fallable {
    public static final BooleanProperty STACK_FILLED = BooleanProperty.create("stack_filled");
    public static final EnumProperty<MudBlockVariant> VARIANT =
            EnumProperty.create("variant", MudBlockVariant.class);
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 1, 16);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final ThreadLocal<Integer> SHAPE_EDIT_DEPTH = ThreadLocal.withInitial(() -> 0);

    public MudBlock(BlockBehaviour.Properties properties) {
        this(properties, SinkingMedium.MUD);
    }

    private final SinkingMedium medium;
    private final float harvestBaselineHardness;

    public MudBlock(BlockBehaviour.Properties properties, SinkingMedium medium) {
        this(properties, medium, 1.0F);
    }

    public MudBlock(BlockBehaviour.Properties properties, SinkingMedium medium,
            float harvestBaselineHardness) {
        // FlowingFluid treats every non-solid block as replaceable even when the
        // block itself is not marked replaceable. This legacy solidity flag only
        // protects the occupied cell; player collision remains the empty shape.
        super(properties.forceSolidOn());
        this.medium = medium;
        this.harvestBaselineHardness = Math.max(0.001F, harvestBaselineHardness);
        BlockState state = defaultBlockState();
        if (state.hasProperty(STACK_FILLED)) {
            state = state.setValue(STACK_FILLED, false);
        }
        if (state.hasProperty(VARIANT)) {
            state = state.setValue(VARIANT, MudBlockVariant.DEFAULT);
        }
        if (state.hasProperty(HEIGHT)) {
            state = state.setValue(HEIGHT, 16);
        }
        if (state.hasProperty(FACING)) {
            state = state.setValue(FACING, Direction.UP);
        }
        registerDefaultState(state);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    public SinkingMedium medium() {
        return medium;
    }

    public float harvestBaselineHardness() {
        return harvestBaselineHardness;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return state.hasProperty(FACING)
                ? state.setValue(FACING, context.getClickedFace())
                : state;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (SableCompat.isPhysicsBakeContext(context)) {
            return shape(state, level);
        }
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Boat
                && surfaceDirection(state, medium) == Direction.UP) {
            return shape(state, level);
        }
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof LivingEntity living
                && !(living instanceof Player)
                && surfaceDirection(state, medium) == Direction.UP) {
            if (level instanceof Level world) {
                VoxelShape sinkSupport = MudMobPhysics.collisionShape(
                        world, pos, state, medium, living);
                if (sinkSupport != null) {
                    return sinkSupport;
                }
            }
        }
        if (level instanceof Level world
                && MudBehaviorContext.sculk(world, pos, medium)
                && context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && !player.isSpectator()
                && player.isShiftKeyDown()
                && surfaceDirection(state, medium) == Direction.UP) {
            double surfaceY = pos.getY() + surfaceHeight(state, medium);
            if (player.getBoundingBox().minY >= surfaceY - 1.0E-4D) {
                return shape(state, level);
            }
        }
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level,
            BlockPos pos, Direction facing, BlockState plant) {
        return MudPlantSupport.result(state, facing, plant);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state, level);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state, level);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return isOpaqueForAmbientOcclusion(state, level, pos)
                ? localShape(state, medium)
                : Shapes.empty();
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    protected boolean isOpaqueForAmbientOcclusion(
            BlockState state, BlockGetter level, BlockPos pos) {
        return medium.opaqueBlock();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STACK_FILLED, VARIANT, HEIGHT, FACING);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!managesVolumeLifecycle()) {
            super.onPlace(state, level, pos, oldState, movedByPiston);
            return;
        }
        if (!level.isClientSide()) {
            MudTuningManager.markMudChanged(level);
        }
        if (!level.isClientSide() && !shapeEditActive()) {
            updateStackFill(level, pos);
            updateStackFill(level, pos.relative(
                    surfaceDirection(state, medium).getOpposite()));
            if (level instanceof ServerLevel serverLevel && !MudFlowSystem.mutationActive()) {
                MudFlowSystem.wake(serverLevel, pos);
                MudGravitySystem.wake(serverLevel, pos);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
            BlockState newState, boolean movedByPiston) {
        if (!managesVolumeLifecycle()) {
            super.onRemove(state, level, pos, newState, movedByPiston);
            return;
        }
        if (!level.isClientSide() && state.getBlock() != newState.getBlock()) {
            MudTuningManager.markMudChanged(level);
            if (level instanceof ServerLevel serverLevel && !MudFlowSystem.mutationActive()) {
                MudFlowSystem.forget(serverLevel, pos);
                MudFlowSystem.wakeNeighbors(serverLevel, pos);
                MudGravitySystem.wake(serverLevel, pos.above());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {
        if (!managesVolumeLifecycle()) {
            super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
            return;
        }
        if (!level.isClientSide() && !shapeEditActive()
                && neighborPos.equals(pos.relative(
                        surfaceDirection(state, medium)))) {
            updateStackFill(level, pos);
        }
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel
                && !MudFlowSystem.mutationActive()) {
            MudFlowSystem.wake(serverLevel, pos);
            MudGravitySystem.wake(serverLevel, pos);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return managesVolumeLifecycle();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (managesVolumeLifecycle()) {
            MudFlowSystem.wakeNow(level, pos);
            MudGravitySystem.wake(level, pos);
        }
    }

    @Override
    protected void tick(
            BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MudGravitySystem.tick(state, level, pos, random);
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state,
            BlockState replacedState, FallingBlockEntity entity) {
        if (level instanceof ServerLevel serverLevel) {
            MudGravitySystem.onLand(serverLevel, pos, state, entity);
        }
    }

    protected boolean managesVolumeLifecycle() {
        return true;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof Boat) {
            return;
        }
        if (entity instanceof ItemEntity item) {
            DroppedItemPhysicsSystem.captureContact(level, pos, state, item, medium);
            return;
        }
        if (MudMediumRuntime.enabled(level, pos, medium)) {
            MudPhysics.applyMudEffects(level, pos, state, entity, medium);
        }
    }

    private VoxelShape shape(BlockState state, BlockGetter level) {
        if (state.hasProperty(STACK_FILLED) && state.getValue(STACK_FILLED)) {
            return Shapes.block();
        }
        return orientShape(
                shapeProfile(state, medium).visualShape(),
                surfaceDirection(state, medium));
    }

    public static MudBlockVariant variant(BlockState state) {
        return state.hasProperty(VARIANT)
                ? state.getValue(VARIANT) : MudBlockVariant.DEFAULT;
    }

    public static int heightPixels(BlockState state, SinkingMedium medium) {
        if (state.hasProperty(STACK_FILLED) && state.getValue(STACK_FILLED)) {
            return 16;
        }
        return configuredHeightPixels(state, medium);
    }

    static int configuredHeightPixels(BlockState state, SinkingMedium medium) {
        return switch (variant(state)) {
            case DEFAULT -> 16;
            case HEIGHT -> state.hasProperty(HEIGHT) ? state.getValue(HEIGHT) : 16;
            case SPECIAL -> MudShapeProfile.special(medium).heightPixels();
        };
    }

    public static int storedHeight(BlockState state) {
        return state.hasProperty(HEIGHT) ? state.getValue(HEIGHT) : 16;
    }

    public static double surfaceHeight(BlockState state, SinkingMedium medium) {
        return heightPixels(state, medium) / 16.0D;
    }

    public static Direction storedFacing(BlockState state) {
        return state.hasProperty(FACING)
                ? state.getValue(FACING)
                : Direction.UP;
    }

    /**
     * Full blocks have no meaningful attachment face and retain ordinary upward
     * sinking behavior. Partial blocks expose the face selected during placement.
     */
    public static Direction surfaceDirection(BlockState state, SinkingMedium medium) {
        return MudOrientation.surfaceDirection(
                configuredHeightPixels(state, medium), storedFacing(state));
    }

    public static boolean supportsVerticalSinking(BlockState state, SinkingMedium medium) {
        return surfaceDirection(state, medium) == Direction.UP;
    }

    public static VoxelShape localShape(BlockState state, SinkingMedium medium) {
        if (state.hasProperty(STACK_FILLED) && state.getValue(STACK_FILLED)) {
            return Shapes.block();
        }
        return orientShape(
                shapeProfile(state, medium).visualShape(),
                surfaceDirection(state, medium));
    }

    public static VoxelShape localShape(
            BlockGetter level, BlockPos pos,
            BlockState state, SinkingMedium medium) {
        if (level != null && pos != null
                && state.getBlock() instanceof AdaptiveMudBlock) {
            VoxelShape sourceShape = AdaptiveMudBlock.sourceShape(level, pos, state);
            if (!sourceShape.isEmpty()) {
                return sourceShape;
            }
        }
        return localShape(state, medium);
    }

    public static AABB localBounds(BlockState state, SinkingMedium medium) {
        VoxelShape shape = localShape(state, medium);
        return shape.isEmpty()
                ? new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D)
                : shape.bounds();
    }

    public static AABB localBounds(
            BlockGetter level, BlockPos pos,
            BlockState state, SinkingMedium medium) {
        VoxelShape shape = localShape(level, pos, state, medium);
        return shape.isEmpty()
                ? new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D)
                : shape.bounds();
    }

    public static boolean containsLocalPoint(BlockState state, SinkingMedium medium,
            Vec3 localPoint, double tolerance) {
        for (AABB box : localShape(state, medium).toAabbs()) {
            if (localPoint.x >= box.minX - tolerance
                    && localPoint.x <= box.maxX + tolerance
                    && localPoint.y >= box.minY - tolerance
                    && localPoint.y <= box.maxY + tolerance
                    && localPoint.z >= box.minZ - tolerance
                    && localPoint.z <= box.maxZ + tolerance) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsLocalPoint(
            BlockGetter level, BlockPos pos,
            BlockState state, SinkingMedium medium,
            Vec3 localPoint, double tolerance) {
        for (AABB box : localShape(level, pos, state, medium).toAabbs()) {
            if (localPoint.x >= box.minX - tolerance
                    && localPoint.x <= box.maxX + tolerance
                    && localPoint.y >= box.minY - tolerance
                    && localPoint.y <= box.maxY + tolerance
                    && localPoint.z >= box.minZ - tolerance
                    && localPoint.z <= box.maxZ + tolerance) {
                return true;
            }
        }
        return false;
    }

    public static Vec3 orientLocalPoint(Vec3 point, Direction facing) {
        return MudOrientation.orientPoint(point, facing);
    }

    public static Direction orientDirection(Direction direction, Direction facing) {
        return MudOrientation.orientDirection(direction, facing);
    }

    private static VoxelShape orientShape(VoxelShape source, Direction facing) {
        if (facing == Direction.UP || source.isEmpty()) {
            return source;
        }
        VoxelShape result = Shapes.empty();
        for (AABB box : source.toAabbs()) {
            Vec3 minimum = new Vec3(
                    Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY);
            Vec3 maximum = new Vec3(
                    Double.NEGATIVE_INFINITY,
                    Double.NEGATIVE_INFINITY,
                    Double.NEGATIVE_INFINITY);
            for (int corner = 0; corner < 8; corner++) {
                Vec3 transformed = orientLocalPoint(new Vec3(
                        (corner & 1) == 0 ? box.minX : box.maxX,
                        (corner & 2) == 0 ? box.minY : box.maxY,
                        (corner & 4) == 0 ? box.minZ : box.maxZ),
                        facing);
                minimum = new Vec3(
                        Math.min(minimum.x, transformed.x),
                        Math.min(minimum.y, transformed.y),
                        Math.min(minimum.z, transformed.z));
                maximum = new Vec3(
                        Math.max(maximum.x, transformed.x),
                        Math.max(maximum.y, transformed.y),
                        Math.max(maximum.z, transformed.z));
            }
            result = Shapes.or(result, Shapes.box(
                    Mth.clamp(minimum.x, 0.0D, 1.0D),
                    Mth.clamp(minimum.y, 0.0D, 1.0D),
                    Mth.clamp(minimum.z, 0.0D, 1.0D),
                    Mth.clamp(maximum.x, 0.0D, 1.0D),
                    Mth.clamp(maximum.y, 0.0D, 1.0D),
                    Mth.clamp(maximum.z, 0.0D, 1.0D)));
        }
        return result;
    }

    public static MudShapeProfile shapeProfile(BlockState state, SinkingMedium medium) {
        if (state.hasProperty(STACK_FILLED) && state.getValue(STACK_FILLED)) {
            return new MudShapeProfile(MudShapeType.FULL, 1.0D);
        }
        return switch (variant(state)) {
            case DEFAULT -> new MudShapeProfile(MudShapeType.FULL, 1.0D);
            case HEIGHT -> new MudShapeProfile(MudShapeType.STATIC_HEIGHT,
                    heightPixels(state, medium) / 16.0D);
            case SPECIAL -> MudShapeProfile.special(medium);
        };
    }

    public static boolean setInstanceShape(Level level, BlockPos pos,
            MudBlockVariant requestedVariant, int requestedHeight) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MudBlock)
                || !state.hasProperty(VARIANT) || !state.hasProperty(HEIGHT)) {
            return false;
        }
        setInstanceShapes(level, List.of(pos), requestedVariant, requestedHeight);
        return true;
    }

    public static int setInstanceShapes(Level level, List<BlockPos> positions,
            MudBlockVariant requestedVariant, int requestedHeight) {
        if (positions.isEmpty()) {
            return 0;
        }
        int height = Math.max(1, Math.min(16, requestedHeight));
        Set<BlockPos> stackRefresh = new HashSet<>();
        Set<BlockPos> changed = new HashSet<>();
        beginShapeEdit();
        try {
            for (BlockPos mutablePos : positions) {
                BlockPos pos = mutablePos.immutable();
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof MudBlock mudBlock)
                        || state.getBlock() instanceof AdaptiveMudBlock
                        || !state.hasProperty(VARIANT) || !state.hasProperty(HEIGHT)) {
                    continue;
                }
                MudBlockVariant variant = requestedVariant == null
                        ? MudBlockVariant.DEFAULT : requestedVariant;
                if (variant == MudBlockVariant.SPECIAL
                        && !MudShapeProfile.supportsSpecial(mudBlock.medium())) {
                    variant = MudBlockVariant.HEIGHT;
                }
                Direction oldDirection = surfaceDirection(state, mudBlock.medium());
                BlockState next = state.setValue(VARIANT, variant).setValue(HEIGHT, height);
                Direction newDirection = surfaceDirection(next, mudBlock.medium());
                stackRefresh.add(pos);
                stackRefresh.add(pos.relative(oldDirection.getOpposite()));
                stackRefresh.add(pos.relative(newDirection.getOpposite()));
                if (next != state && level.setBlock(pos, next, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)) {
                    changed.add(pos);
                }
                if (level instanceof ServerLevel serverLevel) {
                    MudBlockProfileStore.get(serverLevel).trackShapeState(
                            serverLevel, pos, level.getBlockState(pos));
                }
            }
        } finally {
            endShapeEdit();
        }

        for (BlockPos pos : stackRefresh) {
            updateStackFill(level, pos);
        }
        for (BlockPos pos : changed) {
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
        return changed.size();
    }

    public static void refreshStackFills(Level level, List<BlockPos> positions) {
        Set<BlockPos> refresh = new HashSet<>();
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof MudBlock mudBlock) {
                refresh.add(pos.immutable());
                refresh.add(pos.relative(surfaceDirection(state, mudBlock.medium()).getOpposite()));
            }
        }
        for (BlockPos pos : refresh) {
            updateStackFill(level, pos);
        }
    }

    private static boolean shapeEditActive() {
        return SHAPE_EDIT_DEPTH.get() > 0;
    }

    private static void beginShapeEdit() {
        SHAPE_EDIT_DEPTH.set(SHAPE_EDIT_DEPTH.get() + 1);
    }

    private static void endShapeEdit() {
        int remaining = SHAPE_EDIT_DEPTH.get() - 1;
        if (remaining <= 0) {
            SHAPE_EDIT_DEPTH.remove();
        } else {
            SHAPE_EDIT_DEPTH.set(remaining);
        }
    }

    private static void updateStackFill(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MudBlock mudBlock)
                || !state.hasProperty(STACK_FILLED)) {
            return;
        }
        Direction facing = surfaceDirection(state, mudBlock.medium());
        boolean filled = MudMediumRuntime.autoStackFill(level, pos, mudBlock.medium())
                && level.getBlockState(pos.relative(facing)).getBlock()
                instanceof MudBlock;
        if (state.getValue(STACK_FILLED) != filled) {
            level.setBlock(pos, state.setValue(STACK_FILLED, filled), UPDATE_CLIENTS);
        }
    }
}
