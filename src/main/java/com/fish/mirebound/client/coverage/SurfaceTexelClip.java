package com.fish.mirebound.client.coverage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Splits a physical cell at actual material texel boundaries, including mirrored/rotated UVs. */
public final class SurfaceTexelClip {
    private SurfaceTexelClip() {}
    @FunctionalInterface public interface PatchConsumer {
        void accept(double s0, double t0, double s1, double t1, int abgr);
    }

    public static int visit(EquipmentSurfaceGeometry.Face face, int x, int y,
            SurfaceMaterial material, int budget, PatchConsumer consumer) {
        if (budget <= 0 || material.width() <= 0 || material.height() <= 0) return 0;
        double s0 = x / (double) face.width(), s1 = (x + 1D) / face.width();
        double t0 = y / (double) face.height(), t1 = (y + 1D) / face.height();
        double us = face.b().u() - face.a().u(), ut = face.d().u() - face.a().u();
        double vs = face.b().v() - face.a().v(), vt = face.d().v() - face.a().v();
        // ModelPart and baked cuboids use rectangular UVs. Reject a non-affine mapping
        // instead of filling an unknown transparent region with an opaque rectangle.
        if (Math.abs(face.c().u() - face.a().u() - us - ut) > 1e-5
                || Math.abs(face.c().v() - face.a().v() - vs - vt) > 1e-5
                || Math.abs(us * ut) > 1e-7 || Math.abs(vs * vt) > 1e-7) return 0;
        List<Double> s = boundaries(s0, s1, Math.abs(us) > 1e-7 ? face.a().u() : face.a().v(),
                Math.abs(us) > 1e-7 ? us : vs, Math.abs(us) > 1e-7 ? material.width() : material.height());
        List<Double> t = boundaries(t0, t1, Math.abs(ut) > 1e-7 ? face.a().u() : face.a().v(),
                Math.abs(ut) > 1e-7 ? ut : vt, Math.abs(ut) > 1e-7 ? material.width() : material.height());
        int visited = 0;
        for (int j = 1; j < t.size(); j++) for (int i = 1; i < s.size(); i++) {
            if (visited++ >= budget) return budget;
            double a = s.get(i-1), b = t.get(j-1), c = s.get(i), d = t.get(j);
            int pixel = material.sample(face.u((a+c)*.5, (b+d)*.5), face.v((a+c)*.5, (b+d)*.5));
            if ((pixel >>> 24) != 0) consumer.accept(a,b,c,d,pixel);
        }
        return visited;
    }

    private static List<Double> boundaries(double low, double high, double origin, double slope, int size) {
        List<Double> values = new ArrayList<>();
        values.add(low);
        if (Math.abs(slope) > 1e-7) {
            double a = (origin + slope * low) * size, b = (origin + slope * high) * size;
            int first = Math.max(0, (int)Math.floor(Math.min(a,b)) + 1);
            int last = Math.min(size, (int)Math.ceil(Math.max(a,b)) - 1);
            for (int pixel = first; pixel <= last; pixel++) {
                double value = (pixel / (double)size - origin) / slope;
                if (value > low + 1e-8 && value < high - 1e-8) values.add(value);
            }
        }
        values.add(high);
        Collections.sort(values);
        return values;
    }
}
