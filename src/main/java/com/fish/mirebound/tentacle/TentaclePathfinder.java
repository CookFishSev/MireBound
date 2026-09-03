package com.fish.mirebound.tentacle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class TentaclePathfinder {
    private static final double COST_EPSILON = 1.0E-9D;
    private static final double SQRT_TWO = Math.sqrt(2.0D);
    private static final double SQRT_THREE = Math.sqrt(3.0D);

    private TentaclePathfinder() {
    }

    static List<Vec3> find(Vec3 start, Vec3 goal, TentacleCollisionSpace collision,
            double radius, double cellSize, double margin, int maximumNodes) {
        if (collision.clear(start, goal, radius)) {
            return List.of(start, goal);
        }

        double minimumX = Math.min(start.x, goal.x) - margin;
        double minimumY = Math.min(start.y, goal.y) - margin;
        double minimumZ = Math.min(start.z, goal.z) - margin;
        double maximumX = Math.max(start.x, goal.x) + margin;
        double maximumY = Math.max(start.y, goal.y) + margin;
        double maximumZ = Math.max(start.z, goal.z) + margin;
        Vec3 origin = new Vec3(
                Math.floor(minimumX / cellSize) * cellSize,
                Math.floor(minimumY / cellSize) * cellSize,
                Math.floor(minimumZ / cellSize) * cellSize);
        int sizeX = Math.max(1, Mth.ceil((maximumX - origin.x) / cellSize));
        int sizeY = Math.max(1, Mth.ceil((maximumY - origin.y) / cellSize));
        int sizeZ = Math.max(1, Mth.ceil((maximumZ - origin.z) / cellSize));
        Node startNode = nodeFor(start, origin, cellSize, sizeX, sizeY, sizeZ);
        Node goalNode = nodeFor(goal, origin, cellSize, sizeX, sizeY, sizeZ);

        PriorityQueue<OpenNode> frontier = new PriorityQueue<>();
        Map<Node, Double> costs = new HashMap<>();
        Map<Node, Node> previous = new HashMap<>();
        costs.put(startNode, 0.0D);
        frontier.add(new OpenNode(startNode, 0.0D, start.distanceTo(goal)));
        int expanded = 0;

        while (!frontier.isEmpty()) {
            OpenNode currentOpen = frontier.poll();
            Node current = currentOpen.node;
            double currentCost = costs.getOrDefault(current, Double.POSITIVE_INFINITY);
            if (currentOpen.cost > currentCost + COST_EPSILON) {
                continue;
            }
            if (expanded++ >= maximumNodes) {
                break;
            }
            Vec3 currentPoint = position(current, startNode, goalNode, start, goal, origin, cellSize);
            // The exact endpoints may quantize to the same cell. Reaching the cell is not enough:
            // require a clear final leg so a thin wall inside that cell cannot become a "complete"
            // direct route. Testing line of sight from every settled node also creates a safe
            // virtual goal connection without adding a second node for the exact endpoint.
            if (!current.equals(startNode) && collision.clear(currentPoint, goal, radius)) {
                List<Vec3> route = reconstruct(previous, current, startNode, goalNode,
                        start, goal, origin, cellSize);
                if (!route.getLast().equals(goal)) {
                    route = new ArrayList<>(route);
                    route.add(goal);
                }
                return smooth(route, collision, radius);
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Node next = new Node(current.x + dx, current.y + dy, current.z + dz);
                        if (next.x < 0 || next.y < 0 || next.z < 0
                                || next.x > sizeX || next.y > sizeY || next.z > sizeZ) {
                            continue;
                        }
                        Vec3 nextPoint = position(next, startNode, goalNode, start, goal, origin, cellSize);
                        if (!collision.clear(nextPoint, radius)) {
                            continue;
                        }
                        if (!collision.clear(currentPoint, nextPoint, radius)) {
                            continue;
                        }
                        int changedAxes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        double step = changedAxes == 1 ? cellSize
                                : changedAxes == 2 ? cellSize * SQRT_TWO : cellSize * SQRT_THREE;
                        double verticalPenalty = Math.max(0, -dy) * cellSize * 0.08D;
                        double candidate = currentCost + step + verticalPenalty;
                        if (candidate + COST_EPSILON
                                >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                            continue;
                        }
                        costs.put(next, candidate);
                        previous.put(next, current);
                        frontier.add(new OpenNode(next, candidate,
                                candidate + nextPoint.distanceTo(goal)));
                    }
                }
            }
        }
        return List.of();
    }

    private static List<Vec3> reconstruct(Map<Node, Node> previous, Node current,
            Node startNode, Node goalNode, Vec3 start, Vec3 goal, Vec3 origin, double cellSize) {
        List<Vec3> path = new ArrayList<>();
        path.add(position(current, startNode, goalNode, start, goal, origin, cellSize));
        while (!current.equals(startNode)) {
            current = previous.get(current);
            if (current == null) {
                return List.of();
            }
            path.add(position(current, startNode, goalNode, start, goal, origin, cellSize));
        }
        Collections.reverse(path);
        if (!path.getFirst().equals(start)) {
            path.addFirst(start);
        }
        return path;
    }

    private static List<Vec3> smooth(List<Vec3> path, TentacleCollisionSpace collision, double radius) {
        if (path.size() <= 2) {
            return path;
        }
        List<Vec3> result = new ArrayList<>();
        int current = 0;
        result.add(path.getFirst());
        while (current < path.size() - 1) {
            int next = path.size() - 1;
            while (next > current + 1 && !collision.clear(path.get(current), path.get(next), radius)) {
                next--;
            }
            result.add(path.get(next));
            current = next;
        }
        return List.copyOf(result);
    }

    private static Node nodeFor(Vec3 point, Vec3 origin, double cellSize, int sizeX, int sizeY, int sizeZ) {
        return new Node(
                Mth.clamp((int) Math.round((point.x - origin.x) / cellSize), 0, sizeX),
                Mth.clamp((int) Math.round((point.y - origin.y) / cellSize), 0, sizeY),
                Mth.clamp((int) Math.round((point.z - origin.z) / cellSize), 0, sizeZ));
    }

    private static Vec3 position(Node node, Node startNode, Node goalNode,
            Vec3 start, Vec3 goal, Vec3 origin, double cellSize) {
        if (node.equals(startNode)) {
            return start;
        }
        if (node.equals(goalNode)) {
            return goal;
        }
        return new Vec3(
                origin.x + node.x * cellSize,
                origin.y + node.y * cellSize,
                origin.z + node.z * cellSize);
    }

    private record Node(int x, int y, int z) {
    }

    private record OpenNode(Node node, double cost, double score) implements Comparable<OpenNode> {
        @Override
        public int compareTo(OpenNode other) {
            int scoreOrder = Double.compare(score, other.score);
            if (scoreOrder != 0) {
                return scoreOrder;
            }
            int costOrder = Double.compare(cost, other.cost);
            if (costOrder != 0) {
                return costOrder;
            }
            int xOrder = Integer.compare(node.x, other.node.x);
            if (xOrder != 0) {
                return xOrder;
            }
            int yOrder = Integer.compare(node.y, other.node.y);
            return yOrder != 0 ? yOrder : Integer.compare(node.z, other.node.z);
        }
    }
}
