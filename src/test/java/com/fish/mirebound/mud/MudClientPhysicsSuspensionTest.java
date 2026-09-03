package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudClientPhysicsSuspensionTest {
    @Test
    void cameraAndAssimilationSuspensionHaveIndependentOwnership() {
        assertFalse(MudClientPhysics.suspendedBy(false, false));
        assertTrue(MudClientPhysics.suspendedBy(true, false));
        assertTrue(MudClientPhysics.suspendedBy(false, true));
        assertTrue(MudClientPhysics.suspendedBy(true, true));
    }
}
