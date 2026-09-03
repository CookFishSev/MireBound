package com.fish.mirebound.tentacle;

import net.minecraft.world.phys.Vec3;

interface TentacleCollisionSpace {
    Vec3 move(Vec3 from, Vec3 desired, double radius);

    Vec3 project(Vec3 point, double radius);

    boolean clear(Vec3 from, Vec3 to, double radius);

    boolean clear(Vec3 point, double radius);
}
