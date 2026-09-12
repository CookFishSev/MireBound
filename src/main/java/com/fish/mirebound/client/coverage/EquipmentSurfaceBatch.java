package com.fish.mirebound.client.coverage;

import com.fish.mirebound.coverage.armor.EquipmentSurfaceData;
import com.fish.mirebound.coverage.armor.EquipmentSurfacePatch;
import com.fish.mirebound.network.payload.EquipmentSurfaceContactPayload;
import java.util.ArrayList;
import java.util.List;

/** Bounded transport batches rotate through the actual faces instead of truncating their tail. */
record EquipmentSurfaceBatch(List<EquipmentSurfacePatch> patches, int next, int surfaceCells) {
    static EquipmentSurfaceBatch select(List<EquipmentSurfacePatch> faces, int start) {
        if (faces.isEmpty()) return new EquipmentSurfaceBatch(List.of(), 0, 0);
        List<EquipmentSurfacePatch> selected = new ArrayList<>();
        int cursor = Math.floorMod(start, faces.size());
        int cells = 0;
        while (selected.size() < faces.size() && selected.size() < EquipmentSurfaceContactPayload.MAX_PATCHES) {
            EquipmentSurfacePatch face = faces.get(cursor);
            if (cells + face.probes().size() > EquipmentSurfaceData.MAX_CELLS) break;
            selected.add(face);
            cells += face.probes().size();
            cursor = (cursor + 1) % faces.size();
        }
        int total = faces.stream().mapToInt(p -> p.probes().size()).sum();
        return new EquipmentSurfaceBatch(List.copyOf(selected), cursor, total);
    }
}
