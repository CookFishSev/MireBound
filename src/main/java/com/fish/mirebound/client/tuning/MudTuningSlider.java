package com.fish.mirebound.client.tuning;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.client.gui.MireflowGuiTheme;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Parameter slider whose value is mirrored by the row's exact edit box. */
public final class MudTuningSlider extends AbstractSliderButton {
    private final double minimum;
    private final double maximum;
    private final double step;
    private final int decimals;
    private final DoubleConsumer responder;

    public MudTuningSlider(int x, int y, int width, int height,
            MudPhysicsParameter parameter, double current, DoubleConsumer responder) {
        this(x, y, width, height, parameter.minimum(), parameter.maximum(),
                parameter.step(), parameter.decimals(), current, responder);
    }

    public MudTuningSlider(int x, int y, int width, int height,
            double minimum, double maximum, double step, int decimals,
            double current, DoubleConsumer responder) {
        super(x, y, width, height, Component.empty(),
                normalize(minimum, maximum, snapValue(
                        minimum, maximum, step, current)));
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.decimals = decimals;
        this.responder = responder;
        updateMessage();
    }

    public void setParameterValue(double current) {
        value = normalize(minimum, maximum, snapValue(
                minimum, maximum, step, current));
        updateMessage();
    }

    /** Returns the exact value represented by this slider for an arbitrary input. */
    public double snapValue(double current) {
        return snapValue(minimum, maximum, step, current);
    }

    @Override
    protected void updateMessage() {
        double current = snap(minimum, maximum, step, value);
        setMessage(Component.literal(String.format(
                Locale.ROOT, "%." + decimals + "f", current)));
    }

    @Override
    protected void applyValue() {
        responder.accept(snap(minimum, maximum, step, value));
        updateMessage();
    }

    @Override
    public void renderWidget(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int border = isHoveredOrFocused()
                ? MireflowGuiTheme.ACCENT : MireflowGuiTheme.DIVIDER;
        int fill = active ? MireflowGuiTheme.INPUT : 0xFF202622;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
        graphics.fill(getX() + 1, getY() + 1,
                getX() + getWidth() - 1, getY() + getHeight() - 1, fill);

        int trackLeft = getX() + 4;
        int trackRight = Math.max(trackLeft + 1, getX() + getWidth() - 4);
        int trackY = getY() + getHeight() - 5;
        int knob = trackLeft + (int) Math.round(value * (trackRight - trackLeft));
        graphics.fill(trackLeft, trackY, trackRight, trackY + 2, 0xFF4A554E);
        graphics.fill(trackLeft, trackY, knob, trackY + 2,
                active ? MireflowGuiTheme.ACCENT : MireflowGuiTheme.DISABLED);
        graphics.fill(knob - 1, trackY - 2, knob + 2, trackY + 4,
                active ? MireflowGuiTheme.TEXT : MireflowGuiTheme.DISABLED);
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + 3,
                active ? MireflowGuiTheme.TEXT : MireflowGuiTheme.DISABLED);
    }

    static double snap(double minimum, double maximum, double step, double normalized) {
        if (normalized <= 0.0D) {
            return minimum;
        }
        if (normalized >= 1.0D) {
            return maximum;
        }
        double raw = minimum + normalized * (maximum - minimum);
        double stepped = minimum + Math.round((raw - minimum) / step) * step;
        return Math.max(minimum, Math.min(maximum, stepped));
    }

    public static double snapValue(double minimum, double maximum, double step,
            double current) {
        if (!Double.isFinite(current)) {
            return minimum;
        }
        return snap(minimum, maximum, step,
                normalize(minimum, maximum, current));
    }

    private static double normalize(double minimum, double maximum, double current) {
        if (!Double.isFinite(current)) {
            return 0.0D;
        }
        double span = maximum - minimum;
        return span <= 0.0D ? 0.0D
                : Math.max(0.0D, Math.min(1.0D,
                        (current - minimum) / span));
    }
}
