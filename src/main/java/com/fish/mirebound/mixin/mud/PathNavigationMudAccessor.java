package com.fish.mirebound.mixin.mud;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNavigation.class)
public interface PathNavigationMudAccessor {
    @Accessor("timeLastRecompute")
    void mirebound$setLastRecompute(long value);
}
