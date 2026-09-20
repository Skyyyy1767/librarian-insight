package librarianinsight.forge;

import librarianinsight.LibrarianCommands;
import librarianinsight.LibrarianInsight;
import librarianinsight.client.MerchantScreenOverlay;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(LibrarianInsight.MOD_ID)
public final class LibrarianInsightForge {
    public LibrarianInsightForge(FMLJavaModLoadingContext context) {
        LibrarianInsight.initializeClient(FMLPaths.CONFIGDIR.get());
        TickEvent.ClientTickEvent.Post.BUS.addListener(this::clientTick);
        RegisterClientCommandsEvent.BUS.addListener(this::registerCommands);
        PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(this::entityInteract);
        EntityLeaveLevelEvent.BUS.addListener(this::entityLeave);
        ScreenEvent.Init.Pre.BUS.addListener(this::screenInit);
        ContainerScreenEvent.Render.Foreground.BUS.addListener(this::foreground);
    }

    private void clientTick(TickEvent.ClientTickEvent.Post event) {
        LibrarianInsight.clientTick(Minecraft.getInstance());
    }

    private void registerCommands(RegisterClientCommandsEvent event) {
        LibrarianCommands.register(event.getDispatcher(),
                (source, message) -> source.sendSuccess(() -> message, false));
    }

    private void entityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
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

    private void foreground(ContainerScreenEvent.Render.Foreground event) {
        MerchantScreenOverlay.afterForeground(event.getContainerScreen(), event.getGuiGraphics(),
                event.getMouseX(), event.getMouseY());
    }
}
