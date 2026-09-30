package librarianinsight.fabric;

import librarianinsight.fabricquilt.LibrarianInsightFabricQuilt;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class LibrarianInsightFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LibrarianInsightFabricQuilt.initialize(FabricLoader.getInstance().getConfigDir());
    }
}
