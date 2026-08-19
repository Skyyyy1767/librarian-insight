package librarianinsight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PriceDisplayConfigTest {
    @TempDir
    Path directory;

    @Test
    void migratesLegacySettingsWithoutDeletingLegacyFile() throws IOException {
        Path current = directory.resolve("librarian-insight.properties");
        Path legacy = directory.resolve("visible-librarian-trades.properties");
        Files.writeString(legacy, "showPrice=false\ntextColor=yellow\nmenuTheme=dark\n");

        PriceDisplayConfig config = new PriceDisplayConfig(current, legacy);

        assertFalse(config.isEnabled());
        assertEquals(LecternTextColor.YELLOW, config.getTextColor());
        assertEquals(LibrarianMenuTheme.DARK, config.getMenuTheme());
        assertTrue(Files.isRegularFile(current));
        assertTrue(Files.isRegularFile(legacy));

        Properties migrated = load(current);
        assertEquals("false", migrated.getProperty("showPrice"));
        assertEquals("yellow", migrated.getProperty("textColor"));
        assertEquals("dark", migrated.getProperty("menuTheme"));
    }

    @Test
    void currentConfigWinsWhenBothFilesExist() throws IOException {
        Path current = directory.resolve("librarian-insight.properties");
        Path legacy = directory.resolve("visible-librarian-trades.properties");
        Files.writeString(current, "showPrice=true\ntextColor=black\nmenuTheme=light\n");
        Files.writeString(legacy, "showPrice=false\ntextColor=yellow\nmenuTheme=dark\n");

        PriceDisplayConfig config = new PriceDisplayConfig(current, legacy);

        assertTrue(config.isEnabled());
        assertEquals(LecternTextColor.BLACK, config.getTextColor());
        assertEquals(LibrarianMenuTheme.LIGHT, config.getMenuTheme());
    }

    @Test
    void freshInstallCreatesNewConfigAndPersistsSettings() {
        Path current = directory.resolve("librarian-insight.properties");
        Path legacy = directory.resolve("visible-librarian-trades.properties");

        PriceDisplayConfig config = new PriceDisplayConfig(current, legacy);
        assertTrue(Files.isRegularFile(current));

        config.setEnabled(false);
        config.setTextColor(LecternTextColor.YELLOW);
        config.setMenuTheme(LibrarianMenuTheme.DARK);

        PriceDisplayConfig reloaded = new PriceDisplayConfig(current, legacy);
        assertFalse(reloaded.isEnabled());
        assertEquals(LecternTextColor.YELLOW, reloaded.getTextColor());
        assertEquals(LibrarianMenuTheme.DARK, reloaded.getMenuTheme());
    }

    private static Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }
}
