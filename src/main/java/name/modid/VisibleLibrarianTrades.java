package name.modid;

import net.fabricmc.api.ClientModInitializer;

/**
 * Minecraft 26.2 port of Saphjyr's Visible Librarian Trades (MIT).
 */
public final class VisibleLibrarianTrades implements ClientModInitializer {
    public static EnchantmentManager enchantmentManager;
    public static LecternManager lecternManager;
    public static boolean displayIcons;

    @Override
    public void onInitializeClient() {
        enchantmentManager = new EnchantmentManager();
        lecternManager = new LecternManager();
    }

    public static void toggleDisplayIcons() {
        displayIcons = !displayIcons;
    }
}
