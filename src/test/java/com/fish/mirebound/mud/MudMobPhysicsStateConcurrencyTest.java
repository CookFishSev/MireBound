package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Guards the entity-keyed sinking state against unsynchronized cross-thread access.
 *
 * <p>{@code applyMudEffects} admits the client thread for locally controlled mounts while the
 * integrated server tick writes the same maps, and {@code collisionShape} reads them during
 * client collision resolution. An unsynchronized {@link WeakHashMap} can corrupt its table
 * during resize under that overlap, so these fields must stay wrapped.
 */
class MudMobPhysicsStateConcurrencyTest {
    private static final List<String> CROSS_THREAD_STATE_FIELDS =
            List.of("SINKING_STATES", "APPLIED_TICKS", "LAST_REPLAN_TICKS");

    @Test
    void entityKeyedStateFieldsAreSynchronizedWrappers() throws ReflectiveOperationException {
        Class<?> synchronizedMapType = Collections.synchronizedMap(new WeakHashMap<>()).getClass();

        for (String fieldName : CROSS_THREAD_STATE_FIELDS) {
            Field field = MudMobPhysics.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            assertTrue(Modifier.isStatic(field.getModifiers()),
                    fieldName + " must stay a static owner of mob mud state");
            assertEquals(synchronizedMapType, field.get(null).getClass(),
                    fieldName + " must remain a synchronized wrapper because the client thread and "
                            + "the integrated server tick both reach it");
        }
    }

    @Test
    void weakKeyedStateSurvivesConcurrentWritesAndReads() throws InterruptedException {
        // Reproduces the shape of the real overlap: one writer growing the table while a reader
        // walks it. An unsynchronized WeakHashMap can expose a corrupted table or spin forever
        // here; the synchronized wrapper cannot.
        Map<Object, Long> state = Collections.synchronizedMap(new WeakHashMap<>());
        List<Object> strongKeys = new ArrayList<>();
        for (int index = 0; index < 4_000; index++) {
            strongKeys.add(new Object());
        }

        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (Object key : strongKeys) {
                    state.put(key, 1L);
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, "mud-state-writer");

        Thread reader = new Thread(() -> {
            try {
                start.await();
                for (int pass = 0; pass < 4_000; pass++) {
                    for (Object key : strongKeys) {
                        state.get(key);
                    }
                    synchronized (state) {
                        state.size();
                    }
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, "mud-state-reader");

        writer.start();
        reader.start();
        start.countDown();
        writer.join(TimeUnit.SECONDS.toMillis(30));
        reader.join(TimeUnit.SECONDS.toMillis(30));

        assertTrue(failure.get() == null,
                () -> "concurrent mob mud state access failed: " + failure.get());
        assertEquals(strongKeys.size(), state.size(),
                "every written entity key must survive concurrent reads");
    }
}
