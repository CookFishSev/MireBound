package com.fish.mirebound.client;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.assimilation.AssimilationSoulMotion;
import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Distance-driven soul view. World fog is optional; the post pass remains Voxy-safe. */
public final class AssimilationSoulPresentation {
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath(
            Mirebound.MOD_ID, "shaders/post/assimilation_soul.json");
    private static PostChain chain;
    private static int chainWidth = -1;
    private static int chainHeight = -1;
    private static boolean loadFailed;
    private static final Vec3[] CACHED_COLORS = new Vec3[SinkingMedium.COUNT];

    private AssimilationSoulPresentation() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Context context = context(minecraft);
        if (context == null || context.effectStrength() <= 0.001F
                || !ensureChain(minecraft)) {
            return;
        }
        chain.setUniform("EffectStrength", context.effectStrength());
        chain.setUniform("PixelSize", Mth.lerp(context.effectStrength(), 1.0F,
                context.profile().soulPixelSize()));
        chain.setUniform("BlurRadius", context.profile().soulBlurRadius()
                * context.distanceEffect());
        chain.setUniform("MediumRed", (float) context.color().x);
        chain.setUniform("MediumGreen", (float) context.color().y);
        chain.setUniform("MediumBlue", (float) context.color().z);
        chain.setUniform("ColorStrength", context.profile().soulColorStrength());
        chain.setUniform("FogStrength", context.fogStrength()
                * context.profile().soulFogOpacity());
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(event.getPartialTick().getGameTimeDeltaTicks());
        minecraft.getMainRenderTarget().bindWrite(true);
    }

    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Context context = context(Minecraft.getInstance());
        if (context == null || context.fogStrength() <= 0.001F) {
            return;
        }
        float blend = context.fogStrength() * 0.72F;
        event.setRed(Mth.lerp(blend, event.getRed(), (float) context.color().x * 0.42F));
        event.setGreen(Mth.lerp(blend, event.getGreen(), (float) context.color().y * 0.42F));
        event.setBlue(Mth.lerp(blend, event.getBlue(), (float) context.color().z * 0.42F));
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Context context = context(Minecraft.getInstance());
        if (context == null || context.fogStrength() <= 0.001F
                || event.getType() != FogType.NONE) {
            return;
        }
        float targetFar = Math.min(event.getFarPlaneDistance(),
                context.profile().soulFogDistance());
        float far = Mth.lerp(context.fogStrength(), event.getFarPlaneDistance(), targetFar);
        float near = Math.min(event.getNearPlaneDistance(), far * 0.16F);
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(Math.max(near + 0.5F, far));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    public static Context context(Minecraft minecraft) {
        if (minecraft.player == null || !ClientAssimilationState.localSoulActive(minecraft)) {
            return null;
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(minecraft.player.getId());
        Vec3 position = AssimilationSoulCamera.position();
        if (view == null || position == null) {
            return null;
        }
        AssimilationProfile profile = view.profile();
        Vec3 center = view.anchor().add(0.0D, minecraft.player.getEyeHeight() * 0.5D, 0.0D);
        float fraction = (float) AssimilationSoulMotion.distanceFraction(
                position, center, profile.soulRadius());
        float distanceEffect = (float) AssimilationSoulMotion.distanceEffect(
                fraction, profile.soulEffectStart());
        float distanceFog = (float) AssimilationSoulMotion.distanceEffect(
                fraction, profile.soulFogStart());
        float fog = Mth.lerp(distanceFog, profile.soulBaseFogStrength(), 1.0F);
        float effect = Mth.lerp(distanceEffect, profile.soulBaseEffect(), 1.0F);
        return new Context(view, profile, fraction, distanceEffect, effect, fog,
                mixedMediumColor(view));
    }

    public static float soundScale(Minecraft minecraft) {
        Context context = context(minecraft);
        if (context == null) {
            return 1.0F;
        }
        return Mth.clamp(1.0F - context.distanceEffect()
                * context.profile().soulSoundDamping(), 0.08F, 1.0F);
    }

    static Vec3 mediumColor(SinkingMedium medium) {
        Vec3 cached = CACHED_COLORS[medium.id()];
        if (cached != null) {
            return cached;
        }
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int samples = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int pixel = MudSkinTextureCache.skinCoverageTextureAbgr(
                        medium, x * 3 + 1, y * 3 + 1, x * 37 + y * 53, 255);
                red += FastColor.ABGR32.red(pixel);
                green += FastColor.ABGR32.green(pixel);
                blue += FastColor.ABGR32.blue(pixel);
                samples++;
            }
        }
        Vec3 color = new Vec3(red / (255.0D * samples), green / (255.0D * samples),
                blue / (255.0D * samples));
        CACHED_COLORS[medium.id()] = color;
        return color;
    }

    private static Vec3 mixedMediumColor(ClientAssimilationState.View view) {
        Vec3 color = Vec3.ZERO;
        float total = 0.0F;
        for (SinkingMedium medium : SinkingMedium.values()) {
            float weight = view.mediumContribution(medium);
            if (weight <= 0.0001F) {
                continue;
            }
            color = color.add(mediumColor(medium).scale(weight));
            total += weight;
        }
        return total <= 0.0001F ? mediumColor(view.medium()) : color.scale(1.0F / total);
    }

    public static void reset() {
        if (chain != null) {
            chain.close();
        }
        chain = null;
        chainWidth = -1;
        chainHeight = -1;
        loadFailed = false;
        java.util.Arrays.fill(CACHED_COLORS, null);
    }

    private static boolean ensureChain(Minecraft minecraft) {
        if (loadFailed) {
            return false;
        }
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (chain == null) {
            try {
                chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(), EFFECT);
            } catch (IOException | RuntimeException exception) {
                Mirebound.LOGGER.warn("Unable to load assimilation soul post effect", exception);
                loadFailed = true;
                return false;
            }
        }
        if (chainWidth != width || chainHeight != height) {
            chain.resize(width, height);
            chainWidth = width;
            chainHeight = height;
        }
        return true;
    }

    public record Context(ClientAssimilationState.View view, AssimilationProfile profile,
            float distanceFraction, float distanceEffect, float effectStrength,
            float fogStrength, Vec3 color) {
    }
}
