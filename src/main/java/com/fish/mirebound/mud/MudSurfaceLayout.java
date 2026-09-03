package com.fish.mirebound.mud;

/**
 * Canonical one-cell-per-skin-pixel topology for the vanilla player model.
 * Rows on vertical faces run from model bottom to top. Rows on cap faces run
 * from model back to front, matching the vanilla skin UV's top-to-bottom axis.
 */
public final class MudSurfaceLayout {
    public static final int LEGACY_BANDS = MudBodyPart.BANDS;
    public static final int LEGACY_LANES = MudBodyPart.SURFACE_LANES;
    public static final int CELL_COUNT = 1632;

    private static final MudBodyPart[] BODY_PARTS = MudBodyPart.values();
    private static final MudSurface[] SURFACES = MudSurface.values();
    private static final Face[] FACES = new Face[MudBodyPart.COUNT * MudSurface.COUNT];
    private static final byte[] CELL_PART = new byte[CELL_COUNT];
    private static final byte[] CELL_SURFACE = new byte[CELL_COUNT];
    private static final byte[] CELL_ROW = new byte[CELL_COUNT];
    private static final byte[] CELL_COLUMN = new byte[CELL_COUNT];

    static {
        int offset = 0;
        for (MudBodyPart part : BODY_PARTS) {
            CubeSize cube = cubeSize(part);
            for (MudSurface surface : SURFACES) {
                int width = switch (surface) {
                    case LEFT, RIGHT -> cube.depth();
                    default -> cube.width();
                };
                int height = switch (surface) {
                    case TOP, BOTTOM -> cube.depth();
                    default -> cube.height();
                };
                Face face = new Face(part, surface, width, height, offset);
                FACES[faceIndex(part, surface)] = face;
                for (int row = 0; row < height; row++) {
                    for (int column = 0; column < width; column++) {
                        int index = offset + row * width + column;
                        CELL_PART[index] = (byte) part.ordinal();
                        CELL_SURFACE[index] = (byte) surface.ordinal();
                        CELL_ROW[index] = (byte) row;
                        CELL_COLUMN[index] = (byte) column;
                    }
                }
                offset += width * height;
            }
        }
        if (offset != CELL_COUNT) {
            throw new IllegalStateException("Mud surface topology has " + offset + " cells, expected " + CELL_COUNT);
        }
    }

    private MudSurfaceLayout() {
    }

    public static Face face(MudBodyPart part, MudSurface surface) {
        return FACES[faceIndex(part, surface)];
    }

    public static int cellIndex(MudBodyPart part, MudSurface surface, int row, int column) {
        Face face = face(part, surface);
        if (row < 0 || row >= face.height() || column < 0 || column >= face.width()) {
            throw new IndexOutOfBoundsException(part + "/" + surface + " row=" + row + " column=" + column);
        }
        return face.offset() + row * face.width() + column;
    }

    public static MudBodyPart part(int index) {
        checkCellIndex(index);
        return BODY_PARTS[CELL_PART[index] & 0xFF];
    }

    public static MudSurface surface(int index) {
        checkCellIndex(index);
        return SURFACES[CELL_SURFACE[index] & 0xFF];
    }

    public static int row(int index) {
        checkCellIndex(index);
        return CELL_ROW[index] & 0xFF;
    }

    public static int column(int index) {
        checkCellIndex(index);
        return CELL_COLUMN[index] & 0xFF;
    }

    public static int modelHeight(MudBodyPart part) {
        return cubeSize(part).height();
    }

    public static int legacyBand(MudBodyPart part, MudSurface surface, int row) {
        Face face = face(part, surface);
        return switch (surface) {
            case BOTTOM -> 0;
            case TOP -> LEGACY_BANDS - 1;
            default -> Math.min(LEGACY_BANDS - 1, row * LEGACY_BANDS / face.height());
        };
    }

    public static int legacyLane(MudBodyPart part, MudSurface surface, int column) {
        Face face = face(part, surface);
        return Math.min(LEGACY_LANES - 1, column * LEGACY_LANES / face.width());
    }

    public static int rowFromUv(MudBodyPart part, MudSurface surface, int uvRow, int uvHeight) {
        Face face = face(part, surface);
        int mapped = Math.min(face.height() - 1, Math.max(0, uvRow) * face.height() / Math.max(1, uvHeight));
        return face.vertical() ? face.height() - 1 - mapped : mapped;
    }

    public static int columnFromUv(MudBodyPart part, MudSurface surface, int uvColumn, int uvWidth, boolean reverse) {
        Face face = face(part, surface);
        int mapped = Math.min(face.width() - 1, Math.max(0, uvColumn) * face.width() / Math.max(1, uvWidth));
        return reverse ? face.width() - 1 - mapped : mapped;
    }

    public static AdjacentCell neighborAcrossEdge(MudBodyPart part, MudSurface surface, int row, int column, Edge edge) {
        Face source = face(part, surface);
        boolean onEdge = switch (edge) {
            case ROW_MIN -> row == 0;
            case ROW_MAX -> row == source.height() - 1;
            case COLUMN_MIN -> column == 0;
            case COLUMN_MAX -> column == source.width() - 1;
        };
        if (!onEdge) {
            throw new IllegalArgumentException(part + "/" + surface + " cell " + row + "," + column
                    + " is not on " + edge);
        }

        return switch (surface) {
            case FRONT -> switch (edge) {
                case ROW_MIN -> adjacent(part, MudSurface.BOTTOM, sourceDepth(part) - 1, column, Edge.ROW_MAX);
                case ROW_MAX -> adjacent(part, MudSurface.TOP, sourceDepth(part) - 1, column, Edge.ROW_MAX);
                case COLUMN_MIN -> adjacent(part, MudSurface.RIGHT, row, sourceDepth(part) - 1, Edge.COLUMN_MAX);
                case COLUMN_MAX -> adjacent(part, MudSurface.LEFT, row, sourceDepth(part) - 1, Edge.COLUMN_MAX);
            };
            case BACK -> switch (edge) {
                case ROW_MIN -> adjacent(part, MudSurface.BOTTOM, 0, column, Edge.ROW_MIN);
                case ROW_MAX -> adjacent(part, MudSurface.TOP, 0, column, Edge.ROW_MIN);
                case COLUMN_MIN -> adjacent(part, MudSurface.RIGHT, row, 0, Edge.COLUMN_MIN);
                case COLUMN_MAX -> adjacent(part, MudSurface.LEFT, row, 0, Edge.COLUMN_MIN);
            };
            case RIGHT -> switch (edge) {
                case ROW_MIN -> adjacent(part, MudSurface.BOTTOM, column, 0, Edge.COLUMN_MIN);
                case ROW_MAX -> adjacent(part, MudSurface.TOP, column, 0, Edge.COLUMN_MIN);
                case COLUMN_MIN -> adjacent(part, MudSurface.BACK, row, 0, Edge.COLUMN_MIN);
                case COLUMN_MAX -> adjacent(part, MudSurface.FRONT, row, 0, Edge.COLUMN_MIN);
            };
            case LEFT -> switch (edge) {
                case ROW_MIN -> adjacent(part, MudSurface.BOTTOM, column, sourceWidth(part) - 1, Edge.COLUMN_MAX);
                case ROW_MAX -> adjacent(part, MudSurface.TOP, column, sourceWidth(part) - 1, Edge.COLUMN_MAX);
                case COLUMN_MIN -> adjacent(part, MudSurface.BACK, row, sourceWidth(part) - 1, Edge.COLUMN_MAX);
                case COLUMN_MAX -> adjacent(part, MudSurface.FRONT, row, sourceWidth(part) - 1, Edge.COLUMN_MAX);
            };
            case TOP -> switch (edge) {
                case ROW_MIN -> adjacent(part, MudSurface.BACK, sourceHeight(part) - 1, column, Edge.ROW_MAX);
                case ROW_MAX -> adjacent(part, MudSurface.FRONT, sourceHeight(part) - 1, column, Edge.ROW_MAX);
                case COLUMN_MIN -> adjacent(part, MudSurface.RIGHT, sourceHeight(part) - 1, row, Edge.ROW_MAX);
                case COLUMN_MAX -> adjacent(part, MudSurface.LEFT, sourceHeight(part) - 1, row, Edge.ROW_MAX);
            };
            case BOTTOM -> switch (edge) {
                case ROW_MIN -> adjacent(part, MudSurface.BACK, 0, column, Edge.ROW_MIN);
                case ROW_MAX -> adjacent(part, MudSurface.FRONT, 0, column, Edge.ROW_MIN);
                case COLUMN_MIN -> adjacent(part, MudSurface.RIGHT, 0, row, Edge.ROW_MIN);
                case COLUMN_MAX -> adjacent(part, MudSurface.LEFT, 0, row, Edge.ROW_MIN);
            };
        };
    }

    public static int legacySurfaceIndex(MudBodyPart part, int band, MudSurface surface, int lane) {
        int safeBand = Math.max(0, Math.min(LEGACY_BANDS - 1, band));
        int safeLane = Math.max(0, Math.min(LEGACY_LANES - 1, lane));
        return ((part.ordinal() * LEGACY_BANDS + safeBand) * MudSurface.COUNT + surface.ordinal()) * LEGACY_LANES
                + safeLane;
    }

    private static int faceIndex(MudBodyPart part, MudSurface surface) {
        return part.ordinal() * MudSurface.COUNT + surface.ordinal();
    }

    private static CubeSize cubeSize(MudBodyPart part) {
        return switch (part) {
            case HEAD -> new CubeSize(8, 8, 8);
            case BODY -> new CubeSize(8, 12, 4);
            case LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG -> new CubeSize(4, 12, 4);
        };
    }

    private static AdjacentCell adjacent(MudBodyPart part, MudSurface surface, int row, int column, Edge edge) {
        Face face = face(part, surface);
        if (row < 0 || row >= face.height() || column < 0 || column >= face.width()) {
            throw new IllegalStateException("Invalid adjacent cell " + part + "/" + surface + " " + row + "," + column);
        }
        return new AdjacentCell(surface, row, column, edge);
    }

    private static int sourceWidth(MudBodyPart part) {
        return cubeSize(part).width();
    }

    private static int sourceHeight(MudBodyPart part) {
        return cubeSize(part).height();
    }

    private static int sourceDepth(MudBodyPart part) {
        return cubeSize(part).depth();
    }

    private static void checkCellIndex(int index) {
        if (index < 0 || index >= CELL_COUNT) {
            throw new IndexOutOfBoundsException("Mud surface cell " + index);
        }
    }

    public record Face(MudBodyPart part, MudSurface surface, int width, int height, int offset) {
        public int cellCount() {
            return width * height;
        }

        public boolean vertical() {
            return surface != MudSurface.TOP && surface != MudSurface.BOTTOM;
        }
    }

    public enum Edge {
        ROW_MIN,
        ROW_MAX,
        COLUMN_MIN,
        COLUMN_MAX
    }

    public record AdjacentCell(MudSurface surface, int row, int column, Edge edge) {
    }

    private record CubeSize(int width, int height, int depth) {
    }
}
