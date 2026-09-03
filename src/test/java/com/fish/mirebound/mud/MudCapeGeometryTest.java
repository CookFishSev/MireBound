package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudCapeGeometryTest {
    private static final double EPSILON = 1.0E-7D;

    @Test
    void canonicalGridFollowsVanillaCapeAxesWithoutUvMirroring() {
        MudCapeGeometry.CapePose pose = new MudCapeGeometry.CapePose(
                6.0F, 0.0F, 180.0F, 0.0F, 0.0F, 0.0F);
        MudCapeGeometry.CapeBasis basis = MudCapeGeometry.basis(Vec3.ZERO, 1.0D / 16.0D, pose);
        Vec3 firstColumn = MudCapeGeometry.centerPoint(basis, 0, 0);
        Vec3 lastColumn = MudCapeGeometry.centerPoint(basis, 0, 9);
        Vec3 firstRow = MudCapeGeometry.centerPoint(basis, 0, 4);
        Vec3 lastRow = MudCapeGeometry.centerPoint(basis, 15, 4);

        assertEquals(9.0D / 16.0D,
                lastColumn.subtract(firstColumn).dot(basis.side()), EPSILON);
        assertEquals(15.0D / 16.0D,
                lastRow.subtract(firstRow).dot(basis.down()), EPSILON);
        assertTrue(lastRow.y < firstRow.y);
        assertTrue(basis.normal().dot(new Vec3(0.0D, 0.0D, -1.0D)) > 0.9D);
    }

    @Test
    void raisedCapeMovesLowerRowsAwayFromDefaultHangingPosition() {
        MudCapeGeometry.CapeBasis hanging = MudCapeGeometry.basis(Vec3.ZERO, 1.0D / 16.0D,
                new MudCapeGeometry.CapePose(6.0F, 0.0F, 180.0F, 0.0F, 0.0F, 0.0F));
        MudCapeGeometry.CapeBasis raised = MudCapeGeometry.basis(Vec3.ZERO, 1.0D / 16.0D,
                new MudCapeGeometry.CapePose(70.0F, 0.0F, 180.0F, 0.0F, 0.0F, 0.0F));
        Vec3 hangingBottom = MudCapeGeometry.centerPoint(hanging, 15, 4);
        Vec3 raisedBottom = MudCapeGeometry.centerPoint(raised, 15, 4);

        assertTrue(raisedBottom.y > hangingBottom.y + 0.25D);
        assertTrue(raisedBottom.distanceTo(hangingBottom) > 0.75D);
    }
}
