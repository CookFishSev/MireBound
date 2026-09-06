package com.fish.mirebound.mixin.client.tuning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class LevelTuningHighlightMixinTest {
    @Test
    void convertedBlocksInvalidateEvenThoughTheyExtendMudBlock() throws Exception {
        assertTrue(requiresInvalidation(true, true));
    }

    @Test
    void ordinaryMudKeepsTheExistingDefaultStateOptimization() throws Exception {
        assertFalse(requiresInvalidation(false, true));
        assertTrue(requiresInvalidation(false, false));
    }

    private static boolean requiresInvalidation(
            boolean adaptiveBlock, boolean ordinaryMudShape) throws Exception {
        Method method = LevelTuningHighlightMixin.class.getDeclaredMethod(
                "mirebound$requiresInvalidation", boolean.class, boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, adaptiveBlock, ordinaryMudShape);
    }
}
