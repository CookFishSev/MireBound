package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import org.junit.jupiter.api.Test;

class ArmorMudPaintPlanTest {
    @Test
    void diffusionKeepsCoreAndNeverTravelsBeyondOnePhysicalPixel() {
        MudBodyPart part = MudBodyPart.BODY;
        MudSurface surface = MudSurface.FRONT;
        int row = 5;
        int column = 3;
        int core = MudSurfaceLayout.cellIndex(part, surface, row, column);
        ArmorMudData.Builder builder = ArmorMudData.EMPTY.toBuilder();
        builder.mark(core, 0.83F, SinkingMedium.MUD);

        ArmorMudPaintPlan plan = ArmorMudPaintPlan.build(builder.build(), part);

        assertEquals(0.83F, plan.coverage(core), 1.0F / 255.0F);
        int painted = 0;
        for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
            if (MudSurfaceLayout.part(cell) != part || plan.coverage(cell) <= 0.001F || cell == core) {
                continue;
            }
            painted++;
            assertEquals(surface, MudSurfaceLayout.surface(cell));
            int distance = Math.abs(MudSurfaceLayout.row(cell) - row)
                    + Math.abs(MudSurfaceLayout.column(cell) - column);
            assertEquals(1, distance, "fringe must be a direct neighbor of the core");
        }
        assertTrue(painted > 0, "the deterministic pattern should create a visible fringe");
    }

    @Test
    void edgeDiffusionCanCrossOntoTheAdjacentCubeFace() {
        MudBodyPart part = MudBodyPart.HEAD;
        boolean foundCrossFaceFringe = false;
        for (MudSurface surface : MudSurface.values()) {
            MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
            for (int row = 0; row < face.height() && !foundCrossFaceFringe; row++) {
                for (int column = 0; column < face.width() && !foundCrossFaceFringe; column++) {
                    if (row != 0 && row != face.height() - 1 && column != 0 && column != face.width() - 1) {
                        continue;
                    }
                    int core = MudSurfaceLayout.cellIndex(part, surface, row, column);
                    ArmorMudData.Builder builder = ArmorMudData.EMPTY.toBuilder();
                    builder.mark(core, 1.0F, SinkingMedium.MUD);
                    ArmorMudPaintPlan plan = ArmorMudPaintPlan.build(builder.build(), part);
                    for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
                        if (MudSurfaceLayout.part(cell) == part
                                && MudSurfaceLayout.surface(cell) != surface
                                && plan.coverage(cell) > 0.001F) {
                            foundCrossFaceFringe = true;
                            break;
                        }
                    }
                }
            }
        }
        assertTrue(foundCrossFaceFringe, "at least one deterministic edge sample should bridge cube faces");
    }
}
