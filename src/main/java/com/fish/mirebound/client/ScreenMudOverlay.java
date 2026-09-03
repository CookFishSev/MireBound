package com.fish.mirebound.client;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableCompat.SinkingSample;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.MudCoveragePatternSeed;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.network.payload.MudClodScreenImpactPayload;
import com.fish.mirebound.registry.ModBlocks;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Arrays;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Vector3f;

public final class ScreenMudOverlay {
    private static final float MIN_VISIBLE_COVERAGE = 0.025F;
    private static final int GRID_COLUMNS = MudBodyPart.SURFACE_LANES;
    private static final int GRID_ROWS = MudBodyPart.BANDS;
    private static final int VISION_ROWS = MudBodyPart.VISION_BANDS;
    private static final int VISION_COLUMNS = MudBodyPart.VISION_LANES;
    private static final int DEBUG_VISION_TOP_EXTENSION_ROWS = 3;
    private static final int DEBUG_VISION_BOTTOM_EXTENSION_ROWS = 3;
    private static final int DEBUG_VISION_ROWS = VISION_ROWS + DEBUG_VISION_TOP_EXTENSION_ROWS + DEBUG_VISION_BOTTOM_EXTENSION_ROWS;
    private static final float VISION_FACE_VERTICAL_SAMPLE_SCALE = DEBUG_VISION_ROWS / (float) VISION_ROWS;
    private static final float VISION_FACE_VERTICAL_SAMPLE_SHIFT = (DEBUG_VISION_TOP_EXTENSION_ROWS - DEBUG_VISION_BOTTOM_EXTENSION_ROWS) / (float) VISION_ROWS;
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 144;
    private static final int OVAL_PIXEL_STEP = 2;
    private static final int LOCAL_VISION_CELL_SIZE = OVAL_PIXEL_STEP;
    private static final int LOCAL_VISION_COLUMNS = 48;
    private static final int LOCAL_VISION_ROWS = 48;
    private static final float VISION_FILL_UPWARD_EXTENSION_BANDS = LOCAL_VISION_ROWS / (float) DEBUG_VISION_ROWS;
    private static final float CLEAR_RADIUS_X = 0.97F;
    private static final float CLEAR_RADIUS_Y = 0.89F;
    private static final float OVAL_INWARD_EXTENSION_MIN = 0.035F;
    private static final float OVAL_INWARD_EXTENSION_MAX = 0.115F;
    private static final float FACE_SCREEN_Y_SHIFT = -0.025F;
    private static final float VISION_SCREEN_VERTICAL_OVERSCAN = 0.14F;
    private static final float VISION_SCREEN_Y_SHIFT = 0.04F;
    private static final float VISION_CELL_OUTWARD_EXTENSION = 0.75F;
    private static final float VISION_CELL_EDGE_FEATHER = 2.95F;
    private static final float VISION_COLOR_BLEND_RADIUS = 5.85F;
    private static final double WORLD_SURFACE_TOLERANCE = 0.025D;
    private static final double VISION_FACE_FORWARD = 0.075D;
    private static final double VISION_FACE_FAR_FORWARD = 0.145D;
    private static final double VISION_FACE_HALF_WIDTH = 0.210D;
    private static final double VISION_FACE_HALF_HEIGHT = 0.220D;
    private static final double VISION_FACE_CENTER_Y_OFFSET = -0.045D;
    private static final double VISION_FACE_MIN_HORIZONTAL_INSET = -0.025D;
    private static final double VISION_FACE_HORIZONTAL_FEATHER = 0.080D;
    private static final double VISION_FACE_DEPTH_FEATHER = 0.070D;
    private static final double VISION_FACE_SEAM_SEARCH = 0.055D;
    private static final float VISION_FACE_SEAM_WEIGHT = 0.72F;
    private static final float VISION_COVERAGE_RISE_BLEND = 0.64F;
    private static final float VISION_COVERAGE_FALL_BLEND = 0.26F;
    private static final float VISION_COVERAGE_EXIT_FADE_BLEND = 0.08F;
    private static final long VISION_GATE_LINGER_TICKS = 1L;
    private static final long LOCAL_VISION_ACTIVE_REBUILD_INTERVAL_TICKS = 2L;
    private static final float VISION_TEXTURE_CROSSFADE_TICKS = 2.0F;
    private static final int STABLE_TEXTURE_SALT = 0x5F3759DF;
    private static final float[][] COVERAGE = new float[GRID_ROWS][GRID_COLUMNS];
    private static final SinkingMedium[][] MEDIA = new SinkingMedium[GRID_ROWS][GRID_COLUMNS];
    private static final long[][] VISUAL_SOURCE = new long[GRID_ROWS][GRID_COLUMNS];
    private static final float[][] VISION_COVERAGE = new float[VISION_ROWS][VISION_COLUMNS];
    private static final SinkingMedium[][] VISION_MEDIA = new SinkingMedium[VISION_ROWS][VISION_COLUMNS];
    private static final long[][] VISION_VISUAL_SOURCE = new long[VISION_ROWS][VISION_COLUMNS];
    private static final float[][] LOCAL_VISION_COVERAGE = new float[LOCAL_VISION_ROWS][LOCAL_VISION_COLUMNS];
    private static final SinkingMedium[][] LOCAL_VISION_MEDIA = new SinkingMedium[LOCAL_VISION_ROWS][LOCAL_VISION_COLUMNS];
    private static final long[][] LOCAL_VISION_VISUAL_SOURCE = new long[LOCAL_VISION_ROWS][LOCAL_VISION_COLUMNS];
    private static final float[][] DISPLAY_VISION_COVERAGE = new float[LOCAL_VISION_ROWS][LOCAL_VISION_COLUMNS];
    private static final SinkingMedium[][] DISPLAY_VISION_MEDIA = new SinkingMedium[LOCAL_VISION_ROWS][LOCAL_VISION_COLUMNS];
    private static final long[][] DISPLAY_VISION_VISUAL_SOURCE = new long[LOCAL_VISION_ROWS][LOCAL_VISION_COLUMNS];
    private static final SinkingMedium[] SINKING_MEDIA = SinkingMedium.values();
    private static final float[] ACTIVE_MEDIUM_WEIGHTS = new float[SinkingMedium.COUNT];
    private static final ScreenMudSourceMixer SOURCE_MIXER = new ScreenMudSourceMixer();
    private static final ScreenMudImpactVision.Sample IMPACT_SAMPLE =
            new ScreenMudImpactVision.Sample();
    private static float visionObstruction;
    private static float visionGridFill;
    private static float visionGridMaxCoverage;
    private static float serverVisionGridFill;
    private static SinkingMedium visionMedium = SinkingMedium.MUD;

    private static NativeImage facePixels;
    private static DynamicTexture faceTexture;
    private static ResourceLocation faceTextureLocation;
    private static NativeImage visionPixels;
    private static DynamicTexture visionTexture;
    private static ResourceLocation visionTextureLocation;
    private static NativeImage previousVisionPixels;
    private static NativeImage blendedVisionPixels;
    private static DynamicTexture blendedVisionTexture;
    private static ResourceLocation blendedVisionTextureLocation;
    private static long lastFaceSignature = Long.MIN_VALUE;
    private static long lastVisionTextureSignature = Long.MIN_VALUE;
    private static long lastLocalVisionSignature;
    private static long lastLocalVisionBuildGameTime = Long.MIN_VALUE;
    private static int lastLocalVisionCameraMode = -1;
    private static long lastVisionGateGameTime = Long.MIN_VALUE;
    private static long lastLocalVisionFadeGameTime = Long.MIN_VALUE;
    private static long lastLocalVisionAlphaStepGameTime = Long.MIN_VALUE;
    private static float previousLocalVisionRenderAlpha = 1.0F;
    private static float localVisionRenderAlpha = 1.0F;
    private static boolean visionTextureTransitionActive;
    private static double visionTextureTransitionStart;
    private static int lastVisionTextureTransitionStep = -1;

    private ScreenMudOverlay() {
    }

    public static void acceptMudClodImpact(
            MudClodScreenImpactPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null
                ? 0L : minecraft.level.getGameTime();
        ScreenMudImpactVision.accept(payload, gameTime);
        lastLocalVisionBuildGameTime = Long.MIN_VALUE;
        lastLocalVisionSignature = 0L;
    }

    public static boolean hasMudClodImpact() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && ScreenMudImpactVision.active(
                        minecraft.level.getGameTime());
    }

    public static void render(RenderGuiEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || ClientPollutionVisibility.isLocalSuppressed(minecraft)) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        float transitionAlpha = 1.0F;
        long gameTime = minecraft.level == null
                ? Long.MIN_VALUE : minecraft.level.getGameTime();
        boolean impactVisionActive = firstPerson
                && gameTime != Long.MIN_VALUE
                && ScreenMudImpactVision.active(gameTime);
        boolean sampleDynamicMud = ClientMudDebugOptions.screenSampling();
        if (!sampleDynamicMud) {
            renderVanillaBlockVision(event, minecraft, width, height,
                    firstPerson, transitionAlpha);
            if (!impactVisionActive) {
                return;
            }
        }

        long persistentFaceSignature = sampleDynamicMud && firstPerson
                ? sampleFaceCoverage(minecraft.player.getId()) : 0L;
        long faceSignature = persistentFaceSignature;
        long localVisionSignature = sampleDynamicMud || impactVisionActive
                ? rebuildLocalVisionMask(
                        minecraft, width, height, firstPerson,
                        sampleDynamicMud)
                : 0L;
        boolean hasFaceOverlay = faceSignature != 0L;
        float partialTick = event.getPartialTick()
                .getGameTimeDeltaPartialTick(false);
        double renderGameTime = gameTime == Long.MIN_VALUE
                ? 0.0D : gameTime + partialTick;
        float visionRenderAlpha = visionRenderAlpha(
                gameTime, partialTick);
        boolean hasVisionOverlay = localVisionSignature != 0L
                && visionRenderAlpha > MIN_VISIBLE_COVERAGE;
        if (!hasFaceOverlay && !hasVisionOverlay) {
            if (ClientMudDebugOptions.screenVisionDebug()) {
                ScreenMudDebugRenderer.render(event.getGuiGraphics(), width, height);
            }
            return;
        }

        if (hasFaceOverlay && (faceTextureLocation == null || faceSignature != lastFaceSignature)) {
            rebuildFaceTexture();
            lastFaceSignature = faceSignature;
        }
        if (hasVisionOverlay && (visionTextureLocation == null || localVisionSignature != lastVisionTextureSignature)) {
            rebuildVisionTexture(renderGameTime);
            lastVisionTextureSignature = localVisionSignature;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ScreenOverlayLayout.CoverRect cover = ScreenOverlayLayout.cover(
                width, height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        if (hasFaceOverlay) {
            RenderSystem.setShaderColor(
                    1.0F, 1.0F, 1.0F, transitionAlpha);
            graphics.blit(
                    faceTextureLocation,
                    cover.x(),
                    cover.y(),
                    cover.width(),
                    cover.height(),
                    0.0F,
                    0.0F,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT);
            if (hasVisionOverlay) {
                graphics.flush();
            }
        }
        if (hasVisionOverlay) {
            renderVisionTexture(
                    graphics, cover,
                    visionRenderAlpha * transitionAlpha, renderGameTime);
        }
        graphics.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.disableBlend();

        if (ClientMudDebugOptions.screenVisionDebug()) {
            ScreenMudDebugRenderer.render(event.getGuiGraphics(), width, height);
        }
    }

    private static void renderVanillaBlockVision(RenderGuiEvent event, Minecraft minecraft,
            int width, int height, boolean includePlayerFaceState,
            float transitionAlpha) {
        float strength = vanillaVisionStrength(minecraft, includePlayerFaceState);
        if (strength > MIN_VISIBLE_COVERAGE) {
            float alphaStrength = smoothStep(Mth.clamp((strength - 0.02F) / 0.32F, 0.0F, 1.0F));
            int alpha = Mth.clamp(Math.round(
                    alphaStrength * transitionAlpha * 255.0F), 0, 255);
            if (alpha > 0) {
                event.getGuiGraphics().fill(0, 0, width, height, FastColor.ARGB32.color(alpha, 0, 0, 0));
            }
        }

        if (ClientMudDebugOptions.screenVisionDebug() && minecraft.player != null) {
            ScreenMudDebugRenderer.render(event.getGuiGraphics(), width, height);
        }
    }

    private static float vanillaVisionStrength(
            Minecraft minecraft, boolean includePlayerFaceState) {
        if (minecraft.player == null) {
            return 0.0F;
        }

        float strength = 0.0F;
        if (includePlayerFaceState) {
            strength = ClientMudState.displayVisionObstruction(minecraft.player.getId());
            for (int band = 0; band < VISION_ROWS; band++) {
                for (int lane = 0; lane < VISION_COLUMNS; lane++) {
                    strength = Math.max(strength,
                            ClientMudState.displayVisionCoverage(
                                    minecraft.player.getId(), band, lane));
                }
            }
        }

        Level level = minecraft.level;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (level != null && camera != null
                && ScreenMudWorldSampler.belowSurface(level, camera.getPosition())) {
            strength = 1.0F;
        }
        return Mth.clamp(strength, 0.0F, 1.0F);
    }

    static void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        if (faceTextureLocation != null) {
            minecraft.getTextureManager().release(faceTextureLocation);
        }
        if (visionTextureLocation != null) {
            minecraft.getTextureManager().release(visionTextureLocation);
        }
        if (blendedVisionTextureLocation != null) {
            minecraft.getTextureManager().release(
                    blendedVisionTextureLocation);
        }
        facePixels = null;
        faceTexture = null;
        faceTextureLocation = null;
        visionPixels = null;
        visionTexture = null;
        visionTextureLocation = null;
        previousVisionPixels = null;
        blendedVisionPixels = null;
        blendedVisionTexture = null;
        blendedVisionTextureLocation = null;
        lastFaceSignature = Long.MIN_VALUE;
        lastVisionTextureSignature = Long.MIN_VALUE;
        lastLocalVisionSignature = 0L;
        lastLocalVisionBuildGameTime = Long.MIN_VALUE;
        lastLocalVisionCameraMode = -1;
        lastVisionGateGameTime = Long.MIN_VALUE;
        lastLocalVisionFadeGameTime = Long.MIN_VALUE;
        lastLocalVisionAlphaStepGameTime = Long.MIN_VALUE;
        previousLocalVisionRenderAlpha = 1.0F;
        localVisionRenderAlpha = 1.0F;
        visionTextureTransitionActive = false;
        visionTextureTransitionStart = 0.0D;
        lastVisionTextureTransitionStep = -1;
        ScreenMudWorldSampler.reset();
        visionObstruction = 0.0F;
        visionGridFill = 0.0F;
        visionGridMaxCoverage = 0.0F;
        serverVisionGridFill = 0.0F;
        visionMedium = SinkingMedium.MUD;
        ScreenMudImpactVision.reset();
    }

    private static long sampleFaceCoverage(int entityId) {
        long signature = 0xcbf29ce484222325L;
        ClientMudState.CoverageState display = ClientMudState.displaySnapshot(entityId);
        signature = mixSignature(signature, -13, -13,
                display.coveragePatternSeed(), 0);
        visionObstruction = display.visionObstruction();
        visionMedium = display.medium();
        boolean visible = false;
        long faceMediumMask = 0L;
        float activeTotal = 0.0F;
        for (int band = 0; band < GRID_ROWS; band++) {
            for (int lane = 0; lane < GRID_COLUMNS; lane++) {
                MudSample sample = projectedFaceSample(display, band, lane);
                float coverage = sample.strength();
                if (coverage <= MIN_VISIBLE_COVERAGE) {
                    COVERAGE[band][lane] = 0.0F;
                    MEDIA[band][lane] = SinkingMedium.MUD;
                    VISUAL_SOURCE[band][lane] = 0L;
                    signature = mixSignature(signature, band, lane, 0, 0);
                } else {
                    SinkingMedium medium = sample.medium();
                    int quantized = Mth.clamp(Math.round(coverage * 31.0F), 1, 31);
                    COVERAGE[band][lane] = coverage;
                    MEDIA[band][lane] = medium;
                    VISUAL_SOURCE[band][lane] = sample.visualSource();
                    visible = true;
                    faceMediumMask |= MudSkinTextureCache.mediumBit(medium);
                    signature = mixSignature(signature, band, lane, quantized, medium.id());
                    signature = mixSignature(signature, band, lane,
                            Long.hashCode(sample.visualSource()), 71);
                    signature = mixSignature(signature, band, lane,
                            AdaptiveMudClientCache.appearanceRevision(
                                    Minecraft.getInstance().level,
                                    sample.visualSource()), 72);
                }
            }
        }
        for (int band = 0; band < VISION_ROWS; band++) {
            for (int lane = 0; lane < VISION_COLUMNS; lane++) {
                float activeCoverage = display.visionCoverage(band, lane);
                SinkingMedium activeMedium = display.visionMedium(band, lane);
                if (activeCoverage > MIN_VISIBLE_COVERAGE) {
                    VISION_COVERAGE[band][lane] = activeCoverage;
                    VISION_MEDIA[band][lane] = activeMedium;
                    VISION_VISUAL_SOURCE[band][lane] =
                            display.visionVisualSource(band, lane);
                    activeTotal += activeCoverage;
                } else {
                    VISION_COVERAGE[band][lane] = 0.0F;
                    VISION_MEDIA[band][lane] = SinkingMedium.MUD;
                    VISION_VISUAL_SOURCE[band][lane] = 0L;
                }
            }
        }
        visionGridFill = Mth.clamp(activeTotal / (VISION_ROWS * VISION_COLUMNS), 0.0F, 1.0F);
        serverVisionGridFill = visionGridFill;
        visionGridMaxCoverage = Math.max(visionGridMaxCoverage, visionGridFill);
        if (activeTotal > MIN_VISIBLE_COVERAGE) {
            visionMedium = dominantActiveVisionMedium();
        }
        long animation = MudSkinTextureCache.skinCoverageAnimationSignature(faceMediumMask);
        if (animation != 0L) {
            signature = mixSignature(signature, -12, -12, Long.hashCode(animation), 0);
        }
        return visible ? signature : 0L;
    }

    private static SinkingMedium dominantActiveVisionMedium() {
        Arrays.fill(ACTIVE_MEDIUM_WEIGHTS, 0.0F);
        for (int band = 0; band < VISION_ROWS; band++) {
            for (int lane = 0; lane < VISION_COLUMNS; lane++) {
                float coverage = VISION_COVERAGE[band][lane];
                if (coverage <= MIN_VISIBLE_COVERAGE) {
                    continue;
                }

                SinkingMedium medium = VISION_MEDIA[band][lane];
                if (medium != null) {
                    ACTIVE_MEDIUM_WEIGHTS[medium.ordinal()] += coverage;
                }
            }
        }

        SinkingMedium best = visionMedium;
        float bestWeight = 0.0F;
        for (SinkingMedium medium : SINKING_MEDIA) {
            float weight = ACTIVE_MEDIUM_WEIGHTS[medium.ordinal()];
            if (weight > bestWeight) {
                bestWeight = weight;
                best = medium;
            }
        }
        return best == null ? SinkingMedium.MUD : best;
    }

    private static MudSample projectedFaceSample(ClientMudState.CoverageState display, int band, int lane) {
        return surfaceSample(display, band, MudSurface.FRONT, lane, 1.0F);
    }

    private static MudSample surfaceSample(ClientMudState.CoverageState display,
            int band, MudSurface surface, int lane, float weight) {
        if (!contributesToPersistentScreenMask(MudBodyPart.HEAD, surface) || weight <= 0.0F) {
            return MudSample.NONE;
        }

        MudSurfaceLayout.Face face = MudSurfaceLayout.face(MudBodyPart.HEAD, surface);
        int firstRow = band * face.height() / GRID_ROWS;
        int lastRow = Math.max(firstRow + 1,
                (band + 1) * face.height() / GRID_ROWS);
        int column = Mth.clamp(lane * face.width() / GRID_COLUMNS,
                0, face.width() - 1);
        float bestCoverage = 0.0F;
        SinkingMedium bestMedium = SinkingMedium.MUD;
        long bestVisualSource = 0L;
        for (int row = firstRow; row < Math.min(face.height(), lastRow); row++) {
            float coverage = display.surfacePixelCoverage(
                    MudBodyPart.HEAD, surface, row, column) * weight;
            if (coverage > bestCoverage) {
                bestCoverage = coverage;
                bestMedium = display.surfacePixelMedium(
                        MudBodyPart.HEAD, surface, row, column);
                bestVisualSource = display.surfacePixelVisualSource(
                        MudBodyPart.HEAD, surface, row, column);
            }
        }
        return bestCoverage <= MIN_VISIBLE_COVERAGE
                ? MudSample.NONE
                : new MudSample(Mth.clamp(bestCoverage, 0.0F, 1.0F),
                        bestMedium, bestVisualSource);
    }

    static boolean contributesToPersistentScreenMask(MudBodyPart part, MudSurface surface) {
        return part == MudBodyPart.HEAD && surface == MudSurface.FRONT;
    }

    private static long rebuildLocalVisionMask(
            Minecraft minecraft, int width, int height,
            boolean includePlayerFaceState, boolean sampleWorldMud) {
        Level level = minecraft.level;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (level == null || camera == null) {
            clearLocalVisionMask();
            lastLocalVisionSignature = 0L;
            return 0L;
        }

        long gameTime = level.getGameTime();
        boolean impactVisionActive = includePlayerFaceState
                && ScreenMudImpactVision.active(gameTime);
        int cameraMode = (includePlayerFaceState ? 1 : 0)
                | (sampleWorldMud ? 2 : 0);
        if (lastLocalVisionCameraMode != cameraMode) {
            clearLocalVisionMask();
            lastLocalVisionBuildGameTime = Long.MIN_VALUE;
            lastVisionGateGameTime = Long.MIN_VALUE;
            lastLocalVisionCameraMode = cameraMode;
        }

        boolean cameraInsideMud = sampleWorldMud
                && ScreenMudWorldSampler.belowSurface(
                        level, camera.getPosition());
        if (!ScreenMaskCameraPolicy.showsDynamicMudMask(
                includePlayerFaceState, cameraInsideMud)
                && !impactVisionActive) {
            clearLocalVisionMask();
            lastLocalVisionSignature = 0L;
            lastLocalVisionBuildGameTime = level.getGameTime();
            lastVisionGateGameTime = Long.MIN_VALUE;
            return 0L;
        }

        int fov = Mth.clamp(minecraft.options.fov().get(), 30, 110);
        if (lastLocalVisionBuildGameTime == gameTime) {
            return lastLocalVisionSignature;
        }
        if (lastLocalVisionSignature != 0L
                && lastLocalVisionBuildGameTime != Long.MIN_VALUE
                && gameTime - lastLocalVisionBuildGameTime < LOCAL_VISION_ACTIVE_REBUILD_INTERVAL_TICKS) {
            return lastLocalVisionSignature;
        }

        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        Vec3 origin = camera.getPosition();
        Vec3 forward = new Vec3(look.x(), look.y(), look.z());
        Vec3 upVec = new Vec3(up.x(), up.y(), up.z());
        Vec3 rightVec = new Vec3(-left.x(), -left.y(), -left.z());
        if (sampleWorldMud) {
            ScreenMudWorldSampler.beginFrame();
        }

        float serverVisionFill = sampleWorldMud && includePlayerFaceState
                ? serverVisionGridFill : 0.0F;
        boolean serverVisionActive = serverVisionFill > MIN_VISIBLE_COVERAGE * 0.45F || visionObstruction > MIN_VISIBLE_COVERAGE;
        if (!sampleWorldMud || !includePlayerFaceState) {
            serverVisionActive = false;
        }
        boolean localVisionActive = sampleWorldMud
                && ScreenMudWorldSampler.shouldBuildFaceMask(
                        level, origin, forward, upVec, rightVec,
                        VISION_FACE_VERTICAL_SAMPLE_SCALE);
        if (serverVisionActive || localVisionActive || impactVisionActive) {
            lastVisionGateGameTime = gameTime;
        } else if (lastVisionGateGameTime == Long.MIN_VALUE || gameTime - lastVisionGateGameTime > VISION_GATE_LINGER_TICKS) {
            lastLocalVisionBuildGameTime = gameTime;
            fadeLocalVisionAlphaPerTick(gameTime, VISION_COVERAGE_EXIT_FADE_BLEND);
            aggregateLocalVisionForDebug();
            lastLocalVisionSignature = signatureFromLocalVision(fov);
            return lastLocalVisionSignature;
        }
        if (!localVisionActive && !serverVisionActive
                && !impactVisionActive) {
            lastLocalVisionBuildGameTime = gameTime;
            fadeLocalVisionAlphaPerTick(gameTime, VISION_COVERAGE_FALL_BLEND);
            aggregateLocalVisionForDebug();
            lastLocalVisionSignature = signatureFromLocalVision(fov);
            return lastLocalVisionSignature;
        }

        lastLocalVisionBuildGameTime = gameTime;

        visionGridFill = 0.0F;
        visionGridMaxCoverage = 0.0F;
        long displayTicks = localVisionDisplayTicks(gameTime);
        setLocalVisionRenderAlpha(gameTime, displayTicks, 1.0F);
        float displayFallBlend = tickBlend(VISION_COVERAGE_EXIT_FADE_BLEND, displayTicks);
        for (int screenRow = 0; screenRow < LOCAL_VISION_ROWS; screenRow++) {
            float yNorm = (1.0F - (screenRow + 0.5F) / LOCAL_VISION_ROWS * 2.0F) * VISION_FACE_VERTICAL_SAMPLE_SCALE + VISION_FACE_VERTICAL_SAMPLE_SHIFT;
            int band = LOCAL_VISION_ROWS - 1 - screenRow;
            for (int lane = 0; lane < LOCAL_VISION_COLUMNS; lane++) {
                float xNorm = (lane + 0.5F) / LOCAL_VISION_COLUMNS * 2.0F - 1.0F;
                ScreenMudWorldSampler.Sample sample = sampleWorldMud
                        ? ScreenMudWorldSampler.facePoint(
                                level, origin, forward, upVec, rightVec,
                                xNorm, yNorm)
                        : ScreenMudWorldSampler.Sample.NONE;
                float sampleStrength = sample.strength();
                SinkingMedium sampleMedium = sample.medium();
                long sampleVisualSource = sample.visualSource();
                if (impactVisionActive) {
                    ScreenMudImpactVision.sampleAt(
                            band, lane, gameTime, IMPACT_SAMPLE);
                } else {
                    IMPACT_SAMPLE.set(0.0F, SinkingMedium.MUD);
                }
                float impactStrength = IMPACT_SAMPLE.coverage();
                if (impactStrength > sampleStrength) {
                    sampleStrength = impactStrength;
                    sampleMedium = IMPACT_SAMPLE.medium();
                    sampleVisualSource = 0L;
                }
                boolean hasSample = sampleStrength > MIN_VISIBLE_COVERAGE;
                LOCAL_VISION_COVERAGE[band][lane] = sampleStrength;
                if (hasSample) {
                    LOCAL_VISION_MEDIA[band][lane] = sampleMedium;
                    LOCAL_VISION_VISUAL_SOURCE[band][lane] = sampleVisualSource;
                } else {
                    LOCAL_VISION_MEDIA[band][lane] = SinkingMedium.MUD;
                    LOCAL_VISION_VISUAL_SOURCE[band][lane] = 0L;
                }

                float displayCoverage = DISPLAY_VISION_COVERAGE[band][lane];
                if (sampleStrength > displayCoverage) {
                    displayCoverage = Mth.lerp(VISION_COVERAGE_RISE_BLEND, displayCoverage, sampleStrength);
                    DISPLAY_VISION_MEDIA[band][lane] = sampleMedium;
                    DISPLAY_VISION_VISUAL_SOURCE[band][lane] = sampleVisualSource;
                } else {
                    displayCoverage = Mth.lerp(displayFallBlend, displayCoverage, sampleStrength);
                    if (displayCoverage <= MIN_VISIBLE_COVERAGE) {
                        DISPLAY_VISION_MEDIA[band][lane] = SinkingMedium.MUD;
                        DISPLAY_VISION_VISUAL_SOURCE[band][lane] = 0L;
                    }
                }
                if (hasSample) {
                    DISPLAY_VISION_MEDIA[band][lane] = sampleMedium;
                    DISPLAY_VISION_VISUAL_SOURCE[band][lane] = sampleVisualSource;
                }
                DISPLAY_VISION_COVERAGE[band][lane] = displayCoverage;

                if (displayCoverage > MIN_VISIBLE_COVERAGE) {
                    visionGridFill += displayCoverage;
                    visionGridMaxCoverage = Math.max(visionGridMaxCoverage, displayCoverage);
                }
            }
        }

        aggregateLocalVisionForDebug();
        visionGridFill = Mth.clamp(visionGridFill / (LOCAL_VISION_ROWS * LOCAL_VISION_COLUMNS), 0.0F, 1.0F);
        lastLocalVisionSignature = signatureFromLocalVision(fov);
        return lastLocalVisionSignature;
    }

    private static void fadeLocalVisionAlphaPerTick(long gameTime, float blend) {
        long ticks = localVisionDisplayTicks(gameTime);
        if (ticks <= 0L) {
            return;
        }

        float start = localVisionRenderAlpha;
        previousLocalVisionRenderAlpha = ticks <= 1L
                ? start
                : Mth.lerp(tickBlend(blend, ticks - 1L), start, 0.0F);
        localVisionRenderAlpha = Mth.lerp(tickBlend(blend, ticks), start, 0.0F);
        lastLocalVisionAlphaStepGameTime = gameTime;
        if (localVisionRenderAlpha <= MIN_VISIBLE_COVERAGE) {
            clearLocalVisionMask();
        }
    }

    private static void setLocalVisionRenderAlpha(
            long gameTime, long ticks, float target) {
        if (ticks > 0L) {
            previousLocalVisionRenderAlpha = localVisionRenderAlpha;
            lastLocalVisionAlphaStepGameTime = gameTime;
        }
        localVisionRenderAlpha = target;
    }

    private static float visionRenderAlpha(long gameTime, float partialTick) {
        return interpolatedVisionAlpha(
                previousLocalVisionRenderAlpha,
                localVisionRenderAlpha,
                partialTick,
                gameTime == lastLocalVisionAlphaStepGameTime);
    }

    static float interpolatedVisionAlpha(
            float previous, float current, float partialTick,
            boolean changedThisTick) {
        return changedThisTick
                ? Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), previous, current)
                : current;
    }

    private static long localVisionDisplayTicks(long gameTime) {
        long previous = lastLocalVisionFadeGameTime == Long.MIN_VALUE ? gameTime - 1L : lastLocalVisionFadeGameTime;
        long ticks = Mth.clamp(gameTime - previous, 0L, 8L);
        lastLocalVisionFadeGameTime = gameTime;
        return ticks;
    }

    private static float tickBlend(float blend, long ticks) {
        if (ticks <= 0L) {
            return 0.0F;
        }
        return 1.0F - (float) Math.pow(1.0F - Mth.clamp(blend, 0.0F, 1.0F), ticks);
    }

    private static long signatureFromLocalVision(int fov) {
        if (localVisionRenderAlpha <= MIN_VISIBLE_COVERAGE || visionGridMaxCoverage <= MIN_VISIBLE_COVERAGE) {
            return 0L;
        }

        long signature = 0xcbf29ce484222325L;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            signature = mixSignature(signature, -13, -13,
                    ClientMudState.coveragePatternSeed(minecraft.player.getId()), 0);
        }
        long mediumMask = 0L;
        for (int band = 0; band < LOCAL_VISION_ROWS; band++) {
            for (int lane = 0; lane < LOCAL_VISION_COLUMNS; lane++) {
                float coverage = DISPLAY_VISION_COVERAGE[band][lane];
                SinkingMedium medium = DISPLAY_VISION_MEDIA[band][lane] == null ? SinkingMedium.MUD : DISPLAY_VISION_MEDIA[band][lane];
                signature = mixSignature(signature, band, lane, Mth.clamp(Math.round(coverage * 63.0F), 0, 63), medium.id());
                signature = mixSignature(signature, band, lane,
                        Long.hashCode(DISPLAY_VISION_VISUAL_SOURCE[band][lane]), 73);
                signature = mixSignature(signature, band, lane,
                        AdaptiveMudClientCache.appearanceRevision(
                                minecraft.level,
                                DISPLAY_VISION_VISUAL_SOURCE[band][lane]), 74);
                if (coverage > MIN_VISIBLE_COVERAGE) {
                    mediumMask |= MudSkinTextureCache.mediumBit(medium);
                }
            }
        }
        signature = mixSignature(signature, -9, -9, Mth.clamp(Math.round(visionGridFill * 63.0F), 0, 63), fov);
        signature = mixSignature(signature, -10, -10,
                Mth.clamp(Math.round(visionGridMaxCoverage * 63.0F), 0, 63), visionMedium.id());
        long animation = MudSkinTextureCache.coverTextureAnimationSignature(mediumMask,
                Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime());
        return animation == 0L
                ? signature
                : mixSignature(signature, -11, -11, Long.hashCode(animation), 0);
    }

    private static void clearLocalVisionMask() {
        visionGridFill = 0.0F;
        visionGridMaxCoverage = 0.0F;
        previousLocalVisionRenderAlpha = 1.0F;
        localVisionRenderAlpha = 1.0F;
        lastLocalVisionAlphaStepGameTime = Long.MIN_VALUE;
        lastVisionTextureSignature = Long.MIN_VALUE;
        visionTextureTransitionActive = false;
        lastVisionTextureTransitionStep = -1;
        for (int band = 0; band < LOCAL_VISION_ROWS; band++) {
            for (int lane = 0; lane < LOCAL_VISION_COLUMNS; lane++) {
                LOCAL_VISION_COVERAGE[band][lane] = 0.0F;
                LOCAL_VISION_MEDIA[band][lane] = SinkingMedium.MUD;
                LOCAL_VISION_VISUAL_SOURCE[band][lane] = 0L;
                DISPLAY_VISION_COVERAGE[band][lane] = 0.0F;
                DISPLAY_VISION_MEDIA[band][lane] = SinkingMedium.MUD;
                DISPLAY_VISION_VISUAL_SOURCE[band][lane] = 0L;
            }
        }
        for (int band = 0; band < VISION_ROWS; band++) {
            for (int lane = 0; lane < VISION_COLUMNS; lane++) {
                VISION_COVERAGE[band][lane] = 0.0F;
                VISION_MEDIA[band][lane] = SinkingMedium.MUD;
                VISION_VISUAL_SOURCE[band][lane] = 0L;
            }
        }
    }

    private static void aggregateLocalVisionForDebug() {
        int bandsPerCell = Math.max(1, LOCAL_VISION_ROWS / VISION_ROWS);
        int lanesPerCell = Math.max(1, LOCAL_VISION_COLUMNS / VISION_COLUMNS);
        for (int band = 0; band < VISION_ROWS; band++) {
            for (int lane = 0; lane < VISION_COLUMNS; lane++) {
                float total = 0.0F;
                float best = 0.0F;
                SinkingMedium bestMedium = SinkingMedium.MUD;
                long bestVisualSource = 0L;
                for (int localBand = band * bandsPerCell; localBand < Math.min(LOCAL_VISION_ROWS, (band + 1) * bandsPerCell); localBand++) {
                    for (int localLane = lane * lanesPerCell; localLane < Math.min(LOCAL_VISION_COLUMNS, (lane + 1) * lanesPerCell); localLane++) {
                        float coverage = DISPLAY_VISION_COVERAGE[localBand][localLane];
                        total += coverage;
                        if (coverage > best) {
                            best = coverage;
                            bestMedium = DISPLAY_VISION_MEDIA[localBand][localLane] == null ? SinkingMedium.MUD : DISPLAY_VISION_MEDIA[localBand][localLane];
                            bestVisualSource = DISPLAY_VISION_VISUAL_SOURCE[localBand][localLane];
                        }
                    }
                }
                VISION_COVERAGE[band][lane] = Mth.clamp(total / (bandsPerCell * lanesPerCell), 0.0F, 1.0F);
                VISION_MEDIA[band][lane] = bestMedium;
                VISION_VISUAL_SOURCE[band][lane] = bestVisualSource;
            }
        }
    }

    private static long mixSignature(long signature, int band, int lane, int coverage, int medium) {
        signature ^= band * 131L + lane * 17L + coverage * 4099L + medium * 65537L;
        return signature * 0x100000001b3L;
    }

    private static void rebuildFaceTexture() {
        ensureFaceTexture();
        if (rebuildFacePixels()) {
            faceTexture.upload();
        }
    }

    private static void rebuildVisionTexture(double renderGameTime) {
        ensureVisionTexture();
        if (lastVisionTextureSignature != Long.MIN_VALUE) {
            ensureVisionTransitionTexture();
            copyPixels(
                    visionTextureTransitionActive
                            ? blendedVisionPixels : visionPixels,
                    previousVisionPixels);
            visionTextureTransitionStart = renderGameTime;
            visionTextureTransitionActive = true;
            lastVisionTextureTransitionStep = -1;
        } else {
            visionTextureTransitionActive = false;
            lastVisionTextureTransitionStep = -1;
        }
        if (rebuildVisionPixels()) {
            visionTexture.upload();
        }
    }

    private static void renderVisionTexture(
            GuiGraphics graphics, ScreenOverlayLayout.CoverRect cover,
            float alpha, double renderGameTime) {
        float progress = visionTextureTransitionProgress(
                renderGameTime, visionTextureTransitionStart,
                VISION_TEXTURE_CROSSFADE_TICKS);
        if (visionTextureTransitionActive && progress < 1.0F) {
            int step = Mth.clamp(Math.round(progress * 255.0F), 0, 255);
            if (step != lastVisionTextureTransitionStep) {
                rebuildBlendedVisionPixels(step / 255.0F);
                blendedVisionTexture.upload();
                lastVisionTextureTransitionStep = step;
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            blitVisionTexture(
                    graphics, cover, blendedVisionTextureLocation);
            return;
        }

        visionTextureTransitionActive = false;
        lastVisionTextureTransitionStep = -1;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        blitVisionTexture(graphics, cover, visionTextureLocation);
    }

    private static void blitVisionTexture(
            GuiGraphics graphics, ScreenOverlayLayout.CoverRect cover,
            ResourceLocation texture) {
        graphics.blit(
                texture,
                cover.x(),
                cover.y(),
                cover.width(),
                cover.height(),
                0.0F,
                0.0F,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
    }

    static float visionTextureTransitionProgress(
            double renderGameTime, double transitionStart,
            float durationTicks) {
        if (durationTicks <= 0.0F) {
            return 1.0F;
        }
        return Mth.clamp((float) ((renderGameTime - transitionStart)
                / durationTicks), 0.0F, 1.0F);
    }

    static int interpolateNativeColor(
            int previous, int current, float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int from = previous >>> shift & 255;
            int to = current >>> shift & 255;
            result |= Mth.clamp(Math.round(
                    Mth.lerp(clamped, from, to)), 0, 255) << shift;
        }
        return result;
    }

    private static void rebuildBlendedVisionPixels(float progress) {
        for (int y = 0; y < TEXTURE_HEIGHT; y += OVAL_PIXEL_STEP) {
            for (int x = 0; x < TEXTURE_WIDTH; x += OVAL_PIXEL_STEP) {
                int color = interpolateNativeColor(
                        previousVisionPixels.getPixelRGBA(x, y),
                        visionPixels.getPixelRGBA(x, y),
                        progress);
                int maxX = Math.min(TEXTURE_WIDTH,
                        x + OVAL_PIXEL_STEP);
                int maxY = Math.min(TEXTURE_HEIGHT,
                        y + OVAL_PIXEL_STEP);
                for (int cellY = y; cellY < maxY; cellY++) {
                    for (int cellX = x; cellX < maxX; cellX++) {
                        blendedVisionPixels.setPixelRGBA(
                                cellX, cellY, color);
                    }
                }
            }
        }
    }

    private static boolean rebuildFacePixels() {
        Minecraft minecraft = Minecraft.getInstance();
        int patternSeed = minecraft.player == null ? 0
                : ClientMudState.coveragePatternSeed(minecraft.player.getId());
        int salt = MudCoveragePatternSeed.mix(STABLE_TEXTURE_SALT, patternSeed);
        boolean changed = false;
        for (int y0 = 0; y0 < TEXTURE_HEIGHT; y0 += OVAL_PIXEL_STEP) {
            int y = Math.min(TEXTURE_HEIGHT - 1, y0 + OVAL_PIXEL_STEP / 2);
            float yNorm = y / (float) (TEXTURE_HEIGHT - 1);
            float faceSampleYNorm = Mth.clamp(yNorm + FACE_SCREEN_Y_SHIFT, 0.0F, 1.0F);
            float bandPosition = (1.0F - faceSampleYNorm) * (GRID_ROWS - 1);
            int band0 = Mth.clamp((int) Math.floor(bandPosition), 0, GRID_ROWS - 1);
            int band1 = Mth.clamp(band0 + 1, 0, GRID_ROWS - 1);
            float bandBlend = bandPosition - band0;

            for (int x0 = 0; x0 < TEXTURE_WIDTH; x0 += OVAL_PIXEL_STEP) {
                int x = Math.min(TEXTURE_WIDTH - 1, x0 + OVAL_PIXEL_STEP / 2);
                float ring = ovalRingStrength(x, y);
                float xNorm = x / (float) (TEXTURE_WIDTH - 1);
                float lanePosition = (1.0F - xNorm) * (GRID_COLUMNS - 1);
                int lane0 = Mth.clamp((int) Math.floor(lanePosition), 0, GRID_COLUMNS - 1);
                int lane1 = Mth.clamp(lane0 + 1, 0, GRID_COLUMNS - 1);
                float laneBlend = lanePosition - lane0;

                int composed = 0;
                float coverage = smoothedFaceCoverage(bandPosition, lanePosition);
                float faceStrength = coverage * ring;
                if (faceStrength > MIN_VISIBLE_COVERAGE) {
                    SinkingMedium faceMedium = dominantMedium(band0, band1, lane0, lane1, bandBlend, laneBlend);
                    float faceTranslucentWeight = translucentMediumWeight(band0, lane0, bandBlend, laneBlend, COVERAGE, MEDIA);
                    float faceNoise = noise(x >> 1, y >> 1, salt + faceMedium.id() * 977);
                    if (coverage >= 0.42F || faceNoise <= 0.52F + coverage * 0.62F) {
                        int faceAlpha = faceAlpha(faceStrength, coverage, faceTranslucentWeight);
                        if (faceAlpha > 3) {
                            float faceGrain = Mth.clamp(0.82F + noise(x, y, salt + faceMedium.id() * 313 + 41) * 0.18F, 0.82F, 1.0F);
                            int faceArgb = blendedCoverTextureArgb(x, y, salt, faceAlpha, faceMedium, band0, band1, lane0, lane1, bandBlend, laneBlend);
                            composed = darkenArgb(faceArgb, faceGrain);
                        }
                    }
                }
                changed |= updatePixelCell(facePixels, x0, y0, composed);
            }
        }
        return changed;
    }

    private static boolean rebuildVisionPixels() {
        Minecraft minecraft = Minecraft.getInstance();
        int patternSeed = minecraft.player == null ? 0
                : ClientMudState.coveragePatternSeed(minecraft.player.getId());
        int salt = MudCoveragePatternSeed.mix(STABLE_TEXTURE_SALT, patternSeed);
        boolean changed = false;
        for (int y0 = 0; y0 < TEXTURE_HEIGHT; y0 += OVAL_PIXEL_STEP) {
            int y = Math.min(TEXTURE_HEIGHT - 1, y0 + OVAL_PIXEL_STEP / 2);
            float yNorm = y / (float) (TEXTURE_HEIGHT - 1);
            float localVisionBandPosition = localVisionBandPositionForScreenY(yNorm);
            for (int x0 = 0; x0 < TEXTURE_WIDTH; x0 += OVAL_PIXEL_STEP) {
                int x = Math.min(TEXTURE_WIDTH - 1, x0 + OVAL_PIXEL_STEP / 2);
                float xNorm = x / (float) (TEXTURE_WIDTH - 1);
                float localVisionLanePosition = localVisionLanePositionForScreenX(xNorm);
                VisionScreenSample visionSample = screenMappedVisionFillSample(
                        localVisionBandPosition, localVisionLanePosition);
                float activeMask = activeVisionMask(visionSample.coverage(), x, y);
                int composed = 0;
                if (activeMask > MIN_VISIBLE_COVERAGE) {
                    float activeFade = activeCoverageFade(visionSample.opacityCoverage());
                    composed = activeDarkArgb(
                            activeMask, visionSample.translucentWeight(), activeFade,
                            visionSample.medium(), visionSample.visualSource(), x, y);
                    int submergedAlpha = submergedAlpha(activeMask, visionSample.coverage(), visionSample.translucentWeight(), activeFade);
                    if (submergedAlpha > 3) {
                        SinkingMedium submergedMedium = visionSample.medium();
                        float grain = Mth.clamp(0.70F + noise(x, y, salt + submergedMedium.id() * 313 + 41) * 0.30F, 0.64F, 1.0F);
                        int submergedArgb = blendedVisionRasterCoverTextureArgb(x, y, salt, submergedAlpha, submergedMedium, localVisionBandPosition, localVisionLanePosition);
                        float full = smoothStep(Mth.clamp((visionGridFill - 0.34F) / 0.66F, 0.0F, 1.0F));
                        composed = alphaComposite(composed, darkenArgb(submergedArgb, submergedBrightness(visionSample.translucentWeight(), full, grain)));
                    }
                }
                changed |= updatePixelCell(visionPixels, x0, y0, composed);
            }
        }
        return changed;
    }

    private static float activeVisionMask(float coverage, int x, int y) {
        if (coverage <= MIN_VISIBLE_COVERAGE) {
            return 0.0F;
        }

        return visionLayerStrength(coverage, x, y, STABLE_TEXTURE_SALT);
    }

    private static void fillPixelCell(NativeImage targetPixels, int x0, int y0, int argb) {
        int abgr = argbToAbgr(argb);
        int maxX = Math.min(TEXTURE_WIDTH, x0 + OVAL_PIXEL_STEP);
        int maxY = Math.min(TEXTURE_HEIGHT, y0 + OVAL_PIXEL_STEP);
        for (int y = y0; y < maxY; y++) {
            for (int x = x0; x < maxX; x++) {
                targetPixels.setPixelRGBA(x, y, abgr);
            }
        }
    }

    private static boolean updatePixelCell(
            NativeImage targetPixels, int x0, int y0, int argb) {
        int normalized = (argb >>> 24 & 255) <= 3 ? 0 : argb;
        int abgr = argbToAbgr(normalized);
        if (targetPixels.getPixelRGBA(x0, y0) == abgr) {
            return false;
        }
        fillPixelCell(targetPixels, x0, y0, normalized);
        return true;
    }

    private static int mappedScreenYForVisionBand(int band, int screenHeight) {
        float gridYNorm = 1.0F - band / (float) Math.max(1, VISION_ROWS - 1);
        float normalized = visionGridYToScreenY(gridYNorm);
        return Mth.clamp(Math.round(normalized * screenHeight), 0, screenHeight);
    }

    private static float visionScreenYToGridY(float yNorm) {
        float shifted = yNorm + ClientMudDebugOptions.visionScreenShiftY();
        float pulled = Mth.clamp((shifted - 0.5F) / Math.max(0.05F, 1.0F - ClientMudDebugOptions.visionScreenEdgePullY() * 2.0F) + 0.5F, 0.0F, 1.0F);
        return Mth.clamp((pulled - 0.5F) * (1.0F + ClientMudDebugOptions.visionScreenOverscanY() * 2.0F) + 0.5F, 0.0F, 1.0F);
    }

    private static float visionGridYToScreenY(float gridYNorm) {
        float normalized = (gridYNorm - 0.5F) / (1.0F + ClientMudDebugOptions.visionScreenOverscanY() * 2.0F) + 0.5F;
        float pulled = (normalized - 0.5F) * Math.max(0.05F, 1.0F - ClientMudDebugOptions.visionScreenEdgePullY() * 2.0F) + 0.5F;
        return pulled - ClientMudDebugOptions.visionScreenShiftY();
    }

    private static float localVisionBandPositionForScreenY(float yNorm) {
        return (1.0F - visionScreenYToGridY(yNorm)) * (LOCAL_VISION_ROWS - 1);
    }

    private static float localVisionLanePositionForScreenX(float xNorm) {
        return Mth.clamp(xNorm, 0.0F, 1.0F) * (LOCAL_VISION_COLUMNS - 1);
    }

    static int debugVisionColumns() {
        return VISION_COLUMNS;
    }

    static int debugVisionRows() {
        return DEBUG_VISION_ROWS;
    }

    static int debugMappedVisionRows() {
        return VISION_ROWS;
    }

    static int debugTopExtensionRows() {
        return DEBUG_VISION_TOP_EXTENSION_ROWS;
    }

    static int debugMappedScreenY(int band, int screenHeight) {
        return mappedScreenYForVisionBand(band, screenHeight);
    }

    static float debugLocalCoverage(int screenRow, int lane) {
        int rowStart = screenRow * LOCAL_VISION_ROWS / DEBUG_VISION_ROWS;
        int rowEnd = Math.max(rowStart + 1,
                (screenRow + 1) * LOCAL_VISION_ROWS / DEBUG_VISION_ROWS);
        int laneStart = lane * LOCAL_VISION_COLUMNS / VISION_COLUMNS;
        int laneEnd = Math.max(laneStart + 1,
                (lane + 1) * LOCAL_VISION_COLUMNS / VISION_COLUMNS);
        float total = 0.0F;
        int count = 0;
        for (int row = rowStart; row < Math.min(LOCAL_VISION_ROWS, rowEnd); row++) {
            int band = LOCAL_VISION_ROWS - 1 - row;
            for (int localLane = laneStart;
                    localLane < Math.min(LOCAL_VISION_COLUMNS, laneEnd); localLane++) {
                total += DISPLAY_VISION_COVERAGE[band][localLane];
                count++;
            }
        }
        return count <= 0 ? 0.0F : Mth.clamp(total / count, 0.0F, 1.0F);
    }

    static float debugMappedCoverage(int row, int lane) {
        float yNorm = (row + 0.5F) / VISION_ROWS;
        float xNorm = (lane + 0.5F) / VISION_COLUMNS;
        return screenMappedVisionFillSample(
                localVisionBandPositionForScreenY(yNorm),
                localVisionLanePositionForScreenX(xNorm)).coverage();
    }

    private static int activeDarkArgb(float strength, float translucentWeight, float fade,
            SinkingMedium medium, long visualSource, int x, int y) {
        float gridDark = smoothStep(Mth.clamp((visionGridFill - 0.22F) / 0.78F, 0.0F, 1.0F));
        float depthDark = smoothStep(Mth.clamp((visionObstruction - 0.58F) / 0.42F, 0.0F, 1.0F)) * 0.62F;
        float global = Math.max(gridDark, depthDark);
        float solidAlpha = smoothStep(strength) * Mth.lerp(global, 0.10F, 0.74F);
        float translucentAlpha = smoothStep(strength) * Mth.lerp(global, 0.025F, 0.22F);
        float alpha = Mth.lerp(Mth.clamp(translucentWeight, 0.0F, 1.0F), solidAlpha, translucentAlpha) * Mth.clamp(fade, 0.0F, 1.0F);
        int alphaValue = Mth.clamp(Math.round(alpha * 255.0F), 0, 210);
        if (medium == SinkingMedium.TENDER_FLESH) {
            int saliva = sampledCoverageTextureArgb(
                    medium, visualSource, x, y,
                    STABLE_TEXTURE_SALT ^ 0x6A09E667, alphaValue, true);
            int red = Math.round((saliva >>> 16 & 255) * 0.78F);
            int green = Math.round((saliva >>> 8 & 255) * 0.78F);
            int blue = Math.round((saliva & 255) * 0.78F);
            return FastColor.ARGB32.color(alphaValue,
                    Mth.clamp(red, 0, 255), Mth.clamp(green, 0, 255),
                    Mth.clamp(blue, 0, 255));
        }
        int greenTint = Mth.clamp(Math.round(translucentWeight * 18.0F), 0, 18);
        return FastColor.ARGB32.color(alphaValue, 0, greenTint, 0);
    }

    private static int alphaComposite(int bottom, int top) {
        int topAlpha = top >>> 24 & 255;
        if (topAlpha <= 0) {
            return bottom;
        }
        int bottomAlpha = bottom >>> 24 & 255;
        if (bottomAlpha <= 0) {
            return top;
        }

        float topA = topAlpha / 255.0F;
        float bottomA = bottomAlpha / 255.0F;
        float outA = topA + bottomA * (1.0F - topA);
        if (outA <= 1.0E-5F) {
            return 0;
        }

        int topRed = top >>> 16 & 255;
        int topGreen = top >>> 8 & 255;
        int topBlue = top & 255;
        int bottomRed = bottom >>> 16 & 255;
        int bottomGreen = bottom >>> 8 & 255;
        int bottomBlue = bottom & 255;
        int red = Math.round((topRed * topA + bottomRed * bottomA * (1.0F - topA)) / outA);
        int green = Math.round((topGreen * topA + bottomGreen * bottomA * (1.0F - topA)) / outA);
        int blue = Math.round((topBlue * topA + bottomBlue * bottomA * (1.0F - topA)) / outA);
        return FastColor.ARGB32.color(Mth.clamp(Math.round(outA * 255.0F), 0, 255), Mth.clamp(red, 0, 255), Mth.clamp(green, 0, 255), Mth.clamp(blue, 0, 255));
    }

    private static VisionScreenSample screenMappedVisionFillSample(float bandPosition, float lanePosition) {
        VisionScreenSample base = screenMappedVisionSample(bandPosition, lanePosition);
        VisionScreenSample raisedTop = screenMappedVisionSample(bandPosition - VISION_FILL_UPWARD_EXTENSION_BANDS, lanePosition);
        return raisedTop.coverage() > base.coverage() ? raisedTop : base;
    }

    private static VisionScreenSample screenMappedVisionSample(float bandPosition, float lanePosition) {
        float unionCoverage = 0.0F;
        float opacityCoverage = 0.0F;
        SinkingMedium bestMedium = visionMedium;
        long bestVisualSource = 0L;
        float bestMediumScore = 0.0F;
        float translucentTotal = 0.0F;
        float totalWeight = 0.0F;
        int minBand = Mth.clamp((int) Math.floor(bandPosition - VISION_CELL_EDGE_FEATHER - 1.0F), 0, LOCAL_VISION_ROWS - 1);
        int maxBand = Mth.clamp((int) Math.ceil(bandPosition + VISION_CELL_EDGE_FEATHER + 1.0F), 0, LOCAL_VISION_ROWS - 1);
        int minLane = Mth.clamp((int) Math.floor(lanePosition - VISION_CELL_EDGE_FEATHER - 1.0F), 0, LOCAL_VISION_COLUMNS - 1);
        int maxLane = Mth.clamp((int) Math.ceil(lanePosition + VISION_CELL_EDGE_FEATHER + 1.0F), 0, LOCAL_VISION_COLUMNS - 1);
        for (int band = minBand; band <= maxBand; band++) {
            for (int lane = minLane; lane <= maxLane; lane++) {
                float sourceCoverage = DISPLAY_VISION_COVERAGE[band][lane];
                if (sourceCoverage <= MIN_VISIBLE_COVERAGE) {
                    continue;
                }

                float distanceToCell = Math.max(Math.abs(band - bandPosition), Math.abs(lane - lanePosition));
                float edgeDistance = Math.max(0.0F, distanceToCell - 0.50F - VISION_CELL_OUTWARD_EXTENSION);
                float weight = edgeDistance <= 0.0F
                        ? 1.0F
                        : 1.0F - smootherStep(Mth.clamp(edgeDistance / VISION_CELL_EDGE_FEATHER, 0.0F, 1.0F));
                float coverage = sourceCoverage * weight;
                if (coverage <= 1.0E-5F) {
                    continue;
                }

                unionCoverage = 1.0F - (1.0F - unionCoverage) * (1.0F - coverage);
                opacityCoverage = Math.max(opacityCoverage, sourceCoverage);
                SinkingMedium medium = DISPLAY_VISION_MEDIA[band][lane] == null ? SinkingMedium.MUD : DISPLAY_VISION_MEDIA[band][lane];
                if (coverage > bestMediumScore) {
                    bestMediumScore = coverage;
                    bestMedium = medium;
                    bestVisualSource = DISPLAY_VISION_VISUAL_SOURCE[band][lane];
                }
                totalWeight += coverage;
                translucentTotal += coverage * translucentMediumFactor(medium);
            }
        }
        float translucentWeight = totalWeight <= 1.0E-5F ? translucentMediumFactor(bestMedium) : Mth.clamp(translucentTotal / totalWeight, 0.0F, 1.0F);
        return new VisionScreenSample(Mth.clamp(unionCoverage, 0.0F, 1.0F),
                bestMedium, bestVisualSource, translucentWeight,
                Mth.clamp(opacityCoverage, 0.0F, 1.0F));
    }

    private static void ensureFaceTexture() {
        if (faceTexture != null) {
            return;
        }

        facePixels = new NativeImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, true);
        faceTexture = new DynamicTexture(facePixels);
        faceTextureLocation = Minecraft.getInstance().getTextureManager().register("mirebound_screen_mud_face", faceTexture);
    }

    private static void ensureVisionTexture() {
        if (visionTexture != null) {
            return;
        }

        visionPixels = new NativeImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, true);
        visionTexture = new DynamicTexture(visionPixels);
        visionTextureLocation = Minecraft.getInstance().getTextureManager().register("mirebound_screen_mud_vision", visionTexture);
    }

    private static void ensureVisionTransitionTexture() {
        if (blendedVisionTexture != null) {
            return;
        }

        previousVisionPixels = new NativeImage(
                TEXTURE_WIDTH, TEXTURE_HEIGHT, true);
        blendedVisionPixels = new NativeImage(
                TEXTURE_WIDTH, TEXTURE_HEIGHT, true);
        blendedVisionTexture = new DynamicTexture(blendedVisionPixels);
        blendedVisionTextureLocation = Minecraft.getInstance()
                .getTextureManager().register(
                        "mirebound_screen_mud_vision_blended",
                        blendedVisionTexture);
    }

    private static void copyPixels(NativeImage source, NativeImage target) {
        for (int y = 0; y < TEXTURE_HEIGHT; y++) {
            for (int x = 0; x < TEXTURE_WIDTH; x++) {
                target.setPixelRGBA(x, y, source.getPixelRGBA(x, y));
            }
        }
    }

    private static float interpolatedCoverage(int band0, int band1, int lane0, int lane1, float bandBlend, float laneBlend) {
        float low = Mth.lerp(laneBlend, COVERAGE[band0][lane0], COVERAGE[band0][lane1]);
        float high = Mth.lerp(laneBlend, COVERAGE[band1][lane0], COVERAGE[band1][lane1]);
        return Mth.lerp(bandBlend, low, high);
    }

    private static float smoothedFaceCoverage(float bandPosition, float lanePosition) {
        float center = sampleFaceCoverageAt(bandPosition, lanePosition);
        float nearVertical = sampleFaceCoverageAt(bandPosition - 0.72F, lanePosition)
                + sampleFaceCoverageAt(bandPosition + 0.72F, lanePosition);
        float farVertical = sampleFaceCoverageAt(bandPosition - 1.52F, lanePosition)
                + sampleFaceCoverageAt(bandPosition + 1.52F, lanePosition);
        float nearHorizontal = sampleFaceCoverageAt(bandPosition, lanePosition - 0.54F)
                + sampleFaceCoverageAt(bandPosition, lanePosition + 0.54F);
        float nearDiagonal = sampleFaceCoverageAt(bandPosition - 0.82F, lanePosition - 0.62F)
                + sampleFaceCoverageAt(bandPosition - 0.82F, lanePosition + 0.62F)
                + sampleFaceCoverageAt(bandPosition + 0.82F, lanePosition - 0.62F)
                + sampleFaceCoverageAt(bandPosition + 0.82F, lanePosition + 0.62F);
        return Mth.clamp(center * 0.60F + nearVertical * 0.150F + farVertical * 0.070F + nearHorizontal * 0.045F + nearDiagonal * 0.022F, 0.0F, 1.0F);
    }

    private static float sampleFaceCoverageAt(float bandPosition, float lanePosition) {
        float clampedBand = Mth.clamp(bandPosition, 0.0F, GRID_ROWS - 1);
        float clampedLane = Mth.clamp(lanePosition, 0.0F, GRID_COLUMNS - 1);
        int band0 = Mth.clamp((int) Math.floor(clampedBand), 0, GRID_ROWS - 1);
        int band1 = Mth.clamp(band0 + 1, 0, GRID_ROWS - 1);
        int lane0 = Mth.clamp((int) Math.floor(clampedLane), 0, GRID_COLUMNS - 1);
        int lane1 = Mth.clamp(lane0 + 1, 0, GRID_COLUMNS - 1);
        return interpolatedCoverage(band0, band1, lane0, lane1, clampedBand - band0, clampedLane - lane0);
    }

    private static SinkingMedium dominantMedium(int band0, int band1, int lane0, int lane1, float bandBlend, float laneBlend) {
        float inverseBand = 1.0F - bandBlend;
        float inverseLane = 1.0F - laneBlend;
        SinkingMedium result = MEDIA[band0][lane0];
        float best = COVERAGE[band0][lane0] * inverseBand * inverseLane;

        float score = COVERAGE[band0][lane1] * inverseBand * laneBlend;
        if (score > best) {
            best = score;
            result = MEDIA[band0][lane1];
        }
        score = COVERAGE[band1][lane0] * bandBlend * inverseLane;
        if (score > best) {
            best = score;
            result = MEDIA[band1][lane0];
        }
        score = COVERAGE[band1][lane1] * bandBlend * laneBlend;
        if (score > best) {
            result = MEDIA[band1][lane1];
        }
        return result == null ? SinkingMedium.MUD : result;
    }

    private static int faceAlpha(float strength, float coverage, float translucentWeight) {
        float edgeAlpha = Mth.lerp(smoothStep(strength), 0.24F, 0.64F);
        float coverageAlpha = Mth.clamp(Math.max(coverage, strength * 0.72F) * 1.12F, 0.0F, 1.0F);
        float alphaScale = Mth.lerp(Mth.clamp(translucentWeight, 0.0F, 1.0F), 1.0F, 0.72F);
        return Mth.clamp(Math.round(edgeAlpha * coverageAlpha * alphaScale * 255.0F), 0, 180);
    }

    private static int submergedAlpha(float strength, float coverage, float translucentWeight, float fade) {
        float local = Mth.clamp(coverage * 1.30F, 0.0F, 1.0F);
        float edge = smoothStep(strength);
        float solidOpacity = Mth.clamp(0.46F + local * 0.50F, 0.0F, 0.98F);
        float translucentOpacity = Mth.clamp(0.24F + local * 0.34F, 0.0F, 0.58F);
        float opacity = Mth.lerp(Mth.clamp(translucentWeight, 0.0F, 1.0F), solidOpacity, translucentOpacity);
        return Mth.clamp(Math.round(edge * opacity * Mth.clamp(fade, 0.0F, 1.0F) * 255.0F), 0, 255);
    }

    private static float activeCoverageFade(float coverage) {
        return Mth.clamp((float) Math.pow(Mth.clamp(coverage, 0.0F, 1.0F), 1.15D), 0.0F, 1.0F);
    }

    private static float submergedBrightness(float translucentWeight, float full, float grain) {
        float solid = Mth.lerp(full, 0.68F, 0.20F);
        float translucent = Mth.clamp(Mth.lerp(full, 1.03F, 0.72F), 0.56F, 1.08F);
        return Mth.lerp(Mth.clamp(translucentWeight, 0.0F, 1.0F), solid, translucent) * grain;
    }

    private static float visionLayerStrength(float coverage, int x, int y, int salt) {
        if (coverage <= 0.0F) {
            return 0.0F;
        }

        float pixelNoise = noise(x / OVAL_PIXEL_STEP, y / OVAL_PIXEL_STEP, salt + 0x4F1BBCDC);
        float softened = smootherStep(Mth.clamp((coverage - 0.006F - pixelNoise * 0.014F) / 0.78F, 0.0F, 1.0F));
        float fringe = smoothStep(Mth.clamp((coverage - 0.006F - pixelNoise * 0.032F) / 0.56F, 0.0F, 1.0F));
        return Mth.clamp(Math.max(softened, fringe * 0.88F), 0.0F, 1.0F);
    }

    private static int blendedCoverTextureArgb(int x, int y, int salt, int alpha, SinkingMedium fallbackMedium, int band0, int band1, int lane0, int lane1,
            float bandBlend, float laneBlend) {
        return blendedGridCoverTextureArgb(x, y, salt, alpha, fallbackMedium,
                band0, band1, lane0, lane1, bandBlend, laneBlend,
                COVERAGE, MEDIA, VISUAL_SOURCE, true);
    }

    private static int blendedVisionRasterCoverTextureArgb(int x, int y, int salt, int alpha, SinkingMedium fallbackMedium, float bandPosition, float lanePosition) {
        int minBand = Mth.clamp((int) Math.floor(bandPosition - VISION_COLOR_BLEND_RADIUS), 0, LOCAL_VISION_ROWS - 1);
        int maxBand = Mth.clamp((int) Math.ceil(bandPosition + VISION_COLOR_BLEND_RADIUS), 0, LOCAL_VISION_ROWS - 1);
        int minLane = Mth.clamp((int) Math.floor(lanePosition - VISION_COLOR_BLEND_RADIUS), 0, LOCAL_VISION_COLUMNS - 1);
        int maxLane = Mth.clamp((int) Math.ceil(lanePosition + VISION_COLOR_BLEND_RADIUS), 0, LOCAL_VISION_COLUMNS - 1);
        ScreenMudSourceMixer mixer = SOURCE_MIXER;
        mixer.reset();
        for (int band = minBand; band <= maxBand; band++) {
            for (int lane = minLane; lane <= maxLane; lane++) {
                float coverage = DISPLAY_VISION_COVERAGE[band][lane];
                if (coverage <= MIN_VISIBLE_COVERAGE) {
                    continue;
                }

                float distanceToCell = Mth.sqrt(square(band - bandPosition) + square(lane - lanePosition));
                float weight = coverage * transitionKernel(distanceToCell, VISION_COLOR_BLEND_RADIUS);
                if (weight <= 1.0E-5F) {
                    continue;
                }
                mixer.add(DISPLAY_VISION_MEDIA[band][lane],
                        DISPLAY_VISION_VISUAL_SOURCE[band][lane], weight);
            }
        }
        if (mixer.size() == 0) {
            return fallbackMedium == SinkingMedium.TENDER_FLESH
                    ? sampledCoverageTextureArgb(fallbackMedium, 0L,
                            x, y, salt, alpha, true)
                    : sampledCoverTextureArgb(fallbackMedium, 0L,
                            x, y, salt, alpha);
        }
        return mixedSourceArgb(mixer, x, y, salt, alpha, false);
    }

    private static int blendedGridCoverTextureArgb(int x, int y, int salt, int alpha, SinkingMedium fallbackMedium, int band0, int band1, int lane0, int lane1,
            float bandBlend, float laneBlend, float[][] coverageGrid, SinkingMedium[][] mediaGrid,
            long[][] visualSourceGrid,
            boolean sampleSkinCoverage) {
        float bandPosition = band0 + bandBlend;
        float lanePosition = lane0 + laneBlend;
        ScreenMudSourceMixer mixer = SOURCE_MIXER;
        mixer.reset();
        for (int band = 0; band < coverageGrid.length; band++) {
            float bandWeight = verticalTransitionKernel(Math.abs(band - bandPosition));
            if (bandWeight <= 0.0F) {
                continue;
            }
            for (int lane = 0; lane < coverageGrid[band].length; lane++) {
                float weight = coverageGrid[band][lane] * bandWeight * horizontalTransitionKernel(Math.abs(lane - lanePosition));
                if (weight <= 1.0E-5F) {
                    continue;
                }

                mixer.add(mediaGrid[band][lane], visualSourceGrid[band][lane], weight);
            }
        }
        if (mixer.size() == 0) {
            return sampledCoverageTextureArgb(
                    fallbackMedium, 0L, x, y, salt, alpha, sampleSkinCoverage);
        }
        return mixedSourceArgb(mixer, x, y, salt, alpha, sampleSkinCoverage);
    }

    private static int mixedSourceArgb(ScreenMudSourceMixer mixer,
            int x, int y, int salt, int alpha, boolean skinCoverage) {
        float total = 0.0F;
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        for (int index = 0; index < mixer.size(); index++) {
            SinkingMedium medium = mixer.medium(index);
            long visualSource = mixer.visualSource(index);
            float weight = mixer.weight(index);
            int sourceSalt = salt
                    ^ Integer.rotateLeft(Long.hashCode(visualSource), 11)
                    ^ medium.id() * 0x45D9F3B;
            boolean useSkinCoverage = skinCoverage
                    || medium == SinkingMedium.TENDER_FLESH;
            int color = sampledCoverageTextureArgb(
                    medium, visualSource, x, y, sourceSalt, 255,
                    useSkinCoverage);
            total += weight;
            red += (color >>> 16 & 255) * weight;
            green += (color >>> 8 & 255) * weight;
            blue += (color & 255) * weight;
        }
        float inverseTotal = 1.0F / Math.max(total, 1.0E-5F);
        return alpha << 24
                | Mth.clamp(Math.round(red * inverseTotal), 0, 255) << 16
                | Mth.clamp(Math.round(green * inverseTotal), 0, 255) << 8
                | Mth.clamp(Math.round(blue * inverseTotal), 0, 255);
    }

    private static float translucentMediumWeight(int band0, int lane0, float bandBlend, float laneBlend, float[][] coverageGrid, SinkingMedium[][] mediaGrid) {
        float bandPosition = band0 + bandBlend;
        float lanePosition = lane0 + laneBlend;
        float total = 0.0F;
        float translucent = 0.0F;
        for (int band = 0; band < coverageGrid.length; band++) {
            float bandWeight = verticalTransitionKernel(Math.abs(band - bandPosition));
            if (bandWeight <= 0.0F) {
                continue;
            }
            for (int lane = 0; lane < coverageGrid[band].length; lane++) {
                float weight = coverageGrid[band][lane] * bandWeight * horizontalTransitionKernel(Math.abs(lane - lanePosition));
                if (weight <= 1.0E-5F) {
                    continue;
                }

                total += weight;
                translucent += weight * translucentMediumFactor(mediaGrid[band][lane]);
            }
        }
        return total <= 1.0E-5F ? translucentMediumFactor(null) : Mth.clamp(translucent / total, 0.0F, 1.0F);
    }

    private static float translucentMediumFactor(SinkingMedium medium) {
        return medium == SinkingMedium.LIVING_SLIME ? 1.0F : 0.0F;
    }

    private static float verticalTransitionKernel(float distance) {
        return transitionKernel(distance, 1.32F);
    }

    private static float horizontalTransitionKernel(float distance) {
        return transitionKernel(distance, 1.80F);
    }

    private static float transitionKernel(float distance, float radius) {
        if (distance >= radius) {
            return 0.0F;
        }
        return 1.0F - smootherStep(distance / radius);
    }

    private static int sampledCoverTextureArgb(SinkingMedium medium, int x, int y, int salt, int alpha) {
        return sampledCoverTextureArgb(medium, 0L, x, y, salt, alpha);
    }

    private static int sampledCoverTextureArgb(SinkingMedium medium,
            long visualSource, int x, int y, int salt, int alpha) {
        return sampledCoverageTextureArgb(
                medium, visualSource, x, y, salt, alpha, false);
    }

    private static int sampledCoverageTextureArgb(SinkingMedium medium, int x, int y, int salt,
            int alpha, boolean skinCoverage) {
        return sampledCoverageTextureArgb(
                medium, 0L, x, y, salt, alpha, skinCoverage);
    }

    private static int sampledCoverageTextureArgb(SinkingMedium medium,
            long visualSource, int x, int y, int salt,
            int alpha, boolean skinCoverage) {
        SinkingMedium safeMedium = medium == null ? SinkingMedium.MUD : medium;
        int hash = hash(x >> 2, y >> 2, salt + safeMedium.id() * 4099);
        int sampleX = x + ((hash & 15) - 8) * 5 + ((hash >>> 8) & 31) * 11;
        int sampleY = y + (((hash >>> 4) & 15) - 8) * 5 + ((hash >>> 13) & 31) * 7;
        return skinCoverage
                ? MudSkinTextureCache.skinCoverageTextureArgb(
                        safeMedium, visualSource,
                        sampleX, sampleY, salt ^ hash, alpha)
                : MudSkinTextureCache.coverTextureArgb(
                        safeMedium, visualSource,
                        sampleX, sampleY, salt ^ hash, alpha);
    }

    private static float ovalRingStrength(int x, int y) {
        int steppedX = x / OVAL_PIXEL_STEP * OVAL_PIXEL_STEP + OVAL_PIXEL_STEP / 2;
        int steppedY = y / OVAL_PIXEL_STEP * OVAL_PIXEL_STEP + OVAL_PIXEL_STEP / 2;
        float nx = ((steppedX + 0.5F) / TEXTURE_WIDTH) * 2.0F - 1.0F;
        float ny = ((steppedY + 0.5F) / TEXTURE_HEIGHT) * 2.0F - 1.0F;
        float oval = square(nx / CLEAR_RADIUS_X) + square(ny / CLEAR_RADIUS_Y);
        float coarseNoise = noise(
                steppedX / (OVAL_PIXEL_STEP * 3),
                steppedY / (OVAL_PIXEL_STEP * 3),
                STABLE_TEXTURE_SALT + 0x1D73A5B9);
        float fineNoise = noise(
                steppedX / OVAL_PIXEL_STEP,
                steppedY / OVAL_PIXEL_STEP,
                STABLE_TEXTURE_SALT + 0x5C8E29F1);
        float edgeNoise = coarseNoise * 0.76F + fineNoise * 0.24F;
        float inwardExtension = Mth.lerp(edgeNoise, OVAL_INWARD_EXTENSION_MIN, OVAL_INWARD_EXTENSION_MAX);
        float innerRadius = 1.0F - inwardExtension;
        float ovalRadius = Mth.sqrt(oval);
        if (ovalRadius <= innerRadius) {
            return 0.0F;
        }

        float boundaryScale = innerRadius / Math.max(ovalRadius, 1.0E-5F);
        float edgeScale = edgeScale(nx, ny);
        if (edgeScale <= boundaryScale) {
            return 1.0F;
        }
        return Mth.clamp((1.0F - boundaryScale) / (edgeScale - boundaryScale), 0.0F, 1.0F);
    }

    private static float edgeScale(float nx, float ny) {
        float xScale = Math.abs(nx) < 1.0E-5F ? Float.POSITIVE_INFINITY : 1.0F / Math.abs(nx);
        float yScale = Math.abs(ny) < 1.0E-5F ? Float.POSITIVE_INFINITY : 1.0F / Math.abs(ny);
        return Math.min(xScale, yScale);
    }

    private static float square(float value) {
        return value * value;
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float smootherStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * clamped * (clamped * (clamped * 6.0F - 15.0F) + 10.0F);
    }

    private static int darkenArgb(int argb, float brightness) {
        int alpha = argb >>> 24 & 255;
        int red = Math.round((argb >>> 16 & 255) * brightness);
        int green = Math.round((argb >>> 8 & 255) * brightness);
        int blue = Math.round((argb & 255) * brightness);
        return FastColor.ARGB32.color(alpha, Mth.clamp(red, 0, 255), Mth.clamp(green, 0, 255), Mth.clamp(blue, 0, 255));
    }

    private static int argbToAbgr(int argb) {
        int alpha = argb >>> 24 & 255;
        int red = argb >>> 16 & 255;
        int green = argb >>> 8 & 255;
        int blue = argb & 255;
        return FastColor.ABGR32.color(alpha, blue, green, red);
    }

    private static float noise(int x, int y, int salt) {
        return (hash(x, y, salt) & 1023) / 1023.0F;
    }

    private static int hash(int x, int y, int salt) {
        int value = x * 73428767 ^ y * 9122719 ^ salt * 42317861;
        value ^= value >>> 13;
        value *= 1274126177;
        value ^= value >>> 16;
        return value;
    }

    private record MudSample(float strength, SinkingMedium medium,
            long visualSource) {
        private static final MudSample NONE = new MudSample(
                0.0F, SinkingMedium.MUD, 0L);
    }

    private record VisionScreenSample(float coverage, SinkingMedium medium,
            long visualSource, float translucentWeight, float opacityCoverage) {
    }

    private record MudContact(BlockState state, SinkingMedium medium, double surfaceY, double depth, double depthFactor, double availableDepth) {
    }

}
