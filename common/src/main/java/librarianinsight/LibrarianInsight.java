package librarianinsight;

import java.nio.file.Path;
import librarianinsight.client.IntegratedVillagerStatusService;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Librarian Insight, inspired by Saphjyr's Visible Librarian Trades (MIT). */
public final class LibrarianInsight {
    public static final String MOD_ID = "librarian_insight";
    public static final Logger LOGGER = LoggerFactory.getLogger("Librarian Insight");
    public static EnchantmentManager enchantmentManager;
    public static LecternManager lecternManager;
    public static PriceDisplayConfig priceDisplay;
    public static IntegratedVillagerStatusService villagerStatusService;
    public static boolean displayIcons;

    private LibrarianInsight() {}

    public static void initializeClient(Path configDirectory) {
        priceDisplay = PriceDisplayConfig.inDirectory(configDirectory);
        enchantmentManager = new EnchantmentManager();
        lecternManager = new LecternManager();
        villagerStatusService = new IntegratedVillagerStatusService();
        LOGGER.info("Librarian Insight client initialized");
    }

    public static void clientTick(Minecraft minecraft) {
        // Preserve Fabric's original registration order.
        enchantmentManager.clientTick(minecraft);
        lecternManager.clientTick(minecraft);
    }

    public static void toggleDisplayIcons() {
        displayIcons = !displayIcons;
    }
}
