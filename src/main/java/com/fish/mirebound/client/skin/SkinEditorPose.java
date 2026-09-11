package com.fish.mirebound.client.skin;

import java.util.HashMap;
import java.util.Map;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Local preview poses. A body part owns one transform shared by its base and outer layer. */
final class SkinEditorPose {
    enum Axis { X, Y, Z }
    record Rotation(double x, double y, double z) {}
    private final Map<String, Rotation> rotations = new HashMap<>();
    private final Map<String, double[]> offsets = new HashMap<>();

    Rotation rotation(String part) { return rotations.getOrDefault(part, new Rotation(0, 0, 0)); }
    void rotate(String part, Axis axis, double amount) {
        Rotation old=rotation(part);
        rotations.put(part, switch(axis) {
            case X -> new Rotation(old.x()+amount,old.y(),old.z());
            case Y -> new Rotation(old.x(),old.y()+amount,old.z());
            case Z -> new Rotation(old.x(),old.y(),old.z()+amount);
        });
    }

    void rotateLocal(String part, Axis axis, double amount) {
        Rotation old=rotation(part);
        Quaterniond orientation=new Quaterniond().rotationXYZ(old.x(),old.y(),old.z());
        switch(axis) {
            case X -> orientation.rotateLocalX(amount);
            case Y -> orientation.rotateLocalY(amount);
            case Z -> orientation.rotateLocalZ(amount);
        }
        Vector3d euler=orientation.getEulerAnglesXYZ(new Vector3d());
        rotations.put(part,new Rotation(euler.x,euler.y,euler.z));
    }

    SkinEditorPose copy() {
        SkinEditorPose result=new SkinEditorPose();
        result.rotations.putAll(rotations);
        offsets.forEach((part,value)->result.offsets.put(part,value.clone()));
        return result;
    }

    void copyFrom(SkinEditorPose other) {
        reset();
        rotations.putAll(other.rotations);
        other.offsets.forEach((part,value)->offsets.put(part,value.clone()));
    }

    boolean sameAs(SkinEditorPose other) { return rotations.equals(other.rotations) && offsetsEqual(other); }
    private boolean offsetsEqual(SkinEditorPose other) {
        if(!offsets.keySet().equals(other.offsets.keySet())) return false;
        return offsets.keySet().stream().allMatch(part->java.util.Arrays.equals(offsets.get(part),other.offsets.get(part)));
    }
    void reset() { rotations.clear(); offsets.clear(); }

    private void offset(String part,double x,double y,double z) { offsets.put(part,new double[]{x,y,z}); }

    void preset(String name) {
        reset();
        switch(name) {
            case "arms" -> { rotate("right_arm",Axis.Z,2.0); rotate("left_arm",Axis.Z,-2.0); }
            case "walk" -> {
                rotate("right_arm",Axis.X,-0.35); rotate("left_arm",Axis.X,0.35);
                rotate("right_leg",Axis.X,0.45); rotate("left_leg",Axis.X,-0.45);
            }
            case "crouch" -> {
                rotate("body",Axis.X,.5);
                offset("body",0,3.2,0);
                offset("head",0,4.2,0);
                offset("right_arm",0,3.2,0); offset("left_arm",0,3.2,0);
                offset("right_leg",0,.2,4); offset("left_leg",0,.2,4);
            }
            default -> { }
        }
    }

    static double[] pivot(String part) {
        return switch(part) {
            case "right_arm" -> new double[]{-5,2,0};
            case "left_arm" -> new double[]{5,2,0};
            case "right_leg" -> new double[]{-1.9,12,0};
            case "left_leg" -> new double[]{1.9,12,0};
            default -> new double[]{0,0,0};
        };
    }

    double[] transform(String part, double x, double y, double z) {
        double[] pivot=pivot(part); double[] result=rotate(rotation(part),x-pivot[0],y-pivot[1],z-pivot[2]);
        double[] offset=offsets.getOrDefault(part,new double[]{0,0,0});
        return new double[]{result[0]+pivot[0]+offset[0],result[1]+pivot[1]+offset[1],result[2]+pivot[2]+offset[2]};
    }
    double[] transformNormal(String part,double x,double y,double z) { return rotate(rotation(part),x,y,z); }

    private static double[] rotate(Rotation rotation,double x,double y,double z) {
        double cx=Math.cos(rotation.x()),sx=Math.sin(rotation.x());
        double cy=Math.cos(rotation.y()),sy=Math.sin(rotation.y());
        double cz=Math.cos(rotation.z()),sz=Math.sin(rotation.z());
        double y1=y*cx-z*sx,z1=y*sx+z*cx;
        double x2=x*cy+z1*sy,z2=-x*sy+z1*cy;
        return new double[]{x2*cz-y1*sz,x2*sz+y1*cz,z2};
    }
}
