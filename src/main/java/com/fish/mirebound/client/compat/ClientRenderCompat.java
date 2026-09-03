package com.fish.mirebound.client.compat;

import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

public final class ClientRenderCompat {
    private static final String[] SHADER_PIPELINE_MOD_IDS = {
            "iris",
            "oculus"
    };
    private static Method firstPersonEnabledMethod;
    private static Method firstPersonRenderingPlayerMethod;
    private static Method irisShadowPassMethod;
    private static Object irisApiInstance;
    private static Boolean firstPersonBodyRendererLoaded;
    private static Boolean shaderPipelineLoaded;
    private static Boolean firstPersonModelLoaded;
    private static boolean searchedFirstPersonApi;
    private static boolean searchedIrisApi;

    private ClientRenderCompat() {
    }

    public static boolean isFirstPersonBodyRendererLikelyLoaded() {
        if (firstPersonBodyRendererLoaded == null) {
            ModList mods = ModList.get();
            firstPersonBodyRendererLoaded = mods.isLoaded("realcamera")
                    || mods.isLoaded("firstperson") || mods.isLoaded("firstpersonmod");
        }
        return firstPersonBodyRendererLoaded;
    }

    public static boolean useShaderSafeTransparency() {
        if (shaderPipelineLoaded == null) {
            ModList mods = ModList.get();
            shaderPipelineLoaded = false;
            for (String modId : SHADER_PIPELINE_MOD_IDS) {
                if (mods.isLoaded(modId)) {
                    shaderPipelineLoaded = true;
                    break;
                }
            }
        }
        return shaderPipelineLoaded;
    }

    public static boolean isRenderingShaderShadowPass() {
        ensureIrisApi();
        if (irisApiInstance == null || irisShadowPassMethod == null) {
            return false;
        }
        try {
            return irisShadowPassMethod.invoke(irisApiInstance) == Boolean.TRUE;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isFirstPersonModelLoaded() {
        if (firstPersonModelLoaded == null) {
            firstPersonModelLoaded = ModList.get().isLoaded("firstperson");
        }
        return firstPersonModelLoaded;
    }

    public static boolean isFirstPersonModelEnabled() {
        Method method = firstPersonEnabledMethod();
        return method != null && invokeBoolean(method);
    }

    public static boolean isFirstPersonModelRenderingPlayer() {
        Method method = firstPersonRenderingPlayerMethod();
        return method != null && invokeBoolean(method);
    }

    private static Method firstPersonEnabledMethod() {
        ensureFirstPersonApi();
        return firstPersonEnabledMethod;
    }

    private static Method firstPersonRenderingPlayerMethod() {
        ensureFirstPersonApi();
        return firstPersonRenderingPlayerMethod;
    }

    private static void ensureFirstPersonApi() {
        if (searchedFirstPersonApi) {
            return;
        }

        searchedFirstPersonApi = true;
        if (!isFirstPersonModelLoaded()) {
            return;
        }

        try {
            Class<?> api = Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI");
            firstPersonEnabledMethod = api.getMethod("isEnabled");
            firstPersonRenderingPlayerMethod = api.getMethod("isRenderingPlayer");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            firstPersonEnabledMethod = null;
            firstPersonRenderingPlayerMethod = null;
        }
    }

    private static void ensureIrisApi() {
        if (searchedIrisApi) {
            return;
        }
        searchedIrisApi = true;
        if (!useShaderSafeTransparency()) {
            return;
        }
        try {
            Class<?> api = Class.forName(
                    "net.irisshaders.iris.api.v0.IrisApi", false,
                    ClientRenderCompat.class.getClassLoader());
            irisApiInstance = api.getMethod("getInstance").invoke(null);
            irisShadowPassMethod = api.getMethod("isRenderingShadowPass");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            irisApiInstance = null;
            irisShadowPassMethod = null;
        }
    }

    private static boolean invokeBoolean(Method method) {
        try {
            return method.invoke(null) == Boolean.TRUE;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
