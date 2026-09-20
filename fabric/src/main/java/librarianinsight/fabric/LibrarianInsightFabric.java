package librarianinsight.fabric;

import librarianinsight.LibrarianInsight;
import librarianinsight.LibrarianCommands;
import librarianinsight.client.LibrarianInfoMenu;
import librarianinsight.client.MerchantScreenOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

public final class LibrarianInsightFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LibrarianInsight.initializeClient(FabricLoader.getInstance().getConfigDir());
        ClientTickEvents.END_CLIENT_TICK.register(LibrarianInsight::clientTick);
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, level) ->
                LibrarianInsight.lecternManager.blockEntityLoaded(entity));
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, level) ->
                LibrarianInsight.lecternManager.blockEntityUnloaded(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) ->
                LibrarianInsight.lecternManager.entityUnloaded(entity));
        UseBlockCallback.EVENT.register(LibrarianInfoMenu::useBlock);
        UseEntityCallback.EVENT.register(MerchantScreenOverlay::useEntity);
        ScreenEvents.BEFORE_INIT.register((minecraft, screen, width, height) -> {
            MerchantScreenOverlay.beforeScreenInit(minecraft, screen);
            ScreenEvents.afterRender(screen).register((s, graphics, x, y, tick) ->
                    MerchantScreenOverlay.afterForeground(s, graphics, x, y));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                LibrarianCommands.register(dispatcher, FabricClientCommandSource::sendFeedback));
    }
}
