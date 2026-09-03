package com.fish.mirebound.client;

import com.fish.mirebound.network.payload.SculkClampStatePayload;
import com.fish.mirebound.mud.SculkMireHudProgress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Interpolates the small server-authoritative sculk restraint state. */
final class ClientSculkClampManager {
    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();

    private ClientSculkClampManager() {
    }

    static void accept(SculkClampStatePayload payload) {
        Entry entry = ENTRIES.computeIfAbsent(payload.entityId(), Entry::new);
        boolean starting = payload.active() && !entry.active;
        entry.active = payload.active();
        entry.targetPoint = payload.surfacePoint();
        entry.normal = payload.surfaceNormal();
        entry.axisX = orthogonalized(payload.surfaceAxisX(), entry.normal,
                new Vec3(1.0D, 0.0D, 0.0D));
        entry.axisZ = normalized(entry.normal.cross(entry.axisX),
                payload.surfaceAxisZ());
        entry.visualSource = payload.visualSource();
        entry.radius = payload.radius();
        entry.height = payload.height();
        entry.renderDistance = payload.renderDistance();
        entry.emergeTicks = payload.emergeTicks();
        entry.retractTicks = payload.retractTicks();
        entry.closeTicks = payload.closeTicks();
        entry.openTicks = payload.openTicks();
        entry.remainingTicks = payload.remainingTicks();
        entry.maximumTicks = payload.maximumTicks();
        if (starting) {
            entry.countdownMaximumTicks = Math.max(1, payload.remainingTicks());
        } else if (payload.active()) {
            entry.countdownMaximumTicks = Math.max(
                    entry.countdownMaximumTicks, payload.remainingTicks());
        }
        if (!entry.initialized) {
            entry.point = entry.targetPoint;
            entry.previousPoint = entry.point;
            entry.initialized = true;
        }
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Iterator<Entry> iterator = ENTRIES.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (minecraft.level != null
                    && minecraft.level.getEntity(entry.entityId) == null) {
                entry.active = false;
            }
            entry.previousPoint = entry.point;
            entry.previousEmergence = entry.emergence;
            entry.previousGrip = entry.grip;
            entry.point = lerp(entry.point, entry.targetPoint, 0.42D);
            if (entry.active) {
                entry.emergence = clamp01(entry.emergence + 1.0D / entry.emergeTicks);
                if (entry.emergence >= 0.72D) {
                    entry.grip = clamp01(entry.grip + 1.0D / entry.closeTicks);
                }
            } else {
                entry.grip = clamp01(entry.grip - 1.0D / entry.openTicks);
                if (entry.grip <= 0.001D) {
                    entry.emergence = clamp01(entry.emergence - 1.0D / entry.retractTicks);
                }
            }
            if (entry.active && entry.remainingTicks > 0) {
                entry.remainingTicks--;
            }
            if (!entry.active && entry.emergence <= 0.0D) {
                iterator.remove();
            }
        }
    }

    static List<View> views(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ENTRIES.isEmpty()) {
            return List.of();
        }
        List<View> views = new ArrayList<>(ENTRIES.size());
        for (Entry entry : ENTRIES.values()) {
            double emergence = lerp(entry.previousEmergence, entry.emergence, partialTick);
            double grip = lerp(entry.previousGrip, entry.grip, partialTick);
            if (emergence <= 0.001D) {
                continue;
            }
            Vec3 point = lerp(entry.previousPoint, entry.point, partialTick);
            Entity target = minecraft.level.getEntity(entry.entityId);
            if (target != null) {
                Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.42D, 0.0D);
                Vec3 offset = targetCenter.subtract(point);
                point = point.add(offset.subtract(entry.normal.scale(offset.dot(entry.normal))));
            }
            views.add(new View(entry.entityId, point, entry.normal, entry.axisX, entry.axisZ,
                    entry.visualSource,
                    entry.radius, entry.height, entry.renderDistance, emergence, grip,
                    entry.remainingTicks, entry.maximumTicks));
        }
        return List.copyOf(views);
    }

    static float hudProgress(int entityId, float partialTick) {
        Entry entry = ENTRIES.get(entityId);
        if (entry == null || !entry.active) {
            return -1.0F;
        }
        return SculkMireHudProgress.restraint(
                entry.remainingTicks, entry.countdownMaximumTicks, partialTick);
    }

    static void reset() {
        ENTRIES.clear();
    }

    private static Vec3 orthogonalized(Vec3 value, Vec3 normal, Vec3 fallback) {
        Vec3 projected = value.subtract(normal.scale(value.dot(normal)));
        if (projected.lengthSqr() < 1.0E-8D) {
            projected = fallback.subtract(normal.scale(fallback.dot(normal)));
        }
        return normalized(projected, fallback);
    }

    private static Vec3 normalized(Vec3 value, Vec3 fallback) {
        return value == null || value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }

    private static Vec3 lerp(Vec3 first, Vec3 second, double amount) {
        return first.scale(1.0D - amount).add(second.scale(amount));
    }

    private static double lerp(double first, double second, double amount) {
        return first + (second - first) * amount;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    record View(int entityId, Vec3 point, Vec3 normal, Vec3 axisX, Vec3 axisZ,
            long visualSource,
            double radius, double height, double renderDistance,
            double emergence, double grip,
            int remainingTicks, int maximumTicks) {
    }

    private static final class Entry {
        private final int entityId;
        private boolean active;
        private boolean initialized;
        private Vec3 point = Vec3.ZERO;
        private Vec3 previousPoint = Vec3.ZERO;
        private Vec3 targetPoint = Vec3.ZERO;
        private Vec3 normal = new Vec3(0.0D, 1.0D, 0.0D);
        private Vec3 axisX = new Vec3(1.0D, 0.0D, 0.0D);
        private Vec3 axisZ = new Vec3(0.0D, 0.0D, 1.0D);
        private long visualSource;
        private double radius = 0.68D;
        private double height = 0.58D;
        private double renderDistance = 96.0D;
        private double emergence;
        private double previousEmergence;
        private double grip;
        private double previousGrip;
        private int emergeTicks = 10;
        private int retractTicks = 14;
        private int closeTicks = 10;
        private int openTicks = 8;
        private int remainingTicks;
        private int maximumTicks = 1;
        private int countdownMaximumTicks = 1;

        private Entry(int entityId) {
            this.entityId = entityId;
        }
    }
}
