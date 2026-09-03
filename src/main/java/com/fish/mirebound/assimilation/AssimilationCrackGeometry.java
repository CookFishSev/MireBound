package com.fish.mirebound.assimilation;

import com.fish.mirebound.coverage.MudCoverageService;
import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.MudStateStore;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Maps shell hits to canonical body pixels and owns crack topology. */
final class AssimilationCrackGeometry {
    private AssimilationCrackGeometry() {
    }

    static int cellAtHit(ServerPlayer target, AssimilationState state, Vec3 hit) {
        double height = Math.max(0.5D, target.getBbHeight());
        double normalizedY = Mth.clamp(
                (hit.y - state.anchor.y) / height, 0.0D, 0.9999D);
        double yaw = Math.toRadians(state.frozenYaw);
        double dx = hit.x - state.anchor.x;
        double dz = hit.z - state.anchor.z;
        double localX = dx * Math.cos(yaw) + dz * Math.sin(yaw);
        double localZ = -dx * Math.sin(yaw) + dz * Math.cos(yaw);

        MudBodyPart part;
        double partBottom;
        double partTop;
        double partCenterX;
        double partWidth;
        double partDepth;
        if (normalizedY >= 0.74D) {
            part = MudBodyPart.HEAD;
            partBottom = 0.74D;
            partTop = 1.0D;
            partCenterX = 0.0D;
            partWidth = 0.50D;
            partDepth = 0.50D;
        } else if (normalizedY >= 0.36D) {
            partBottom = 0.36D;
            partTop = 0.74D;
            if (localX >= 0.22D) {
                part = MudBodyPart.LEFT_ARM;
                partCenterX = 0.27D;
                partWidth = 0.18D;
            } else if (localX <= -0.22D) {
                part = MudBodyPart.RIGHT_ARM;
                partCenterX = -0.27D;
                partWidth = 0.18D;
            } else {
                part = MudBodyPart.BODY;
                partCenterX = 0.0D;
                partWidth = 0.44D;
            }
            partDepth = 0.25D;
        } else {
            part = localX >= 0.0D
                    ? MudBodyPart.LEFT_LEG : MudBodyPart.RIGHT_LEG;
            partBottom = 0.0D;
            partTop = 0.36D;
            partCenterX = localX >= 0.0D ? 0.13D : -0.13D;
            partWidth = 0.25D;
            partDepth = 0.25D;
        }

        double partY = Mth.clamp(
                (normalizedY - partBottom)
                        / Math.max(0.001D, partTop - partBottom),
                0.0D, 1.0D);
        double partX = localX - partCenterX;
        double normalizedX = partX / Math.max(0.01D, partWidth * 0.5D);
        double normalizedZ = localZ / Math.max(0.01D, partDepth * 0.5D);
        MudSurface surface = hitSurface(partY, normalizedX, normalizedZ);
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        int row;
        int column;
        if (surface == MudSurface.TOP || surface == MudSurface.BOTTOM) {
            row = coordinateToCell(localZ, partDepth, face.height());
            column = coordinateToCell(partX, partWidth, face.width());
        } else {
            row = Mth.clamp((int) Math.floor(partY * face.height()),
                    0, face.height() - 1);
            double horizontal = surface == MudSurface.LEFT
                    || surface == MudSurface.RIGHT ? localZ : partX;
            double span = surface == MudSurface.LEFT
                    || surface == MudSurface.RIGHT ? partDepth : partWidth;
            column = coordinateToCell(horizontal, span, face.width());
        }
        return MudSurfaceLayout.cellIndex(part, surface, row, column);
    }

    static MudSurface hitSurface(
            double partY, double normalizedX, double normalizedZ) {
        if (partY >= 0.985D) {
            return MudSurface.TOP;
        }
        if (partY <= 0.015D) {
            return MudSurface.BOTTOM;
        }
        if (Math.abs(normalizedX) > Math.abs(normalizedZ)) {
            return normalizedX >= 0.0D
                    ? MudSurface.LEFT : MudSurface.RIGHT;
        }
        return normalizedZ >= 0.0D ? MudSurface.FRONT : MudSurface.BACK;
    }

    static BitSet openCrack(ServerPlayer player, AssimilationState state,
            int start, int radius) {
        BitSet opened = cellsAround(start, radius);
        state.revealedCells.or(opened);
        clearOrdinaryCoverage(player, opened);
        return opened;
    }

    static BitSet cellsAround(int start, int radius) {
        BitSet result = new BitSet(MudSurfaceLayout.CELL_COUNT);
        result.set(start);
        if (radius <= 0) {
            return result;
        }
        Deque<CellStep> queue = new ArrayDeque<>();
        BitSet visited = new BitSet(MudSurfaceLayout.CELL_COUNT);
        queue.add(new CellStep(start, 0));
        visited.set(start);
        while (!queue.isEmpty()) {
            CellStep current = queue.removeFirst();
            result.set(current.cell());
            if (current.distance() >= radius) {
                continue;
            }
            for (int neighbor : neighbors(current.cell())) {
                if (!visited.get(neighbor)) {
                    visited.set(neighbor);
                    queue.addLast(new CellStep(
                            neighbor, current.distance() + 1));
                }
            }
        }
        return result;
    }

    static void clearOrdinaryCoverage(ServerPlayer player, BitSet cells) {
        MudPlayerData mud = MudStateStore.get(player);
        boolean skinChanged = mud.clearSurfaceCoverage(cells);
        boolean armorChanged = false;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            ItemStack stack = player.getItemBySlot(slot);
            ArmorMudData original = ArmorMudManager.data(stack);
            if (stack.isEmpty() || original.isEmpty()) {
                continue;
            }
            ArmorMudData.Builder builder = original.toBuilder();
            for (int cell = cells.nextSetBit(0);
                    cell >= 0; cell = cells.nextSetBit(cell + 1)) {
                if (ArmorMudManager.slotOwnsSurface(
                        slot, MudSurfaceLayout.part(cell),
                        MudSurfaceLayout.surface(cell),
                        MudSurfaceLayout.row(cell))) {
                    builder.wash(cell, 1.0F, 1.0F, player.tickCount);
                }
            }
            if (builder.changed()) {
                ArmorMudManager.store(stack, builder.build());
                armorChanged = true;
            }
        }
        if (skinChanged) {
            MudCoverageService.sync(player, mud, true);
        }
        if (armorChanged) {
            player.getInventory().setChanged();
        }
    }

    private static int coordinateToCell(
            double coordinate, double span, int cells) {
        return Mth.clamp((int) Math.floor(
                (coordinate / Math.max(0.01D, span) + 0.5D) * cells),
                0, cells - 1);
    }

    private static int[] neighbors(int cell) {
        MudBodyPart part = MudSurfaceLayout.part(cell);
        MudSurface surface = MudSurfaceLayout.surface(cell);
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        int row = MudSurfaceLayout.row(cell);
        int column = MudSurfaceLayout.column(cell);
        return new int[] {
                neighbor(part, surface, face, row, column, -1, 0),
                neighbor(part, surface, face, row, column, 1, 0),
                neighbor(part, surface, face, row, column, 0, -1),
                neighbor(part, surface, face, row, column, 0, 1)
        };
    }

    private static int neighbor(MudBodyPart part, MudSurface surface,
            MudSurfaceLayout.Face face, int row, int column,
            int rowDelta, int columnDelta) {
        int nextRow = row + rowDelta;
        int nextColumn = column + columnDelta;
        if (nextRow >= 0 && nextRow < face.height()
                && nextColumn >= 0 && nextColumn < face.width()) {
            return MudSurfaceLayout.cellIndex(
                    part, surface, nextRow, nextColumn);
        }
        MudSurfaceLayout.Edge edge = nextRow < 0
                ? MudSurfaceLayout.Edge.ROW_MIN
                : nextRow >= face.height()
                        ? MudSurfaceLayout.Edge.ROW_MAX
                        : nextColumn < 0
                                ? MudSurfaceLayout.Edge.COLUMN_MIN
                                : MudSurfaceLayout.Edge.COLUMN_MAX;
        MudSurfaceLayout.AdjacentCell adjacent =
                MudSurfaceLayout.neighborAcrossEdge(
                        part, surface, row, column, edge);
        return MudSurfaceLayout.cellIndex(
                part, adjacent.surface(), adjacent.row(), adjacent.column());
    }

    private record CellStep(int cell, int distance) {
    }
}
