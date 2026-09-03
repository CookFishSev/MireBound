package com.fish.mirebound.adaptive;

import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pure collision composition kept independent from Minecraft block registration. */
final class AdaptiveMudCollision {
    private AdaptiveMudCollision() {
    }

    static VoxelShape synchronize(
            VoxelShape mudCollision, VoxelShape sourceCollision, boolean entitySpecific) {
        if (!entitySpecific) {
            return sourceCollision;
        }
        if (mudCollision.isEmpty() || sourceCollision.isEmpty()) {
            return Shapes.empty();
        }
        return Shapes.joinUnoptimized(mudCollision, sourceCollision, BooleanOp.AND);
    }
}
