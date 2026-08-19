package librarianinsight.client;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import librarianinsight.mixin.MerchantScreenAccessor;
import librarianinsight.trade.LibrarianMinimumPrice;
import librarianinsight.trade.LibrarianTradeMode;
import librarianinsight.trade.LibrarianTradeReference;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Adds a read-only current/minimum cost card to directly opened librarian screens. */
public final class MerchantScreenOverlay {
    private static final int VANILLA_SCREEN_WIDTH = 276;
    private static final int VANILLA_SCREEN_HEIGHT = 166;
    private static final int PANEL_WIDTH = 236;
    private static final int PANEL_HEIGHT = 36;
    private static final int CONTEXT_LIFETIME_TICKS = 12;
    private static final int PANEL_BACKGROUND = 0xE0101010;
    private static final int PANEL_BORDER = 0xFFA0A0A0;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xFFB0B0B0;

    private static PendingContext pendingContext;
    private static final Map<MerchantScreen, OverlayState> ACTIVE_SCREENS = new WeakHashMap<>();

    private MerchantScreenOverlay() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (entity instanceof Villager villager
                    && villager.isAlive()
                    && !villager.isBaby()
                    && villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
                pendingContext = new PendingContext(
                        villager.getUUID(),
                        villager.getVillagerData().type().unwrapKey(),
                        level.getGameTime()
                );
            } else {
                clearPendingContext();
            }
            return InteractionResult.PASS;
        });

        ScreenEvents.BEFORE_INIT.register((minecraft, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof MerchantScreen merchantScreen)) {
                clearPendingContext();
                return;
            }
            OverlayState overlayState = ACTIVE_SCREENS.get(merchantScreen);
            if (overlayState == null) {
                Optional<PendingContext> context = consumeLibrarianContext(minecraft);
                if (context.isEmpty()) {
                    return;
                }
                overlayState = new OverlayState(context.get().villagerType());
                ACTIVE_SCREENS.put(merchantScreen, overlayState);
            }
            OverlayState retainedState = overlayState;
            ScreenEvents.afterForeground(merchantScreen).register((ignored, graphics, mouseX, mouseY, tickProgress) ->
                    extractOverlay(merchantScreen, retainedState, graphics, mouseX, mouseY));
        });
    }

    private static Optional<PendingContext> consumeLibrarianContext(Minecraft minecraft) {
        PendingContext context = pendingContext;
        clearPendingContext();
        if (context == null || minecraft.level == null) {
            return Optional.empty();
        }
        long age = minecraft.level.getGameTime() - context.interactionTime();
        if (age < 0L || age > CONTEXT_LIFETIME_TICKS) {
            return Optional.empty();
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof Villager villager && villager.getUUID().equals(context.librarianUuid())) {
                return villager.isAlive()
                        && !villager.isBaby()
                        && villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)
                        ? Optional.of(context)
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static void clearPendingContext() {
        pendingContext = null;
    }

    private static void extractOverlay(
            MerchantScreen screen,
            OverlayState overlayState,
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY
    ) {
        MerchantOffers offers = screen.getMenu().getOffers();
        int selected = ((MerchantScreenAccessor) screen).librarianInsight$getShopItem();
        if (selected < 0 || selected >= offers.size()) {
            return;
        }

        MerchantOffer offer = offers.get(selected);
        Optional<PanelPosition> panelPosition = positionPanel(screen);
        if (panelPosition.isEmpty()) {
            return;
        }
        PanelPosition panel = panelPosition.get();
        graphics.fill(panel.x(), panel.y(), panel.x() + PANEL_WIDTH, panel.y() + PANEL_HEIGHT, PANEL_BACKGROUND);
        graphics.outline(panel.x(), panel.y(), PANEL_WIDTH, PANEL_HEIGHT, PANEL_BORDER);

        Font font = Minecraft.getInstance().font;
        int currentY = panel.y() + 1;
        int minimumY = panel.y() + 18;
        graphics.text(font, "Current:", panel.x() + 5, currentY + 4, TEXT_COLOR, false);
        LibrarianMinimumPrice.CurrentCost current = LibrarianMinimumPrice.currentCost(offer);
        extractCosts(
                graphics,
                font,
                current.first(),
                current.second().orElse(ItemStack.EMPTY),
                panel.x() + 94,
                currentY,
                mouseX,
                mouseY
        );

        graphics.text(font, "Minimum vanilla:", panel.x() + 5, minimumY + 4, TEXT_COLOR, false);
        Optional<MinimumCosts> minimum = overlayState.minimumFor(offer);
        if (minimum.isPresent()) {
            MinimumCosts costs = minimum.get();
            extractCosts(graphics, font, costs.first(), costs.second(), panel.x() + 94, minimumY, mouseX, mouseY);
        } else {
            graphics.text(font, "unavailable", panel.x() + 94, minimumY + 4, MUTED_TEXT_COLOR, false);
        }
    }

    private static void extractCosts(
            GuiGraphicsExtractor graphics,
            Font font,
            ItemStack first,
            ItemStack second,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        extractCost(graphics, font, first, x, y, mouseX, mouseY);
        if (!second.isEmpty()) {
            extractCost(graphics, font, second, x + 31, y, mouseX, mouseY);
        }
    }

    private static void extractCost(
            GuiGraphicsExtractor graphics,
            Font font,
            ItemStack stack,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.fakeItem(stack, x, y);
        graphics.itemDecorations(font, stack, x, y, stack.getCount() == 1 ? "1" : null);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    /**
     * The only integration point for the conservative vanilla-trade matcher.
     * Until a stock 26.2 offer is recognized, custom/unknown trades deliberately
     * report no minimum instead of inferring one from their current cost.
     */
    private static Optional<MinimumCosts> minimumFor(
            MerchantOffer offer,
            Optional<ResourceKey<VillagerType>> villagerType
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Optional.empty();
        }
        LibrarianTradeMode mode = LibrarianTradeMode.fromEnabledFeatures(minecraft.level.enabledFeatures());
        return LibrarianMinimumPrice.minimumNaturalCost(
                offer,
                minecraft.level.registryAccess(),
                mode,
                villagerType
        ).map(MerchantScreenOverlay::toMinimumCosts);
    }

    private static MinimumCosts toMinimumCosts(LibrarianTradeReference.NaturalCost cost) {
        LibrarianTradeReference.ItemRange first = cost.first();
        ItemStack firstStack = new ItemStack(first.item(), first.count().minimum());
        ItemStack secondStack = cost.second()
                .map(second -> new ItemStack(second.item(), second.count().minimum()))
                .orElse(ItemStack.EMPTY);
        return new MinimumCosts(firstStack, secondStack);
    }

    private static Optional<PanelPosition> positionPanel(MerchantScreen screen) {
        int left = (screen.width - VANILLA_SCREEN_WIDTH) / 2;
        int top = (screen.height - VANILLA_SCREEN_HEIGHT) / 2;
        int rightSpace = screen.width - (left + VANILLA_SCREEN_WIDTH);
        if (rightSpace >= PANEL_WIDTH + 4) {
            return Optional.of(new PanelPosition(left + VANILLA_SCREEN_WIDTH + 4, top + 24));
        }
        if (left >= PANEL_WIDTH + 4) {
            return Optional.of(new PanelPosition(left - PANEL_WIDTH - 4, top + 24));
        }
        if (screen.height - (top + VANILLA_SCREEN_HEIGHT) >= PANEL_HEIGHT + 1) {
            return Optional.of(new PanelPosition(left + VANILLA_SCREEN_WIDTH - PANEL_WIDTH, top + VANILLA_SCREEN_HEIGHT + 1));
        }
        // Do not obscure vanilla slots/buttons at unusually small scaled sizes.
        return Optional.empty();
    }

    private record PanelPosition(int x, int y) {
    }

    private record PendingContext(
            UUID librarianUuid,
            Optional<ResourceKey<VillagerType>> villagerType,
            long interactionTime
    ) {
        private PendingContext {
            villagerType = Optional.ofNullable(villagerType).orElseGet(Optional::empty);
        }
    }

    /** Avoids rebuilding the full enchanted-book reference list every frame. */
    private static final class OverlayState {
        private final Optional<ResourceKey<VillagerType>> villagerType;
        private MerchantOffer cachedOffer;
        private Optional<MinimumCosts> cachedMinimum = Optional.empty();

        private OverlayState(Optional<ResourceKey<VillagerType>> villagerType) {
            this.villagerType = villagerType;
        }

        private Optional<MinimumCosts> minimumFor(MerchantOffer offer) {
            if (cachedOffer != offer) {
                cachedOffer = offer;
                cachedMinimum = MerchantScreenOverlay.minimumFor(offer, villagerType);
            }
            return cachedMinimum;
        }
    }

    private record MinimumCosts(ItemStack first, ItemStack second) {
        private MinimumCosts {
            first = first.copy();
            second = second.copy();
        }
    }
}
