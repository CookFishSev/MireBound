package com.fish.mirebound.client.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.client.tuning.MudTuningSessionModel.ObjectFilter;
import com.fish.mirebound.mud.MudBlockVariant;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.MudSinkingDepthControl;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.MudTuningScope;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.tuning.MudTuningCapabilities;
import com.fish.mirebound.mud.tuning.MudTuningObjectId;
import com.fish.mirebound.mud.tuning.MudTuningSelectionElement;
import com.fish.mirebound.network.payload.MudTuningSessionPayload;
import java.util.List;
import org.junit.jupiter.api.Test;

class MudTuningClientModelsTest {
    @Test
    void modeCycleWrapsInBothDirections() {
        assertEquals(MudTuningWandMode.SINGLE, MudTuningWandMode.RANGE.cycle(1));
        assertEquals(MudTuningWandMode.SETTINGS, MudTuningWandMode.RANGE.cycle(-1));
        assertEquals(MudTuningWandMode.GENERATION,
                MudTuningWandMode.SUMMON.cycle(1));
        assertEquals(MudTuningWandMode.SETTINGS,
                MudTuningWandMode.GENERATION.cycle(1));
        assertEquals(MudTuningWandMode.RANGE,
                MudTuningWandMode.SETTINGS.cycle(1));
        assertEquals(4, MudTuningWandMode.SETTINGS.ordinal());
        assertEquals(5, MudTuningWandMode.GENERATION.ordinal());
    }

    @Test
    void selectionAdjustmentCyclesPointsThenWholeRange() {
        assertEquals(MudTuningSelectionElement.NONE,
                MudTuningSelectionElement.next(
                        MudTuningSelectionElement.NONE, false, false));
        assertEquals(MudTuningSelectionElement.FIRST,
                MudTuningSelectionElement.next(
                        MudTuningSelectionElement.NONE, true, false));
        assertEquals(MudTuningSelectionElement.FIRST,
                MudTuningSelectionElement.next(
                        MudTuningSelectionElement.NONE, true, true));
        assertEquals(MudTuningSelectionElement.SECOND,
                MudTuningSelectionElement.next(
                        MudTuningSelectionElement.FIRST, true, true));
        assertEquals(MudTuningSelectionElement.BODY,
                MudTuningSelectionElement.next(
                        MudTuningSelectionElement.SECOND, true, true));
        assertEquals(MudTuningSelectionElement.FIRST,
                MudTuningSelectionElement.next(
                        MudTuningSelectionElement.BODY, true, true));
    }

    @Test
    void compactLayoutKeepsPositiveContentAtMinimumTestViewport() {
        MudTuningScreenLayout layout = MudTuningScreenLayout.calculate(320, 240);
        assertTrue(layout.compact());
        assertTrue(layout.sidebarWidth() >= 28);
        assertTrue(layout.visibleRows() >= 1);
        assertTrue(layout.contentBottom() > layout.contentTop());
        assertEquals(4, layout.visiblePageCount(5));
        assertEquals(3, layout.visiblePageCount(3));
    }

    @Test
    void regularLayoutSeparatesObjectFiltersFromObjectTabs() {
        MudTuningScreenLayout layout = MudTuningScreenLayout.calculate(960, 540);
        int filterBottom = 27 + 18;
        int objectTabsTop = layout.headerHeight() - 22 - 2;

        assertTrue(filterBottom < objectTabsTop);
    }

    @Test
    void tentacleLayoutUsesTheFullWidthWithoutMudNavigation() {
        MudTuningScreenLayout layout = MudTuningScreenLayout.calculateTentacle(320, 240);

        assertEquals(0, layout.sidebarWidth());
        assertEquals(0, layout.contentLeft());
        assertTrue(layout.contentBottom() > layout.contentTop());
        assertTrue(layout.visiblePageCount(5) >= 4);
    }

    @Test
    void objectFiltersSeparateAllFourObjectKinds() {
        MudTuningSessionModel.ObjectModel nativeObject = model(
                MudTuningObjectId.nativeMedium(SinkingMedium.MUD),
                MudTuningCapabilities.EDIT_PARAMETERS).objects().getFirst();
        MudTuningSessionModel.ObjectModel sourceObject = model(
                MudTuningObjectId.sourceBlock(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("stone")),
                MudTuningCapabilities.CONVERT).objects().getFirst();
        MudTuningSessionModel.ObjectModel convertedObject = model(
                MudTuningObjectId.convertedBlock(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("stone")),
                MudTuningCapabilities.EDIT_PARAMETERS).objects().getFirst();
        MudTuningSessionModel.ObjectModel incompatibleObject = model(
                MudTuningObjectId.incompatibleBlock(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("bedrock")),
                0).objects().getFirst();

        assertTrue(ObjectFilter.NATIVE.accepts(nativeObject));
        assertTrue(ObjectFilter.SOURCE.accepts(sourceObject));
        assertTrue(ObjectFilter.CONVERTED.accepts(convertedObject));
        assertTrue(ObjectFilter.INCOMPATIBLE.accepts(incompatibleObject));
        assertFalse(ObjectFilter.NATIVE.accepts(sourceObject));
        assertFalse(ObjectFilter.SOURCE.accepts(convertedObject));
        assertFalse(ObjectFilter.CONVERTED.accepts(incompatibleObject));
    }

    @Test
    void hudAnchorsKeepTheExistingVerticalBaseline() {
        int controlsHeight = 77;

        assertEquals(108, MudTuningHudLayout.anchoredModeY(240, 54));
        assertEquals(128, MudTuningHudLayout.anchoredModeY(260, 54));
        assertTrue(controlsHeight > 0);
    }

    @Test
    void hudGroupsUseContinuousPositionsBetweenTheScreenEdges() {
        int width = 320;
        int controlsWidth = 96;

        assertEquals(6, MudTuningHudLayout.horizontalPosition(
                0.0D, width, controlsWidth));
        assertEquals(59, MudTuningHudLayout.horizontalPosition(
                0.25D, width, controlsWidth));
        assertEquals(112, MudTuningHudLayout.horizontalPosition(
                0.5D, width, controlsWidth));
        assertEquals(218, MudTuningHudLayout.horizontalPosition(
                1.0D, width, controlsWidth));
    }

    @Test
    void hudBoundsPreventOverlapWithTheConfiguredGap() {
        MudTuningHudLayout.HudBounds left = new MudTuningHudLayout.HudBounds(
                10, 10, 80, 30, 80, 30, 1.0D);
        MudTuningHudLayout.HudBounds touchingGap = new MudTuningHudLayout.HudBounds(
                92, 10, 80, 30, 80, 30, 1.0D);
        MudTuningHudLayout.HudBounds separated = new MudTuningHudLayout.HudBounds(
                94, 10, 80, 30, 80, 30, 1.0D);

        assertTrue(left.overlaps(touchingGap, 3));
        assertFalse(left.overlaps(separated, 3));
        assertEquals(0.80D, MudTuningHudElement.CENTER.defaultY(), 0.0D);
        assertEquals(0.0D, MudTuningHudElement.CONTROLS.defaultX(), 0.0D);
        assertEquals(1.0D, MudTuningHudElement.CONTROLS.defaultY(), 0.0D);
    }

    @Test
    void controlHintsUseTheThreeHorizontalAlignmentRegions() {
        assertEquals(MudTuningWandHud.ControlsAlignment.LEFT,
                MudTuningWandHud.controlsAlignment(0.20D));
        assertEquals(MudTuningWandHud.ControlsAlignment.CENTER,
                MudTuningWandHud.controlsAlignment(0.50D));
        assertEquals(MudTuningWandHud.ControlsAlignment.RIGHT,
                MudTuningWandHud.controlsAlignment(0.80D));
        assertEquals(MudTuningWandHud.ControlsAlignment.CENTER,
                MudTuningWandHud.controlsAlignment(1.0D / 3.0D));
        assertEquals(MudTuningWandHud.ControlsAlignment.RIGHT,
                MudTuningWandHud.controlsAlignment(2.0D / 3.0D));
    }

    @Test
    void hudColorsAcceptOnlySixDigitRgbValuesAndHaveStableFormatting() {
        assertEquals(0x12ABEF,
                MudTuningClientSettings.parseHexColor("#12abef", -1));
        assertEquals("12ABEF", MudTuningClientSettings.formatHexColor(0x12ABEF));
        assertEquals(0x13579B,
                MudTuningClientSettings.parseHexColor("not-a-color", 0x13579B));
        assertEquals(0x13579B,
                MudTuningClientSettings.parseHexColor("12345", 0x13579B));
    }

    @Test
    void sliderInputUsesTheSameStepAndBoundsAsTheRenderedSlider() {
        assertEquals(0.0D,
                MudTuningSlider.snapValue(0.0D, 1.0D, 0.1D,
                        Double.NEGATIVE_INFINITY));
        assertEquals(0.3D,
                MudTuningSlider.snapValue(0.0D, 1.0D, 0.1D, 0.26D),
                1.0E-9D);
        assertEquals(0.0D,
                MudTuningSlider.snapValue(0.0D, 1.0D, 0.1D,
                        Double.POSITIVE_INFINITY));
    }

    @Test
    void modeCellsGrowInsideStableSquareTracks() {
        int trackSize = 56;

        assertEquals(54, MudTuningHudLayout.modeCellSize(trackSize, true));
        assertEquals(48, MudTuningHudLayout.modeCellSize(trackSize, false));
        assertEquals(30, MudTuningHudLayout.modeCellSize(36, false));
        assertTrue(MudTuningHudLayout.modeCellSize(trackSize, true) <= trackSize);
    }

    @Test
    void adaptiveProfilesExcludeLivingSlimeAndInheritMiningByDefault() {
        assertFalse(MudPhysicsParameter.SLIME_VERTICAL_SPRING.appliesToAdaptive());
        assertFalse(MudPhysicsParameter.AUTO_STACK_FILL.appliesToAdaptive());
        assertFalse(MudPhysicsParameter.FLOW_ENABLED.appliesToAdaptive());
        assertFalse(MudPhysicsParameter.FLOW_INTERVAL_TICKS.appliesToAdaptive());
        assertTrue(MudPhysicsParameter.ASSIMILATION_ENABLED.appliesToAdaptive());
        double[] defaults = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        assertEquals(0.0D,
                defaults[MudPhysicsParameter.HARVEST_OVERRIDE_SOURCE_ENABLED.ordinal()]);
    }

    @Test
    void dedicatedTentacleSettingsHideTheirOwnTemplateSwitch() {
        MudTuningSessionModel.ObjectModel tentacle = model(
                MudTuningObjectId.tentacle(),
                MudTuningCapabilities.EDIT_PARAMETERS).objects().getFirst();

        assertFalse(tentacle.parameters(MudTuningNavigation.Page.TENTACLE_CORE)
                .contains(MudPhysicsParameter.TENTACLE_ENABLED));
    }

    @Test
    void templateSwitchesLeadTheirParameterPages() {
        MudTuningSessionModel.ObjectModel mud = model(
                MudTuningObjectId.nativeMedium(SinkingMedium.MUD),
                MudTuningCapabilities.EDIT_PARAMETERS).objects().getFirst();

        assertEquals(MudPhysicsParameter.COVERAGE_ENABLED,
                mud.parameters(MudTuningNavigation.Page.COVERAGE_CORE).getFirst());
        assertEquals(MudPhysicsParameter.ASSIMILATION_ENABLED,
                mud.parameters(MudTuningNavigation.Page.ASSIMILATION_CORE).getFirst());
        assertEquals(MudPhysicsParameter.ERUPTION_CONTINUOUS_ENABLED,
                mud.parameters(MudTuningNavigation.Page.ERUPTION_CONTINUOUS).getFirst());
        assertEquals(MudPhysicsParameter.ERUPTION_SURGES_ENABLED,
                mud.parameters(MudTuningNavigation.Page.ERUPTION_SURGES).getFirst());
        assertFalse(mud.parameters(MudTuningNavigation.Page.ERUPTION_SPAWNING)
                .contains(MudPhysicsParameter.ERUPTION_MAX_ACTIVE));
        assertTrue(mud.parameters(MudTuningNavigation.Page.ERUPTION_CONTINUOUS)
                .contains(MudPhysicsParameter.ERUPTION_FLOW_PARTICLE_LIFETIME_TICKS));
    }

    @Test
    void gravityToggleAppearsImmediatelyAboveFiniteFlow() {
        MudTuningSessionModel.ObjectModel mud = model(
                MudTuningObjectId.nativeMedium(SinkingMedium.MUD),
                MudTuningCapabilities.EDIT_PARAMETERS).objects().getFirst();
        List<MudPhysicsParameter> parameters =
                mud.parameters(MudTuningNavigation.Page.BASIC_FLOW);

        assertEquals(MudPhysicsParameter.GRAVITY_FALLING_ENABLED,
                parameters.get(0));
        assertEquals(MudPhysicsParameter.FLOW_ENABLED, parameters.get(1));
    }

    @Test
    void editFlagsClearWhenAValueOrShapeReturnsToServerState() {
        double[] baseline = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        MudTuningSessionModel model = model(baseline, baseline);
        MudTuningSessionModel.ObjectModel object = model.objects().getFirst();
        MudPhysicsParameter parameter = MudPhysicsParameter.SURFACE_BUBBLE_RATE;

        object.set(parameter, baseline[parameter.ordinal()] + parameter.step());
        assertTrue(object.changed()[parameter.ordinal()]);
        object.reset(parameter);
        assertFalse(object.changed()[parameter.ordinal()]);

        object.setBlockHeight(12);
        assertTrue(object.shapeChanged());
        object.resetBlockHeight();
        assertFalse(object.shapeChanged());
    }

    @Test
    void resettingAnExistingLocalValueStillProducesARealEdit() {
        double[] baseline = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        double[] local = baseline.clone();
        MudPhysicsParameter parameter = MudPhysicsParameter.SURFACE_BUBBLE_RATE;
        local[parameter.ordinal()] += parameter.step();
        MudTuningSessionModel.ObjectModel object = model(local, baseline).objects().getFirst();

        assertTrue(object.differsFromBaseline(parameter));
        object.reset(parameter);

        assertFalse(object.differsFromBaseline(parameter));
        assertTrue(object.changed()[parameter.ordinal()]);
    }

    @Test
    void maximumSinkingDepthChangesOnlyTheSimpleConfiguration() {
        double[] baseline = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        MudTuningSessionModel.ObjectModel object = model(baseline, baseline)
                .objects().getFirst();
        double factor = baseline[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()];
        double margin = baseline[MudPhysicsParameter.COLUMN_MARGIN.ordinal()];

        object.setMaximumSinkingDepth(0.65D);

        assertEquals(0.65D, object.maximumSinkingDepth(), 1.0E-9D);
        assertEquals(0.65D,
                object.values()[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()], 1.0E-9D);
        assertEquals(factor,
                object.values()[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()], 1.0E-9D);
        assertEquals(margin,
                object.values()[MudPhysicsParameter.COLUMN_MARGIN.ordinal()], 1.0E-9D);
        assertTrue(object.changed()[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()]);
        assertFalse(object.changed()[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()]);
        assertFalse(object.changed()[MudPhysicsParameter.COLUMN_MARGIN.ordinal()]);
    }

    @Test
    void simpleNaturalDepthIsBoundedByItsIndependentMaximum() {
        double[] baseline = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        MudTuningSessionModel.ObjectModel object = model(baseline, baseline)
                .objects().getFirst();

        object.setMaximumSinkingDepth(0.70D);
        object.setNaturalSinkingDepth(0.55D);
        assertEquals(0.55D, object.naturalSinkingDepth(), 1.0E-9D);

        object.setNaturalSinkingDepth(0.90D);
        assertEquals(0.70D, object.naturalSinkingDepth(), 1.0E-9D);

        object.setMaximumSinkingDepth(0.40D);
        assertEquals(0.40D, object.maximumSinkingDepth(), 1.0E-9D);
        assertEquals(0.40D, object.naturalSinkingDepth(), 1.0E-9D);
        assertTrue(object.changed()[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()]);
        assertTrue(object.changed()[MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal()]);
    }

    @Test
    void simpleModeRejectsDirectChildParameterEdits() {
        double[] baseline = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        MudTuningSessionModel.ObjectModel object = model(baseline, baseline)
                .objects().getFirst();
        double factor = object.values()[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()];
        double margin = object.values()[MudPhysicsParameter.COLUMN_MARGIN.ordinal()];

        object.set(MudPhysicsParameter.MAX_DEPTH_FACTOR, 0.25D);
        object.set(MudPhysicsParameter.COLUMN_MARGIN, 0.20D);

        assertEquals(factor,
                object.values()[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()], 1.0E-9D);
        assertEquals(margin,
                object.values()[MudPhysicsParameter.COLUMN_MARGIN.ordinal()], 1.0E-9D);
    }

    @Test
    void advancedModeKeepsChildParametersIndependent() {
        double[] baseline = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        MudTuningSessionModel.ObjectModel object = model(baseline, baseline)
                .objects().getFirst();
        object.setDepthControlMode(MudSinkingDepthControl.Mode.ADVANCED);
        double margin = object.values()[MudPhysicsParameter.COLUMN_MARGIN.ordinal()];

        object.set(MudPhysicsParameter.MAX_DEPTH_FACTOR, 0.25D);

        assertEquals(0.25D,
                object.values()[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()], 1.0E-9D);
        assertEquals(margin,
                object.values()[MudPhysicsParameter.COLUMN_MARGIN.ordinal()], 1.0E-9D);
        assertEquals(0.45D, object.maximumSinkingDepth(), 1.0E-9D);
    }

    @Test
    void simpleDepthResetRestoresOnlyItsBaselineValue() {
        double[] baseline = MudPhysicsProfiles.defaultValues(SinkingMedium.SOFT_QUICKSAND);
        MudTuningSessionModel.ObjectModel object = model(baseline, baseline)
                .objects().getFirst();
        double factor = baseline[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()];
        double margin = baseline[MudPhysicsParameter.COLUMN_MARGIN.ordinal()];

        object.setMaximumSinkingDepth(0.37D);
        object.resetMaximumSinkingDepth();

        assertEquals(baseline[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()],
                object.values()[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()], 1.0E-9D);
        assertEquals(factor,
                object.values()[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()], 1.0E-9D);
        assertEquals(margin,
                object.values()[MudPhysicsParameter.COLUMN_MARGIN.ordinal()], 1.0E-9D);
        assertFalse(object.maximumSinkingDepthDiffersFromBaseline());
    }

    @Test
    void switchingModesPreservesBothDepthConfigurations() {
        double[] baseline = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        MudTuningSessionModel.ObjectModel object = model(baseline, baseline)
                .objects().getFirst();
        object.setMaximumSinkingDepth(0.73D);
        object.setDepthControlMode(MudSinkingDepthControl.Mode.ADVANCED);
        object.set(MudPhysicsParameter.MAX_DEPTH_FACTOR, 0.25D);
        object.set(MudPhysicsParameter.COLUMN_MARGIN, 0.20D);

        assertEquals(0.45D, object.maximumSinkingDepth(), 1.0E-9D);

        object.setDepthControlMode(MudSinkingDepthControl.Mode.SIMPLE);

        assertEquals(0.73D, object.maximumSinkingDepth(), 1.0E-9D);
        assertEquals(0.25D,
                object.values()[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()], 1.0E-9D);
        assertEquals(0.20D,
                object.values()[MudPhysicsParameter.COLUMN_MARGIN.ordinal()], 1.0E-9D);
    }

    @Test
    void sliderSnappingPreservesExactEndpoints() {
        assertEquals(0.02D, MudTuningSlider.snap(0.02D, 1.03D, 0.10D, 0.0D), 0.0D);
        assertEquals(1.03D, MudTuningSlider.snap(0.02D, 1.03D, 0.10D, 1.0D), 0.0D);
        assertEquals(0.52D, MudTuningSlider.snap(0.02D, 1.03D, 0.10D, 0.50D), 1.0E-9D);
    }

    @Test
    void authoritativeRefreshRetainsPendingSimpleDepthEdit() {
        double[] baseline = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        MudTuningObjectId id = MudTuningObjectId.nativeMedium(SinkingMedium.MUD);
        int capabilities = MudTuningCapabilities.EDIT_PARAMETERS;
        MudTuningSessionModel model = model(id, capabilities, baseline, baseline);
        model.objects().getFirst().setMaximumSinkingDepth(0.62D);

        model.accept(payload(id, capabilities, baseline, baseline), false);

        MudTuningSessionModel.ObjectModel refreshed = model.objects().getFirst();
        assertEquals(0.62D, refreshed.maximumSinkingDepth(), 1.0E-9D);
        assertTrue(refreshed.changed()[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()]);
    }

    @Test
    void blockHeightSelectsTheMatchingShapeVariant() {
        double[] baseline = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        MudTuningSessionModel.ObjectModel object = model(baseline, baseline)
                .objects().getFirst();

        object.setBlockHeight(9);
        assertEquals(MudBlockVariant.HEIGHT.ordinal(), object.blockVariant());

        object.setBlockHeight(16);
        assertEquals(MudBlockVariant.DEFAULT.ordinal(), object.blockVariant());
    }

    private static MudTuningSessionModel model(double[] values, double[] baseline) {
        return model(MudTuningObjectId.nativeMedium(SinkingMedium.MUD),
                MudTuningCapabilities.EDIT_PARAMETERS | MudTuningCapabilities.EDIT_SHAPE,
                values, baseline);
    }

    private static MudTuningSessionModel model(MudTuningObjectId id, int capabilities) {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
        return model(id, capabilities, values, values);
    }

    private static MudTuningSessionModel model(MudTuningObjectId id, int capabilities,
            double[] values, double[] baseline) {
        return new MudTuningSessionModel(payload(id, capabilities, values, baseline));
    }

    private static MudTuningSessionPayload payload(MudTuningObjectId id, int capabilities,
            double[] values, double[] baseline) {
        MudTuningSessionPayload.MediumProfile profile =
                new MudTuningSessionPayload.MediumProfile(
                        id,
                        1, false, false, 0, 16, false,
                        0,
                        capabilities,
                        values.clone(), baseline.clone());
        return new MudTuningSessionPayload(
                MudTuningScope.SINGLE, true,
                MudTuningAnchor.world(net.minecraft.core.BlockPos.ZERO),
                MudTuningAnchor.world(net.minecraft.core.BlockPos.ZERO),
                List.of(profile));
    }
}
