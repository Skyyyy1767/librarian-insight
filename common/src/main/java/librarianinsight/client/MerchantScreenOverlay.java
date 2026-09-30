package librarianinsight.client;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import librarianinsight.mixin.MerchantScreenAccessor;
import librarianinsight.trade.LibrarianMinimumPrice;
import librarianinsight.trade.LibrarianTradeMode;
import librarianinsight.trade.LibrarianTradeReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/** Adds a read-only current/minimum cost card to directly opened librarian screens. */
public final class MerchantScreenOverlay {
    private static final int VANILLA_SCREEN_WIDTH = 276;
    private static final int VANILLA_SCREEN_HEIGHT = 166;
    private static final int PANEL_WIDTH = 236;
    private static final int PANEL_HEIGHT = 56;
    private static final int CONTEXT_LIFETIME_TICKS = 12;

    private static PendingContext pendingContext;
    private static final Map<MerchantScreen, OverlayState> ACTIVE_SCREENS = new WeakHashMap<>();

    private MerchantScreenOverlay() {
    }

    public static InteractionResult useEntity(Player player, Level level, InteractionHand hand,
            Entity entity, @Nullable EntityHitResult hitResult) {
        if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (entity instanceof Villager villager && villager.isAlive() && !villager.isBaby()
                && villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            pendingContext = new PendingContext(villager.getUUID(),
                    villager.getVillagerData().type().unwrapKey(), level.getGameTime());
        } else {
            clearPendingContext();
        }
        return InteractionResult.PASS;
    }

    public static void beforeScreenInit(Minecraft minecraft, Screen screen) {
        if (!(screen instanceof MerchantScreen merchantScreen)) {
            clearPendingContext();
            return;
        }
        if (!ACTIVE_SCREENS.containsKey(merchantScreen)) {
            consumeLibrarianContext(minecraft).ifPresent(context ->
                    ACTIVE_SCREENS.put(merchantScreen, new OverlayState(context.villagerType())));
        }
    }

    public static void afterForeground(Screen screen,
            GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (screen instanceof MerchantScreen merchantScreen) {
            OverlayState state = ACTIVE_SCREENS.get(merchantScreen);
            if (state != null) {
                extractOverlay(merchantScreen, state, graphics, mouseX, mouseY);
            }
        }
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
        LibrarianMenuPalette palette = LibrarianMenuPalette.forTheme(
                librarianinsight.LibrarianInsight.priceDisplay.getMenuTheme());
        drawPanel(graphics, panel, palette);

        Font font = Minecraft.getInstance().font;
        graphics.fakeItem(new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK), panel.x() + 5, panel.y() + 1);
        graphics.text(font, "Librarian Insight", panel.x() + 27, panel.y() + 5, palette.headerText(), false);

        int currentY = panel.y() + 19;
        int minimumY = panel.y() + 37;
        graphics.text(font, "Current cost", panel.x() + 7, currentY + 4, palette.primaryText(), false);
        LibrarianMinimumPrice.CurrentCost current = LibrarianMinimumPrice.currentCost(offer);
        extractCosts(
                graphics,
                font,
                current.first(),
                current.second().orElse(ItemStack.EMPTY),
                panel.x() + 104,
                currentY,
                mouseX,
                mouseY,
                palette
        );

        graphics.text(font, "Natural minimum", panel.x() + 7, minimumY + 4, palette.primaryText(), false);
        Optional<MinimumCosts> minimum = overlayState.minimumFor(offer);
        if (minimum.isPresent()) {
            MinimumCosts costs = minimum.get();
            extractCosts(graphics, font, costs.first(), costs.second(), panel.x() + 104, minimumY,
                    mouseX, mouseY, palette);
        } else {
            graphics.text(font, "unavailable", panel.x() + 104, minimumY + 4, palette.secondaryText(), false);
        }
    }

    private static void drawPanel(
            GuiGraphicsExtractor graphics,
            PanelPosition panel,
            LibrarianMenuPalette palette
    ) {
        graphics.fill(panel.x() + 3, panel.y() + 4, panel.x() + PANEL_WIDTH + 3,
                panel.y() + PANEL_HEIGHT + 4, palette.shadow());
        graphics.fillGradient(panel.x(), panel.y(), panel.x() + PANEL_WIDTH, panel.y() + PANEL_HEIGHT,
                palette.panel(), palette.panelShade());
        graphics.fillGradient(panel.x() + 1, panel.y() + 1, panel.x() + PANEL_WIDTH - 1, panel.y() + 18,
                palette.headerTop(), palette.headerBottom());
        graphics.outline(panel.x(), panel.y(), PANEL_WIDTH, PANEL_HEIGHT, palette.border());
        graphics.outline(panel.x() + 1, panel.y() + 1, PANEL_WIDTH - 2, PANEL_HEIGHT - 2, palette.innerBorder());
        graphics.horizontalLine(panel.x() + 4, panel.x() + PANEL_WIDTH - 5, panel.y() + 18, palette.accent());
        graphics.horizontalLine(panel.x() + 6, panel.x() + PANEL_WIDTH - 7, panel.y() + 36, palette.separator());
    }

    private static void extractCosts(
            GuiGraphicsExtractor graphics,
            Font font,
            ItemStack first,
            ItemStack second,
            int x,
            int y,
            int mouseX,
            int mouseY,
            LibrarianMenuPalette palette
    ) {
        extractCost(graphics, font, first, x, y, mouseX, mouseY, palette);
        if (!second.isEmpty()) {
            graphics.text(font, "+", x + 21, y + 4, palette.primaryText(), false);
            extractCost(graphics, font, second, x + 32, y, mouseX, mouseY, palette);
        }
    }

    private static void extractCost(
            GuiGraphicsExtractor graphics,
            Font font,
            ItemStack stack,
            int x,
            int y,
            int mouseX,
            int mouseY,
            LibrarianMenuPalette palette
    ) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.fill(x - 1, y - 1, x + 17, y + 17, palette.slotShadow());
        graphics.fill(x, y, x + 16, y + 16, palette.slot());
        graphics.fakeItem(stack, x, y);
        graphics.itemDecorations(font, stack, x, y, stack.getCount() == 1 ? "1" : null);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    /**
     * The only integration point for the conservative vanilla-trade matcher.
     * Until a stock 26.3 offer is recognized, custom/unknown trades deliberately
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
