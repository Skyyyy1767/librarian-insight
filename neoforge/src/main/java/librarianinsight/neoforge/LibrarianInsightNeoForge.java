package librarianinsight.neoforge;

import librarianinsight.LibrarianInsight;
import librarianinsight.LibrarianCommands;
import librarianinsight.client.MerchantScreenOverlay;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod(value = LibrarianInsight.MOD_ID, dist = Dist.CLIENT)
public final class LibrarianInsightNeoForge {
    public LibrarianInsightNeoForge() {
        LibrarianInsight.initializeClient(FMLPaths.CONFIGDIR.get());
        NeoForge.EVENT_BUS.addListener(this::clientTick);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::entityInteract);
        NeoForge.EVENT_BUS.addListener(this::entityLeave);
        NeoForge.EVENT_BUS.addListener(this::screenInit);
        NeoForge.EVENT_BUS.addListener(this::foreground);
    }

    private void clientTick(ClientTickEvent.Post event) {
        LibrarianInsight.clientTick(Minecraft.getInstance());
    }

    private void registerCommands(RegisterClientCommandsEvent event) {
        LibrarianCommands.register(event.getDispatcher(),
                (source, message) -> source.sendSuccess(() -> message, false));
    }

    private void entityInteract(PlayerInteractEvent.EntityInteract event) {
        MerchantScreenOverlay.useEntity(event.getEntity(), event.getLevel(),
                event.getHand(), event.getTarget(), null);
    }

    private void entityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            LibrarianInsight.lecternManager.entityUnloaded(event.getEntity());
        }
    }

    private void screenInit(ScreenEvent.Init.Pre event) {
        MerchantScreenOverlay.beforeScreenInit(Minecraft.getInstance(), event.getScreen());
    }

    private void foreground(ScreenEvent.Render.Foreground event) {
        MerchantScreenOverlay.afterForeground(event.getScreen(), event.getGuiGraphics(),
                event.getMouseX(), event.getMouseY());
    }
}
