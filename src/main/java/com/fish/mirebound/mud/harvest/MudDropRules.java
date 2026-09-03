package com.fish.mirebound.mud.harvest;

import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.harvest.MudDropRule.MudDropEntry;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.registry.ModMudworkContent;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Items;

/**
 * The standardized drop table for every sinking medium.
 *
 * <p>Before this table only {@link SinkingMedium#MUD} dropped anything without Silk Touch; the
 * other media were silently destroyed. Each native mud medium yields its own texture-matched mud
 * ball, while other media retain a compact vanilla material representation.
 *
 * <p>Drops are safe to make useful because mud volume is conserved: flow is finite-volume with no
 * regeneration and bucket pickup debits source pixels, so a deposit cannot be farmed indefinitely.
 *
 * <p>The map is fully populated at class init and validated by {@link #ruleFor}, so adding a medium
 * to {@link SinkingMedium} without a rule fails fast in tests rather than silently dropping nothing.
 */
public final class MudDropRules {
    private static final Map<SinkingMedium, MudDropRule> RULES = createRules();

    private MudDropRules() {
    }

    /** Returns the drop rule for a medium; never null for a registered medium. */
    public static MudDropRule ruleFor(SinkingMedium medium) {
        MudDropRule rule = RULES.get(medium);
        if (rule == null) {
            throw new IllegalStateException("No drop rule registered for medium " + medium);
        }
        return rule;
    }

    /** Exposes the table for validation without allowing mutation. */
    public static Map<SinkingMedium, MudDropRule> rules() {
        return RULES;
    }

    private static Map<SinkingMedium, MudDropRule> createRules() {
        Map<SinkingMedium, MudDropRule> rules = new EnumMap<>(SinkingMedium.class);

        // Native mud media: each uses a matching mud ball and the shared height-based yield.
        rules.put(SinkingMedium.MUD,
                MudDropRule.of(ModBlocks.MUD_BALL, MudDropYield.SCALED));
        rules.put(SinkingMedium.THIN_MUD,
                MudDropRule.of(ModBlocks.THIN_MUD_BALL, MudDropYield.SCALED));
        rules.put(SinkingMedium.SHALLOW_MUD,
                MudDropRule.of(ModBlocks.SHALLOW_MUD_BALL, MudDropYield.SCALED));
        rules.put(SinkingMedium.TIDAL_MUD,
                MudDropRule.of(ModBlocks.TIDAL_MUD_BALL, MudDropYield.SCALED,
                new MudDropEntry(() -> Items.SEAGRASS, MudDropYield.SPARSE)));
        rules.put(SinkingMedium.MIRE,
                MudDropRule.of(ModBlocks.MIRE_MUD_BALL, MudDropYield.SCALED,
                new MudDropEntry(() -> Items.DEAD_BUSH, MudDropYield.RARE)));

        // Sand family: one visible material block for one full medium block.
        rules.put(SinkingMedium.SOFT_QUICKSAND,
                MudDropRule.of(() -> Items.SAND, MudDropYield.BLOCK_SCALED));
        rules.put(SinkingMedium.RED_QUICKSAND,
                MudDropRule.of(() -> Items.RED_SAND, MudDropYield.BLOCK_SCALED));
        rules.put(SinkingMedium.ASH_QUICKSAND,
                MudDropRule.of(() -> Items.SAND, MudDropYield.BLOCK_SCALED,
                        new MudDropEntry(() -> Items.GUNPOWDER, MudDropYield.SPARSE)));
        rules.put(SinkingMedium.JUNGLE_QUICKSAND,
                MudDropRule.of(() -> Items.SAND, MudDropYield.FULL_BLOCK_ONLY,
                        new MudDropEntry(() -> Items.STICK, MudDropYield.SPARSE)));

        // Silt family: fine sediment, each keyed to the biome material it reads as.
        rules.put(SinkingMedium.SILT,
                MudDropRule.of(ModBlocks.SILT, MudDropYield.SINGLE));
        rules.put(SinkingMedium.GRAVEL_SILT,
                MudDropRule.of(ModBlocks.GRAVEL_SILT_MUD_BALL, MudDropYield.SCALED,
                        new MudDropEntry(() -> Items.FLINT, MudDropYield.SPARSE)));
        rules.put(SinkingMedium.SOUL_SILT,
                MudDropRule.of(() -> Items.SOUL_SOIL, MudDropYield.BLOCK_SCALED));
        rules.put(SinkingMedium.END_SILT,
                MudDropRule.of(() -> Items.END_STONE, MudDropYield.BLOCK_SCALED,
                        new MudDropEntry(() -> Items.CHORUS_FRUIT, MudDropYield.RARE)));
        rules.put(SinkingMedium.PEAT_SILT,
                MudDropRule.of(ModBlocks.PEAT_SILT_MUD_BALL, MudDropYield.SCALED,
                        new MudDropEntry(() -> Items.CHARCOAL, MudDropYield.SPARSE)));

        // Peat and bog: organic soil plus the plant matter growing in it.
        rules.put(SinkingMedium.PEAT_BOG,
                MudDropRule.of(ModBlocks.PEAT_BOG_MUD_BALL, MudDropYield.SCALED,
                        new MudDropEntry(() -> Items.CHARCOAL, MudDropYield.SPARSE)));

        // Clay family: each native clay keeps its own color and four balls rebuild one block.
        rules.put(SinkingMedium.GEL_CLAY,
                MudDropRule.of(ModMudworkContent.GEL_CLAY_BALL,
                        MudDropYield.PIECE_SCALED));
        rules.put(SinkingMedium.LIME_MUD,
                MudDropRule.of(ModBlocks.LIME_MUD_BALL, MudDropYield.SCALED));
        rules.put(SinkingMedium.STONE_CLAY,
                MudDropRule.of(ModMudworkContent.STONE_CLAY_BALL,
                        MudDropYield.PIECE_SCALED));
        rules.put(SinkingMedium.PALE_MIRE,
                MudDropRule.of(ModMudworkContent.PALE_CLAY_BALL,
                        MudDropYield.PIECE_SCALED));

        // Slime family: elastic media that give back slime rather than soil.
        rules.put(SinkingMedium.LIVING_SLIME,
                MudDropRule.of(() -> Items.SLIME_BALL, MudDropYield.PIECE_SCALED));
        rules.put(SinkingMedium.ASSIMILATION_SLIME,
                MudDropRule.of(ModMudworkContent.BLOOD_CLOT_BALL,
                        MudDropYield.PIECE_SCALED));

        // Tar: flammable residue rather than soil.
        rules.put(SinkingMedium.TAR,
                MudDropRule.of(ModMudworkContent.TAR_BLOB,
                        MudDropYield.NINE_PIECE_SCALED));

        // Fungal and sculk: the growth that defines the block.
        rules.put(SinkingMedium.FUNGAL_MIRE,
                MudDropRule.of(ModBlocks.FUNGAL_MIRE_MUD_BALL, MudDropYield.SCALED,
                new MudDropEntry(() -> Items.BROWN_MUSHROOM, MudDropYield.SPARSE),
                new MudDropEntry(() -> Items.RED_MUSHROOM, MudDropYield.SPARSE)));
        rules.put(SinkingMedium.SCULK_MIRE,
                MudDropRule.of(() -> Items.AIR, MudDropYield.NONE));

        // Creature media: organic matter, matching their sword-preferred harvest profile.
        rules.put(SinkingMedium.TENDER_FLESH, MudDropRule.of(() -> Items.ROTTEN_FLESH,
                MudDropYield.PIECE_SCALED,
                new MudDropEntry(() -> Items.STRING, MudDropYield.RARE)));
        rules.put(SinkingMedium.INSECT_MOUND,
                MudDropRule.of(ModMudworkContent.MAGGOT,
                        MudDropYield.PIECE_SCALED));

        return Map.copyOf(rules);
    }
}
