package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.common.util.TriState;
import org.junit.jupiter.api.Test;

class AdaptiveMudModelsTest {
    @Test
    void emittingSourceDisablesDefaultAmbientOcclusion() {
        assertEquals(TriState.FALSE, AdaptiveMudModels.sourceAmbientOcclusion(
                TriState.DEFAULT, 15));
    }

    @Test
    void nonEmittingSourceKeepsDefaultAmbientOcclusion() {
        assertEquals(TriState.DEFAULT, AdaptiveMudModels.sourceAmbientOcclusion(
                TriState.DEFAULT, 0));
    }

    @Test
    void explicitSourcePreferenceIsPreserved() {
        assertEquals(TriState.TRUE, AdaptiveMudModels.sourceAmbientOcclusion(
                TriState.TRUE, 15));
        assertEquals(TriState.FALSE, AdaptiveMudModels.sourceAmbientOcclusion(
                TriState.FALSE, 0));
    }

    @Test
    void sourceRequestedCullingHidesCoveredInterface() {
        assertTrue(AdaptiveMudModels.sourceStatesHideSharedFace(
                true, false, false, true, true));
    }

    @Test
    void sourcePreservedInterfaceRemainsVisible() {
        assertFalse(AdaptiveMudModels.sourceStatesHideSharedFace(
                true, false, false, false, true));
    }

    @Test
    void incompleteCoverageKeepsInterfaceVisible() {
        assertFalse(AdaptiveMudModels.sourceStatesHideSharedFace(
                true, false, false, true, false));
    }

    @Test
    void transparentSourceKeepsBoundaryAgainstDifferentBlock() {
        assertTrue(AdaptiveMudModels.sourceStatesNeedVisibleInterface(
                false, false, true));
        assertFalse(AdaptiveMudModels.sourceStatesHideSharedFace(
                false, false, true, true, true));
    }

    @Test
    void identicalTransparentSourceStillCullsItsInternalFace() {
        assertFalse(AdaptiveMudModels.sourceStatesNeedVisibleInterface(
                true, false, false));
        assertTrue(AdaptiveMudModels.sourceStatesHideSharedFace(
                true, false, false, true, true));
    }

    @Test
    void differentOpaqueSourcesMayStillCullCoveredInterface() {
        assertFalse(AdaptiveMudModels.sourceStatesNeedVisibleInterface(
                false, true, true));
        assertTrue(AdaptiveMudModels.sourceStatesHideSharedFace(
                false, true, true, true, true));
    }

    @Test
    void crossedPlaneModelsDoNotEnableDeformationByDefault() {
        assertFalse(AdaptiveMudModels.supportsModelDeformation(
                0.0F, 1.0F, false));
        assertFalse(AdaptiveMudModels.normalsSpanVolume(
                new float[] {1.0F, 0.0F, 1.0F},
                new float[] {-1.0F, 0.0F, -1.0F},
                new float[] {1.0F, 0.0F, -1.0F},
                new float[] {-1.0F, 0.0F, 1.0F}));
    }

    @Test
    void closedSlopedModelsEnableDeformationWithoutAHorizontalTop() {
        assertTrue(AdaptiveMudModels.normalsSpanVolume(
                new float[] {1.0F, 0.0F, 0.0F},
                new float[] {0.0F, 0.0F, 1.0F},
                new float[] {0.0F, 1.0F, 1.0F}));
        assertTrue(AdaptiveMudModels.supportsModelDeformation(
                0.0F, 1.0F, true));
    }

    @Test
    void volumetricModelsUseTheirActualVerticalBounds() {
        assertTrue(AdaptiveMudModels.supportsModelDeformation(
                0.25F, 0.75F, true));
        assertEquals(0.0F, AdaptiveMudModels.compressedModelCoordinate(
                0.25F, 0.25F, 0.5F), 1.0E-6F);
        assertEquals(0.25F, AdaptiveMudModels.compressedModelCoordinate(
                0.75F, 0.25F, 0.5F), 1.0E-6F);
    }

    @Test
    void fullScaleRaisedModelsAreStillMovedOntoTheirSupportSurface() {
        assertTrue(AdaptiveMudModels.requiresModelTransform(
                1.0F, Direction.UP, 0.5F));
        assertEquals(0.0F, AdaptiveMudModels.compressedModelCoordinate(
                0.5F, 0.5F, 1.0F), 1.0E-6F);
        assertEquals(0.5F, AdaptiveMudModels.compressedModelCoordinate(
                1.0F, 0.5F, 1.0F), 1.0E-6F);
    }

    @Test
    void ordinaryFullHeightModelsAvoidTheTransformPath() {
        assertFalse(AdaptiveMudModels.requiresModelTransform(
                1.0F, Direction.UP, 0.0F));
    }
}
