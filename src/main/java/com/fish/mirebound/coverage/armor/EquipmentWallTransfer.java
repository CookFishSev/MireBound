package com.fish.mirebound.coverage.armor;

import com.fish.mirebound.network.payload.EquipmentSurfaceContactPayload;
import com.fish.mirebound.stain.MudWallStainSystem;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/** Adapts verified worn-item contact cells to the existing wall-stain transfer pipeline. */
final class EquipmentWallTransfer {
    private final Map<Long, Vec3> normals = new HashMap<>();
    private final List<MudWallStainSystem.EquipmentSource> sources = new ArrayList<>();
    private final EquipmentSurfaceData data;
    private final EquipmentSurfaceContacts contacts;
    private final int tick, interval, floor;

    EquipmentWallTransfer(EquipmentSurfaceContactPayload payload, EquipmentSurfaceData data,
            EquipmentSurfaceContacts contacts, int tick, int interval, float minimumCoverage) {
        this.data = data;
        this.contacts = contacts;
        this.tick = tick;
        this.interval = interval;
        floor = Math.round(Math.clamp(minimumCoverage, 0F, 1F) * 255);
        for (EquipmentSurfacePatch patch : payload.patches()) {
            Vec3 normal = patch.b().subtract(patch.a()).cross(patch.d().subtract(patch.a()));
            if (normal.lengthSqr() > 1e-10) normals.put(patch.face(), normal.normalize());
        }
    }

    void offer(long face, int cell, Vec3 point) {
        EquipmentSurfaceData.Cell dirt = data.cell(face, cell);
        Vec3 normal = normals.get(face);
        if (dirt == null || dirt.strength() <= floor || normal == null
                || sources.size() >= EquipmentSurfaceData.MAX_CELLS
                || !contacts.reserveWallTransfer(face, cell, tick, interval)) return;
        sources.add(new MudWallStainSystem.EquipmentSource(face, cell, point, normal,
                dirt.strength() / 255F, SinkingMedium.byId(dirt.medium()), dirt.source()));
    }

    List<MudWallStainSystem.EquipmentSource> sources() { return List.copyOf(sources); }
}
