package com.fish.mirebound.mud;

import static com.fish.mirebound.coverage.MudSkinCoverageOperations.blendSkinSurfaceEdges;
import static com.fish.mirebound.coverage.MudSkinCoverageOperations.innerCleanlinessMask;

import com.fish.mirebound.assimilation.AssimilationConfig;
import com.fish.mirebound.assimilation.AssimilationSystem;
import com.fish.mirebound.coverage.MudCoverageService;
import java.util.Arrays;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/** Applies one bounded splash sphere to canonical skin and armor pixels. */
final class MudSplashPlayerPainter {
    private static final int CLOD_ACCUMULATION_PASSES = 8;
    private static final MudBodyPart[] BODY_PARTS = MudBodyPart.values();
    private static final MudSurface[] SURFACES = MudSurface.values();
    private static final ThreadLocal<boolean[]> ARMOR_PROTECTED =
            ThreadLocal.withInitial(() -> new boolean[MudSurfaceLayout.CELL_COUNT]);

    private MudSplashPlayerPainter() {
    }

    static void resetThreadState() {
        ARMOR_PROTECTED.remove();
    }

    static boolean apply(ServerPlayer player, Vec3 impact,
            float radius, float strength, SinkingMedium medium,
            long visualSource,
            boolean forceOrdinaryCoverage) {
        return apply(player, impact, radius, strength, medium,
                visualSource, forceOrdinaryCoverage, 1);
    }

    static boolean applyClod(ServerPlayer player, Vec3 impact,
            float radius, float strength, SinkingMedium medium,
            long visualSource,
            boolean forceOrdinaryCoverage) {
        return apply(player, impact, radius, strength, medium,
                visualSource, forceOrdinaryCoverage,
                CLOD_ACCUMULATION_PASSES);
    }

    private static boolean apply(ServerPlayer player, Vec3 impact,
            float radius, float strength, SinkingMedium medium,
            long visualSource, boolean forceOrdinaryCoverage,
            int accumulationPasses) {
        if (radius <= 0.0F || strength <= 0.0F
                || MudPhysics.isPollutionSuppressed(player)) {
            return false;
        }
        if (!forceOrdinaryCoverage
                && AssimilationConfig.appliesTo(medium)
                && !AssimilationConfig.profileFor(medium)
                        .ordinaryCoverageEnabled()) {
            return false;
        }

        MudPlayerData data = MudStateStore.get(player);
        MudEntityGeometry.SurfacePixelSampler[] geometry =
                MudEntityGeometry.surfacePixelSamplers(player);
        Vec3 surfaceImpact = nearestModelSurface(impact, geometry);
        boolean[] armorProtected = ARMOR_PROTECTED.get();
        Arrays.fill(armorProtected, false);
        boolean armorChanged = paintArmor(
                player, surfaceImpact, radius, strength, medium,
                visualSource,
                geometry, armorProtected, accumulationPasses);
        boolean skinChanged = paintSkin(
                player, data, surfaceImpact, radius, strength, medium,
                visualSource,
                geometry, armorProtected, accumulationPasses);

        if (skinChanged) {
            blendSkinSurfaceEdges(player, data, innerCleanlinessMask(player));
            data.refreshCoverageAfterSurfaceUpdate();
        }
        if (!skinChanged && !armorChanged) {
            return false;
        }
        MudCoverageService.save(player, data);
        MudCoverageService.sync(player, data, true);
        return true;
    }

    private static Vec3 nearestModelSurface(Vec3 impact,
            MudEntityGeometry.SurfacePixelSampler[] geometry) {
        Vec3 nearest = impact;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (MudEntityGeometry.SurfacePixelSampler sampler : geometry) {
            Vec3 candidate = sampler.nearestSurfacePoint(impact);
            double distance = candidate.distanceToSqr(impact);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static boolean paintArmor(ServerPlayer player, Vec3 impact,
            float radius, float strength, SinkingMedium medium,
            long visualSource,
            MudEntityGeometry.SurfacePixelSampler[] geometry,
            boolean[] armorProtected, int accumulationPasses) {
        boolean changed = false;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            var stack = player.getItemBySlot(slot);
            if (!ArmorMudManager.validArmor(stack, slot)) {
                continue;
            }
            boolean stainProtected =
                    MudEnchantmentEffects.preventsArmorStaining(player, stack);
            ArmorMudData.Builder builder = ArmorMudManager.data(stack).toBuilder();
            for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
                if (AssimilationSystem.keepsCrackClear(player, cell)) {
                    continue;
                }
                MudBodyPart part = MudSurfaceLayout.part(cell);
                MudSurface surface = MudSurfaceLayout.surface(cell);
                int row = MudSurfaceLayout.row(cell);
                if (!ArmorMudManager.slotOwnsSurface(slot, part, surface, row)) {
                    continue;
                }
                armorProtected[cell] = true;
                int column = MudSurfaceLayout.column(cell);
                MudEntityGeometry.SurfacePixelSampler partGeometry =
                        geometry[part.ordinal()];
                Vec3 point = partGeometry.point(part, surface, row, column)
                        .add(partGeometry.outwardNormal(surface)
                                .scale(ArmorMudManager.surfaceOffset(slot)));
                double distance = point.distanceTo(impact);
                if (distance <= radius && !stainProtected
                        && ArmorMudManager.allowsCoveragePixel(
                                player, slot, cell, medium)) {
                    float falloff = Mth.clamp(
                            1.0F - (float) distance / radius, 0.22F, 1.0F);
                    changed |= builder.markSplash(
                            cell,
                            MudCoverageRules.contactTarget(
                                    player.level(), medium, strength * falloff),
                            medium, visualSource, accumulationPasses);
                }
            }
            if (builder.changed()) {
                ArmorMudManager.blendSurfaceEdges(player, slot, builder);
                ArmorMudManager.store(stack, builder.build());
            }
        }
        return changed;
    }

    private static boolean paintSkin(ServerPlayer player, MudPlayerData data,
            Vec3 impact, float radius, float strength, SinkingMedium medium,
            long visualSource,
            MudEntityGeometry.SurfacePixelSampler[] geometry,
            boolean[] armorProtected, int accumulationPasses) {
        boolean changed = false;
        for (MudBodyPart part : BODY_PARTS) {
            for (MudSurface surface : SURFACES) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    for (int column = 0; column < face.width(); column++) {
                        int cell = MudSurfaceLayout.cellIndex(
                                part, surface, row, column);
                        if (armorProtected[cell]
                                || AssimilationSystem.keepsCrackClear(player, cell)) {
                            continue;
                        }
                        Vec3 point = geometry[part.ordinal()].point(
                                part, surface, row, column);
                        double distance = point.distanceTo(impact);
                        if (distance > radius
                                || !MudCoverageRules.allowsPixel(
                                        player.level(), medium,
                                        MudCoverageRules.DOMAIN_SKIN,
                                        cell, MudSurfaceLayout.CELL_COUNT)) {
                            continue;
                        }
                        float falloff = Mth.clamp(
                                1.0F - (float) distance / radius, 0.22F, 1.0F);
                        float current = data.surfacePixelCoverage(
                                part, surface, row, column);
                        float target = MudCoverageRules.contactTarget(
                                player.level(), medium, strength * falloff);
                        float next = MudCoverageRules.accumulateSplash(
                                current, target, accumulationPasses);
                        if (MudCoverageRules.splashChangesCell(
                                current, next,
                                data.surfacePixelMedium(
                                        part, surface, row, column),
                                medium,
                                data.surfacePixelVisualSource(
                                        part, surface, row, column),
                                visualSource)) {
                            data.setSurfacePixelCoverage(
                                    part, surface, row, column, next, medium,
                                    data.surfacePixelAppearance(
                                            part, surface, row, column),
                                    visualSource);
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }
}
