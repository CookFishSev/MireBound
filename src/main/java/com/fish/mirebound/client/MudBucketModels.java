package com.fish.mirebound.client;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.container.MudContainerRules;
import com.fish.mirebound.mud.container.MudVolumeData;
import com.fish.mirebound.registry.ModDataComponents;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

/** Selects the visual fill of the shared finite-volume mud bucket item. */
public final class MudBucketModels {
    private static final ModelResourceLocation BUCKET_MODEL =
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "mud_bucket"));

    private MudBucketModels() {
    }

    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        for (SinkingMedium medium : SinkingMedium.values()) {
            if (MudContainerRules.isBucketable(medium)) {
                event.register(mediumModel(medium));
            }
        }
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel base = event.getModels().get(BUCKET_MODEL);
        if (base == null) {
            return;
        }
        Map<SinkingMedium, BakedModel> mediumModels =
                new EnumMap<>(SinkingMedium.class);
        for (SinkingMedium medium : SinkingMedium.values()) {
            if (!MudContainerRules.isBucketable(medium)) {
                continue;
            }
            BakedModel model = event.getModels().get(mediumModel(medium));
            if (model != null) {
                mediumModels.put(medium, model);
            }
        }
        event.getModels().put(BUCKET_MODEL,
                new MudBucketBakedModel(base, mediumModels));
    }

    private static ModelResourceLocation mediumModel(SinkingMedium medium) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                Mirebound.MOD_ID,
                "item/mud_bucket/" + medium.serializedName()));
    }

    private static final class MudBucketBakedModel
            extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        private MudBucketBakedModel(BakedModel originalModel,
                Map<SinkingMedium, BakedModel> mediumModels) {
            super(originalModel);
            this.overrides = new MudBucketOverrides(
                    originalModel.getOverrides(), Map.copyOf(mediumModels));
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }

    private static final class MudBucketOverrides extends ItemOverrides {
        private final ItemOverrides nested;
        private final Map<SinkingMedium, BakedModel> mediumModels;

        private MudBucketOverrides(ItemOverrides nested,
                Map<SinkingMedium, BakedModel> mediumModels) {
            this.nested = nested;
            this.mediumModels = mediumModels;
        }

        @Override
        public BakedModel resolve(BakedModel originalModel, ItemStack stack,
                @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            BakedModel nestedModel = nested.resolve(
                    originalModel, stack, level, entity, seed);
            if (nestedModel != originalModel) {
                return nestedModel;
            }
            MudVolumeData data = stack.get(ModDataComponents.MUD_VOLUME.get());
            if (data == null || !MudContainerRules.isBucketable(data.medium())) {
                return originalModel;
            }
            return mediumModels.getOrDefault(data.medium(), originalModel);
        }
    }
}
