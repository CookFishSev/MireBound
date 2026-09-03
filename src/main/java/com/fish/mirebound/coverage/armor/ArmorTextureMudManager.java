package com.fish.mirebound.coverage.armor;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.compat.curios.CuriosCompat;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableCompat.SinkingSample;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.ArmorTextureMudData;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudCoverageRules;
import com.fish.mirebound.mud.MudEnchantmentEffects;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.ArmorTextureContactPayload;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.water.MudWashingSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Validates client-rendered armor UV samples against the authoritative server world. */
public final class ArmorTextureMudManager {
    private static final double MAX_ORIGIN_ERROR_SQR = 16.0D;
    private static final double MAX_SAMPLE_OFFSET_SQR = 16.0D;
    private static final double WATER_RADIUS = 0.035D;
    private static final int CONTACT_CAPTURE_INTERVAL_TICKS = 2;
    private static final float WATER_WASH_AMOUNT_PER_TICK = 0.032F;
    private static final float RAIN_WASH_AMOUNT_PER_TICK = 0.018F;
    private static final float CLEAR_THRESHOLD = 0.00025F;
    private ArmorTextureMudManager() {
    }

    public static void handleSamples(ServerPlayer player, ArmorTextureContactPayload payload) {
        if (!payload.validTarget() || !payload.validCandidateCount()
                || payload.texture() == null || !payload.validDimensions()
                || !payload.validOrigin()
                || payload.samples().isEmpty() || payload.samples().size() > ArmorTextureContactPayload.MAX_SAMPLES) {
            return;
        }

        EquipmentSlot slot = payload.targetType() == ArmorTextureContactPayload.TargetType.ARMOR
                ? slot(payload.slotIndex())
                : null;
        ItemStack stack = slot == null
                ? CuriosCompat.stack(player, payload.curiosIdentifier(), payload.curiosIndex(),
                        payload.curiosCosmetic())
                : player.getItemBySlot(slot);
        // Texture contacts originate from the actual equipment renderer. A wearable slot may
        // contain a cosmetic ItemStack that is not an ArmorItem, so only require a real stack.
        // validOrigin() above already rejected non-finite origins, so this range test is authoritative.
        if (stack.isEmpty() || player.position().distanceToSqr(payload.origin()) > MAX_ORIGIN_ERROR_SQR) {
            return;
        }

        ArmorTextureMudData original = ArmorMudManager.textureData(stack);
        ArmorTextureMudData.Layer dirtyLayer = original.layer(payload.texture(), payload.width(), payload.height());
        ArmorTextureMudData.Builder builder = original.toBuilder();
        boolean stainProtected = MudEnchantmentEffects.preventsArmorStaining(player, stack);
        float waterWashAmount = sampledWashAmount(original, payload, WATER_WASH_AMOUNT_PER_TICK);
        float rainWashAmount = sampledWashAmount(original, payload, RAIN_WASH_AMOUNT_PER_TICK);
        MudWashingSystem.WaterContactProbe waterProbe = MudWashingSystem.captureWaterContact(
                player.level(), sampleBounds(payload).inflate(WATER_RADIUS + 0.01D), player);
        int maximumPixel = payload.width() * payload.height();
        int coverageDomain = MudCoverageRules.textureDomain(
                payload.texture().hashCode(), payload.width(), payload.height());
        Map<BlockPos, Long> visualSourceCache = new HashMap<>();
        for (ArmorTextureContactPayload.Sample sample : payload.samples()) {
            if (sample.pixel() < 0 || sample.pixel() >= maximumPixel) {
                continue;
            }
            Vec3 offset = new Vec3(sample.offsetX(), sample.offsetY(), sample.offsetZ());
            if (!finite(offset) || offset.lengthSqr() > MAX_SAMPLE_OFFSET_SQR) {
                continue;
            }
            Vec3 point = payload.origin().add(offset);
            if (!waterProbe.isEmpty() && waterProbe.touches(point, WATER_RADIUS, 0.040D)) {
                SinkingMedium dirtyMedium = dirtyLayer == null
                        ? SinkingMedium.MUD : dirtyLayer.mediumAt(sample.pixel());
                builder.wash(payload.texture(), payload.width(), payload.height(), sample.pixel(),
                        variedWashAmount(waterWashAmount, sample.pixel(), player.tickCount)
                                * MudMediumRuntime.waterWashMultiplier(player.level(), dirtyMedium),
                        CLEAR_THRESHOLD);
                continue;
            }
            MediumContact contact = sinkingMediumAt(player, point, visualSourceCache);
            SinkingMedium medium = contact == null ? null : contact.medium();
            if (com.fish.mirebound.assimilation.AssimilationConfig.appliesTo(medium)
                    && !com.fish.mirebound.assimilation.AssimilationConfig.profileFor(medium)
                            .ordinaryCoverageEnabled()) {
                medium = null;
            }
            if (medium != null && !stainProtected
                    && MudCoverageRules.allowsPixel(
                            player.level(), medium, coverageDomain,
                            sample.pixel(), maximumPixel)) {
                builder.mark(payload.texture(), payload.width(), payload.height(), sample.pixel(),
                        MudCoverageRules.contactTarget(player.level(), medium, 1.0F), medium,
                        contact.visualSource());
            } else if (player.serverLevel().isRainingAt(BlockPos.containing(point))) {
                builder.wash(payload.texture(), payload.width(), payload.height(), sample.pixel(),
                        variedWashAmount(rainWashAmount, sample.pixel(), player.tickCount)
                                * MudMediumRuntime.rainWashMultiplier(player.level(),
                                        dirtyLayer == null ? SinkingMedium.MUD : dirtyLayer.mediumAt(sample.pixel())),
                        CLEAR_THRESHOLD);
            }
        }
        if (builder.changed()) {
            ArmorMudManager.storeTextureData(stack, builder.build());
            if (slot == null) {
                CuriosCompat.commit(player, payload.curiosIdentifier(), payload.curiosIndex(),
                        payload.curiosCosmetic(), stack);
            }
        }
    }

    private static float sampledWashAmount(ArmorTextureMudData data,
            ArmorTextureContactPayload payload, float perTick) {
        ArmorTextureMudData.Layer layer = data.layer(payload.texture(), payload.width(), payload.height());
        if (layer == null || layer.isEmpty()) {
            return perTick * CONTACT_CAPTURE_INTERVAL_TICKS;
        }
        int sent = Math.max(1, payload.samples().size());
        float revisitTicks = CONTACT_CAPTURE_INTERVAL_TICKS
                * Math.max(sent, payload.candidateCount()) / (float) sent;
        return Mth.clamp(perTick * revisitTicks,
                perTick * CONTACT_CAPTURE_INTERVAL_TICKS, 1.0F);
    }

    private static AABB sampleBounds(ArmorTextureContactPayload payload) {
        double minX = payload.origin().x;
        double minY = payload.origin().y;
        double minZ = payload.origin().z;
        double maxX = minX;
        double maxY = minY;
        double maxZ = minZ;
        for (ArmorTextureContactPayload.Sample sample : payload.samples()) {
            Vec3 offset = new Vec3(sample.offsetX(), sample.offsetY(), sample.offsetZ());
            if (!finite(offset) || offset.lengthSqr() > MAX_SAMPLE_OFFSET_SQR) {
                continue;
            }
            Vec3 point = payload.origin().add(offset);
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static float variedWashAmount(float amount, int pixel, int tick) {
        int value = pixel * 73428767 ^ (tick / 5) * 9122719;
        value ^= value >>> 13;
        value *= 1274126177;
        value ^= value >>> 16;
        float noise = (value & 1023) / 1023.0F;
        return Mth.clamp(amount * (0.58F + noise * 0.72F), 0.0F, 1.0F);
    }

    private static EquipmentSlot slot(int index) {
        EquipmentSlot[] slots = ArmorMudManager.armorSlots();
        return index >= 0 && index < slots.length ? slots[index] : null;
    }

    private static MediumContact sinkingMediumAt(ServerPlayer player, Vec3 point,
            Map<BlockPos, Long> visualSourceCache) {
        Level level = player.level();
        BlockPos pos = BlockPos.containing(point);
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium != null && MudBlock.containsLocalPoint(
                level, pos, state, medium,
                point.subtract(pos.getX(), pos.getY(), pos.getZ()),
                0.040D)) {
            long visualSource = state.getBlock() instanceof AdaptiveMudBlock
                    ? visualSourceCache.computeIfAbsent(pos, ignored ->
                            MudVisualSource.capture(level, pos,
                                    MudBlock.surfaceDirection(state, medium)))
                    : MudVisualSource.NONE;
            return new MediumContact(medium, visualSource);
        }

        SinkingSample sample = SableCompat.sampleSinking(level, point, player);
        if (sample == null) {
            return null;
        }
        return new MediumContact(sample.medium(), sample.visualSource());
    }

    private record MediumContact(SinkingMedium medium, long visualSource) {
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

}
