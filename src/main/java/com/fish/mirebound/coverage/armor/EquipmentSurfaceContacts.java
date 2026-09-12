package com.fish.mirebound.coverage.armor;

import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Bounded recent poses used by the water gun, never persisted as item coordinates. */
final class EquipmentSurfaceContacts {
    record Key(long face, int cell) {}
    record Contact(Key key, Vec3 offset, int tick, int wallTick) {}
    private final LinkedHashMap<Key, Contact> contacts = new LinkedHashMap<>(128, .75F, true);
    private int lastFullPass = Integer.MIN_VALUE;
    boolean acceptFullPass(int tick) {
        if (lastFullPass == tick) return false;
        lastFullPass = tick;
        return true;
    }
    void offer(long face, int cell, Vec3 offset, int tick) {
        Key key = new Key(face, cell);
        Contact previous = contacts.get(key);
        contacts.put(key, new Contact(key, offset, tick, previous == null ? Integer.MIN_VALUE : previous.wallTick));
        while (contacts.size() > EquipmentSurfaceData.MAX_CELLS) contacts.remove(contacts.keySet().iterator().next());
    }
    boolean reserveWallTransfer(long face, int cell, int tick, int interval) {
        Key key = new Key(face, cell);
        Contact contact = contacts.get(key);
        if (contact == null || contact.wallTick != Integer.MIN_VALUE && tick >= contact.wallTick
                && tick - contact.wallTick < Math.max(1, interval)) return false;
        contacts.put(key, new Contact(key, contact.offset, contact.tick, tick));
        return true;
    }
    List<Contact> near(Vec3 relativeImpact, double radius, int tick) {
        double squared = radius * radius;
        return contacts.values().stream().filter(contact -> tick >= contact.tick && tick - contact.tick <= 20
                && contact.offset.distanceToSqr(relativeImpact) <= squared).toList();
    }
}
