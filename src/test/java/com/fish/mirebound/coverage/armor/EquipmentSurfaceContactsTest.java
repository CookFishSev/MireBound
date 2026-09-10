package com.fish.mirebound.coverage.armor;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EquipmentSurfaceContactsTest {
    @Test void waterGunOnlyReachesTheCorrectAntennaAndIgnoresStalePoses() {
        var contacts = new EquipmentSurfaceContacts();
        contacts.offer(1,0,new Vec3(-1,1,0),100);
        contacts.offer(2,0,new Vec3(1,1,0),100);
        var hit = contacts.near(new Vec3(-1,1,0),.3,104);
        assertEquals(1,hit.size());assertEquals(1,hit.getFirst().key().face());
        assertTrue(contacts.near(new Vec3(-1,1,0),.3,130).isEmpty());
        assertTrue(contacts.near(new Vec3(-1,1,0),.3,99).isEmpty());
    }
}
