package com.fish.mirebound.client.worldgen;

import com.fish.mirebound.client.gui.MireflowEditBox;
import com.fish.mirebound.client.gui.MireflowGuiTheme;
import com.fish.mirebound.client.tuning.MudTuningSlider;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** The wand's slider and exact input, linked without rebuilding widgets during a drag. */
final class WorldgenNumericControl {
    private final MudTuningSlider slider;
    private final EditBox field;
    private final int decimals;
    private boolean syncing;

    WorldgenNumericControl(Font font, int x, int y, int width, Component label,
            double min, double max, double step, int decimals, double current,
            boolean enabled, DoubleConsumer responder, Consumer<AbstractWidget> add) {
        this.decimals = decimals;
        int fieldWidth = Math.min(68, Math.max(30, width / 3));
        int sliderWidth = Math.max(16, width - fieldWidth - 4);
        field = new MireflowEditBox(font, x + sliderWidth + 4, y, fieldWidth, 20, label);
        field.setMaxLength(12);
        field.setFilter(s -> s.isEmpty() || s.matches("\\d*(\\.\\d*)?"));
        slider = new MudTuningSlider(x, y, sliderWidth, 20, min, max, step, decimals,
                current, next -> {
                    setValue(next);
                    responder.accept(next);
                });
        setValue(current);
        field.setResponder(s -> {
            if (syncing) return;
            try {
                double value = Double.parseDouble(s);
                if (!Double.isFinite(value) || value < min || value > max) throw new NumberFormatException();
                field.setTextColor(MireflowGuiTheme.TEXT);
                double snapped = slider.snapValue(value);
                slider.setParameterValue(snapped);
                responder.accept(snapped);
            } catch (NumberFormatException ignored) { field.setTextColor(0xFFFF8974); }
        });
        slider.active = enabled;
        field.active = enabled;
        field.setEditable(enabled);
        Tooltip tooltip = Tooltip.create(label);
        slider.setTooltip(tooltip);
        field.setTooltip(tooltip);
        add.accept(slider);
        add.accept(field);
    }

    EditBox field() { return field; }

    void syncValue(double value) {
        slider.setParameterValue(value);
        if (!field.isFocused()) setValue(value);
    }

    void setValue(double value) {
        syncing = true;
        try {
            slider.setParameterValue(value);
            field.setValue(String.format(Locale.ROOT, "%." + decimals + "f", value));
            field.setTextColor(MireflowGuiTheme.TEXT);
        } finally { syncing = false; }
    }
}
