package librarianinsight.client;

import librarianinsight.LibrarianMenuTheme;

/** Cohesive lectern, parchment, lapis, and enchanted-book colors for the menu. */
record LibrarianMenuPalette(
        int backdropTop,
        int backdropBottom,
        int screenBackground,
        int screenShade,
        int headerTop,
        int headerBottom,
        int panel,
        int panelShade,
        int clickableBox,
        int border,
        int innerBorder,
        int primaryText,
        int secondaryText,
        int headingText,
        int headerText,
        int selected,
        int hovered,
        int selectedBorder,
        int separator,
        int associationText,
        int statusText,
        int successText,
        int warningText,
        int dangerText,
        int scrollbarTrack,
        int scrollbarThumb,
        int shadow,
        int slot,
        int slotShadow,
        int accent,
        int accentBright
) {
    /** Warm parchment and dark oak, with lapis selection and gold trim. */
    static final LibrarianMenuPalette LIGHT = new LibrarianMenuPalette(
            0x9A17110C, 0xD0080705,
            0xFFF0DCA8, 0xFFD0B879,
            0xFF4A2D1C, 0xFF29170E,
            0xFFE4CE96, 0xFFC9AB6B, 0xFFD8BE80,
            0xFF2A190F, 0xFFF8E9BE,
            0xFF2A2118, 0xFF66543C, 0xFF201810, 0xFFFFF0C7,
            0xFF9FB8D8, 0xFFE8D6A7, 0xFF355C91, 0xFF8C7147,
            0xFF594830, 0xFF8A5B16, 0xFF2D6A3C, 0xFF8A5B16, 0xFF9A332D,
            0xFF8B7048, 0xFF3D6598,
            0xB0000000, 0xFFB69A63, 0xFF6D5434,
            0xFFD6A83D, 0xFFFFD86A
    );

    /** Deepslate and dark oak surfaces with warm inventory contrast. */
    static final LibrarianMenuPalette DARK = new LibrarianMenuPalette(
            0xB008090B, 0xE0020304,
            0xFF24221F, 0xFF171614,
            0xFF3B271A, 0xFF21150F,
            0xFF302D29, 0xFF201E1B, 0xFF3A3631,
            0xFF080706, 0xFF514B43,
            0xFFF4EAD2, 0xFFB8AD98, 0xFFFFF3D5, 0xFFFFEAC1,
            0xFF344C70, 0xFF464139, 0xFF7398CC, 0xFF5A544B,
            0xFFC5B99F, 0xFFE0B85E, 0xFF79CE8C, 0xFFE0B85E, 0xFFFF7770,
            0xFF181715, 0xFF7197CB,
            0xD0000000, 0xFF1B1A18, 0xFF090908,
            0xFFD8AA45, 0xFFFFDA70
    );

    static LibrarianMenuPalette forTheme(LibrarianMenuTheme theme) {
        return theme == LibrarianMenuTheme.DARK ? DARK : LIGHT;
    }
}
