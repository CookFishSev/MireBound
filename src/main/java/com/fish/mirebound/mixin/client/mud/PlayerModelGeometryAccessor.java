package com.fish.mirebound.mixin.client.mud;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerModel.class)
public interface PlayerModelGeometryAccessor {
    @Accessor("cloak")
    ModelPart mirebound$getCloak();

    @Accessor("ear")
    ModelPart mirebound$getEar();
}
