package name.modid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Small dependency-free client settings for lectern presentation. */
public final class PriceDisplayConfig {
    private static final String PRICE_KEY = "showPrice";
    private static final String TEXT_COLOR_KEY = "textColor";
    private final Path path;
    private boolean enabled = true;
    private LecternTextColor textColor = LecternTextColor.BLACK;

    public PriceDisplayConfig() {
        this(FabricLoader.getInstance().getConfigDir().resolve("visible-librarian-trades.properties"));
    }

    PriceDisplayConfig(Path path) {
        this.path = path;
        load();
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

    private void load() {
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty(PRICE_KEY, "true"));
            textColor = LecternTextColor.parse(properties.getProperty(TEXT_COLOR_KEY))
                    .orElse(LecternTextColor.BLACK);
        } catch (IOException exception) {
            VisibleLibrarianTrades.LOGGER.warn("Could not read price display config {}; using default ON", path, exception);
        }
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty(PRICE_KEY, Boolean.toString(enabled));
        properties.setProperty(TEXT_COLOR_KEY, textColor.commandName());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Visible Librarian Trades client settings");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            VisibleLibrarianTrades.LOGGER.warn("Could not save price display config {}", path, exception);
        }
    }
}
