package com.fish.mirebound.client;

import net.minecraft.world.phys.Vec3;

/** Fixed position history used to render a curved fountain without extra particles. */
final class MudSplashTrail {
    private static final int CAPACITY = 24;
    private final Vec3[] points = new Vec3[CAPACITY];
    private int head;
    private int count;

    void reset(Vec3 origin) {
        head = 0;
        count = 1;
        points[0] = origin;
    }

    void record(Vec3 point) {
        head = (head + 1) % CAPACITY;
        points[head] = point;
        count = Math.min(CAPACITY, count + 1);
    }

    double availableLength(Vec3 interpolatedHead, double maximumLength) {
        double length = 0.0D;
        Vec3 from = interpolatedHead;
        for (int age = 1; age < count && length < maximumLength; age++) {
            Vec3 to = pointAtAge(age);
            length += from.distanceTo(to);
            from = to;
        }
        return Math.min(maximumLength, length);
    }

    Vec3 sampleBack(Vec3 interpolatedHead, double distance) {
        if (distance <= 0.0D || count <= 1) {
            return interpolatedHead;
        }
        Vec3 from = interpolatedHead;
        double remaining = distance;
        for (int age = 1; age < count; age++) {
            Vec3 to = pointAtAge(age);
            double segmentLength = from.distanceTo(to);
            if (segmentLength > 1.0E-8D && remaining <= segmentLength) {
                return from.lerp(to, remaining / segmentLength);
            }
            remaining -= segmentLength;
            from = to;
        }
        return from;
    }

    private Vec3 pointAtAge(int age) {
        int index = head - age;
        if (index < 0) {
            index += CAPACITY;
        }
        return points[index];
    }
}
