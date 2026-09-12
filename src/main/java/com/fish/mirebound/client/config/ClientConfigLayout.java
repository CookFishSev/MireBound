package com.fish.mirebound.client.config;

import com.fish.mirebound.client.gui.MireflowGuiTheme;

/** Fixed design coordinates shared by rendering and pointer input. */
record ClientConfigLayout(MireflowGuiTheme.Panel panel, double scale) {
    static final int WIDTH = 560;
    static final int HEIGHT = 360;
    private static final int MARGIN = 12;

    static ClientConfigLayout fit(int width, int height) {
        double scale = Math.min(1.0D, Math.min(
                Math.max(1, width - MARGIN * 2) / (double) WIDTH,
                Math.max(1, height - MARGIN * 2) / (double) HEIGHT));
        int canvasWidth = (int) Math.ceil(width / scale);
        int canvasHeight = (int) Math.ceil(height / scale);
        return new ClientConfigLayout(new MireflowGuiTheme.Panel(
                (canvasWidth - WIDTH) / 2, (canvasHeight - HEIGHT) / 2,
                WIDTH, HEIGHT, canvasWidth, canvasHeight), scale);
    }

    double pointer(double coordinate) {
        return coordinate / scale;
    }
}
