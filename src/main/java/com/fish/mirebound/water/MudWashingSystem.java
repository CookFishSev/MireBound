package com.fish.mirebound.water;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.coverage.MudCoverageService;
import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCapeGeometry;
import com.fish.mirebound.mud.MudCapeLayout;
import com.fish.mirebound.mud.MudCoverageAppearanceSnapshot;
import com.fish.mirebound.mud.MudEntityGeometry;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.MudStateStore;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Owns exact player, cape, and armor washing from water, rain, and water guns. */
public final class MudWashingSystem {
    private static final float WATER_SURFACE_WASH_AMOUNT = 0.032F;
    private static final float RAIN_SURFACE_WASH_AMOUNT = 0.018F;
    private static final float CLEAR_THRESHOLD = 0.00025F;
    private static final double WATER_TOUCH_RADIUS = 0.024D;

    private MudWashingSystem() {
    }

    public static boolean washFromWaterGun(
            ServerPlayer player, Vec3 impact, float radius, float amount) {
        if (radius <= 0.0F || amount <= 0.0F) {
            return false;
        }
        MudPlayerData data = MudStateStore.get(player);
        MudEntityGeometry.SurfacePixelSampler[] geometry =
                MudEntityGeometry.surfacePixelSamplers(player);
        boolean[] armorProtected = new boolean[MudSurfaceLayout.CELL_COUNT];
        boolean armorChanged = false;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            var stack = player.getItemBySlot(slot);
            if (!ArmorMudManager.validArmor(stack, slot)) {
                continue;
            }
            for (int cell = 0; cell < armorProtected.length; cell++) {
                MudBodyPart part = MudSurfaceLayout.part(cell);
                MudSurface surface = MudSurfaceLayout.surface(cell);
                int row = MudSurfaceLayout.row(cell);
                if (ArmorMudManager.slotOwnsSurface(slot, part, surface, row)) {
                    armorProtected[cell] = true;
                }
            }
            ArmorMudData original = ArmorMudManager.data(stack);
            if (original.isEmpty()) {
                continue;
            }
            ArmorMudData.Builder builder = original.toBuilder();
            original.forEach((cell, coverage, medium) -> {
                MudBodyPart part = MudSurfaceLayout.part(cell);
                MudSurface surface = MudSurfaceLayout.surface(cell);
                int row = MudSurfaceLayout.row(cell);
                int column = MudSurfaceLayout.column(cell);
                if (!ArmorMudManager.slotOwnsSurface(slot, part, surface, row)) {
                    return;
                }
                MudEntityGeometry.SurfacePixelSampler partGeometry = geometry[part.ordinal()];
                Vec3 point = partGeometry.point(part, surface, row, column)
                        .add(partGeometry.outwardNormal(surface)
                                .scale(ArmorMudManager.surfaceOffset(slot)));
                double distance = point.distanceTo(impact);
                if (distance <= radius) {
                    float falloff = Mth.clamp(
                            1.0F - (float) distance / radius, 0.18F, 1.0F);
                    builder.wash(cell,
                            amount * falloff
                                    * MudMediumRuntime.waterWashMultiplier(
                                            player.level(), medium),
                            CLEAR_THRESHOLD,
                            player.tickCount + cell * 13);
                }
            });
            if (builder.changed()) {
                ArmorMudManager.store(stack, builder.build());
                armorChanged = true;
            }
        }

        boolean skinChanged = false;
        if (data.hasPersistentCoverage()) {
            for (MudBodyPart part : MudBodyPart.values()) {
                for (MudSurface surface : MudSurface.values()) {
                    MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                    for (int row = 0; row < face.height(); row++) {
                        for (int column = 0; column < face.width(); column++) {
                            int cell = MudSurfaceLayout.cellIndex(
                                    part, surface, row, column);
                            if (armorProtected[cell] || data.surfaceCoverage[cell] <= 0.0F) {
                                continue;
                            }
                            Vec3 point = geometry[part.ordinal()].point(
                                    part, surface, row, column);
                            double distance = point.distanceTo(impact);
                            if (distance > radius) {
                                continue;
                            }
                            float falloff = Mth.clamp(
                                    1.0F - (float) distance / radius, 0.18F, 1.0F);
                            SinkingMedium dirtyMedium = data.surfacePixelMedium(
                                    part, surface, row, column);
                            skinChanged |= reduceSurfacePixel(
                                    data,
                                    part,
                                    surface,
                                    row,
                                    column,
                                    amount * falloff
                                            * MudMediumRuntime.waterWashMultiplier(
                                                    player.level(), dirtyMedium),
                                    CLEAR_THRESHOLD,
                                    player.tickCount + cell * 13);
                        }
                    }
                }
            }
        }
        if (skinChanged) {
            data.refreshCoverageSummary();
        }

        boolean capeChanged = false;
        MudCapeGeometry.CapeBasis capeBasis = MudEntityGeometry.capeBasis(player);
        for (MudCapeLayout.Side side : MudCapeLayout.Side.values()) {
            for (int row = 0; row < MudCapeLayout.ROWS; row++) {
                for (int column = 0; column < MudCapeLayout.COLUMNS; column++) {
                    int capeCell = MudCapeLayout.index(side, row, column);
                    if (data.capeCoverage[capeCell] <= 0.0F) {
                        continue;
                    }
                    Vec3 point = MudEntityGeometry.capeProbe(
                            capeBasis, side, row, column);
                    double distance = point.distanceTo(impact);
                    if (distance > radius) {
                        continue;
                    }
                    float falloff = Mth.clamp(
                            1.0F - (float) distance / radius, 0.18F, 1.0F);
                    SinkingMedium dirtyMedium = data.capePixelMedium(side, row, column);
                    capeChanged |= reduceCapePixel(
                            data,
                            side,
                            row,
                            column,
                            amount * falloff
                                    * MudMediumRuntime.waterWashMultiplier(
                                            player.level(), dirtyMedium),
                            CLEAR_THRESHOLD,
                            player.tickCount + capeCell * 13);
                }
            }
        }
        if (!skinChanged && !armorChanged && !capeChanged) {
            return false;
        }
        MudCoverageService.save(player, data);
        MudCoverageService.sync(player, data, true);
        return true;
    }

    public static boolean washWaterTouchedCoverage(
            ServerPlayer player, MudPlayerData data) {
        if (!hasWaterWashTargets(player, data)) {
            return false;
        }
        Level level = player.level();
        AABB waterBounds = player.getBoundingBox().inflate(0.24D, 0.16D, 0.24D);
        OrdinaryWaterProbe ordinaryWaterProbe = OrdinaryWaterProbe.capture(
                level, waterBounds.inflate(WATER_TOUCH_RADIUS + 0.01D));
        SableCompat.WaterVolumeProbe sableWaterProbe = SableCompat.isLoaded()
                ? SableCompat.waterVolumeProbe(level, waterBounds, player)
                : null;
        if (ordinaryWaterProbe.isEmpty()
                && (sableWaterProbe == null || sableWaterProbe.isEmpty())) {
            return false;
        }

        MudEntityGeometry.SurfacePixelSampler[] geometry =
                MudEntityGeometry.surfacePixelSamplers(player);
        boolean changed = false;
        if (data.hasPersistentCoverage()) {
            for (MudBodyPart part : MudBodyPart.values()) {
                for (MudSurface surface : MudSurface.values()) {
                    MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                    for (int row = 0; row < face.height(); row++) {
                        for (int column = 0; column < face.width(); column++) {
                            if (data.surfacePixelCoverage(
                                    part, surface, row, column) <= 0.0F) {
                                continue;
                            }
                            Vec3 point = geometry[part.ordinal()].point(
                                    part, surface, row, column);
                            if (isWaterTouching(point, ordinaryWaterProbe, sableWaterProbe)) {
                                SinkingMedium dirtyMedium = data.surfacePixelMedium(
                                        part, surface, row, column);
                                changed |= reduceSurfacePixel(
                                        data,
                                        part,
                                        surface,
                                        row,
                                        column,
                                        WATER_SURFACE_WASH_AMOUNT
                                                * MudMediumRuntime.waterWashMultiplier(
                                                        level, dirtyMedium),
                                        CLEAR_THRESHOLD,
                                        player.tickCount);
                            }
                        }
                    }
                }
            }
        }
        if (changed) {
            data.refreshCoverageSummary();
        }

        MudCapeGeometry.CapeBasis capeBasis = MudEntityGeometry.capeBasis(player);
        for (MudCapeLayout.Side side : MudCapeLayout.Side.values()) {
            for (int row = 0; row < MudCapeLayout.ROWS; row++) {
                for (int column = 0; column < MudCapeLayout.COLUMNS; column++) {
                    int capeCell = MudCapeLayout.index(side, row, column);
                    if (data.capeCoverage[capeCell] <= 0.0F
                            || !isWaterTouching(
                                    MudEntityGeometry.capeProbe(
                                            capeBasis, side, row, column),
                                    ordinaryWaterProbe,
                                    sableWaterProbe)) {
                        continue;
                    }
                    SinkingMedium dirtyMedium = data.capePixelMedium(side, row, column);
                    changed |= reduceCapePixel(
                            data,
                            side,
                            row,
                            column,
                            WATER_SURFACE_WASH_AMOUNT
                                    * MudMediumRuntime.waterWashMultiplier(
                                            level, dirtyMedium),
                            CLEAR_THRESHOLD,
                            player.tickCount + capeCell * 13);
                }
            }
        }
        return washWaterTouchedArmor(
                player, ordinaryWaterProbe, sableWaterProbe, geometry) || changed;
    }

    public static void washRainExposedCoverage(
            ServerPlayer player, MudPlayerData data) {
        washRainExposedCoverage(player, data, player.position().y - 0.25D);
    }

    public static void washRainExposedCoverage(
            ServerPlayer player, MudPlayerData data, double coveredSurfaceY) {
        if (!isPlayerInRain(player)) {
            return;
        }
        int minExposedBand = exposedRainLevel(player, coveredSurfaceY);
        if (data.hasPersistentCoverage()) {
            washRainExposed(
                    data,
                    player.level(),
                    RAIN_SURFACE_WASH_AMOUNT,
                    CLEAR_THRESHOLD,
                    minExposedBand,
                    player.tickCount);
        }
        washRainExposedArmor(player, data, minExposedBand);
    }

    private static void washRainExposed(
            MudPlayerData data, Level level, float amount, float clearThreshold,
            int minExposedBand, int tickSalt) {
        for (MudBodyPart part : MudBodyPart.values()) {
            for (MudSurface surface : MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    int band = MudSurfaceLayout.legacyBand(part, surface, row);
                    if (band < minExposedBand) {
                        continue;
                    }
                    float verticalStrength = rainVerticalStrength(band - minExposedBand);
                    for (int column = 0; column < face.width(); column++) {
                        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
                        float current = data.surfaceCoverage[index];
                        if (current <= 0.0F) {
                            continue;
                        }
                        float noise = surfaceCellNoise(part, surface, row, column, tickSalt / 9);
                        if (noise < 0.36F) {
                            SinkingMedium medium = SinkingMedium.byId(data.surfaceMedium[index] & 0xFF);
                            float reduction = amount * MudMediumRuntime.rainWashMultiplier(level, medium)
                                    * verticalStrength * (0.55F + noise * 1.70F);
                            reduceSurfacePixel(data, index, reduction, clearThreshold);
                        }
                    }
                }
            }
        }
        for (MudCapeLayout.Side side : MudCapeLayout.Side.values()) {
            for (int row = 0; row < MudCapeLayout.ROWS; row++) {
                float verticalStrength = rainVerticalStrength(Math.max(0, MudCapeLayout.ROWS - 1 - row));
                for (int column = 0; column < MudCapeLayout.COLUMNS; column++) {
                    int index = MudCapeLayout.index(side, row, column);
                    float current = data.capeCoverage[index];
                    if (current <= 0.0F) {
                        continue;
                    }
                    float noise = capeCellNoise(row, column, tickSalt / 9 + side.ordinal() * 101);
                    if (noise < 0.36F) {
                        SinkingMedium medium = SinkingMedium.byId(data.capeMedium[index] & 0xFF);
                        float reduction = amount * MudMediumRuntime.rainWashMultiplier(level, medium)
                                * verticalStrength * (0.55F + noise * 1.70F);
                        reduceCapePixel(data, index, reduction, clearThreshold);
                    }
                }
            }
        }
        data.refreshCoverageSummary();
    }

    private static boolean reduceSurfacePixel(
            MudPlayerData data, MudBodyPart part, MudSurface surface, int row, int column,
            float amount, float clearThreshold, int tickSalt) {
        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
        float noise = surfaceCellNoise(part, surface, row, column, tickSalt / 5);
        return reduceSurfacePixel(data, index, amount * (0.58F + noise * 0.72F), clearThreshold);
    }

    private static boolean reduceSurfacePixel(
            MudPlayerData data, int index, float reduction, float clearThreshold) {
        float current = data.surfaceCoverage[index];
        if (current <= 0.0F) {
            return false;
        }
        data.surfaceCoverage[index] = Math.max(0.0F, current - reduction);
        if (data.surfaceCoverage[index] <= clearThreshold) {
            data.surfaceCoverage[index] = 0.0F;
            data.surfaceMedium[index] = (byte) SinkingMedium.MUD.id();
            data.surfaceAppearance[index] = MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
        }
        return data.surfaceCoverage[index] != current;
    }

    private static boolean reduceCapePixel(
            MudPlayerData data, MudCapeLayout.Side side, int row, int column,
            float amount, float clearThreshold, int tickSalt) {
        int index = MudCapeLayout.index(side, row, column);
        float noise = capeCellNoise(row, column, tickSalt / 5);
        return reduceCapePixel(data, index, amount * (0.58F + noise * 0.72F), clearThreshold);
    }

    private static boolean reduceCapePixel(
            MudPlayerData data, int index, float reduction, float clearThreshold) {
        float current = data.capeCoverage[index];
        if (current <= 0.0F) {
            return false;
        }
        data.capeCoverage[index] = Math.max(0.0F, current - reduction);
        if (data.capeCoverage[index] <= clearThreshold) {
            data.capeCoverage[index] = 0.0F;
            data.capeMedium[index] = (byte) SinkingMedium.MUD.id();
            data.capeAppearance[index] = MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
        }
        return data.capeCoverage[index] != current;
    }

    private static float rainVerticalStrength(int distanceAboveSurface) {
        return switch (distanceAboveSurface) {
            case 0 -> 0.62F;
            case 1 -> 0.82F;
            default -> 1.0F;
        };
    }

    private static float surfaceCellNoise(
            MudBodyPart part, MudSurface surface, int row, int column, int salt) {
        int value = part.ordinal() * 73428767 ^ surface.ordinal() * 42317861 ^ row * 9122719
                ^ column * 1274126177 ^ salt * 1103515245;
        value ^= value >>> 13;
        value *= 1274126177;
        value ^= value >>> 16;
        return (value & 1023) / 1023.0F;
    }

    private static float capeCellNoise(int row, int column, int salt) {
        int value = row * 9122719 ^ column * 1274126177 ^ salt * 1103515245 ^ 0x4c3a2f17;
        value ^= value >>> 13;
        value *= 1274126177;
        value ^= value >>> 16;
        return (value & 1023) / 1023.0F;
    }

    public static boolean isPlayerInRain(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos bodyPos = BlockPos.containing(player.position());
        BlockPos headPos = BlockPos.containing(player.getEyePosition());
        return level.isRainingAt(bodyPos) || level.isRainingAt(headPos);
    }

    public static boolean isWaterTouching(Level level, Vec3 point) {
        return isWaterTouching(level, point, null);
    }

    public static boolean isWaterTouching(Level level, Vec3 point, double radius) {
        return isWaterTouching(level, point, radius, 0.030D);
    }

    public static boolean isWaterTouching(
            Level level, Vec3 point, double radius, double fluidTolerance) {
        return isWaterTouching(level, point, radius, fluidTolerance, null, null);
    }

    /** Freezes ordinary and physicalized water once for a batch of exact probes. */
    public static WaterContactProbe captureWaterContact(
            Level level, AABB bounds, Entity trackingEntity) {
        OrdinaryWaterProbe ordinary = OrdinaryWaterProbe.capture(level, bounds);
        SableCompat.WaterVolumeProbe sable = SableCompat.isLoaded()
                ? SableCompat.waterVolumeProbe(level, bounds, trackingEntity)
                : null;
        return new WaterContactProbe(ordinary, sable);
    }

    private static int exposedRainLevel(
            ServerPlayer player, double coveredSurfaceY) {
        double exposedStart = Mth.clamp(
                (coveredSurfaceY - player.position().y)
                        / Math.max(player.getBbHeight(), 0.1D),
                0.0D,
                1.2D);
        return Mth.clamp(
                (int) Math.floor(exposedStart * MudBodyPart.BANDS),
                0,
                MudBodyPart.BANDS);
    }

    private static boolean hasWaterWashTargets(
            ServerPlayer player, MudPlayerData data) {
        if (data.hasPersistentCoverage()) {
            return true;
        }
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            var stack = player.getItemBySlot(slot);
            if (ArmorMudManager.validArmor(stack, slot)
                    && !ArmorMudManager.data(stack).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean washWaterTouchedArmor(
            ServerPlayer player,
            OrdinaryWaterProbe ordinaryWaterProbe,
            SableCompat.WaterVolumeProbe sableWaterProbe,
            MudEntityGeometry.SurfacePixelSampler[] geometry) {
        boolean changed = false;
        Level level = player.level();
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            var stack = player.getItemBySlot(slot);
            if (!ArmorMudManager.validArmor(stack, slot)) {
                continue;
            }
            ArmorMudData original = ArmorMudManager.data(stack);
            if (original.isEmpty()) {
                continue;
            }
            ArmorMudData.Builder builder = original.toBuilder();
            original.forEach((cell, coverage, medium) -> {
                MudBodyPart part = MudSurfaceLayout.part(cell);
                MudSurface surface = MudSurfaceLayout.surface(cell);
                int row = MudSurfaceLayout.row(cell);
                if (!ArmorMudManager.slotOwnsSurface(slot, part, surface, row)) {
                    return;
                }
                MudEntityGeometry.SurfacePixelSampler partGeometry = geometry[part.ordinal()];
                Vec3 point = partGeometry.point(
                        part, surface, row, MudSurfaceLayout.column(cell));
                Vec3 normal = partGeometry.outwardNormal(surface);
                if (isWaterTouching(
                        point.add(normal.scale(ArmorMudManager.surfaceOffset(slot))),
                        ordinaryWaterProbe,
                        sableWaterProbe)) {
                    builder.wash(
                            cell,
                            WATER_SURFACE_WASH_AMOUNT
                                    * MudMediumRuntime.waterWashMultiplier(level, medium),
                            CLEAR_THRESHOLD,
                            player.tickCount);
                }
            });
            if (builder.changed()) {
                ArmorMudManager.store(stack, builder.build());
                changed = true;
            }
        }
        return changed;
    }

    private static void washRainExposedArmor(
            ServerPlayer player, MudPlayerData playerData, int minExposedBand) {
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            var stack = player.getItemBySlot(slot);
            if (!ArmorMudManager.validArmor(stack, slot)) {
                continue;
            }
            ArmorMudData original = ArmorMudManager.data(stack);
            if (original.isEmpty()) {
                continue;
            }
            ArmorMudData.Builder builder = original.toBuilder();
            original.forEach((cell, coverage, medium) -> {
                MudBodyPart part = MudSurfaceLayout.part(cell);
                MudSurface surface = MudSurfaceLayout.surface(cell);
                int row = MudSurfaceLayout.row(cell);
                if (!ArmorMudManager.slotOwnsSurface(slot, part, surface, row)
                        || MudSurfaceLayout.legacyBand(part, surface, row) < minExposedBand
                        || playerData.armorContactCoverage(slot, cell) > 0) {
                    return;
                }
                builder.wash(
                        cell,
                        RAIN_SURFACE_WASH_AMOUNT
                                * MudMediumRuntime.rainWashMultiplier(
                                        player.level(), medium),
                        CLEAR_THRESHOLD,
                        player.tickCount / 9);
            });
            if (builder.changed()) {
                ArmorMudManager.store(stack, builder.build());
            }
        }
    }

    private static boolean isWaterTouching(
            Level level,
            Vec3 point,
            SableCompat.WaterVolumeProbe sableWaterProbe) {
        return isWaterTouching(
                level, point, WATER_TOUCH_RADIUS, 0.030D, null, sableWaterProbe);
    }

    private static boolean isWaterTouching(
            Vec3 point,
            OrdinaryWaterProbe ordinaryWaterProbe,
            SableCompat.WaterVolumeProbe sableWaterProbe) {
        return isWaterTouching(
                null, point, WATER_TOUCH_RADIUS, 0.030D,
                ordinaryWaterProbe, sableWaterProbe);
    }

    private static boolean isWaterTouching(
            Level level,
            Vec3 point,
            double radius,
            double fluidTolerance,
            OrdinaryWaterProbe ordinaryWaterProbe,
            SableCompat.WaterVolumeProbe sableWaterProbe) {
        return isOrdinaryWaterAt(level, ordinaryWaterProbe, point, fluidTolerance)
                || isOrdinaryWaterAt(level, ordinaryWaterProbe,
                        point.add(radius, 0.0D, 0.0D), fluidTolerance)
                || isOrdinaryWaterAt(level, ordinaryWaterProbe,
                        point.add(-radius, 0.0D, 0.0D), fluidTolerance)
                || isOrdinaryWaterAt(level, ordinaryWaterProbe,
                        point.add(0.0D, radius, 0.0D), fluidTolerance)
                || isOrdinaryWaterAt(level, ordinaryWaterProbe,
                        point.add(0.0D, -radius, 0.0D), fluidTolerance)
                || isOrdinaryWaterAt(level, ordinaryWaterProbe,
                        point.add(0.0D, 0.0D, radius), fluidTolerance)
                || isOrdinaryWaterAt(level, ordinaryWaterProbe,
                        point.add(0.0D, 0.0D, -radius), fluidTolerance)
                || isSableWaterAt(sableWaterProbe, point)
                || isSableWaterAt(sableWaterProbe, point.add(radius, 0.0D, 0.0D))
                || isSableWaterAt(sableWaterProbe, point.add(-radius, 0.0D, 0.0D))
                || isSableWaterAt(sableWaterProbe, point.add(0.0D, radius, 0.0D))
                || isSableWaterAt(sableWaterProbe, point.add(0.0D, -radius, 0.0D))
                || isSableWaterAt(sableWaterProbe, point.add(0.0D, 0.0D, radius))
                || isSableWaterAt(sableWaterProbe, point.add(0.0D, 0.0D, -radius));
    }

    private static boolean isOrdinaryWaterAt(
            Level level, OrdinaryWaterProbe probe, Vec3 point, double tolerance) {
        return probe != null ? probe.contains(point, tolerance) : isWaterAt(level, point, tolerance);
    }

    private static boolean isSableWaterAt(
            SableCompat.WaterVolumeProbe probe, Vec3 point) {
        return probe != null && probe.contains(point, 0.004D);
    }

    private static boolean isWaterAt(
            Level level, Vec3 point, double fluidTolerance) {
        BlockPos pos = BlockPos.containing(point);
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER)) {
            return false;
        }
        double fluidTop = pos.getY() + fluid.getHeight(level, pos);
        return point.y <= fluidTop + fluidTolerance
                && point.y >= pos.getY() - fluidTolerance;
    }

    static final class OrdinaryWaterProbe {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final float[] heights;
        private final int waterCells;

        private OrdinaryWaterProbe(int minX, int minY, int minZ,
                int sizeX, int sizeY, int sizeZ, float[] heights, int waterCells) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.heights = heights;
            this.waterCells = waterCells;
        }

        static OrdinaryWaterProbe capture(Level level, AABB bounds) {
            int minX = Mth.floor(bounds.minX);
            int minY = Mth.floor(bounds.minY);
            int minZ = Mth.floor(bounds.minZ);
            int maxX = Mth.floor(bounds.maxX);
            int maxY = Mth.floor(bounds.maxY);
            int maxZ = Mth.floor(bounds.maxZ);
            int sizeX = Math.max(1, maxX - minX + 1);
            int sizeY = Math.max(1, maxY - minY + 1);
            int sizeZ = Math.max(1, maxZ - minZ + 1);
            float[] heights = new float[sizeX * sizeY * sizeZ];
            int waterCells = 0;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        cursor.set(x, y, z);
                        FluidState fluid = level.getFluidState(cursor);
                        if (!fluid.is(FluidTags.WATER)) {
                            continue;
                        }
                        int index = (x - minX)
                                + (z - minZ) * sizeX
                                + (y - minY) * sizeX * sizeZ;
                        heights[index] = fluid.getHeight(level, cursor);
                        waterCells++;
                    }
                }
            }
            return new OrdinaryWaterProbe(
                    minX, minY, minZ, sizeX, sizeY, sizeZ, heights, waterCells);
        }

        boolean isEmpty() {
            return waterCells == 0;
        }

        boolean contains(Vec3 point, double tolerance) {
            int x = Mth.floor(point.x);
            int y = Mth.floor(point.y);
            int z = Mth.floor(point.z);
            int localX = x - minX;
            int localY = y - minY;
            int localZ = z - minZ;
            if (localX < 0 || localX >= sizeX
                    || localY < 0 || localY >= sizeY
                    || localZ < 0 || localZ >= sizeZ) {
                return false;
            }
            float height = heights[localX + localZ * sizeX + localY * sizeX * sizeZ];
            return height > 0.0F
                    && point.y <= y + height + tolerance
                    && point.y >= y - tolerance;
        }
    }

    public static final class WaterContactProbe {
        private final OrdinaryWaterProbe ordinary;
        private final SableCompat.WaterVolumeProbe sable;

        private WaterContactProbe(
                OrdinaryWaterProbe ordinary, SableCompat.WaterVolumeProbe sable) {
            this.ordinary = ordinary;
            this.sable = sable;
        }

        public boolean isEmpty() {
            return ordinary.isEmpty() && (sable == null || sable.isEmpty());
        }

        public boolean touches(Vec3 point, double radius, double fluidTolerance) {
            return isWaterTouching(
                    null, point, radius, fluidTolerance, ordinary, sable);
        }
    }
}
