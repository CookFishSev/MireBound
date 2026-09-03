package com.fish.mirebound.client.compat.freecam;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class FreecamCompat {
    private static final String FREECAM_CLASS = "net.xolt.freecam.Freecam";
    private static final Method IS_ENABLED = findIsEnabled();

    private FreecamCompat() {
    }

    public static boolean isExternalCameraActive(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }
        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity != null && cameraEntity != minecraft.player) {
            return true;
        }
        if (IS_ENABLED == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(IS_ENABLED.invoke(null));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static Method findIsEnabled() {
        try {
            ClassLoader loader = FreecamCompat.class.getClassLoader();
            Class<?> freecam = Class.forName(FREECAM_CLASS, false, loader);
            return freecam.getMethod("isEnabled");
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
            return null;
        }
    }
}
