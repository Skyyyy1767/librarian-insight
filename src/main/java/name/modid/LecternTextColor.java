package name.modid;

import java.util.Locale;
import java.util.Optional;

/** Supported client-selectable colors for enchantment names on lecterns. */
public enum LecternTextColor {
    BLACK(0xFF000000),
    WHITE(0xFFFFFFFF),
    RED(0xFFFF5555),
    GREEN(0xFF55FF55),
    BLUE(0xFF5555FF),
    YELLOW(0xFFFFFF55),
    ORANGE(0xFFFF8800),
    PURPLE(0xFFAA00AA),
    PINK(0xFFFF55FF),
    CYAN(0xFF55FFFF),
    GRAY(0xFFAAAAAA);

    private final int argb;

    LecternTextColor(int argb) {
        this.argb = argb;
    }

    public int argb() {
        return argb;
    }

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        String name = commandName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static Optional<LecternTextColor> parse(String value) {
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
