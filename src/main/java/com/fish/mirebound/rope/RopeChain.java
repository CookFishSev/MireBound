package com.fish.mirebound.rope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Owns one rope's interactions while {@link RopeSimulation} owns its physics. */
public final class RopeChain {
    private static final double EPSILON = 1.0E-10D;
    private static final int PICKUP_TRANSITION_TICKS = 4;
    private static final double RESCUE_FIXED_POINT_MAX_STEP = 0.10D;
    private static final int RESCUE_TAIL_CONSTRAINT_PASSES = 24;

    private final RopeProperties properties;
    private final RopeSimulation simulation;
    private final boolean[] anchoredSegments;
    private final boolean[] rescueAnchoredSegments;
    private final RopeFrame[] anchorFrames;
    private final Vec3[] anchorStarts;
    private final Vec3[] anchorEnds;
    private int draggedSegment = -1;
    private Vec3 dragCenter;
    private Vec3 dragTarget;
    private Vec3 dragTransitionStart;
    private int dragTransitionTicks;
    private RopeFrame dragFrame;
    private Vec3 dragVelocity = Vec3.ZERO;
    private int rescueTemporaryFixedPoint = -1;
    private Vec3 rescueTemporaryPosition;
    private Vec3 rescueTemporaryTarget;
    private int movingLassoFirstNode = -1;
    private Vec3[] movingLassoTargets;
    private int rescueLassoFirstSegment = -1;

    public RopeChain(RopeProperties properties, Vec3[] positions, Vec3[] velocities) {
        this.properties = properties == null ? RopeProperties.DEFAULT : properties;
        this.simulation = RopeSimulation.server(this.properties, positions, velocities);
        this.anchoredSegments = new boolean[this.properties.segmentCount()];
        this.rescueAnchoredSegments = new boolean[this.properties.segmentCount()];
        this.anchorFrames = new RopeFrame[this.properties.segmentCount()];
        this.anchorStarts = new Vec3[this.properties.segmentCount()];
        this.anchorEnds = new Vec3[this.properties.segmentCount()];
    }

    public static RopeChain thrown(RopeProperties properties, Vec3 origin,
            Vec3 direction, float charge) {
        RopeProperties used = properties == null ? RopeProperties.DEFAULT : properties;
        Vec3 forward = direction == null || direction.lengthSqr() <= EPSILON
                ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        Vec3 side = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        side = side.lengthSqr() <= EPSILON
                ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        Vec3[] positions = new Vec3[used.nodeCount()];
        Vec3[] velocities = new Vec3[used.nodeCount()];
        double halfLength = used.segmentLength() * 0.5D;
        double tipSpeed = Mth.lerp(Mth.clamp(charge, 0.0F, 1.0F),
                0.35D, (float) used.maximumThrowSpeed());
        for (int node = 0; node < positions.length; node++) {
            positions[node] = origin.add(side.scale((node & 1) == 0
                    ? -halfLength : halfLength));
            double share = node / (double) (positions.length - 1);
            double tipRamp = Mth.clamp((share - 0.72D) / 0.28D, 0.0D, 1.0D);
            tipRamp = tipRamp * tipRamp * (3.0D - 2.0D * tipRamp);
            velocities[node] = forward.scale(0.04D
                    + tipRamp * (tipSpeed - 0.04D));
        }
        return new RopeChain(used, positions, velocities);
    }

    public static RopeChain rescueThrown(RopeProperties properties, Vec3 origin,
            Vec3 direction, float charge, int lassoFirstNode, Vec3[] lassoTargets) {
        RopeChain chain = thrown(properties, origin, direction, charge);
        chain.rescueLassoFirstSegment = lassoFirstNode;
        if (!chain.setMovingLasso(lassoFirstNode, lassoTargets)) {
            throw new IllegalArgumentException("invalid rescue lasso targets");
        }
        return chain;
    }

    public void step(RopeCollisionWorld collision) {
        advancePickupTransition();
        advanceRescueTemporaryFixedPoint();
        simulation.step(collision);
        if (rescueTemporaryFixedPoint >= 0 && rescueLassoFirstSegment >= 3) {
            simulation.enforceDistanceConstraints(
                    rescueLassoFirstSegment - 3, 3,
                    RESCUE_TAIL_CONSTRAINT_PASSES);
        }
        if (draggedSegment >= 0) {
            simulation.dampFreeVelocities(draggedSegment);
        } else if (rescueTemporaryFixedPoint >= 0) {
            simulation.dampFreeVelocitiesAroundPoint(rescueTemporaryFixedPoint);
        }
    }

    public boolean setDragTarget(int segment, Vec3 target) {
        Vec3 tangent = validSegment(segment)
                ? simulation.point(segment + 1).subtract(simulation.point(segment))
                : Vec3.ZERO;
        return setDragTarget(segment, target, RopeFrame.fromTangent(tangent));
    }

    public boolean setDragTarget(int segment, Vec3 target, RopeFrame frame) {
        if (!validSegment(segment) || !finite(target) || frame == null) {
            return false;
        }
        if (draggedSegment != segment && draggedSegment >= 0) {
            clearDragState();
        }
        if (rescueAnchoredSegments[segment]) {
            clearRescueAnchorsAround(segment);
        }
        if (anchoredSegments[segment]) {
            removeAnchor(segment);
        }
        Vec3 acceptedTarget = clampToAnchors(segment, target, frame);
        if (draggedSegment == segment && dragCenter != null) {
            dragTarget = acceptedTarget;
            if (dragTransitionTicks > 0) {
                dragFrame = frame;
                syncFixedPoints();
                return true;
            }
            dragVelocity = limitSpeed(acceptedTarget.subtract(dragCenter), 0.75D);
            dragCenter = acceptedTarget;
        } else {
            Vec3 currentCenter = segmentCenter(segment);
            dragCenter = currentCenter == null ? acceptedTarget : currentCenter;
            dragTarget = acceptedTarget;
            dragTransitionStart = dragCenter;
            dragTransitionTicks = PICKUP_TRANSITION_TICKS;
            dragVelocity = Vec3.ZERO;
        }
        draggedSegment = segment;
        dragFrame = frame;
        if (dragTarget == null) {
            dragTarget = acceptedTarget;
        }
        syncFixedPoints();
        return true;
    }

    /** Moves a newly grabbed segment to its first target without crossing the target. */
    private void advancePickupTransition() {
        if (draggedSegment < 0 || dragTransitionTicks <= 0
                || dragCenter == null || dragTarget == null || dragTransitionStart == null) {
            return;
        }
        int completedTicks = PICKUP_TRANSITION_TICKS - dragTransitionTicks + 1;
        double progress = Mth.clamp(
                completedTicks / (double) PICKUP_TRANSITION_TICKS, 0.0D, 1.0D);
        dragCenter = dragTransitionStart.lerp(dragTarget, progress);
        dragTransitionTicks--;
        if (dragTransitionTicks == 0) {
            dragCenter = dragTarget;
            dragTransitionStart = null;
        }
        syncFixedPoints();
    }

    private Vec3 clampToAnchors(int segment, Vec3 target, RopeFrame frame) {
        Vec3 result = target;
        Vec3 tangent = safeTangent(frame.y(), segment);
        double halfLength = properties.segmentLength() * 0.5D;
        int passes = Math.max(1, anchoredSegments.length * 2);
        for (int pass = 0; pass < passes; pass++) {
            Vec3 beforePass = result;
            for (int anchor = 0; anchor < anchoredSegments.length; anchor++) {
                if (!anchoredSegments[anchor] || anchor == segment) {
                    continue;
                }
                boolean before = anchor < segment;
                Vec3 fixed = before ? anchorEnds[anchor] : anchorStarts[anchor];
                if (fixed == null) {
                    continue;
                }
                int availableEdges = before
                        ? segment - anchor - 1 : anchor - segment - 1;
                Vec3 endpoint = before
                        ? result.subtract(tangent.scale(halfLength))
                        : result.add(tangent.scale(halfLength));
                double maximum = Math.max(0, availableEdges)
                        * properties.segmentLength();
                Vec3 offset = endpoint.subtract(fixed);
                double distance = offset.length();
                if (distance > maximum + 1.0E-6D) {
                    endpoint = distance <= EPSILON
                            ? fixed : fixed.add(offset.scale(maximum / distance));
                    result = before
                            ? endpoint.add(tangent.scale(halfLength))
                            : endpoint.subtract(tangent.scale(halfLength));
                }
            }
            if (result.distanceToSqr(beforePass) <= EPSILON) {
                break;
            }
        }
        return result;
    }

    /** Keeps a held segment inside the available slack of every fixed segment. */
    public Vec3 clampDragTarget(int segment, Vec3 target, RopeFrame frame) {
        return !validSegment(segment) || target == null || frame == null
                ? target : clampToAnchors(segment, target, frame);
    }

    public boolean anchorSegment(int segment) {
        if (!validSegment(segment) || anchoredSegments[segment]
                || draggedSegment != segment) {
            return false;
        }
        Vec3 center = segmentCenter(segment);
        if (center == null) {
            return false;
        }
        Vec3 tangent = dragFrame == null
                ? safeTangent(simulation.point(segment + 1)
                        .subtract(simulation.point(segment)), segment)
                : safeTangent(dragFrame.y(), segment);
        double halfLength = properties.segmentLength() * 0.5D;
        anchoredSegments[segment] = true;
        anchorFrames[segment] = dragFrame == null
                ? RopeFrame.fromTangent(tangent) : dragFrame;
        anchorStarts[segment] = center.subtract(tangent.scale(halfLength));
        anchorEnds[segment] = center.add(tangent.scale(halfLength));
        clearDragState();
        syncFixedPoints();
        return true;
    }

    public boolean canExtendAt(int endpointSegment) {
        if (!validSegment(endpointSegment)) {
            return false;
        }
        // The rescue end is reserved as soon as the lasso is created. Do not
        // wait for the five loop segments to be reported as anchored.
        return rescueLassoFirstSegment < 0
                || endpointSegment < rescueLassoFirstSegment;
    }

    public void clearDrag() {
        int releasedSegment = draggedSegment;
        Vec3 releaseVelocity = dragVelocity;
        Vec3 releasedStart = releasedSegment >= 0
                ? simulation.point(releasedSegment) : null;
        Vec3 releasedEnd = releasedSegment >= 0
                ? simulation.point(releasedSegment + 1) : null;
        clearDragState();
        syncFixedPoints();
        if (releasedSegment < 0 || releasedSegment >= properties.segmentCount()) {
            return;
        }
        simulation.resetVelocities();
        simulation.setVelocity(releasedSegment, releaseVelocity);
        simulation.setVelocity(releasedSegment + 1, releaseVelocity);
        removeReleaseTension(releasedSegment, releasedStart, releasedEnd);
    }

    private void removeReleaseTension(int segment, Vec3 start, Vec3 end) {
        if (segment > 0 && start != null) {
            int adjacent = segment - 1;
            simulation.removeVelocityInto(adjacent,
                    start.subtract(simulation.point(adjacent)));
        }
        if (segment + 2 < simulation.pointCount() && end != null) {
            int adjacent = segment + 2;
            simulation.removeVelocityInto(adjacent,
                    end.subtract(simulation.point(adjacent)));
        }
    }

    private void clearDragState() {
        draggedSegment = -1;
        dragCenter = null;
        dragTarget = null;
        dragTransitionStart = null;
        dragTransitionTicks = 0;
        dragFrame = null;
        dragVelocity = Vec3.ZERO;
    }

    private void syncFixedPoints() {
        simulation.clearFixedPoints();
        for (int segment = 0; segment < anchoredSegments.length; segment++) {
            if (!anchoredSegments[segment]) {
                continue;
            }
            simulation.fixPoint(segment, anchorStarts[segment]);
            simulation.fixPoint(segment + 1, anchorEnds[segment]);
        }
        if (movingLassoTargets != null && movingLassoFirstNode >= 0) {
            for (int index = 0; index < movingLassoTargets.length; index++) {
                simulation.fixPoint(movingLassoFirstNode + index,
                        movingLassoTargets[index]);
            }
        }
        if (rescueTemporaryFixedPoint >= 0 && rescueTemporaryPosition != null) {
            simulation.fixPoint(rescueTemporaryFixedPoint, rescueTemporaryPosition);
        }
        if (draggedSegment < 0 || dragCenter == null || dragFrame == null) {
            return;
        }
        Vec3 tangent = safeTangent(dragFrame.y(), draggedSegment);
        double halfLength = properties.segmentLength() * 0.5D;
        if (!isAnchoredNode(draggedSegment, draggedSegment)) {
            simulation.fixPoint(draggedSegment,
                    dragCenter.subtract(tangent.scale(halfLength)));
        }
        if (!isAnchoredNode(draggedSegment + 1, draggedSegment)) {
            simulation.fixPoint(draggedSegment + 1,
                    dragCenter.add(tangent.scale(halfLength)));
        }
    }

    private boolean isAnchoredNode(int node, int ignoredSegment) {
        for (int segment = 0; segment < anchoredSegments.length; segment++) {
            if (segment == ignoredSegment || !anchoredSegments[segment]) {
                continue;
            }
            if (node == segment || node == segment + 1) {
                return true;
            }
        }
        return false;
    }

    public List<Vec3> positions() {
        return simulation.positions();
    }

    public RopeProperties properties() {
        return properties;
    }

    public List<Vec3> velocities() {
        return List.copyOf(Arrays.asList(simulation.velocityArrayPerTick()));
    }

    public Vec3[] positionArray() {
        return simulation.positionArray();
    }

    public List<Vec3> motionTargets(int ticks) {
        int span = Math.max(1, ticks);
        Vec3[] velocities = simulation.velocityArrayPerTick();
        List<Vec3> result = new ArrayList<>(velocities.length);
        for (int point = 0; point < velocities.length; point++) {
            Vec3 motion = velocities[point]
                    .add(0.0D, -properties.gravityPerTick(), 0.0D).scale(span);
            result.add(simulation.point(point).add(limitSpeed(motion,
                    properties.maximumThrowSpeed() * span)));
        }
        return List.copyOf(result);
    }

    public int segmentCount() {
        return properties.segmentCount();
    }

    public Vec3 segmentCenter(int segment) {
        return validSegment(segment)
                ? simulation.point(segment).lerp(simulation.point(segment + 1), 0.5D)
                : null;
    }

    public Vec3 point(int node) {
        return node >= 0 && node < simulation.pointCount()
                ? simulation.point(node) : null;
    }

    /** Sets the single temporary point used by the server rescue constraint. */
    public void setRescueTemporaryFixedPoint(int node, Vec3 target) {
        if (node < 0 || node >= simulation.pointCount() || !finite(target)) {
            return;
        }
        if (rescueTemporaryFixedPoint != node || rescueTemporaryPosition == null) {
            rescueTemporaryPosition = simulation.point(node);
        }
        rescueTemporaryFixedPoint = node;
        rescueTemporaryTarget = target;
        syncFixedPoints();
    }

    private void advanceRescueTemporaryFixedPoint() {
        if (rescueTemporaryFixedPoint < 0 || rescueTemporaryPosition == null
                || rescueTemporaryTarget == null) {
            return;
        }
        Vec3 offset = rescueTemporaryTarget.subtract(rescueTemporaryPosition);
        double distance = offset.length();
        if (distance <= RESCUE_FIXED_POINT_MAX_STEP) {
            rescueTemporaryPosition = rescueTemporaryTarget;
        } else if (distance > EPSILON) {
            rescueTemporaryPosition = rescueTemporaryPosition.add(
                    offset.scale(RESCUE_FIXED_POINT_MAX_STEP / distance));
        }
        syncFixedPoints();
    }

    public void clearRescueTemporaryFixedPoint() {
        rescueTemporaryFixedPoint = -1;
        rescueTemporaryPosition = null;
        rescueTemporaryTarget = null;
        syncFixedPoints();
    }

    public boolean setMovingLasso(int firstNode, Vec3[] targets) {
        if (firstNode < 0 || targets == null || targets.length != 6
                || firstNode + targets.length > simulation.pointCount()) {
            return false;
        }
        for (Vec3 target : targets) {
            if (!finite(target)) {
                return false;
            }
        }
        movingLassoFirstNode = firstNode;
        movingLassoTargets = Arrays.copyOf(targets, targets.length);
        syncFixedPoints();
        return true;
    }

    public void clearMovingLasso() {
        movingLassoFirstNode = -1;
        movingLassoTargets = null;
        syncFixedPoints();
    }

    public boolean anchorLasso(int firstSegment, Vec3[] points) {
        if (firstSegment < 0 || points == null || points.length != 6
                || firstSegment + 5 > anchoredSegments.length) {
            return false;
        }
        for (int index = 0; index < points.length; index++) {
            if (!finite(points[index])) {
                return false;
            }
            if (index > 0 && Math.abs(points[index - 1].distanceTo(points[index])
                    - properties.segmentLength()) > 1.0E-3D) {
                return false;
            }
        }
        clearMovingLasso();
        clearRescueTemporaryFixedPoint();
        for (int offset = 0; offset < 5; offset++) {
            int segment = firstSegment + offset;
            Vec3 start = points[offset];
            Vec3 end = points[offset + 1];
            anchoredSegments[segment] = true;
            rescueAnchoredSegments[segment] = true;
            anchorFrames[segment] = RopeFrame.fromTangent(end.subtract(start));
            anchorStarts[segment] = start;
            anchorEnds[segment] = end;
        }
        syncFixedPoints();
        return true;
    }

    public RopeChain extended(boolean atStart) {
        if (properties.segmentCount() >= RopeProperties.MAX_SEGMENTS) {
            return null;
        }
        Vec3[] positions = simulation.positionArray();
        Vec3[] velocities = simulation.velocityArrayPerTick();
        int offset = atStart ? 1 : 0;
        Vec3[] extendedPositions = new Vec3[positions.length + 1];
        Vec3[] extendedVelocities = new Vec3[velocities.length + 1];
        if (atStart) {
            Vec3 tangent = safeTangent(positions[1].subtract(positions[0]), 0);
            extendedPositions[0] = positions[0].subtract(
                    tangent.scale(properties.segmentLength()));
            extendedVelocities[0] = velocities[0];
        } else {
            int last = positions.length - 1;
            Vec3 tangent = safeTangent(positions[last].subtract(positions[last - 1]),
                    properties.segmentCount() - 1);
            extendedPositions[last + 1] = positions[last].add(
                    tangent.scale(properties.segmentLength()));
            extendedVelocities[last + 1] = velocities[last];
        }
        System.arraycopy(positions, 0, extendedPositions, offset, positions.length);
        System.arraycopy(velocities, 0, extendedVelocities, offset, velocities.length);
        RopeChain result = new RopeChain(
                properties.withSegmentCount(properties.segmentCount() + 1),
                extendedPositions, extendedVelocities);
        result.rescueLassoFirstSegment = rescueLassoFirstSegment < 0
                ? -1 : rescueLassoFirstSegment + (atStart ? 1 : 0);
        copyAnchorsTo(result, offset, 0, properties.segmentCount());
        result.syncFixedPoints();
        return result;
    }

    /** Joins two free endpoints while preserving every fixed-length segment. */
    public RopeChain join(RopeChain other, int thisEndpointSegment,
            int otherEndpointSegment) {
        if (other == null || other == this
                || !validEndpointSegment(thisEndpointSegment)
                || !other.validEndpointSegment(otherEndpointSegment)
                || rescueLassoFirstSegment() >= 0
                || other.rescueLassoFirstSegment() >= 0
                || properties.segmentCount() + other.properties.segmentCount()
                        > RopeProperties.MAX_SEGMENTS
                || Math.abs(properties.segmentLength() - other.properties.segmentLength())
                        > 1.0E-6D
                || isAnchored(thisEndpointSegment)
                || other.isAnchored(otherEndpointSegment)) {
            return null;
        }
        boolean reverseThis = thisEndpointSegment == 0;
        boolean reverseOther = otherEndpointSegment == other.properties.segmentCount() - 1;
        int thisCount = properties.segmentCount();
        int otherCount = other.properties.segmentCount();
        Vec3[] thisPositions = simulation.positionArray();
        Vec3[] otherPositions = other.simulation.positionArray();
        Vec3[] thisVelocities = simulation.velocityArrayPerTick();
        Vec3[] otherVelocities = other.simulation.velocityArrayPerTick();
        Vec3 thisJoin = thisPositions[reverseThis ? 0 : thisPositions.length - 1];
        Vec3 otherJoin = otherPositions[reverseOther ? otherPositions.length - 1 : 0];
        // Keep the rope being dragged in place and translate only the target
        // rope onto its endpoint. The shared endpoint is written once below.
        Vec3 translation = thisJoin.subtract(otherJoin);
        Vec3[] joinedPositions = new Vec3[thisPositions.length + otherPositions.length - 1];
        Vec3[] joinedVelocities = new Vec3[joinedPositions.length];
        for (int index = 0; index < thisPositions.length; index++) {
            int source = reverseThis ? thisPositions.length - 1 - index : index;
            joinedPositions[index] = thisPositions[source];
            joinedVelocities[index] = thisVelocities[source];
        }
        for (int index = 1; index < otherPositions.length; index++) {
            int source = reverseOther
                    ? otherPositions.length - 1 - index : index;
            int target = thisPositions.length + index - 1;
            joinedPositions[target] = otherPositions[source].add(translation);
            joinedVelocities[target] = otherVelocities[source];
        }
        RopeChain result = new RopeChain(
                properties.withSegmentCount(thisCount + otherCount),
                joinedPositions, joinedVelocities);
        copyAnchorsTo(result, 0, 0, thisCount, reverseThis, Vec3.ZERO);
        other.copyAnchorsTo(result, thisCount, 0, otherCount, reverseOther, translation);
        result.syncFixedPoints();
        return result;
    }

    public Split splitAt(int segment) {
        if (!validSegment(segment)) {
            return null;
        }
        RopeChain first = segment == 0 ? null : slice(0, segment);
        int secondStart = segment + 1;
        RopeChain second = secondStart >= properties.segmentCount()
                ? null : slice(secondStart, properties.segmentCount() - secondStart);
        return new Split(first, second);
    }

    private RopeChain slice(int firstSegment, int segmentCount) {
        Vec3[] positions = simulation.positionArray();
        Vec3[] velocities = simulation.velocityArrayPerTick();
        Vec3[] slicedPositions = Arrays.copyOfRange(
                positions, firstSegment, firstSegment + segmentCount + 1);
        Vec3[] slicedVelocities = Arrays.copyOfRange(
                velocities, firstSegment, firstSegment + segmentCount + 1);
        RopeChain result = new RopeChain(
                properties.withSegmentCount(segmentCount), slicedPositions, slicedVelocities);
        if (rescueLassoFirstSegment >= firstSegment
                && rescueLassoFirstSegment < firstSegment + segmentCount) {
            result.rescueLassoFirstSegment = rescueLassoFirstSegment - firstSegment;
        }
        copyAnchorsTo(result, 0, firstSegment, segmentCount);
        result.syncFixedPoints();
        return result;
    }

    private void copyAnchorsTo(RopeChain target, int targetOffset,
            int sourceSegment, int targetSegmentCount) {
        copyAnchorsTo(target, targetOffset, sourceSegment, targetSegmentCount, false);
    }

    private void copyAnchorsTo(RopeChain target, int targetOffset,
            int sourceSegment, int targetSegmentCount, boolean reverse) {
        copyAnchorsTo(target, targetOffset, sourceSegment, targetSegmentCount,
                reverse, Vec3.ZERO);
    }

    private void copyAnchorsTo(RopeChain target, int targetOffset,
            int sourceSegment, int targetSegmentCount, boolean reverse,
            Vec3 translation) {
        for (int segment = sourceSegment;
                segment < sourceSegment + targetSegmentCount
                        && segment < anchoredSegments.length; segment++) {
            if (!anchoredSegments[segment]) {
                continue;
            }
            int localSegment = reverse
                    ? targetSegmentCount - 1 - (segment - sourceSegment)
                    : segment - sourceSegment;
            int targetSegment = localSegment + targetOffset;
            target.anchoredSegments[targetSegment] = true;
            target.rescueAnchoredSegments[targetSegment] = rescueAnchoredSegments[segment];
            target.anchorFrames[targetSegment] = reverse
                    ? reverseFrame(anchorFrames[segment]) : anchorFrames[segment];
            Vec3 start = anchorStarts[segment];
            Vec3 end = anchorEnds[segment];
            if (reverse) {
                Vec3 swap = start;
                start = end;
                end = swap;
            }
            target.anchorStarts[targetSegment] = start == null
                    ? null : start.add(translation);
            target.anchorEnds[targetSegment] = end == null
                    ? null : end.add(translation);
        }
    }

    private static RopeFrame reverseFrame(RopeFrame frame) {
        return frame == null ? null : new RopeFrame(
                frame.x(), frame.y().scale(-1.0D), frame.z().scale(-1.0D));
    }

    /** Returns whether this segment is an unlocked, ordinary rope endpoint. */
    public boolean canConnectAt(int segment) {
        return validEndpointSegment(segment)
                && !anchoredSegments[segment]
                && rescueLassoFirstSegment < 0;
    }

    private boolean validEndpointSegment(int segment) {
        return validSegment(segment)
                && (segment == 0 || segment == properties.segmentCount() - 1)
                && !rescueAnchoredSegments[segment];
    }

    public boolean removeAnchor(int segment) {
        if (segment < 0 || segment >= anchoredSegments.length) {
            return false;
        }
        boolean changed = anchoredSegments[segment];
        anchoredSegments[segment] = false;
        rescueAnchoredSegments[segment] = false;
        anchorFrames[segment] = null;
        anchorStarts[segment] = null;
        anchorEnds[segment] = null;
        if (changed) {
            syncFixedPoints();
        }
        return changed;
    }

    /** Releases only the anchors created by a rescue lasso. */
    public boolean clearRescueAnchors() {
        boolean changed = false;
        for (int segment = 0; segment < rescueAnchoredSegments.length; segment++) {
            if (!rescueAnchoredSegments[segment]) {
                continue;
            }
            changed = true;
            rescueAnchoredSegments[segment] = false;
            anchoredSegments[segment] = false;
            anchorFrames[segment] = null;
            anchorStarts[segment] = null;
            anchorEnds[segment] = null;
        }
        if (rescueTemporaryFixedPoint >= 0) {
            rescueTemporaryFixedPoint = -1;
            rescueTemporaryPosition = null;
            rescueTemporaryTarget = null;
            changed = true;
        }
        if (changed) {
            rescueLassoFirstSegment = -1;
            syncFixedPoints();
        }
        return changed;
    }

    /** Releases the contiguous rescue-locked group containing {@code segment}. */
    public boolean clearRescueAnchorsAround(int segment) {
        if (!validSegment(segment) || !rescueAnchoredSegments[segment]) {
            return false;
        }
        int first = segment;
        while (first > 0 && rescueAnchoredSegments[first - 1]) {
            first--;
        }
        int last = segment;
        while (last + 1 < rescueAnchoredSegments.length
                && rescueAnchoredSegments[last + 1]) {
            last++;
        }
        boolean clearsLasso = rescueLassoFirstSegment >= first
                && rescueLassoFirstSegment <= last;
        for (int current = first; current <= last; current++) {
            rescueAnchoredSegments[current] = false;
            anchoredSegments[current] = false;
            anchorFrames[current] = null;
            anchorStarts[current] = null;
            anchorEnds[current] = null;
        }
        if (rescueTemporaryFixedPoint >= 0) {
            rescueTemporaryFixedPoint = -1;
            rescueTemporaryPosition = null;
            rescueTemporaryTarget = null;
        }
        if (clearsLasso) {
            rescueLassoFirstSegment = -1;
        }
        syncFixedPoints();
        return true;
    }

    public record Split(RopeChain first, RopeChain second) {
    }

    public int draggedSegment() {
        return draggedSegment;
    }

    public boolean isAnchored(int segment) {
        return segment >= 0 && segment < anchoredSegments.length
                && anchoredSegments[segment];
    }

    public int rescueLassoFirstSegment() {
        if (rescueLassoFirstSegment >= 0) {
            return (movingLassoTargets != null || hasRescueLassoAnchors(rescueLassoFirstSegment))
                    && rescueLassoFirstSegment < properties.segmentCount()
                    ? rescueLassoFirstSegment : -1;
        }
        int first = properties.segmentCount() - 5;
        return first >= 1 && hasRescueLassoAnchors(first) ? first : -1;
    }

    private boolean hasRescueLassoAnchors(int first) {
        if (first < 1 || first + 5 > rescueAnchoredSegments.length) {
            return false;
        }
        for (int segment = first; segment < first + 5; segment++) {
            if (!rescueAnchoredSegments[segment] || anchorStarts[segment] == null
                    || anchorEnds[segment] == null) {
                return false;
            }
            int next = segment == first + 4 ? first : segment + 1;
            if (anchorEnds[segment].distanceToSqr(anchorStarts[next]) > 1.0E-6D) {
                return false;
            }
        }
        return true;
    }

    public List<RopeSegmentOrientation> anchoredOrientations() {
        List<RopeSegmentOrientation> result = new ArrayList<>();
        for (int segment = 0; segment < anchoredSegments.length; segment++) {
            if (anchoredSegments[segment] && anchorFrames[segment] != null) {
                if (rescueAnchoredSegments[segment]) {
                    continue;
                }
                result.add(new RopeSegmentOrientation(segment, anchorFrames[segment]));
            }
        }
        return List.copyOf(result);
    }

    public List<RopeSegmentOrientation> rescueAnchoredOrientations() {
        List<RopeSegmentOrientation> result = new ArrayList<>();
        for (int segment = 0; segment < rescueAnchoredSegments.length; segment++) {
            if (rescueAnchoredSegments[segment] && anchorFrames[segment] != null) {
                result.add(new RopeSegmentOrientation(segment, anchorFrames[segment]));
            }
        }
        return List.copyOf(result);
    }

    public List<AnchorState> anchorStates() {
        List<AnchorState> result = new ArrayList<>();
        for (int segment = 0; segment < anchoredSegments.length; segment++) {
                if (anchoredSegments[segment] && anchorFrames[segment] != null
                    && anchorStarts[segment] != null && anchorEnds[segment] != null) {
                result.add(new AnchorState(segment, anchorFrames[segment],
                        anchorStarts[segment], anchorEnds[segment], rescueAnchoredSegments[segment]));
            }
        }
        return List.copyOf(result);
    }

    public void restoreAnchors(List<AnchorState> savedAnchors) {
        if (savedAnchors == null) {
            return;
        }
        for (AnchorState anchor : savedAnchors) {
            if (anchor == null || !validSegment(anchor.segment()) || anchor.frame() == null
                    || !finite(anchor.start()) || !finite(anchor.end())) {
                continue;
            }
            anchoredSegments[anchor.segment()] = true;
            rescueAnchoredSegments[anchor.segment()] = anchor.rescue();
            anchorFrames[anchor.segment()] = anchor.frame();
            anchorStarts[anchor.segment()] = anchor.start();
            anchorEnds[anchor.segment()] = anchor.end();
        }
        syncFixedPoints();
    }

    public RopeSegmentOrientation draggedOrientation() {
        return draggedSegment >= 0 && dragFrame != null
                ? new RopeSegmentOrientation(draggedSegment, dragFrame) : null;
    }

    public record AnchorState(int segment, RopeFrame frame, Vec3 start, Vec3 end,
            boolean rescue) {
        public AnchorState(int segment, RopeFrame frame, Vec3 start, Vec3 end) {
            this(segment, frame, start, end, false);
        }
    }

    public double maximumSegmentError() {
        return simulation.maximumSegmentError();
    }

    private boolean validSegment(int segment) {
        return segment >= 0 && segment < properties.segmentCount();
    }

    private Vec3 safeTangent(Vec3 tangent, int segment) {
        if (tangent != null && tangent.lengthSqr() > EPSILON) {
            return tangent.normalize();
        }
        Vec3[] velocities = simulation.velocityArrayPerTick();
        if (segment >= 0 && segment + 1 < velocities.length) {
            Vec3 delta = velocities[segment + 1].subtract(velocities[segment]);
            if (delta.lengthSqr() > EPSILON) {
                return delta.normalize();
            }
        }
        return (segment & 1) == 0
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(-1.0D, 0.0D, 0.0D);
    }

    private static Vec3 limitSpeed(Vec3 velocity, double maximum) {
        double lengthSquared = velocity.lengthSqr();
        return maximum <= 0.0D ? Vec3.ZERO
                : lengthSquared > maximum * maximum
                ? velocity.scale(maximum / Math.sqrt(lengthSquared)) : velocity;
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x)
                && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
