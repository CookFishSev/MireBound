package com.fish.mirebound.tentacle;

import com.fish.mirebound.Mirebound;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent, reconstructable state for procedural tentacles in one dimension. */
public final class TentacleSavedData extends SavedData {
    private static final String DATA_NAME = "mirebound_tentacles";
    private static final int DATA_VERSION = 1;
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_POINTS = 32;
    private static final Factory<TentacleSavedData> FACTORY =
            new Factory<>(TentacleSavedData::new, TentacleSavedData::load);

    private final Map<Integer, State> states = new LinkedHashMap<>();
    private int nextId = 1;

    private TentacleSavedData() {
    }

    public static TentacleSavedData get(ServerLevel level) {
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
        for (State state : replacement == null ? List.<State>of() : replacement) {
            if (state == null || state.id() <= 0 || states.size() >= MAX_ENTRIES) {
                continue;
            }
            states.put(state.id(), state);
            highest = Math.max(highest, state.id());
        }
        this.nextId = Math.max(1, Math.max(nextId, highest + 1));
        setDirty();
    }

    public void remove(int id) {
        if (states.remove(id) != null) {
            setDirty();
        }
    }

    public void clear() {
        if (!states.isEmpty()) {
            states.clear();
            setDirty();
        }
    }

    private static TentacleSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TentacleSavedData data = new TentacleSavedData();
        data.nextId = Math.max(1, tag.getInt("NextId"));
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        int count = Math.min(MAX_ENTRIES, entries.size());
        for (int index = 0; index < count; index++) {
            CompoundTag entry = entries.getCompound(index);
            State state = readState(entry);
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
        if (id <= 0 || !tag.contains("Root", Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag root = tag.getCompound("Root");
        Vec3Data rootPoint = readVector(root);
        if (rootPoint == null) {
            return null;
        }
        List<Vec3Data> points = readVectors(tag.getList("Points", Tag.TAG_COMPOUND));
        List<Vec3Data> previous = readVectors(tag.getList("Previous", Tag.TAG_COMPOUND));
        boolean hasTracked = tag.hasUUID("Tracked");
        return new State(id, rootPoint.toVec3(),
                finiteOr(tag.getDouble("Volume"), 0.015625D), tag.getLong("Seed"),
                phase(tag.getString("Phase")), tag.getBoolean("Tracking"),
                hasTracked ? tag.getUUID("Tracked") : null,
                Math.max(0, tag.getInt("Age")), Math.max(0, tag.getInt("PhaseTicks")),
                clamp01(tag.getDouble("Extension")),
                Math.max(0.01D, finiteOr(tag.getDouble("LengthScale"), 1.0D)),
                tag.getBoolean("GrabEnabled"), grabMode(tag.getString("GrabMode")),
                pointsToVec3(points), pointsToVec3(previous));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", DATA_VERSION);
        tag.putInt("NextId", nextId);
        ListTag entries = new ListTag();
        for (State state : states.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Id", state.id());
            writeVector(entry, "Root", state.root());
            entry.putDouble("Volume", state.volume());
            entry.putLong("Seed", state.visualSeed());
            entry.putString("Phase", state.phase().name());
            entry.putBoolean("Tracking", state.tracking());
            if (state.trackedPlayer() != null) {
                entry.putUUID("Tracked", state.trackedPlayer());
            }
            entry.putInt("Age", state.age());
            entry.putInt("PhaseTicks", state.phaseTicks());
            entry.putDouble("Extension", state.extension());
            entry.putDouble("LengthScale", state.lengthScale());
            entry.putBoolean("GrabEnabled", state.grabEnabled());
            entry.putString("GrabMode", state.grabMode().name());
            entry.put("Points", writeVectors(state.points()));
            entry.put("Previous", writeVectors(state.previous()));
            entries.add(entry);
        }
        tag.put("Entries", entries);
        return tag;
    }

    private static ListTag writeVectors(List<Vec3> vectors) {
        ListTag result = new ListTag();
        int count = Math.min(MAX_POINTS, vectors == null ? 0 : vectors.size());
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

    private static List<Vec3Data> readVectors(ListTag list) {
        List<Vec3Data> result = new ArrayList<>(Math.min(MAX_POINTS, list.size()));
        for (int index = 0; index < Math.min(MAX_POINTS, list.size()); index++) {
            Vec3Data value = readVector(list.getCompound(index));
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private static void writeVector(CompoundTag parent, String key, net.minecraft.world.phys.Vec3 point) {
        CompoundTag value = new CompoundTag();
        value.putDouble("X", point.x);
        value.putDouble("Y", point.y);
        value.putDouble("Z", point.z);
        parent.put(key, value);
    }

    private static Vec3Data readVector(CompoundTag tag) {
        if (!tag.contains("X", Tag.TAG_DOUBLE)
                || !tag.contains("Y", Tag.TAG_DOUBLE)
                || !tag.contains("Z", Tag.TAG_DOUBLE)) {
            return null;
        }
        double x = tag.getDouble("X");
        double y = tag.getDouble("Y");
        double z = tag.getDouble("Z");
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                ? new Vec3Data(x, y, z) : null;
    }

    private static List<net.minecraft.world.phys.Vec3> pointsToVec3(List<Vec3Data> values) {
        List<net.minecraft.world.phys.Vec3> result = new ArrayList<>(values.size());
        for (Vec3Data value : values) {
            result.add(value.toVec3());
        }
        return List.copyOf(result);
    }

    private static TentaclePhase phase(String name) {
        try {
            return TentaclePhase.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return TentaclePhase.IDLE;
        }
    }

    private static TentacleGrabMode grabMode(String name) {
        TentacleGrabMode mode = TentacleGrabMode.byName(name);
        return mode == null ? TentacleGrabMode.THRASH : mode;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp01(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D;
    }

    private static boolean finite(net.minecraft.world.phys.Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private record Vec3Data(double x, double y, double z) {
        private Vec3Data {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("non-finite tentacle vector");
            }
        }

        private net.minecraft.world.phys.Vec3 toVec3() {
            return new net.minecraft.world.phys.Vec3(x, y, z);
        }
    }

    public record State(int id, net.minecraft.world.phys.Vec3 root, double volume, long visualSeed,
            TentaclePhase phase, boolean tracking, UUID trackedPlayer, int age, int phaseTicks,
            double extension, double lengthScale, boolean grabEnabled, TentacleGrabMode grabMode,
            List<net.minecraft.world.phys.Vec3> points,
            List<net.minecraft.world.phys.Vec3> previous) {
        public State {
            phase = phase == null ? TentaclePhase.IDLE : phase;
            grabMode = grabMode == null ? TentacleGrabMode.THRASH : grabMode;
            points = points == null ? List.of() : List.copyOf(points);
            previous = previous == null ? List.of() : List.copyOf(previous);
        }
    }
}
