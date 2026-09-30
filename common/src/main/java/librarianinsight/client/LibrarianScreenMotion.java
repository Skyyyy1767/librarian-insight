package librarianinsight.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Lightweight, time-based motion state shared by the screen's visual components. */
final class LibrarianScreenMotion {
    private static final double OPEN_SECONDS = 0.18;
    private static final double TAB_SECONDS = 0.16;
    private static final double SELECTION_SECONDS = 0.22;
    private static final double RESPONSE = 20.0;

    private final long createdAt = System.nanoTime();
    private final Map<Object, HoverValue> hovers = new HashMap<>();
    private long previousFrame = createdAt;
    private long tabChangedAt = createdAt - (long)(TAB_SECONDS * 1_000_000_000L);
    private long selectionChangedAt = createdAt - (long)(SELECTION_SECONDS * 1_000_000_000L);
    private float deltaSeconds;
    private long frameId;

    void beginFrame(long now) {
        deltaSeconds = (float)Math.min(0.05, Math.max(0.0, (now - previousFrame) / 1_000_000_000.0));
        previousFrame = now;
        frameId++;
        Iterator<HoverValue> iterator = hovers.values().iterator();
        while (iterator.hasNext()) {
            HoverValue hover = iterator.next();
            if (hover.lastFrame < frameId - 1 && hover.value < 0.01F) {
                iterator.remove();
            }
        }
    }

    float openProgress(long now) {
        return easeOutCubic(progress(now - createdAt, OPEN_SECONDS));
    }

    int entranceOffset(long now) {
        return Math.round(7.0F * (1.0F - openProgress(now)));
    }

    int contentOffset(long now) {
        return Math.round(4.0F * (1.0F - easeOutCubic(progress(now - tabChangedAt, TAB_SECONDS))));
    }

    float tabProgress(long now) {
        return easeOutCubic(progress(now - tabChangedAt, TAB_SECONDS));
    }

    float selectionPulse(long now) {
        float progress = progress(now - selectionChangedAt, SELECTION_SECONDS);
        return 1.0F - progress;
    }

    void tabChanged(long now) {
        tabChangedAt = now;
        selectionChangedAt = now;
    }

    void selectionChanged(long now) {
        selectionChangedAt = now;
    }

    float hover(Object key, boolean hovered) {
        HoverValue value = hovers.computeIfAbsent(key, ignored -> new HoverValue());
        float target = hovered ? 1.0F : 0.0F;
        float response = 1.0F - (float)Math.exp(-RESPONSE * deltaSeconds);
        value.value += (target - value.value) * response;
        if (Math.abs(value.value - target) < 0.005F) {
            value.value = target;
        }
        value.lastFrame = frameId;
        return value.value;
    }

    double scroll(double displayed, double target) {
        float response = 1.0F - (float)Math.exp(-RESPONSE * deltaSeconds);
        double next = displayed + (target - displayed) * response;
        return Math.abs(next - target) < 0.05 ? target : next;
    }

    static int mix(int from, int to, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int a = mixChannel(from >>> 24, to >>> 24, clamped);
        int r = mixChannel(from >>> 16, to >>> 16, clamped);
        int g = mixChannel(from >>> 8, to >>> 8, clamped);
        int b = mixChannel(from, to, clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }

    static int withAlpha(int color, float alpha) {
        int sourceAlpha = color >>> 24;
        int adjusted = Math.round(sourceAlpha * Math.max(0.0F, Math.min(1.0F, alpha)));
        return color & 0x00FFFFFF | adjusted << 24;
    }

    private static int mixChannel(int from, int to, float amount) {
        return Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
    }

    private static float progress(long elapsedNanos, double seconds) {
        return Math.max(0.0F, Math.min(1.0F, (float)(elapsedNanos / (seconds * 1_000_000_000.0))));
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static final class HoverValue {
        private float value;
        private long lastFrame;
    }
}
