package com.fish.mirebound.client.compat;

import com.fish.mirebound.Mirebound;
import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.neoforged.fml.ModList;

/** Size-matched neutral PBR maps for our dynamic decals, owned and released by Iris. */
public final class IrisDecalMaterials {
    static final int FLAT_NORMAL_ABGR = 0xFFFF7F7F;
    static final int MATTE_SPECULAR_ABGR = 0;
    private static boolean searched;
    private static boolean registered;

    private IrisDecalMaterials() {
    }

    public static DynamicTexture createTexture(int width, int height) {
        registerLoader();
        return registered ? new DecalTexture(width, height) : new DynamicTexture(width, height, true);
    }

    private static void registerLoader() {
        if (searched) return;
        searched = true;
        ModList mods = ModList.get();
        if (mods == null || (!mods.isLoaded("iris") && !mods.isLoaded("oculus"))) return;
        try {
            ClassLoader classes = IrisDecalMaterials.class.getClassLoader();
            Class<?> registryClass = Class.forName(
                    "net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry", true, classes);
            Class<?> loaderClass = Class.forName(
                    "net.irisshaders.iris.pbr.loader.PBRTextureLoader", false, classes);
            Class<?> consumerClass = Class.forName(
                    "net.irisshaders.iris.pbr.loader.PBRTextureLoader$PBRTextureConsumer", false, classes);
            Method acceptNormal = consumerClass.getMethod("acceptNormalTexture", AbstractTexture.class);
            Method acceptSpecular = consumerClass.getMethod("acceptSpecularTexture", AbstractTexture.class);
            Object loader = Proxy.newProxyInstance(classes, new Class<?>[] {loaderClass}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "load" -> {
                        loadMaterials((DynamicTexture) args[0], args[2], acceptNormal, acceptSpecular);
                        yield null;
                    }
                    case "toString" -> "Mirebound decal material loader";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
            registryClass.getMethod("register", Class.class, loaderClass).invoke(
                    registryClass.getField("INSTANCE").get(null), DecalTexture.class, loader);
            registered = true;
            Mirebound.LOGGER.info("Registered size-matched Iris materials for dynamic decals");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            Mirebound.LOGGER.warn("Iris decal materials are unavailable; using default texture materials", failure);
        }
    }

    private static void loadMaterials(DynamicTexture base, Object consumer,
            Method acceptNormal, Method acceptSpecular) throws ReflectiveOperationException {
        NativeImage pixels = base.getPixels();
        if (pixels == null) return;
        DynamicTexture normal = null;
        DynamicTexture specular = null;
        boolean transferred = false;
        try {
            // Integer POM fetches use albedo coordinates. A 1x1 fallback makes
            // neighboring height samples out of bounds at every face's corners.
            normal = materialTexture(pixels.getWidth(), pixels.getHeight(), FLAT_NORMAL_ABGR);
            specular = materialTexture(pixels.getWidth(), pixels.getHeight(), MATTE_SPECULAR_ABGR);
            acceptNormal.invoke(consumer, normal);
            acceptSpecular.invoke(consumer, specular);
            transferred = true;
        } finally {
            if (!transferred) {
                if (normal != null) normal.close();
                if (specular != null) specular.close();
            }
        }
    }

    private static DynamicTexture materialTexture(int width, int height, int color) {
        NativeImage image = materialImage(width, height, color);
        DynamicTexture texture;
        try {
            texture = new DynamicTexture(image);
        } catch (RuntimeException | Error failure) {
            image.close();
            throw failure;
        }
        texture.setFilter(false, false);
        return texture;
    }

    static NativeImage materialImage(int width, int height, int color) {
        NativeImage image = new NativeImage(width, height, false);
        image.fillRect(0, 0, width, height, color);
        return image;
    }

    private static final class DecalTexture extends DynamicTexture {
        private DecalTexture(int width, int height) {
            super(width, height, true);
        }
    }
}
