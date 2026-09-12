package com.fish.mirebound.coverage.armor;

import java.util.HashSet;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** A posed face and its visible cell probes, compressed relative to its four corners. */
public record EquipmentSurfacePatch(long face, int width, int height,
        Vec3 a, Vec3 b, Vec3 c, Vec3 d, List<Integer> probes) {
    public EquipmentSurfacePatch {
        probes = List.copyOf(probes);
        if (face == 0 || width < 1 || width > 16 || height < 1 || height > 16
                || !validCorner(a) || !validCorner(b) || !validCorner(c) || !validCorner(d)
                || probes.size() > width * height) throw new IllegalArgumentException("Invalid equipment face patch");
        HashSet<Integer> cells = new HashSet<>();
        for (int probe : probes) {
            int cell = cell(probe);
            if (probe < 0 || probe > 0xffffff || cell % 16 >= width || cell / 16 >= height
                    || !cells.add(cell)) throw new IllegalArgumentException("Invalid equipment patch cell");
        }
    }

    public static boolean validCorner(Vec3 point) {
        return point != null && Double.isFinite(point.lengthSqr()) && point.lengthSqr() <= 16;
    }

    public static int cell(int probe) {
        return probe >>> 16;
    }

    public static int probe(int cell, double withinX, double withinY) {
        int x = (int) Math.round(Math.clamp(withinX, 0D, 1D) * 255);
        int y = (int) Math.round(Math.clamp(withinY, 0D, 1D) * 255);
        return cell << 16 | x << 8 | y;
    }

    public Vec3 point(int probe) {
        int cell = cell(probe);
        double x = (cell % 16 + (probe >> 8 & 255) / 255D) / width;
        double y = (cell / 16 + (probe & 255) / 255D) / height;
        return a.lerp(b, x).lerp(d.lerp(c, x), y);
    }
}
