package name.modid.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import name.modid.KnownLibrarianSnapshot;
import name.modid.LecternAssociation;
import name.modid.VisibleLibrarianTrades;
import name.modid.trade.LibrarianEnchantedBookReference;
import name.modid.trade.LibrarianMinimumPrice;
import name.modid.trade.LibrarianTradeCatalog;
import name.modid.trade.LibrarianTradeMode;
import name.modid.trade.LibrarianTradeReference;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/** Icon-first, read-only librarian reference browser for an empty lectern. */
public final class LibrarianInfoScreen extends Screen {
    private static final int BACKGROUND = 0xF0181818;
    private static final int PANEL = 0xE0282828;
    private static final int PANEL_DARK = 0xE0121212;
    private static final int BORDER = 0xFF8B8B8B;
    private static final int SELECTED = 0xFF4C7A9E;
    private static final int HOVERED = 0xFF46525C;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFFB0B0B0;
    private static final int ACCENT = 0xFFFFD56A;
    private static final int ERROR = 0xFFFF8A80;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 17;
    private static final int CURRENT_ROW_HEIGHT = 29;
    private static final int BOOK_ROW_HEIGHT = 23;

    private final BlockPos lecternPos;
    private final List<HitTarget> hitTargets = new ArrayList<>();
    private Tab tab = Tab.CURRENT;
    private int selectedCurrent;
    private int currentScroll;
    private int selectedProfessionLevel = 1;
    private @Nullable LibrarianTradeReference selectedPossible;
    private @Nullable LibrarianEnchantedBookReference selectedBook;
    private boolean browsingBooks;
    private int bookProfessionLevel = 1;
    private int bookScroll;
    private @Nullable KnownLibrarianSnapshot cachedSnapshot;
    private MerchantOffers cachedOffers = new MerchantOffers();
    private @Nullable UUID refreshRequestedFor;
    private long refreshRequestedAt = Long.MIN_VALUE;

    public LibrarianInfoScreen(BlockPos lecternPos) {
        super(Component.literal("Visible Librarian Trades"));
        this.lecternPos = lecternPos.immutable();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        hitTargets.clear();

        Layout layout = layout();
        graphics.fill(layout.x(), layout.y(), layout.right(), layout.bottom(), BACKGROUND);
        graphics.outline(layout.x(), layout.y(), layout.width(), layout.height(), BORDER);
        graphics.centeredText(font, title, layout.x() + layout.width() / 2, layout.y() + 7, TEXT);

        int tabY = layout.y() + 21;
        int tabWidth = Math.max(100, (layout.width() - 12) / 2);
        Rect currentTab = new Rect(layout.x() + 5, tabY, tabWidth, 17);
        Rect possibleTab = new Rect(currentTab.right() + 2, tabY, layout.right() - currentTab.right() - 7, 17);
        drawTab(graphics, currentTab, "Current Librarian", tab == Tab.CURRENT, mouseX, mouseY,
                () -> switchTab(Tab.CURRENT));
        drawTab(graphics, possibleTab, "Possible Trades", tab == Tab.POSSIBLE, mouseX, mouseY,
                () -> switchTab(Tab.POSSIBLE));

        Rect content = new Rect(
                layout.x() + 5,
                layout.y() + HEADER_HEIGHT,
                layout.width() - 10,
                layout.height() - HEADER_HEIGHT - FOOTER_HEIGHT
        );
        if (tab == Tab.CURRENT) {
            drawCurrentTab(graphics, content, mouseX, mouseY);
        } else {
            drawPossibleTab(graphics, content, mouseX, mouseY);
        }

        graphics.text(font, "Read-only • no trades are changed", layout.x() + 7, layout.bottom() - 13, MUTED, false);
    }

    private void drawCurrentTab(GuiGraphicsExtractor graphics, Rect content, int mouseX, int mouseY) {
        LecternAssociation association = VisibleLibrarianTrades.lecternManager.getAssociationForMenu(lecternPos);
        KnownLibrarianSnapshot snapshot = association == null
                ? null
                : VisibleLibrarianTrades.enchantmentManager.getOfferSnapshot(association.villagerUuid());
        if (association != null && !association.villagerUuid().equals(refreshRequestedFor)) {
            refreshRequestedFor = association.villagerUuid();
            refreshRequestedAt = minecraft.level == null ? 0L : minecraft.level.getGameTime();
            VisibleLibrarianTrades.enchantmentManager.requestOfferRefresh(association.villagerUuid());
        }
        updateCachedOffers(snapshot);

        int listWidth = Math.max(145, Math.min(218, content.width() / 2));
        Rect list = new Rect(content.x(), content.y(), listWidth, content.height());
        Rect detail = new Rect(list.right() + 5, content.y(), content.right() - list.right() - 5, content.height());
        panel(graphics, list);
        panel(graphics, detail);

        graphics.text(font, "Known unlocked trades", list.x() + 6, list.y() + 5, TEXT, false);
        Rect rows = new Rect(list.x() + 3, list.y() + 18, list.width() - 6, list.height() - 21);
        if (association == null) {
            drawWrapped(graphics, "No associated librarian is known yet.", rows.x() + 5, rows.y() + 5, rows.width() - 10, MUTED);
            drawWrapped(graphics, "The Possible Trades tab remains available.", rows.x() + 5, rows.y() + 29, rows.width() - 10, MUTED);
            drawEmptyCurrentDetail(graphics, detail);
            return;
        }
        if (snapshot == null || cachedOffers.isEmpty()) {
            drawWrapped(graphics, "Waiting for this librarian's current offers…", rows.x() + 5, rows.y() + 5, rows.width() - 10, MUTED);
            drawAssociationNote(graphics, detail, association);
            return;
        }

        int visibleRows = Math.max(1, rows.height() / CURRENT_ROW_HEIGHT);
        currentScroll = clamp(currentScroll, 0, Math.max(0, cachedOffers.size() - visibleRows));
        selectedCurrent = clamp(selectedCurrent, 0, cachedOffers.size() - 1);
        graphics.enableScissor(rows.x(), rows.y(), rows.right(), rows.bottom());
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int offerIndex = currentScroll + visibleIndex;
            if (offerIndex >= cachedOffers.size()) {
                break;
            }
            Rect row = new Rect(rows.x(), rows.y() + visibleIndex * CURRENT_ROW_HEIGHT, rows.width(), CURRENT_ROW_HEIGHT - 2);
            MerchantOffer offer = cachedOffers.get(offerIndex);
            boolean hovered = row.contains(mouseX, mouseY);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(),
                    offerIndex == selectedCurrent ? SELECTED : hovered ? HOVERED : PANEL_DARK);
            graphics.outline(row.x(), row.y(), row.width(), row.height(), offerIndex == selectedCurrent ? ACCENT : BORDER);
            drawOfferFlow(graphics, offer, row.x() + 3, row.y() + 5, mouseX, mouseY, row.width());
            hitTargets.add(new HitTarget(row, () -> selectedCurrent = offerIndex));
        }
        graphics.disableScissor();
        drawScrollbar(graphics, rows, cachedOffers.size(), visibleRows, currentScroll);
        drawCurrentDetail(graphics, detail, cachedOffers.get(selectedCurrent), snapshot, association, mouseX, mouseY);
    }

    private void drawCurrentDetail(
            GuiGraphicsExtractor graphics,
            Rect detail,
            MerchantOffer offer,
            KnownLibrarianSnapshot snapshot,
            LecternAssociation association,
            int mouseX,
            int mouseY
    ) {
        String name = offerName(offer);
        graphics.text(font, trim(name, detail.width() - 12), detail.x() + 6, detail.y() + 6, TEXT, false);
        boolean refreshing = snapshot.receivedGameTime() < refreshRequestedAt;
        graphics.text(font, refreshing ? "Last known current (refreshing…)" : "Current",
                detail.x() + 6, detail.y() + 24, refreshing ? ACCENT : MUTED, false);
        LibrarianMinimumPrice.CurrentCost current = LibrarianMinimumPrice.currentCost(offer);
        drawCost(graphics, current.first(), current.second(), detail.x() + 6, detail.y() + 35, mouseX, mouseY, false);

        graphics.text(font, "Minimum vanilla", detail.x() + 6, detail.y() + 57, MUTED, false);
        LibrarianTradeMode mode = currentMode();
        Optional<LibrarianTradeReference.NaturalCost> minimum = minecraft.level == null
                ? Optional.empty()
                : LibrarianMinimumPrice.minimumNaturalCost(
                        offer,
                        minecraft.level.registryAccess(),
                        mode,
                        snapshot.villagerType()
                );
        if (minimum.isPresent()) {
            drawNaturalCost(graphics, minimum.get(), detail.x() + 6, detail.y() + 68, mouseX, mouseY, false);
        } else {
            graphics.text(font, "Unavailable for this trade", detail.x() + 6, detail.y() + 72, MUTED, false);
        }

        int noteY = detail.bottom() - 30;
        graphics.text(font, association.confidence().description(), detail.x() + 6, noteY, MUTED, false);
        if (VisibleLibrarianTrades.lecternManager.isAssociationAmbiguous(lecternPos)
                || association.confidence() == LecternAssociation.Confidence.FALLBACK) {
            graphics.text(font, "Crowded setup: association is estimated", detail.x() + 6, noteY + 11, ACCENT, false);
        }
    }

    private void drawAssociationNote(GuiGraphicsExtractor graphics, Rect detail, LecternAssociation association) {
        graphics.text(font, "Association", detail.x() + 6, detail.y() + 7, TEXT, false);
        drawWrapped(graphics, association.confidence().description(), detail.x() + 6, detail.y() + 24, detail.width() - 12, MUTED);
        if (VisibleLibrarianTrades.lecternManager.isAssociationAmbiguous(lecternPos)
                || association.confidence() == LecternAssociation.Confidence.FALLBACK) {
            drawWrapped(graphics, "Multiple nearby lecterns can be ambiguous client-side.", detail.x() + 6, detail.y() + 48, detail.width() - 12, ACCENT);
        }
    }

    private void drawEmptyCurrentDetail(GuiGraphicsExtractor graphics, Rect detail) {
        graphics.text(font, "Current Librarian", detail.x() + 6, detail.y() + 7, TEXT, false);
        drawWrapped(graphics, "This lectern stays blank here until VLT learns a librarian and receives its offers.",
                detail.x() + 6, detail.y() + 25, detail.width() - 12, MUTED);
    }

    private void drawPossibleTab(GuiGraphicsExtractor graphics, Rect content, int mouseX, int mouseY) {
        LibrarianTradeMode mode = currentMode();
        int levelY = content.y();
        int levelWidth = Math.max(42, (content.width() - 8) / 5);
        for (int level = 1; level <= 5; level++) {
            int captured = level;
            Rect button = new Rect(content.x() + (level - 1) * (levelWidth + 2), levelY, levelWidth, 17);
            drawSmallButton(graphics, button, shortLevelName(level), selectedProfessionLevel == level && !browsingBooks,
                    mouseX, mouseY, () -> selectLevel(captured));
        }

        int bodyY = content.y() + 21;
        if (mode == LibrarianTradeMode.TRADE_REBALANCE) {
            graphics.text(font, "Trade Rebalance active • biome/variant rules shown", content.x() + 3, bodyY, ACCENT, false);
            bodyY += 12;
        }
        Rect body = new Rect(content.x(), bodyY, content.width(), content.bottom() - bodyY);
        if (browsingBooks) {
            drawBookBrowser(graphics, body, mode, mouseX, mouseY);
        } else {
            drawPossibleGrid(graphics, body, mode, mouseX, mouseY);
        }
    }

    private void drawPossibleGrid(GuiGraphicsExtractor graphics, Rect body, LibrarianTradeMode mode, int mouseX, int mouseY) {
        int gridWidth = Math.max(145, Math.min(218, body.width() / 2));
        Rect grid = new Rect(body.x(), body.y(), gridWidth, body.height());
        Rect detail = new Rect(grid.right() + 5, body.y(), body.right() - grid.right() - 5, body.height());
        panel(graphics, grid);
        panel(graphics, detail);
        graphics.text(font, levelName(selectedProfessionLevel), grid.x() + 6, grid.y() + 5, TEXT, false);

        Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.entity.npc.villager.VillagerType>> type = currentVillagerType();
        List<LibrarianTradeReference> trades = LibrarianTradeCatalog.possibleTradesAtLevel(
                mode, type, selectedProfessionLevel
        );
        int cellSize = 48;
        int columns = Math.max(2, (grid.width() - 8) / cellSize);
        for (int index = 0; index < trades.size(); index++) {
            LibrarianTradeReference trade = trades.get(index);
            int column = index % columns;
            int rowIndex = index / columns;
            Rect cell = new Rect(grid.x() + 5 + column * cellSize, grid.y() + 20 + rowIndex * 48, cellSize - 3, 45);
            boolean selected = trade.equals(selectedPossible);
            boolean hovered = cell.contains(mouseX, mouseY);
            graphics.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), selected ? SELECTED : hovered ? HOVERED : PANEL_DARK);
            graphics.outline(cell.x(), cell.y(), cell.width(), cell.height(), selected ? ACCENT : BORDER);
            ItemStack icon = trade.iconStack();
            int iconX = cell.x() + (cell.width() - 16) / 2;
            drawIcon(graphics, icon, iconX, cell.y() + 4, mouseX, mouseY, false);
            graphics.centeredText(font, trim(itemName(icon), cell.width() - 4), cell.x() + cell.width() / 2, cell.y() + 24, TEXT);
            hitTargets.add(new HitTarget(cell, () -> selectPossible(trade)));
        }
        if (selectedPossible == null && !trades.isEmpty()) {
            selectedPossible = trades.getFirst();
        }
        if (selectedPossible == null) {
            drawWrapped(graphics, "No stock trade is available at this level for the known variant.",
                    detail.x() + 6, detail.y() + 8, detail.width() - 12, MUTED);
        } else {
            drawPossibleDetail(graphics, detail, selectedPossible, mouseX, mouseY);
        }
    }

    private void drawPossibleDetail(
            GuiGraphicsExtractor graphics,
            Rect detail,
            LibrarianTradeReference trade,
            int mouseX,
            int mouseY
    ) {
        ItemStack icon = trade.iconStack();
        graphics.text(font, trim(itemName(icon), detail.width() - 12), detail.x() + 6, detail.y() + 6, TEXT, false);
        String direction = trade.direction() == LibrarianTradeReference.Direction.LIBRARIAN_BUYS
                ? "Librarian buys"
                : "Librarian sells";
        graphics.text(font, direction, detail.x() + 6, detail.y() + 23, MUTED, false);

        if (trade.enchantedBookSelector()) {
            drawIcon(graphics, icon, detail.x() + 6, detail.y() + 36, mouseX, mouseY, false);
            graphics.text(font, "Choose an enchantment and level", detail.x() + 28, detail.y() + 40, TEXT, false);
            Rect open = new Rect(detail.x() + 6, detail.y() + 62, Math.min(150, detail.width() - 12), 19);
            drawSmallButton(graphics, open, "Browse enchanted books", false, mouseX, mouseY, () -> {
                browsingBooks = true;
                bookProfessionLevel = selectedProfessionLevel;
                selectedBook = null;
                bookScroll = 0;
            });
        } else {
            trade.naturalCost().ifPresent(cost ->
                    drawNaturalCost(graphics, cost, detail.x() + 6, detail.y() + 37, mouseX, mouseY, true));
            graphics.text(font, "Receives", detail.x() + 6, detail.y() + 61, MUTED, false);
            ItemStack result = new ItemStack(trade.result().item(), trade.result().count());
            drawIcon(graphics, result, detail.x() + 6, detail.y() + 73, mouseX, mouseY, true);
            graphics.text(font, "Available at: " + levelName(trade.professionLevel()),
                    detail.x() + 6, detail.y() + 98, TEXT, false);
        }
    }

    private void drawBookBrowser(GuiGraphicsExtractor graphics, Rect body, LibrarianTradeMode mode, int mouseX, int mouseY) {
        int listWidth = Math.max(170, Math.min(245, body.width() / 2 + 20));
        Rect listPanel = new Rect(body.x(), body.y(), listWidth, body.height());
        Rect detail = new Rect(listPanel.right() + 5, body.y(), body.right() - listPanel.right() - 5, body.height());
        panel(graphics, listPanel);
        panel(graphics, detail);
        Rect back = new Rect(listPanel.x() + 5, listPanel.y() + 4, 42, 17);
        drawSmallButton(graphics, back, "Back", false, mouseX, mouseY, () -> browsingBooks = false);
        graphics.text(font, levelName(bookProfessionLevel) + " books", listPanel.x() + 53, listPanel.y() + 8, TEXT, false);

        List<LibrarianEnchantedBookReference> books = availableBooks(mode).stream()
                .filter(book -> book.professionLevels().contains(bookProfessionLevel))
                .toList();
        Rect rows = new Rect(listPanel.x() + 3, listPanel.y() + 24, listPanel.width() - 6, listPanel.height() - 27);
        int visibleRows = Math.max(1, rows.height() / BOOK_ROW_HEIGHT);
        bookScroll = clamp(bookScroll, 0, Math.max(0, books.size() - visibleRows));
        graphics.enableScissor(rows.x(), rows.y(), rows.right(), rows.bottom());
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = bookScroll + visible;
            if (index >= books.size()) {
                break;
            }
            LibrarianEnchantedBookReference book = books.get(index);
            Rect row = new Rect(rows.x(), rows.y() + visible * BOOK_ROW_HEIGHT, rows.width(), BOOK_ROW_HEIGHT - 1);
            boolean selected = book.equals(selectedBook);
            boolean hovered = row.contains(mouseX, mouseY);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), selected ? SELECTED : hovered ? HOVERED : PANEL_DARK);
            ItemStack icon = book.iconStack();
            drawIcon(graphics, icon, row.x() + 3, row.y() + 3, mouseX, mouseY, false);
            graphics.text(font, trim(bookName(book), row.width() - 25), row.x() + 23, row.y() + 7, TEXT, false);
            hitTargets.add(new HitTarget(row, () -> selectedBook = book));
        }
        graphics.disableScissor();
        drawScrollbar(graphics, rows, books.size(), visibleRows, bookScroll);

        if (selectedBook == null && !books.isEmpty()) {
            selectedBook = books.getFirst();
        }
        if (selectedBook == null) {
            drawWrapped(graphics, "No enchanted books are available for this level and variant.",
                    detail.x() + 6, detail.y() + 8, detail.width() - 12, MUTED);
            return;
        }
        LibrarianEnchantedBookReference book = selectedBook;
        graphics.text(font, trim(bookName(book), detail.width() - 12), detail.x() + 6, detail.y() + 6, TEXT, false);
        graphics.text(font, "Possible through Librarian: Yes", detail.x() + 6, detail.y() + 24, TEXT, false);
        graphics.text(font, "Minimum vanilla", detail.x() + 6, detail.y() + 42, MUTED, false);
        drawNaturalCost(graphics, book.naturalCost(), detail.x() + 6, detail.y() + 53, mouseX, mouseY, false);
        graphics.text(font, "Natural range: " + rangeText(book.emeraldRange()),
                detail.x() + 6, detail.y() + 77, TEXT, false);
        graphics.text(font, "Available levels: " + professionLevels(book.professionLevels()),
                detail.x() + 6, detail.y() + 92, TEXT, false);
        if (!book.villagerTypes().isEmpty()) {
            String variants = book.villagerTypes().stream()
                    .map(type -> titleCase(type.identifier().getPath()))
                    .sorted()
                    .reduce((first, second) -> first + ", " + second)
                    .orElse("");
            drawWrapped(graphics, "Variant: " + variants, detail.x() + 6, detail.y() + 108, detail.width() - 12, ACCENT);
        }
    }

    private List<LibrarianEnchantedBookReference> availableBooks(LibrarianTradeMode mode) {
        if (minecraft.level == null) {
            return List.of();
        }
        return LibrarianTradeCatalog.enchantedBooks(
                minecraft.level.registryAccess(), mode, currentVillagerType()
        );
    }

    private Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.entity.npc.villager.VillagerType>> currentVillagerType() {
        LecternAssociation association = VisibleLibrarianTrades.lecternManager.getAssociationForMenu(lecternPos);
        if (association == null) {
            return Optional.empty();
        }
        KnownLibrarianSnapshot snapshot = VisibleLibrarianTrades.enchantmentManager.getOfferSnapshot(association.villagerUuid());
        return snapshot == null ? Optional.empty() : snapshot.villagerType();
    }

    private void updateCachedOffers(@Nullable KnownLibrarianSnapshot snapshot) {
        if (snapshot != cachedSnapshot) {
            cachedSnapshot = snapshot;
            cachedOffers = snapshot == null ? new MerchantOffers() : snapshot.offersCopy();
            selectedCurrent = 0;
            currentScroll = 0;
        }
    }

    private void drawOfferFlow(
            GuiGraphicsExtractor graphics,
            MerchantOffer offer,
            int x,
            int y,
            int mouseX,
            int mouseY,
            int availableWidth
    ) {
        ItemStack first = offer.getCostA();
        ItemStack second = offer.getCostB();
        drawIcon(graphics, first, x, y, mouseX, mouseY, true);
        int cursor = x + 19;
        if (!second.isEmpty()) {
            graphics.text(font, "+", cursor, y + 4, TEXT, false);
            cursor += 9;
            drawIcon(graphics, second, cursor, y, mouseX, mouseY, true);
            cursor += 20;
        }
        graphics.text(font, "→", cursor, y + 4, TEXT, false);
        cursor += 12;
        ItemStack result = offer.getResult();
        drawIcon(graphics, result, cursor, y, mouseX, mouseY, true);
        cursor += 20;
        if (availableWidth - (cursor - x) > 35) {
            graphics.text(font, trim(offerName(offer), availableWidth - (cursor - x) - 3), cursor, y + 4, TEXT, false);
        }
    }

    private void drawCost(
            GuiGraphicsExtractor graphics,
            ItemStack first,
            Optional<ItemStack> second,
            int x,
            int y,
            int mouseX,
            int mouseY,
            boolean label
    ) {
        drawIcon(graphics, first, x, y, mouseX, mouseY, true);
        int cursor = x + 22;
        if (second.isPresent() && !second.get().isEmpty()) {
            graphics.text(font, "+", cursor, y + 4, TEXT, false);
            cursor += 10;
            drawIcon(graphics, second.get(), cursor, y, mouseX, mouseY, true);
            cursor += 21;
        }
        if (label) {
            graphics.text(font, "minimum", cursor, y + 4, MUTED, false);
        }
    }

    private void drawNaturalCost(
            GuiGraphicsExtractor graphics,
            LibrarianTradeReference.NaturalCost cost,
            int x,
            int y,
            int mouseX,
            int mouseY,
            boolean showRange
    ) {
        int cursor = x;
        cursor = drawItemRange(graphics, cost.first(), cursor, y, mouseX, mouseY, showRange);
        if (cost.second().isPresent()) {
            graphics.text(font, "+", cursor + 1, y + 4, TEXT, false);
            cursor += 11;
            drawItemRange(graphics, cost.second().get(), cursor, y, mouseX, mouseY, showRange);
        }
    }

    private int drawItemRange(
            GuiGraphicsExtractor graphics,
            LibrarianTradeReference.ItemRange range,
            int x,
            int y,
            int mouseX,
            int mouseY,
            boolean showRange
    ) {
        ItemStack stack = new ItemStack(range.item(), range.count().minimum());
        drawIcon(graphics, stack, x, y, mouseX, mouseY, !showRange || range.count().isFixed());
        int cursor = x + 19;
        if (showRange && !range.count().isFixed()) {
            graphics.text(font, rangeText(range.count()), cursor, y + 4, TEXT, false);
            cursor += font.width(rangeText(range.count())) + 3;
        } else {
            cursor += 3;
        }
        return cursor;
    }

    private void drawIcon(
            GuiGraphicsExtractor graphics,
            ItemStack stack,
            int x,
            int y,
            int mouseX,
            int mouseY,
            boolean showOne
    ) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.fakeItem(stack, x, y);
        graphics.itemDecorations(font, stack, x, y, showOne && stack.getCount() == 1 ? "1" : null);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    private void drawTab(
            GuiGraphicsExtractor graphics,
            Rect rect,
            String label,
            boolean active,
            int mouseX,
            int mouseY,
            Runnable action
    ) {
        boolean hovered = rect.contains(mouseX, mouseY);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), active ? SELECTED : hovered ? HOVERED : PANEL);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), active ? ACCENT : BORDER);
        graphics.centeredText(font, label, rect.x() + rect.width() / 2, rect.y() + 4, TEXT);
        hitTargets.add(new HitTarget(rect, action));
    }

    private void drawSmallButton(
            GuiGraphicsExtractor graphics,
            Rect rect,
            String label,
            boolean active,
            int mouseX,
            int mouseY,
            Runnable action
    ) {
        boolean hovered = rect.contains(mouseX, mouseY);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), active ? SELECTED : hovered ? HOVERED : PANEL_DARK);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), active ? ACCENT : BORDER);
        graphics.centeredText(font, trim(label, rect.width() - 4), rect.x() + rect.width() / 2, rect.y() + 4, TEXT);
        hitTargets.add(new HitTarget(rect, action));
    }

    private void panel(GuiGraphicsExtractor graphics, Rect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), BORDER);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, Rect area, int total, int visible, int first) {
        if (total <= visible || visible <= 0) {
            return;
        }
        int trackX = area.right() - 3;
        graphics.fill(trackX, area.y(), trackX + 2, area.bottom(), 0xFF333333);
        int thumbHeight = Math.max(10, area.height() * visible / total);
        int travel = area.height() - thumbHeight;
        int thumbY = area.y() + travel * first / Math.max(1, total - visible);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ACCENT);
    }

    private void drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        graphics.textWithWordWrap(font, Component.literal(text), x, y, Math.max(20, width), color, false);
    }

    private void switchTab(Tab newTab) {
        tab = newTab;
        browsingBooks = false;
    }

    private void selectLevel(int level) {
        selectedProfessionLevel = level;
        selectedPossible = null;
        selectedBook = null;
        browsingBooks = false;
    }

    private void selectPossible(LibrarianTradeReference trade) {
        selectedPossible = trade;
        if (trade.enchantedBookSelector()) {
            browsingBooks = true;
            bookProfessionLevel = trade.professionLevel();
            selectedBook = null;
            bookScroll = 0;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (HitTarget target : List.copyOf(hitTargets)) {
                if (target.bounds().contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int direction = scrollY > 0.0 ? -1 : 1;
        if (tab == Tab.CURRENT) {
            currentScroll = Math.max(0, currentScroll + direction);
            return true;
        }
        if (browsingBooks) {
            bookScroll = Math.max(0, bookScroll + direction * 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    private LibrarianTradeMode currentMode() {
        return minecraft.level != null
                ? LibrarianTradeMode.fromEnabledFeatures(minecraft.level.enabledFeatures())
                : LibrarianTradeMode.STANDARD;
    }

    private Layout layout() {
        int panelWidth = Math.min(480, Math.max(300, width - 20));
        int panelHeight = Math.min(286, Math.max(190, height - 20));
        panelWidth = Math.min(panelWidth, width - 4);
        panelHeight = Math.min(panelHeight, height - 4);
        return new Layout((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private static String offerName(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        if (result.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
            var stored = result.getOrDefault(
                    net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY
            );
            if (!stored.isEmpty()) {
                var enchantment = stored.keySet().iterator().next();
                return Enchantment.getFullname(enchantment, stored.getLevel(enchantment)).getString();
            }
        }
        return itemName(result);
    }

    private static String bookName(LibrarianEnchantedBookReference book) {
        return Enchantment.getFullname(book.enchantment(), book.enchantmentLevel()).getString();
    }

    private static String itemName(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    private static String rangeText(LibrarianTradeReference.CountRange range) {
        return range.isFixed() ? Integer.toString(range.minimum()) : range.minimum() + "–" + range.maximum();
    }

    private static String professionLevels(List<Integer> levels) {
        return levels.stream().map(LibrarianInfoScreen::shortLevelName).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String levelName(int level) {
        return switch (level) {
            case 1 -> "Novice";
            case 2 -> "Apprentice";
            case 3 -> "Journeyman";
            case 4 -> "Expert";
            case 5 -> "Master";
            default -> "Level " + level;
        };
    }

    private static String shortLevelName(int level) {
        return switch (level) {
            case 1 -> "Novice";
            case 2 -> "Apprent.";
            case 3 -> "Journey.";
            case 4 -> "Expert";
            case 5 -> "Master";
            default -> Integer.toString(level);
        };
    }

    private static String titleCase(String value) {
        String spaced = value.replace('_', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum Tab {
        CURRENT,
        POSSIBLE
    }

    private record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }

    private record Layout(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    private record HitTarget(Rect bounds, Runnable action) {
    }
}
