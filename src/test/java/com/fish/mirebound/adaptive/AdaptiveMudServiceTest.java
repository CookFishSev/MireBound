package com.fish.mirebound.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

class AdaptiveMudServiceTest {
    @Test
    void restoreSourceFilterOnlyMatchesTheRequestedOriginalBlock() {
        ResourceLocation stone = ResourceLocation.withDefaultNamespace("stone");
        ResourceLocation dirt = ResourceLocation.withDefaultNamespace("dirt");

        assertTrue(AdaptiveMudService.matchesSourceFilter(stone, stone));
        assertFalse(AdaptiveMudService.matchesSourceFilter(dirt, stone));
        assertFalse(AdaptiveMudService.matchesSourceFilter(null, stone));
        assertTrue(AdaptiveMudService.matchesSourceFilter(null, null));
    }

    @Test
    void conversionAndRestorationUseVanillaFunctionalBlockUpdates() {
        int flags = AdaptiveMudService.FUNCTIONAL_REPLACEMENT_FLAGS;

        assertTrue((flags & Block.UPDATE_CLIENTS) != 0);
        assertTrue((flags & Block.UPDATE_NEIGHBORS) != 0);
        assertEquals(0, flags & Block.UPDATE_KNOWN_SHAPE);
    }

    @Test
    void unrestrictedConversionKeepsAirExistingProxyAndNativeMudExcluded() {
        for (AdaptiveMudEligibility.Result result : AdaptiveMudEligibility.Result.values()) {
            assertEquals(result.supported(), AdaptiveMudService.canConvert(result, false));
            assertEquals(result != AdaptiveMudEligibility.Result.AIR
                            && result != AdaptiveMudEligibility.Result.ALREADY_ADAPTIVE
                            && result != AdaptiveMudEligibility.Result.ALREADY_MUD,
                    AdaptiveMudService.canConvert(result, true), result.name());
        }
    }

    @Test
    void blockEntityDataBoundaryPreservesNormalNestedData() {
        CompoundTag source = new CompoundTag();
        source.putString("id", "minecraft:chest");
        ListTag items = new ListTag();
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:stone");
        item.putByte("Count", (byte) 1);
        items.add(item);
        source.put("Items", items);

        CompoundTag bounded = AdaptiveMudSourceStore.boundedBlockEntityData(source);

        assertEquals(source, bounded);
        assertTrue(bounded != source);
    }

    @Test
    void blockEntityDataBoundaryRejectsOversizedNestedData() {
        CompoundTag tooLarge = new CompoundTag();
        tooLarge.put("Items", new ByteArrayTag(new byte[16_385]));

        assertEquals(null, AdaptiveMudSourceStore.boundedBlockEntityData(tooLarge));
    }

    @Test
    void blockEntityDataBoundaryRejectsExcessiveNesting() {
        CompoundTag root = new CompoundTag();
        CompoundTag current = root;
        for (int depth = 0; depth < 18; depth++) {
            CompoundTag next = new CompoundTag();
            current.put("nested", next);
            current = next;
        }

        assertEquals(null, AdaptiveMudSourceStore.boundedBlockEntityData(root));
    }

    @Test
    void blockEntityDataBoundaryDoesNotExposeMutableCopies() {
        CompoundTag source = new CompoundTag();
        source.putString("id", "minecraft:barrel");

        CompoundTag first = AdaptiveMudSourceStore.boundedBlockEntityData(source);
        first.putString("id", "minecraft:chest");

        CompoundTag second = AdaptiveMudSourceStore.boundedBlockEntityData(source);
        assertEquals("minecraft:barrel", second.getString("id"));
    }
}
