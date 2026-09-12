package com.fish.mirebound.coverage.armor;

import com.fish.mirebound.mud.MudCoverageRules;
import com.fish.mirebound.mud.MudEnchantmentEffects;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.network.payload.EquipmentSurfaceContactPayload;
import com.fish.mirebound.registry.ModDataComponents;
import com.fish.mirebound.water.MudWashingSystem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Revalidates actual surface points; only the server changes item-owned coverage. */
public final class EquipmentSurfaceService {
    private static final Map<ServerPlayer, Map<EquipmentSurfaceTarget, TrackedItem>> CONTACTS = new WeakHashMap<>();
    private record TrackedItem(ItemStack stack, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            EquipmentSurfaceContacts contacts) {}
    private EquipmentSurfaceService() {}
    public static EquipmentSurfaceData data(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.EQUIPMENT_SURFACE_MUD.get(), EquipmentSurfaceData.EMPTY);
    }
    public static boolean validPoint(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
    public static void handle(ServerPlayer player, EquipmentSurfaceContactPayload payload) {
        if (!player.isAlive() || player.isSpectator() || MudPhysics.isPollutionSuppressed(player)
                || !payload.target().valid() || !validPoint(payload.origin())
                || payload.origin().distanceToSqr(player.position()) > 4.0D
                || payload.samples().isEmpty() && payload.patches().isEmpty()) return;
        ItemStack stack = payload.target().resolve(player);
        if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(payload.item())) return;
        EquipmentSurfaceData old = data(stack);
        Map<EquipmentSurfaceTarget, TrackedItem> playerContacts = CONTACTS.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        TrackedItem tracked = playerContacts.get(payload.target());
        if (tracked == null || tracked.stack != stack || !tracked.dimension.equals(player.level().dimension())) {
            tracked = new TrackedItem(stack, player.level().dimension(), new EquipmentSurfaceContacts());
            playerContacts.put(payload.target(), tracked);
            while (playerContacts.size() > 32) playerContacts.remove(playerContacts.keySet().iterator().next());
        }
        var builder = old.toBuilder();
        if (!payload.patches().isEmpty() && !tracked.contacts.acceptFullPass(player.tickCount)) return;
        if (payload.completeSurface() && !payload.patches().isEmpty()) {
            builder.surfaceCells(payload.surfaceCells());
        }
        AABB bounds = new AABB(payload.origin(), payload.origin());
        for (var sample : payload.samples()) {
            Vec3 offset = new Vec3(sample.x(), sample.y(), sample.z());
            if (validPoint(offset) && offset.lengthSqr() <= 16) {
                Vec3 point = payload.origin().add(offset);
                bounds = bounds.minmax(new AABB(point, point));
            }
        }
        for (EquipmentSurfacePatch patch : payload.patches()) {
            for (Vec3 corner : new Vec3[] {patch.a(), patch.b(), patch.c(), patch.d()}) {
                Vec3 point = payload.origin().add(corner);
                bounds = bounds.minmax(new AABB(point, point));
            }
        }
        var water = MudWashingSystem.captureWaterContact(player.level(), bounds.inflate(.07D), player);
        var sable = com.fish.mirebound.compat.sable.SableCompat.sinkingVolumeProbe(player.level(), bounds.inflate(.07D), player);
        Map<BlockPos, Long> sources = new HashMap<>();
        HashSet<SampleKey> seen = new HashSet<>();
        boolean protectedItem = MudEnchantmentEffects.preventsArmorStaining(player, stack);
        EquipmentSurfaceContacts contacts = tracked.contacts;
        EquipmentWallTransfer wallTransfer = new EquipmentWallTransfer(payload, old, contacts, player.tickCount,
                com.fish.mirebound.mud.MudPhysicsSettings.wallStainUpdateIntervalTicks(),
                com.fish.mirebound.mud.MudPhysicsSettings.wallStainMinimumSourceCoverage());
        visitSamples(payload, (face, cell, offset) -> {
            if (face == 0 || cell < 0 || cell >= 256
                    || !validPoint(offset) || offset.lengthSqr() > 16
                    || !seen.add(new SampleKey(face, cell))) return;
            Vec3 point = payload.origin().add(offset);
            contacts.offer(face, cell, point.subtract(player.position()), player.tickCount);
            var previous = old.cell(face, cell);
            if (!water.isEmpty() && water.touches(point, 0.028D, 0.04D)) {
                if (previous != null) builder.wash(face, cell, Math.max(1, Math.round(24F
                        * MudMediumRuntime.waterWashMultiplier(player.level(), com.fish.mirebound.mud.SinkingMedium.byId(previous.medium())))));
                return;
            }
            var contact = ArmorTextureMudManager.sinkingMediumAt(player, point, sources, sable);
            if (contact != null && !protectedItem
                    && (!com.fish.mirebound.assimilation.AssimilationConfig.appliesTo(contact.medium())
                        || com.fish.mirebound.assimilation.AssimilationConfig.profileFor(contact.medium()).ordinaryCoverageEnabled())
                    && MudCoverageRules.allowsPixel(player.level(), contact.medium(),
                        Long.hashCode(face), cell, 256)) {
                int strength = Math.round(255F * MudCoverageRules.contactTarget(player.level(), contact.medium(), 1F));
                builder.stain(face, cell, strength, contact.medium().id(), contact.visualSource());
            } else if (previous != null && player.serverLevel().isRainingAt(BlockPos.containing(point))) {
                builder.wash(face, cell, Math.max(1, Math.round(8F
                        * MudMediumRuntime.rainWashMultiplier(player.level(), com.fish.mirebound.mud.SinkingMedium.byId(previous.medium())))));
            }
            if (contact == null) wallTransfer.offer(face, cell, point);
        });
        for (var source : com.fish.mirebound.stain.MudWallStainSystem.transferEquipment(player, wallTransfer.sources())) {
            builder.transferToWall(source.face(), source.cell(),
                    com.fish.mirebound.mud.MudPhysicsSettings.wallStainTransferAmount(),
                    com.fish.mirebound.mud.MudPhysicsSettings.wallStainMinimumSourceCoverage());
        }
        if (builder.changed()) {
            EquipmentSurfaceData result = builder.build();
            if (result.isEmpty() && old.isEmpty()) return;
            if (result.isEmpty()) stack.remove(ModDataComponents.EQUIPMENT_SURFACE_MUD.get());
            else stack.set(ModDataComponents.EQUIPMENT_SURFACE_MUD.get(), result);
            payload.target().commit(player, stack);
        }
    }

    @FunctionalInterface
    interface SampleVisitor {
        void visit(long face, int cell, Vec3 offset);
    }

    static void visitSamples(EquipmentSurfaceContactPayload payload, SampleVisitor visitor) {
        for (var sample : payload.samples()) {
            visitor.visit(sample.face(), sample.cell(), new Vec3(sample.x(), sample.y(), sample.z()));
        }
        for (EquipmentSurfacePatch patch : payload.patches()) {
            for (int probe : patch.probes()) visitor.visit(patch.face(), EquipmentSurfacePatch.cell(probe), patch.point(probe));
        }
    }

    public static boolean washFromWaterGun(ServerPlayer player, Vec3 impact, float radius, float amount) {
        Map<EquipmentSurfaceTarget, TrackedItem> tracked = CONTACTS.get(player);
        if (tracked == null || !validPoint(impact) || !Float.isFinite(radius) || radius <= 0
                || !Float.isFinite(amount) || amount <= 0) return false;
        boolean changed = false;
        for (var entry : tracked.entrySet()) {
            TrackedItem item = entry.getValue();
            if (!item.dimension.equals(player.level().dimension()) || entry.getKey().resolve(player) != item.stack) continue;
            EquipmentSurfaceData original = data(item.stack);
            if (original.isEmpty()) continue;
            var builder = original.toBuilder();
            Vec3 relative = impact.subtract(player.position());
            for (var contact : item.contacts.near(relative, radius, player.tickCount)) {
                var cell = original.cell(contact.key().face(), contact.key().cell());
                if (cell == null) continue;
                double falloff = Math.max(.18D, 1D - relative.distanceTo(contact.offset()) / radius);
                int wash = Math.max(1, (int)Math.round(255 * amount * falloff * MudMediumRuntime.waterWashMultiplier(
                        player.level(), com.fish.mirebound.mud.SinkingMedium.byId(cell.medium()))));
                builder.wash(cell.face(), cell.index(), wash);
            }
            if (builder.changed()) {
                EquipmentSurfaceData result = builder.build();
                if (result.isEmpty()) item.stack.remove(ModDataComponents.EQUIPMENT_SURFACE_MUD.get());
                else item.stack.set(ModDataComponents.EQUIPMENT_SURFACE_MUD.get(), result);
                entry.getKey().commit(player, item.stack);
                changed = true;
            }
        }
        return changed;
    }

    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        CONTACTS.remove(event.getEntity());
    }
    public static void onStop(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { CONTACTS.clear(); }
    private record SampleKey(long face, int cell) {}
}
