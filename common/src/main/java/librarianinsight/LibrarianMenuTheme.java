package librarianinsight;

import java.util.Locale;
import java.util.Optional;

/** Client-selectable palette for the standalone librarian information menu. */
public enum LibrarianMenuTheme {
    LIGHT,
    DARK;

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        String name = commandName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public LibrarianMenuTheme toggled() {
        return this == LIGHT ? DARK : LIGHT;
    }

    public static Optional<LibrarianMenuTheme> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
