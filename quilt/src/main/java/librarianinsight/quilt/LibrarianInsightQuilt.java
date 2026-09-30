package librarianinsight.quilt;

import librarianinsight.fabricquilt.LibrarianInsightFabricQuilt;
import net.fabricmc.api.ClientModInitializer;
import org.quiltmc.loader.api.QuiltLoader;

public final class LibrarianInsightQuilt implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LibrarianInsightFabricQuilt.initialize(QuiltLoader.getConfigDir());
    }
}
