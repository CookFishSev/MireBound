package com.fish.mirebound.generation.natural;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/** Register once in every biome, including untagged mod biomes. The profile gates placement. */
public record NaturalMudBiomeModifier(Holder<PlacedFeature> feature) implements BiomeModifier {
    public static final MapCodec<NaturalMudBiomeModifier> CODEC = PlacedFeature.CODEC
            .fieldOf("feature").xmap(NaturalMudBiomeModifier::new, NaturalMudBiomeModifier::feature);
    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD) builder.getGenerationSettings()
                .addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, feature);
    }
    @Override
    public MapCodec<? extends BiomeModifier> codec() { return CODEC; }
}
