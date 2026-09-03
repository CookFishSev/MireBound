package com.fish.mirebound.generation;

import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.tuning.MudTuningConversionSafety;
import com.fish.mirebound.network.payload.MudTerrainGenerationPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Validates all client generation intents before they reach the task scheduler. */
public final class MudTerrainGenerationManager {
    private MudTerrainGenerationManager() {
    }

    public static void handle(
            ServerPlayer player, MudTerrainGenerationPayload payload) {
        if (!player.hasPermissions(2)) {
            return;
        }
        if (payload.action() == MudTerrainGenerationPayload.Action.CANCEL) {
            if (MudTerrainGenerationScheduler.cancel(player)) {
                player.displayClientMessage(Component.translatable(
                        "message.mirebound.generation.cancelled"), true);
            } else {
                player.displayClientMessage(Component.translatable(
                        "message.mirebound.generation.no_active_task"), true);
            }
            return;
        }
        if (payload.action() == MudTerrainGenerationPayload.Action.UNDO) {
            if (!MudTerrainGenerationScheduler.submitUndo(player)) {
                player.displayClientMessage(Component.translatable(
                        "message.mirebound.generation.nothing_to_undo"), true);
            }
            return;
        }
        if (payload.action() != MudTerrainGenerationPayload.Action.GENERATE) {
            return;
        }
        if (!MudTuningConversionSafety.isUnlocked(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.generation.locked"), true);
            return;
        }
        MudTerrainGenerationRequest request = payload.request();
        if (request == null || !request.validWireValues()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!validCenter(player, level, request)
                || request.type().isLake()
                        && !validLakeSettings(level, request.center(),
                                request.lakeSettings())
                || request.type().isNaturalDeposit()
                        && !validInnerBlock(request.lakeSettings().innerBlockId())) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.generation.invalid_target"), true);
            return;
        }
        if (!MudTerrainGenerationScheduler.submit(player, request)) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.adaptive.task_active"), true);
        }
    }

    private static boolean validCenter(
            ServerPlayer player, ServerLevel level,
            MudTerrainGenerationRequest request) {
        BlockPos center = request.center();
        if (!level.isInWorldBounds(center)
                || !level.getWorldBorder().isWithinBounds(center)
                || !level.getChunkSource().hasChunk(
                        center.getX() >> 4, center.getZ() >> 4)) {
            return false;
        }
        double range = MudPhysicsSettings.mudTuningWandInteractionRange() + 1.0D;
        Vec3 target = Vec3.atCenterOf(center);
        return player.distanceToSqr(target) <= range * range;
    }

    static boolean validLakeSettings(
            ServerLevel level, BlockPos center, MudTerrainLakeSettings settings) {
        if (!validBlockId(settings.shellBlockId())
                || !validBlockId(settings.innerBlockId())) {
            return false;
        }
        if (!MudTerrainLakeSettings.AIR.equals(settings.shellBlockId())) {
            BlockState shell = BuiltInRegistries.BLOCK
                    .get(settings.shellBlockId()).defaultBlockState();
            if (!validShellState(shell)) {
                return false;
            }
        }
        if (MudTerrainLakeSettings.AIR.equals(settings.innerBlockId())) {
            return true;
        }
        BlockState inner = BuiltInRegistries.BLOCK
                .get(settings.innerBlockId()).defaultBlockState();
        return MudTerrainBlockRules.validInner(inner);
    }

    private static boolean validInnerBlock(ResourceLocation id) {
        if (!validBlockId(id) || MudTerrainLakeSettings.AIR.equals(id)) {
            return MudTerrainLakeSettings.AIR.equals(id);
        }
        return MudTerrainBlockRules.validInner(
                BuiltInRegistries.BLOCK.get(id).defaultBlockState());
    }

    private static boolean validBlockId(ResourceLocation id) {
        return id != null && BuiltInRegistries.BLOCK.containsKey(id);
    }

    private static boolean validShellState(BlockState state) {
        return MudTerrainBlockRules.validFullSource(state);
    }
}
