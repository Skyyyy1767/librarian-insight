package librarianinsight;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Small dependency-free client settings for Librarian Insight presentation. */
public final class PriceDisplayConfig {
    private static final String CONFIG_FILENAME = "librarian-insight.properties";
    private static final String LEGACY_CONFIG_FILENAME = "visible-librarian-trades.properties";
    private static final String PRICE_KEY = "showPrice";
    private static final String TEXT_COLOR_KEY = "textColor";
    private static final String MENU_THEME_KEY = "menuTheme";
    private final Path path;
    private boolean enabled = true;
    private LecternTextColor textColor = LecternTextColor.BLACK;
    private LibrarianMenuTheme menuTheme = LibrarianMenuTheme.LIGHT;

    public PriceDisplayConfig() {
        this(
                FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILENAME),
                FabricLoader.getInstance().getConfigDir().resolve(LEGACY_CONFIG_FILENAME)
        );
    }

    PriceDisplayConfig(Path path) {
        this(path, null);
    }

    PriceDisplayConfig(Path path, Path legacyPath) {
        this.path = path;
        load(legacyPath);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public LecternTextColor getTextColor() {
        return textColor;
    }

    public void setTextColor(LecternTextColor textColor) {
        this.textColor = textColor;
        save();
    }

    public LibrarianMenuTheme getMenuTheme() {
        return menuTheme;
    }

    public void setMenuTheme(LibrarianMenuTheme menuTheme) {
        this.menuTheme = menuTheme;
        save();
    }

    public void toggleMenuTheme() {
        setMenuTheme(menuTheme.toggled());
    }

    private void load(Path legacyPath) {
        if (Files.isRegularFile(path)) {
            loadFrom(path);
            return;
        }

        if (legacyPath != null && Files.isRegularFile(legacyPath) && loadFrom(legacyPath)) {
            LibrarianInsight.LOGGER.info(
                    "Imported legacy Visible Librarian Trades client config from {} to {}",
                    legacyPath,
                    path
            );
            save();
            return;
        }

        save();
    }

    private boolean loadFrom(Path source) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(source)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty(PRICE_KEY, "true"));
            textColor = LecternTextColor.parse(properties.getProperty(TEXT_COLOR_KEY))
                    .orElse(LecternTextColor.BLACK);
            menuTheme = LibrarianMenuTheme.parse(properties.getProperty(MENU_THEME_KEY))
                    .orElse(LibrarianMenuTheme.LIGHT);
            return true;
        } catch (IOException exception) {
            LibrarianInsight.LOGGER.warn(
                    "Could not read Librarian Insight client config {}; using defaults",
                    source,
                    exception
            );
            return false;
        }
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty(PRICE_KEY, Boolean.toString(enabled));
        properties.setProperty(TEXT_COLOR_KEY, textColor.commandName());
        properties.setProperty(MENU_THEME_KEY, menuTheme.commandName());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Librarian Insight client settings");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LibrarianInsight.LOGGER.warn("Could not save price display config {}", path, exception);
        }
    }
}
