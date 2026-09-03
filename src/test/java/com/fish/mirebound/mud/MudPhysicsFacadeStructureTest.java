package com.fish.mirebound.mud;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MudPhysicsFacadeStructureTest {
    @Test
    void facadeRemainsStatelessAndFinal() {
        assertTrue(Modifier.isFinal(MudPhysics.class.getModifiers()));
        assertEquals(0, MudPhysics.class.getDeclaredFields().length);
    }

    @Test
    void hotPathOwnersStayOutsideTheFacade() {
        Set<String> facadeMethods = Arrays.stream(MudPhysics.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertFalse(facadeMethods.contains("findSablePlayerContact"));
        assertFalse(facadeMethods.contains("updatePartCoverage"));
        assertFalse(facadeMethods.contains("applyClientPlayerMovement"));
        assertFalse(facadeMethods.contains("findMudVolumeSnapshot"));
    }

    @Test
    void splitRuntimeOwnersRemainInTheMudDomain() {
        assertEquals("com.fish.mirebound.mud", MudPlayerPhysicsController.class.getPackageName());
        assertEquals("com.fish.mirebound.mud", MudContactResolver.class.getPackageName());
        assertEquals("com.fish.mirebound.mud", MudVolumeContactResolver.class.getPackageName());
        assertEquals("com.fish.mirebound.mud", MudPlayerMovement.class.getPackageName());
        assertEquals("com.fish.mirebound.mud", MudClientPhysics.class.getPackageName());
        assertEquals("com.fish.mirebound.mud", MudCoverageSampler.class.getPackageName());
        assertEquals("com.fish.mirebound.mud", MudPollutionSuppression.class.getPackageName());
        assertEquals("com.fish.mirebound.mud", TenderFleshEnclosureSystem.class.getPackageName());
        assertEquals("com.fish.mirebound.mud", MudSurfaceFeedback.class.getPackageName());
    }
}
