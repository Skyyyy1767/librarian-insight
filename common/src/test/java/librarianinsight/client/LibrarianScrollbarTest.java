package librarianinsight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LibrarianScrollbarTest {
    @Test
    void rowThumbTracksFirstVisibleRowAndMapsPointerToBothEnds() {
        LibrarianScrollbar.Geometry geometry = LibrarianScrollbar.rows(100, 20, 120, 30, 6, 12.0);

        assertEquals(24.0, geometry.maxValue());
        assertEquals(68, geometry.thumbY());
        assertTrue(geometry.containsThumb(101, 70));
        assertEquals(0.0, geometry.valueForPointer(-100, 4));
        assertEquals(24.0, geometry.valueForPointer(1_000, 4));
    }

    @Test
    void pixelThumbClampsAndUsesExpandedHorizontalGrabArea() {
        LibrarianScrollbar.Geometry geometry = LibrarianScrollbar.pixels(50, 10, 100, 400, 999.0);

        assertEquals(300.0, geometry.maxValue());
        assertEquals(85, geometry.thumbY());
        assertTrue(geometry.containsThumb(48, 90));
        assertFalse(geometry.containsThumb(47, 90));
        assertEquals(150.0, geometry.valueForPointer(60, 12.5), 0.0001);
    }
}
