package com.fish.mirebound.client;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockVariant;
import com.fish.mirebound.mud.MudShapeProfile;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

/** Reuses one baked cube per medium and caches its sixteen possible local heights. */
public final class MudVariantModels {
    private static final int CONNECTED_GRID_SIZE = 3;
    private static final int CONNECTED_VARIANT_COUNT =
            CONNECTED_GRID_SIZE * CONNECTED_GRID_SIZE;
    private static final ModelProperty<BlockPos> BLOCK_POS =
            new ModelProperty<>();
    private static final ModelProperty<Integer> HIDDEN_FACES =
            new ModelProperty<>();
    private static final EnumSet<SinkingMedium> CONNECTED_MEDIA =
            EnumSet.of(
                    SinkingMedium.MUD,
                    SinkingMedium.ASH_QUICKSAND,
                    SinkingMedium.SOUL_SILT,
                    SinkingMedium.GEL_CLAY,
                    SinkingMedium.LIME_MUD,
                    SinkingMedium.PEAT_BOG,
                    SinkingMedium.TAR,
                    SinkingMedium.LIVING_SLIME,
                    SinkingMedium.INSECT_MOUND,
                    SinkingMedium.END_SILT,
                    SinkingMedium.SCULK_MIRE,
                    SinkingMedium.GRAVEL_SILT,
                    SinkingMedium.FUNGAL_MIRE,
                    SinkingMedium.STONE_CLAY,
                    SinkingMedium.PALE_MIRE,
                    SinkingMedium.PEAT_SILT,
                    SinkingMedium.TENDER_FLESH,
                    SinkingMedium.MIRE);
    /** Marker consumed only by the optional Sable directional-lighting mixin. */
    public interface SableDynamicUpQuad {
    }

    private MudVariantModels() {
    }

    public static ModelData fallingModelData(FallingBlockEntity entity) {
        if (!(entity.getBlockState().getBlock() instanceof MudBlock)) {
            return ModelData.EMPTY;
        }
        return ModelData.EMPTY.derive()
                .with(BLOCK_POS, entity.getStartPos().immutable())
                .build();
    }

    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        for (SinkingMedium medium : SinkingMedium.values()) {
            if (MudShapeProfile.supportsSpecial(medium)) {
                event.register(specialLocation(medium));
            }
            if (CONNECTED_MEDIA.contains(medium)) {
                for (int variant = 0;
                        variant < CONNECTED_VARIANT_COUNT;
                        variant++) {
                    event.register(connectedLocation(medium, variant));
                }
            }
        }
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<SinkingMedium, BakedModel> specialModels = new EnumMap<>(SinkingMedium.class);
        Map<SinkingMedium, BakedModel[]> connectedModels =
                new EnumMap<>(SinkingMedium.class);
        for (SinkingMedium medium : SinkingMedium.values()) {
            if (MudShapeProfile.supportsSpecial(medium)) {
                BakedModel special = event.getModels().get(specialLocation(medium));
                if (special != null) {
                    specialModels.put(medium, special);
                }
            }
            if (CONNECTED_MEDIA.contains(medium)) {
                BakedModel[] variants =
                        new BakedModel[CONNECTED_VARIANT_COUNT];
                boolean complete = true;
                for (int variant = 0;
                        variant < CONNECTED_VARIANT_COUNT;
                        variant++) {
                    variants[variant] = event.getModels().get(
                            connectedLocation(medium, variant));
                    complete &= variants[variant] != null;
                }
                if (complete) {
                    connectedModels.put(medium, variants);
                }
            }
        }

        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof MudBlock mudBlock)) {
                continue;
            }
            BakedModel base = event.getModels().get(
                    BlockModelShaper.stateToModelLocation(block.defaultBlockState()));
            if (base == null) {
                continue;
            }
            MudVariantBakedModel wrapper = new MudVariantBakedModel(
                    base,
                    specialModels.get(mudBlock.medium()),
                    connectedModels.get(mudBlock.medium()));
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                event.getModels().put(BlockModelShaper.stateToModelLocation(state), wrapper);
            }
        }
    }

    private static ModelResourceLocation specialLocation(SinkingMedium medium) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                Mirebound.MOD_ID, "block/special/" + medium.serializedName()));
    }

    private static ModelResourceLocation connectedLocation(
            SinkingMedium medium, int variant) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(
                        Mirebound.MOD_ID,
                        "block/connected/"
                                + medium.serializedName()
                                + "_"
                                + variant));
    }

    static int connectedTileIndex(BlockPos pos, Direction face) {
        int u;
        int v;
        switch (face.getAxis()) {
            case Y -> {
                u = pos.getX();
                v = pos.getZ();
            }
            case Z -> {
                u = pos.getX();
                v = pos.getY();
            }
            case X -> {
                u = pos.getZ();
                v = pos.getY();
            }
            default -> throw new IllegalStateException(
                    "Unhandled face axis " + face.getAxis());
        }
        return Math.floorMod(v, CONNECTED_GRID_SIZE)
                * CONNECTED_GRID_SIZE
                + Math.floorMod(u, CONNECTED_GRID_SIZE);
    }

    static boolean shouldCullCoveredMudInterface(
            SinkingMedium current, SinkingMedium neighbor) {
        if (current == neighbor) {
            return true;
        }
        return current.opaqueBlock() && neighbor.opaqueBlock();
    }

    static boolean coversCurrentSharedFace(
            VoxelShape currentShape, VoxelShape neighborShape, Direction face) {
        return Shapes.blockOccudes(currentShape, neighborShape, face);
    }

    private static final class MudVariantBakedModel
            extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel specialModel;
        private final BakedModel[] connectedModels;
        private final List<BakedQuad>[][][] heightQuads;
        private final List<BakedQuad>[] specialQuads;

        @SuppressWarnings("unchecked")
        private MudVariantBakedModel(
                BakedModel originalModel,
                @Nullable BakedModel specialModel,
                @Nullable BakedModel[] connectedModels) {
            super(originalModel);
            this.specialModel = specialModel;
            this.connectedModels =
                    connectedModels == null
                            ? null
                            : Arrays.copyOf(
                                    connectedModels,
                                    connectedModels.length);
            int modelCount = this.connectedModels == null
                    ? 1
                    : this.connectedModels.length;
            this.heightQuads =
                    new List[modelCount][17][Direction.values().length];
            this.specialQuads = new List[Direction.values().length];
            for (int modelIndex = 0;
                    modelIndex < modelCount;
                    modelIndex++) {
                BakedModel model = this.connectedModels == null
                        ? originalModel
                        : this.connectedModels[modelIndex];
                List<BakedQuad> cubeQuads = collectAllQuads(model);
                for (int height = 1; height < 16; height++) {
                    List<BakedQuad> scaled =
                            new ArrayList<>(cubeQuads.size());
                    for (BakedQuad quad : cubeQuads) {
                        scaled.add(scaleHeight(
                                quad, height / 16.0F));
                    }
                    for (Direction facing : Direction.values()) {
                        heightQuads[modelIndex][height][facing.ordinal()] =
                                orientQuads(scaled, facing);
                    }
                }
            }
            if (specialModel != null) {
                List<BakedQuad> special = collectAllQuads(specialModel);
                for (Direction facing : Direction.values()) {
                    specialQuads[facing.ordinal()] = orientQuads(special, facing);
                }
            }
        }

        @Override
        public ModelData getModelData(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                ModelData modelData) {
            ModelData originalData = originalModel.getModelData(
                    level, pos, state, modelData);
            return originalData.derive()
                    .with(BLOCK_POS, pos.immutable())
                    .with(HIDDEN_FACES, hiddenFaceMask(level, pos))
                    .build();
        }

        @Override
        public TriState useAmbientOcclusion(
                BlockState state, ModelData modelData, RenderType renderType) {
            if (state != null
                    && state.getBlock() instanceof MudBlock mudBlock
                    && mudBlock.medium().opaqueBlock()) {
                return TriState.TRUE;
            }
            return originalModel.useAmbientOcclusion(state, modelData, renderType);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData modelData, @Nullable RenderType renderType) {
            if (state == null
                    || !(state.getBlock() instanceof MudBlock mudBlock)) {
                return originalModel.getQuads(state, side, random, modelData, renderType);
            }
            int hiddenFaces = modelData.get(HIDDEN_FACES) == null
                    ? 0 : modelData.get(HIDDEN_FACES);
            if (side != null && (hiddenFaces & faceBit(side)) != 0) {
                return List.of();
            }
            MudBlockVariant variant = MudBlock.variant(state);
            if (variant == MudBlockVariant.SPECIAL && specialModel != null) {
                Direction facing = MudBlock.surfaceDirection(
                        state, mudBlock.medium());
                return side == null
                        ? filterHidden(specialQuads[facing.ordinal()], hiddenFaces)
                        : List.of();
            }
            boolean stackFilled =
                    state.hasProperty(MudBlock.STACK_FILLED)
                            && state.getValue(MudBlock.STACK_FILLED);
            if (stackFilled || variant != MudBlockVariant.HEIGHT) {
                if (modelData.get(BLOCK_POS) != null) {
                    return side == null
                            ? placedFullBlockQuads(
                                    state, random, modelData, renderType, hiddenFaces)
                            : List.of();
                }
                Direction textureFace = side == null
                        ? MudBlock.surfaceDirection(
                                state, mudBlock.medium())
                        : side;
                return filterHidden(connectedModel(modelData, textureFace)
                        .getQuads(
                                state,
                                side,
                                random,
                                modelData,
                                renderType), hiddenFaces);
            }
            int height = MudBlock.heightPixels(state, mudBlock.medium());
            if (height >= 16) {
                if (modelData.get(BLOCK_POS) != null) {
                    return side == null
                            ? placedFullBlockQuads(
                                    state, random, modelData, renderType, hiddenFaces)
                            : List.of();
                }
                Direction textureFace = side == null
                        ? MudBlock.surfaceDirection(
                                state, mudBlock.medium())
                        : side;
                return filterHidden(connectedModel(modelData, textureFace)
                        .getQuads(
                                state,
                                side,
                                random,
                                modelData,
                                renderType), hiddenFaces);
            }
            // Partial blocks keep all six faces unculled so a neighboring full block
            // cannot incorrectly hide the recessed top or exposed side strip.
            Direction facing = MudBlock.surfaceDirection(state, mudBlock.medium());
            int modelIndex = connectedModelIndex(modelData, facing);
            return side == null
                    ? filterHidden(heightQuads[modelIndex][height][facing.ordinal()], hiddenFaces)
                    : List.of();
        }

        private List<BakedQuad> placedFullBlockQuads(
                BlockState state,
                RandomSource random,
                ModelData modelData,
                @Nullable RenderType renderType,
                int hiddenFaces) {
            List<BakedQuad> visible = new ArrayList<>(24);
            Direction surface = MudBlock.surfaceDirection(
                    state, ((MudBlock) state.getBlock()).medium());
            visible.addAll(filterHidden(
                    connectedModel(modelData, surface).getQuads(
                            state, null, random, modelData, renderType),
                    hiddenFaces));
            for (Direction face : Direction.values()) {
                if ((hiddenFaces & faceBit(face)) != 0) {
                    continue;
                }
                visible.addAll(connectedModel(modelData, face).getQuads(
                        state, face, random, modelData, renderType));
            }
            return List.copyOf(visible);
        }

        private static List<BakedQuad> filterHidden(
                List<BakedQuad> quads, int hiddenFaces) {
            if (hiddenFaces == 0) {
                return quads;
            }
            List<BakedQuad> visible = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                if ((hiddenFaces & faceBit(quad.getDirection())) == 0) {
                    visible.add(quad);
                }
            }
            return List.copyOf(visible);
        }

        private BakedModel connectedModel(
                ModelData modelData, Direction face) {
            if (connectedModels == null) {
                return originalModel;
            }
            return connectedModels[
                    connectedModelIndex(modelData, face)];
        }

        private int connectedModelIndex(
                ModelData modelData, Direction face) {
            if (connectedModels == null) {
                return 0;
            }
            BlockPos pos = modelData.get(BLOCK_POS);
            return pos == null
                    ? 0
                    : connectedTileIndex(pos, face);
        }

        private static List<BakedQuad> collectAllQuads(BakedModel model) {
            List<BakedQuad> result = new ArrayList<>();
            result.addAll(model.getQuads(null, null, RandomSource.create(0L), ModelData.EMPTY, null));
            for (Direction direction : Direction.values()) {
                result.addAll(model.getQuads(
                        null, direction, RandomSource.create(0L), ModelData.EMPTY, null));
            }
            return List.copyOf(result);
        }

        private static int faceBit(Direction face) {
            return 1 << face.ordinal();
        }

        private static int hiddenFaceMask(
                BlockAndTintGetter level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof MudBlock currentMud)) {
                return 0;
            }
            VoxelShape currentShape = MudBlock.localShape(
                    level, pos, state, currentMud.medium());
            int mask = 0;
            for (Direction face : Direction.values()) {
                BlockPos neighborPos = pos.relative(face);
                BlockState neighbor = level.getBlockState(neighborPos);
                if (!(neighbor.getBlock() instanceof MudBlock mud)) {
                    continue;
                }
                if (!shouldCullCoveredMudInterface(
                        currentMud.medium(), mud.medium())) {
                    continue;
                }
                if (coversCurrentSharedFace(
                        currentShape,
                        MudBlock.localShape(
                                level, neighborPos, neighbor, mud.medium()),
                        face)) {
                    mask |= faceBit(face);
                }
            }
            return mask;
        }

        private static BakedQuad scaleHeight(BakedQuad source, float scale) {
            int[] vertices = Arrays.copyOf(source.getVertices(), source.getVertices().length);
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                float y = Float.intBitsToFloat(vertices[offset + 1]);
                vertices[offset + 1] = Float.floatToRawIntBits(y * scale);
                if (source.getDirection().getAxis().isHorizontal()) {
                    int uvOffset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                    float v = Float.intBitsToFloat(vertices[uvOffset + 1]);
                    float spriteV0 = source.getSprite().getV0();
                    float spriteV1 = source.getSprite().getV1();
                    float normalizedV = (v - spriteV0) / (spriteV1 - spriteV0);
                    float remappedV = 1.0F - (1.0F - normalizedV) * scale;
                    vertices[uvOffset + 1] = Float.floatToRawIntBits(
                            spriteV0 + remappedV * (spriteV1 - spriteV0));
                }
            }
            return new PartialHeightQuad(
                    vertices,
                    source.getTintIndex(),
                    source.getDirection(),
                    source.getSprite(),
                    source.isShade(),
                    source.hasAmbientOcclusion());
        }

        private static List<BakedQuad> orientQuads(
                List<BakedQuad> source, Direction facing) {
            if (facing == Direction.UP) {
                return List.copyOf(source);
            }
            List<BakedQuad> result = new ArrayList<>(source.size());
            for (BakedQuad quad : source) {
                int[] vertices = Arrays.copyOf(
                        quad.getVertices(), quad.getVertices().length);
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * IQuadTransformer.STRIDE
                            + IQuadTransformer.POSITION;
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
                result.add(new PartialHeightQuad(
                        vertices,
                        quad.getTintIndex(),
                        MudBlock.orientDirection(quad.getDirection(), facing),
                        quad.getSprite(),
                        quad.isShade(),
                        quad.hasAmbientOcclusion()));
            }
            return List.copyOf(result);
        }

        private static final class PartialHeightQuad extends BakedQuad
                implements SableDynamicUpQuad {
            private PartialHeightQuad(int[] vertices, int tintIndex, Direction direction,
                    net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                    boolean shade, boolean ambientOcclusion) {
                super(vertices, tintIndex, direction, sprite, shade, ambientOcclusion);
            }
        }
    }
}
