package librarianinsight.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class LibrarianScreenLayoutTest {
    @ParameterizedTest
    @CsvSource({
            "240,160",
            "320,180",
            "427,240",
            "640,360",
            "960,540"
    })
    void layoutStaysUsableAndInsideScaledWindow(int width, int height) {
        LibrarianScreenLayout.Spec layout = LibrarianScreenLayout.resolve(width, height, 7);

        assertTrue(layout.x() >= 0);
        assertTrue(layout.y() >= 0);
        assertTrue(layout.right() <= width);
        assertTrue(layout.bottom() <= height);
        assertTrue(layout.contentHeight() > 0);
        assertTrue(layout.headerHeight() + layout.footerHeight() < layout.height());
    }
}
