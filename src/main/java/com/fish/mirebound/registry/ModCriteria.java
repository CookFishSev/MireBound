package com.fish.mirebound.registry;

import com.fish.mirebound.Mirebound;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One-shot gameplay events used by Mirebound: Sinking Depths advancements. */
public final class ModCriteria {
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Mirebound.MOD_ID);

    private static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> ENTERED_MUD =
            TRIGGERS.register("entered_mud", PlayerTrigger::new);
    private static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> SCULK_RESTRAINED =
            TRIGGERS.register("sculk_restrained", PlayerTrigger::new);
    private static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> TENDER_FLESH_ENCLOSED =
            TRIGGERS.register("tender_flesh_enclosed", PlayerTrigger::new);

    private ModCriteria() {
    }

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }

    public static void enteredMud(ServerPlayer player) {
        ENTERED_MUD.get().trigger(player);
    }

    public static void sculkRestrained(ServerPlayer player) {
        SCULK_RESTRAINED.get().trigger(player);
    }

    public static void tenderFleshEnclosed(ServerPlayer player) {
        TENDER_FLESH_ENCLOSED.get().trigger(player);
    }
}
