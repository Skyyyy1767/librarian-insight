package name.modid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Small dependency-free client setting for the lectern price row. */
public final class PriceDisplayConfig {
    private static final String KEY = "showPrice";
    private final Path path;
    private boolean enabled = true;

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

    private void load() {
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty(KEY, "true"));
        } catch (IOException exception) {
            VisibleLibrarianTrades.LOGGER.warn("Could not read price display config {}; using default ON", path, exception);
        }
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty(KEY, Boolean.toString(enabled));
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
