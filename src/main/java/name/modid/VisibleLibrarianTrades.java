package name.modid;

import name.modid.client.LibrarianInfoMenu;
import name.modid.client.MerchantScreenOverlay;
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
        LibrarianInfoMenu.register();
        MerchantScreenOverlay.register();
        registerCommands();
    }

    public static void toggleDisplayIcons() {
        displayIcons = !displayIcons;
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("vlt")
                        .then(literal("price")
                                .executes(context -> updatePriceSetting(context.getSource(), null))
                                .then(literal("on").executes(context -> updatePriceSetting(context.getSource(), true)))
                                .then(literal("off").executes(context -> updatePriceSetting(context.getSource(), false))))
                        .then(colorCommand())
        ));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> colorCommand() {
        var command = literal("color");
        for (LecternTextColor color : LecternTextColor.values()) {
            command.then(literal(color.commandName())
                    .executes(context -> updateTextColor(context.getSource(), color, false)));
        }
        return command.then(literal("reset")
                .executes(context -> updateTextColor(context.getSource(), LecternTextColor.BLACK, true)));
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
        source.sendFeedback(Component.literal("Price display " + (priceDisplay.isEnabled() ? "enabled." : "disabled.")));
        return 1;
    }

    private static int updateTextColor(
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
            LecternTextColor color,
            boolean reset
    ) {
        priceDisplay.setTextColor(color);
        String message = reset
                ? "Text color reset to Black."
                : "Text color set to " + color.displayName() + ".";
        source.sendFeedback(Component.literal(message));
        return 1;
    }
}
