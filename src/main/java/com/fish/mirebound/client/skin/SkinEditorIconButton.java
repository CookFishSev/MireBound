package com.fish.mirebound.client.skin;

import com.fish.mirebound.client.gui.MireflowGuiTheme;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Image-backed toolbar icons keep their shape independent of the active font. */
final class SkinEditorIconButton extends Button {
    private static final ResourceLocation ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "mirebound", "textures/gui/skin_editor_icons.png");
    enum Icon { UNDO, REDO, BRUSH, SELECT, ROTATE, VIEW, GRID, ALPHA, EYE, HIDDEN, CUBE, FOLDER, LESS, MORE }
    private static final int ICON_COUNT = Icon.values().length;
    private static final RenderType ICONS = RenderType.create("mirebound_skin_editor_icons",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(ICON_TEXTURE, true, true))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private final Icon icon;
    private final BooleanSupplier selected;
    private boolean hoverSuppressed;

    static void prepareTexture() {
        var manager = Minecraft.getInstance().getTextureManager();
        if (!(manager.getTexture(ICON_TEXTURE, null) instanceof SkinEditorIconTexture))
            manager.register(ICON_TEXTURE, new SkinEditorIconTexture(ICON_TEXTURE, ICON_COUNT));
    }

    SkinEditorIconButton(Icon icon, Component label, BooleanSupplier selected, Runnable action) {
        super(0, 0, 22, 22, label, button -> {
            action.run();
            button.setFocused(false);
            ((SkinEditorIconButton) button).hoverSuppressed = true;
        }, DEFAULT_NARRATION);
        this.icon = icon;
        this.selected = selected;
        setTooltip(Tooltip.create(label));
    }

    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        boolean chosen = selected.getAsBoolean();
        boolean hovering = isHoveredOrFocused();
        if (!hovering) hoverSuppressed = false;
        boolean hot = hovering && !hoverSuppressed;
        int border = active && (chosen || hot) ? MireflowGuiTheme.ACCENT : MireflowGuiTheme.DIVIDER;
        g.fill(getX(), getY(), getX() + width, getY() + height, border);
        g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1,
                chosen ? 0xFF3B3321 : hot && active ? 0xFF303138 : 0xFF1D2026);
        int color=!active?MireflowGuiTheme.DISABLED:chosen?MireflowGuiTheme.ACCENT:MireflowGuiTheme.TEXT;
        drawImage(g,icon,getX()+2,getY()+2,width-4,height-4,color);
    }

    static void draw(GuiGraphics g, Icon icon, int x, int y, int color) {
        drawImage(g,icon,x-8,y-8,16,16,color);
    }

    private static void drawImage(GuiGraphics g,Icon icon,int x,int y,int width,int height,int color) {
        g.flush();
        var buffer=g.bufferSource().getBuffer(ICONS);
        var matrix=g.pose().last().pose();
        float u0=icon.ordinal()/(float)ICON_COUNT, u1=(icon.ordinal()+1F)/ICON_COUNT;
        // Icons own alpha blending; plain GUI blits can inherit blending disabled by panel fills.
        buffer.addVertex(matrix,x,y,0).setUv(u0,0).setColor(color);
        buffer.addVertex(matrix,x,y+height,0).setUv(u0,1).setColor(color);
        buffer.addVertex(matrix,x+width,y+height,0).setUv(u1,1).setColor(color);
        buffer.addVertex(matrix,x+width,y,0).setUv(u1,0).setColor(color);
        g.bufferSource().endBatch(ICONS);
    }

}
