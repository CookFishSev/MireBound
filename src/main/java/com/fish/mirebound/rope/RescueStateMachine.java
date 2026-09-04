package com.fish.mirebound.rope;

/** Legal server-side lifecycle for one rescue-rope interaction. */
public final class RescueStateMachine {
    private RescueStateMachine() {
    }

    public enum State {
        IDLE,
        FLYING,
        ANCHORED,
        HAULING
    }

    public static boolean canTransition(State from, State to) {
        if (from == null || to == null || from == to) {
            return from == to;
        }
        return switch (from) {
            case IDLE -> to == State.FLYING;
            case FLYING -> to == State.ANCHORED || to == State.IDLE;
            case ANCHORED -> to == State.HAULING || to == State.IDLE;
            case HAULING -> to == State.ANCHORED || to == State.IDLE;
        };
    }

    public static State transition(State from, State to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal rescue state transition: "
                    + from + " -> " + to);
        }
        return to;
    }
}
