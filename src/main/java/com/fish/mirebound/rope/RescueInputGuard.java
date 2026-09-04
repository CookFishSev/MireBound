package com.fish.mirebound.rope;

import com.fish.mirebound.network.payload.RopeRescueHaulPayload;
import java.util.UUID;

/** Pure validation for rescue-haul session identity and packet ordering. */
final class RescueInputGuard {
    private RescueInputGuard() {
    }

    static boolean acceptsStart(UUID ownerId, UUID playerId,
            RescueStateMachine.State state, boolean sessionActive,
            long lastSessionId, int lassoFirst,
            RopeRescueHaulPayload payload) {
        return payload != null
                && payload.operation() == RopeRescueHaulPayload.Operation.START
                && ownerId != null && ownerId.equals(playerId)
                && state == RescueStateMachine.State.ANCHORED
                && !sessionActive
                && payload.sessionId() > lastSessionId
                && payload.sequence() > 0L
                && payload.segmentIndex() >= 0
                && payload.segmentIndex() < lassoFirst;
    }

    static boolean acceptsFollowUp(UUID sessionPlayerId, long sessionId,
            long lastSequence, UUID playerId, RopeRescueHaulPayload payload,
            RopeRescueHaulPayload.Operation expectedOperation) {
        return payload != null && expectedOperation != null
                && payload.operation() == expectedOperation
                && sessionPlayerId != null && sessionPlayerId.equals(playerId)
                && payload.sessionId() == sessionId
                && payload.sequence() > lastSequence;
    }
}
