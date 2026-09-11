package com.fish.mirebound.client.skin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SkinEditorPoseTest {
    @Test void baseAndOuterLayersShareTheSelectedPartRotation() {
        var pose=new SkinEditorPose();
        pose.rotate("left_arm",SkinEditorPose.Axis.Z,Math.PI/2);
        var base=pose.transform("left_arm",5,2,0);
        var outer=pose.transform("left_arm",5,2,0);
        assertArrayEquals(base,outer,1e-9);
        assertEquals(5,base[0],1e-9);
        assertEquals(2,base[1],1e-9);
    }

    @Test void rotatingAroundAHandOrFootUsesTheVanillaPartPivot() {
        var pose=new SkinEditorPose();
        pose.rotate("right_arm",SkinEditorPose.Axis.X,.5);
        assertArrayEquals(new double[]{-5,2,0},pose.transform("right_arm",-5,2,0),1e-9);
        pose.rotate("left_leg",SkinEditorPose.Axis.Y,.5);
        assertArrayEquals(new double[]{1.9,12,0},pose.transform("left_leg",1.9,12,0),1e-9);
    }

    @Test void builtInPosesUseVanillaLikeArmAndCrouchTransforms() {
        var pose=new SkinEditorPose();
        pose.preset("arms");
        assertTrue(pose.transform("right_arm",-5,14,0)[1] < 2);
        assertTrue(pose.transform("left_arm",5,14,0)[1] < 2);
        pose.preset("crouch");
        assertEquals(3.2,pose.transform("body",0,0,0)[1],1e-9);
        assertEquals(4.2,pose.transform("head",0,0,0)[1],1e-9);
        assertEquals(4,pose.transform("right_leg",-1.9,12,0)[2],1e-9);
    }
}
