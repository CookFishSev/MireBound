package com.fish.mirebound.mixin.client.compat.sodium;

import com.fish.mirebound.client.MudPlantSurfaceOffset;
import com.fish.mirebound.client.compat.sodium.SodiumBlockRenderContextAccess;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies plant surface attachment even when Sodium skips BlockState#getOffset. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
        remap = false)
public abstract class SodiumMudPlantOffsetMixin {
    @Shadow
    private Vector3f posOffset;

    @Inject(
            method = "renderModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/model/color/ColorProviderRegistry;"
                            + "getColorProvider(Lnet/minecraft/world/level/block/Block;)"
                            + "Lnet/caffeinemc/mods/sodium/client/model/color/ColorProvider;",
                    shift = At.Shift.BEFORE),
            require = 0,
            remap = false)
    private void mirebound$fitPlantToMudSurface(
            BakedModel model, BlockState state, BlockPos pos, BlockPos origin,
            CallbackInfo callback) {
        Vec3 original = new Vec3(
                posOffset.x - origin.getX(),
                posOffset.y - origin.getY(),
                posOffset.z - origin.getZ());
        Vec3 adjusted = MudPlantSurfaceOffset.adjust(
                ((SodiumBlockRenderContextAccess) this).mirebound$getLevel(),
                state, pos, original);
        posOffset.set(
                (float) (origin.getX() + adjusted.x),
                (float) (origin.getY() + adjusted.y),
                (float) (origin.getZ() + adjusted.z));
    }
}
