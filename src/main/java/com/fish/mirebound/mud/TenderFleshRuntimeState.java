package com.fish.mirebound.mud;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Ephemeral per-player contractile-tissue state. It is never persisted. */
final class TenderFleshRuntimeState {
    double wrap;
    double contraction;
    double escapeOpportunity;
    double escapeVelocity;
    double pressure;
    double calmness;
    double enclosureProgress;
    double enclosureCenterX;
    double enclosureCenterY;
    double enclosureCenterZ;
    double enclosurePlayerX;
    double enclosurePlayerZ;
    int enclosureBrokenMask;
    int enclosurePillarDamagePacked;
    int enclosurePillarRequiredHitsPacked;
    int enclosureCooldownTicks;
    int enclosureStrikeCooldownTicks;
    int escapeTicks;
    boolean enclosureActive;
    boolean enclosureRetreating;
    boolean enclosureCenterSet;
    boolean enclosurePlayerCenterSet;
    ResourceKey<Level> enclosureDimension;
    boolean beatArmed = true;
    boolean relaxationBeatArmed;
    boolean pressureBeatArmed = true;
    boolean calmBeatArmed;

    void reset() {
        wrap = 0.0D;
        contraction = 0.0D;
        escapeOpportunity = 0.0D;
        escapeVelocity = 0.0D;
        pressure = 0.0D;
        calmness = 0.0D;
        enclosureProgress = 0.0D;
        enclosureCenterX = 0.0D;
        enclosureCenterY = 0.0D;
        enclosureCenterZ = 0.0D;
        enclosurePlayerX = 0.0D;
        enclosurePlayerZ = 0.0D;
        enclosureBrokenMask = 0;
        enclosurePillarDamagePacked = 0;
        enclosurePillarRequiredHitsPacked = 0;
        enclosureCooldownTicks = 0;
        enclosureStrikeCooldownTicks = 0;
        escapeTicks = 0;
        enclosureActive = false;
        enclosureRetreating = false;
        enclosureCenterSet = false;
        enclosurePlayerCenterSet = false;
        enclosureDimension = null;
        beatArmed = true;
        relaxationBeatArmed = false;
        pressureBeatArmed = true;
        calmBeatArmed = false;
    }
}
