package com.fish.mirebound.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

class AdaptiveMudBlockTest {
    @Test
    void shapeCompressionGroundsTheSourceModelsActualBase() {
        assertEquals(0.0D, AdaptiveMudDeformation.compressedCoordinate(
                0.25D, 0.25D, 0.5D), 1.0E-7D);
        assertEquals(0.25D, AdaptiveMudDeformation.compressedCoordinate(
                0.75D, 0.25D, 0.5D), 1.0E-7D);
    }

    @Test
    void raisedSourceCollisionIsMovedOntoTheLocalSupportSurface() {
        VoxelShape raised = Block.box(
                0.0D, 8.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        VoxelShape expected = Block.box(
                0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);

        VoxelShape result = AdaptiveMudDeformation.deformShape(
                raised, 0.5F, Direction.UP);

        assertTrue(sameShape(expected, result));
    }

    @Test
    void fullScaleRaisedSourceCollisionIsStillGrounded() {
        VoxelShape raised = Block.box(
                0.0D, 8.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        VoxelShape expected = Block.box(
                0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);

        VoxelShape result = AdaptiveMudDeformation.deformShape(
                raised, 1.0F, Direction.UP);

        assertTrue(sameShape(expected, result));
    }

    @Test
    void surfaceHeightFollowsTheActualCollisionColumn() {
        VoxelShape stepped = Shapes.or(
                Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
                Block.box(8.0D, 8.0D, 0.0D, 16.0D, 16.0D, 16.0D));

        assertEquals(0.5D,
                AdaptiveMudDeformation.topSurfaceAt(stepped, 0.25D, 0.5D),
                1.0E-7D);
        assertEquals(1.0D,
                AdaptiveMudDeformation.topSurfaceAt(stepped, 0.75D, 0.5D),
                1.0E-7D);
        assertTrue(Double.isNaN(
                AdaptiveMudDeformation.topSurfaceAt(stepped, 1.25D, 0.5D)));
    }

    @Test
    void farmlandHeightDoesNotFallBackToAFullProxyCube() {
        VoxelShape farmland = Block.box(
                0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);

        assertEquals(15.0D / 16.0D,
                AdaptiveMudDeformation.topSurfaceAt(farmland, 0.5D, 0.5D),
                1.0E-7D);
    }

    @Test
    void staticQueriesUseTheStoredSourceCollision() {
        VoxelShape source = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);

        VoxelShape result = AdaptiveMudCollision.synchronize(
                Shapes.empty(), source, false);

        assertTrue(sameShape(source, result));
    }

    @Test
    void sinkingPlayersRemainUnblockedByTheSourceCollision() {
        VoxelShape result = AdaptiveMudCollision.synchronize(
                Shapes.empty(), Shapes.block(), true);

        assertTrue(result.isEmpty());
    }

    @Test
    void entitySupportNeverExtendsOutsideTheStoredSourceCollision() {
        VoxelShape mudSupport = Block.box(
                0.0D, 4.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        VoxelShape source = Block.box(
                0.0D, 0.0D, 0.0D, 8.0D, 8.0D, 16.0D);
        VoxelShape expected = Block.box(
                0.0D, 4.0D, 0.0D, 8.0D, 8.0D, 16.0D);

        VoxelShape result = AdaptiveMudCollision.synchronize(
                mudSupport, source, true);

        assertTrue(sameShape(expected, result));
    }

    private static boolean sameShape(VoxelShape first, VoxelShape second) {
        return !Shapes.joinIsNotEmpty(first, second, BooleanOp.NOT_SAME);
    }
}
