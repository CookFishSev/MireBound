package com.fish.mirebound.client.tooltip;

/** Time-based glyph motion; tooltip reconstruction does not restart the reveal. */
final class WandLoreAnimation {
    static final double CHARACTER_DELAY = 0.065D;
    private static final double FADE_SECONDS = 0.42D;
    private static final double FLICKER_PERIOD = 1.7D;

    private WandLoreAnimation() {
    }

    static double opacity(double seconds, int index) {
        return smooth((seconds - index * CHARACTER_DELAY) / FADE_SECONDS);
    }

    static float floatOffset(double seconds, int index) {
        double phase = random(index * 31L + 7) * Math.PI * 2;
        return (float) (Math.sin(seconds * 1.05D + phase) * 0.72D);
    }

    static int gray(double seconds, int index) {
        double time = Math.max(0, seconds) / FLICKER_PERIOD + random(index * 79L + 13);
        long cycle = (long) Math.floor(time);
        double phase = time - cycle;
        double noise = random(cycle * 73471L + index * 9127L);
        double pulse = noise < 0.26D ? Math.pow(Math.sin(phase * Math.PI), 4) : 0;
        return (int) Math.round(166 - pulse * (38 + noise * 100));
    }

    private static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static double random(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }

    static final class Session {
        private long lastSeen = Long.MIN_VALUE;
        private long expandedAt;
        private boolean expanded;

        double observe(boolean shift, long nowMillis) {
            if (shift && (!expanded || lastSeen == Long.MIN_VALUE || nowMillis - lastSeen > 250)) {
                expandedAt = nowMillis;
            }
            lastSeen = nowMillis;
            expanded = shift;
            return shift ? Math.max(0, nowMillis - expandedAt) / 1000.0D : 0;
        }

        void reset() {
            lastSeen = Long.MIN_VALUE;
            expanded = false;
        }
    }
}
