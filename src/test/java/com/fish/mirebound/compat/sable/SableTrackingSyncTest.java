package com.fish.mirebound.compat.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class SableTrackingSyncTest {
    @Test
    void readsDeduplicatedSortedLoadedStorageChunks() {
        FakeSubLevel subLevel = new FakeSubLevel(new FakePlot(List.of(
                new FakeHolder(new FakeChunk(new ChunkPos(3, -2))),
                new FakeHolder(new FakeChunk(new ChunkPos(-1, 4))),
                new FakeHolder(new FakeChunk(new ChunkPos(3, -2))))));

        assertEquals(List.of(new ChunkPos(3, -2), new ChunkPos(-1, 4)),
                SableTrackingSync.loadedStorageChunks(subLevel));
    }

    public record FakeSubLevel(FakePlot plot) {
        public FakePlot getPlot() {
            return plot;
        }
    }

    public record FakePlot(List<FakeHolder> holders) {
        public List<FakeHolder> getLoadedChunks() {
            return holders;
        }
    }

    public record FakeHolder(FakeChunk chunk) {
        public FakeChunk getChunk() {
            return chunk;
        }
    }

    public record FakeChunk(ChunkPos pos) {
        public ChunkPos getPos() {
            return pos;
        }
    }
}
