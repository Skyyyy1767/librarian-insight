package librarianinsight.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import librarianinsight.KnownLibrarianSnapshot;
import librarianinsight.LecternAssociation;
import librarianinsight.LibrarianInsight;
import librarianinsight.status.VillagerStatusCalculations;
import librarianinsight.status.VillagerStatusSnapshot;
import librarianinsight.trade.LibrarianEnchantedBookReference;
import librarianinsight.trade.LibrarianMinimumPrice;
import librarianinsight.trade.LibrarianTradeCatalog;
import librarianinsight.trade.LibrarianTradeMode;
import librarianinsight.trade.LibrarianTradeReference;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** Icon-first, read-only librarian reference browser for an empty lectern. */
public final class LibrarianInfoScreen extends Screen {
    private static final int CURRENT_ROW_HEIGHT = 43;
    private static final int BOOK_ROW_HEIGHT = 31;
    private static final float MIN_TEXT_SCALE = 0.75F;
    private static final Object CLOSE_BUTTON_MOTION_KEY = new Object();

    private final BlockPos lecternPos;
    private final List<HitTarget> hitTargets = new ArrayList<>();
    private final List<ScrollbarTarget> scrollbarTargets = new ArrayList<>();
    private final LibrarianScreenMotion motion = new LibrarianScreenMotion();
    private LibrarianMenuPalette palette = LibrarianMenuPalette.LIGHT;
    private Tab tab = Tab.CURRENT;
    private Tab previousTab = Tab.CURRENT;
    private long frameTime;
    private LibrarianScreenLayout.@Nullable Spec layoutSpec;
    private @Nullable EditBox bookSearch;
    private String bookQuery = "";
    private int selectedCurrent;
    private int currentScroll;
    private double currentScrollVisual;
    private int selectedProfessionLevel = 1;
    private @Nullable LibrarianTradeReference selectedPossible;
    private @Nullable LibrarianEnchantedBookReference selectedBook;
    private boolean browsingBooks;
    private int bookProfessionLevel = 1;
    private int bookScroll;
    private double bookScrollVisual;
    private int bookGridColumns = 1;
    private int currentDetailScroll;
    private double currentDetailScrollVisual;
    private int possibleDetailScroll;
    private double possibleDetailScrollVisual;
    private int possibleGridScroll;
    private double possibleGridScrollVisual;
    private int currentDetailMaxScroll;
    private int possibleDetailMaxScroll;
    private int possibleGridMaxScroll;
    private int statusScroll;
    private double statusScrollVisual;
    private int statusMaxScroll;
    private int statusDetailScroll;
    private double statusDetailScrollVisual;
    private int statusDetailMaxScroll;
    private @Nullable Rect activeDetailBounds;
    private @Nullable Rect activePossibleGridBounds;
    private @Nullable Rect activeStatusBounds;
    private @Nullable Rect activeClickClip;
    private @Nullable Rect activeTooltipClip;
    private @Nullable ScrollbarDrag activeScrollbarDrag;
    private @Nullable KnownLibrarianSnapshot cachedSnapshot;
    private MerchantOffers cachedOffers = new MerchantOffers();
    private @Nullable UUID refreshRequestedFor;
    private long refreshRequestedAt = Long.MIN_VALUE;
    private @Nullable StatusCard selectedStatusCard;
    private IntegratedVillagerStatusService.Result statusResult;
    private boolean statusRequestInFlight;
    private long statusRequestGeneration;
    private long lastStatusRequestAt = Long.MIN_VALUE;

    public LibrarianInfoScreen(BlockPos lecternPos) {
        super(Component.literal("Librarian Insight"));
        this.lecternPos = lecternPos.immutable();
    }

    @Override
    protected void init() {
        super.init();
        EditBox search = new EditBox(font, 0, 0, 120, 14, Component.literal("Search enchantments"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setHint(Component.literal("Search enchantments"));
        search.setTextShadow(false);
        search.setValue(bookQuery);
        search.setResponder(value -> {
            bookQuery = value;
            bookScroll = 0;
            bookScrollVisual = 0.0;
            selectedBook = null;
            possibleDetailScroll = 0;
            possibleDetailScrollVisual = 0.0;
        });
        search.visible = false;
        bookSearch = addRenderableWidget(search);
        activeScrollbarDrag = null;
        requestStatusSnapshot(true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        frameTime = System.nanoTime();
        motion.beginFrame(frameTime);
        palette = LibrarianMenuPalette.forTheme(LibrarianInsight.priceDisplay.getMenuTheme());
        if (bookSearch != null) {
            bookSearch.visible = false;
            bookSearch.setTextColor(palette.primaryText());
            bookSearch.setTextColorUneditable(palette.secondaryText());
        }
        hitTargets.clear();
        scrollbarTargets.clear();
        activeDetailBounds = null;
        activePossibleGridBounds = null;
        activeStatusBounds = null;
        activeClickClip = null;
        activeTooltipClip = null;
        currentDetailMaxScroll = 0;
        possibleDetailMaxScroll = 0;
        possibleGridMaxScroll = 0;
        statusMaxScroll = 0;
        statusDetailMaxScroll = 0;

        float opening = motion.openProgress(frameTime);
        graphics.fillGradient(0, 0, width, height,
                LibrarianScreenMotion.withAlpha(palette.backdropTop(), opening),
                LibrarianScreenMotion.withAlpha(palette.backdropBottom(), opening));
        layoutSpec = LibrarianScreenLayout.resolve(width, height, motion.entranceOffset(frameTime));
        Layout layout = layout();
        drawShell(graphics, layout);
        graphics.enableScissor(layout.x() + 1, layout.y() + 1, layout.right() - 1, layout.bottom() - 1);
        drawHeader(graphics, layout, mouseX, mouseY);

        int tabY = layout.y() + layout.headerHeight() - (layout.compact() ? 20 : 23);
        int tabHeight = layout.compact() ? 18 : 21;
        int tabWidth = Math.max(1, (layout.width() - 16) / 3);
        Rect currentTab = new Rect(layout.x() + 5, tabY, tabWidth, tabHeight);
        Rect possibleTab = new Rect(currentTab.right() + 3, tabY, tabWidth, tabHeight);
        Rect statusTab = new Rect(possibleTab.right() + 3, tabY, layout.right() - possibleTab.right() - 8, tabHeight);
        boolean shortLabels = layout.compact();
        drawTab(graphics, currentTab, shortLabels ? "Current" : "Current Librarian", Items.ENCHANTED_BOOK,
                Tab.CURRENT, mouseX, mouseY);
        drawTab(graphics, possibleTab, shortLabels ? "Trades" : "Possible Trades", Items.EMERALD,
                Tab.POSSIBLE, mouseX, mouseY);
        drawTab(graphics, statusTab, shortLabels ? "Status" : "Villager Status", Items.BELL,
                Tab.STATUS, mouseX, mouseY);
        drawTabIndicator(graphics, currentTab, possibleTab, statusTab);

        int contentOffset = motion.contentOffset(frameTime);
        Rect content = new Rect(
                layout.x() + 5,
                layout.y() + layout.headerHeight() + contentOffset,
                layout.width() - 10,
                Math.max(1, layout.contentHeight() - contentOffset)
        );
        switch (tab) {
            case CURRENT -> drawCurrentTab(graphics, content, mouseX, mouseY);
            case POSSIBLE -> drawPossibleTab(graphics, content, mouseX, mouseY);
            case STATUS -> drawStatusTab(graphics, content, mouseX, mouseY);
        }

        drawFooter(graphics, layout);
        graphics.disableScissor();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawShell(GuiGraphicsExtractor graphics, Layout layout) {
        graphics.fill(layout.x() + 5, layout.y() + 6, layout.right() + 5, layout.bottom() + 6, palette.shadow());
        graphics.fill(layout.x() + 2, layout.y() + 3, layout.right() + 2, layout.bottom() + 3,
                LibrarianScreenMotion.withAlpha(palette.shadow(), 0.72F));
        graphics.fillGradient(layout.x(), layout.y(), layout.right(), layout.bottom(),
                palette.screenBackground(), palette.screenShade());
        graphics.outline(layout.x(), layout.y(), layout.width(), layout.height(), palette.border());
        graphics.outline(layout.x() + 1, layout.y() + 1, Math.max(1, layout.width() - 2),
                Math.max(1, layout.height() - 2), palette.innerBorder());
        graphics.fill(layout.x() + 3, layout.y() + 3, layout.x() + 5, layout.y() + 5, palette.accent());
        graphics.fill(layout.right() - 5, layout.y() + 3, layout.right() - 3, layout.y() + 5, palette.accent());
        graphics.fill(layout.x() + 3, layout.bottom() - 5, layout.x() + 5, layout.bottom() - 3, palette.accent());
        graphics.fill(layout.right() - 5, layout.bottom() - 5, layout.right() - 3, layout.bottom() - 3,
                palette.accent());
    }

    private void drawHeader(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        int bottom = layout.y() + layout.headerHeight();
        graphics.fillGradient(layout.x() + 2, layout.y() + 2, layout.right() - 2, bottom,
                palette.headerTop(), palette.headerBottom());
        graphics.fill(layout.x() + 2, bottom - 2, layout.right() - 2, bottom, palette.border());
        graphics.horizontalLine(layout.x() + 4, layout.right() - 5, bottom - 3, palette.accent());

        int iconX = layout.x() + 10;
        int iconY = layout.y() + (layout.compact() ? 5 : 8);
        graphics.fakeItem(new ItemStack(Items.ENCHANTED_BOOK), iconX, iconY);
        int closeSize = layout.compact() ? 16 : 18;
        Rect close = new Rect(layout.right() - closeSize - 7, layout.y() + 6, closeSize, closeSize);
        drawCloseButton(graphics, close, mouseX, mouseY);

        int titleX = iconX + 22;
        int titleRight = close.x() - 5;
        drawFittedText(graphics, title.getString(),
                new Rect(titleX, layout.y() + (layout.compact() ? 4 : 6), Math.max(1, titleRight - titleX), 13),
                1, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headerText());
        if (!layout.compact()) {
            drawFittedText(graphics, "Lectern trade compendium",
                    new Rect(titleX, layout.y() + 19, Math.max(1, titleRight - titleX), 10),
                    1, MIN_TEXT_SCALE, TextAlignment.LEFT, true,
                    LibrarianScreenMotion.mix(palette.headerText(), palette.accentBright(), 0.32F));
        }
        if (!layout.compact() && layout.width() >= 470) {
            String badge = "READ ONLY";
            int badgeWidth = font.width(badge) + 10;
            int badgeX = close.x() - badgeWidth - 7;
            graphics.fill(badgeX, layout.y() + 8, badgeX + badgeWidth, layout.y() + 20, 0x50355C91);
            graphics.outline(badgeX, layout.y() + 8, badgeWidth, 12, palette.selectedBorder());
            graphics.text(font, badge, badgeX + 5, layout.y() + 10, palette.headerText(), false);
        }
    }

    private void drawTabIndicator(GuiGraphicsExtractor graphics, Rect current, Rect possible, Rect status) {
        Rect from = tabRect(previousTab, current, possible, status);
        Rect to = tabRect(tab, current, possible, status);
        float progress = motion.tabProgress(frameTime);
        int x = Math.round(from.x() + (to.x() - from.x()) * progress);
        int right = Math.round(from.right() + (to.right() - from.right()) * progress);
        int y = to.bottom() - 3;
        graphics.fill(x + 3, y, Math.max(x + 4, right - 3), y + 2, palette.accentBright());
    }

    private static Rect tabRect(Tab tab, Rect current, Rect possible, Rect status) {
        return switch (tab) {
            case CURRENT -> current;
            case POSSIBLE -> possible;
            case STATUS -> status;
        };
    }

    private void drawFooter(GuiGraphicsExtractor graphics, Layout layout) {
        int y = layout.bottom() - layout.footerHeight();
        graphics.fillGradient(layout.x() + 2, y, layout.right() - 2, layout.bottom() - 2,
                palette.screenShade(), palette.headerBottom());
        graphics.horizontalLine(layout.x() + 4, layout.right() - 5, y, palette.border());
        String note = "\u25C6  Read only \u2022 trades remain unchanged";
        boolean showPosition = !layout.compact() && layout.width() >= 430;
        String position = showPosition
                ? lecternPos.getX() + ", " + lecternPos.getY() + ", " + lecternPos.getZ()
                : "";
        int noteWidth = layout.width() - 16 - (showPosition ? font.width(position) + 14 : 0);
        drawFittedText(graphics, note,
                new Rect(layout.x() + 8, y + 2, Math.max(1, noteWidth), layout.footerHeight() - 4),
                1, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headerText());
        if (showPosition) {
            int positionWidth = font.width(position);
            graphics.text(font, position, layout.right() - positionWidth - 9,
                    y + Math.max(2, (layout.footerHeight() - font.lineHeight) / 2), palette.headerText(), false);
        }
    }

    private void drawCurrentTab(GuiGraphicsExtractor graphics, Rect content, int mouseX, int mouseY) {
        LecternAssociation association = LibrarianInsight.lecternManager.getAssociationForMenu(lecternPos);
        KnownLibrarianSnapshot snapshot = association == null
                ? null
                : LibrarianInsight.enchantmentManager.getOfferSnapshot(association.villagerUuid());
        if (association != null && !association.villagerUuid().equals(refreshRequestedFor)) {
            refreshRequestedFor = association.villagerUuid();
            refreshRequestedAt = minecraft.level == null ? 0L : minecraft.level.getGameTime();
            LibrarianInsight.enchantmentManager.requestOfferRefresh(association.villagerUuid());
        }
        updateCachedOffers(snapshot);

        int listWidth = Math.max(1, Math.min(252, (content.width() - 5) / 2));
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
        int maxCurrentScroll = Math.max(0, cachedOffers.size() - visibleRows);
        currentScroll = clamp(currentScroll, 0, maxCurrentScroll);
        currentScrollVisual = Math.max(0.0, Math.min(maxCurrentScroll, currentScrollVisual));
        currentScrollVisual = animateScroll(ScrollRegion.CURRENT_LIST, currentScrollVisual, currentScroll);
        int firstVisible = clamp((int)Math.floor(currentScrollVisual), 0, maxCurrentScroll);
        int rowOffset = (int)Math.round((currentScrollVisual - firstVisible) * rowHeight);
        selectedCurrent = clamp(selectedCurrent, 0, cachedOffers.size() - 1);
        graphics.enableScissor(rows.x(), rows.y(), rows.right(), rows.bottom());
        activeTooltipClip = rows;
        for (int visibleIndex = 0; visibleIndex <= visibleRows; visibleIndex++) {
            int offerIndex = firstVisible + visibleIndex;
            if (offerIndex >= cachedOffers.size()) {
                break;
            }
            Rect row = new Rect(rows.x(), rows.y() + visibleIndex * rowHeight - rowOffset,
                    rows.width(), rowHeight - 2);
            MerchantOffer offer = cachedOffers.get(offerIndex);
            Rect visibleRow = intersection(row, rows);
            boolean hovered = visibleRow != null && visibleRow.contains(mouseX, mouseY);
            selectableSurface(graphics, row, offer, offerIndex == selectedCurrent, hovered);
            drawOfferFlow(graphics, offer, row.x() + 3, row.y() + 5, mouseX, mouseY, row.width(), row.height());
            if (visibleRow != null) {
                hitTargets.add(new HitTarget(visibleRow, () -> {
                    selectedCurrent = offerIndex;
                    currentDetailScroll = 0;
                    currentDetailScrollVisual = 0.0;
                    motion.selectionChanged(System.nanoTime());
                }));
            }
        }
        activeTooltipClip = null;
        graphics.disableScissor();
        drawScrollbar(graphics, rows, cachedOffers.size(), visibleRows, currentScrollVisual,
                ScrollRegion.CURRENT_LIST);
        drawCurrentDetail(graphics, detail, cachedOffers.get(selectedCurrent), snapshot, association, mouseX, mouseY);
    }

    private void drawStatusTab(GuiGraphicsExtractor graphics, Rect content, int mouseX, int mouseY) {
        requestStatusSnapshot(false);
        panel(graphics, content);
        if (selectedStatusCard == null) {
            drawStatusDashboard(graphics, content, mouseX, mouseY);
        } else {
            drawStatusDetail(graphics, content, selectedStatusCard, mouseX, mouseY);
        }
    }

    private void drawStatusDashboard(GuiGraphicsExtractor graphics, Rect content, int mouseX, int mouseY) {
        drawFittedText(graphics, "Villager Status",
                new Rect(content.x() + 6, content.y() + 3, content.width() - 12, 13),
                1, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headingText());
        Rect viewport = new Rect(content.x() + 3, content.y() + 19, content.width() - 6, content.height() - 22);
        activeStatusBounds = viewport;
        int gap = 4;
        int columns = layoutSpec == null ? 2 : layoutSpec.statusColumns();
        int cardWidth = Math.max(1, (viewport.width() - gap * (columns - 1) - 3) / columns);
        int cardHeight = 61;
        StatusCard[] cards = StatusCard.values();
        int rows = (cards.length + columns - 1) / columns;
        int totalHeight = rows * (cardHeight + gap) - gap;
        statusMaxScroll = Math.max(0, totalHeight - viewport.height());
        statusScroll = clamp(statusScroll, 0, statusMaxScroll);
        statusScrollVisual = Math.max(0.0, Math.min(statusMaxScroll, statusScrollVisual));
        statusScrollVisual = animateScroll(ScrollRegion.STATUS_DASHBOARD, statusScrollVisual, statusScroll);
        int displayedScroll = (int)Math.round(statusScrollVisual);

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        activeTooltipClip = viewport;
        for (int index = 0; index < cards.length; index++) {
            StatusCard card = cards[index];
            int column = index % columns;
            int row = index / columns;
            Rect box = new Rect(
                    viewport.x() + column * (cardWidth + gap),
                    viewport.y() + row * (cardHeight + gap) - displayedScroll,
                    cardWidth,
                    cardHeight
            );
            Rect visible = intersection(box, viewport);
            boolean hovered = visible != null && visible.contains(mouseX, mouseY);
            selectableSurface(graphics, box, card, false, hovered);
            drawFittedText(graphics, card.title,
                    new Rect(box.x() + 3, box.y() + 3, box.width() - 6, 12),
                    1, MIN_TEXT_SCALE, TextAlignment.CENTER, true, palette.headingText());
            drawIcon(graphics, statusIcon(card), box.x() + 5, box.y() + 24, mouseX, mouseY, false);
            CardSummary summary = statusSummary(card);
            drawFittedText(graphics, summary.text(),
                    new Rect(box.x() + 25, box.y() + 17, box.width() - 29, box.height() - 20),
                    4, MIN_TEXT_SCALE, TextAlignment.LEFT, true, toneColor(summary.tone()));
            if (visible != null) {
                hitTargets.add(new HitTarget(visible, () -> {
                    selectedStatusCard = card;
                    statusDetailScroll = 0;
                    statusDetailScrollVisual = 0.0;
                    motion.selectionChanged(System.nanoTime());
                }));
            }
        }
        activeTooltipClip = null;
        graphics.disableScissor();
        drawPixelScrollbar(graphics, viewport, totalHeight, statusScrollVisual,
                ScrollRegion.STATUS_DASHBOARD);
    }

    private void drawStatusDetail(
            GuiGraphicsExtractor graphics,
            Rect content,
            StatusCard card,
            int mouseX,
            int mouseY
    ) {
        Rect back = new Rect(content.x() + 4, content.y() + 3, 44, 18);
        drawSmallButton(graphics, back, "Back", false, mouseX, mouseY, () -> {
            selectedStatusCard = null;
            statusDetailScroll = 0;
            statusDetailScrollVisual = 0.0;
            motion.selectionChanged(System.nanoTime());
        });
        drawFittedText(graphics, card.title,
                new Rect(back.right() + 5, content.y() + 3, content.right() - back.right() - 10, 18),
                2, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headingText());

        Rect viewport = new Rect(content.x() + 5, content.y() + 25, content.width() - 10, content.height() - 30);
        activeStatusBounds = viewport;
        List<StatusDetailRow> rows = statusDetailRows(card);
        List<TextLayout> layouts = new ArrayList<>();
        int totalHeight = 3;
        for (StatusDetailRow row : rows) {
            int textWidth = Math.max(12, viewport.width() - (row.icon().isPresent() ? 23 : 2) - 4);
            TextLayout layout = planText(row.text(), textWidth, row.heading() ? 2 : 8, MIN_TEXT_SCALE);
            layouts.add(layout);
            totalHeight += Math.max(row.icon().isPresent() ? 16 : 0, layout.height()) + 5;
        }
        statusDetailMaxScroll = Math.max(0, totalHeight - viewport.height());
        statusDetailScroll = clamp(statusDetailScroll, 0, statusDetailMaxScroll);
        statusDetailScrollVisual = Math.max(0.0, Math.min(statusDetailMaxScroll, statusDetailScrollVisual));
        statusDetailScrollVisual = animateScroll(
                ScrollRegion.STATUS_DETAIL, statusDetailScrollVisual, statusDetailScroll
        );

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        activeTooltipClip = viewport;
        int y = viewport.y() + 3 - (int)Math.round(statusDetailScrollVisual);
        for (int index = 0; index < rows.size(); index++) {
            StatusDetailRow row = rows.get(index);
            TextLayout textLayout = layouts.get(index);
            int textX = viewport.x() + 2;
            if (row.icon().isPresent()) {
                drawIcon(graphics, row.icon().get(), textX, y, mouseX, mouseY, true);
                textX += 23;
            }
            int rowHeight = Math.max(row.icon().isPresent() ? 16 : 0, textLayout.height());
            drawTextLayout(graphics, textLayout,
                    new Rect(textX, y, viewport.right() - textX - 3, rowHeight),
                    TextAlignment.LEFT, true, toneColor(row.tone()));
            y += rowHeight + 5;
        }
        activeTooltipClip = null;
        graphics.disableScissor();
        drawPixelScrollbar(graphics, viewport, totalHeight, statusDetailScrollVisual,
                ScrollRegion.STATUS_DETAIL);
    }

    private CardSummary statusSummary(StatusCard card) {
        VillagerStatusSnapshot snapshot = integratedStatus();
        LecternAssociation association = statusAssociation();
        KnownLibrarianSnapshot known = statusKnownSnapshot();
        return switch (card) {
            case WORKSTATION -> {
                if (snapshot != null) {
                    String owner = snapshot.serverConfirmedOwner() ? "Lectern confirmed" : "Assignment estimated";
                    yield new CardSummary(owner + "\n" + activityLabel(snapshot),
                            snapshot.serverConfirmedOwner() ? Tone.SUCCESS : Tone.WARNING);
                }
                yield association == null
                        ? new CardSummary("No assigned villager yet", Tone.WARNING)
                        : new CardSummary(association.confidence().description(),
                                association.confidence() == LecternAssociation.Confidence.FALLBACK ? Tone.WARNING : Tone.NORMAL);
            }
            case HEALTH -> {
                float[] health = statusHealth(snapshot, association);
                yield health == null
                        ? new CardSummary("Health unavailable", Tone.MUTED)
                        : new CardSummary(formatNumber(health[0]) + " / " + formatNumber(health[1]),
                                health[0] >= health[1] ? Tone.SUCCESS : Tone.NORMAL);
            }
            case RESTOCK -> snapshot == null
                    ? new CardSummary("Server details unavailable", Tone.MUTED)
                    : new CardSummary(
                            snapshot.restock().usedToday() + " of 2 used today\n" + restockSummary(snapshot),
                            restockTone(snapshot.restock().state()));
            case TRADES -> {
                int total = snapshot != null ? snapshot.trades().size() : known == null ? 0 : known.offersCopy().size();
                int available = snapshot != null ? snapshot.availableTrades() : availableOffers(known);
                int out = Math.max(0, total - available);
                yield total == 0
                        ? new CardSummary("Trade data unavailable", Tone.MUTED)
                        : new CardSummary(total + " total\n" + available + " available - " + out + " out", out > 0 ? Tone.WARNING : Tone.SUCCESS);
            }
            case STANDING -> snapshot == null
                    ? new CardSummary("Reputation unavailable", Tone.MUTED)
                    : new CardSummary(
                            standingLabel(snapshot.gossip().reputation()) + " (" + signed(snapshot.gossip().reputation()) + ")\nPrices: "
                                    + helpingLabel(snapshot.gossip().reputation()),
                            snapshot.gossip().reputation() > 0 ? Tone.SUCCESS
                                    : snapshot.gossip().reputation() < 0 ? Tone.DANGER : Tone.NORMAL);
            case LEVEL -> {
                int level = snapshot != null ? snapshot.professionLevel() : known == null ? 0 : known.villagerLevel();
                int xp = snapshot != null ? snapshot.villagerXp() : known == null ? 0 : known.villagerXp();
                yield level == 0
                        ? new CardSummary("Level unavailable", Tone.MUTED)
                        : new CardSummary(levelName(level) + "\n" + (level >= 5 ? "Maximum level" : xp + " / " + VillagerData.getMaxXpPerLevel(level) + " XP"), Tone.NORMAL);
            }
            case HERO -> {
                VillagerStatusSnapshot.Hero hero = snapshot != null ? snapshot.hero() : clientHero();
                yield hero.active()
                        ? new CardSummary("Active - Level " + roman(hero.level()) + "\n" + duration(hero) + " remaining", Tone.SUCCESS)
                        : new CardSummary("Inactive", Tone.MUTED);
            }
        };
    }

    private List<StatusDetailRow> statusDetailRows(StatusCard card) {
        List<StatusDetailRow> rows = new ArrayList<>();
        VillagerStatusSnapshot snapshot = integratedStatus();
        LecternAssociation association = statusAssociation();
        KnownLibrarianSnapshot known = statusKnownSnapshot();
        switch (card) {
            case WORKSTATION -> {
                rows.add(detail("Workstation", Items.LECTERN.getDefaultInstance(), Tone.HEADING, true));
                if (snapshot != null) {
                    rows.add(detail(snapshot.serverConfirmedOwner()
                            ? "Lectern assigned: Confirmed by the integrated server"
                            : "Lectern assigned: Estimated from client observations",
                            null, snapshot.serverConfirmedOwner() ? Tone.SUCCESS : Tone.WARNING, false));
                    rows.add(detail("Activity: " + activityLabel(snapshot), null, Tone.NORMAL, false));
                    rows.add(detail(String.format(Locale.ROOT, "Distance to lectern: %.1f blocks", snapshot.distanceToLectern()), null, Tone.NORMAL, false));
                    rows.add(detail("Within working range: " + yesNo(snapshot.withinWorkingRange()), null,
                            snapshot.withinWorkingRange() ? Tone.SUCCESS : Tone.WARNING, false));
                    rows.add(detail("Path accessibility is not tested. Minecraft does not provide a safe, stable yes/no check without changing navigation.",
                            null, Tone.MUTED, false));
                } else if (association != null) {
                    rows.add(detail("Association: " + association.confidence().description(), null,
                            association.confidence() == LecternAssociation.Confidence.FALLBACK ? Tone.WARNING : Tone.NORMAL, false));
                    rows.add(detail("Exact server workstation information is unavailable on this server.", null, Tone.MUTED, false));
                } else {
                    rows.add(detail("No librarian is currently associated with this lectern.", null, Tone.WARNING, false));
                }
            }
            case HEALTH -> {
                float[] health = statusHealth(snapshot, association);
                rows.add(detail("Health", Items.APPLE.getDefaultInstance(), Tone.HEADING, true));
                if (health == null) {
                    rows.add(detail("Health is unavailable while the associated villager is not loaded.", null, Tone.MUTED, false));
                } else {
                    rows.add(detail(healthHearts(health[0], health[1]), null, health[0] >= health[1] ? Tone.SUCCESS : Tone.NORMAL, false));
                    rows.add(detail(formatNumber(health[0]) + " / " + formatNumber(health[1]), null, Tone.NORMAL, false));
                }
            }
            case RESTOCK -> addRestockDetails(rows, snapshot);
            case TRADES -> addTradeDetails(rows, snapshot, known);
            case STANDING -> addStandingDetails(rows, snapshot, known);
            case LEVEL -> addLevelDetails(rows, snapshot, known);
            case HERO -> addHeroDetails(rows, snapshot);
        }
        return rows;
    }

    private void addRestockDetails(List<StatusDetailRow> rows, @Nullable VillagerStatusSnapshot snapshot) {
        rows.add(detail("Restock Status", Items.CLOCK.getDefaultInstance(), Tone.HEADING, true));
        if (snapshot == null) {
            rows.add(detail("Exact restock counters are available in single-player. This remote server does not provide them.", null, Tone.MUTED, false));
            return;
        }
        VillagerStatusSnapshot.Restock restock = snapshot.restock();
        rows.add(detail(restock.usedToday() + " of 2 used today", null, Tone.NORMAL, false));
        rows.add(detail(restock.remaining() + " remaining", null, restock.remaining() > 0 ? Tone.SUCCESS : Tone.WARNING, false));
        rows.add(detail("Status: " + restockSummary(snapshot), null, restockTone(restock.state()), false));
        if (restock.cooldownTicks() > 0) {
            rows.add(detail("Cooldown: " + ticksToDuration(restock.cooldownTicks()), null, Tone.WARNING, false));
        }
        rows.add(detail(snapshot.tradesNeedingRestock() + " trades need restocking", Items.ENCHANTED_BOOK.getDefaultInstance(), Tone.NORMAL, false));
        rows.add(detail(snapshot.outOfStockTrades() + " trades are out of stock", null,
                snapshot.outOfStockTrades() > 0 ? Tone.WARNING : Tone.SUCCESS, false));
        restock.lastActualRestockGameTime().ifPresent(last -> rows.add(detail(
                "Last restock: " + ticksToDuration(Math.max(0, snapshot.serverGameTime() - last)) + " ago",
                null, Tone.MUTED, false)));
        rows.add(detail("A ready status is not an automatic restock. The villager must still work at its lectern.", null, Tone.MUTED, false));
    }

    private void addTradeDetails(
            List<StatusDetailRow> rows,
            @Nullable VillagerStatusSnapshot snapshot,
            @Nullable KnownLibrarianSnapshot known
    ) {
        rows.add(detail("Trade Availability", Items.ENCHANTED_BOOK.getDefaultInstance(), Tone.HEADING, true));
        if (snapshot != null && !snapshot.trades().isEmpty()) {
            rows.add(detail(snapshot.trades().size() + " total - " + snapshot.availableTrades() + " available - "
                    + snapshot.outOfStockTrades() + " out of stock", null, Tone.NORMAL, false));
            for (VillagerStatusSnapshot.Trade trade : snapshot.trades()) {
                String state = trade.outOfStock() ? "Out of stock" : "Available";
                rows.add(detail(itemName(trade.result()) + "\n" + state + " - Uses: " + trade.uses() + " / " + trade.maximumUses(),
                        trade.result(), trade.outOfStock() ? Tone.WARNING : Tone.NORMAL, false));
            }
            return;
        }
        MerchantOffers offers = known == null ? new MerchantOffers() : known.offersCopy();
        if (offers.isEmpty()) {
            rows.add(detail("No current trade data is available.", null, Tone.MUTED, false));
            return;
        }
        for (MerchantOffer offer : offers) {
            rows.add(detail(offerName(offer) + "\n" + (offer.isOutOfStock() ? "Out of stock" : "Available")
                            + " - Uses: " + offer.getUses() + " / " + offer.getMaxUses(),
                    offer.getResult(), offer.isOutOfStock() ? Tone.WARNING : Tone.NORMAL, false));
        }
    }

    private void addStandingDetails(
            List<StatusDetailRow> rows,
            @Nullable VillagerStatusSnapshot snapshot,
            @Nullable KnownLibrarianSnapshot known
    ) {
        rows.add(detail("Player Standing", Items.EMERALD.getDefaultInstance(), Tone.HEADING, true));
        if (snapshot == null) {
            rows.add(detail("Exact reputation and gossip are available in single-player but are not sent by this remote server.", null, Tone.MUTED, false));
            return;
        }
        int reputation = snapshot.gossip().reputation();
        rows.add(detail(standingLabel(reputation) + " - Reputation: " + signed(reputation), null,
                reputation > 0 ? Tone.SUCCESS : reputation < 0 ? Tone.DANGER : Tone.NORMAL, false));
        rows.add(detail("Prices: " + helpingLabel(reputation), null,
                reputation > 0 ? Tone.SUCCESS : reputation < 0 ? Tone.DANGER : Tone.NORMAL, false));
        rows.add(detail("Reputation details", null, Tone.HEADING, true));
        rows.add(detail("Major positive: " + snapshot.gossip().value(VillagerStatusSnapshot.GossipKind.MAJOR_POSITIVE), null, Tone.SUCCESS, false));
        rows.add(detail("Minor positive: " + snapshot.gossip().value(VillagerStatusSnapshot.GossipKind.MINOR_POSITIVE), null, Tone.SUCCESS, false));
        rows.add(detail("Trading: " + snapshot.gossip().value(VillagerStatusSnapshot.GossipKind.TRADING), null, Tone.SUCCESS, false));
        rows.add(detail("Minor negative: " + snapshot.gossip().value(VillagerStatusSnapshot.GossipKind.MINOR_NEGATIVE), null, Tone.DANGER, false));
        rows.add(detail("Major negative: " + snapshot.gossip().value(VillagerStatusSnapshot.GossipKind.MAJOR_NEGATIVE), null, Tone.DANGER, false));
        rows.add(detail("Price details", null, Tone.HEADING, true));
        MerchantOffers actual = known == null ? new MerchantOffers() : known.offersCopy();
        for (int index = 0; index < snapshot.trades().size(); index++) {
            VillagerStatusSnapshot.Trade trade = snapshot.trades().get(index);
            VillagerStatusCalculations.PriceResult price = trade.price();
            int actualCurrent = index < actual.size() && matchingStatusTrade(trade, actual.get(index))
                    ? actual.get(index).getCostA().getCount()
                    : price.current();
            int unknown = actualCurrent - price.current();
            StringBuilder text = new StringBuilder(itemName(trade.result()))
                    .append("\nBase: ").append(price.base())
                    .append(" - Demand: ").append(signed(price.demand()))
                    .append(" - Reputation: ").append(signed(price.reputation()))
                    .append(" - Hero: ").append(signed(price.hero()));
            if (unknown != 0) {
                text.append(" - Other/unknown: ").append(signed(unknown));
            }
            text.append(" - Current: ").append(actualCurrent);
            rows.add(detail(text.toString(), trade.result(), unknown == 0 ? Tone.NORMAL : Tone.WARNING, false));
        }
    }

    private void addLevelDetails(
            List<StatusDetailRow> rows,
            @Nullable VillagerStatusSnapshot snapshot,
            @Nullable KnownLibrarianSnapshot known
    ) {
        rows.add(detail("Librarian Level", Items.EXPERIENCE_BOTTLE.getDefaultInstance(), Tone.HEADING, true));
        int level = snapshot != null ? snapshot.professionLevel() : known == null ? 0 : known.villagerLevel();
        int xp = snapshot != null ? snapshot.villagerXp() : known == null ? 0 : known.villagerXp();
        if (level == 0) {
            rows.add(detail("Level information is unavailable.", null, Tone.MUTED, false));
        } else if (level >= 5) {
            rows.add(detail("Master", null, Tone.SUCCESS, false));
            rows.add(detail("Maximum level", null, Tone.NORMAL, false));
        } else {
            int minimum = VillagerData.getMinXpPerLevel(level);
            int maximum = VillagerData.getMaxXpPerLevel(level);
            rows.add(detail(levelName(level), null, Tone.NORMAL, false));
            rows.add(detail(xp + " / " + maximum + " XP\n" + progressBar(xp, minimum, maximum), null, Tone.SUCCESS, false));
            rows.add(detail("Next: " + levelName(level + 1), null, Tone.MUTED, false));
        }
    }

    private void addHeroDetails(List<StatusDetailRow> rows, @Nullable VillagerStatusSnapshot snapshot) {
        rows.add(detail("Hero of the Village", Items.TOTEM_OF_UNDYING.getDefaultInstance(), Tone.HEADING, true));
        VillagerStatusSnapshot.Hero hero = snapshot != null ? snapshot.hero() : clientHero();
        if (!hero.active()) {
            rows.add(detail("Inactive", null, Tone.MUTED, false));
            rows.add(detail("When active, this effect helps reduce villager prices.", null, Tone.MUTED, false));
        } else {
            rows.add(detail("Active", null, Tone.SUCCESS, false));
            rows.add(detail("Level " + roman(hero.level()), null, Tone.NORMAL, false));
            rows.add(detail(duration(hero) + " remaining", null, Tone.NORMAL, false));
            rows.add(detail("Prices: Helping", Items.EMERALD.getDefaultInstance(), Tone.SUCCESS, false));
        }
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
        TextLayout minimumLabel = planText("Minimum", textWidth, 2, MIN_TEXT_SCALE);
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
        boolean ambiguous = LibrarianInsight.lecternManager.isAssociationAmbiguous(lecternPos)
                || association.confidence() == LecternAssociation.Confidence.FALLBACK;
        TextLayout ambiguity = ambiguous
                ? planText("Crowded setup: association is estimated", textWidth, 4, MIN_TEXT_SCALE)
                : TextLayout.EMPTY;

        int contentHeight = 4 + nameLayout.height() + 3 + currentLabel.height() + 2 + 16
                + 5 + minimumLabel.height() + 2 + (minimum.isPresent() ? 16 : unavailable.height())
                + 8 + confidence.height() + (ambiguous ? 3 + ambiguity.height() : 0) + 4;
        currentDetailMaxScroll = Math.max(0, contentHeight - viewport.height());
        currentDetailScroll = clamp(currentDetailScroll, 0, currentDetailMaxScroll);
        currentDetailScrollVisual = Math.max(0.0, Math.min(currentDetailMaxScroll, currentDetailScrollVisual));
        currentDetailScrollVisual = animateScroll(
                ScrollRegion.CURRENT_DETAIL, currentDetailScrollVisual, currentDetailScroll
        );

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        activeTooltipClip = viewport;
        int y = viewport.y() + 4 - (int)Math.round(currentDetailScrollVisual);
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
        drawPixelScrollbar(graphics, viewport, contentHeight, currentDetailScrollVisual,
                ScrollRegion.CURRENT_DETAIL);
    }

    private void drawAssociationNote(GuiGraphicsExtractor graphics, Rect detail, LecternAssociation association) {
        Rect inner = inset(detail, 6);
        drawFittedText(graphics, "Association", new Rect(inner.x(), inner.y(), inner.width(), 13),
                1, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headingText());
        drawFittedText(graphics, association.confidence().description(),
                new Rect(inner.x(), inner.y() + 18, inner.width(), 27),
                3, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.associationText());
        if (LibrarianInsight.lecternManager.isAssociationAmbiguous(lecternPos)
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
            drawFittedText(graphics, "This lectern stays blank here until Librarian Insight learns a librarian and receives its offers.",
                    new Rect(inner.x(), messageY, inner.width(), inner.bottom() - messageY),
                    7, MIN_TEXT_SCALE, TextAlignment.LEFT, false, palette.secondaryText());
        }
    }

    private void drawPossibleTab(GuiGraphicsExtractor graphics, Rect content, int mouseX, int mouseY) {
        LibrarianTradeMode mode = currentMode();
        if (browsingBooks) {
            drawBookBrowser(graphics, content, mode, mouseX, mouseY);
            return;
        }
        int levelY = content.y();
        int levelWidth = Math.max(1, (content.width() - 8) / 5);
        int levelHeight = 23;
        for (int level = 1; level <= 5; level++) {
            int captured = level;
            Rect button = new Rect(content.x() + (level - 1) * (levelWidth + 2), levelY, levelWidth, levelHeight);
            drawSmallButton(graphics, button, levelButtonName(level, content.width()),
                    selectedProfessionLevel == level && !browsingBooks,
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
        drawPossibleGrid(graphics, body, mode, mouseX, mouseY);
    }

    private void drawPossibleGrid(GuiGraphicsExtractor graphics, Rect body, LibrarianTradeMode mode, int mouseX, int mouseY) {
        int gridWidth = Math.max(1, Math.min(250, (body.width() - 5) / 2));
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
        int columns = layoutSpec == null ? 2 : layoutSpec.possibleGridColumns(gridRows.width());
        int cellGap = 4;
        int availableGridWidth = gridRows.width();
        int cellWidth = Math.max(1, (availableGridWidth - cellGap * (columns - 1)) / columns);
        int cellHeight = possibleCellHeight(trades, cellWidth);
        int rowCount = (trades.size() + columns - 1) / columns;
        int gridContentHeight = rowCount * (cellHeight + cellGap) - (rowCount == 0 ? 0 : cellGap);
        if (gridContentHeight > gridRows.height()) {
            availableGridWidth = Math.max(1, gridRows.width() - 3);
            cellWidth = Math.max(1, (availableGridWidth - cellGap * (columns - 1)) / columns);
            cellHeight = possibleCellHeight(trades, cellWidth);
            gridContentHeight = rowCount * (cellHeight + cellGap) - (rowCount == 0 ? 0 : cellGap);
        }
        possibleGridMaxScroll = Math.max(0, gridContentHeight - gridRows.height());
        possibleGridScroll = clamp(possibleGridScroll, 0, possibleGridMaxScroll);
        possibleGridScrollVisual = Math.max(0.0, Math.min(possibleGridMaxScroll, possibleGridScrollVisual));
        possibleGridScrollVisual = animateScroll(
                ScrollRegion.POSSIBLE_GRID, possibleGridScrollVisual, possibleGridScroll
        );
        int displayedGridScroll = (int)Math.round(possibleGridScrollVisual);
        activePossibleGridBounds = gridRows;
        graphics.enableScissor(gridRows.x(), gridRows.y(), gridRows.right(), gridRows.bottom());
        activeTooltipClip = gridRows;
        for (int index = 0; index < trades.size(); index++) {
            LibrarianTradeReference trade = trades.get(index);
            int column = index % columns;
            int rowIndex = index / columns;
            Rect cell = new Rect(
                    gridRows.x() + column * (cellWidth + cellGap),
                    gridRows.y() + rowIndex * (cellHeight + cellGap) - displayedGridScroll,
                    cellWidth,
                    cellHeight
            );
            boolean selected = trade.equals(selectedPossible);
            Rect visibleCell = intersection(cell, gridRows);
            boolean hovered = visibleCell != null && visibleCell.contains(mouseX, mouseY);
            selectableSurface(graphics, cell, trade, selected, hovered);
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
        drawPixelScrollbar(graphics, gridRows, gridContentHeight, possibleGridScrollVisual,
                ScrollRegion.POSSIBLE_GRID);
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
            possibleDetailScrollVisual = Math.max(0.0,
                    Math.min(possibleDetailMaxScroll, possibleDetailScrollVisual));
            possibleDetailScrollVisual = animateScroll(
                    ScrollRegion.POSSIBLE_DETAIL, possibleDetailScrollVisual, possibleDetailScroll
            );
            int displayedDetailScroll = (int)Math.round(possibleDetailScrollVisual);

            graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
            activeTooltipClip = viewport;
            activeClickClip = viewport;
            int y = viewport.y() + 4 - displayedDetailScroll;
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
                bookScrollVisual = 0.0;
                possibleDetailScroll = 0;
                possibleDetailScrollVisual = 0.0;
                motion.selectionChanged(System.nanoTime());
            });
            activeClickClip = null;
            activeTooltipClip = null;
            graphics.disableScissor();
            drawPixelScrollbar(graphics, viewport, contentHeight, possibleDetailScrollVisual,
                    ScrollRegion.POSSIBLE_DETAIL);
        } else {
            TextLayout receives = planText("Receives", textWidth, 2, MIN_TEXT_SCALE);
            TextLayout available = planText(
                    "Available at: " + levelName(trade.professionLevel()), textWidth, 3, MIN_TEXT_SCALE
            );
            int contentHeight = 4 + titleLayout.height() + 3 + directionLayout.height() + 6
                    + 16 + 7 + receives.height() + 2 + 16 + 6 + available.height() + 4;
            possibleDetailMaxScroll = Math.max(0, contentHeight - viewport.height());
            possibleDetailScroll = clamp(possibleDetailScroll, 0, possibleDetailMaxScroll);
            possibleDetailScrollVisual = Math.max(0.0,
                    Math.min(possibleDetailMaxScroll, possibleDetailScrollVisual));
            possibleDetailScrollVisual = animateScroll(
                    ScrollRegion.POSSIBLE_DETAIL, possibleDetailScrollVisual, possibleDetailScroll
            );
            int displayedDetailScroll = (int)Math.round(possibleDetailScrollVisual);

            graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
            activeTooltipClip = viewport;
            int y = viewport.y() + 4 - displayedDetailScroll;
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
            drawPixelScrollbar(graphics, viewport, contentHeight, possibleDetailScrollVisual,
                    ScrollRegion.POSSIBLE_DETAIL);
        }
    }

    private void drawBookBrowser(
            GuiGraphicsExtractor graphics,
            Rect body,
            LibrarianTradeMode mode,
            int mouseX,
            int mouseY
    ) {
        boolean focusedDetail = body.width() < 470;
        int toolbarHeight = focusedDetail ? 44 : 25;
        Rect toolbar = new Rect(body.x(), body.y(), body.width(), toolbarHeight);
        panel(graphics, toolbar);

        String normalizedQuery = bookQuery.strip().toLowerCase(Locale.ROOT);
        List<LibrarianEnchantedBookReference> books = availableBooks(mode).stream()
                .filter(book -> book.professionLevels().contains(bookProfessionLevel))
                .filter(book -> normalizedQuery.isEmpty()
                        || bookName(book).toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .toList();
        if (selectedBook != null && !books.contains(selectedBook)) {
            selectedBook = null;
            possibleDetailScroll = 0;
            possibleDetailScrollVisual = 0.0;
        }

        Rect back = new Rect(toolbar.x() + 5, toolbar.y() + 4, 42, 17);
        drawSmallButton(graphics, back, "Back", false, mouseX, mouseY, () -> {
            browsingBooks = false;
            activeScrollbarDrag = null;
            if (bookSearch != null) {
                bookSearch.setFocused(false);
            }
        });
        int searchWidth = focusedDetail ? toolbar.width() - 10 : Math.min(230, toolbar.width() / 2);
        int searchX = focusedDetail ? toolbar.x() + 5 : toolbar.right() - searchWidth - 5;
        Rect searchBounds = new Rect(searchX, focusedDetail ? toolbar.y() + 24 : toolbar.y() + 4,
                Math.max(1, searchWidth), 17);
        String heading = levelName(bookProfessionLevel) + " enchantments"
                + (mode == LibrarianTradeMode.TRADE_REBALANCE ? " · Rebalanced" : "");
        int titleRight = focusedDetail ? toolbar.right() - 5 : searchBounds.x() - 7;
        drawFittedText(graphics, heading,
                new Rect(back.right() + 6, toolbar.y() + 3, Math.max(1, titleRight - back.right() - 6), 19),
                2, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headingText());
        configureBookSearch(graphics, searchBounds);

        Rect browserBody = new Rect(body.x(), toolbar.bottom() + 4, body.width(),
                Math.max(1, body.bottom() - toolbar.bottom() - 4));
        if (focusedDetail && selectedBook != null) {
            drawBookDetailPanel(graphics, browserBody, selectedBook, true, mouseX, mouseY);
            return;
        }

        int listWidth = focusedDetail ? browserBody.width() : Math.max(300, browserBody.width() * 2 / 3);
        listWidth = Math.min(browserBody.width(), listWidth);
        Rect listPanel = new Rect(browserBody.x(), browserBody.y(), listWidth, browserBody.height());
        panel(graphics, listPanel);
        Rect detail = focusedDetail ? null : new Rect(listPanel.right() + 5, browserBody.y(),
                Math.max(1, browserBody.right() - listPanel.right() - 5), browserBody.height());
        if (detail != null) {
            panel(graphics, detail);
        }

        drawFittedText(graphics, "Enchantment catalog · " + books.size(),
                new Rect(listPanel.x() + 6, listPanel.y() + 3, listPanel.width() - 12, 14),
                1, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headingText());
        Rect rows = new Rect(listPanel.x() + 3, listPanel.y() + 19,
                listPanel.width() - 6, Math.max(1, listPanel.height() - 22));
        drawBookGrid(graphics, rows, books, mouseX, mouseY);

        if (detail == null) {
            return;
        }
        if (selectedBook == null) {
            Rect inner = inset(detail, 7);
            ItemStack icon = new ItemStack(Items.ENCHANTED_BOOK);
            int iconX = inner.x() + Math.max(0, (inner.width() - 16) / 2);
            drawIcon(graphics, icon, iconX, inner.y() + 6, mouseX, mouseY, false);
            String message = books.isEmpty()
                    ? normalizedQuery.isEmpty()
                            ? "No enchanted books are available for this level and variant."
                            : "No enchanted books match \u201c" + bookQuery.strip() + "\u201d."
                    : "Select an enchantment to inspect its cost, level, and variant rules.";
            drawFittedText(graphics, message,
                    new Rect(inner.x(), inner.y() + 30, inner.width(), Math.max(1, inner.height() - 30)),
                    8, MIN_TEXT_SCALE, TextAlignment.CENTER, false, palette.secondaryText());
            return;
        }
        drawBookDetailContent(graphics, inset(detail, 5), selectedBook, mouseX, mouseY);
    }

    private void configureBookSearch(GuiGraphicsExtractor graphics, Rect bounds) {
        boolean searchFocused = bookSearch != null && bookSearch.isFocused();
        graphics.fill(bounds.x() + 1, bounds.y() + 2, bounds.right() + 1,
                bounds.bottom() + 2, LibrarianScreenMotion.withAlpha(palette.shadow(), 0.45F));
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), palette.slot());
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                searchFocused ? palette.selectedBorder() : palette.slotShadow());
        if (bookSearch != null) {
            bookSearch.visible = true;
            // EditBox maintains derived text coordinates in its individual setters.
            // Calling AbstractWidget#setRectangle bypasses those updates in 26.3.
            bookSearch.setX(bounds.x() + 5);
            bookSearch.setY(bounds.y() + 2);
            bookSearch.setWidth(Math.max(1, bounds.width() - 10));
            bookSearch.setHeight(Math.max(1, bounds.height() - 4));
        }
    }

    private void drawBookGrid(
            GuiGraphicsExtractor graphics,
            Rect rows,
            List<LibrarianEnchantedBookReference> books,
            int mouseX,
            int mouseY
    ) {
        int columns = rows.width() >= 270 ? 2 : 1;
        bookGridColumns = columns;
        int gap = 3;
        int usableWidth = Math.max(1, rows.width() - 5);
        int cellWidth = Math.max(1, (usableWidth - gap * (columns - 1)) / columns);
        int rowHeight = Math.max(BOOK_ROW_HEIGHT, bookRowHeight(books, cellWidth));
        int rowPitch = rowHeight + gap;
        int totalRows = (books.size() + columns - 1) / columns;
        int visibleRows = Math.max(1, (rows.height() + gap) / rowPitch);
        int maxBookScroll = Math.max(0, totalRows - visibleRows);
        bookScroll = clamp(bookScroll, 0, maxBookScroll);
        bookScrollVisual = Math.max(0.0, Math.min(maxBookScroll, bookScrollVisual));
        bookScrollVisual = animateScroll(ScrollRegion.BOOK_LIST, bookScrollVisual, bookScroll);
        int firstRow = clamp((int)Math.floor(bookScrollVisual), 0, maxBookScroll);
        int rowOffset = (int)Math.round((bookScrollVisual - firstRow) * rowPitch);

        graphics.enableScissor(rows.x(), rows.y(), rows.right(), rows.bottom());
        activeTooltipClip = rows;
        for (int visibleRow = 0; visibleRow <= visibleRows; visibleRow++) {
            int gridRow = firstRow + visibleRow;
            for (int column = 0; column < columns; column++) {
                int index = gridRow * columns + column;
                if (index >= books.size()) {
                    break;
                }
                LibrarianEnchantedBookReference book = books.get(index);
                Rect cell = new Rect(rows.x() + column * (cellWidth + gap),
                        rows.y() + visibleRow * rowPitch - rowOffset, cellWidth, rowHeight);
                Rect visibleCell = intersection(cell, rows);
                boolean selected = book.equals(selectedBook);
                boolean hovered = visibleCell != null && visibleCell.contains(mouseX, mouseY);
                selectableSurface(graphics, cell, book, selected, hovered);
                ItemStack icon = book.iconStack();
                drawIcon(graphics, icon, cell.x() + 4, cell.y() + (cell.height() - 16) / 2,
                        mouseX, mouseY, false);
                drawFittedText(graphics, bookName(book),
                        new Rect(cell.x() + 24, cell.y() + 2, Math.max(10, cell.width() - 28), cell.height() - 4),
                        2, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.primaryText());
                if (visibleCell != null) {
                    hitTargets.add(new HitTarget(visibleCell, () -> {
                        selectedBook = book;
                        possibleDetailScroll = 0;
                        possibleDetailScrollVisual = 0.0;
                        motion.selectionChanged(System.nanoTime());
                    }));
                }
            }
        }
        activeTooltipClip = null;
        graphics.disableScissor();
        drawScrollbar(graphics, rows, totalRows, visibleRows, bookScrollVisual, ScrollRegion.BOOK_LIST);
    }

    private void drawBookDetailPanel(
            GuiGraphicsExtractor graphics,
            Rect panelBounds,
            LibrarianEnchantedBookReference book,
            boolean showResultsButton,
            int mouseX,
            int mouseY
    ) {
        panel(graphics, panelBounds);
        int topInset = 5;
        if (showResultsButton) {
            Rect results = new Rect(panelBounds.x() + 5, panelBounds.y() + 4, 54, 17);
            drawSmallButton(graphics, results, "Results", false, mouseX, mouseY, () -> {
                selectedBook = null;
                possibleDetailScroll = 0;
                possibleDetailScrollVisual = 0.0;
                activeScrollbarDrag = null;
                motion.selectionChanged(System.nanoTime());
            });
            drawFittedText(graphics, bookName(book),
                    new Rect(results.right() + 6, panelBounds.y() + 3,
                            Math.max(1, panelBounds.right() - results.right() - 11), 19),
                    2, MIN_TEXT_SCALE, TextAlignment.LEFT, true, palette.headingText());
            topInset = 25;
        }
        Rect viewport = new Rect(panelBounds.x() + 5, panelBounds.y() + topInset,
                Math.max(1, panelBounds.width() - 10), Math.max(1, panelBounds.height() - topInset - 5));
        drawBookDetailContent(graphics, viewport, book, mouseX, mouseY);
    }

    private void drawBookDetailContent(
            GuiGraphicsExtractor graphics,
            Rect viewport,
            LibrarianEnchantedBookReference book,
            int mouseX,
            int mouseY
    ) {
        activeDetailBounds = viewport;
        int textWidth = Math.max(12, viewport.width() - 2);
        TextLayout titleLayout = planText(bookName(book), textWidth, 4, MIN_TEXT_SCALE);
        TextLayout possible = planText("Possible through Librarian: Yes", textWidth, 4, MIN_TEXT_SCALE);
        TextLayout minimum = planText("Minimum", textWidth, 2, MIN_TEXT_SCALE);
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
        possibleDetailScrollVisual = Math.max(0.0,
                Math.min(possibleDetailMaxScroll, possibleDetailScrollVisual));
        possibleDetailScrollVisual = animateScroll(
                ScrollRegion.POSSIBLE_DETAIL, possibleDetailScrollVisual, possibleDetailScroll
        );
        int displayedDetailScroll = (int)Math.round(possibleDetailScrollVisual);

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        activeTooltipClip = viewport;
        int y = viewport.y() + 4 - displayedDetailScroll;
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
        drawPixelScrollbar(graphics, viewport, contentHeight, possibleDetailScrollVisual,
                ScrollRegion.POSSIBLE_DETAIL);
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
        LecternAssociation association = LibrarianInsight.lecternManager.getAssociationForMenu(lecternPos);
        if (association == null) {
            return Optional.empty();
        }
        KnownLibrarianSnapshot snapshot = LibrarianInsight.enchantmentManager.getOfferSnapshot(association.villagerUuid());
        return snapshot == null ? Optional.empty() : snapshot.villagerType();
    }

    private void updateCachedOffers(@Nullable KnownLibrarianSnapshot snapshot) {
        if (snapshot != cachedSnapshot) {
            cachedSnapshot = snapshot;
            cachedOffers = snapshot == null ? new MerchantOffers() : snapshot.offersCopy();
            selectedCurrent = LibrarianMinimumPrice.enchantedBookOfferIndex(cachedOffers);
            currentScroll = selectedCurrent;
            currentScrollVisual = selectedCurrent;
            currentDetailScroll = 0;
            currentDetailScrollVisual = 0.0;
        }
    }

    private void requestStatusSnapshot(boolean force) {
        if (minecraft.level == null || LibrarianInsight.villagerStatusService == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (!force && (statusRequestInFlight || now - lastStatusRequestAt < 20L)) {
            return;
        }
        LecternAssociation retained = LibrarianInsight.lecternManager.getRetainedAssociation(lecternPos);
        UUID fallback = retained == null ? null : retained.villagerUuid();
        Object requestedLevel = minecraft.level;
        long generation = ++statusRequestGeneration;
        statusRequestInFlight = true;
        lastStatusRequestAt = now;
        LibrarianInsight.villagerStatusService.request(lecternPos, fallback, result -> {
            if (generation != statusRequestGeneration
                    || minecraft.gui.screen() != this
                    || minecraft.level != requestedLevel
                    || minecraft.level == null
                    || !minecraft.level.getBlockState(lecternPos).is(Blocks.LECTERN)) {
                return;
            }
            statusRequestInFlight = false;
            statusResult = result;
            VillagerStatusSnapshot snapshot = result.snapshot();
            if (snapshot != null && snapshot.serverConfirmedOwner()) {
                LibrarianInsight.lecternManager.confirmIntegratedServerAssociation(
                        lecternPos, snapshot.villagerUuid()
                );
            } else if (snapshot != null
                    && result.availability() == IntegratedVillagerStatusService.Availability.INTEGRATED_FALLBACK
                    && snapshot.jobSite().filter(lecternPos::equals).isEmpty()) {
                LibrarianInsight.lecternManager.invalidateIntegratedServerAssociation(
                        lecternPos, snapshot.villagerUuid()
                );
            }
        });
    }

    private @Nullable VillagerStatusSnapshot integratedStatus() {
        return statusResult == null ? null : statusResult.snapshot();
    }

    private @Nullable LecternAssociation statusAssociation() {
        LecternAssociation retained = LibrarianInsight.lecternManager.getRetainedAssociation(lecternPos);
        if (retained != null) {
            return retained;
        }
        if (statusResult != null
                && statusResult.availability() != IntegratedVillagerStatusService.Availability.REMOTE_SERVER
                && statusResult.availability() != IntegratedVillagerStatusService.Availability.NO_LOADED_OWNER) {
            return null;
        }
        return LibrarianInsight.lecternManager.getAssociationForMenu(lecternPos);
    }

    private @Nullable KnownLibrarianSnapshot statusKnownSnapshot() {
        VillagerStatusSnapshot integrated = integratedStatus();
        if (integrated != null) {
            KnownLibrarianSnapshot known = LibrarianInsight.enchantmentManager.getOfferSnapshot(integrated.villagerUuid());
            if (known != null) {
                return known;
            }
        }
        LecternAssociation association = statusAssociation();
        return association == null
                ? null
                : LibrarianInsight.enchantmentManager.getOfferSnapshot(association.villagerUuid());
    }

    private @Nullable Villager clientVillager(@Nullable LecternAssociation association) {
        if (association == null) {
            return null;
        }
        for (Villager villager : LibrarianInsight.enchantmentManager.getTrackedVillagers()) {
            if (villager.getUUID().equals(association.villagerUuid()) && villager.isAlive()) {
                return villager;
            }
        }
        return null;
    }

    private float @Nullable [] statusHealth(
            @Nullable VillagerStatusSnapshot snapshot,
            @Nullable LecternAssociation association
    ) {
        if (snapshot != null) {
            return new float[]{snapshot.health(), snapshot.maximumHealth()};
        }
        Villager villager = clientVillager(association);
        return villager == null ? null : new float[]{villager.getHealth(), villager.getMaxHealth()};
    }

    private VillagerStatusSnapshot.Hero clientHero() {
        if (minecraft.player == null) {
            return VillagerStatusSnapshot.Hero.inactive();
        }
        MobEffectInstance effect = minecraft.player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        return effect == null
                ? VillagerStatusSnapshot.Hero.inactive()
                : new VillagerStatusSnapshot.Hero(
                        true, effect.getAmplifier() + 1, effect.getDuration(), effect.isInfiniteDuration()
                );
    }

    private static int availableOffers(@Nullable KnownLibrarianSnapshot known) {
        if (known == null) {
            return 0;
        }
        return (int)known.offersCopy().stream().filter(offer -> !offer.isOutOfStock()).count();
    }

    private static boolean matchingStatusTrade(VillagerStatusSnapshot.Trade trade, MerchantOffer offer) {
        if (!ItemStack.isSameItemSameComponents(trade.result(), offer.getResult())
                || !ItemStack.isSameItemSameComponents(trade.baseCost(), offer.getBaseCostA())) {
            return false;
        }
        Optional<ItemStack> expectedSecond = trade.secondCost();
        ItemStack actualSecond = offer.getCostB();
        return expectedSecond.isEmpty()
                ? actualSecond.isEmpty()
                : !actualSecond.isEmpty() && ItemStack.isSameItemSameComponents(expectedSecond.get(), actualSecond);
    }

    private static String activityLabel(VillagerStatusSnapshot snapshot) {
        if (snapshot.workingAtLectern()) {
            return "Working at lectern";
        }
        return switch (snapshot.activity()) {
            case WORK -> "Work period";
            case MEET -> "Meeting";
            case REST -> "Rest period";
            case IDLE -> "Idle";
            case PANIC -> "Panicking";
            case PRE_RAID -> "Preparing for raid";
            case RAID -> "Raid activity";
            case HIDE -> "Hiding";
            case PLAY -> "Playing";
            case SLEEPING -> "Sleeping";
            case UNKNOWN -> "Activity unavailable";
        };
    }

    private static String restockSummary(VillagerStatusSnapshot snapshot) {
        return switch (snapshot.restock().state()) {
            case NOT_NEEDED -> "No restock needed";
            case READY_WHEN_WORKS -> "Ready when villager works";
            case COOLDOWN -> "Waiting for cooldown";
            case DAILY_LIMIT -> "Daily limit reached";
            case NO_WORKSTATION -> "Needs an assigned lectern";
            case MUST_REACH_LECTERN -> "Must reach lectern";
            case WAITING_FOR_WORK -> "Waiting for work period";
        };
    }

    private static Tone restockTone(VillagerStatusCalculations.RestockState state) {
        return switch (state) {
            case NOT_NEEDED, READY_WHEN_WORKS -> Tone.SUCCESS;
            case COOLDOWN, MUST_REACH_LECTERN, WAITING_FOR_WORK -> Tone.WARNING;
            case DAILY_LIMIT, NO_WORKSTATION -> Tone.DANGER;
        };
    }

    private int toneColor(Tone tone) {
        return switch (tone) {
            case HEADING -> palette.headingText();
            case NORMAL -> palette.primaryText();
            case MUTED -> palette.secondaryText();
            case SUCCESS -> palette.successText();
            case WARNING -> palette.warningText();
            case DANGER -> palette.dangerText();
        };
    }

    private static ItemStack statusIcon(StatusCard card) {
        return switch (card) {
            case WORKSTATION -> Items.LECTERN.getDefaultInstance();
            case HEALTH -> Items.APPLE.getDefaultInstance();
            case RESTOCK -> Items.CLOCK.getDefaultInstance();
            case TRADES -> Items.ENCHANTED_BOOK.getDefaultInstance();
            case STANDING -> Items.EMERALD.getDefaultInstance();
            case LEVEL -> Items.EXPERIENCE_BOTTLE.getDefaultInstance();
            case HERO -> Items.TOTEM_OF_UNDYING.getDefaultInstance();
        };
    }

    private static StatusDetailRow detail(
            String text,
            @Nullable ItemStack icon,
            Tone tone,
            boolean heading
    ) {
        return new StatusDetailRow(text, Optional.ofNullable(icon).map(ItemStack::copy), tone, heading);
    }

    private static String standingLabel(int reputation) {
        return reputation > 0 ? "Positive" : reputation < 0 ? "Negative" : "Neutral";
    }

    private static String helpingLabel(int reputation) {
        return reputation > 0 ? "Helping" : reputation < 0 ? "Hurting" : "Neutral";
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String formatNumber(float value) {
        return value == Math.round(value)
                ? Integer.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String healthHearts(float health, float maximumHealth) {
        int hearts = Math.max(1, (int)Math.ceil(maximumHealth / 2.0F));
        int filled = clamp((int)Math.ceil(health / 2.0F), 0, hearts);
        return "\u2665 ".repeat(filled) + "\u2661 ".repeat(hearts - filled);
    }

    private static String progressBar(int xp, int minimum, int maximum) {
        int sections = 16;
        float progress = maximum <= minimum ? 1.0F : (xp - minimum) / (float)(maximum - minimum);
        int filled = clamp(Math.round(progress * sections), 0, sections);
        return "\u2588".repeat(filled) + "\u2591".repeat(sections - filled);
    }

    private static String ticksToDuration(long ticks) {
        long totalSeconds = Math.max(0L, ticks / 20L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + "m " + String.format(Locale.ROOT, "%02ds", seconds);
    }

    private static String duration(VillagerStatusSnapshot.Hero hero) {
        return hero.infinite() ? "Infinite" : ticksToDuration(hero.remainingTicks());
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
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
        graphics.fill(x - 1, y - 1, x + 17, y + 17, palette.slotShadow());
        graphics.fill(x, y, x + 16, y + 16, palette.slot());
        graphics.horizontalLine(x, x + 15, y, palette.innerBorder());
        graphics.verticalLine(x, y, y + 15, palette.innerBorder());
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
            net.minecraft.world.item.Item icon,
            Tab target,
            int mouseX,
            int mouseY
    ) {
        boolean active = tab == target;
        boolean hovered = rect.contains(mouseX, mouseY);
        float hover = motion.hover(target, hovered && !active);
        int base = active ? palette.panel() : palette.headerBottom();
        int fill = LibrarianScreenMotion.mix(base, active ? palette.selected() : palette.hovered(), hover);
        graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.bottom(),
                LibrarianScreenMotion.mix(fill, palette.innerBorder(), active ? 0.16F : 0.04F), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(),
                active ? palette.selectedBorder() : palette.border());
        int iconY = rect.y() + (rect.height() - 16) / 2;
        graphics.fakeItem(new ItemStack(icon), rect.x() + 5, iconY);
        drawFittedText(graphics, label,
                new Rect(rect.x() + 24, rect.y() + 1, Math.max(1, rect.width() - 27), rect.height() - 2),
                2, MIN_TEXT_SCALE, TextAlignment.CENTER, true,
                active ? palette.primaryText() : palette.headerText());
        hitTargets.add(new HitTarget(rect, () -> switchTab(target)));
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
        float hover = motion.hover(label, hovered && !active);
        int fill = LibrarianScreenMotion.mix(active ? palette.selected() : palette.clickableBox(),
                palette.hovered(), hover);
        graphics.fill(rect.x() + 1, rect.y() + 2, rect.right() + 1, rect.bottom() + 2,
                LibrarianScreenMotion.withAlpha(palette.shadow(), 0.58F));
        graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.bottom(),
                LibrarianScreenMotion.mix(fill, palette.innerBorder(), 0.12F), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(),
                active ? palette.selectedBorder() : palette.border());
        graphics.horizontalLine(rect.x() + 2, rect.right() - 3, rect.y() + 1,
                active ? palette.accentBright() : palette.innerBorder());
        drawFittedText(graphics, label, inset(rect, 2),
                2, MIN_TEXT_SCALE, TextAlignment.CENTER, true, palette.primaryText());
        Rect hitbox = activeClickClip == null ? rect : intersection(rect, activeClickClip);
        if (hitbox != null) {
            hitTargets.add(new HitTarget(hitbox, action));
        }
    }

    private void drawCloseButton(GuiGraphicsExtractor graphics, Rect rect, int mouseX, int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        float hover = motion.hover(CLOSE_BUTTON_MOTION_KEY, hovered);
        int fill = LibrarianScreenMotion.mix(palette.clickableBox(), palette.hovered(), hover);
        graphics.fill(rect.x() + 1, rect.y() + 2, rect.right() + 1, rect.bottom() + 2,
                LibrarianScreenMotion.withAlpha(palette.shadow(), 0.58F));
        graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.bottom(),
                LibrarianScreenMotion.mix(fill, palette.innerBorder(), 0.12F), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), palette.border());
        graphics.horizontalLine(rect.x() + 2, rect.right() - 3, rect.y() + 1, palette.innerBorder());

        String glyph = "\u00D7";
        // The Minecraft multiplication glyph sits optically high and left in its
        // advance box. These corrections center the visible strokes, not the box.
        int glyphX = rect.x() + (rect.width() - font.width(glyph)) / 2 + 1;
        int glyphY = rect.y() + (rect.height() - font.lineHeight) / 2 + 2;
        graphics.text(font, glyph, glyphX, glyphY, palette.primaryText(), false);
        hitTargets.add(new HitTarget(rect, this::onClose));
    }

    private void panel(GuiGraphicsExtractor graphics, Rect rect) {
        graphics.fill(rect.x() + 2, rect.y() + 3, rect.right() + 2, rect.bottom() + 3,
                LibrarianScreenMotion.withAlpha(palette.shadow(), 0.48F));
        graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.bottom(), palette.panel(), palette.panelShade());
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), palette.border());
        if (rect.width() > 4 && rect.height() > 4) {
            graphics.horizontalLine(rect.x() + 2, rect.right() - 3, rect.y() + 1, palette.innerBorder());
            graphics.verticalLine(rect.x() + 1, rect.y() + 2, rect.bottom() - 3, palette.innerBorder());
        }
        graphics.fill(rect.x() + 2, rect.y() + 2, rect.x() + 4, rect.y() + 4, palette.accent());
    }

    private void selectableSurface(
            GuiGraphicsExtractor graphics,
            Rect rect,
            Object key,
            boolean selected,
            boolean hovered
    ) {
        float hover = motion.hover(key, hovered && !selected);
        int fill = LibrarianScreenMotion.mix(selected ? palette.selected() : palette.clickableBox(),
                palette.hovered(), hover);
        if (selected) {
            fill = LibrarianScreenMotion.mix(fill, palette.accentBright(), motion.selectionPulse(frameTime) * 0.10F);
        }
        graphics.fillGradient(rect.x(), rect.y(), rect.right(), rect.bottom(),
                LibrarianScreenMotion.mix(fill, palette.innerBorder(), 0.08F), fill);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(),
                selected ? palette.selectedBorder() : LibrarianScreenMotion.mix(palette.separator(),
                        palette.selectedBorder(), hover));
        if (selected) {
            graphics.fill(rect.x(), rect.y() + 2, rect.x() + 3, rect.bottom() - 2, palette.accentBright());
        } else if (hover > 0.02F) {
            graphics.fill(rect.x(), rect.y() + 2, rect.x() + 2, rect.bottom() - 2,
                    LibrarianScreenMotion.withAlpha(palette.accent(), hover));
        }
    }

    private void drawScrollbar(
            GuiGraphicsExtractor graphics,
            Rect area,
            int total,
            int visible,
            double first,
            ScrollRegion region
    ) {
        LibrarianScrollbar.Geometry geometry = LibrarianScrollbar.rows(
                area.right() - 4, area.y(), area.height(), total, visible, first
        );
        drawScrollbar(graphics, geometry, region);
    }

    private void drawPixelScrollbar(
            GuiGraphicsExtractor graphics,
            Rect area,
            int contentHeight,
            double scroll,
            ScrollRegion region
    ) {
        LibrarianScrollbar.Geometry geometry = LibrarianScrollbar.pixels(
                area.right() - 3, area.y(), area.height(), contentHeight, scroll
        );
        drawScrollbar(graphics, geometry, region);
    }

    private void drawScrollbar(
            GuiGraphicsExtractor graphics,
            LibrarianScrollbar.@Nullable Geometry geometry,
            ScrollRegion region
    ) {
        if (geometry == null) {
            return;
        }
        boolean dragging = activeScrollbarDrag != null && activeScrollbarDrag.region() == region;
        int thumb = dragging ? palette.selectedBorder() : palette.scrollbarThumb();
        graphics.fill(geometry.x(), geometry.y(), geometry.right(), geometry.bottom(), palette.scrollbarTrack());
        graphics.fill(geometry.x(), geometry.thumbY(), geometry.right(), geometry.thumbBottom(), thumb);
        graphics.fill(geometry.x() + 1, geometry.thumbY() + 1, geometry.right(), geometry.thumbBottom() - 1,
                palette.accentBright());
        scrollbarTargets.add(new ScrollbarTarget(region, geometry));
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
        if (tab == newTab) {
            return;
        }
        activeScrollbarDrag = null;
        previousTab = tab;
        tab = newTab;
        browsingBooks = false;
        if (bookSearch != null) {
            bookSearch.setFocused(false);
        }
        currentDetailScroll = 0;
        currentDetailScrollVisual = 0.0;
        possibleDetailScroll = 0;
        possibleDetailScrollVisual = 0.0;
        motion.tabChanged(System.nanoTime());
        if (newTab == Tab.STATUS) {
            requestStatusSnapshot(false);
        }
    }

    private void selectLevel(int level) {
        activeScrollbarDrag = null;
        selectedProfessionLevel = level;
        selectedPossible = null;
        selectedBook = null;
        browsingBooks = false;
        possibleGridScroll = 0;
        possibleGridScrollVisual = 0.0;
        possibleDetailScroll = 0;
        possibleDetailScrollVisual = 0.0;
        if (bookSearch != null) {
            bookSearch.setFocused(false);
        }
        motion.selectionChanged(System.nanoTime());
    }

    private void selectPossible(LibrarianTradeReference trade) {
        activeScrollbarDrag = null;
        selectedPossible = trade;
        possibleDetailScroll = 0;
        possibleDetailScrollVisual = 0.0;
        motion.selectionChanged(System.nanoTime());
        if (trade.enchantedBookSelector()) {
            browsingBooks = true;
            bookProfessionLevel = trade.professionLevel();
            selectedBook = null;
            bookScroll = 0;
            bookScrollVisual = 0.0;
        }
    }

    private @Nullable ScrollbarTarget scrollbarAt(double mouseX, double mouseY) {
        for (int index = scrollbarTargets.size() - 1; index >= 0; index--) {
            ScrollbarTarget target = scrollbarTargets.get(index);
            if (target.geometry().containsThumb(mouseX, mouseY)) {
                return target;
            }
        }
        return null;
    }

    private @Nullable ScrollbarTarget scrollbarFor(ScrollRegion region) {
        for (int index = scrollbarTargets.size() - 1; index >= 0; index--) {
            ScrollbarTarget target = scrollbarTargets.get(index);
            if (target.region() == region) {
                return target;
            }
        }
        return null;
    }

    private double animateScroll(ScrollRegion region, double displayed, double target) {
        return activeScrollbarDrag != null && activeScrollbarDrag.region() == region
                ? displayed
                : motion.scroll(displayed, target);
    }

    private void setScrollFromDrag(ScrollRegion region, double value) {
        int target = (int)Math.round(value);
        switch (region) {
            case CURRENT_LIST -> {
                currentScroll = target;
                currentScrollVisual = value;
            }
            case CURRENT_DETAIL -> {
                currentDetailScroll = target;
                currentDetailScrollVisual = value;
            }
            case POSSIBLE_GRID -> {
                possibleGridScroll = target;
                possibleGridScrollVisual = value;
            }
            case POSSIBLE_DETAIL -> {
                possibleDetailScroll = target;
                possibleDetailScrollVisual = value;
            }
            case BOOK_LIST -> {
                bookScroll = target;
                bookScrollVisual = value;
            }
            case STATUS_DASHBOARD -> {
                statusScroll = target;
                statusScrollVisual = value;
            }
            case STATUS_DETAIL -> {
                statusDetailScroll = target;
                statusDetailScrollVisual = value;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ScrollbarTarget scrollbar = scrollbarAt(event.x(), event.y());
            if (scrollbar != null) {
                activeScrollbarDrag = new ScrollbarDrag(
                        scrollbar.region(), event.y() - scrollbar.geometry().thumbY()
                );
                return true;
            }
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
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (activeScrollbarDrag != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ScrollbarTarget target = scrollbarFor(activeScrollbarDrag.region());
            if (target == null) {
                activeScrollbarDrag = null;
                return true;
            }
            double value = target.geometry().valueForPointer(event.y(), activeScrollbarDrag.grabOffset());
            setScrollFromDrag(target.region(), value);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (activeScrollbarDrag != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            activeScrollbarDrag = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int direction = scrollY > 0.0 ? -1 : 1;
        if (tab == Tab.STATUS && activeStatusBounds != null && activeStatusBounds.contains(mouseX, mouseY)) {
            if (selectedStatusCard == null) {
                statusScroll = clamp(statusScroll + direction * 16, 0, statusMaxScroll);
            } else {
                statusDetailScroll = clamp(statusDetailScroll + direction * 14, 0, statusDetailMaxScroll);
            }
            return true;
        }
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
            bookScroll = Math.max(0, bookScroll + direction * (bookGridColumns > 1 ? 1 : 2));
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

    @Override
    public void removed() {
        activeScrollbarDrag = null;
        statusRequestGeneration++;
        statusRequestInFlight = false;
        super.removed();
    }

    private LibrarianTradeMode currentMode() {
        return minecraft.level != null
                ? LibrarianTradeMode.fromEnabledFeatures(minecraft.level.enabledFeatures())
                : LibrarianTradeMode.STANDARD;
    }

    private Layout layout() {
        LibrarianScreenLayout.Spec spec = layoutSpec == null
                ? LibrarianScreenLayout.resolve(width, height, 0)
                : layoutSpec;
        return new Layout(spec.x(), spec.y(), spec.width(), spec.height(),
                spec.headerHeight(), spec.footerHeight(), spec.compact());
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

    private static String levelButtonName(int level, int availableWidth) {
        if (availableWidth >= 390) {
            return levelName(level);
        }
        return switch (level) {
            case 2 -> "Apprent.";
            case 3 -> "Journey.";
            default -> levelName(level);
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
        POSSIBLE,
        STATUS
    }

    private enum StatusCard {
        WORKSTATION("Workstation & Activity"),
        HEALTH("Health"),
        RESTOCK("Restock"),
        TRADES("Trade Availability"),
        STANDING("Player Standing & Prices"),
        LEVEL("Librarian Level"),
        HERO("Hero of the Village");

        private final String title;

        StatusCard(String title) {
            this.title = title;
        }
    }

    private enum Tone {
        HEADING,
        NORMAL,
        MUTED,
        SUCCESS,
        WARNING,
        DANGER
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

    private record CardSummary(String text, Tone tone) {
    }

    private record StatusDetailRow(String text, Optional<ItemStack> icon, Tone tone, boolean heading) {
        private StatusDetailRow {
            icon = icon.map(ItemStack::copy);
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

    private record Layout(
            int x,
            int y,
            int width,
            int height,
            int headerHeight,
            int footerHeight,
            boolean compact
    ) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        int contentHeight() {
            return Math.max(1, height - headerHeight - footerHeight);
        }
    }

    private enum ScrollRegion {
        CURRENT_LIST,
        CURRENT_DETAIL,
        POSSIBLE_GRID,
        POSSIBLE_DETAIL,
        BOOK_LIST,
        STATUS_DASHBOARD,
        STATUS_DETAIL
    }

    private record ScrollbarTarget(ScrollRegion region, LibrarianScrollbar.Geometry geometry) {
    }

    private record ScrollbarDrag(ScrollRegion region, double grabOffset) {
    }

    private record HitTarget(Rect bounds, Runnable action) {
    }
}
