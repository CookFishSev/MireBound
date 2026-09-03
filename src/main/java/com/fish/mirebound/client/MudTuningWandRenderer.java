package com.fish.mirebound.client;

import com.fish.mirebound.client.tuning.MudTuningClientState;
import com.fish.mirebound.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Renders the fixed mirrored cage and the animated medium-textured core. */
final class MudTuningWandRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float FIRST_PERSON_IDLE_TILT = 30.5F;
    private static final float CORE_X = 8.0F / 16.0F;
    private static final float CORE_Y = 28.05F / 16.0F;
    private static final float CORE_Z = 8.0F / 16.0F;
    private static final float CORE_HALF_SIZE = 1.55F / 16.0F;
    private static final float GUI_HEAD_CENTER_OFFSET = -15.7F / 16.0F;

    private MudTuningWandRenderer(Minecraft minecraft) {
        super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    static void register(RegisterClientExtensionsEvent event) {
        MudTuningWandRenderer renderer = new MudTuningWandRenderer(Minecraft.getInstance());
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        }, ModBlocks.MUD_TUNING_WAND.get());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        poseStack.pushPose();
        if (displayContext.firstPerson()) {
            float tilt = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    ? FIRST_PERSON_IDLE_TILT : -FIRST_PERSON_IDLE_TILT;
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(tilt));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        MudTuningWandClientEffects.AimView aim =
                MudTuningWandClientEffects.activeAim(stack, displayContext, partialTick);
        if (aim != null) {
            MudTuningWandCoreFocus.applyAim(
                    poseStack, displayContext, aim.target(), aim.amount());
        }

        boolean gui = displayContext == ItemDisplayContext.GUI;
        if (gui) {
            poseStack.translate(0.0F, GUI_HEAD_CENTER_OFFSET, 0.0F);
        }
        float opening = gui ? 0.0F
                : MudTuningWandClientEffects.openingAmount(
                        stack, displayContext, partialTick);
        BakedModel body = minecraft.getModelManager().getModel(
                gui ? MudTuningWandModels.GUI_HEAD : MudTuningWandModels.BODY);
        renderModel(itemRenderer, body, stack, poseStack, buffers,
                packedLight, packedOverlay, stack.hasFoil());
        MudTuningWandGeometryRenderer.renderFixedHead(
                poseStack, buffers, packedLight, packedOverlay, opening);

        double time = minecraft.level == null
                ? Util.getMillis() / 50.0D
                : minecraft.level.getGameTime() + partialTick;
        boolean completeSelection = MudTuningClientState.hasSecond();
        boolean awaitingSecond = MudTuningClientState.hasFirst() && !completeSelection;
        MudTuningWandCoreMotion.Motion motion = MudTuningWandCoreMotion.sample(
                time, awaitingSecond, completeSelection);
        float coreY = CORE_Y + motion.bobPixels() / 16.0F;
        MudTuningWandCoreFocus.capture(
                stack, displayContext, poseStack, CORE_X, coreY, CORE_Z, partialTick);
        ResourceLocation coreTexture = MudTuningWandCoreTexture.texture(time);
        if (coreTexture != null) {
            poseStack.pushPose();
            poseStack.translate(CORE_X, coreY, CORE_Z);
            poseStack.mulPose(Axis.YP.rotationDegrees(motion.rotationDegrees()));
            poseStack.mulPose(Axis.XP.rotationDegrees(motion.pitchDegrees()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(motion.rollDegrees()));
            poseStack.scale(motion.pulse(), motion.pulse(), motion.pulse());
            VertexConsumer coreVertices = buffers.getBuffer(
                    RenderType.entityTranslucent(coreTexture));
            MudTuningWandGeometryRenderer.renderCoreCube(
                    poseStack.last(), coreVertices, CORE_HALF_SIZE,
                    LightTexture.FULL_BRIGHT, packedOverlay);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void renderModel(ItemRenderer itemRenderer, BakedModel model,
            ItemStack stack, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay, boolean foil) {
        VertexConsumer consumer = foil
                ? ItemRenderer.getFoilBufferDirect(
                        buffers, Sheets.cutoutBlockSheet(), true, true)
                : buffers.getBuffer(Sheets.cutoutBlockSheet());
        itemRenderer.renderModelLists(
                model, stack, packedLight, packedOverlay, poseStack, consumer);
    }
}
