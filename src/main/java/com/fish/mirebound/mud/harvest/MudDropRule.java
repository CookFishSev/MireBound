package com.fish.mirebound.mud.harvest;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;

/**
 * The complete non-silk-touch drop table for one sinking medium.
 *
 * <p>Every medium resolves to exactly one rule, so every full mud block has a defined material
 * outcome instead of the historical behavior where only {@link SinkingMedium#MUD} dropped
 * anything. Rules are declared as suppliers because mod items are registered lazily; nothing here
 * touches a registry until a block is actually broken.
 *
 * <p>Volume awareness lives in {@link MudDropYield}: the {@code primary} entry scales with the
 * broken block's 1/16 volume, while {@code bonus} entries are flat chance rolls that represent
 * debris embedded in the medium rather than the medium itself.
 */
public record MudDropRule(MudDropEntry primary, List<MudDropEntry> bonus) {
    public MudDropRule {
        bonus = bonus == null ? List.of() : List.copyOf(bonus);
    }

    public static MudDropRule of(Supplier<? extends ItemLike> item, MudDropYield yield) {
        return new MudDropRule(new MudDropEntry(item, yield), List.of());
    }

    public static MudDropRule of(Supplier<? extends ItemLike> item, MudDropYield yield,
            MudDropEntry... bonus) {
        return new MudDropRule(new MudDropEntry(item, yield), List.of(bonus));
    }

    /**
     * One item line in a drop rule.
     *
     * @param item lazily resolved item, so this table can be declared before registration
     * @param yield how the broken volume converts into a stack size
     */
    public record MudDropEntry(Supplier<? extends ItemLike> item, MudDropYield yield) {
    }
}
