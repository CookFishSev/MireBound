package com.fish.mirebound.mixin.client.tentacle;

import com.fish.mirebound.client.tentacle.TentacleGrabCamera;
import com.fish.mirebound.client.AssimilationSoulCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 900)
public abstract class CameraRagdollPositionMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void mirebound$followRagdollHead(BlockGetter level, Entity entity,
            boolean detached, boolean thirdPersonReverse, float partialTick,
            CallbackInfo callback) {
        TentacleGrabCamera.applyPosition((Camera) (Object) this, entity, partialTick);
        AssimilationSoulCamera.applyPosition((Camera) (Object) this, entity, partialTick);
    }
}
