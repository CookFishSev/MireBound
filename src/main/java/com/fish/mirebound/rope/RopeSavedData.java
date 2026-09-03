package com.fish.mirebound.rope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent, reconstructable state for non-entity rope chains in one dimension. */
public final class RopeSavedData extends SavedData {
    private static final String DATA_NAME = "mirebound_ropes";
    private static final int DATA_VERSION = 1;
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_NODES = RopeProperties.MAX_SEGMENTS + 1;
    private static final Factory<RopeSavedData> FACTORY =
            new Factory<>(RopeSavedData::new, RopeSavedData::load);

    private final Map<Integer, State> states = new LinkedHashMap<>();
    private int nextId = 1;

    private RopeSavedData() {
    }

    public static RopeSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public int nextId() {
        return nextId;
    }

    public List<State> states() {
        return List.copyOf(states.values());
    }

    public void replace(int nextId, Collection<State> replacement) {
        states.clear();
        int highest = 0;
        for (State state : replacement) {
            if (state == null || state.id() <= 0 || states.size() >= MAX_ENTRIES) {
                continue;
            }
            states.put(state.id(), state);
            highest = Math.max(highest, state.id());
        }
        this.nextId = Math.max(1, Math.max(nextId, highest + 1));
        setDirty();
    }

    private static RopeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RopeSavedData data = new RopeSavedData();
        data.nextId = Math.max(1, tag.getInt("NextId"));
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        int count = Math.min(MAX_ENTRIES, entries.size());
        for (int index = 0; index < count; index++) {
            State state = readState(entries.getCompound(index));
            if (state != null && !data.states.containsKey(state.id())) {
                data.states.put(state.id(), state);
                data.nextId = Math.max(data.nextId, state.id() + 1);
            }
        }
        if (tag.getInt("Version") < DATA_VERSION) {
            data.setDirty();
        }
        return data;
    }

    private static State readState(CompoundTag tag) {
        int id = tag.getInt("Id");
        if (id <= 0) {
            return null;
        }
        List<Vec3> nodes = readVectors(tag.getList("Nodes", Tag.TAG_COMPOUND));
        if (nodes.size() < 2 || nodes.size() > MAX_NODES) {
            return null;
        }
        List<Vec3> velocities = readVectors(tag.getList("Velocities", Tag.TAG_COMPOUND));
        if (velocities.size() != nodes.size()) {
            velocities = zeroVectors(nodes.size());
        }
        CompoundTag propertiesTag = tag.contains("Properties", Tag.TAG_COMPOUND)
                ? tag.getCompound("Properties") : new CompoundTag();
        RopeProperties properties = readProperties(propertiesTag, nodes.size() - 1);
        if (properties.nodeCount() != nodes.size()) {
            return null;
        }
        UUID owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        BlockPos rescueAnchorPos = tag.contains("RescueAnchorX", Tag.TAG_INT)
                && tag.contains("RescueAnchorY", Tag.TAG_INT)
                && tag.contains("RescueAnchorZ", Tag.TAG_INT)
                ? new BlockPos(tag.getInt("RescueAnchorX"),
                        tag.getInt("RescueAnchorY"), tag.getInt("RescueAnchorZ")) : null;
        return new State(id, owner, Math.max(0, tag.getInt("Age")), properties,
                nodes, velocities, readAnchors(tag.getList("Anchors", Tag.TAG_COMPOUND)),
                rescueAnchorPos);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", DATA_VERSION);
        tag.putInt("NextId", nextId);
        ListTag entries = new ListTag();
        for (State state : states.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Id", state.id());
            if (state.ownerId() != null) {
                entry.putUUID("Owner", state.ownerId());
            }
            entry.putInt("Age", state.age());
            entry.put("Properties", writeProperties(state.properties()));
            entry.put("Nodes", writeVectors(state.nodes()));
            entry.put("Velocities", writeVectors(state.velocities()));
            entry.put("Anchors", writeAnchors(state.anchors()));
            if (state.rescueAnchorPos() != null) {
                entry.putInt("RescueAnchorX", state.rescueAnchorPos().getX());
                entry.putInt("RescueAnchorY", state.rescueAnchorPos().getY());
                entry.putInt("RescueAnchorZ", state.rescueAnchorPos().getZ());
            }
            entries.add(entry);
        }
        tag.put("Entries", entries);
        return tag;
    }

    private static RopeProperties readProperties(CompoundTag tag, int fallbackSegments) {
        RopeProperties defaults = RopeProperties.DEFAULT.withSegmentCount(fallbackSegments);
        return new RopeProperties(
                clampInt(tag, "SegmentCount", defaults.segmentCount(), 1, RopeProperties.MAX_SEGMENTS),
                positive(tag, "SegmentLength", defaults.segmentLength(), 0.125D, 4.0D),
                positive(tag, "CollisionRadius", defaults.collisionRadius(), 0.01D, 1.0D),
                positive(tag, "GravityPerTick", defaults.gravityPerTick(), 0.0D, 1.0D),
                clamp(tag, "VelocityDamping", defaults.velocityDamping(), 0.0D, 1.0D),
                clamp(tag, "ContactVelocityDamping", defaults.contactVelocityDamping(), 0.0D, 1.0D),
                clamp(tag, "DragVelocityDamping", defaults.dragVelocityDamping(), 0.0D, 1.0D),
                clampInt(tag, "CollisionRefreshTicks", defaults.collisionRefreshTicks(), 1, 20),
                clampInt(tag, "MaximumCollisionBlockSamples", defaults.maximumCollisionBlockSamples(),
                        64, 16_384),
                positive(tag, "MaximumThrowSpeed", defaults.maximumThrowSpeed(), 0.05D, 8.0D));
    }

    private static CompoundTag writeProperties(RopeProperties properties) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SegmentCount", properties.segmentCount());
        tag.putDouble("SegmentLength", properties.segmentLength());
        tag.putDouble("CollisionRadius", properties.collisionRadius());
        tag.putDouble("GravityPerTick", properties.gravityPerTick());
        tag.putDouble("VelocityDamping", properties.velocityDamping());
        tag.putDouble("ContactVelocityDamping", properties.contactVelocityDamping());
        tag.putDouble("DragVelocityDamping", properties.dragVelocityDamping());
        tag.putInt("CollisionRefreshTicks", properties.collisionRefreshTicks());
        tag.putInt("MaximumCollisionBlockSamples", properties.maximumCollisionBlockSamples());
        tag.putDouble("MaximumThrowSpeed", properties.maximumThrowSpeed());
        return tag;
    }

    private static ListTag writeVectors(List<Vec3> vectors) {
        ListTag result = new ListTag();
        int count = Math.min(MAX_NODES, vectors == null ? 0 : vectors.size());
        for (int index = 0; index < count; index++) {
            Vec3 point = vectors.get(index);
            if (point == null || !finite(point)) {
                continue;
            }
            CompoundTag value = new CompoundTag();
            value.putDouble("X", point.x);
            value.putDouble("Y", point.y);
            value.putDouble("Z", point.z);
            result.add(value);
        }
        return result;
    }

    private static List<Vec3> readVectors(ListTag list) {
        List<Vec3> result = new ArrayList<>(Math.min(MAX_NODES, list.size()));
        for (int index = 0; index < Math.min(MAX_NODES, list.size()); index++) {
            CompoundTag value = list.getCompound(index);
            double x = value.getDouble("X");
            double y = value.getDouble("Y");
            double z = value.getDouble("Z");
            if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
                result.add(new Vec3(x, y, z));
            }
        }
        return List.copyOf(result);
    }

    private static List<Vec3> zeroVectors(int count) {
        List<Vec3> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(Vec3.ZERO);
        }
        return List.copyOf(result);
    }

    private static ListTag writeAnchors(List<RopeChain.AnchorState> anchors) {
        ListTag result = new ListTag();
        for (RopeChain.AnchorState anchor : anchors) {
            if (anchor == null || anchor.frame() == null
                    || !finite(anchor.start()) || !finite(anchor.end())) {
                continue;
            }
            CompoundTag value = new CompoundTag();
            value.putInt("Segment", anchor.segment());
            value.putBoolean("Rescue", anchor.rescue());
            writeFrame(value, anchor.frame());
            writeVector(value, "Start", anchor.start());
            writeVector(value, "End", anchor.end());
            result.add(value);
        }
        return result;
    }

    private static List<RopeChain.AnchorState> readAnchors(ListTag list) {
        List<RopeChain.AnchorState> result = new ArrayList<>();
        for (int index = 0; index < Math.min(RopeProperties.MAX_SEGMENTS, list.size()); index++) {
            CompoundTag value = list.getCompound(index);
            RopeFrame frame = readFrame(value);
            Vec3 start = readVector(value, "Start");
            Vec3 end = readVector(value, "End");
            if (frame != null && start != null && end != null) {
                result.add(new RopeChain.AnchorState(value.getInt("Segment"), frame, start, end,
                        value.getBoolean("Rescue")));
            }
        }
        return List.copyOf(result);
    }

    private static void writeFrame(CompoundTag parent, RopeFrame frame) {
        writeVector(parent, "X", frame.x());
        writeVector(parent, "Y", frame.y());
        writeVector(parent, "Z", frame.z());
    }

    private static RopeFrame readFrame(CompoundTag parent) {
        Vec3 x = readVector(parent, "X");
        Vec3 y = readVector(parent, "Y");
        Vec3 z = readVector(parent, "Z");
        return RopeFrame.from(x, y, z);
    }

    private static void writeVector(CompoundTag parent, String key, Vec3 point) {
        CompoundTag value = new CompoundTag();
        value.putDouble("X", point.x);
        value.putDouble("Y", point.y);
        value.putDouble("Z", point.z);
        parent.put(key, value);
    }

    private static Vec3 readVector(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag value = parent.getCompound(key);
        double x = value.getDouble("X");
        double y = value.getDouble("Y");
        double z = value.getDouble("Z");
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                ? new Vec3(x, y, z) : null;
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x)
                && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static int clampInt(CompoundTag tag, String key, int fallback, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum,
                tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : fallback));
    }

    private static double positive(CompoundTag tag, String key, double fallback,
            double minimum, double maximum) {
        return clamp(tag, key, fallback, minimum, maximum);
    }

    private static double clamp(CompoundTag tag, String key, double fallback,
            double minimum, double maximum) {
        double value = tag.contains(key, Tag.TAG_DOUBLE) ? tag.getDouble(key) : fallback;
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }

    public record State(int id, UUID ownerId, int age, RopeProperties properties,
            List<Vec3> nodes, List<Vec3> velocities, List<RopeChain.AnchorState> anchors,
            BlockPos rescueAnchorPos) {
        public State(int id, UUID ownerId, int age, RopeProperties properties,
                List<Vec3> nodes, List<Vec3> velocities,
                List<RopeChain.AnchorState> anchors) {
            this(id, ownerId, age, properties, nodes, velocities, anchors, null);
        }

        public State {
            properties = properties == null ? RopeProperties.DEFAULT : properties;
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            velocities = velocities == null ? List.of() : List.copyOf(velocities);
            anchors = anchors == null ? List.of() : List.copyOf(anchors);
            rescueAnchorPos = rescueAnchorPos == null ? null : rescueAnchorPos.immutable();
        }
    }
}
