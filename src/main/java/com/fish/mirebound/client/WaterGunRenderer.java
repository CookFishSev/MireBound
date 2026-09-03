package com.fish.mirebound.client;

import com.fish.mirebound.water.WaterGunItem;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Renders the static pressure gun, transparent tank shell, and live stored-water level. */
final class WaterGunRenderer extends BlockEntityWithoutLevelRenderer {
    private static final Material WATER_STILL = new Material(
            InventoryMenu.BLOCK_ATLAS,
            ResourceLocation.withDefaultNamespace("block/water_still"));
    private static final float WATER_FILL_MIN_Y = 16.2F / 16.0F;
    private static final float WATER_FILL_MAX_Y = 22.8F / 16.0F;
    private static final float RECOIL_PIVOT_X = 8.0F / 16.0F;
    private static final float RECOIL_PIVOT_Y = 7.0F / 16.0F;
    private static final float RECOIL_PIVOT_Z = 8.0F / 16.0F;
    private static final float[][] WATER_SECTIONS = {
            section(5.4F, 10.6F, 17.3F, 21.7F, 4.2F, 5.8F),
            section(4.8F, 11.2F, 16.8F, 22.2F, 6.2F, 7.9F),
            section(4.15F, 11.85F, 16.2F, 22.8F, 8.2F, 20.4F),
            section(4.8F, 11.2F, 16.8F, 22.2F, 20.7F, 22.3F),
            section(5.4F, 10.6F, 17.3F, 21.7F, 22.6F, 24.0F)
    };

    WaterGunRenderer(Minecraft minecraft) {
        super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        poseStack.pushPose();
        applyFirstPersonAnimations(minecraft, displayContext, poseStack);
        WaterGunNozzleFocus.capture(stack, displayContext, poseStack);
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        BakedModel body = minecraft.getModelManager().getModel(WaterGunModels.BODY);
        renderModel(itemRenderer, body, stack, poseStack, buffers,
                packedLight, packedOverlay, stack.hasFoil());

        float fill = fillFraction(stack);
        if (fill > 0.0F) {
            VertexConsumer water = WATER_STILL.sprite().wrap(
                    buffers.getBuffer(Sheets.translucentCullBlockSheet()));
            renderWater(poseStack.last(), water, fill, packedLight, packedOverlay);
        }

        BakedModel glass = minecraft.getModelManager().getModel(WaterGunModels.TANK_GLASS);
        itemRenderer.renderModelLists(glass, stack, packedLight, packedOverlay,
                poseStack, buffers.getBuffer(Sheets.translucentCullBlockSheet()));
        poseStack.popPose();
    }

    private static void applyFirstPersonAnimations(Minecraft minecraft,
            ItemDisplayContext context, PoseStack poseStack) {
        if (!WaterGunNozzleFocus.isLocalMainHandContext(context)) {
            return;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        float refillDip = WaterGunStreamClientManager.refillDip(partialTick);
        if (refillDip > 0.001F) {
            poseStack.translate(0.0F, -0.13F * refillDip, 0.0F);
        }
        float recoil = WaterGunStreamClientManager.localRecoil(partialTick);
        if (recoil <= 0.001F) {
            return;
        }
        poseStack.translate(RECOIL_PIVOT_X, RECOIL_PIVOT_Y, RECOIL_PIVOT_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(
                WaterGunStreamClientManager.recoilDegrees() * recoil));
        poseStack.translate(-RECOIL_PIVOT_X, -RECOIL_PIVOT_Y, -RECOIL_PIVOT_Z);
    }

    static float fillFraction(ItemStack stack) {
        int capacity = WaterGunItem.displayCapacity();
        return Mth.clamp(WaterGunItem.water(stack) / (float) capacity, 0.0F, 1.0F);
    }

    private static void renderModel(ItemRenderer renderer, BakedModel model,
            ItemStack stack, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay, boolean foil) {
        VertexConsumer vertices = foil
                ? ItemRenderer.getFoilBufferDirect(
                        buffers, Sheets.cutoutBlockSheet(), true, true)
                : buffers.getBuffer(Sheets.cutoutBlockSheet());
        renderer.renderModelLists(
                model, stack, packedLight, packedOverlay, poseStack, vertices);
    }

    private static void renderWater(PoseStack.Pose pose, VertexConsumer vertices,
            float fill, int packedLight, int packedOverlay) {
        float waterLine = Mth.lerp(fill, WATER_FILL_MIN_Y, WATER_FILL_MAX_Y);
        for (float[] section : WATER_SECTIONS) {
            float top = Math.min(waterLine, section[3]);
            if (top > section[2]) {
                renderWaterSection(pose, vertices, section, top,
                        packedLight, packedOverlay);
            }
        }
    }

    private static void renderWaterSection(PoseStack.Pose pose,
            VertexConsumer vertices, float[] section, float top,
            int packedLight, int packedOverlay) {
        float minX = section[0];
        float maxX = section[1];
        float minY = section[2];
        float minZ = section[4];
        float maxZ = section[5];
        quad(pose, vertices,
                minX, minY, minZ, minX, top, minZ,
                maxX, top, minZ, maxX, minY, minZ,
                0.0F, 0.0F, -1.0F,
                172, 214, 246, 208, packedLight, packedOverlay);
        quad(pose, vertices,
                minX, minY, maxZ, maxX, minY, maxZ,
                maxX, top, maxZ, minX, top, maxZ,
                0.0F, 0.0F, 1.0F,
                172, 214, 246, 208, packedLight, packedOverlay);
        quad(pose, vertices,
                minX, minY, minZ, minX, minY, maxZ,
                minX, top, maxZ, minX, top, minZ,
                -1.0F, 0.0F, 0.0F,
                132, 190, 232, 205, packedLight, packedOverlay);
        quad(pose, vertices,
                maxX, minY, minZ, maxX, top, minZ,
                maxX, top, maxZ, maxX, minY, maxZ,
                1.0F, 0.0F, 0.0F,
                132, 190, 232, 205, packedLight, packedOverlay);
        quad(pose, vertices,
                minX, minY, minZ, maxX, minY, minZ,
                maxX, minY, maxZ, minX, minY, maxZ,
                0.0F, -1.0F, 0.0F,
                110, 171, 218, 205, packedLight, packedOverlay);
        quad(pose, vertices,
                minX, top, minZ, minX, top, maxZ,
                maxX, top, maxZ, maxX, top, minZ,
                0.0F, 1.0F, 0.0F,
                205, 238, 255, 230, packedLight, packedOverlay);
    }

    private static float[] section(float minX, float maxX,
            float minY, float maxY, float minZ, float maxZ) {
        return new float[] {
                minX / 16.0F, maxX / 16.0F,
                minY / 16.0F, maxY / 16.0F,
                minZ / 16.0F, maxZ / 16.0F
        };
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz,
            float normalX, float normalY, float normalZ,
            int red, int green, int blue, int alpha,
            int packedLight, int packedOverlay) {
        vertex(pose, vertices, ax, ay, az, 0.0F, 1.0F,
                normalX, normalY, normalZ, red, green, blue, alpha,
                packedLight, packedOverlay);
        vertex(pose, vertices, bx, by, bz, 0.0F, 0.0F,
                normalX, normalY, normalZ, red, green, blue, alpha,
                packedLight, packedOverlay);
        vertex(pose, vertices, cx, cy, cz, 1.0F, 0.0F,
                normalX, normalY, normalZ, red, green, blue, alpha,
                packedLight, packedOverlay);
        vertex(pose, vertices, dx, dy, dz, 1.0F, 1.0F,
                normalX, normalY, normalZ, red, green, blue, alpha,
                packedLight, packedOverlay);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            float x, float y, float z, float u, float v,
            float normalX, float normalY, float normalZ,
            int red, int green, int blue, int alpha,
            int packedLight, int packedOverlay) {
        vertices.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(packedOverlay == 0
                        ? OverlayTexture.NO_OVERLAY : packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
