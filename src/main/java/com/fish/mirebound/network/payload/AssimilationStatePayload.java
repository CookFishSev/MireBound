package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.assimilation.AssimilationQteAction;
import com.fish.mirebound.assimilation.AssimilationStage;
import com.fish.mirebound.assimilation.AssimilationTracePattern;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public record AssimilationStatePayload(
        int entityId,
        AssimilationStage stage,
        float progress,
        float shellIntegrity,
        int restoringTicks,
        double anchorX,
        double anchorY,
        double anchorZ,
        float frozenYaw,
        float frozenPitch,
        float bodyPitch,
        float bodyRoll,
        float frozenWalkPosition,
        float frozenWalkSpeed,
        int patternSeed,
        int mediumId,
        byte[] contributions,
        byte[] visualPaletteEntries,
        long[] visualSources,
        byte[] revealedCells,
        int qteCell,
        int qteButton,
        AssimilationQteAction qteAction,
        int qteRapidClicks,
        int qteTraceProgress,
        int qteTicksRemaining,
        int qteStreak,
        int qteSequence,
        boolean partialPurgeActive,
        float partialPurgeZoneStart,
        float partialPurgeZoneEnd,
        float partialPurgeCursor,
        boolean partialPurgeCursorForward,
        int partialPurgeCursorOneWayTicks,
        int partialPurgeCooldownTicks,
        byte partialPurgeResult,
        int partialPurgeResultTicks,
        int partialPurgeRound,
        AssimilationProfile profile) implements CustomPacketPayload {
    private static final int MAX_REVEAL_BYTES = (MudSurfaceLayout.CELL_COUNT + 7) / 8;
    public static final Type<AssimilationStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "assimilation_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssimilationStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AssimilationStatePayload decode(RegistryFriendlyByteBuf buffer) {
                    int entityId = buffer.readVarInt();
                    AssimilationStage stage = AssimilationStage.byId(buffer.readUnsignedByte());
                    float progress = buffer.readUnsignedShort() / 1000.0F;
                    float shell = buffer.readUnsignedShort() / 1000.0F;
                    int restoring = buffer.readVarInt();
                    double x = buffer.readDouble();
                    double y = buffer.readDouble();
                    double z = buffer.readDouble();
                    float yaw = buffer.readFloat();
                    float pitch = buffer.readFloat();
                    float bodyPitch = buffer.readFloat();
                    float bodyRoll = buffer.readFloat();
                    float walkPosition = buffer.readFloat();
                    float walkSpeed = buffer.readFloat();
                    int patternSeed = buffer.readInt();
                    int mediumId = buffer.readUnsignedByte();
                    byte[] contributions = new byte[SinkingMedium.COUNT];
                    int contributionCount = buffer.readVarInt();
                    if (contributionCount < 0 || contributionCount > SinkingMedium.COUNT) {
                        throw new IllegalArgumentException(
                                "Invalid assimilation contribution count " + contributionCount);
                    }
                    for (int i = 0; i < contributionCount; i++) {
                        int id = buffer.readUnsignedByte();
                        int strength = buffer.readUnsignedByte();
                        if (id < SinkingMedium.COUNT) {
                            contributions[id] = (byte) strength;
                        }
                    }
                    int visualCount = buffer.readVarInt();
                    if (visualCount < 0 || visualCount > MudVisualPalette.MAX_ENTRIES) {
                        throw new IllegalArgumentException(
                                "Invalid assimilation visual count " + visualCount);
                    }
                    byte[] visualEntries = new byte[visualCount * 2];
                    long[] visualSources = new long[visualCount];
                    for (int index = 0; index < visualCount; index++) {
                        visualEntries[index * 2] = buffer.readByte();
                        visualEntries[index * 2 + 1] = buffer.readByte();
                        visualSources[index] = buffer.readLong();
                    }
                    byte[] revealed = buffer.readByteArray(MAX_REVEAL_BYTES);
                    int qteCell = buffer.readVarInt() - 1;
                    int qteButton = buffer.readUnsignedByte();
                    AssimilationQteAction qteAction = AssimilationQteAction.byId(
                            buffer.readUnsignedByte());
                    int qteRapidClicks = buffer.readUnsignedByte();
                    int qteTraceProgress = buffer.readUnsignedByte();
                    int qteTicksRemaining = buffer.readVarInt();
                    int qteStreak = buffer.readVarInt();
                    int qteSequence = buffer.readVarInt();
                    boolean partialPurgeActive = buffer.readBoolean();
                    float partialPurgeZoneStart = buffer.readUnsignedShort() / 1000.0F;
                    float partialPurgeZoneEnd = buffer.readUnsignedShort() / 1000.0F;
                    float partialPurgeCursor = buffer.readUnsignedShort() / 1000.0F;
                    boolean partialPurgeCursorForward = buffer.readBoolean();
                    int partialPurgeCursorOneWayTicks = buffer.readVarInt();
                    int partialPurgeCooldownTicks = buffer.readVarInt();
                    byte partialPurgeResult = buffer.readByte();
                    int partialPurgeResultTicks = buffer.readVarInt();
                    int partialPurgeRound = buffer.readVarInt();
                    AssimilationProfile profile = buffer.readBoolean() ? readProfile(buffer) : null;
                    return new AssimilationStatePayload(entityId, stage, progress, shell, restoring,
                            x, y, z, yaw, pitch, bodyPitch, bodyRoll,
                            walkPosition, walkSpeed, patternSeed,
                            mediumId, contributions, visualEntries, visualSources,
                            revealed, qteCell, qteButton,
                            qteAction, qteRapidClicks, qteTraceProgress,
                            qteTicksRemaining, qteStreak, qteSequence,
                            partialPurgeActive, partialPurgeZoneStart,
                            partialPurgeZoneEnd, partialPurgeCursor,
                            partialPurgeCursorForward, partialPurgeCursorOneWayTicks,
                            partialPurgeCooldownTicks, partialPurgeResult,
                            partialPurgeResultTicks, partialPurgeRound, profile);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, AssimilationStatePayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeByte(payload.stage().ordinal());
                    buffer.writeShort(Math.round(payload.progress() * 1000.0F));
                    buffer.writeShort(Math.round(payload.shellIntegrity() * 1000.0F));
                    buffer.writeVarInt(payload.restoringTicks());
                    buffer.writeDouble(payload.anchorX());
                    buffer.writeDouble(payload.anchorY());
                    buffer.writeDouble(payload.anchorZ());
                    buffer.writeFloat(payload.frozenYaw());
                    buffer.writeFloat(payload.frozenPitch());
                    buffer.writeFloat(payload.bodyPitch());
                    buffer.writeFloat(payload.bodyRoll());
                    buffer.writeFloat(payload.frozenWalkPosition());
                    buffer.writeFloat(payload.frozenWalkSpeed());
                    buffer.writeInt(payload.patternSeed());
                    buffer.writeByte(payload.mediumId());
                    int contributionCount = 0;
                    for (byte contribution : payload.contributions()) {
                        contributionCount += (contribution & 0xFF) == 0 ? 0 : 1;
                    }
                    buffer.writeVarInt(contributionCount);
                    for (int id = 0; id < payload.contributions().length; id++) {
                        int strength = payload.contributions()[id] & 0xFF;
                        if (strength > 0) {
                            buffer.writeByte(id);
                            buffer.writeByte(strength);
                        }
                    }
                    int visualCount = payload.visualSources().length;
                    buffer.writeVarInt(visualCount);
                    for (int index = 0; index < visualCount; index++) {
                        buffer.writeByte(payload.visualPaletteEntries()[index * 2]);
                        buffer.writeByte(payload.visualPaletteEntries()[index * 2 + 1]);
                        buffer.writeLong(payload.visualSources()[index]);
                    }
                    buffer.writeByteArray(payload.revealedCells());
                    buffer.writeVarInt(payload.qteCell() + 1);
                    buffer.writeByte(payload.qteButton());
                    buffer.writeByte(payload.qteAction().ordinal());
                    buffer.writeByte(payload.qteRapidClicks());
                    buffer.writeByte(payload.qteTraceProgress());
                    buffer.writeVarInt(payload.qteTicksRemaining());
                    buffer.writeVarInt(payload.qteStreak());
                    buffer.writeVarInt(payload.qteSequence());
                    buffer.writeBoolean(payload.partialPurgeActive());
                    buffer.writeShort(Math.round(payload.partialPurgeZoneStart() * 1000.0F));
                    buffer.writeShort(Math.round(payload.partialPurgeZoneEnd() * 1000.0F));
                    buffer.writeShort(Math.round(payload.partialPurgeCursor() * 1000.0F));
                    buffer.writeBoolean(payload.partialPurgeCursorForward());
                    buffer.writeVarInt(payload.partialPurgeCursorOneWayTicks());
                    buffer.writeVarInt(payload.partialPurgeCooldownTicks());
                    buffer.writeByte(payload.partialPurgeResult());
                    buffer.writeVarInt(payload.partialPurgeResultTicks());
                    buffer.writeVarInt(payload.partialPurgeRound());
                    buffer.writeBoolean(payload.profile() != null);
                    if (payload.profile() != null) {
                        writeProfile(buffer, payload.profile());
                    }
                }
            };

    public AssimilationStatePayload {
        stage = stage == null ? AssimilationStage.NORMAL : stage;
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        shellIntegrity = Mth.clamp(shellIntegrity, 0.0F, 1.0F);
        restoringTicks = Math.max(0, restoringTicks);
        mediumId = SinkingMedium.byId(mediumId).id();
        if (contributions == null || contributions.length != SinkingMedium.COUNT) {
            byte[] normalized = new byte[SinkingMedium.COUNT];
            if (contributions != null) {
                System.arraycopy(contributions, 0, normalized, 0,
                        Math.min(contributions.length, normalized.length));
            } else if (progress > 0.0F) {
                normalized[mediumId] = (byte) Mth.clamp(Math.round(progress * 255.0F), 0, 255);
            }
            contributions = normalized;
        } else {
            contributions = contributions.clone();
        }
        int visualCount = Math.min(MudVisualPalette.MAX_ENTRIES,
                Math.min(visualPaletteEntries == null ? 0 : visualPaletteEntries.length / 2,
                        visualSources == null ? 0 : visualSources.length));
        if (visualCount == 0 && progress > 0.0F) {
            visualPaletteEntries = new byte[] {
                    (byte) mediumId,
                    (byte) 255
            };
            visualSources = new long[] {0L};
        } else {
            visualPaletteEntries = visualPaletteEntries == null
                    ? new byte[0] : java.util.Arrays.copyOf(visualPaletteEntries, visualCount * 2);
            visualSources = visualSources == null
                    ? new long[0] : java.util.Arrays.copyOf(visualSources, visualCount);
        }
        revealedCells = revealedCells == null ? new byte[0] : revealedCells.clone();
        qteCell = qteCell >= 0 && qteCell < MudSurfaceLayout.CELL_COUNT ? qteCell : -1;
        qteButton = Mth.clamp(qteButton, 0, 2);
        qteAction = qteAction == null ? AssimilationQteAction.NONE : qteAction;
        qteRapidClicks = Mth.clamp(qteRapidClicks, 0, 8);
        qteTraceProgress = Mth.clamp(qteTraceProgress, 0,
                AssimilationTracePattern.NODE_COUNT);
        if (qteCell < 0 || qteButton == 0) {
            qteAction = AssimilationQteAction.NONE;
        } else if (qteAction == AssimilationQteAction.NONE) {
            qteAction = AssimilationQteAction.CLICK;
        }
        qteTicksRemaining = Math.max(0, qteTicksRemaining);
        qteStreak = Math.max(0, qteStreak);
        qteSequence = Math.max(0, qteSequence);
        partialPurgeZoneStart = Mth.clamp(partialPurgeZoneStart, 0.0F, 1.0F);
        partialPurgeZoneEnd = Mth.clamp(partialPurgeZoneEnd,
                partialPurgeZoneStart, 1.0F);
        partialPurgeCursor = Mth.clamp(partialPurgeCursor, 0.0F, 1.0F);
        partialPurgeCursorOneWayTicks = Mth.clamp(partialPurgeCursorOneWayTicks, 1, 240);
        partialPurgeCooldownTicks = Math.max(0, partialPurgeCooldownTicks);
        partialPurgeResult = (byte) Mth.clamp(partialPurgeResult, 0, 2);
        partialPurgeResultTicks = Math.max(0, partialPurgeResultTicks);
        partialPurgeRound = Math.max(0, partialPurgeRound);
    }

    private static AssimilationProfile readProfile(RegistryFriendlyByteBuf buffer) {
        return new AssimilationProfile(
                buffer.readBoolean(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readFloat(), buffer.readVarInt(),
                buffer.readFloat(), buffer.readVarInt(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readFloat(),
                buffer.readFloat(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    private static void writeProfile(RegistryFriendlyByteBuf buffer, AssimilationProfile profile) {
        buffer.writeBoolean(profile.enabled());
        buffer.writeFloat(profile.gainPerTick());
        buffer.writeFloat(profile.immersionExponent());
        buffer.writeFloat(profile.minimumMoveScale());
        buffer.writeFloat(profile.minimumLookScale());
        buffer.writeFloat(profile.minimumAnimationScale());
        buffer.writeFloat(profile.screenOpacity());
        buffer.writeFloat(profile.blurStrength());
        buffer.writeBoolean(profile.armorEnabled());
        buffer.writeBoolean(profile.ordinaryCoverageEnabled());
        buffer.writeBoolean(profile.finalStasisEnabled());
        buffer.writeBoolean(profile.shellPhysicsEnabled());
        buffer.writeFloat(profile.shellGravity());
        buffer.writeFloat(profile.shellAirDrag());
        buffer.writeFloat(profile.shellGroundFriction());
        buffer.writeFloat(profile.shellRestitution());
        buffer.writeFloat(profile.shellMaximumSpeed());
        buffer.writeFloat(profile.shellMaximumTilt());
        buffer.writeFloat(profile.shellTiltResponse());
        buffer.writeFloat(profile.shellTeleportReleaseDistance());
        buffer.writeVarInt(profile.shellTransportHandoffTicks());
        buffer.writeFloat(profile.soulRadius());
        buffer.writeFloat(profile.soulMoveSpeed());
        buffer.writeFloat(profile.soulSprintMultiplier());
        buffer.writeFloat(profile.soulAcceleration());
        buffer.writeFloat(profile.soulDrag());
        buffer.writeFloat(profile.soulEmergenceBackOffset());
        buffer.writeFloat(profile.soulEmergenceUpOffset());
        buffer.writeFloat(profile.soulBaseEffect());
        buffer.writeFloat(profile.soulEffectStart());
        buffer.writeFloat(profile.soulColorStrength());
        buffer.writeFloat(profile.soulPixelSize());
        buffer.writeFloat(profile.soulBlurRadius());
        buffer.writeFloat(profile.soulBaseFogStrength());
        buffer.writeFloat(profile.soulFogStart());
        buffer.writeFloat(profile.soulFogDistance());
        buffer.writeFloat(profile.soulFogOpacity());
        buffer.writeFloat(profile.soulBoundarySoftness());
        buffer.writeFloat(profile.soulFloatAmplitude());
        buffer.writeFloat(profile.soulFloatSpeed());
        buffer.writeFloat(profile.soulSoundDamping());
        buffer.writeFloat(profile.rescuePulseStrength());
        buffer.writeFloat(profile.rescueCrackDarkness());
        buffer.writeFloat(profile.rescueDamagePerHit());
        buffer.writeVarInt(profile.rescueRevealRadius());
        buffer.writeVarInt(profile.rescuePulseTicks());
        buffer.writeBoolean(profile.selfRescueQteEnabled());
        buffer.writeVarInt(profile.selfRescueQteTimeoutTicks());
        buffer.writeVarInt(profile.selfRescueQteNextDelayTicks());
        buffer.writeVarInt(profile.selfRescueQteFailureDelayTicks());
        buffer.writeVarInt(profile.selfRescueQteRequiredStreak());
        buffer.writeVarInt(profile.selfRescueQteRevealRadius());
        buffer.writeVarInt(profile.selfRescueQteFadeTicks());
        buffer.writeFloat(profile.selfRescueQteAimDegrees());
        buffer.writeFloat(profile.selfRescueQteHoldChance());
        buffer.writeVarInt(profile.selfRescueQteHoldTicks());
        buffer.writeFloat(profile.selfRescueQteRapidChance());
        buffer.writeVarInt(profile.selfRescueQteRapidClicks());
        buffer.writeFloat(profile.selfRescueQteTraceChance());
        buffer.writeVarInt(profile.selfRescueQteTraceTimeoutTicks());
        buffer.writeVarInt(profile.selfRescueQteTraceNodes());
        buffer.writeVarInt(profile.selfRescueQteTraceSpacing());
        buffer.writeFloat(profile.selfRescueQteTraceHitRadius());
        buffer.writeFloat(profile.selfRescueQteRange());
        buffer.writeVarInt(profile.soulTransitionTicks());
        buffer.writeVarInt(profile.restoreTicks());
        buffer.writeVarInt(profile.restoreBlackoutFadeTicks());
        buffer.writeVarInt(profile.rescueGraceTicks());
        buffer.writeBoolean(profile.partialPurgeEnabled());
        buffer.writeBoolean(profile.partialPurgeCancelOnMove());
        buffer.writeVarInt(profile.partialPurgeCursorMinOneWayTicks());
        buffer.writeVarInt(profile.partialPurgeCursorMaxOneWayTicks());
        buffer.writeFloat(profile.partialPurgeZoneMinWidth());
        buffer.writeFloat(profile.partialPurgeZoneMaxWidth());
        buffer.writeFloat(profile.partialPurgeSuccessAmount());
        buffer.writeFloat(profile.partialPurgeFailureAmount());
        buffer.writeVarInt(profile.partialPurgeSuccessWeaknessTicks());
        buffer.writeVarInt(profile.partialPurgeFailureWeaknessTicks());
        buffer.writeFloat(profile.partialPurgeFailureDamage());
        buffer.writeVarInt(profile.partialPurgeRoundCooldownTicks());
        buffer.writeVarInt(profile.partialPurgeSplashDroplets());
        buffer.writeFloat(profile.partialPurgeSplashSpeed());
        buffer.writeFloat(profile.screenCrackStartProgress());
        buffer.writeVarInt(profile.screenCrackMinIntervalTicks());
        buffer.writeVarInt(profile.screenCrackMaxIntervalTicks());
        buffer.writeVarInt(profile.screenCrackFadeInTicks());
        buffer.writeVarInt(profile.screenCrackHoldTicks());
        buffer.writeVarInt(profile.screenCrackFadeOutTicks());
        buffer.writeFloat(profile.screenCrackMinLength());
        buffer.writeFloat(profile.screenCrackMaxLength());
        buffer.writeFloat(profile.screenCrackMinWidth());
        buffer.writeFloat(profile.screenCrackMaxWidth());
    }

    @Override
    public Type<AssimilationStatePayload> type() {
        return TYPE;
    }
}
