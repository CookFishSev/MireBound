package com.fish.mirebound.mixin.client.tentacle;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("speedOld")
    float mirebound$getSpeedOld();

    @Accessor("speedOld")
    void mirebound$setSpeedOld(float value);

    @Accessor("speed")
    float mirebound$getSpeed();

    @Accessor("speed")
    void mirebound$setSpeed(float value);

    @Accessor("position")
    float mirebound$getPosition();

    @Accessor("position")
    void mirebound$setPosition(float value);
}
