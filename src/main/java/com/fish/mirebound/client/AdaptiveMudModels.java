package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.AdaptiveMudBlockEntity;
import com.fish.mirebound.adaptive.AdaptiveMudDeformation;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

/** Delegates adaptive proxy rendering to the exact stored source block state. */
public final class AdaptiveMudModels {
    private static final ModelProperty<BlockState> SOURCE_STATE = new ModelProperty<>();
    private static final ModelProperty<ModelData> SOURCE_DATA = new ModelProperty<>();
    private static final ModelProperty<Integer> SOURCE_LIGHT_EMISSION = new ModelProperty<>();
    private static final ModelProperty<Integer> HIDDEN_FACES = new ModelProperty<>();
    private static final ModelProperty<Boolean> SOURCE_DEFORMATION = new ModelProperty<>();
    private static final ModelProperty<Boolean> SOURCE_TRANSFORM = new ModelProperty<>();

    private AdaptiveMudModels() {
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        for (AdaptiveMudBlock block : ModBlocks.adaptiveBlocks()) {
            BlockState nativeState = ModBlocks.blockFor(block.medium()).defaultBlockState();
            BakedModel fallback = event.getModels().get(
                    BlockModelShaper.stateToModelLocation(nativeState));
            if (fallback == null) {
                fallback = event.getModels().get(
                        BlockModelShaper.stateToModelLocation(block.defaultBlockState()));
            }
            if (fallback == null) {
                continue;
            }
            BakedModel wrapper = new AdaptiveBakedModel(fallback);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                event.getModels().put(BlockModelShaper.stateToModelLocation(state), wrapper);
            }
        }
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null) {
                return -1;
            }
            BlockState source = AdaptiveMudClientCache.sourceState(
                    Minecraft.getInstance().level, pos);
            if (source == null || source.getBlock() instanceof AdaptiveMudBlock) {
                return -1;
            }
            return event.getBlockColors().getColor(
                    source, sourceView(level, pos, source), pos, tintIndex);
        }, ModBlocks.adaptiveBlocks().toArray(AdaptiveMudBlock[]::new));
    }

    private static BakedModel sourceModel(BlockState source, BakedModel fallback) {
        if (source == null || source.getBlock() instanceof AdaptiveMudBlock) {
            return fallback;
        }
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(source);
    }

    private static final class AdaptiveBakedModel extends BakedModelWrapper<BakedModel>
            implements IDynamicBakedModel {
        private AdaptiveBakedModel(BakedModel fallback) {
            super(fallback);
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos,
                BlockState state, ModelData modelData) {
            BlockState source = AdaptiveMudClientCache.sourceState(
                    Minecraft.getInstance().level, pos);
            if (source == null) {
                return modelData;
            }
            BakedModel model = sourceModel(source, originalModel);
            BlockAndTintGetter sourceLevel = sourceView(level, pos, source);
            BlockEntity sourceEntity = sourceLevel.getBlockEntity(pos);
            ModelData baseSourceData = sourceEntity == null
                    ? ModelData.EMPTY : sourceEntity.getModelData();
            ModelData sourceData = model.getModelData(
                    sourceLevel, pos, source, baseSourceData);
            AdaptiveMudBlock adaptive = (AdaptiveMudBlock) state.getBlock();
            float height = MudBlock.heightPixels(state, adaptive.medium()) / 16.0F;
            Direction facing = MudBlock.surfaceDirection(state, adaptive.medium());
            VoxelShape sourceShape = source.getShape(
                    sourceLevel, pos, CollisionContext.empty());
            float sourceMinimumY = sourceShape.isEmpty()
                    ? 0.0F : (float) sourceShape.bounds().minY;
            return modelData.derive()
                    .with(SOURCE_STATE, source)
                    .with(SOURCE_DATA, sourceData)
                    .with(SOURCE_LIGHT_EMISSION,
                            source.getLightEmission(sourceLevel, pos))
                    .with(HIDDEN_FACES, hiddenFaceMask(level, pos, state))
                    .with(SOURCE_DEFORMATION,
                            AdaptiveMudDeformation.enabledByDefault(source))
                    .with(SOURCE_TRANSFORM,
                            requiresModelTransform(height, facing, sourceMinimumY))
                    .build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData modelData, @Nullable RenderType renderType) {
            Integer hidden = modelData.get(HIDDEN_FACES);
            if (side != null && hidden != null && (hidden & faceBit(side)) != 0) {
                return List.of();
            }
            BlockState source = modelData.get(SOURCE_STATE);
            ModelData sourceData = modelData.get(SOURCE_DATA);
            BakedModel model = sourceModel(source, originalModel);
            if (state != null && state.getBlock() instanceof AdaptiveMudBlock block) {
                float height = MudBlock.heightPixels(state, block.medium()) / 16.0F;
                if (Boolean.TRUE.equals(modelData.get(SOURCE_DEFORMATION))
                        && Boolean.TRUE.equals(modelData.get(SOURCE_TRANSFORM))) {
                    if (side != null) {
                        return List.of();
                    }
                    BlockState sourceState = source == null ? state : source;
                    ModelData data = sourceData == null ? ModelData.EMPTY : sourceData;
                    List<BakedQuad> all = new ArrayList<>();
                    all.addAll(model.getQuads(
                            sourceState, null, random, data, renderType));
                    for (Direction sourceSide : Direction.values()) {
                        all.addAll(model.getQuads(
                                sourceState, sourceSide, random, data, renderType));
                    }
                    Direction facing = MudBlock.surfaceDirection(state, block.medium());
                    int hiddenFaces = hidden == null ? 0 : hidden;
                    ModelGeometry geometry = ModelGeometry.analyze(all);
                    if (!geometry.deformable()) {
                        return filterHidden(all, hiddenFaces);
                    }
                    List<BakedQuad> transformed = new ArrayList<>(all.size());
                    for (BakedQuad quad : all) {
                        BakedQuad scaled = scaleToHeight(
                                quad, height, facing, geometry);
                        if ((hiddenFaces & faceBit(scaled.getDirection())) == 0) {
                            transformed.add(scaled);
                        }
                    }
                    return List.copyOf(transformed);
                }
            }
            List<BakedQuad> quads = model.getQuads(
                    source == null ? state : source,
                    side,
                    random,
                    sourceData == null ? ModelData.EMPTY : sourceData,
                    renderType);
            return quads;
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(
                BlockState state, RandomSource random, ModelData modelData) {
            BlockState source = modelData.get(SOURCE_STATE);
            ModelData sourceData = modelData.get(SOURCE_DATA);
            BakedModel model = sourceModel(source, originalModel);
            return model.getRenderTypes(
                    source == null ? state : source,
                    random,
                    sourceData == null ? ModelData.EMPTY : sourceData);
        }

        @Override
        public TriState useAmbientOcclusion(
                BlockState state, ModelData modelData, RenderType renderType) {
            BlockState source = modelData.get(SOURCE_STATE);
            ModelData sourceData = modelData.get(SOURCE_DATA);
            BakedModel model = sourceModel(source, originalModel);
            TriState sourceAmbientOcclusion = model.useAmbientOcclusion(
                    source == null ? state : source,
                    sourceData == null ? ModelData.EMPTY : sourceData,
                    renderType);
            Integer sourceLightEmission = modelData.get(SOURCE_LIGHT_EMISSION);
            return sourceLightEmission == null
                    ? sourceAmbientOcclusion
                    : sourceAmbientOcclusion(
                            sourceAmbientOcclusion, sourceLightEmission);
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData modelData) {
            BlockState source = modelData.get(SOURCE_STATE);
            ModelData sourceData = modelData.get(SOURCE_DATA);
            return sourceModel(source, originalModel).getParticleIcon(
                    sourceData == null ? ModelData.EMPTY : sourceData);
        }

        private static int hiddenFaceMask(
                BlockAndTintGetter level, BlockPos pos, BlockState state) {
            if (!(state.getBlock() instanceof AdaptiveMudBlock block)) {
                return 0;
            }
            BlockState source = AdaptiveMudClientCache.sourceState(
                    Minecraft.getInstance().level, pos);
            if (source != null && !sourceOpaque(source, level, pos)) {
                return 0;
            }
            var currentShape = MudBlock.localShape(level, pos, state, block.medium());
            int mask = 0;
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);
                BlockState neighborState = level.getBlockState(neighborPos);
                SinkingMedium neighbor = ModBlocks.mediumOf(neighborState.getBlock());
                if (neighbor != null
                        && opaqueMudState(level, neighborPos, neighborState, neighbor)
                        && MudVariantModels.shouldCullCoveredMudInterface(
                                block.medium(), neighbor)
                        && MudVariantModels.coversCurrentSharedFace(
                                currentShape,
                                MudBlock.localShape(
                                        level, neighborPos, neighborState, neighbor),
                                direction)) {
                    mask |= faceBit(direction);
                }
            }
            return mask;
        }

        private static boolean opaqueMudState(BlockAndTintGetter level, BlockPos pos,
                BlockState state, SinkingMedium medium) {
            if (!(state.getBlock() instanceof AdaptiveMudBlock)) {
                return medium.opaqueBlock();
            }
            BlockState source = AdaptiveMudClientCache.sourceState(
                    Minecraft.getInstance().level, pos);
            return source == null || sourceOpaque(source, level, pos);
        }
    }

    private static int faceBit(Direction direction) {
        return 1 << direction.ordinal();
    }

    /**
     * Gives dynamic source models their original state and virtual block entity at
     * the proxy position while delegating every neighboring query to the real world.
     */
    static BlockAndTintGetter sourceView(
            BlockAndTintGetter level, BlockPos pos, BlockState source) {
        BlockEntity sourceEntity = null;
        BlockEntity current = level.getBlockEntity(pos);
        if (current instanceof AdaptiveMudBlockEntity adaptiveEntity) {
            sourceEntity = adaptiveEntity.virtualSourceBlockEntity();
        }
        return new SourceBlockAndTintGetter(
                level, pos.immutable(), source, sourceEntity);
    }

    private record SourceBlockAndTintGetter(
            BlockAndTintGetter delegate,
            BlockPos sourcePos,
            BlockState sourceState,
            @Nullable BlockEntity sourceEntity) implements BlockAndTintGetter {
        @Override
        @Nullable
        public BlockEntity getBlockEntity(BlockPos pos) {
            return sourcePos.equals(pos) ? sourceEntity : delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return sourcePos.equals(pos) ? sourceState : delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return sourcePos.equals(pos)
                    ? sourceState.getFluidState() : delegate.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return delegate.getBlockTint(pos, resolver);
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return delegate.getBrightness(layer, pos);
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return delegate.getRawBrightness(pos, amount);
        }
    }

    static TriState sourceAmbientOcclusion(
            TriState sourcePreference, int sourceLightEmission) {
        return sourcePreference == TriState.DEFAULT && sourceLightEmission > 0
                ? TriState.FALSE
                : sourcePreference;
    }

    public static boolean shouldCullSourceInterface(
            BlockState currentState, BlockState neighborState,
            BlockGetter level, BlockPos currentPos, BlockPos neighborPos,
            Direction face) {
        boolean currentAdaptive = currentState.getBlock() instanceof AdaptiveMudBlock;
        boolean neighborAdaptive = neighborState.getBlock() instanceof AdaptiveMudBlock;
        if (!currentAdaptive && !neighborAdaptive) {
            return false;
        }

        BlockState currentSource = currentAdaptive
                ? AdaptiveMudClientCache.sourceState(
                        Minecraft.getInstance().level, currentPos)
                : currentState;
        BlockState neighborSource = neighborAdaptive
                ? AdaptiveMudClientCache.sourceState(
                        Minecraft.getInstance().level, neighborPos)
                : neighborState;
        if (currentSource == null || neighborSource == null
                || currentSource.getBlock() instanceof AdaptiveMudBlock
                || neighborSource.getBlock() instanceof AdaptiveMudBlock) {
            return false;
        }

        VoxelShape currentShape = currentAdaptive
                ? adaptiveSourceShape(currentState, currentSource,
                        level, currentPos)
                : currentState.getShape(level, currentPos, CollisionContext.empty());
        VoxelShape neighborShape = neighborAdaptive
                ? adaptiveSourceShape(neighborState, neighborSource,
                        level, neighborPos)
                : neighborState.getShape(level, neighborPos, CollisionContext.empty());
        return sourceStatesHideSharedFace(
                currentSource == neighborSource,
                sourceOpaque(currentSource, level, currentPos),
                sourceOpaque(neighborSource, level, neighborPos),
                currentSource.skipRendering(neighborSource, face),
                MudVariantModels.coversCurrentSharedFace(
                        currentShape, neighborShape, face));
    }

    public static boolean shouldPreserveSourceInterface(
            BlockState currentState, BlockState neighborState,
            BlockGetter level, BlockPos currentPos, BlockPos neighborPos) {
        boolean currentAdaptive = currentState.getBlock() instanceof AdaptiveMudBlock;
        boolean neighborAdaptive = neighborState.getBlock() instanceof AdaptiveMudBlock;
        if (!currentAdaptive && !neighborAdaptive) {
            return false;
        }
        BlockState currentSource = currentAdaptive
                ? AdaptiveMudClientCache.sourceState(
                        Minecraft.getInstance().level, currentPos)
                : currentState;
        BlockState neighborSource = neighborAdaptive
                ? AdaptiveMudClientCache.sourceState(
                        Minecraft.getInstance().level, neighborPos)
                : neighborState;
        if (currentSource == null || neighborSource == null
                || currentSource.getBlock() instanceof AdaptiveMudBlock
                || neighborSource.getBlock() instanceof AdaptiveMudBlock) {
            return false;
        }
        return sourceStatesNeedVisibleInterface(
                currentSource == neighborSource,
                sourceOpaque(currentSource, level, currentPos),
                sourceOpaque(neighborSource, level, neighborPos));
    }

    static boolean sourceStatesHideSharedFace(
            boolean sameSource, boolean currentOpaque, boolean neighborOpaque,
            boolean sourceSkipsRendering, boolean sharedFaceCovered) {
        return sharedFaceCovered && sourceSkipsRendering
                && (sameSource || currentOpaque && neighborOpaque);
    }

    static boolean sourceStatesNeedVisibleInterface(
            boolean sameSource, boolean currentOpaque, boolean neighborOpaque) {
        return !sameSource && (!currentOpaque || !neighborOpaque);
    }

    private static VoxelShape adaptiveSourceShape(
            BlockState adaptiveState, BlockState sourceState,
            BlockGetter level, BlockPos pos) {
        AdaptiveMudBlock block = (AdaptiveMudBlock) adaptiveState.getBlock();
        return AdaptiveMudDeformation.deform(
                sourceState,
                sourceState.getShape(level, pos, CollisionContext.empty()),
                MudBlock.heightPixels(adaptiveState, block.medium()) / 16.0F,
                MudBlock.surfaceDirection(adaptiveState, block.medium()));
    }

    private static boolean sourceOpaque(
            BlockState source, BlockGetter level, BlockPos pos) {
        SinkingMedium sourceMedium = ModBlocks.mediumOf(source.getBlock());
        return sourceMedium == null
                ? source.isSolidRender(level, pos)
                : sourceMedium.opaqueBlock();
    }

    private static List<BakedQuad> filterHidden(
            List<BakedQuad> quads, int hiddenFaces) {
        if (hiddenFaces == 0) {
            return List.copyOf(quads);
        }
        List<BakedQuad> visible = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            if ((hiddenFaces & faceBit(quad.getDirection())) == 0) {
                visible.add(quad);
            }
        }
        return List.copyOf(visible);
    }

    private static BakedQuad scaleToHeight(
            BakedQuad quad, float height, Direction facing, ModelGeometry geometry) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        if (stride < 6) {
            return quad;
        }
        float minimumY = Float.POSITIVE_INFINITY;
        float maximumY = Float.NEGATIVE_INFINITY;
        float lowV = 0.0F;
        float highV = 0.0F;
        int lowCount = 0;
        int highCount = 0;
        for (int vertex = 0; vertex < 4; vertex++) {
            float y = Float.intBitsToFloat(vertices[vertex * stride + 1]);
            minimumY = Math.min(minimumY, y);
            maximumY = Math.max(maximumY, y);
        }
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float v = Float.intBitsToFloat(vertices[offset + 5]);
            if (Math.abs(y - minimumY) < 1.0E-4F) {
                lowV += v;
                lowCount++;
            }
            if (Math.abs(y - maximumY) < 1.0E-4F) {
                highV += v;
                highCount++;
            }
        }
        float croppedV = lowCount == 0 || highCount == 0 ? 0.0F
                : lowV / lowCount + (highV / highCount - lowV / lowCount) * height;
        boolean verticalSpan = maximumY - minimumY
                > geometry.verticalSpan() * 0.9F;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            boolean upper = Math.abs(y - maximumY) < 1.0E-4F;
            vertices[offset + 1] = Float.floatToRawIntBits(
                    compressedModelCoordinate(y, geometry.minimumY(), height));
            if (verticalSpan && upper) {
                vertices[offset + 5] = Float.floatToRawIntBits(croppedV);
            }
        }
        if (facing != Direction.UP) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * stride;
                var point = MudBlock.orientLocalPoint(
                        new net.minecraft.world.phys.Vec3(
                                Float.intBitsToFloat(vertices[offset]),
                                Float.intBitsToFloat(vertices[offset + 1]),
                                Float.intBitsToFloat(vertices[offset + 2])),
                        facing);
                vertices[offset] = Float.floatToRawIntBits((float) point.x);
                vertices[offset + 1] = Float.floatToRawIntBits((float) point.y);
                vertices[offset + 2] = Float.floatToRawIntBits((float) point.z);
            }
        }
        float[] normal = quadNormal(vertices, stride);
        Direction direction = normal == null
                ? MudBlock.orientDirection(quad.getDirection(), facing)
                : closestDirection(normal);
        if (normal != null && stride > 7) {
            int packedNormal = packedNormal(normal);
            for (int vertex = 0; vertex < 4; vertex++) {
                vertices[vertex * stride + 7] = packedNormal;
            }
        }
        return new BakedQuad(vertices, quad.getTintIndex(), direction,
                quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    private record ModelGeometry(float minimumY, float maximumY,
            boolean volumetricSurface) {
        private static final float EPSILON = 1.0E-4F;
        private static final double SURFACE_VOLUME_EPSILON = 1.0E-5D;

        private static ModelGeometry analyze(List<BakedQuad> quads) {
            float minimumY = Float.POSITIVE_INFINITY;
            float maximumY = Float.NEGATIVE_INFINITY;
            float minimumX = Float.POSITIVE_INFINITY;
            float maximumX = Float.NEGATIVE_INFINITY;
            float minimumZ = Float.POSITIVE_INFINITY;
            float maximumZ = Float.NEGATIVE_INFINITY;
            for (BakedQuad quad : quads) {
                int[] vertices = quad.getVertices();
                int stride = vertices.length / 4;
                if (stride < 3) {
                    continue;
                }
                float quadMinX = Float.POSITIVE_INFINITY;
                float quadMaxX = Float.NEGATIVE_INFINITY;
                float quadMinY = Float.POSITIVE_INFINITY;
                float quadMaxY = Float.NEGATIVE_INFINITY;
                float quadMinZ = Float.POSITIVE_INFINITY;
                float quadMaxZ = Float.NEGATIVE_INFINITY;
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * stride;
                    float x = Float.intBitsToFloat(vertices[offset]);
                    float y = Float.intBitsToFloat(vertices[offset + 1]);
                    float z = Float.intBitsToFloat(vertices[offset + 2]);
                    quadMinX = Math.min(quadMinX, x);
                    quadMaxX = Math.max(quadMaxX, x);
                    quadMinY = Math.min(quadMinY, y);
                    quadMaxY = Math.max(quadMaxY, y);
                    quadMinZ = Math.min(quadMinZ, z);
                    quadMaxZ = Math.max(quadMaxZ, z);
                }
                minimumX = Math.min(minimumX, quadMinX);
                maximumX = Math.max(maximumX, quadMaxX);
                minimumY = Math.min(minimumY, quadMinY);
                maximumY = Math.max(maximumY, quadMaxY);
                minimumZ = Math.min(minimumZ, quadMinZ);
                maximumZ = Math.max(maximumZ, quadMaxZ);
            }
            if (!Float.isFinite(minimumX) || !Float.isFinite(minimumY)
                    || !Float.isFinite(minimumZ)) {
                return new ModelGeometry(minimumY, maximumY, false);
            }
            float centerX = (minimumX + maximumX) * 0.5F;
            float centerY = (minimumY + maximumY) * 0.5F;
            float centerZ = (minimumZ + maximumZ) * 0.5F;
            List<float[]> normals = new ArrayList<>(quads.size());
            double surfaceVolume = 0.0D;
            for (BakedQuad quad : quads) {
                int[] vertices = quad.getVertices();
                int stride = vertices.length / 4;
                if (stride < 3) {
                    continue;
                }
                float[] normal = quadNormal(vertices, stride);
                if (normal != null) {
                    normals.add(normal);
                }
                surfaceVolume += quadSurfaceVolume(
                        vertices, stride, centerX, centerY, centerZ);
            }
            boolean volumetric = surfaceVolume > SURFACE_VOLUME_EPSILON
                    && normalsSpanVolume(normals.toArray(float[][]::new));
            return new ModelGeometry(minimumY, maximumY, volumetric);
        }

        private boolean deformable() {
            return supportsModelDeformation(
                    minimumY, maximumY, volumetricSurface);
        }

        private float verticalSpan() {
            return maximumY - minimumY;
        }
    }

    static boolean supportsModelDeformation(
            float minimumY, float maximumY, boolean volumetricSurface) {
        return Float.isFinite(minimumY) && Float.isFinite(maximumY)
                && maximumY - minimumY > 1.0E-4F
                && volumetricSurface;
    }

    static boolean normalsSpanVolume(float[]... normals) {
        float[] first = null;
        for (float[] normal : normals) {
            if (normalLengthSquared(normal) > 1.0E-8F) {
                first = normalized(normal);
                break;
            }
        }
        if (first == null) {
            return false;
        }
        for (float[] candidate : normals) {
            float[] second = normalized(candidate);
            float crossX = first[1] * second[2] - first[2] * second[1];
            float crossY = first[2] * second[0] - first[0] * second[2];
            float crossZ = first[0] * second[1] - first[1] * second[0];
            float crossLength = (float) Math.sqrt(
                    crossX * crossX + crossY * crossY + crossZ * crossZ);
            if (crossLength <= 1.0E-3F) {
                continue;
            }
            for (float[] thirdCandidate : normals) {
                float[] third = normalized(thirdCandidate);
                float determinant = Math.abs(
                        crossX * third[0]
                                + crossY * third[1]
                                + crossZ * third[2]) / crossLength;
                if (determinant > 1.0E-3F) {
                    return true;
                }
            }
        }
        return false;
    }

    private static float[] quadNormal(int[] vertices, int stride) {
        float firstX = Float.intBitsToFloat(vertices[3 * stride])
                - Float.intBitsToFloat(vertices[stride]);
        float firstY = Float.intBitsToFloat(vertices[3 * stride + 1])
                - Float.intBitsToFloat(vertices[stride + 1]);
        float firstZ = Float.intBitsToFloat(vertices[3 * stride + 2])
                - Float.intBitsToFloat(vertices[stride + 2]);
        float secondX = Float.intBitsToFloat(vertices[2 * stride])
                - Float.intBitsToFloat(vertices[0]);
        float secondY = Float.intBitsToFloat(vertices[2 * stride + 1])
                - Float.intBitsToFloat(vertices[1]);
        float secondZ = Float.intBitsToFloat(vertices[2 * stride + 2])
                - Float.intBitsToFloat(vertices[2]);
        float normalX = secondY * firstZ - secondZ * firstY;
        float normalY = secondZ * firstX - secondX * firstZ;
        float normalZ = secondX * firstY - secondY * firstX;
        float length = (float) Math.sqrt(
                normalX * normalX + normalY * normalY + normalZ * normalZ);
        return length <= 1.0E-6F ? null
                : new float[] {normalX / length, normalY / length, normalZ / length};
    }

    private static Direction closestDirection(float[] normal) {
        Direction closest = Direction.UP;
        float closestDot = -Float.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            float dot = normal[0] * direction.getStepX()
                    + normal[1] * direction.getStepY()
                    + normal[2] * direction.getStepZ();
            if (dot > closestDot) {
                closestDot = dot;
                closest = direction;
            }
        }
        return closest;
    }

    private static int packedNormal(float[] normal) {
        int x = ((byte) Math.round(normal[0] * 127.0F)) & 0xFF;
        int y = ((byte) Math.round(normal[1] * 127.0F)) & 0xFF;
        int z = ((byte) Math.round(normal[2] * 127.0F)) & 0xFF;
        return x | y << 8 | z << 16;
    }

    private static double quadSurfaceVolume(
            int[] vertices, int stride, float centerX, float centerY, float centerZ) {
        return triangleSurfaceVolume(
                vertices, stride, 0, 1, 2, centerX, centerY, centerZ)
                + triangleSurfaceVolume(
                        vertices, stride, 0, 2, 3, centerX, centerY, centerZ);
    }

    private static double triangleSurfaceVolume(
            int[] vertices, int stride, int first, int second, int third,
            float centerX, float centerY, float centerZ) {
        float ax = Float.intBitsToFloat(vertices[first * stride]) - centerX;
        float ay = Float.intBitsToFloat(vertices[first * stride + 1]) - centerY;
        float az = Float.intBitsToFloat(vertices[first * stride + 2]) - centerZ;
        float bx = Float.intBitsToFloat(vertices[second * stride]) - centerX;
        float by = Float.intBitsToFloat(vertices[second * stride + 1]) - centerY;
        float bz = Float.intBitsToFloat(vertices[second * stride + 2]) - centerZ;
        float cx = Float.intBitsToFloat(vertices[third * stride]) - centerX;
        float cy = Float.intBitsToFloat(vertices[third * stride + 1]) - centerY;
        float cz = Float.intBitsToFloat(vertices[third * stride + 2]) - centerZ;
        float crossX = by * cz - bz * cy;
        float crossY = bz * cx - bx * cz;
        float crossZ = bx * cy - by * cx;
        return Math.abs(ax * crossX + ay * crossY + az * crossZ) / 6.0D;
    }

    private static float[] normalized(float[] vector) {
        float length = (float) Math.sqrt(normalLengthSquared(vector));
        return length <= 1.0E-8F ? new float[] {0.0F, 0.0F, 0.0F}
                : new float[] {vector[0] / length, vector[1] / length, vector[2] / length};
    }

    private static float normalLengthSquared(float[] vector) {
        return vector.length < 3 ? 0.0F
                : vector[0] * vector[0]
                        + vector[1] * vector[1]
                        + vector[2] * vector[2];
    }

    static float compressedModelCoordinate(
            float coordinate, float minimum, float factor) {
        return (coordinate - minimum) * factor;
    }

    static boolean requiresModelTransform(
            float factor, Direction facing, float sourceMinimumY) {
        return factor < 0.9999F || facing != Direction.UP
                || Math.abs(sourceMinimumY) > 1.0E-4F;
    }
}
