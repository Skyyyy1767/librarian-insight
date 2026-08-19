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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/** Icon-first, read-only librarian reference browser for an empty lectern. */
public final class LibrarianInfoScreen extends Screen {
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 17;
    private static final int CURRENT_ROW_HEIGHT = 43;
    private static final int BOOK_ROW_HEIGHT = 31;
    private static final float MIN_TEXT_SCALE = 0.75F;

    private final BlockPos lecternPos;
    private final List<HitTarget> hitTargets = new ArrayList<>();
    private LibrarianMenuPalette palette = LibrarianMenuPalette.LIGHT;
    private Tab tab = Tab.CURRENT;
    private int selectedCurrent;
    private int currentScroll;
    private int selectedProfessionLevel = 1;
    private @Nullable LibrarianTradeReference selectedPossible;
    private @Nullable LibrarianEnchantedBookReference selectedBook;
    private boolean browsingBooks;
    private int bookProfessionLevel = 1;
    private int bookScroll;
    private int currentDetailScroll;
    private int possibleDetailScroll;
    private int possibleGridScroll;
    private int currentDetailMaxScroll;
    private int possibleDetailMaxScroll;
    private int possibleGridMaxScroll;
    private @Nullable Rect activeDetailBounds;
    private @Nullable Rect activePossibleGridBounds;
    private @Nullable Rect activeClickClip;
    private @Nullable Rect activeTooltipClip;
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
        palette = LibrarianMenuPalette.forTheme(VisibleLibrarianTrades.priceDisplay.getMenuTheme());
        hitTargets.clear();
        activeDetailBounds = null;
        activePossibleGridBounds = null;
        activeClickClip = null;
        activeTooltipClip = null;
        currentDetailMaxScroll = 0;
        possibleDetailMaxScroll = 0;
        possibleGridMaxScroll = 0;

        Layout layout = layout();
        graphics.fill(layout.x(), layout.y(), layout.right(), layout.bottom(), palette.screenBackground());
        graphics.outline(layout.x(), layout.y(), layout.width(), layout.height(), palette.border());
        graphics.enableScissor(layout.x() + 1, layout.y() + 1, layout.right() - 1, layout.bottom() - 1);
        drawFittedText(graphics, title.getString(),
                new Rect(layout.x() + 5, layout.y() + 3, layout.width() - 10, 15),
                1, MIN_TEXT_SCALE, TextAlignment.CENTER, true, palette.headingText());

        int tabY = layout.y() + 21;
        int tabWidth = Math.max(1, (layout.width() - 12) / 2);
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

        drawFittedText(graphics, "Read-only • no trades are changed",
                new Rect(layout.x() + 7, layout.bottom() - 14, layout.width() - 14, 11),
                1, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.secondaryText());
        graphics.disableScissor();
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

        int listWidth = Math.max(1, Math.min(218, (content.width() - 5) / 2));
        Rect list = new Rect(content.x(), content.y(), listWidth, content.height());
        Rect detail = new Rect(list.right() + 5, content.y(), content.right() - list.right() - 5, content.height());
        panel(graphics, list);
        panel(graphics, detail);

        drawFittedText(graphics, "Known unlocked trades",
                new Rect(list.x() + 6, list.y() + 3, list.width() - 12, 13),
                1, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headingText());
        Rect rows = new Rect(list.x() + 3, list.y() + 18, list.width() - 6, list.height() - 21);
        if (association == null) {
            drawFittedText(graphics, "No associated librarian is known yet.",
                    new Rect(rows.x() + 5, rows.y() + 4, rows.width() - 10, 30),
                    3, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.secondaryText());
            int secondMessageY = rows.y() + 37;
            if (secondMessageY < rows.bottom()) {
                drawFittedText(graphics, "The Possible Trades tab remains available.",
                        new Rect(rows.x() + 5, secondMessageY, rows.width() - 10, rows.bottom() - secondMessageY),
                        4, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.secondaryText());
            }
            drawEmptyCurrentDetail(graphics, detail);
            return;
        }
        if (snapshot == null || cachedOffers.isEmpty()) {
            int waitingY = rows.y() + 4;
            if (waitingY < rows.bottom()) {
                drawFittedText(graphics, "Waiting for this librarian's current offers…",
                        new Rect(rows.x() + 5, waitingY, rows.width() - 10, rows.bottom() - waitingY),
                        4, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.secondaryText());
            }
            drawAssociationNote(graphics, detail, association);
            return;
        }

        int rowHeight = currentRowHeight(rows.width());
        int visibleRows = Math.max(1, rows.height() / rowHeight);
        currentScroll = clamp(currentScroll, 0, Math.max(0, cachedOffers.size() - visibleRows));
        selectedCurrent = clamp(selectedCurrent, 0, cachedOffers.size() - 1);
        graphics.enableScissor(rows.x(), rows.y(), rows.right(), rows.bottom());
        activeTooltipClip = rows;
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int offerIndex = currentScroll + visibleIndex;
            if (offerIndex >= cachedOffers.size()) {
                break;
            }
            Rect row = new Rect(rows.x(), rows.y() + visibleIndex * rowHeight, rows.width(), rowHeight - 2);
            MerchantOffer offer = cachedOffers.get(offerIndex);
            Rect visibleRow = intersection(row, rows);
            boolean hovered = visibleRow != null && visibleRow.contains(mouseX, mouseY);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(),
                    offerIndex == selectedCurrent ? palette.selected() : hovered ? palette.hovered() : palette.clickableBox());
            graphics.outline(row.x(), row.y(), row.width(), row.height(),
                    offerIndex == selectedCurrent ? palette.selectedBorder() : palette.separator());
            drawOfferFlow(graphics, offer, row.x() + 3, row.y() + 5, mouseX, mouseY, row.width(), row.height());
            if (visibleRow != null) {
                hitTargets.add(new HitTarget(visibleRow, () -> {
                    selectedCurrent = offerIndex;
                    currentDetailScroll = 0;
                }));
            }
        }
        activeTooltipClip = null;
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
        Rect viewport = inset(detail, 5);
        activeDetailBounds = viewport;
        int textWidth = Math.max(12, viewport.width() - 2);
        TextLayout nameLayout = planText(offerName(offer), textWidth, 3, MIN_TEXT_SCALE);
        boolean refreshing = snapshot.receivedGameTime() < refreshRequestedAt;
        TextLayout currentLabel = planText(
                refreshing ? "Last known current (refreshing…)" : "Current",
                textWidth, 3, MIN_TEXT_SCALE
        );
        TextLayout minimumLabel = planText("Minimum vanilla", textWidth, 2, MIN_TEXT_SCALE);
        LibrarianMinimumPrice.CurrentCost current = LibrarianMinimumPrice.currentCost(offer);
        LibrarianTradeMode mode = currentMode();
        Optional<LibrarianTradeReference.NaturalCost> minimum = minecraft.level == null
                ? Optional.empty()
                : LibrarianMinimumPrice.minimumNaturalCost(
                        offer,
                        minecraft.level.registryAccess(),
                        mode,
                        snapshot.villagerType()
                );
        TextLayout unavailable = minimum.isEmpty()
                ? planText("Unavailable for this trade", textWidth, 3, MIN_TEXT_SCALE)
                : TextLayout.EMPTY;
        TextLayout confidence = planText(association.confidence().description(), textWidth, 3, MIN_TEXT_SCALE);
        boolean ambiguous = VisibleLibrarianTrades.lecternManager.isAssociationAmbiguous(lecternPos)
                || association.confidence() == LecternAssociation.Confidence.FALLBACK;
        TextLayout ambiguity = ambiguous
                ? planText("Crowded setup: association is estimated", textWidth, 4, MIN_TEXT_SCALE)
                : TextLayout.EMPTY;

        int contentHeight = 4 + nameLayout.height() + 3 + currentLabel.height() + 2 + 16
                + 5 + minimumLabel.height() + 2 + (minimum.isPresent() ? 16 : unavailable.height())
                + 8 + confidence.height() + (ambiguous ? 3 + ambiguity.height() : 0) + 4;
        currentDetailMaxScroll = Math.max(0, contentHeight - viewport.height());
        currentDetailScroll = clamp(currentDetailScroll, 0, currentDetailMaxScroll);

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        activeTooltipClip = viewport;
        int y = viewport.y() + 4 - currentDetailScroll;
        drawTextLayout(graphics, nameLayout, new Rect(viewport.x(), y, textWidth, nameLayout.height()),
                TextAlignment.LEFT, false, palette.primaryText());
        y += nameLayout.height() + 3;
        drawTextLayout(graphics, currentLabel, new Rect(viewport.x(), y, textWidth, currentLabel.height()),
                TextAlignment.LEFT, false, refreshing ? palette.statusText() : palette.secondaryText());
        y += currentLabel.height() + 2;
        drawCost(graphics, current.first(), current.second(), viewport.x(), y, mouseX, mouseY, false);
        y += 21;
        drawTextLayout(graphics, minimumLabel, new Rect(viewport.x(), y, textWidth, minimumLabel.height()),
                TextAlignment.LEFT, false, palette.secondaryText());
        y += minimumLabel.height() + 2;
        if (minimum.isPresent()) {
            drawNaturalCost(graphics, minimum.get(), viewport.x(), y, mouseX, mouseY, false);
            y += 16;
        } else {
            drawTextLayout(graphics, unavailable, new Rect(viewport.x(), y, textWidth, unavailable.height()),
                    TextAlignment.LEFT, false, palette.secondaryText());
            y += unavailable.height();
        }
        y += 8;
        drawTextLayout(graphics, confidence, new Rect(viewport.x(), y, textWidth, confidence.height()),
                TextAlignment.LEFT, false, palette.associationText());
        y += confidence.height();
        if (ambiguous) {
            y += 3;
            drawTextLayout(graphics, ambiguity, new Rect(viewport.x(), y, textWidth, ambiguity.height()),
                    TextAlignment.LEFT, false, palette.statusText());
        }
        activeTooltipClip = null;
        graphics.disableScissor();
        drawPixelScrollbar(graphics, viewport, contentHeight, currentDetailScroll);
    }

    private void drawAssociationNote(GuiGraphicsExtractor graphics, Rect detail, LecternAssociation association) {
        Rect inner = inset(detail, 6);
        drawFittedText(graphics, "Association", new Rect(inner.x(), inner.y(), inner.width(), 13),
                1, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headingText());
        drawFittedText(graphics, association.confidence().description(),
                new Rect(inner.x(), inner.y() + 18, inner.width(), 27),
                3, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.associationText());
        if (VisibleLibrarianTrades.lecternManager.isAssociationAmbiguous(lecternPos)
                || association.confidence() == LecternAssociation.Confidence.FALLBACK) {
            int warningY = inner.y() + 49;
            if (warningY < inner.bottom()) {
                drawFittedText(graphics, "Multiple nearby lecterns can be ambiguous client-side.",
                        new Rect(inner.x(), warningY, inner.width(), inner.bottom() - warningY),
                        5, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.statusText());
            }
        }
    }

    private void drawEmptyCurrentDetail(GuiGraphicsExtractor graphics, Rect detail) {
        Rect inner = inset(detail, 6);
        drawFittedText(graphics, "Current Librarian", new Rect(inner.x(), inner.y(), inner.width(), 18),
                2, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.headingText());
        int messageY = inner.y() + 23;
        if (messageY < inner.bottom()) {
            drawFittedText(graphics, "This lectern stays blank here until VLT learns a librarian and receives its offers.",
                    new Rect(inner.x(), messageY, inner.width(), inner.bottom() - messageY),
                    7, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.secondaryText());
        }
    }

    private void drawPossibleTab(GuiGraphicsExtractor graphics, Rect content, int mouseX, int mouseY) {
        LibrarianTradeMode mode = currentMode();
        int levelY = content.y();
        int levelWidth = Math.max(1, (content.width() - 8) / 5);
        int levelHeight = 23;
        for (int level = 1; level <= 5; level++) {
            int captured = level;
            Rect button = new Rect(content.x() + (level - 1) * (levelWidth + 2), levelY, levelWidth, levelHeight);
            drawSmallButton(graphics, button, levelName(level), selectedProfessionLevel == level && !browsingBooks,
                    mouseX, mouseY, () -> selectLevel(captured));
        }

        int bodyY = content.y() + levelHeight + 4;
        if (mode == LibrarianTradeMode.TRADE_REBALANCE) {
            TextLayout banner = planText(
                    "Trade Rebalance active • biome/variant rules shown",
                    Math.max(20, content.width() - 6), 2, MIN_TEXT_SCALE
            );
            drawTextLayout(graphics, banner,
                    new Rect(content.x() + 3, bodyY, content.width() - 6, banner.height()),
                    TextAlignment.LEFT, false, palette.statusText());
            bodyY += banner.height() + 3;
        }
        Rect body = new Rect(content.x(), bodyY, content.width(), Math.max(1, content.bottom() - bodyY));
        if (browsingBooks) {
            drawBookBrowser(graphics, body, mode, mouseX, mouseY);
        } else {
            drawPossibleGrid(graphics, body, mode, mouseX, mouseY);
        }
    }

    private void drawPossibleGrid(GuiGraphicsExtractor graphics, Rect body, LibrarianTradeMode mode, int mouseX, int mouseY) {
        int gridWidth = Math.max(1, Math.min(218, (body.width() - 5) / 2));
        Rect grid = new Rect(body.x(), body.y(), gridWidth, body.height());
        Rect detail = new Rect(grid.right() + 5, body.y(), body.right() - grid.right() - 5, body.height());
        panel(graphics, grid);
        panel(graphics, detail);
        drawFittedText(graphics, levelName(selectedProfessionLevel),
                new Rect(grid.x() + 6, grid.y() + 3, grid.width() - 12, 14),
                1, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headingText());

        Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.entity.npc.villager.VillagerType>> type = currentVillagerType();
        List<LibrarianTradeReference> trades = LibrarianTradeCatalog.possibleTradesAtLevel(
                mode, type, selectedProfessionLevel
        );
        Rect gridRows = new Rect(grid.x() + 3, grid.y() + 19, grid.width() - 6, Math.max(1, grid.height() - 22));
        int columns = 2;
        int cellGap = 4;
        int availableGridWidth = gridRows.width();
        int cellWidth = Math.max(1, (availableGridWidth - cellGap) / columns);
        int cellHeight = possibleCellHeight(trades, cellWidth);
        int rowCount = (trades.size() + columns - 1) / columns;
        int gridContentHeight = rowCount * (cellHeight + cellGap) - (rowCount == 0 ? 0 : cellGap);
        if (gridContentHeight > gridRows.height()) {
            availableGridWidth = Math.max(1, gridRows.width() - 3);
            cellWidth = Math.max(1, (availableGridWidth - cellGap) / columns);
            cellHeight = possibleCellHeight(trades, cellWidth);
            gridContentHeight = rowCount * (cellHeight + cellGap) - (rowCount == 0 ? 0 : cellGap);
        }
        possibleGridMaxScroll = Math.max(0, gridContentHeight - gridRows.height());
        possibleGridScroll = clamp(possibleGridScroll, 0, possibleGridMaxScroll);
        activePossibleGridBounds = gridRows;
        graphics.enableScissor(gridRows.x(), gridRows.y(), gridRows.right(), gridRows.bottom());
        activeTooltipClip = gridRows;
        for (int index = 0; index < trades.size(); index++) {
            LibrarianTradeReference trade = trades.get(index);
            int column = index % columns;
            int rowIndex = index / columns;
            Rect cell = new Rect(
                    gridRows.x() + column * (cellWidth + cellGap),
                    gridRows.y() + rowIndex * (cellHeight + cellGap) - possibleGridScroll,
                    cellWidth,
                    cellHeight
            );
            boolean selected = trade.equals(selectedPossible);
            Rect visibleCell = intersection(cell, gridRows);
            boolean hovered = visibleCell != null && visibleCell.contains(mouseX, mouseY);
            graphics.fill(cell.x(), cell.y(), cell.right(), cell.bottom(),
                    selected ? palette.selected() : hovered ? palette.hovered() : palette.clickableBox());
            graphics.outline(cell.x(), cell.y(), cell.width(), cell.height(),
                    selected ? palette.selectedBorder() : palette.separator());
            ItemStack icon = trade.iconStack();
            int iconX = cell.x() + (cell.width() - 16) / 2;
            drawIcon(graphics, icon, iconX, cell.y() + 4, mouseX, mouseY, false);
            drawFittedText(graphics, itemName(icon),
                    new Rect(cell.x() + 2, cell.y() + 23, cell.width() - 4, cell.height() - 25),
                    2, MIN_TEXT_SCALE, TextAlignment.CENTER, true, palette.primaryText());
            if (visibleCell != null) {
                hitTargets.add(new HitTarget(visibleCell, () -> selectPossible(trade)));
            }
        }
        activeTooltipClip = null;
        graphics.disableScissor();
        drawPixelScrollbar(graphics, gridRows, gridContentHeight, possibleGridScroll);
        if (selectedPossible == null && !trades.isEmpty()) {
            selectedPossible = trades.getFirst();
        }
        if (selectedPossible == null) {
            Rect inner = inset(detail, 6);
            drawFittedText(graphics, "No stock trade is available at this level for the known variant.",
                    inner, 8, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.secondaryText());
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
        Rect viewport = inset(detail, 5);
        activeDetailBounds = viewport;
        int textWidth = Math.max(12, viewport.width() - 2);
        ItemStack icon = trade.iconStack();
        TextLayout titleLayout = planText(itemName(icon), textWidth, 3, MIN_TEXT_SCALE);
        String direction = trade.direction() == LibrarianTradeReference.Direction.LIBRARIAN_BUYS
                ? "Librarian buys"
                : "Librarian sells";
        TextLayout directionLayout = planText(direction, textWidth, 2, MIN_TEXT_SCALE);

        if (trade.enchantedBookSelector()) {
            int promptWidth = Math.max(12, textWidth - 22);
            TextLayout prompt = planText("Choose an enchantment and level", promptWidth, 4, MIN_TEXT_SCALE);
            int promptBandHeight = Math.max(16, prompt.height());
            int buttonHeight = 25;
            int contentHeight = 4 + titleLayout.height() + 3 + directionLayout.height() + 5
                    + promptBandHeight + 6 + buttonHeight + 4;
            possibleDetailMaxScroll = Math.max(0, contentHeight - viewport.height());
            possibleDetailScroll = clamp(possibleDetailScroll, 0, possibleDetailMaxScroll);

            graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
            activeTooltipClip = viewport;
            activeClickClip = viewport;
            int y = viewport.y() + 4 - possibleDetailScroll;
            drawTextLayout(graphics, titleLayout, new Rect(viewport.x(), y, textWidth, titleLayout.height()),
                    TextAlignment.LEFT, false, palette.primaryText());
            y += titleLayout.height() + 3;
            drawTextLayout(graphics, directionLayout, new Rect(viewport.x(), y, textWidth, directionLayout.height()),
                    TextAlignment.LEFT, false, palette.secondaryText());
            y += directionLayout.height() + 5;
            drawIcon(graphics, icon, viewport.x(), y, mouseX, mouseY, false);
            drawTextLayout(graphics, prompt,
                    new Rect(viewport.x() + 22, y, promptWidth, promptBandHeight),
                    TextAlignment.LEFT, true, palette.primaryText());
            y += promptBandHeight + 6;
            Rect open = new Rect(viewport.x(), y, Math.max(1, Math.min(154, viewport.width())), buttonHeight);
            drawSmallButton(graphics, open, "Browse enchanted books", false, mouseX, mouseY, () -> {
                browsingBooks = true;
                bookProfessionLevel = selectedProfessionLevel;
                selectedBook = null;
                bookScroll = 0;
                possibleDetailScroll = 0;
            });
            activeClickClip = null;
            activeTooltipClip = null;
            graphics.disableScissor();
            drawPixelScrollbar(graphics, viewport, contentHeight, possibleDetailScroll);
        } else {
            TextLayout receives = planText("Receives", textWidth, 2, MIN_TEXT_SCALE);
            TextLayout available = planText(
                    "Available at: " + levelName(trade.professionLevel()), textWidth, 3, MIN_TEXT_SCALE
            );
            int contentHeight = 4 + titleLayout.height() + 3 + directionLayout.height() + 6
                    + 16 + 7 + receives.height() + 2 + 16 + 6 + available.height() + 4;
            possibleDetailMaxScroll = Math.max(0, contentHeight - viewport.height());
            possibleDetailScroll = clamp(possibleDetailScroll, 0, possibleDetailMaxScroll);

            graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
            activeTooltipClip = viewport;
            int y = viewport.y() + 4 - possibleDetailScroll;
            drawTextLayout(graphics, titleLayout, new Rect(viewport.x(), y, textWidth, titleLayout.height()),
                    TextAlignment.LEFT, false, palette.primaryText());
            y += titleLayout.height() + 3;
            drawTextLayout(graphics, directionLayout, new Rect(viewport.x(), y, textWidth, directionLayout.height()),
                    TextAlignment.LEFT, false, palette.secondaryText());
            y += directionLayout.height() + 6;
            int costY = y;
            trade.naturalCost().ifPresent(cost ->
                    drawNaturalCost(graphics, cost, viewport.x(), costY, mouseX, mouseY, true));
            y += 23;
            drawTextLayout(graphics, receives, new Rect(viewport.x(), y, textWidth, receives.height()),
                    TextAlignment.LEFT, false, palette.secondaryText());
            y += receives.height() + 2;
            ItemStack result = new ItemStack(trade.result().item(), trade.result().count());
            drawIcon(graphics, result, viewport.x(), y, mouseX, mouseY, true);
            y += 22;
            drawTextLayout(graphics, available, new Rect(viewport.x(), y, textWidth, available.height()),
                    TextAlignment.LEFT, false, palette.primaryText());
            activeTooltipClip = null;
            graphics.disableScissor();
            drawPixelScrollbar(graphics, viewport, contentHeight, possibleDetailScroll);
        }
    }

    private void drawBookBrowser(GuiGraphicsExtractor graphics, Rect body, LibrarianTradeMode mode, int mouseX, int mouseY) {
        int listWidth = Math.max(1, Math.min(245, (body.width() - 5) / 2));
        Rect listPanel = new Rect(body.x(), body.y(), listWidth, body.height());
        Rect detail = new Rect(listPanel.right() + 5, body.y(), body.right() - listPanel.right() - 5, body.height());
        panel(graphics, listPanel);
        panel(graphics, detail);
        Rect back = new Rect(listPanel.x() + 5, listPanel.y() + 4, 42, 17);
        drawSmallButton(graphics, back, "Back", false, mouseX, mouseY, () -> browsingBooks = false);
        drawFittedText(graphics, levelName(bookProfessionLevel) + " books",
                new Rect(listPanel.x() + 53, listPanel.y() + 3, Math.max(1, listPanel.width() - 59), 19),
                2, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headingText());

        List<LibrarianEnchantedBookReference> books = availableBooks(mode).stream()
                .filter(book -> book.professionLevels().contains(bookProfessionLevel))
                .toList();
        Rect rows = new Rect(listPanel.x() + 3, listPanel.y() + 24, listPanel.width() - 6, listPanel.height() - 27);
        int rowHeight = bookRowHeight(books, rows.width());
        int visibleRows = Math.max(1, rows.height() / rowHeight);
        bookScroll = clamp(bookScroll, 0, Math.max(0, books.size() - visibleRows));
        graphics.enableScissor(rows.x(), rows.y(), rows.right(), rows.bottom());
        activeTooltipClip = rows;
        for (int visible = 0; visible < visibleRows; visible++) {
            int index = bookScroll + visible;
            if (index >= books.size()) {
                break;
            }
            LibrarianEnchantedBookReference book = books.get(index);
            Rect row = new Rect(rows.x(), rows.y() + visible * rowHeight, rows.width(), rowHeight - 1);
            Rect visibleRow = intersection(row, rows);
            boolean selected = book.equals(selectedBook);
            boolean hovered = visibleRow != null && visibleRow.contains(mouseX, mouseY);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(),
                    selected ? palette.selected() : hovered ? palette.hovered() : palette.clickableBox());
            ItemStack icon = book.iconStack();
            drawIcon(graphics, icon, row.x() + 3, row.y() + (row.height() - 16) / 2, mouseX, mouseY, false);
            drawFittedText(graphics, bookName(book),
                    new Rect(row.x() + 23, row.y() + 2, Math.max(10, row.width() - 27), row.height() - 4),
                    2, MIN_TEXT_SCALE, TextAlignment.CENTER, true, palette.primaryText());
            if (visibleRow != null) {
                hitTargets.add(new HitTarget(visibleRow, () -> {
                    selectedBook = book;
                    possibleDetailScroll = 0;
                }));
            }
        }
        activeTooltipClip = null;
        graphics.disableScissor();
        drawScrollbar(graphics, rows, books.size(), visibleRows, bookScroll);

        if (selectedBook == null && !books.isEmpty()) {
            selectedBook = books.getFirst();
        }
        if (selectedBook == null) {
            Rect inner = inset(detail, 6);
            drawFittedText(graphics, "No enchanted books are available for this level and variant.",
                    inner, 8, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.secondaryText());
            return;
        }
        LibrarianEnchantedBookReference book = selectedBook;
        Rect viewport = inset(detail, 5);
        activeDetailBounds = viewport;
        int textWidth = Math.max(12, viewport.width() - 2);
        TextLayout titleLayout = planText(bookName(book), textWidth, 4, MIN_TEXT_SCALE);
        TextLayout possible = planText("Possible through Librarian: Yes", textWidth, 4, MIN_TEXT_SCALE);
        TextLayout minimum = planText("Minimum vanilla", textWidth, 2, MIN_TEXT_SCALE);
        TextLayout naturalRange = planText(
                "Natural range: " + rangeText(book.emeraldRange()), textWidth, 3, MIN_TEXT_SCALE
        );
        TextLayout availableLevels = planText(
                "Available levels: " + professionLevels(book.professionLevels()), textWidth, 6, MIN_TEXT_SCALE
        );
        TextLayout variant = TextLayout.EMPTY;
        if (!book.villagerTypes().isEmpty()) {
            String variants = book.villagerTypes().stream()
                    .map(type -> titleCase(type.identifier().getPath()))
                    .sorted()
                    .reduce((first, second) -> first + ", " + second)
                    .orElse("");
            variant = planText("Variant: " + variants, textWidth, 7, MIN_TEXT_SCALE);
        }
        int contentHeight = 4 + titleLayout.height() + 3 + possible.height() + 5 + minimum.height()
                + 2 + 16 + 6 + naturalRange.height() + 3 + availableLevels.height()
                + (variant.isEmpty() ? 0 : 3 + variant.height()) + 4;
        possibleDetailMaxScroll = Math.max(0, contentHeight - viewport.height());
        possibleDetailScroll = clamp(possibleDetailScroll, 0, possibleDetailMaxScroll);

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        activeTooltipClip = viewport;
        int y = viewport.y() + 4 - possibleDetailScroll;
        drawTextLayout(graphics, titleLayout, new Rect(viewport.x(), y, textWidth, titleLayout.height()),
                TextAlignment.LEFT, false, palette.primaryText());
        y += titleLayout.height() + 3;
        drawTextLayout(graphics, possible, new Rect(viewport.x(), y, textWidth, possible.height()),
                TextAlignment.LEFT, false, palette.primaryText());
        y += possible.height() + 5;
        drawTextLayout(graphics, minimum, new Rect(viewport.x(), y, textWidth, minimum.height()),
                TextAlignment.LEFT, false, palette.secondaryText());
        y += minimum.height() + 2;
        drawNaturalCost(graphics, book.naturalCost(), viewport.x(), y, mouseX, mouseY, false);
        y += 22;
        drawTextLayout(graphics, naturalRange, new Rect(viewport.x(), y, textWidth, naturalRange.height()),
                TextAlignment.LEFT, false, palette.primaryText());
        y += naturalRange.height() + 3;
        drawTextLayout(graphics, availableLevels, new Rect(viewport.x(), y, textWidth, availableLevels.height()),
                TextAlignment.LEFT, false, palette.primaryText());
        y += availableLevels.height();
        if (!variant.isEmpty()) {
            y += 3;
            drawTextLayout(graphics, variant, new Rect(viewport.x(), y, textWidth, variant.height()),
                    TextAlignment.LEFT, false, palette.statusText());
        }
        activeTooltipClip = null;
        graphics.disableScissor();
        drawPixelScrollbar(graphics, viewport, contentHeight, possibleDetailScroll);
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
            currentDetailScroll = 0;
        }
    }

    private void drawOfferFlow(
            GuiGraphicsExtractor graphics,
            MerchantOffer offer,
            int x,
            int y,
            int mouseX,
            int mouseY,
            int availableWidth,
            int availableHeight
    ) {
        ItemStack first = offer.getCostA();
        ItemStack second = offer.getCostB();
        int innerWidth = Math.max(20, availableWidth - 6);
        int flowWidth = second.isEmpty() ? 44 : 73;
        int cursor = x + Math.max(0, (innerWidth - flowWidth) / 2);
        drawIcon(graphics, first, cursor, y, mouseX, mouseY, true);
        cursor += 19;
        if (!second.isEmpty()) {
            graphics.text(font, "+", cursor, y + 4, palette.primaryText(), false);
            cursor += 9;
            drawIcon(graphics, second, cursor, y, mouseX, mouseY, true);
            cursor += 20;
        }
        graphics.text(font, "→", cursor, y + 4, palette.primaryText(), false);
        cursor += 12;
        ItemStack result = offer.getResult();
        drawIcon(graphics, result, cursor, y, mouseX, mouseY, true);
        drawFittedText(graphics, offerName(offer),
                new Rect(x, y + 19, innerWidth, Math.max(1, availableHeight - 25)),
                2, MIN_TEXT_SCALE, TextAlignment.CENTER, true, palette.primaryText());
    }

    private int currentRowHeight(int rowWidth) {
        int labelWidth = Math.max(20, rowWidth - 6);
        int requiredHeight = CURRENT_ROW_HEIGHT;
        for (MerchantOffer offer : cachedOffers) {
            TextLayout label = planText(offerName(offer), labelWidth, 2, MIN_TEXT_SCALE);
            requiredHeight = Math.max(requiredHeight, 27 + label.height());
        }
        return requiredHeight;
    }

    private int possibleCellHeight(List<LibrarianTradeReference> trades, int cellWidth) {
        int labelWidth = Math.max(1, cellWidth - 4);
        int requiredHeight = 48;
        for (LibrarianTradeReference trade : trades) {
            TextLayout label = planText(itemName(trade.iconStack()), labelWidth, 2, MIN_TEXT_SCALE);
            requiredHeight = Math.max(requiredHeight, 25 + label.height());
        }
        return requiredHeight;
    }

    private int bookRowHeight(List<LibrarianEnchantedBookReference> books, int rowWidth) {
        int labelWidth = Math.max(10, rowWidth - 27);
        int requiredHeight = BOOK_ROW_HEIGHT;
        for (LibrarianEnchantedBookReference book : books) {
            TextLayout label = planText(bookName(book), labelWidth, 2, MIN_TEXT_SCALE);
            requiredHeight = Math.max(requiredHeight, 5 + label.height());
        }
        return requiredHeight;
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
            graphics.text(font, "+", cursor, y + 4, palette.primaryText(), false);
            cursor += 10;
            drawIcon(graphics, second.get(), cursor, y, mouseX, mouseY, true);
            cursor += 21;
        }
        if (label) {
            graphics.text(font, "minimum", cursor, y + 4, palette.secondaryText(), false);
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
            graphics.text(font, "+", cursor + 1, y + 4, palette.primaryText(), false);
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
            graphics.text(font, rangeText(range.count()), cursor, y + 4, palette.primaryText(), false);
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
        if ((activeTooltipClip == null || activeTooltipClip.contains(mouseX, mouseY))
                && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
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
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                active ? palette.selected() : hovered ? palette.hovered() : palette.panel());
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(),
                active ? palette.selectedBorder() : palette.border());
        drawFittedText(graphics, label, inset(rect, 1),
                2, MIN_TEXT_SCALE, TextAlignment.CENTER, true, palette.primaryText());
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
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                active ? palette.selected() : hovered ? palette.hovered() : palette.clickableBox());
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(),
                active ? palette.selectedBorder() : palette.border());
        drawFittedText(graphics, label, inset(rect, 2),
                2, MIN_TEXT_SCALE, TextAlignment.CENTER, true, palette.primaryText());
        Rect hitbox = activeClickClip == null ? rect : intersection(rect, activeClickClip);
        if (hitbox != null) {
            hitTargets.add(new HitTarget(hitbox, action));
        }
    }

    private void panel(GuiGraphicsExtractor graphics, Rect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), palette.panel());
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), palette.border());
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, Rect area, int total, int visible, int first) {
        if (total <= visible || visible <= 0) {
            return;
        }
        int trackX = area.right() - 3;
        graphics.fill(trackX, area.y(), trackX + 2, area.bottom(), palette.scrollbarTrack());
        int thumbHeight = Math.max(10, area.height() * visible / total);
        int travel = area.height() - thumbHeight;
        int thumbY = area.y() + travel * first / Math.max(1, total - visible);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, palette.scrollbarThumb());
    }

    private void drawPixelScrollbar(GuiGraphicsExtractor graphics, Rect area, int contentHeight, int scroll) {
        if (contentHeight <= area.height() || area.height() <= 0) {
            return;
        }
        int trackX = area.right() - 2;
        graphics.fill(trackX, area.y(), trackX + 2, area.bottom(), palette.scrollbarTrack());
        int thumbHeight = Math.max(10, area.height() * area.height() / contentHeight);
        int travel = area.height() - thumbHeight;
        int maxScroll = contentHeight - area.height();
        int thumbY = area.y() + travel * clamp(scroll, 0, maxScroll) / Math.max(1, maxScroll);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, palette.scrollbarThumb());
    }

    private TextLayout planText(String text, int width, int maxLines, float minimumScale) {
        return planText(text, width, Integer.MAX_VALUE, maxLines, minimumScale);
    }

    private TextLayout planText(String text, int width, int maxHeight, int maxLines, float minimumScale) {
        if (text.isEmpty() || width <= 0) {
            return TextLayout.EMPTY;
        }
        Component component = Component.literal(text);
        TextLayout fallback = TextLayout.EMPTY;
        for (int step = 0; step <= 10; step++) {
            float scale = 1.0F - step * 0.025F;
            if (scale + 0.0001F < minimumScale) {
                break;
            }
            int logicalWidth = Math.max(1, (int)Math.floor(width / scale));
            List<FormattedCharSequence> lines = List.copyOf(font.split(component, logicalWidth));
            int height = Math.max(1, (int)Math.ceil(lines.size() * font.lineHeight * scale));
            fallback = new TextLayout(lines, scale, height);
            if (lines.size() <= maxLines && height <= maxHeight) {
                return fallback;
            }
        }
        return fallback;
    }

    private int drawFittedText(
            GuiGraphicsExtractor graphics,
            String text,
            Rect bounds,
            int maxLines,
            float minimumScale,
            TextAlignment alignment,
            boolean verticallyCentered,
            int color
    ) {
        TextLayout layout = planText(text, bounds.width(), bounds.height(), maxLines, minimumScale);
        drawTextLayout(graphics, layout, bounds, alignment, verticallyCentered, color);
        return layout.height();
    }

    private void drawTextLayout(
            GuiGraphicsExtractor graphics,
            TextLayout layout,
            Rect bounds,
            TextAlignment alignment,
            boolean verticallyCentered,
            int color
    ) {
        if (layout.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        int startY = verticallyCentered
                ? bounds.y() + Math.max(0, (bounds.height() - layout.height()) / 2)
                : bounds.y();
        graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.x(), startY);
        graphics.pose().scale(layout.scale(), layout.scale());
        float logicalWidth = bounds.width() / layout.scale();
        for (int lineIndex = 0; lineIndex < layout.lines().size(); lineIndex++) {
            FormattedCharSequence line = layout.lines().get(lineIndex);
            int lineX = alignment == TextAlignment.CENTER
                    ? Math.max(0, Math.round((logicalWidth - font.width(line)) / 2.0F))
                    : 0;
            graphics.text(font, line, lineX, lineIndex * font.lineHeight, color, false);
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    private void switchTab(Tab newTab) {
        tab = newTab;
        browsingBooks = false;
        currentDetailScroll = 0;
        possibleDetailScroll = 0;
    }

    private void selectLevel(int level) {
        selectedProfessionLevel = level;
        selectedPossible = null;
        selectedBook = null;
        browsingBooks = false;
        possibleGridScroll = 0;
        possibleDetailScroll = 0;
    }

    private void selectPossible(LibrarianTradeReference trade) {
        selectedPossible = trade;
        possibleDetailScroll = 0;
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
        if (activeDetailBounds != null && activeDetailBounds.contains(mouseX, mouseY)) {
            if (tab == Tab.CURRENT) {
                currentDetailScroll = clamp(currentDetailScroll + direction * 12, 0, currentDetailMaxScroll);
            } else {
                possibleDetailScroll = clamp(possibleDetailScroll + direction * 12, 0, possibleDetailMaxScroll);
            }
            return true;
        }
        if (tab == Tab.POSSIBLE && !browsingBooks
                && activePossibleGridBounds != null && activePossibleGridBounds.contains(mouseX, mouseY)) {
            possibleGridScroll = clamp(possibleGridScroll + direction * 12, 0, possibleGridMaxScroll);
            return true;
        }
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
        panelWidth = Math.max(1, Math.min(panelWidth, Math.max(1, width - 4)));
        panelHeight = Math.max(1, Math.min(panelHeight, Math.max(1, height - 4)));
        return new Layout((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }

    private static Rect inset(Rect rect, int amount) {
        int horizontal = Math.min(amount, Math.max(0, (rect.width() - 1) / 2));
        int vertical = Math.min(amount, Math.max(0, (rect.height() - 1) / 2));
        return new Rect(
                rect.x() + horizontal,
                rect.y() + vertical,
                Math.max(1, rect.width() - horizontal * 2),
                Math.max(1, rect.height() - vertical * 2)
        );
    }

    private static @Nullable Rect intersection(Rect first, Rect second) {
        int x = Math.max(first.x(), second.x());
        int y = Math.max(first.y(), second.y());
        int right = Math.min(first.right(), second.right());
        int bottom = Math.min(first.bottom(), second.bottom());
        return right > x && bottom > y ? new Rect(x, y, right - x, bottom - y) : null;
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
        return levels.stream().map(LibrarianInfoScreen::levelName).reduce((a, b) -> a + ", " + b).orElse("");
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

    private enum TextAlignment {
        LEFT,
        CENTER
    }

    private record TextLayout(List<FormattedCharSequence> lines, float scale, int height) {
        private static final TextLayout EMPTY = new TextLayout(List.of(), 1.0F, 0);

        boolean isEmpty() {
            return lines.isEmpty();
        }
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
