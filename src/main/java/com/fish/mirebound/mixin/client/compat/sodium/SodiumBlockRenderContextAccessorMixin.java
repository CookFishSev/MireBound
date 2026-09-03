package com.fish.mirebound.mixin.client.compat.sodium;

import com.fish.mirebound.client.compat.sodium.SodiumBlockRenderContextAccess;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Reads the render slice used by Sodium's chunk worker. */
@Pseudo
@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public abstract class SodiumBlockRenderContextAccessorMixin
        implements SodiumBlockRenderContextAccess {
    @Shadow
    protected BlockAndTintGetter level;

    @Override
    public BlockAndTintGetter mirebound$getLevel() {
        return level;
    }
}
