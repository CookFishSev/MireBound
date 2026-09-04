package com.fish.mirebound.rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RescueStateMachineTest {
    @Test
    void acceptsOnlyTheRescueLifecycleTransitions() {
        assertTrue(RescueStateMachine.canTransition(
                RescueStateMachine.State.IDLE, RescueStateMachine.State.FLYING));
        assertTrue(RescueStateMachine.canTransition(
                RescueStateMachine.State.FLYING, RescueStateMachine.State.ANCHORED));
        assertTrue(RescueStateMachine.canTransition(
                RescueStateMachine.State.ANCHORED, RescueStateMachine.State.HAULING));
        assertTrue(RescueStateMachine.canTransition(
                RescueStateMachine.State.HAULING, RescueStateMachine.State.ANCHORED));
        assertTrue(RescueStateMachine.canTransition(
                RescueStateMachine.State.HAULING, RescueStateMachine.State.IDLE));
        assertFalse(RescueStateMachine.canTransition(
                RescueStateMachine.State.IDLE, RescueStateMachine.State.ANCHORED));
        assertFalse(RescueStateMachine.canTransition(
                RescueStateMachine.State.ANCHORED, RescueStateMachine.State.FLYING));
    }

    @Test
    void rejectsSkippingTheAnchorState() {
        assertThrows(IllegalStateException.class, () -> RescueStateMachine.transition(
                RescueStateMachine.State.IDLE, RescueStateMachine.State.HAULING));
        assertThrows(IllegalStateException.class, () -> RescueStateMachine.transition(
                RescueStateMachine.State.FLYING, RescueStateMachine.State.HAULING));
    }

    @Test
    void sameStateIsNotASecondTransition() {
        assertEquals(RescueStateMachine.State.ANCHORED,
                RescueStateMachine.transition(
                        RescueStateMachine.State.ANCHORED,
                        RescueStateMachine.State.ANCHORED));
    }
}
