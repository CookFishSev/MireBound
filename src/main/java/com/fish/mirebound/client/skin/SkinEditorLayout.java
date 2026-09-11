package com.fish.mirebound.client.skin;

/** Screen-space panel bounds shared by drawing, picking and divider dragging. */
record SkinEditorLayout(int width, int height, int left, int right) {
    static final int TOP = 30, TOOL_TOP = 35, MODEL_TOP = 64, UV_TOP = MODEL_TOP, GAP = 5;

    static SkinEditorLayout fit(int width, int height, double leftRatio, double rightRatio) {
        int minLeft = Math.min(160, width / 4);
        int minRight = Math.min(146, width / 3);
        int minModel = Math.min(200, width - minLeft - minRight);
        int left = clamp((int) Math.round(width * leftRatio), minLeft, width - minRight - minModel);
        int right = clamp((int) Math.round(width * rightRatio), left + minModel, width - minRight);
        return new SkinEditorLayout(width, height, left, right);
    }

    SkinEditorLayout drag(int divider, int x) {
        int minLeft = Math.min(160, width / 4), minRight = Math.min(146, width / 3);
        int minModel = Math.min(200, width - minLeft - minRight);
        return divider == 1
                ? new SkinEditorLayout(width, height, clamp(x, minLeft, right - minModel), right)
                : new SkinEditorLayout(width, height, left, clamp(x, left + minModel, width - minRight));
    }

    int bottom() { return height - 22; }
    int modelLeft() { return left + GAP; }
    int modelRight() { return right - GAP; }
    boolean inModel(double x, double y) {
        return x >= modelLeft() && x < modelRight() && y >= MODEL_TOP && y < bottom();
    }
    boolean inUv(double x, double y) {
        return x >= GAP && x < left - GAP && y >= UV_TOP && y < bottom();
    }
    int dividerAt(double x, double y) {
        if (y < TOP || y >= bottom()) return 0;
        return Math.abs(x - left) <= 4 ? 1 : Math.abs(x - right) <= 4 ? 2 : 0;
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
