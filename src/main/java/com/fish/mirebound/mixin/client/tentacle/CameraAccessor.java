package com.fish.mirebound.mixin.client.tentacle;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPosition")
    void mirebound$setPosition(Vec3 position);

    @Invoker("setRotation")
    void mirebound$setRotation(float yaw, float pitch, float roll);
}
