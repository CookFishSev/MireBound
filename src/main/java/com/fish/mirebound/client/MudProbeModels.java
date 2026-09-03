package com.fish.mirebound.client;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Keeps the endpoint probe grip separate from the ordinary centered grip. */
final class MudProbeModels {
    private static final float GRIP_PIVOT = -27.0F / 64.0F;
    private static final float PROBE_MODEL_SCALE = 1.05F;
    private static final float FORWARD_GRIP_CORRECTION = 0.28F;
    private static final float UPWARD_GRIP_CORRECTION = 0.22F;
    private static final Quaternionf ARM_ALIGNMENT = armAlignment();
    private static final Vector3f GRIP_CORRECTION = gripCorrection();
    private static final ModelResourceLocation ITEM_MODEL =
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "mud_probe"));
    private static final ModelResourceLocation PROBING_MODEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "item/mud_probe_probing"));

    private MudProbeModels() {
    }

    static void registerAdditional(ModelEvent.RegisterAdditional event) {
        event.register(PROBING_MODEL);
    }

    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel base = event.getModels().get(ITEM_MODEL);
        BakedModel probing = event.getModels().get(PROBING_MODEL);
        if (base != null && probing != null) {
            event.getModels().put(ITEM_MODEL, new ProbeBakedModel(
                    base, new ProbePoseBakedModel(probing)));
        }
    }

    private static Quaternionf armAlignment() {
        float radians = (float) (Math.PI / 180.0D);
        Quaternionf current = new Quaternionf().rotationXYZ(
                0.0F, -90.0F * radians, 55.0F * radians);
        Quaternionf aligned = new Quaternionf().rotationXYZ(
                0.0F, -90.0F * radians, 135.0F * radians);
        Quaternionf longAxisRoll = new Quaternionf().rotationAxis(
                90.0F * radians,
                (float) (1.0D / Math.sqrt(2.0D)),
                (float) (1.0D / Math.sqrt(2.0D)),
                0.0F);
        return current.conjugate().mul(aligned).mul(longAxisRoll).normalize();
    }

    private static Vector3f gripCorrection() {
        float radians = (float) (Math.PI / 180.0D);
        Quaternionf handToModel = new Quaternionf()
                .rotationX(-90.0F * radians)
                .rotateY(180.0F * radians)
                .mul(new Quaternionf().rotationXYZ(
                        0.0F, -90.0F * radians, 55.0F * radians))
                .mul(new Quaternionf(ARM_ALIGNMENT));
        Vector3f correction = new Vector3f(
                0.0F, FORWARD_GRIP_CORRECTION, -UPWARD_GRIP_CORRECTION);
        return handToModel.conjugate().transform(correction).div(PROBE_MODEL_SCALE);
    }

    private static final class ProbeBakedModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        private ProbeBakedModel(BakedModel originalModel, BakedModel probingModel) {
            super(originalModel);
            overrides = new ProbeOverrides(originalModel.getOverrides(), probingModel);
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }

    private static final class ProbePoseBakedModel extends BakedModelWrapper<BakedModel> {
        private ProbePoseBakedModel(BakedModel originalModel) {
            super(originalModel);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context,
                PoseStack poseStack, boolean leftHand) {
            BakedModel transformed = originalModel.applyTransform(
                    context, poseStack, leftHand);
            if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
                poseStack.translate(GRIP_PIVOT, GRIP_PIVOT, 0.0F);
                poseStack.mulPose(new Quaternionf(ARM_ALIGNMENT));
                poseStack.translate(-GRIP_PIVOT, -GRIP_PIVOT, 0.0F);
                poseStack.translate(
                        GRIP_CORRECTION.x, GRIP_CORRECTION.y, GRIP_CORRECTION.z);
            }
            return transformed;
        }
    }

    private static final class ProbeOverrides extends ItemOverrides {
        private final ItemOverrides nested;
        private final BakedModel probingModel;

        private ProbeOverrides(ItemOverrides nested, BakedModel probingModel) {
            this.nested = nested;
            this.probingModel = probingModel;
        }

        @Override
        public BakedModel resolve(BakedModel originalModel, ItemStack stack,
                @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            BakedModel nestedModel = nested.resolve(
                    originalModel, stack, level, entity, seed);
            if (nestedModel != originalModel) {
                return nestedModel;
            }
            if (entity != null
                    && entity.isUsingItem()
                    && entity.getUseItem() == stack
                    && stack.getItem() == ModBlocks.MUD_PROBE.get()) {
                return probingModel;
            }
            return originalModel;
        }
    }
}
