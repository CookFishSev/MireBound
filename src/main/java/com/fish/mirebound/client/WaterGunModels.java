package com.fish.mirebound.client;

import com.fish.mirebound.Mirebound;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Registers the static pressure-gun shell rendered around its dynamic water volume. */
final class WaterGunModels {
    static final ModelResourceLocation BODY = standalone("item/water_gun/body");
    static final ModelResourceLocation TANK_GLASS = standalone("item/water_gun/tank_glass");

    private WaterGunModels() {
    }

    static void registerAdditional(ModelEvent.RegisterAdditional event) {
        event.register(BODY);
        event.register(TANK_GLASS);
    }

    private static ModelResourceLocation standalone(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, path));
    }
}
