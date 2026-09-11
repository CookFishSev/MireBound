package com.fish.mirebound.client.skin;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.coverage.skin.SkinStainMask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkinStainStorageTest {
    @TempDir Path root;
    @Test void selfMaskRoundTripsWithoutPlayerOverrides() throws Exception {
        SkinStainStorage store=new SkinStainStorage(root);
        assertTrue(store.loadMask().isEmpty());
        BitSet bits=new BitSet();bits.set(1023);bits.set(65535);
        SkinStainMask mask=new SkinStainMask(256,256,bits);
        store.saveMask(mask); assertEquals(mask,store.loadMask());
        assertFalse(Files.readString(root.resolve("self.json")).contains("players"));
        store.saveMask(SkinStainMask.empty(64,64));
        assertTrue(store.loadMask().isEmpty());
        store.saveMask(mask);
        assertEquals(mask,store.loadMask());
    }

    @Test void malformedLocalFilesDoNotBecomeUnboundedMasks() throws Exception {
        Files.writeString(root.resolve("self.json"),"{\"version\":1,\"mask\":{\"width\":100000,\"height\":100000,\"blocked\":\"\"}}");
        assertThrows(IOException.class,()->new SkinStainStorage(root).loadMask());
        Files.writeString(root.resolve("self.json"),"{\"version\":2}");
        assertThrows(IOException.class,()->new SkinStainStorage(root).loadMask());
    }
}
