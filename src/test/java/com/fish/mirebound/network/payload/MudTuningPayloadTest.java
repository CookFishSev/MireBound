package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.MudTuningScope;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.tuning.MudTuningCapabilities;
import com.fish.mirebound.mud.tuning.MudTuningObjectId;
import com.fish.mirebound.mud.tuning.MudTuningObjectScanner;
import com.fish.mirebound.mud.tuning.MudTuningSelectionElement;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class MudTuningPayloadTest {
    private static final UUID SUB_LEVEL_ID = UUID.fromString("791567ca-780b-43f8-a1f3-52611378ff3a");
    private static final MudTuningAnchor FIRST = MudTuningAnchor.sable(
            SUB_LEVEL_ID, new BlockPos(20_501_120, 64, 20_503_184));
    private static final MudTuningAnchor SECOND = FIRST.withPos(FIRST.pos().offset(4, 2, 7));

    @Test
    void anchorDistinguishesWorldAndStableSableDomains() {
        MudTuningAnchor world = MudTuningAnchor.world(FIRST.pos());

        assertFalse(world.isSable());
        assertTrue(FIRST.isSable());
        assertTrue(FIRST.sameDomain(SECOND));
        assertFalse(FIRST.sameDomain(world));
    }

    @Test
    void selectionCodecPreservesAnchorsAndGroupedHighlights() {
        MudTuningSelectionPayload expected = new MudTuningSelectionPayload(
                true, FIRST, true, SECOND,
                new MudTuningSelectionPayload.SelectionSummary(120L, 72, 8, 12, 20, 8),
                List.of(
                        new MudTuningSelectionPayload.HighlightGroup(
                                MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE,
                                MudTuningAnchor.WORLD_SUB_LEVEL_ID, new long[] {1L, 2L},
                                new long[] {BlockPos.asLong(3, 4, 5)}, new byte[] {2}),
                        new MudTuningSelectionPayload.HighlightGroup(
                                MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE,
                                SUB_LEVEL_ID,
                                new long[] {FIRST.pos().asLong(), SECOND.pos().asLong()},
                                new long[] {FIRST.pos().asLong()}, new byte[] {1})));
        RegistryFriendlyByteBuf buffer = buffer();

        MudTuningSelectionPayload.STREAM_CODEC.encode(buffer, expected);
        MudTuningSelectionPayload actual = MudTuningSelectionPayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected.hasFirst(), actual.hasFirst());
        assertEquals(expected.first(), actual.first());
        assertEquals(expected.hasSecond(), actual.hasSecond());
        assertEquals(expected.second(), actual.second());
        assertEquals(expected.summary(), actual.summary());
        assertEquals(2, actual.highlightGroups().size());
        assertEquals(MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE,
                actual.highlightGroups().get(1).kind());
        assertEquals(SUB_LEVEL_ID, actual.highlightGroups().get(1).subLevelId());
        assertArrayEquals(expected.highlightGroups().get(1).positions(),
                actual.highlightGroups().get(1).positions());
        assertArrayEquals(expected.highlightGroups().get(0).positions(),
                actual.highlightGroups().get(0).positions());
        assertArrayEquals(expected.highlightGroups().get(0).edgeCorners(),
                actual.highlightGroups().get(0).edgeCorners());
        assertArrayEquals(expected.highlightGroups().get(0).edgeAxes(),
                actual.highlightGroups().get(0).edgeAxes());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void selectionCodecPreservesEmptyPositionGroups() {
        MudTuningSelectionPayload expected = new MudTuningSelectionPayload(
                false, MudTuningAnchor.WORLD_ORIGIN, false, MudTuningAnchor.WORLD_ORIGIN,
                MudTuningSelectionPayload.SelectionSummary.EMPTY,
                List.of(new MudTuningSelectionPayload.HighlightGroup(
                        MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE,
                        MudTuningAnchor.WORLD_SUB_LEVEL_ID, new long[0],
                        new long[] {BlockPos.asLong(8, 9, 10)}, new byte[] {2})));
        RegistryFriendlyByteBuf buffer = buffer();

        MudTuningSelectionPayload.STREAM_CODEC.encode(buffer, expected);
        MudTuningSelectionPayload actual = MudTuningSelectionPayload.STREAM_CODEC.decode(buffer);

        assertArrayEquals(expected.highlightGroups().getFirst().positions(),
                actual.highlightGroups().getFirst().positions());
        assertArrayEquals(expected.highlightGroups().getFirst().edgeCorners(),
                actual.highlightGroups().getFirst().edgeCorners());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void requestApplyAndSessionCodecsPreserveSableDomain() {
        RegistryFriendlyByteBuf requestBuffer = buffer();
        MudTuningRequestPayload request = new MudTuningRequestPayload(
                MudTuningRequestPayload.Action.OPEN_SINGLE, FIRST);
        MudTuningRequestPayload.STREAM_CODEC.encode(requestBuffer, request);
        assertEquals(request, MudTuningRequestPayload.STREAM_CODEC.decode(requestBuffer));
        requestBuffer.release();

        MudTuningSelectionNudgePayload nudge = new MudTuningSelectionNudgePayload(
                MudTuningSelectionElement.SECOND, Direction.WEST);
        RegistryFriendlyByteBuf nudgeBuffer = buffer();
        MudTuningSelectionNudgePayload.STREAM_CODEC.encode(nudgeBuffer, nudge);
        assertEquals(nudge,
                MudTuningSelectionNudgePayload.STREAM_CODEC.decode(nudgeBuffer));
        assertEquals(0, nudgeBuffer.readableBytes());
        nudgeBuffer.release();

        double[] values = new double[MudPhysicsParameter.COUNT];
        boolean[] changed = new boolean[MudPhysicsParameter.COUNT];
        values[MudPhysicsParameter.AUTO_STACK_FILL.ordinal()] = 1.0D;
        changed[MudPhysicsParameter.AUTO_STACK_FILL.ordinal()] = true;
        MudTuningObjectId nativeMud = MudTuningObjectId.nativeMedium(SinkingMedium.MUD);
        MudTuningApplyPayload apply = new MudTuningApplyPayload(
                MudTuningScope.RANGE, nativeMud, FIRST, SECOND, 1, 12,
                true, false, values, changed);
        RegistryFriendlyByteBuf applyBuffer = buffer();
        MudTuningApplyPayload.STREAM_CODEC.encode(applyBuffer, apply);
        MudTuningApplyPayload decodedApply = MudTuningApplyPayload.STREAM_CODEC.decode(applyBuffer);
        assertEquals(FIRST, decodedApply.first());
        assertEquals(SECOND, decodedApply.second());
        assertEquals(nativeMud, decodedApply.objectId());
        assertArrayEquals(values, decodedApply.values());
        assertArrayEquals(changed, decodedApply.changed());
        applyBuffer.release();

        AdaptiveMudActionPayload adaptive = new AdaptiveMudActionPayload(
                AdaptiveMudActionPayload.Action.CONVERT,
                MudTuningScope.RANGE, FIRST, SECOND, 13,
                ResourceLocation.withDefaultNamespace("stone"));
        RegistryFriendlyByteBuf adaptiveBuffer = buffer();
        AdaptiveMudActionPayload.STREAM_CODEC.encode(adaptiveBuffer, adaptive);
        assertEquals(adaptive, AdaptiveMudActionPayload.STREAM_CODEC.decode(adaptiveBuffer));
        adaptiveBuffer.release();

        MudTuningConversionUnlockPayload unlockRequest =
                new MudTuningConversionUnlockPayload(true);
        RegistryFriendlyByteBuf unlockRequestBuffer = buffer();
        MudTuningConversionUnlockPayload.STREAM_CODEC.encode(
                unlockRequestBuffer, unlockRequest);
        assertEquals(unlockRequest,
                MudTuningConversionUnlockPayload.STREAM_CODEC.decode(
                        unlockRequestBuffer));
        unlockRequestBuffer.release();

        MudTuningConversionSafetyPayload safetyState =
                new MudTuningConversionSafetyPayload(true, true, false);
        RegistryFriendlyByteBuf safetyStateBuffer = buffer();
        MudTuningConversionSafetyPayload.STREAM_CODEC.encode(
                safetyStateBuffer, safetyState);
        assertEquals(safetyState,
                MudTuningConversionSafetyPayload.STREAM_CODEC.decode(
                        safetyStateBuffer));
        safetyStateBuffer.release();

        MudTuningObjectId converted = MudTuningObjectId.convertedBlock(
                ResourceLocation.withDefaultNamespace("stone"));
        MudTuningSessionPayload.MediumProfile objectProfile =
                new MudTuningSessionPayload.MediumProfile(
                        converted, 7, true, false, 0, 16, false, 1,
                         MudTuningCapabilities.EDIT_PARAMETERS
                                 | MudTuningCapabilities.SABLE_SCOPE,
                         values, values.clone());
        MudTuningSessionPayload session = new MudTuningSessionPayload(
                MudTuningScope.RANGE, true, FIRST, SECOND, List.of(objectProfile));
        RegistryFriendlyByteBuf sessionBuffer = buffer();
        MudTuningSessionPayload.STREAM_CODEC.encode(sessionBuffer, session);
        MudTuningSessionPayload decodedSession =
                MudTuningSessionPayload.STREAM_CODEC.decode(sessionBuffer);
        assertEquals(FIRST, decodedSession.first());
        assertEquals(SECOND, decodedSession.second());
        assertEquals(converted, decodedSession.profiles().getFirst().objectId());
        assertEquals(7, decodedSession.profiles().getFirst().blockCount());
        assertEquals(0, sessionBuffer.readableBytes());
         sessionBuffer.release();
     }

    @Test
    void sessionCodecPalettesRepeatedProfilesInsteadOfRepeatingFullVectors() {
        double[] baseline = new double[MudPhysicsParameter.COUNT];
        double[] values = baseline.clone();
        values[MudPhysicsParameter.SURFACE_BUBBLE_RATE.ordinal()] = 0.75D;
        List<MudTuningSessionPayload.MediumProfile> profiles = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            profiles.add(new MudTuningSessionPayload.MediumProfile(
                    MudTuningObjectId.sourceBlock(ResourceLocation.fromNamespaceAndPath(
                            "palette_test", "block_" + index)),
                    1, false, false, 0, 16, false, 1,
                    MudTuningCapabilities.CONVERT, values, baseline));
        }
        MudTuningSessionPayload session = new MudTuningSessionPayload(
                MudTuningScope.RANGE, true, FIRST, SECOND, profiles);
        RegistryFriendlyByteBuf buffer = buffer();

        MudTuningSessionPayload.STREAM_CODEC.encode(buffer, session);
        int encodedBytes = buffer.readableBytes();
        MudTuningSessionPayload decoded = MudTuningSessionPayload.STREAM_CODEC.decode(buffer);

        assertEquals(128, decoded.profiles().size());
        assertArrayEquals(values, decoded.profiles().get(73).values());
        assertArrayEquals(baseline, decoded.profiles().get(73).resetValues());
        long legacyVectorBytes = 128L * MudPhysicsParameter.COUNT
                * (Double.BYTES * 2L + 1L);
        assertTrue(encodedBytes < legacyVectorBytes / 16L,
                "repeated profiles should be at least 16x smaller than the old vectors");
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void objectIdentityDistinguishesAllTuningDomains() {
        ResourceLocation stone = ResourceLocation.withDefaultNamespace("stone");
        assertFalse(MudTuningObjectId.nativeMedium(SinkingMedium.MUD).hasSourceBlock());
        assertTrue(MudTuningObjectId.sourceBlock(stone).hasSourceBlock());
        assertTrue(MudTuningObjectId.convertedBlock(stone).hasSourceBlock());
        assertFalse(MudTuningObjectId.adaptiveDefault().hasSourceBlock());
        assertFalse(MudTuningObjectId.sourceBlock(stone)
                .equals(MudTuningObjectId.convertedBlock(stone)));
    }

    @Test
    void objectIdentityCodecPreservesEveryKind() {
        List<MudTuningObjectId> ids = List.of(
                MudTuningObjectId.nativeMedium(SinkingMedium.TAR),
                MudTuningObjectId.sourceBlock(ResourceLocation.withDefaultNamespace("stone")),
                MudTuningObjectId.convertedBlock(ResourceLocation.withDefaultNamespace("dirt")),
                MudTuningObjectId.incompatibleBlock(
                        ResourceLocation.withDefaultNamespace("bedrock")),
                MudTuningObjectId.adaptiveDefault());
        RegistryFriendlyByteBuf buffer = buffer();

        for (MudTuningObjectId id : ids) {
            id.write(buffer);
        }
        for (MudTuningObjectId id : ids) {
            assertEquals(id, MudTuningObjectId.read(buffer));
        }
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void sableCapabilitiesKeepFlowVisibleButUnsupported() {
        MudTuningCapabilities capabilities = MudTuningObjectScanner.capabilitiesFor(
                MudTuningObjectId.convertedBlock(
                        ResourceLocation.withDefaultNamespace("stone")),
                true, null);

        assertTrue(capabilities.has(MudTuningCapabilities.EDIT_PARAMETERS));
        assertTrue(capabilities.has(MudTuningCapabilities.SABLE_SCOPE));
        assertFalse(capabilities.has(MudTuningCapabilities.FINITE_FLOW));
    }

    @Test
    void adaptiveObjectsNeverExposeFiniteFlow() {
        ResourceLocation stone = ResourceLocation.withDefaultNamespace("stone");

        MudTuningCapabilities converted = MudTuningObjectScanner.capabilitiesFor(
                MudTuningObjectId.convertedBlock(stone), false, null);
        MudTuningCapabilities defaults = MudTuningObjectScanner.capabilitiesFor(
                MudTuningObjectId.adaptiveDefault(), false, null);

        assertFalse(converted.has(MudTuningCapabilities.FINITE_FLOW));
        assertFalse(defaults.has(MudTuningCapabilities.FINITE_FLOW));
    }

    @Test
    void adaptiveProfileCodecPreservesAllTemplateValues() {
        double[] values = new double[MudPhysicsParameter.COUNT];
        values[MudPhysicsParameter.COVERAGE_ENABLED.ordinal()] = 1.0D;
        values[MudPhysicsParameter.TENTACLE_ENABLED.ordinal()] = 1.0D;
        values[MudPhysicsParameter.FLESH_ENABLED.ordinal()] = 1.0D;
        AdaptiveMudProfileSyncPayload expected = new AdaptiveMudProfileSyncPayload(values);
        RegistryFriendlyByteBuf buffer = buffer();

        AdaptiveMudProfileSyncPayload.STREAM_CODEC.encode(buffer, expected);
        AdaptiveMudProfileSyncPayload actual =
                AdaptiveMudProfileSyncPayload.STREAM_CODEC.decode(buffer);

        assertArrayEquals(values, actual.values());
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void adaptiveProfileRejectsInvalidOrAliasedValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveMudProfileSyncPayload(new double[0]));

        double[] nonFinite = new double[MudPhysicsParameter.COUNT];
        nonFinite[0] = Double.NaN;
        assertThrows(IllegalArgumentException.class,
                () -> new AdaptiveMudProfileSyncPayload(nonFinite));

        double[] values = new double[MudPhysicsParameter.COUNT];
        AdaptiveMudProfileSyncPayload payload = new AdaptiveMudProfileSyncPayload(values);
        values[0] = 1.0D;
        assertEquals(0.0D, payload.values()[0]);
        double[] exposed = payload.values();
        exposed[0] = 2.0D;
        assertEquals(0.0D, payload.values()[0]);
    }

    @Test
    void physicsProfileRejectsInvalidOrAliasedValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new MudPhysicsProfileSyncPayload(
                        SinkingMedium.MUD.id(), false, false, false,
                        BlockPos.ZERO, 0, 16,
                        new double[MudPhysicsParameter.COUNT - 1]));

        double[] nonFinite = new double[MudPhysicsParameter.COUNT];
        nonFinite[0] = Double.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class,
                () -> new MudPhysicsProfileSyncPayload(
                        SinkingMedium.MUD.id(), false, false, false,
                        BlockPos.ZERO, 0, 16, nonFinite));

        double[] values = new double[MudPhysicsParameter.COUNT];
        MudPhysicsProfileSyncPayload payload = new MudPhysicsProfileSyncPayload(
                SinkingMedium.MUD.id(), false, false, false,
                BlockPos.ZERO, 0, 16, values);
        values[0] = 1.0D;
        assertEquals(0.0D, payload.values()[0]);
        double[] exposed = payload.values();
        exposed[0] = 2.0D;
        assertEquals(0.0D, payload.values()[0]);
    }

    @Test
    void directWandActionsRemainAppendOnly() {
        assertEquals(8, MudTuningRequestPayload.Action.CLEAR_SELECTION.ordinal());
        assertEquals(13, MudTuningRequestPayload.Action.RESTORE_RANGE.ordinal());
    }

    @Test
    void globalSettingsCodecsPreserveRequestAndAuthoritativeValues() {
        MudTuningGlobalRequestPayload request = new MudTuningGlobalRequestPayload(
                true, 37, true, 45, 48.5D);
        RegistryFriendlyByteBuf requestBuffer = buffer();
        MudTuningGlobalRequestPayload.STREAM_CODEC.encode(requestBuffer, request);
        assertEquals(request,
                MudTuningGlobalRequestPayload.STREAM_CODEC.decode(requestBuffer));
        assertEquals(0, requestBuffer.readableBytes());
        requestBuffer.release();

        MudTuningGlobalSettingsPayload settings =
                new MudTuningGlobalSettingsPayload(37, true, 45, 48.5D, true);
        RegistryFriendlyByteBuf settingsBuffer = buffer();
        MudTuningGlobalSettingsPayload.STREAM_CODEC.encode(settingsBuffer, settings);
        assertEquals(settings,
                MudTuningGlobalSettingsPayload.STREAM_CODEC.decode(settingsBuffer));
        assertEquals(0, settingsBuffer.readableBytes());
        settingsBuffer.release();
    }

    @Test
    void tentacleWandActionCodecPreservesFloatingPointPlacement() {
        TentacleWandActionPayload expected = new TentacleWandActionPayload(
                TentacleWandActionPayload.Action.SUMMON, -1,
                12.375D, 71.625D, -8.25D, 37);
        RegistryFriendlyByteBuf buffer = buffer();

        TentacleWandActionPayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, TentacleWandActionPayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void tentacleWandActionCodecPreservesRemoveIntent() {
        TentacleWandActionPayload expected = new TentacleWandActionPayload(
                TentacleWandActionPayload.Action.REMOVE, 17,
                4.5D, 8.0D, -3.25D, 0);
        RegistryFriendlyByteBuf buffer = buffer();

        TentacleWandActionPayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, TentacleWandActionPayload.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void invalidWorldMutationActionsDecodeToNonExecutableSentinels() {
        RegistryFriendlyByteBuf adaptiveBuffer = buffer();
        adaptiveBuffer.writeVarInt(999);
        adaptiveBuffer.writeVarInt(MudTuningScope.SINGLE.ordinal());
        MudTuningAnchor.write(adaptiveBuffer, FIRST);
        MudTuningAnchor.write(adaptiveBuffer, FIRST);
        adaptiveBuffer.writeVarInt(SinkingMedium.MUD.id());
        adaptiveBuffer.writeResourceLocation(ResourceLocation.parse("minecraft:stone"));
        assertEquals(AdaptiveMudActionPayload.Action.INVALID,
                AdaptiveMudActionPayload.STREAM_CODEC.decode(adaptiveBuffer).action());
        adaptiveBuffer.release();

        RegistryFriendlyByteBuf tentacleBuffer = buffer();
        tentacleBuffer.writeVarInt(999);
        tentacleBuffer.writeVarInt(-1);
        tentacleBuffer.writeDouble(0.0D);
        tentacleBuffer.writeDouble(64.0D);
        tentacleBuffer.writeDouble(0.0D);
        tentacleBuffer.writeVarInt(25);
        assertEquals(TentacleWandActionPayload.Action.INVALID,
                TentacleWandActionPayload.STREAM_CODEC.decode(tentacleBuffer).action());
        tentacleBuffer.release();
    }

    @Test
    void wandBeamCodecPreservesPlayerHandTimeAndSableAnchor() {
        MudTuningWandBeamPayload expected = new MudTuningWandBeamPayload(
                42, SECOND, false, 987_654L);
        RegistryFriendlyByteBuf buffer = buffer();

        MudTuningWandBeamPayload.STREAM_CODEC.encode(buffer, expected);
        MudTuningWandBeamPayload actual =
                MudTuningWandBeamPayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    @Test
    void sculkClampCodecPreservesAdaptiveVisualSource() {
        SculkClampStatePayload expected = new SculkClampStatePayload(
                27, true, new Vec3(2.5D, 64.0D, -3.5D),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D),
                0x8A17_F05E_49C2_1307L,
                0.72F, 0.61F, 96.0F,
                12, 16, 10, 8, 38, 80);
        RegistryFriendlyByteBuf buffer = buffer();

        SculkClampStatePayload.STREAM_CODEC.encode(buffer, expected);
        SculkClampStatePayload actual =
                SculkClampStatePayload.STREAM_CODEC.decode(buffer);

        assertEquals(expected, actual);
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }
}
