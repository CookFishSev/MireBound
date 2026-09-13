package com.fish.mirebound.client.worldgen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;

/** Actual dimension membership with a registry fallback for biomes injected after world creation. */
final class NaturalMudDimensionCatalog {
    private NaturalMudDimensionCatalog() {}

    static String translationKey(ResourceLocation dimension) {
        if (!"minecraft".equals(dimension.getNamespace())) return dimension.toLanguageKey("dimension");
        String name = switch (dimension.getPath()) {
            case "the_nether" -> "nether";
            case "the_end" -> "end";
            default -> dimension.getPath();
        };
        return "gui.mirebound.worldgen.dimension." + name;
    }

    static List<Entry> read(WorldCreationContext context) {
        Map<ResourceLocation, LevelStem> stems = new HashMap<>();
        context.datapackDimensions().holders().forEach(holder ->
                stems.put(holder.key().location(), holder.value()));
        context.selectedDimensions().dimensions().forEach((key, value) -> stems.put(key.location(), value));
        return stems.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new Entry(
                entry.getKey(), entry.getValue().generator().getBiomeSource().possibleBiomes().stream()
                        .flatMap(biome -> biome.unwrapKey().stream()).map(key -> key.location())
                        .collect(Collectors.toUnmodifiableSet()))).toList();
    }

    static boolean matches(List<Entry> entries, ResourceLocation dimension,
            ResourceLocation biome, Set<ResourceLocation> taggedDimensions) {
        boolean assigned = false;
        for (Entry entry : entries) {
            if (!entry.biomes().contains(biome)) continue;
            if (entry.id().equals(dimension)) return true;
            assigned = true;
        }
        if (assigned) return false;
        return taggedDimensions.isEmpty() || taggedDimensions.contains(dimension);
    }
    record Entry(ResourceLocation id, Set<ResourceLocation> biomes) {}
}
