package com.fish.mirebound;

import com.fish.mirebound.client.ClientEvents;
import com.fish.mirebound.adaptive.AdaptiveMudSourceSync;
import com.fish.mirebound.adaptive.AdaptiveMudBehaviorSettings;
import com.fish.mirebound.adaptive.AdaptiveMudConversionScheduler;
import com.fish.mirebound.assimilation.AssimilationSystem;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.mud.MudMobPhysics;
import com.fish.mirebound.mud.tuning.MudTuningManager;
import com.fish.mirebound.mud.tuning.MudTuningConversionSafety;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.harvest.MudHarvestSystem;
import com.fish.mirebound.mud.harvest.MudVolumeDropSystem;
import com.fish.mirebound.mud.flow.MudFlowSystem;
import com.fish.mirebound.mud.container.MudVolumeContainerSystem;
import com.fish.mirebound.command.MudCommands;
import com.fish.mirebound.coverage.MudCoverageService;
import com.fish.mirebound.eruption.MudEruptionSystem;
import com.fish.mirebound.entitycoverage.EntityMudCoverageService;
import com.fish.mirebound.generation.MudTerrainGenerationScheduler;
import com.fish.mirebound.generation.natural.NaturalMudGenerationSettings;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsSystem;
import com.fish.mirebound.splash.MudSplashSystem;
import com.fish.mirebound.stain.MudDecorationInteractionSystem;
import com.fish.mirebound.stain.MudWallStainSystem;
import com.fish.mirebound.mud.CoverageDebugLog;
import com.fish.mirebound.mud.PhysicsTraceLog;
import com.fish.mirebound.mud.MudLocalProfileSync;
import com.fish.mirebound.physics.PlayerGravityControl;
import com.fish.mirebound.swarm.SwarmSystem;
import com.fish.mirebound.tentacle.TentacleSystem;
import com.fish.mirebound.rope.RopeRuntime;
import com.fish.mirebound.tool.MudTuningWandReach;
import com.fish.mirebound.tool.MudTuningWandInteractionGuard;
import com.fish.mirebound.network.ModNetworking;
import com.fish.mirebound.network.ServerInputBudget;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.registry.ModCreativeTabs;
import com.fish.mirebound.registry.ModCriteria;
import com.fish.mirebound.registry.ModMudworkContent;
import com.fish.mirebound.registry.ModDataComponents;
import com.fish.mirebound.registry.ModEnchantmentEffectComponents;
import com.fish.mirebound.registry.ModFeatures;
import com.fish.mirebound.registry.ModParticles;
import com.fish.mirebound.water.WaterGunSystem;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Mirebound.MOD_ID)
public class Mirebound {
    public static final String MOD_ID = "mirebound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mirebound(IEventBus modBus, ModContainer modContainer) {
        ModBlocks.register(modBus);
        ModMudworkContent.register(modBus);
        ModCreativeTabs.register(modBus);
        ModCriteria.register(modBus);
        ModDataComponents.register(modBus);
        ModEnchantmentEffectComponents.register(modBus);
        ModFeatures.register(modBus);
        ModParticles.register(modBus);
        MudPhysicsSettings.register(modContainer, modBus);
        modBus.addListener(ModNetworking::register);
        // Gravity recovery must run before mud/tentacle login, logout and clone work.
        NeoForge.EVENT_BUS.addListener(PlayerGravityControl::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(PlayerGravityControl::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(PlayerGravityControl::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(PlayerGravityControl::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(MudMobPhysics::onEntityTick);
        NeoForge.EVENT_BUS.addListener(EntityMudCoverageService::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(DroppedItemPhysicsSystem::onEntityTick);
        NeoForge.EVENT_BUS.addListener(MudTuningWandReach::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                MudTuningWandInteractionGuard::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                MudTuningWandInteractionGuard::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                MudDecorationInteractionSystem::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                MudTuningWandInteractionGuard::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                MudTuningWandInteractionGuard::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                MudTuningWandInteractionGuard::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                MudTuningWandInteractionGuard::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(MudHarvestSystem::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(MudVolumeDropSystem::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onLivingBreathe);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onLivingAttack);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(MudVolumeContainerSystem::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
                MudWallStainSystem::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(MudTuningManager::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(MudTuningManager::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(MudTuningConversionSafety::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(AdaptiveMudBehaviorSettings::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(MudCoverageService::onStartTracking);
        NeoForge.EVENT_BUS.addListener(EntityMudCoverageService::onStartTracking);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onStartTracking);
        NeoForge.EVENT_BUS.addListener(DroppedItemPhysicsSystem::onStartTracking);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ServerInputBudget::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(MudTuningManager::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(MudTuningManager::onServerStopping);
        NeoForge.EVENT_BUS.addListener(AdaptiveMudConversionScheduler::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(MudTerrainGenerationScheduler::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(MudTuningConversionSafety::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onClone);
        NeoForge.EVENT_BUS.addListener(MudPhysics::onServerStopping);
        NeoForge.EVENT_BUS.addListener(ServerInputBudget::onServerStopping);
        NeoForge.EVENT_BUS.addListener(AssimilationSystem::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MudMobPhysics::onServerStopping);
        NeoForge.EVENT_BUS.addListener(EntityMudCoverageService::onServerStopping);
        NeoForge.EVENT_BUS.addListener(DroppedItemPhysicsSystem::onServerStopping);
        NeoForge.EVENT_BUS.addListener(AdaptiveMudConversionScheduler::onServerTick);
        NeoForge.EVENT_BUS.addListener(AdaptiveMudConversionScheduler::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MudTerrainGenerationScheduler::onServerTick);
        NeoForge.EVENT_BUS.addListener(MudTerrainGenerationScheduler::onServerStopping);
        NeoForge.EVENT_BUS.addListener(NaturalMudGenerationSettings::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(NaturalMudGenerationSettings::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MudFlowSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(MudFlowSystem::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(MudFlowSystem::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MudLocalProfileSync::onChunkSent);
        NeoForge.EVENT_BUS.addListener(AdaptiveMudSourceSync::onChunkSent);
        NeoForge.EVENT_BUS.addListener(MudSplashSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(MudSplashSystem::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(MudSplashSystem::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MudEruptionSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(MudEruptionSystem::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(MudEruptionSystem::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(MudEruptionSystem::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(MudEruptionSystem::onServerStopping);
        NeoForge.EVENT_BUS.addListener(WaterGunSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(EntityMudCoverageService::onServerTick);
        NeoForge.EVENT_BUS.addListener(WaterGunSystem::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(WaterGunSystem::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(WaterGunSystem::onServerStopping);
        NeoForge.EVENT_BUS.addListener(SwarmSystem::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SwarmSystem::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SwarmSystem::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(SwarmSystem::onServerStopping);
        NeoForge.EVENT_BUS.addListener(TentacleSystem::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(TentacleSystem::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(TentacleSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(TentacleSystem::onServerStopping);
        NeoForge.EVENT_BUS.addListener(RopeRuntime::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RopeRuntime::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RopeRuntime::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(RopeRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(RopeRuntime::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(RopeRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(PhysicsTraceLog::onServerStopping);
        NeoForge.EVENT_BUS.addListener(CoverageDebugLog::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MudCommands::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientOnly.register(modBus, modContainer);
        }
    }

    private static final class ClientOnly {
        private static void register(IEventBus modBus, ModContainer modContainer) {
            ClientEvents.register(modBus, modContainer);
        }
    }
}
