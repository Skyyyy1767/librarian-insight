package librarianinsight.client;

/** Pixel geometry and pointer mapping for the screen's custom scrollbars. */
final class LibrarianScrollbar {
    static final int TRACK_WIDTH = 3;
    static final int HIT_PADDING = 2;
    private static final int MIN_THUMB_HEIGHT = 10;

    private LibrarianScrollbar() {
    }

    static Geometry rows(int x, int y, int height, int totalRows, int visibleRows, double value) {
        if (height <= 0 || visibleRows <= 0 || totalRows <= visibleRows) {
            return null;
        }
        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, height * visibleRows / totalRows);
        return geometry(x, y, height, thumbHeight, totalRows - visibleRows, value);
    }

    static Geometry pixels(int x, int y, int viewportHeight, int contentHeight, double value) {
        if (viewportHeight <= 0 || contentHeight <= viewportHeight) {
            return null;
        }
        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, viewportHeight * viewportHeight / contentHeight);
        return geometry(x, y, viewportHeight, thumbHeight, contentHeight - viewportHeight, value);
    }

    private static Geometry geometry(
            int x,
            int y,
            int height,
            int thumbHeight,
            double maxValue,
            double value
    ) {
        int clampedThumbHeight = Math.min(height, thumbHeight);
        int travel = Math.max(0, height - clampedThumbHeight);
        double clampedValue = clamp(value, 0.0, maxValue);
        int thumbY = y + (int)Math.round(travel * clampedValue / Math.max(1.0, maxValue));
        return new Geometry(x, y, height, thumbY, clampedThumbHeight, maxValue);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Geometry(int x, int y, int height, int thumbY, int thumbHeight, double maxValue) {
        int right() {
            return x + TRACK_WIDTH;
        }

        int bottom() {
            return y + height;
        }

        int thumbBottom() {
            return thumbY + thumbHeight;
        }

        boolean containsThumb(double pointerX, double pointerY) {
            return pointerX >= x - HIT_PADDING
                    && pointerX < right() + HIT_PADDING
                    && pointerY >= thumbY
                    && pointerY < thumbBottom();
        }

        double valueForPointer(double pointerY, double grabOffset) {
            int travel = height - thumbHeight;
            if (travel <= 0 || maxValue <= 0.0) {
                return 0.0;
            }
            double thumbTop = clamp(pointerY - grabOffset, y, y + travel);
            return (thumbTop - y) * maxValue / travel;
        }
    }
}
