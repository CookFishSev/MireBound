package com.fish.mirebound.stain;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Shared support-face clipping for authoritative contact and client flow previews. */
public final class WallStainGeometry {
    private WallStainGeometry() {
    }

    public static boolean contains(List<AABB> boxes, Direction face,
            double horizontal, double vertical) {
        final double tolerance = 1.0E-4D;
        for (AABB box : boxes) {
            boolean reachesFace = switch (face) {
                case WEST -> box.minX <= tolerance;
                case EAST -> box.maxX >= 1.0D - tolerance;
                case DOWN -> box.minY <= tolerance;
                case UP -> box.maxY >= 1.0D - tolerance;
                case NORTH -> box.minZ <= tolerance;
                case SOUTH -> box.maxZ >= 1.0D - tolerance;
            };
            if (!reachesFace) {
                continue;
            }
            boolean inside = switch (face.getAxis()) {
                case X -> horizontal >= box.minZ - tolerance && horizontal <= box.maxZ + tolerance
                        && vertical >= box.minY - tolerance && vertical <= box.maxY + tolerance;
                case Y -> horizontal >= box.minX - tolerance && horizontal <= box.maxX + tolerance
                        && vertical >= box.minZ - tolerance && vertical <= box.maxZ + tolerance;
                case Z -> horizontal >= box.minX - tolerance && horizontal <= box.maxX + tolerance
                        && vertical >= box.minY - tolerance && vertical <= box.maxY + tolerance;
            };
            if (inside) {
                return true;
            }
        }
        return false;
    }

}
