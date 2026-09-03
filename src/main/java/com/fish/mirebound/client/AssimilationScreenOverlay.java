package com.fish.mirebound.client;

import com.fish.mirebound.assimilation.AssimilationStage;
import com.fish.mirebound.assimilation.AssimilationSoulMotion;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Stable full-screen projection of the permanent HEAD/FRONT assimilation cells. */
final class AssimilationScreenOverlay {
    private static final int WIDTH = 256;
    private static final int HEIGHT = 144;
    private static final int CELL = 2;
    private static DynamicTexture texture;
    private static ResourceLocation location;
    private static long lastSignature = Long.MIN_VALUE;
    private static int lastOpacity = Integer.MIN_VALUE;

    private AssimilationScreenOverlay() {
    }

    static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null
                || ClientPollutionVisibility.isLocalSuppressed(minecraft)) {
            return;
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(minecraft.player.getId());
        if (view == null) {
            return;
        }
        if (view.stage() == AssimilationStage.RESTORING) {
            int alpha = Math.round(AssimilationSoulMotion.restoreBlackout(
                    view.restoringTicks(), view.profile().restoreTicks(),
                    view.profile().restoreBlackoutFadeTicks()) * 255.0F);
            if (alpha > 0) {
                graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight(),
                        FastColor.ARGB32.color(alpha, 0, 0, 0));
            }
            return;
        }
        if (view.restoreBlackoutTailTicks() > 0) {
            int alpha = Math.round(AssimilationSoulMotion.restoreReleaseBlackout(
                    view.restoreBlackoutTailTicks(),
                    view.profile().restoreBlackoutFadeTicks()) * 255.0F);
            if (alpha > 0) {
                graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight(),
                        FastColor.ARGB32.color(alpha, 0, 0, 0));
            }
            return;
        }
        boolean sealingTransition = view.stage() == AssimilationStage.SEALED
                && view.soulTransitionTicks() > 0;
        if ((view.stage().frozen() && !sealingTransition) || view.progress() <= 0.002F) {
            return;
        }

        int opacity = Mth.clamp(Math.round(view.profile().screenOpacity() * 255.0F), 0, 255);
        long crackSignature = AssimilationScreenCracks.tick(
                minecraft.player.getId(), view.patternSeed(), view.progress(),
                minecraft.player.tickCount, view.profile());
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        if (ensureTexture(minecraft.player.getId(), opacity, crackSignature)) {
            ScreenOverlayLayout.CoverRect cover = ScreenOverlayLayout.cover(
                    screenWidth, screenHeight, WIDTH, HEIGHT);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float transitionAlpha = sealingTransition
                    ? Mth.clamp(view.soulTransitionTicks()
                            / (float) Math.max(1, view.profile().soulTransitionTicks()), 0.0F, 1.0F)
                    : 1.0F;
            graphics.setColor(1.0F, 1.0F, 1.0F, transitionAlpha);
            graphics.blit(location, cover.x(), cover.y(), cover.width(), cover.height(),
                    0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        if (sealingTransition) {
            float total = Math.max(1.0F, view.profile().soulTransitionTicks());
            float elapsed = 1.0F - Mth.clamp(view.soulTransitionTicks() / total, 0.0F, 1.0F);
            int blackAlpha = Math.round((float) Math.sin(Math.PI * elapsed) * 184.0F);
            if (blackAlpha > 0) {
                graphics.fill(0, 0, screenWidth, screenHeight,
                        FastColor.ARGB32.color(blackAlpha, 0, 0, 0));
            }
        }
    }

    private static boolean ensureTexture(int entityId, int opacity, long crackSignature) {
        Minecraft minecraft = Minecraft.getInstance();
        if (texture == null || location == null) {
            texture = new DynamicTexture(WIDTH, HEIGHT, true);
            texture.setFilter(false, false);
            location = minecraft.getTextureManager().register(
                    "mirebound_assimilation_screen", texture);
        }
        long signature = ClientAssimilationState.signature(entityId) * 31L + crackSignature;
        if (lastSignature == signature && lastOpacity == opacity) {
            return true;
        }
        NativeImage image = texture.getPixels();
        if (image == null) {
            return false;
        }
        image.fillRect(0, 0, WIDTH, HEIGHT, 0);
        for (int y = 0; y < HEIGHT; y += CELL) {
            for (int x = 0; x < WIDTH; x += CELL) {
                float xNorm = (x + CELL * 0.5F) / WIDTH;
                float yNorm = (y + CELL * 0.5F) / HEIGHT;
                float coverage = mappedFaceCoverage(entityId, xNorm, yNorm);
                coverage *= 1.0F - AssimilationScreenCracks.openness(xNorm, yNorm);
                if (coverage <= 0.001F) {
                    continue;
                }
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(
                        MudBodyPart.HEAD, MudSurface.FRONT);
                float rowPosition = mappedFaceRow(yNorm, face.height());
                float columnPosition = mappedFaceColumn(xNorm, face.width());
                int sampled = MudSkinTextureCache.blendedAssimilationTextureAbgr(
                        entityId, MudBodyPart.HEAD, MudSurface.FRONT,
                        rowPosition, columnPosition, x / CELL, y / CELL,
                        entityId * 31 + 0x41A55A17, 255);
                int alpha = Mth.clamp(Math.round(opacity * coverage), 0, 255);
                int color = FastColor.ABGR32.color(alpha,
                        FastColor.ABGR32.blue(sampled),
                        FastColor.ABGR32.green(sampled),
                        FastColor.ABGR32.red(sampled));
                image.fillRect(x, y, Math.min(CELL, WIDTH - x),
                        Math.min(CELL, HEIGHT - y), color);
            }
        }
        texture.upload();
        texture.setFilter(false, false);
        lastSignature = signature;
        lastOpacity = opacity;
        return true;
    }

    private static int mappedFaceCell(float xNorm, float yNorm) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(
                MudBodyPart.HEAD, MudSurface.FRONT);
        int row = Mth.clamp(Math.round(mappedFaceRow(yNorm, face.height())),
                0, face.height() - 1);
        int column = Mth.clamp(Math.round(mappedFaceColumn(xNorm, face.width())),
                0, face.width() - 1);
        return MudSurfaceLayout.cellIndex(
                MudBodyPart.HEAD, MudSurface.FRONT, row, column);
    }

    private static float mappedFaceCoverage(int entityId, float xNorm, float yNorm) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(
                MudBodyPart.HEAD, MudSurface.FRONT);
        float rowPosition = mappedFaceRow(yNorm, face.height());
        float columnPosition = mappedFaceColumn(xNorm, face.width());
        int row0 = Mth.clamp((int) Math.floor(rowPosition), 0, face.height() - 1);
        int row1 = Mth.clamp(row0 + 1, 0, face.height() - 1);
        int column0 = Mth.clamp((int) Math.floor(columnPosition), 0, face.width() - 1);
        int column1 = Mth.clamp(column0 + 1, 0, face.width() - 1);
        float rowBlend = smoothStep(rowPosition - row0);
        float columnBlend = smoothStep(columnPosition - column0);
        float lower = Mth.lerp(columnBlend,
                faceCoverage(entityId, row0, column0),
                faceCoverage(entityId, row0, column1));
        float upper = Mth.lerp(columnBlend,
                faceCoverage(entityId, row1, column0),
                faceCoverage(entityId, row1, column1));
        return Mth.clamp(Mth.lerp(rowBlend, lower, upper), 0.0F, 1.0F);
    }

    static float mappedFaceRow(float screenY, int rows) {
        return (1.0F - Mth.clamp(screenY, 0.0F, 1.0F)) * Math.max(0, rows - 1);
    }

    static float mappedFaceColumn(float screenX, int columns) {
        return (1.0F - Mth.clamp(screenX, 0.0F, 1.0F)) * Math.max(0, columns - 1);
    }

    private static float faceCoverage(int entityId, int row, int column) {
        return ClientAssimilationState.coverage(entityId,
                MudSurfaceLayout.cellIndex(
                        MudBodyPart.HEAD, MudSurface.FRONT, row, column));
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - value * 2.0F);
    }

    static void reset() {
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        } else if (texture != null) {
            texture.close();
        }
        texture = null;
        location = null;
        lastSignature = Long.MIN_VALUE;
        lastOpacity = Integer.MIN_VALUE;
        AssimilationScreenCracks.reset();
    }
}
