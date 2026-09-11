package com.fish.mirebound.client.skin;

import com.fish.mirebound.client.MudSkinTextureCache;
import com.fish.mirebound.client.coverage.SurfaceMaterial;
import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.client.gui.MireflowEditBox;
import com.fish.mirebound.client.gui.MireflowGuiTheme;
import com.fish.mirebound.coverage.skin.SkinStainMask;
import java.io.IOException;
import java.util.EnumMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;

/** UV/model workspace. Model geometry, layout and outliner state have separate owners. */
public final class SkinStainEditorScreen extends Screen {
    private enum Tool { BRUSH, SELECT, ROTATE }
    private enum Gesture { NONE, MODEL_BRUSH, UV_BRUSH, MODEL_SELECT, UV_SELECT, ROTATE, VIEW_ROTATE, MODEL_PAN, UV_PAN, LEFT_DIVIDER, RIGHT_DIVIDER }
    private final Screen parent;
    private final SkinEditorVisibility visibility = new SkinEditorVisibility();
    private final SkinEditorPose pose = new SkinEditorPose();
    private final EnumMap<Tool, SkinEditorIconButton> tools = new EnumMap<>(Tool.class);
    private SkinMaskEdit edit;
    private SkinStainMask savedMask;
    private SurfaceMaterial material;
    private ResourceLocation skinTexture;
    private SkinEditorTextures textures;
    private boolean slim, legacy, deny = true, showGrid = true, modelAlpha, uvAlpha;
    private List<SkinEditorMesh.Face> mesh = List.of();
    private List<SkinEditorProjection.Quad> projected = List.of();
    private SkinEditorLayout layout;
    private SkinEditorPartsPanel parts;
    private SkinEditorSelection selection;
    private Tool tool = Tool.BRUSH;
    private SkinEditorPose.Axis poseAxis = SkinEditorPose.Axis.Z;
    private String selectedPart = "head";
    private Gesture gesture = Gesture.NONE;
    private int gestureButton, brush = 1, refreshTicks, lastX = -1, lastY, brushDragStartX, brushDragStartValue;
    private boolean brushDragging;
    private SkinEditorMesh.Face lastFace;
    private double selectionX, selectionY, selectionEndX, selectionEndY;
    private double yaw = SkinEditorView.DEFAULT_YAW, pitch = SkinEditorView.DEFAULT_PITCH, modelZoom = 1, modelPanX, modelPanY;
    private double uvZoom = 1, uvPanX, uvPanY, leftRatio = .24, rightRatio = .80;
    private long projectedVisibility = -1;
    private boolean projectionDirty = true;
    private Button undo, redo, reset, cancel, apply, denyButton, allowButton, viewButton, gridButton, modelAlphaButton, uvAlphaButton, brushMinus, brushPlus;
    private Button poseDefault, poseArms, poseWalk, poseCrouch;
    private EditBox brushInput;
    private String message;
    private int applyCooldownTicks;
    private final Deque<ApplyToast> applyToasts = new ArrayDeque<>();
    private final Deque<SkinEditorPose> poseUndo = new ArrayDeque<>(), poseRedo = new ArrayDeque<>();
    private SkinEditorPose poseBeforeGesture;
    private SkinEditorPose.Axis poseHoverAxis;
    private long gestureEditRevision;
    private EditKind lastEdit = EditKind.NONE;
    private enum EditKind { NONE, MASK, POSE }

    public SkinStainEditorScreen(Screen parent) { super(text("title")); this.parent = parent; }
    static Component text(String key, Object... args) { return Component.translatable("gui.mirebound.skin." + key, args); }

    @Override protected void init() {
        finishGesture();
        layout = SkinEditorLayout.fit(width, height, leftRatio, rightRatio);
        refreshSkin();
        createWidgets();
        positionWidgets();
        if (message == null) message = ClientSkinStainRules.loadError();
    }

    private void refreshSkin() {
        PlayerSkin skin = minecraft.player == null
                ? minecraft.getSkinManager().getInsecureSkin(minecraft.getGameProfile()) : minecraft.player.getSkin();
        ResourceLocation texture = MudSkinTextureCache.originalSkinTexture(skin.texture());
        SurfaceMaterial pixels = MudSkinTextureCache.currentMaterial(texture);
        int w = pixels == null ? 64 : pixels.width(), h = pixels == null ? 64 : pixels.height();
        if (w > SkinStainMask.MAX_DIMENSION || h > SkinStainMask.MAX_DIMENSION) {
            message = "gui.mirebound.skin.too_large";
            return;
        }
        boolean nextSlim = skin.model() == PlayerSkin.Model.SLIM, nextLegacy = w == h * 2;
        boolean resized = edit != null && (edit.width() != w || edit.height() != h);
        if (edit == null) {
            savedMask = ClientSkinStainRules.ownMask().resized(w, h);
            edit = new SkinMaskEdit(savedMask);
        } else if (resized) {
            finishGesture();
            edit = new SkinMaskEdit(edit.snapshot().resized(w, h));
            savedMask = savedMask.resized(w, h);
        }
        if (resized || !texture.equals(skinTexture) || slim != nextSlim || legacy != nextLegacy || textures == null) {
            closeTextures();
            skinTexture = texture; slim = nextSlim; legacy = nextLegacy; material = pixels;
            mesh = SkinEditorMesh.create(slim, legacy);
            textures = new SkinEditorTextures(w, h);
            projectionDirty = true;
            if (parts != null) parts.refresh();
        } else if (material == null ? pixels != null : pixels == null || material.pixelSource() != pixels.pixelSource()) {
            material = pixels;
            textures.invalidate();
        }
        updateActions();
    }

    @Override public void tick() {
        if (selection != null && selection.step(edit, 8192)) {
            selection = null; edit.end(); updateActions();
        }
        if (++refreshTicks >= 20 && selection == null && gesture == Gesture.NONE) {
            refreshTicks = 0;
            refreshSkin();
        }
        if (applyCooldownTicks > 0) applyCooldownTicks--;
        applyToasts.removeIf(toast -> ++toast.age >= ApplyToast.LIFETIME);
    }

    private void createWidgets() {
        clearWidgets(); tools.clear();
        SkinEditorIconButton.prepareTexture();
        undo = addIcon(SkinEditorIconButton.Icon.UNDO, "undo", () -> { finishGesture(); edit.undo(); updateActions(); });
        redo = addIcon(SkinEditorIconButton.Icon.REDO, "redo", () -> { finishGesture(); edit.redo(); updateActions(); });
        reset = addRenderableWidget(MireflowButton.builder(text("reset"), b -> resetAll())
                .tooltip(Tooltip.create(text("reset_hint"))).build());
        cancel = addRenderableWidget(MireflowButton.builder(text("cancel"), b -> onClose()).build());
        apply = addRenderableWidget(MireflowButton.builder(text("apply"), b -> {
            save();
            b.setFocused(false);
        }).build());
        for (Tool candidate : Tool.values()) {
            String key = "tool." + candidate.name().toLowerCase(java.util.Locale.ROOT);
            var button = new SkinEditorIconButton(SkinEditorIconButton.Icon.valueOf(candidate.name()), text(key),
                    () -> tool == candidate, () -> { finishGesture(); tool = candidate; updateActions(); });
            tools.put(candidate, addRenderableWidget(button));
        }
        viewButton = addIcon(SkinEditorIconButton.Icon.VIEW, "reset_view", this::resetView);
        gridButton = addRenderableWidget(new SkinEditorIconButton(SkinEditorIconButton.Icon.GRID,
                text("grid"), () -> showGrid, () -> showGrid = !showGrid));
        modelAlphaButton = addRenderableWidget(new SkinEditorIconButton(SkinEditorIconButton.Icon.ALPHA,
                text("alpha_model"), () -> modelAlpha, () -> modelAlpha = !modelAlpha));
        uvAlphaButton = addRenderableWidget(new SkinEditorIconButton(SkinEditorIconButton.Icon.ALPHA,
                text("alpha_uv"), () -> uvAlpha, () -> uvAlpha = !uvAlpha));
        brushMinus = addIcon(SkinEditorIconButton.Icon.LESS, "brush_label", () -> setBrush(brush - 1));
        brushPlus = addIcon(SkinEditorIconButton.Icon.MORE, "brush_label", () -> setBrush(brush + 1));
        poseDefault = poseButton("pose.default", "default");
        poseArms = poseButton("pose.arms", "arms");
        poseWalk = poseButton("pose.walk", "walk");
        poseCrouch = poseButton("pose.crouch", "crouch");
        denyButton = addRenderableWidget(MireflowButton.builder(text("mark"), b -> setDeny(true))
                .tone(MireflowButton.Tone.DANGER).selected(deny).build());
        allowButton = addRenderableWidget(MireflowButton.builder(text("unmark"), b -> setDeny(false))
                .tone(MireflowButton.Tone.POSITIVE).selected(!deny).build());
        brushInput = new MireflowEditBox(font, 0, 0, 60, 20, text("brush_label"));
        brushInput.setMaxLength(2);
        brushInput.setFilter(value -> value.chars().allMatch(c -> c >= '0' && c <= '9'));
        brushInput.setValue(Integer.toString(brush));
        brushInput.setResponder(value -> {
            int parsed = value.isEmpty() ? 0 : Integer.parseInt(value);
            boolean valid = parsed >= 1 && parsed <= 16;
            if (valid) brush = parsed;
            brushInput.setTextColor(valid || value.isEmpty() ? MireflowGuiTheme.TEXT : MireflowGuiTheme.ERROR);
        });
        brushInput.setTooltip(Tooltip.create(text("brush_hint")));
        addRenderableWidget(brushInput);
        if (parts == null) parts = new SkinEditorPartsPanel(visibility, () -> legacy,
                part -> { selectedPart = part; updateActions(); }, () -> selectedPart);
        addRenderableWidget(parts);
        updateActions();
    }

    private Button addIcon(SkinEditorIconButton.Icon icon, String key, Runnable action) {
        return addRenderableWidget(new SkinEditorIconButton(icon, text(key), () -> false, action));
    }

    private void positionWidgets() {
        int center = (width - 116) / 2;
        bounds(undo, center, 4, 24, 22); bounds(redo, center + 28, 4, 24, 22);
        bounds(reset, center + 58, 4, 58, 22);
        int actionWidth = Math.min(72, Math.max(36, (width - 156) / 4));
        bounds(cancel, width - 12 - actionWidth * 2, 4, actionWidth, 22);
        bounds(apply, width - 8 - actionWidth, 4, actionWidth, 22);
        int available = layout.modelRight() - layout.modelLeft() - 8;
        int size = Math.min(22, Math.max(18, (available - 20) / 6));
        int leftTools = layout.modelLeft() + 4;
        bounds(tools.get(Tool.BRUSH), leftTools, SkinEditorLayout.TOOL_TOP, size, 22);
        bounds(tools.get(Tool.SELECT), leftTools + size + 4, SkinEditorLayout.TOOL_TOP, size, 22);
        bounds(tools.get(Tool.ROTATE), leftTools + 2 * (size + 4), SkinEditorLayout.TOOL_TOP, size, 22);
        int rightTools = layout.modelRight() - 4 - 3 * size - 2 * 4;
        bounds(viewButton, rightTools, SkinEditorLayout.TOOL_TOP, size, 22);
        bounds(gridButton, rightTools + size + 4, SkinEditorLayout.TOOL_TOP, size, 22);
        bounds(modelAlphaButton, rightTools + 2 * (size + 4), SkinEditorLayout.TOOL_TOP, size, 22);
        int sidebarX = layout.right() + 8, sidebarWidth = width - sidebarX - 8;
        int half = (sidebarWidth - 4) / 2;
        bounds(denyButton, sidebarX, 39, half, 22);
        bounds(allowButton, sidebarX + half + 4, 39, sidebarWidth - half - 4, 22);
        int fieldWidth = Math.min(76, Math.max(34, sidebarWidth / 2));
        brushInput.setX(width - 50 - fieldWidth); brushInput.setY(68); brushInput.setWidth(fieldWidth);
        brushMinus.setX(brushInput.getX()-22); brushMinus.setY(68); brushMinus.setWidth(20); brushMinus.setHeight(20);
        brushPlus.setX(brushInput.getX()+fieldWidth+3); brushPlus.setY(68); brushPlus.setWidth(20); brushPlus.setHeight(20);
        bounds(uvAlphaButton,layout.left()-31,SkinEditorLayout.TOOL_TOP,22,22);
        int poseY=110, poseWidth=Math.max(1,(sidebarWidth-12)/4);
        bounds(poseDefault,sidebarX,poseY,poseWidth,20);bounds(poseArms,sidebarX+poseWidth+4,poseY,poseWidth,20);
        bounds(poseWalk,sidebarX+2*(poseWidth+4),poseY,poseWidth,20);bounds(poseCrouch,sidebarX+3*(poseWidth+4),poseY,sidebarWidth-3*(poseWidth+4),20);
        parts.setX(sidebarX); parts.setY(152); parts.setWidth(sidebarWidth);
        parts.setHeight(Math.max(20, layout.bottom() - 152));
        projectionDirty = true;
    }

    private static void bounds(Button button, int x, int y, int w, int h) {
        button.setX(x); button.setY(y); button.setWidth(w); button.setHeight(h);
    }

    private void setDeny(boolean value) {
        finishGesture(); deny = value;
        createWidgets(); positionWidgets();
    }

    private void updateActions() {
        if (undo == null) return;
        boolean ready = edit != null && textures != null && selection == null && gesture == Gesture.NONE;
        undo.active = ready && edit.canUndo(); redo.active = ready && edit.canRedo(); reset.active = ready;
        apply.active = ready;
        denyButton.active = allowButton.active = ready;
        brushInput.active = ready;
        brushInput.setEditable(brushInput.active);
        brushMinus.active = brushPlus.active = brushInput.active;
        parts.active = ready;
        for (var button : tools.values()) button.active = ready;
        viewButton.active = ready;
        gridButton.active = ready;
        modelAlphaButton.active = ready;
        uvAlphaButton.active = ready;
        poseDefault.active = poseArms.active = poseWalk.active = poseCrouch.active = ready;
    }

    private void resetAll() {
        finishGesture();
        edit.replace(SkinStainMask.empty(edit.width(), edit.height()));
        visibility.reset(); parts.refresh(); resetView();
        brush = 1; tool = Tool.BRUSH; deny = true; showGrid = true; modelAlpha = uvAlpha = false; pose.reset();
        poseUndo.clear(); poseRedo.clear(); lastEdit=EditKind.NONE; message = null;
        createWidgets(); positionWidgets();
    }

    private void commitBrush() {
        if (brushInput == null) return;
        String value = brushInput.getValue();
        if (!value.isEmpty()) {
            try { brush = Mth.clamp(Integer.parseInt(value), 1, 16); }
            catch(NumberFormatException ignored) { }
        }
        brushInput.setValue(Integer.toString(brush));
        brushInput.setFocused(false);
    }

    private void setBrush(int value) {
        brush=Mth.clamp(value,1,16);
        if(brushInput!=null)brushInput.setValue(Integer.toString(brush));
    }

    private Button poseButton(String key,String preset) {
        return addRenderableWidget(MireflowButton.builder(text(key),b->{
            finishGesture(); SkinEditorPose before=pose.copy(); pose.preset(preset);
            recordPoseEdit(before); projectionDirty=true;
            b.setFocused(false);
            if (b instanceof MireflowButton button) button.suppressHoverUntilLeave();
        }).build());
    }

    private void resetView() {
        yaw = SkinEditorView.DEFAULT_YAW; pitch = SkinEditorView.DEFAULT_PITCH; modelZoom = 1;
        modelPanX = modelPanY = 0;
        projectionDirty = true;
    }

    private void save() {
        if (applyCooldownTicks > 0) return;
        finishGesture();
        if (edit == null) return;
        try {
            ClientSkinStainRules.save(edit.snapshot());
            savedMask = edit.snapshot();
            applyCooldownTicks = 12;
            applyToasts.addLast(new ApplyToast());
            while (applyToasts.size() > 4) applyToasts.removeFirst();
            updateActions();
        }
        catch (IOException | IllegalArgumentException error) {
            message = error instanceof IOException ? "gui.mirebound.skin.io_error" : "gui.mirebound.skin.too_complex";
        }
    }

    private void recordPoseEdit(SkinEditorPose before) {
        if (pose.sameAs(before)) return;
        poseUndo.addLast(before);
        while (poseUndo.size()>32) poseUndo.removeFirst();
        poseRedo.clear(); lastEdit=EditKind.POSE;
    }

    private void beginPoseGesture() { poseBeforeGesture=pose.copy(); }

    private void commitPoseGesture() {
        if (poseBeforeGesture!=null) recordPoseEdit(poseBeforeGesture);
        poseBeforeGesture=null;
    }

    private double uvScale() {
        return Math.max(.01, Math.min((layout.left() - 20D) / edit.width(),
                (layout.bottom() - SkinEditorLayout.UV_TOP - 8D) / edit.height()) * uvZoom);
    }
    private double uvLeft() { return layout.left() * .5 - edit.width() * uvScale() * .5 + uvPanX; }
    private double uvTop() { return (SkinEditorLayout.UV_TOP + layout.bottom()) * .5 - edit.height() * uvScale() * .5 + uvPanY; }

    private void ensureProjection() {
        if (!projectionDirty && projectedVisibility == visibility.revision()) return;
        projected = SkinEditorProjection.project(mesh, visibility::visible, yaw, pitch, modelScale(), modelCenterX(), modelCenterY(), pose);
        projectedVisibility = visibility.revision(); projectionDirty = false;
    }

    private double modelScale() {
        return Math.max(.01, Math.min((layout.modelRight() - layout.modelLeft() - 20D) / 22D,
                (layout.bottom() - SkinEditorLayout.MODEL_TOP - 12D) / 34D)) * modelZoom;
    }
    private double modelCenterX() {return (layout.modelLeft()+layout.modelRight())*.5+modelPanX;}
    private double modelCenterY() {return (SkinEditorLayout.MODEL_TOP+layout.bottom())*.5+modelPanY;}

    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF0D0E11);
        g.fill(0, 0, width, SkinEditorLayout.TOP, 0xFF17181C);
        g.fill(0, SkinEditorLayout.TOP - 1, width, SkinEditorLayout.TOP, MireflowGuiTheme.ACCENT);
        int titleWidth = Math.max(0, (width - 116) / 2 - 18);
        if (titleWidth > 20) g.drawString(font, font.plainSubstrByWidth(title.getString(), titleWidth), 8, 11, MireflowGuiTheme.TEXT, false);
        g.fill(layout.right(), SkinEditorLayout.TOP, width, layout.bottom(), 0xFF18191D);
        g.fill(5, SkinEditorLayout.TOP, layout.left() - 5, SkinEditorLayout.UV_TOP - 3, 0xFF18191D);
        g.fill(layout.modelLeft(), SkinEditorLayout.TOP, layout.modelRight(), SkinEditorLayout.MODEL_TOP - 3, 0xFF18191D);
        g.fill(5, SkinEditorLayout.UV_TOP - 3, layout.left() - 5, SkinEditorLayout.UV_TOP - 2, MireflowGuiTheme.DIVIDER);
        g.fill(layout.modelLeft(), SkinEditorLayout.MODEL_TOP - 3, layout.modelRight(), SkinEditorLayout.MODEL_TOP - 2, MireflowGuiTheme.DIVIDER);
        g.drawString(font, font.plainSubstrByWidth(text("brush_label").getString(),
                Math.max(1, brushInput.getX() - layout.right() - 14)), layout.right() + 8, 74, MireflowGuiTheme.TEXT, false);
        g.drawString(font, "px", width - 22, 74, MireflowGuiTheme.MUTED, false);
        g.drawString(font, text("poses"), layout.right() + 8, 94, MireflowGuiTheme.ACCENT, false);
        g.fill(layout.right() + 8, 105, width - 8, 106, MireflowGuiTheme.DIVIDER);
        g.drawString(font, text("parts"), layout.right() + 8, 136, MireflowGuiTheme.ACCENT, false);
        g.fill(layout.right() + 8, 147, width - 8, 148, MireflowGuiTheme.DIVIDER);
        if (edit != null && textures != null) {
            textures.update(material, edit);
            renderUv(g); renderModel(g, mx, my);
            if (selection == null) renderHover(g, mx, my);
            renderRectangle(g);
            SkinEditorView.gizmo(g,layout,yaw,pitch,mx,my);
            Component status = message != null ? Component.translatable(message)
                    : selection != null ? text("selecting", selection.percent()) : text("status", edit.width(), edit.height());
            g.drawString(font, font.plainSubstrByWidth(status.getString(), width - 16), 8, height - 15,
                    message == null ? MireflowGuiTheme.MUTED : MireflowGuiTheme.ERROR, false);
        } else if (message != null) g.drawString(font, Component.translatable(message), 8, height - 15, MireflowGuiTheme.ERROR, false);
        renderDividers(g, mx, my);
        super.render(g, mx, my, pt);
        renderApplyToast(g);
    }

    private void renderApplyToast(GuiGraphics g) {
        if (applyToasts.isEmpty()) return;
        // Flush model and widget batches, then draw in a positive GUI Z layer so
        // the toast cannot be hidden by model depth or a later widget batch.
        g.flush();
        RenderSystem.disableDepthTest();
        g.pose().pushPose();
        g.pose().translate(0.0D, 0.0D, 1000.0D);
        int slot = 0;
        var iterator = applyToasts.descendingIterator();
        while (iterator.hasNext()) {
            ApplyToast toast = iterator.next();
            double in = Math.min(1D, toast.age / 8D);
            double out = Math.min(1D, Math.max(0D, (ApplyToast.LIFETIME - toast.age) / 18D));
            double smooth = in * in * (3D - 2D * in);
            int alpha = (int)(255D * Math.min(in, out));
            int y = (int)(height - 46 - slot * 28 + (1D - smooth) * 20D);
            int left = width / 2 - 72, right = width / 2 + 72;
            g.fill(left, y, right, y + 24, alpha << 24 | 0x202126);
            g.renderOutline(left, y, right - left, 24, alpha << 24 | MireflowGuiTheme.ACCENT);
            g.drawCenteredString(font, text("apply_success"), width / 2, y + 8,
                    alpha << 24 | MireflowGuiTheme.TEXT);
            slot++;
        }
        g.flush();
        g.pose().popPose();
    }

    private void renderDividers(GuiGraphics g, int mx, int my) {
        int middle = (SkinEditorLayout.TOP + layout.bottom()) / 2;
        for (int i = 1; i <= 2; i++) {
            int x = i == 1 ? layout.left() : layout.right();
            boolean dragging = gesture == (i == 1 ? Gesture.LEFT_DIVIDER : Gesture.RIGHT_DIVIDER);
            int color = dragging || layout.dividerAt(mx, my) == i ? MireflowGuiTheme.ACCENT : MireflowGuiTheme.DIVIDER;
            g.fill(x - 1, SkinEditorLayout.TOP, x + 1, layout.bottom(), color);
            g.fill(x - 3, middle - 13, x + 3, middle + 13, 0xFF1D1E22);
            for (int dy = -6; dy <= 6; dy += 6) g.fill(x - 1, middle + dy, x + 1, middle + dy + 2, color);
        }
    }

    private void clipUv(GuiGraphics g) { g.enableScissor(5, SkinEditorLayout.UV_TOP, layout.left() - 5, layout.bottom()); }
    private void clipModel(GuiGraphics g) { g.enableScissor(layout.modelLeft(), SkinEditorLayout.MODEL_TOP, layout.modelRight(), layout.bottom()); }

    private void renderUv(GuiGraphics g) {
        int right = layout.left() - 5, bottom = layout.bottom();
        SkinEditorDraw.checker(g, 5, SkinEditorLayout.UV_TOP, right, bottom);
        clipUv(g);
        double scale = uvScale(), x = uvLeft(), y = uvTop();
        int uvColor = (uvAlpha ? 0x88 : 0xFF) << 24 | 0x00FFFFFF;
        SkinEditorDraw.texture(g, textures.uvType, x, y, edit.width() * scale, edit.height() * scale, uvColor);
        SkinEditorDraw.texture(g, textures.markerType, x, y, edit.width() * scale, edit.height() * scale, 0xFFFFFFFF); g.flush();
        if (scale >= 4) {
            int x0 = Math.max(0, (int) ((5 - x) / scale)), x1 = Math.min(edit.width(), (int) ((right - x) / scale) + 1);
            int y0 = Math.max(0, (int) ((SkinEditorLayout.UV_TOP - y) / scale)), y1 = Math.min(edit.height(), (int) ((bottom - y) / scale) + 1);
            for (int i = x0; i <= x1; i++) {
                int px = (int) (x + i * scale);
                g.fill(px, Math.max(SkinEditorLayout.UV_TOP, (int) y), px + 1, Math.min(bottom, (int) (y + edit.height() * scale)), 0x553A3A3F);
            }
            for (int i = y0; i <= y1; i++) {
                int py = (int) (y + i * scale);
                g.fill(Math.max(5, (int) x), py, Math.min(right, (int) (x + edit.width() * scale)), py + 1, 0x553A3A3F);
            }
        }
        g.disableScissor();
    }

    private void renderModel(GuiGraphics g,int mouseX,int mouseY) {
        int left = layout.modelLeft(), right = layout.modelRight(), top = SkinEditorLayout.MODEL_TOP, bottom = layout.bottom();
        g.fill(left, top, right, bottom, 0xFF141519);
        for (int y = top; y < bottom; y += 16) for (int x = left; x < right; x += 16)
            if (((x - left) / 16 + (y - top) / 16) % 2 == 0)
                g.fill(x, y, Math.min(right, x + 16), Math.min(bottom, y + 16), 0xFF18191D);
        clipModel(g); ensureProjection();
        SkinEditorDraw.beginModel(g);
        if(showGrid)SkinEditorView.grid(g,SkinEditorView.frame(yaw,pitch),modelScale(),modelCenterX(),modelCenterY());
        for (var quad : projected) {
            int light = (int) (quad.light() * 255);
            int alpha = modelAlpha ? 0x88 : 0xFF;
            SkinEditorDraw.modelQuad(g, textures.modelType, quad.points(), alpha << 24 | light << 16 | light << 8 | light);
            SkinEditorDraw.modelQuad(g, textures.modelMarkerType, quad.points(), 0xFFFFFFFF);
        }
        SkinEditorDraw.endModel(g);
        if(tool==Tool.ROTATE)renderPoseGizmo(g,mouseX,mouseY);
        g.flush(); g.disableScissor();
    }

    private double[] selectedPartAxis() {
        double[] pivot=SkinEditorPose.pivot(selectedPart);
        double[] transformed=pose.transform(selectedPart,pivot[0],pivot[1],pivot[2]);
        var point=SkinEditorView.frame(yaw,pitch).project(transformed[0],transformed[1]-8,transformed[2]);
        return new double[]{modelCenterX()+point.x()*modelScale(),modelCenterY()+point.y()*modelScale()};
    }

    private void renderPoseGizmo(GuiGraphics g,int mouseX,int mouseY) {
        double[] center=selectedPartAxis();
        poseHoverAxis=gesture==Gesture.ROTATE?poseAxis:SkinEditorView.poseAxisAt(center[0],center[1],mouseX,mouseY,
                poseRadius(),yaw,pitch,pose,selectedPart);
        SkinEditorPose.Axis active=gesture==Gesture.ROTATE?poseAxis:poseHoverAxis;
        SkinEditorView.poseGizmo(g,center[0],center[1],poseRadius(),active,yaw,pitch,pose,selectedPart);
    }

    private double poseRadius() { return Math.min(48,Math.max(28,modelScale()*2.5)); }

    private void renderHover(GuiGraphics g, int mx, int my) {
        if(SkinEditorView.inGizmo(layout,mx,my))return;
        if (gesture != Gesture.NONE || tool == Tool.ROTATE) return;
        SkinEditorProjection.Hit hit = null;
        int[] pixel;
        if (layout.inModel(mx, my)) { hit = modelHit(mx, my); pixel = hit == null ? null : new int[]{hit.x(edit.width()), hit.y(edit.height())}; }
        else if (layout.inUv(mx, my)) pixel = uvPixel(mx, my);
        else return;
        if (pixel == null) return;
        float u0 = pixel[0] / (float) edit.width(), v0 = pixel[1] / (float) edit.height();
        float u1 = (pixel[0] + 1F) / edit.width(), v1 = (pixel[1] + 1F) / edit.height();
        clipModel(g);
        for (var quad : projected) {
            if (hit != null && hit.quad() != quad) continue;
            float[] bounds = uvBounds(quad.face());
            if ((u0 + u1) * .5F < bounds[0] || (u0 + u1) * .5F >= bounds[2]
                    || (v0 + v1) * .5F < bounds[1] || (v0 + v1) * .5F >= bounds[3]) continue;
            var center = SkinEditorProjection.uvPoint(quad, (u0 + u1) * .5F, (v0 + v1) * .5F);
            if (center == null || !SkinEditorProjection.exposed(projected, quad, center)) continue;
            var a = SkinEditorProjection.uvPoint(quad, u0, v0); var b = SkinEditorProjection.uvPoint(quad, u1, v0);
            var c = SkinEditorProjection.uvPoint(quad, u1, v1); var d = SkinEditorProjection.uvPoint(quad, u0, v1);
            if (a == null || b == null || c == null || d == null) continue;
            SkinEditorDraw.line(g, a.x(), a.y(), b.x(), b.y(), -1); SkinEditorDraw.line(g, b.x(), b.y(), c.x(), c.y(), -1);
            SkinEditorDraw.line(g, c.x(), c.y(), d.x(), d.y(), -1); SkinEditorDraw.line(g, d.x(), d.y(), a.x(), a.y(), -1);
        }
        g.flush(); g.disableScissor();
        clipUv(g);
        int x = (int) (uvLeft() + pixel[0] * uvScale()), y = (int) (uvTop() + pixel[1] * uvScale());
        int side = Math.max(1, (int) Math.ceil(uvScale()));
        g.renderOutline(x, y, side, side, -1); g.disableScissor();
    }

    private void renderRectangle(GuiGraphics g) {
        if (gesture != Gesture.MODEL_SELECT && gesture != Gesture.UV_SELECT) return;
        if (gesture == Gesture.MODEL_SELECT) clipModel(g); else clipUv(g);
        int x = (int) Math.min(selectionX, selectionEndX), y = (int) Math.min(selectionY, selectionEndY);
        int w = Math.max(1, (int) Math.abs(selectionEndX - selectionX)), h = Math.max(1, (int) Math.abs(selectionEndY - selectionY));
        g.fill(x, y, x + w, y + h, 0x22D7B45F); g.renderOutline(x, y, w, h, MireflowGuiTheme.ACCENT);
        g.disableScissor();
    }

    private SkinEditorProjection.Hit modelHit(double x, double y) {
        ensureProjection(); return SkinEditorProjection.pick(projected, x, y);
    }

    private int[] uvPixel(double x, double y) {
        int px = (int) Math.floor((x - uvLeft()) / uvScale()), py = (int) Math.floor((y - uvTop()) / uvScale());
        return px < 0 || py < 0 || px >= edit.width() || py >= edit.height() ? null : new int[]{px, py};
    }

    private static float[] uvBounds(SkinEditorMesh.Face face) {
        float u0 = 1, v0 = 1, u1 = 0, v1 = 0;
        for (var p : face.vertices()) { u0 = Math.min(u0, p.u()); v0 = Math.min(v0, p.v()); u1 = Math.max(u1, p.u()); v1 = Math.max(v1, p.v()); }
        return new float[]{u0, v0, u1, v1};
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if(button==0&&brushInput!=null&&brushInput.isActive()&&brushInput.isMouseOver(x,y)) {
            brushDragging=true;brushDragStartX=(int)x;brushDragStartValue=brush;
        }
        if (brushInput != null && brushInput.isFocused() && !brushInput.isMouseOver(x, y)) commitBrush();
        if (selection != null) return super.mouseClicked(x, y, button);
        int divider = layout.dividerAt(x, y);
        if (button == 0 && divider != 0) {
            finishGesture(); gesture = divider == 1 ? Gesture.LEFT_DIVIDER : Gesture.RIGHT_DIVIDER; gestureButton = button;
            return true;
        }
        if (super.mouseClicked(x, y, button)) return true;
        if (edit == null || textures == null) return false;
        if(button==0&&tool==Tool.ROTATE&&layout.inModel(x,y)) {
            double[] center=selectedPartAxis();
            if(center!=null) {
                SkinEditorPose.Axis axis=SkinEditorView.poseAxisAt(center[0],center[1],x,y,poseRadius(),yaw,pitch,pose,selectedPart);
                if(axis!=null) {poseAxis=axis;beginPoseGesture();gestureButton=button;gesture=Gesture.ROTATE;updateActions();return true;}
            }
        }
        if(SkinEditorView.inGizmo(layout,x,y) && button==0) {
            if(button==0 && tool==Tool.ROTATE) {
                var axis=SkinEditorView.axisAt(layout,yaw,pitch,x,y);
                if(axis!=null) {
                    poseAxis=axis==SkinEditorView.Axis.X||axis==SkinEditorView.Axis.NEG_X?SkinEditorPose.Axis.X
                            :axis==SkinEditorView.Axis.Y||axis==SkinEditorView.Axis.NEG_Y?SkinEditorPose.Axis.Y:SkinEditorPose.Axis.Z;
                    gestureButton=button;gesture=Gesture.ROTATE;updateActions();return true;
                }
            }
            if(button==0) {
                var angles=SkinEditorView.click(layout,yaw,pitch,x,y);
                if(angles!=null){yaw=angles.yaw();pitch=angles.pitch();projectionDirty=true;}
            }
            return true;
        }
        boolean model = layout.inModel(x, y), uv = layout.inUv(x, y);
        if (!model && !uv) return false;
        finishGesture(); gestureButton = button; gestureEditRevision = edit.revision();
        if(model && button==0 && tool==Tool.ROTATE) {
            var hit=modelHit(x,y);
            if(hit!=null)selectedPart=hit.quad().face().part();
            projectionDirty=true; updateActions(); return hit!=null;
        }
        if (model && button == 1) gesture = Gesture.VIEW_ROTATE;
        else if (uv && (button == 1 || button == 2)) gesture = Gesture.UV_PAN;
        else if (model && button == 0 && modelHit(x,y)==null) gesture = Gesture.MODEL_PAN;
        else if (uv && button == 0 && tool == Tool.BRUSH && !hasShiftDown() && uvPixel(x,y)==null) gesture = Gesture.UV_PAN;
        else if (button == 0 && (tool == Tool.SELECT || hasShiftDown())) {
            gesture = model ? Gesture.MODEL_SELECT : Gesture.UV_SELECT;
            selectionX = selectionEndX = x; selectionY = selectionEndY = y;
        } else if (button == 0 && tool == Tool.BRUSH) {
            gesture = model ? Gesture.MODEL_BRUSH : Gesture.UV_BRUSH;
            edit.begin(); if (model) paintModel(x, y); else paintUv(x, y);
        }
        updateActions();
        return gesture != Gesture.NONE;
    }

    private void paintModel(double x, double y) {
        var hit = modelHit(x, y);
        if (hit == null) { lastX = -1; lastFace = null; return; }
        int px = hit.x(edit.width()), py = hit.y(edit.height());
        if (lastX < 0 || lastFace != hit.quad().face()) { lastX = px; lastY = py; }
        float[] bounds = uvBounds(hit.quad().face());
        edit.line(lastX, lastY, px, py, brush, deny, (int) Math.ceil(bounds[0] * edit.width()),
                (int) Math.ceil(bounds[1] * edit.height()), (int) Math.floor(bounds[2] * edit.width()), (int) Math.floor(bounds[3] * edit.height()));
        lastX = px; lastY = py; lastFace = hit.quad().face(); message = null;
    }

    private void paintUv(double x, double y) {
        int[] pixel = uvPixel(x, y);
        if (pixel == null) { lastX = -1; return; }
        if (lastX < 0) { lastX = pixel[0]; lastY = pixel[1]; }
        edit.line(lastX, lastY, pixel[0], pixel[1], brush, deny);
        lastX = pixel[0]; lastY = pixel[1]; message = null;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if(brushDragging&&button==0) {setBrush(brushDragStartValue+(int)((x-brushDragStartX)/3));return true;}
        if (gesture == Gesture.NONE || button != gestureButton) return super.mouseDragged(x, y, button, dx, dy);
        switch (gesture) {
            case LEFT_DIVIDER, RIGHT_DIVIDER -> {
                layout = layout.drag(gesture == Gesture.LEFT_DIVIDER ? 1 : 2, (int) x);
                leftRatio = layout.left() / (double) width; rightRatio = layout.right() / (double) width;
                positionWidgets();
            }
            case MODEL_BRUSH -> { if (layout.inModel(x, y)) paintModel(x, y); else lastX = -1; }
            case UV_BRUSH -> { if (layout.inUv(x, y)) paintUv(x, y); else lastX = -1; }
            case MODEL_SELECT, UV_SELECT -> updateRectangle(x, y);
            case ROTATE -> {
                if(tool==Tool.ROTATE) { pose.rotateLocal(selectedPart,poseAxis,(dx-dy)*.02); projectionDirty=true; }
                else { var angles=SkinEditorView.drag(yaw,pitch,dx,dy);yaw=angles.yaw();pitch=angles.pitch();projectionDirty=true; }
            }
            case VIEW_ROTATE -> { var angles=SkinEditorView.drag(yaw,pitch,dx,dy);yaw=angles.yaw();pitch=angles.pitch();projectionDirty=true; }
            case MODEL_PAN -> { modelPanX += dx; modelPanY += dy; projectionDirty = true; }
            case UV_PAN -> { uvPanX += dx; uvPanY += dy; }
            default -> {}
        }
        return true;
    }

    private void updateRectangle(double x, double y) {
        boolean model = gesture == Gesture.MODEL_SELECT;
        selectionEndX = Mth.clamp(x, model ? layout.modelLeft() : 5, model ? layout.modelRight() - 1 : layout.left() - 6);
        selectionEndY = Mth.clamp(y, model ? SkinEditorLayout.MODEL_TOP : SkinEditorLayout.UV_TOP, layout.bottom() - 1);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if(brushDragging&&button==0) {brushDragging=false;commitBrush();return true;}
        if (gesture == Gesture.NONE || button != gestureButton) return super.mouseReleased(x, y, button);
        if (gesture == Gesture.ROTATE) commitPoseGesture();
        if (gesture == Gesture.MODEL_SELECT || gesture == Gesture.UV_SELECT) {
            updateRectangle(x, y); message = null;
            if (gesture == Gesture.MODEL_SELECT) {
                ensureProjection(); edit.begin();
                selection = new SkinEditorSelection(projected, edit.width(), edit.height(), selectionX, selectionY, selectionEndX, selectionEndY, deny);
                if (selection.step(edit, 8192)) selection = null;
            } else {
                // Raw UV coordinates keep an off-texture rectangle from painting a clamped edge pixel.
                int x0 = (int) Math.floor((selectionX - uvLeft()) / uvScale()), y0 = (int) Math.floor((selectionY - uvTop()) / uvScale());
                int x1 = (int) Math.floor((selectionEndX - uvLeft()) / uvScale()), y1 = (int) Math.floor((selectionEndY - uvTop()) / uvScale());
                edit.rectangle(x0, y0, x1, y1, deny);
            }
        }
        if (gesture != Gesture.ROTATE && edit != null && edit.revision() != gestureEditRevision) lastEdit=EditKind.MASK;
        gesture = Gesture.NONE; lastX = -1; lastFace = null;
        if (selection == null && edit != null) edit.end();
        updateActions();
        super.mouseReleased(x, y, button);
        return true;
    }

    private void finishGesture() {
        if (gesture == Gesture.ROTATE) commitPoseGesture();
        else if (gesture != Gesture.NONE && edit != null && edit.revision() != gestureEditRevision) lastEdit=EditKind.MASK;
        selection = null;
        if (edit != null) edit.end();
        gesture = Gesture.NONE; lastX = -1; lastFace = null;
        poseHoverAxis=null;
    }

    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (gesture != Gesture.NONE || selection != null) return true;
        if (parts != null && parts.mouseScrolled(x, y, sx, sy)) return true;
        if (edit == null || sy == 0) return false;
        if (layout.inModel(x, y)) { modelZoom = Mth.clamp(modelZoom * Math.pow(1.15, sy), .2, 64); projectionDirty = true; return true; }
        if (layout.inUv(x, y)) {
            double px = (x - uvLeft()) / uvScale(), py = (y - uvTop()) / uvScale();
            uvZoom = Mth.clamp(uvZoom * Math.pow(1.2, sy), .25, 512);
            double scale = uvScale();
            uvPanX = x - layout.left() * .5 + edit.width() * scale * .5 - px * scale;
            uvPanY = y - (SkinEditorLayout.UV_TOP + layout.bottom()) * .5 + edit.height() * scale * .5 - py * scale;
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (brushInput != null && brushInput.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commitBrush(); return true; }
            return super.keyPressed(key, scan, modifiers);
        }
        if (selection != null && key != GLFW.GLFW_KEY_ESCAPE) return true;
        if (edit != null && hasControlDown() && (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y)) {
            finishGesture();
            boolean redo = key == GLFW.GLFW_KEY_Y || hasShiftDown();
            if (lastEdit == EditKind.POSE && (redo ? !poseRedo.isEmpty() : !poseUndo.isEmpty())) {
                if (redo) {
                    poseUndo.addLast(pose.copy()); pose.copyFrom(poseRedo.removeLast());
                } else {
                    poseRedo.addLast(pose.copy()); pose.copyFrom(poseUndo.removeLast());
                }
                lastEdit=EditKind.POSE; projectionDirty=true;
            } else {
                if (redo) edit.redo(); else edit.undo();
                lastEdit=EditKind.MASK;
            }
            updateActions(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() { finishGesture(); closeTextures(); }
    private void closeTextures() { if (textures != null) { textures.close(); textures = null; } }

    private static final class ApplyToast {
        private static final int LIFETIME = 70;
        private int age;
    }
}
