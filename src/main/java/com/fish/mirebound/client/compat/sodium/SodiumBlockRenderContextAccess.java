package com.fish.mirebound.client.compat.sodium;

import net.minecraft.world.level.BlockAndTintGetter;

/** Exposes Sodium's render slice to the plant offset bridge. */
public interface SodiumBlockRenderContextAccess {
    BlockAndTintGetter mirebound$getLevel();
}
