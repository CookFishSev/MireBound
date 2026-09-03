package com.fish.mirebound.mud;

import net.minecraft.util.Mth;

/** Two independently owned 10x16 cape faces shared by contact, persistence, sync, and UV painting. */
public final class MudCapeLayout {
    public static final int COLUMNS = 10;
    public static final int ROWS = 16;
    public static final int FACE_CELL_COUNT = COLUMNS * ROWS;
    public static final int CELL_COUNT = FACE_CELL_COUNT * Side.values().length;

    private MudCapeLayout() {
    }

    public static int index(int row, int column) {
        return index(Side.OUTER, row, column);
    }

    public static int index(Side side, int row, int column) {
        return side.ordinal() * FACE_CELL_COUNT
                + Mth.clamp(row, 0, ROWS - 1) * COLUMNS
                + Mth.clamp(column, 0, COLUMNS - 1);
    }

    public static int faceIndex(int row, int column) {
        return Mth.clamp(row, 0, ROWS - 1) * COLUMNS
                + Mth.clamp(column, 0, COLUMNS - 1);
    }

    public static int row(int index) {
        return Math.floorMod(Mth.clamp(index, 0, CELL_COUNT - 1), FACE_CELL_COUNT) / COLUMNS;
    }

    public static int column(int index) {
        return Math.floorMod(Mth.clamp(index, 0, CELL_COUNT - 1), FACE_CELL_COUNT) % COLUMNS;
    }

    public enum Side {
        OUTER,
        INNER
    }
}
