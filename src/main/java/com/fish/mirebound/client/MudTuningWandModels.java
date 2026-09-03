package com.fish.mirebound.client;

import com.fish.mirebound.Mirebound;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Registers the static wand body consumed by its built-in item renderer. */
final class MudTuningWandModels {
    static final ModelResourceLocation BODY = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "item/mud_tuning_wand/body"));
    static final ModelResourceLocation GUI_HEAD = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "item/mud_tuning_wand/gui_head"));
    private MudTuningWandModels() {
    }

    static void registerAdditional(ModelEvent.RegisterAdditional event) {
        event.register(BODY);
        event.register(GUI_HEAD);
    }
}
