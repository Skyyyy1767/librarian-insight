package name.modid.client;

import name.modid.LibrarianMenuTheme;

/** Centralized colors for the standalone VLT librarian browser. */
record LibrarianMenuPalette(
        int screenBackground,
        int panel,
        int clickableBox,
        int border,
        int primaryText,
        int secondaryText,
        int headingText,
        int selected,
        int hovered,
        int selectedBorder,
        int separator,
        int associationText,
        int statusText,
        int scrollbarTrack,
        int scrollbarThumb
) {
    /**
     * Vanilla container screens use a light gray surface and dark 0x404040
     * text. These neighboring grays echo the inventory/trading UI without
     * flattening the browser into a pure-white panel.
     */
    static final LibrarianMenuPalette LIGHT = new LibrarianMenuPalette(
            0xF0C6C6C6,
            0xFFE1E1E1,
            0xFFB8B8B8,
            0xFF373737,
            0xFF404040,
            0xFF505050,
            0xFF202020,
            0xFF9FC7E8,
            0xFFD6D6D6,
            0xFF2F5E82,
            0xFF8B8B8B,
            0xFF555555,
            0xFF8A5A00,
            0xFF8B8B8B,
            0xFF3E6F94
    );

    /** The pre-theme browser colors, preserved exactly for Dark Mode. */
    static final LibrarianMenuPalette DARK = new LibrarianMenuPalette(
            0xF0181818,
            0xE0282828,
            0xE0121212,
            0xFF8B8B8B,
            0xFFF4F4F4,
            0xFFB0B0B0,
            0xFFF4F4F4,
            0xFF4C7A9E,
            0xFF46525C,
            0xFFFFD56A,
            0xFF8B8B8B,
            0xFFB0B0B0,
            0xFFFFD56A,
            0xFF333333,
            0xFFFFD56A
    );

    static LibrarianMenuPalette forTheme(LibrarianMenuTheme theme) {
        return theme == LibrarianMenuTheme.DARK ? DARK : LIGHT;
    }
}
