package com.fish.mirebound.coverage.armor;

import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Bounded recent poses used by the water gun, never persisted as item coordinates. */
final class EquipmentSurfaceContacts {
    record Key(long face, int cell) {}
    record Contact(Key key, Vec3 offset, int tick) {}
    private final LinkedHashMap<Key, Contact> contacts = new LinkedHashMap<>(128, .75F, true);
    void offer(long face, int cell, Vec3 offset, int tick) {
        contacts.put(new Key(face, cell), new Contact(new Key(face, cell), offset, tick));
        while (contacts.size() > EquipmentSurfaceData.MAX_CELLS) contacts.remove(contacts.keySet().iterator().next());
    }
    List<Contact> near(Vec3 relativeImpact, double radius, int tick) {
        double squared = radius * radius;
        return contacts.values().stream().filter(contact -> tick >= contact.tick && tick - contact.tick <= 20
                && contact.offset.distanceToSqr(relativeImpact) <= squared).toList();
    }
}
