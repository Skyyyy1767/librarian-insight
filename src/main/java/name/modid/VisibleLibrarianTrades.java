package name.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Minecraft 26.2 port of Saphjyr's Visible Librarian Trades (MIT).
 */
public final class VisibleLibrarianTrades implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("visible-librarian-trades");
    public static EnchantmentManager enchantmentManager;
    public static LecternManager lecternManager;
    public static PriceDisplayConfig priceDisplay;
    public static boolean displayIcons;

    @Override
    public void onInitializeClient() {
        priceDisplay = new PriceDisplayConfig();
        enchantmentManager = new EnchantmentManager();
        lecternManager = new LecternManager();
        registerCommands();
    }

    public static void toggleDisplayIcons() {
        displayIcons = !displayIcons;
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("vlt").then(literal("price")
                        .executes(context -> updatePriceSetting(context.getSource(), null))
                        .then(literal("on").executes(context -> updatePriceSetting(context.getSource(), true)))
                        .then(literal("off").executes(context -> updatePriceSetting(context.getSource(), false))))
        ));
    }

    private static int updatePriceSetting(
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
            Boolean enabled
    ) {
        if (enabled == null) {
            priceDisplay.toggle();
        } else {
            priceDisplay.setEnabled(enabled);
        }
        source.sendFeedback(Component.literal("Lectern price display: " + (priceDisplay.isEnabled() ? "ON" : "OFF")));
        return 1;
    }
}
