package com.fish.mirebound.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerInputBudgetTest {
    @Test
    void eachInputClassHasAnIndependentPerTickBudget() {
        ServerInputBudget.TickBudget budget = new ServerInputBudget.TickBudget();
        for (int index = 0; index < 4; index++) {
            assertTrue(budget.allow(ServerInputBudget.Channel.PLAYER_GEOMETRY, 100L));
        }
        assertFalse(budget.allow(ServerInputBudget.Channel.PLAYER_GEOMETRY, 100L));
        assertTrue(budget.allow(ServerInputBudget.Channel.ASSIMILATION_SOUL_POSITION, 100L));
    }

    @Test
    void advancingTheServerTickRestoresTheWholeBudget() {
        ServerInputBudget.TickBudget budget = new ServerInputBudget.TickBudget();
        for (int index = 0; index < 4; index++) {
            assertTrue(budget.allow(ServerInputBudget.Channel.MUD_TUNING_APPLY, 40L));
        }
        assertFalse(budget.allow(ServerInputBudget.Channel.MUD_TUNING_APPLY, 40L));
        assertTrue(budget.allow(ServerInputBudget.Channel.MUD_TUNING_APPLY, 41L));
    }

    @Test
    void ordinaryClientCadenceRemainsWellInsideInputBudgets() {
        ServerInputBudget.TickBudget budget = new ServerInputBudget.TickBudget();
        for (int index = 0; index < 4; index++) {
            assertTrue(budget.allow(ServerInputBudget.Channel.WATER_GUN_INPUT, 88L));
        }
        assertFalse(budget.allow(ServerInputBudget.Channel.WATER_GUN_INPUT, 88L));

        for (int index = 0; index < 8; index++) {
            assertTrue(budget.allow(ServerInputBudget.Channel.SCULK_MIRE_INPUT, 88L));
        }
        assertFalse(budget.allow(ServerInputBudget.Channel.SCULK_MIRE_INPUT, 88L));
    }
}
