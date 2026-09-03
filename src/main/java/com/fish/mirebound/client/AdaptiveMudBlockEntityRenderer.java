package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.AdaptiveMudBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Renders source blocks such as chests whose visible model is owned by a BER. */
public final class AdaptiveMudBlockEntityRenderer
        implements BlockEntityRenderer<AdaptiveMudBlockEntity> {
    private final BlockEntityRenderDispatcher dispatcher;

    public AdaptiveMudBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        dispatcher = context.getBlockEntityRenderDispatcher();
    }

    @Override
    public void render(AdaptiveMudBlockEntity blockEntity, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        BlockEntity source = blockEntity.virtualSourceBlockEntity();
        if (source != null) {
            dispatcher.render(source, partialTick, poseStack, buffers);
        }
    }
}
