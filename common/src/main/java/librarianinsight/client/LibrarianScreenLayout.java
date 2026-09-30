package librarianinsight.client;

/**
 * Version-neutral sizing policy for the standalone screen. Keeping these
 * calculations away from rendering makes the 26.2 backport a small API
 * adaptation instead of a second layout implementation.
 */
final class LibrarianScreenLayout {
    static final int MAX_WIDTH = 560;
    static final int MAX_HEIGHT = 334;
    static final int OUTER_MARGIN = 6;

    private LibrarianScreenLayout() {
    }

    static Spec resolve(int screenWidth, int screenHeight, int entranceOffset) {
        int availableWidth = Math.max(1, screenWidth - OUTER_MARGIN * 2);
        int availableHeight = Math.max(1, screenHeight - OUTER_MARGIN * 2);
        int width = Math.min(MAX_WIDTH, availableWidth);
        int height = Math.min(MAX_HEIGHT, availableHeight);

        boolean shortScreen = height < 230;
        int headerHeight = shortScreen ? 48 : 58;
        int footerHeight = shortScreen ? 16 : 20;
        if (headerHeight + footerHeight >= height) {
            headerHeight = Math.max(28, height / 3);
            footerHeight = Math.max(11, Math.min(16, height / 8));
        }

        int x = (screenWidth - width) / 2;
        int restingY = (screenHeight - height) / 2;
        int y = Math.min(screenHeight - height, restingY + Math.max(0, entranceOffset));
        boolean compact = width < 390 || height < 230;
        return new Spec(x, y, width, height, headerHeight, footerHeight, compact);
    }

    record Spec(
            int x,
            int y,
            int width,
            int height,
            int headerHeight,
            int footerHeight,
            boolean compact
    ) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        int contentHeight() {
            return Math.max(1, height - headerHeight - footerHeight);
        }

        int statusColumns() {
            return width >= 390 ? 2 : 1;
        }

        int possibleGridColumns(int gridWidth) {
            return gridWidth >= 178 ? 2 : 1;
        }
    }
}
