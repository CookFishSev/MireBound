package com.fish.mirebound.rope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.network.payload.RopeRescueHaulPayload;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RescueInputGuardTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER_PLAYER = UUID.randomUUID();

    @Test
    void startRequiresTheOwnerANewSessionAndAHaulableSegment() {
        RopeRescueHaulPayload start = RopeRescueHaulPayload.start(4, 2, 8L, 1L);

        assertTrue(RescueInputGuard.acceptsStart(
                OWNER, OWNER, RescueStateMachine.State.ANCHORED,
                false, 7L, 5, start));
        assertFalse(RescueInputGuard.acceptsStart(
                OWNER, OTHER_PLAYER, RescueStateMachine.State.ANCHORED,
                false, 7L, 5, start));
        assertFalse(RescueInputGuard.acceptsStart(
                OWNER, OWNER, RescueStateMachine.State.ANCHORED,
                false, 8L, 5, start));
        assertFalse(RescueInputGuard.acceptsStart(
                OWNER, OWNER, RescueStateMachine.State.FLYING,
                false, 7L, 5, start));
        assertFalse(RescueInputGuard.acceptsStart(
                OWNER, OWNER, RescueStateMachine.State.ANCHORED,
                false, 7L, 2, start));
    }

    @Test
    void followUpRejectsWrongPlayerSessionOperationAndSequence() {
        RopeRescueHaulPayload keepAlive =
                RopeRescueHaulPayload.keepAlive(4, 8L, 4L);

        assertTrue(RescueInputGuard.acceptsFollowUp(
                OWNER, 8L, 3L, OWNER, keepAlive,
                RopeRescueHaulPayload.Operation.KEEP_ALIVE));
        assertFalse(RescueInputGuard.acceptsFollowUp(
                OWNER, 8L, 3L, OTHER_PLAYER, keepAlive,
                RopeRescueHaulPayload.Operation.KEEP_ALIVE));
        assertFalse(RescueInputGuard.acceptsFollowUp(
                OWNER, 9L, 3L, OWNER, keepAlive,
                RopeRescueHaulPayload.Operation.KEEP_ALIVE));
        assertFalse(RescueInputGuard.acceptsFollowUp(
                OWNER, 8L, 4L, OWNER, keepAlive,
                RopeRescueHaulPayload.Operation.KEEP_ALIVE));
        assertFalse(RescueInputGuard.acceptsFollowUp(
                OWNER, 8L, 3L, OWNER, keepAlive,
                RopeRescueHaulPayload.Operation.STOP));
    }
}
