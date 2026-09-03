package com.fish.mirebound.client.tuning;

import com.fish.mirebound.client.MudTuningWandCoreTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Transparent radial picker centered on the normal crosshair. */
final class SummonSelectionScreen extends Screen {
    private static final int SLOT_SIZE = 46;
    private static final int RING_RADIUS = 72;
    private static final int RING_SEGMENTS = 32;

    private SummonSelectionScreen() {
        super(Component.translatable("hud.mirebound.tuning.summon.choose"));
    }

    static void open(Minecraft minecraft) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.setScreen(new SummonSelectionScreen());
        }
    }

    @Override
    public void tick() {
        if (minecraft.player == null
                || MudTuningInputController.heldWandHand(minecraft.player) == null
                || MudTuningClientState.mode() != MudTuningWandMode.SUMMON) {
            onClose();
        }
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = radius(width, height, SLOT_SIZE);
        double time = minecraft.level == null ? 0.0D
                : minecraft.level.getGameTime() + partialTick;
        int accent = 0xFF000000 | MudTuningWandCoreTexture.hudColor(time);
        renderRing(graphics, centerX, centerY, radius, accent);
        graphics.drawCenteredString(font, title, centerX,
                Math.max(5, centerY - radius - SLOT_SIZE / 2 - 14), 0xFFF1F5EE);

        MudTuningSummonType[] types = MudTuningSummonType.values();
        int hovered = slotAt(mouseX, mouseY,
                centerX, centerY, radius, SLOT_SIZE, types.length);
        for (int index = 0; index < types.length; index++) {
            renderSlot(graphics, types[index], slotBounds(
                    index, types.length, centerX, centerY, radius, SLOT_SIZE),
                    index == hovered, types[index] == MudTuningClientState.summonType(), accent);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MudTuningSummonType[] types = MudTuningSummonType.values();
        int index = slotAt(mouseX, mouseY, width / 2, height / 2,
                radius(width, height, SLOT_SIZE), SLOT_SIZE, types.length);
        if (index >= 0) {
            MudTuningClientState.setSummonType(types[index]);
            MudTuningWandUiSounds.playSummonSelection(minecraft);
        }
        onClose();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static SlotBounds slotBounds(int index, int count,
            int centerX, int centerY, int radius, int slotSize) {
        int safeCount = Math.max(1, count);
        double angle = -Math.PI / 2.0D + Math.PI * 2.0D * index / safeCount;
        int slotCenterX = centerX + (int) Math.round(Math.cos(angle) * radius);
        int slotCenterY = centerY + (int) Math.round(Math.sin(angle) * radius);
        return new SlotBounds(
                slotCenterX - slotSize / 2,
                slotCenterY - slotSize / 2,
                slotSize);
    }

    static int slotAt(double mouseX, double mouseY,
            int centerX, int centerY, int radius, int slotSize, int count) {
        for (int index = 0; index < count; index++) {
            if (slotBounds(index, count, centerX, centerY, radius, slotSize)
                    .contains(mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    private static int radius(int width, int height, int slotSize) {
        int maximum = Math.max(slotSize / 2 + 4,
                Math.min(width, height) / 2 - slotSize / 2 - 18);
        return Math.min(RING_RADIUS, maximum);
    }

    private void renderSlot(GuiGraphics graphics, MudTuningSummonType type,
            SlotBounds bounds, boolean hovered, boolean selected, int accent) {
        int border = selected ? accent : hovered ? 0xFFFFFFFF : 0xFF77847C;
        int background = hovered ? 0xED26332C : 0xE817201C;
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), border);
        graphics.fill(bounds.left() + 2, bounds.top() + 2,
                bounds.right() - 2, bounds.bottom() - 2, background);
        int iconSize = bounds.size() - 19;
        MudTuningSummonIconRenderer.render(graphics, type,
                bounds.left() + (bounds.size() - iconSize) / 2,
                bounds.top() + 5, iconSize,
                selected ? 0xFFFFFFFF : 0xFFD3DDD7);
        Component label = Component.translatable(type.translationKey());
        graphics.drawCenteredString(font, label,
                bounds.left() + bounds.size() / 2,
                bounds.bottom() - font.lineHeight - 3,
                selected ? 0xFFFFFFFF : 0xFFC4CEC8);
    }

    private static void renderRing(
            GuiGraphics graphics, int centerX, int centerY, int radius, int accent) {
        int color = accent & 0x00FFFFFF | 0x92000000;
        for (int index = 0; index < RING_SEGMENTS; index++) {
            double angle = Math.PI * 2.0D * index / RING_SEGMENTS;
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    record SlotBounds(int left, int top, int size) {
        int right() {
            return left + size;
        }

        int bottom() {
            return top + size;
        }

        boolean contains(double x, double y) {
            return x >= left && x < right() && y >= top && y < bottom();
        }
    }
}
