package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ContactGeometryModeTest {
    @Test
    void acceptsCanonicalAndShortNames() {
        assertEquals(ContactGeometryMode.MODEL_PART, ContactGeometryMode.byName("model_part"));
        assertEquals(ContactGeometryMode.MODEL_PART, ContactGeometryMode.byName("model"));
        assertEquals(ContactGeometryMode.SODIUM_VERTICES,
                ContactGeometryMode.byName("sodium_vertices"));
        assertEquals(ContactGeometryMode.SODIUM_VERTICES,
                ContactGeometryMode.byName("sodium"));
        assertEquals(ContactGeometryMode.AUTO, ContactGeometryMode.byName("AUTO"));
    }

    @Test
    void rejectsUnknownMode() {
        assertNull(ContactGeometryMode.byName("mesh_shader"));
        assertNull(ContactGeometryMode.byName(null));
    }
}
