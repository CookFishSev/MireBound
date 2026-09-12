package com.fish.mirebound.rope;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import net.minecraft.world.phys.Vec3;

/** Bounded endpoint joints; connecting never changes either chain's identity or pose. */
public final class RopeConnections {
    public static final int MAX_CONNECTIONS = 64;
    private static final double CLOSING_STEP = 0.12D;
    private final List<Joint> joints = new ArrayList<>();

    public record State(RopeEndpoint first, RopeEndpoint second, double slack) {
    }

    private static final class Joint {
        private RopeEndpoint first;
        private RopeEndpoint second;
        private double slack;

        private Joint(State state) {
            first = state.first();
            second = state.second();
            slack = state.slack();
        }
    }

    public List<State> states() {
        return joints.stream().map(j -> new State(j.first, j.second, j.slack)).toList();
    }

    public RopeEndpoint other(RopeEndpoint endpoint) {
        for (Joint joint : joints) {
            if (joint.first.equals(endpoint)) return joint.second;
            if (joint.second.equals(endpoint)) return joint.first;
        }
        return null;
    }

    public boolean add(State state, IntFunction<RopeChain> chains) {
        return add(state, chains, false);
    }

    public boolean restore(State state, IntFunction<RopeChain> chains) {
        return add(state, chains, true);
    }

    private boolean add(State state, IntFunction<RopeChain> chains, boolean restoring) {
        if (state == null || state.first() == null || state.second() == null
                || state.first().equals(state.second()) || joints.size() >= MAX_CONNECTIONS
                || !Double.isFinite(state.slack()) || state.slack() < 0 || state.slack() > 8
                || other(state.first()) != null || other(state.second()) != null) return false;
        RopeChain first = chains.apply(state.first().ropeId());
        RopeChain second = chains.apply(state.second().ropeId());
        if (first == null || second == null
                || !first.canExtendAt(state.first().segment(first))
                || !second.canExtendAt(state.second().segment(second))
                || !restoring && (!first.canConnectAt(state.first().segment(first))
                        || !second.canConnectAt(state.second().segment(second)))
                || first == second && first.segmentCount() < 3) return false;
        joints.add(new Joint(state));
        return true;
    }

    public void advance(IntFunction<RopeChain> chains) {
        for (Joint joint : joints) {
            if (chains.apply(joint.first.ropeId()) != null && chains.apply(joint.second.ropeId()) != null) {
                joint.slack = Math.max(0, joint.slack - CLOSING_STEP);
            }
        }
    }

    public void step(IntFunction<RopeChain> chains, IntFunction<RopeCollisionWorld> collisions) {
        Set<Integer> ids = new java.util.LinkedHashSet<>();
        for (Joint joint : joints) {
            if (chains.apply(joint.first.ropeId()) == null || chains.apply(joint.second.ropeId()) == null) continue;
            ids.add(joint.first.ropeId());
            ids.add(joint.second.ropeId());
        }
        List<RopeSimulation> simulations = new ArrayList<>(ids.size());
        List<RopeCollisionWorld> worlds = new ArrayList<>(ids.size());
        for (int id : ids) {
            RopeChain chain = chains.apply(id);
            chain.prepareStep();
            simulations.add(chain.simulation());
            worlds.add(collisions.apply(id));
        }
        advance(chains);
        RopeSimulation.stepConnected(simulations, worlds, () -> solve(chains, collisions));
        for (int id : ids) chains.apply(id).finishStep();
    }

    public void solve(IntFunction<RopeChain> chains, IntFunction<RopeCollisionWorld> collisions) {
        for (Joint joint : joints) {
            RopeChain first = chains.apply(joint.first.ropeId());
            RopeChain second = chains.apply(joint.second.ropeId());
            if (first == null || second == null) continue;
            int a = joint.first.point(first);
            int b = joint.second.point(second);
            Vec3 offset = second.point(b).subtract(first.point(a));
            double distance = offset.length();
            if (distance <= joint.slack + 1.0E-6D) continue;
            double firstWeight = first.connectionPointWeight(a);
            double secondWeight = second.connectionPointWeight(b);
            double total = firstWeight + secondWeight;
            if (total == 0) continue;
            Vec3 correction = offset.scale((distance - joint.slack) / distance);
            first.moveConnectionPoint(a, correction.scale(firstWeight / total),
                    collisions.apply(joint.first.ropeId()));
            second.moveConnectionPoint(b, correction.scale(-secondWeight / total),
                    collisions.apply(joint.second.ropeId()));
        }
    }

    public Set<Integer> component(int id) {
        Set<Integer> result = new HashSet<>();
        result.add(id);
        boolean changed;
        do {
            changed = false;
            for (Joint joint : joints) {
                if (result.contains(joint.first.ropeId())) changed |= result.add(joint.second.ropeId());
                if (result.contains(joint.second.ropeId())) changed |= result.add(joint.first.ropeId());
            }
        } while (changed);
        return result;
    }

    /** Limits a held segment against the nearest anchors on both sides of the connected path. */
    public Vec3 clampDragTarget(int id, int segment, Vec3 target, RopeFrame frame,
            IntFunction<RopeChain> chains) {
        RopeChain chain = chains.apply(id);
        Vec3 result = chain.clampDragTarget(segment, target, frame);
        if (frame == null || result == null) return result;
        ReachLimit before = reachLimit(id, segment - 1, -1, chains);
        ReachLimit after = reachLimit(id, segment + 1, 1, chains);
        Vec3 halfAxis = frame.y().normalize().scale(chain.properties().segmentLength() * 0.5D);
        for (int pass = 0; pass < 4; pass++) {
            if (before != null) result = before.clamp(result.subtract(halfAxis)).add(halfAxis);
            if (after != null) result = after.clamp(result.add(halfAxis)).subtract(halfAxis);
        }
        return result;
    }

    private ReachLimit reachLimit(int id, int start, int direction, IntFunction<RopeChain> chains) {
        Set<PathEntry> visited = new HashSet<>();
        double length = 0;
        while (visited.add(new PathEntry(id, start, direction))) {
            RopeChain chain = chains.apply(id);
            if (chain == null) return null;
            for (int segment = start; segment >= 0 && segment < chain.segmentCount(); segment += direction) {
                if (chain.isAnchored(segment)) {
                    return new ReachLimit(chain.point(direction > 0 ? segment : segment + 1), length);
                }
                length += chain.properties().segmentLength();
            }
            RopeEndpoint exit = new RopeEndpoint(id, direction < 0);
            Joint found = null;
            for (Joint joint : joints) {
                if (joint.first.equals(exit) || joint.second.equals(exit)) { found = joint; break; }
            }
            if (found == null) return null;
            RopeEndpoint peer = found.first.equals(exit) ? found.second : found.first;
            length += found.slack;
            id = peer.ropeId();
            RopeChain next = chains.apply(id);
            if (next == null) return null;
            direction = peer.start() ? 1 : -1;
            start = peer.start() ? 0 : next.segmentCount() - 1;
        }
        return null;
    }

    private record PathEntry(int ropeId, int segment, int direction) {
    }

    private record ReachLimit(Vec3 anchor, double length) {
        private Vec3 clamp(Vec3 endpoint) {
            Vec3 offset = endpoint.subtract(anchor);
            double distance = offset.length();
            return distance > length && distance > 1.0E-10D
                    ? anchor.add(offset.scale(length / distance)) : endpoint;
        }
    }

    public void remove(Set<Integer> removed) {
        joints.removeIf(j -> removed.contains(j.first.ropeId()) || removed.contains(j.second.ropeId()));
    }

    public void split(int oldId, int headId, int tailId) {
        for (Joint joint : joints) {
            joint.first = remap(joint.first, oldId, headId, tailId);
            joint.second = remap(joint.second, oldId, headId, tailId);
        }
        joints.removeIf(j -> j.first == null || j.second == null);
    }

    private static RopeEndpoint remap(RopeEndpoint endpoint, int oldId, int headId, int tailId) {
        if (endpoint.ropeId() != oldId) return endpoint;
        int id = endpoint.start() ? headId : tailId;
        return id < 0 ? null : new RopeEndpoint(id, endpoint.start());
    }
}
