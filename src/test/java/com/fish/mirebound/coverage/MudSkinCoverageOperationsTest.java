package com.fish.mirebound.coverage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import org.junit.jupiter.api.Test;

class MudSkinCoverageOperationsTest {
    @Test
    void edgeBlendingMustNotBypassEntryForSolesOrLowestSideRow() {
        for (MudBodyPart part : new MudBodyPart[] {MudBodyPart.LEFT_LEG, MudBodyPart.RIGHT_LEG}) {
            for (MudSurface surface : MudSurface.values()) {
                var face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    for (int column = 0; column < face.width(); column++) {
                        int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                        if (surface == MudSurface.BOTTOM || face.vertical() && row == 0) {
                            assertTrue(MudSkinCoverageOperations.isFootCell(cell));
                        } else {
                            assertFalse(MudSkinCoverageOperations.isFootCell(cell));
                        }
                    }
                }
            }
        }
    }
}
