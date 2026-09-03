package com.fish.mirebound.client.tuning;

import com.fish.mirebound.client.MudTuningWandCoreTexture;
import com.fish.mirebound.network.payload.TentacleWandActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Immediate drag selector: the opening mouse press owns the thumb until release. */
final class TentacleVolumeSelectionScreen extends Screen {
    private static final int MINIMUM_WIDTH = 160;
    private static final int MAXIMUM_WIDTH = 420;
    private static final int CANCEL_GAP = 8;
    private static final int CANCEL_WIDTH = 72;
    private static final int PANEL_TOP_OFFSET = -37;
    private static final int PANEL_BOTTOM_OFFSET = 35;
    private static final int DEFAULT_VOLUME = TentacleWandActionPayload.MINIMUM_SUMMON_VOLUME;

    private final Vec3 placementTarget;
    private final int releaseButton;
    private int selectedVolume;
    private int audibleVolume;
    private long nextVolumeSoundMillis;
    private boolean submitted;

    private TentacleVolumeSelectionScreen(Vec3 placementTarget, int releaseButton) {
        super(Component.translatable("hud.mirebound.tuning.tentacle_volume"));
        this.placementTarget = placementTarget;
        this.releaseButton = releaseButton;
        selectedVolume = DEFAULT_VOLUME;
        audibleVolume = DEFAULT_VOLUME;
    }

    static void open(Minecraft minecraft, Vec3 placementTarget, int releaseButton) {
        if (minecraft != null && placementTarget != null) {
            minecraft.setScreen(new TentacleVolumeSelectionScreen(
                    placementTarget, releaseButton));
        }
    }

    static void summonDefault(Vec3 placementTarget) {
        if (placementTarget != null) {
            sendSummon(placementTarget, DEFAULT_VOLUME);
        }
    }

    Vec3 placementTarget() {
        return placementTarget;
    }

    @Override
    protected void init() {
        int thumbX = volumeX(selectedVolume, barLeft(), barRight());
        double scale = minecraft.getWindow().getGuiScale();
        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(),
                thumbX * scale, barY() * scale);
    }

    @Override
    public void tick() {
        if (minecraft.player == null || minecraft.level == null
                || MudTuningInputController.heldWandHand(minecraft.player) == null) {
            onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        selectedVolume = volumeAt(mouseX, barLeft(), barRight());
        updateVolumeSound(false);
        int left = barLeft();
        int right = barRight();
        int y = barY();
        int cancelLeft = cancelLeft();
        int cancelRight = cancelLeft + CANCEL_WIDTH;
        boolean cancelHovered = cancelAt(
                mouseX, mouseY, cancelLeft, cancelTop(), cancelRight, cancelBottom());
        int thumbX = volumeX(selectedVolume, left, right);
        double time = minecraft.level == null ? 0.0D
                : minecraft.level.getGameTime() + partialTick;
        int accent = 0xFF000000 | MudTuningWandCoreTexture.hudColor(time);
        int panelLeft = left - 13;
        int panelRight = cancelRight;
        graphics.fill(panelLeft, y + PANEL_TOP_OFFSET,
                panelRight, y + PANEL_BOTTOM_OFFSET, 0xD90A0E0C);
        graphics.fill(panelLeft, y + PANEL_TOP_OFFSET,
                panelRight, y + PANEL_TOP_OFFSET + 2, accent);
        graphics.drawCenteredString(font, title,
                (panelLeft + panelRight) / 2, y - 29, 0xFFE8EEE8);

        graphics.fill(left - 2, y - 5, right + 3, y + 6, 0xFF050706);
        graphics.fill(left, y - 3, right + 1, y + 4, 0xFF34413A);
        graphics.fill(left, y - 3, thumbX + 1, y + 4, accent);
        for (int volume = 1; volume <= 50; volume += 7) {
            int tickX = volumeX(volume, left, right);
            graphics.fill(tickX, y + 5, tickX + 1, y + 8, 0xFF89978E);
        }
        graphics.fill(thumbX - 5, y - 10, thumbX + 6, y + 11, 0xFF080B09);
        graphics.fill(thumbX - 3, y - 8, thumbX + 4, y + 9, accent);
        graphics.fill(thumbX - 1, y - 6, thumbX + 2, y + 7, 0xFFF2F5E9);

        Component value = Component.literal(Integer.toString(selectedVolume));
        graphics.drawCenteredString(font, value, thumbX, y - 24, 0xFFFFFFFF);
        graphics.drawString(font, Component.literal("1"), left, y + 13, 0xFFB8C3BC, false);
        Component maximum = Component.literal("50");
        graphics.drawString(font, maximum, right - font.width(maximum), y + 13,
                0xFFB8C3BC, false);

        int cancelBorder = cancelHovered ? 0xFFFF8A7A : 0xFF7D8780;
        graphics.fill(cancelLeft, cancelTop(), cancelRight, cancelBottom(), cancelBorder);
        graphics.fill(cancelLeft + 1, cancelTop() + 1,
                cancelRight - 1, cancelBottom() - 1,
                cancelHovered ? 0xFF542A25 : 0xFF242B27);
        graphics.drawCenteredString(font,
                Component.translatable("gui.mirebound.physics.cancel"),
                (cancelLeft + cancelRight) / 2, y - 4, 0xFFF1E9DD);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != releaseButton || submitted) {
            return true;
        }
        submitted = true;
        if (!cancelAt(mouseX, mouseY, cancelLeft(), cancelTop(),
                cancelLeft() + CANCEL_WIDTH, cancelBottom())) {
            selectedVolume = volumeAt(mouseX, barLeft(), barRight());
            updateVolumeSound(true);
            sendSummon(placementTarget, selectedVolume);
        }
        onClose();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!submitted && cancelAt(mouseX, mouseY, cancelLeft(), cancelTop(),
                cancelLeft() + CANCEL_WIDTH, cancelBottom())) {
            submitted = true;
            onClose();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static int volumeAt(double mouseX, int left, int right) {
        double amount = Mth.clamp((mouseX - left) / Math.max(1.0D, right - left), 0.0D, 1.0D);
        return Mth.clamp((int) Math.round(1.0D + amount * 49.0D), 1, 50);
    }

    static int volumeX(int volume, int left, int right) {
        double amount = (Mth.clamp(volume, 1, 50) - 1.0D) / 49.0D;
        return left + (int) Math.round((right - left) * amount);
    }

    static boolean cancelAt(double mouseX, double mouseY,
            int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right
                && mouseY >= top && mouseY < bottom;
    }

    private int barLeft() {
        return (width - barWidth() - CANCEL_GAP - CANCEL_WIDTH) / 2;
    }

    private int barRight() {
        return barLeft() + barWidth();
    }

    private int barWidth() {
        return Math.min(MAXIMUM_WIDTH, Math.max(MINIMUM_WIDTH,
                width - 54 - CANCEL_GAP - CANCEL_WIDTH));
    }

    private int cancelLeft() {
        return barRight() + CANCEL_GAP;
    }

    private int barY() {
        return height / 2;
    }

    private int cancelTop() {
        return barY() + PANEL_TOP_OFFSET;
    }

    private int cancelBottom() {
        return barY() + PANEL_BOTTOM_OFFSET;
    }

    private void updateVolumeSound(boolean force) {
        if (selectedVolume == audibleVolume) {
            return;
        }
        long now = Util.getMillis();
        if (!force && now < nextVolumeSoundMillis) {
            return;
        }
        MudTuningWandUiSounds.playVolumeStep(minecraft, selectedVolume);
        audibleVolume = selectedVolume;
        nextVolumeSoundMillis = now + 28L;
    }

    private static void sendSummon(Vec3 target, int volume) {
        PacketDistributor.sendToServer(new TentacleWandActionPayload(
                TentacleWandActionPayload.Action.SUMMON, -1,
                target.x, target.y, target.z, volume));
    }
}
