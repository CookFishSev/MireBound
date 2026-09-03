package com.fish.mirebound.client.tuning;

/** Responsive full-screen editor geometry with no viewport-scaled typography. */
public record MudTuningScreenLayout(int width, int height, boolean compact,
        int headerHeight, int footerHeight, int sidebarWidth, int contentLeft,
        int contentTop, int contentRight, int contentBottom, int rowHeight) {
    public static MudTuningScreenLayout calculate(int width, int height) {
        boolean compact = width < 520;
        int header = compact ? 76 : 72;
        int footer = 34;
        int sidebar = compact ? 32 : Math.min(158, Math.max(126, width / 6));
        int rowHeight = width < 420 ? 42 : 34;
        return new MudTuningScreenLayout(width, height, compact, header, footer, sidebar,
                sidebar + 1, header + 40, width, height - footer, rowHeight);
    }

    public static MudTuningScreenLayout calculateTentacle(int width, int height) {
        boolean compact = width < 520;
        int header = 31;
        int footer = 34;
        int rowHeight = width < 420 ? 42 : 34;
        return new MudTuningScreenLayout(width, height, compact, header, footer, 0,
                0, header + 27, width, height - footer, rowHeight);
    }

    public int visibleRows() {
        return Math.max(1, (contentBottom - contentTop - 4) / rowHeight);
    }

    public int visiblePageCount(int pageCount) {
        if (pageCount <= 0) {
            return 0;
        }
        int available = width - contentLeft - 14;
        int withoutNavigation = Math.max(1, (available + 3) / 57);
        if (pageCount <= withoutNavigation) {
            return pageCount;
        }
        return Math.max(1, Math.min(pageCount, (available - 42) / 57));
    }
}
