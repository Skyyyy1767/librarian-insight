package librarianinsight.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class LibrarianTradeCatalogTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void usesThe1211LevelFiveTrades() {
        var standard = LibrarianTradeCatalog.possibleTradesAtLevel(
                LibrarianTradeMode.STANDARD, Optional.empty(), 5
        );
        assertEquals(1, standard.size());
        assertEquals(Items.NAME_TAG, standard.getFirst().result().item());
        assertEquals(20, standard.getFirst().naturalCost().orElseThrow().first().count().minimum());
        assertFalse(standard.stream().anyMatch(trade -> trade.result().item() == Items.YELLOW_CANDLE));
        assertFalse(standard.stream().anyMatch(trade -> trade.result().item() == Items.RED_CANDLE));

        var rebalanced = LibrarianTradeCatalog.possibleTradesAtLevel(
                LibrarianTradeMode.TRADE_REBALANCE, Optional.empty(), 5
        );
        assertTrue(rebalanced.stream().anyMatch(LibrarianTradeReference::enchantedBookSelector));
        assertTrue(rebalanced.stream().anyMatch(trade -> trade.result().item() == Items.NAME_TAG));
    }

    @Test
    void selectsTheEnchantedBookWhenItIsNotTheFirstOffer() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(
                new ItemCost(Items.PAPER, 24),
                Items.EMERALD.getDefaultInstance(),
                16,
                2,
                0.05F
        ));
        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 10),
                Optional.of(new ItemCost(Items.BOOK)),
                Items.ENCHANTED_BOOK.getDefaultInstance(),
                12,
                1,
                0.2F
        ));

        int selected = LibrarianMinimumPrice.enchantedBookOfferIndex(offers);

        assertEquals(1, selected);
        assertEquals(10, LibrarianMinimumPrice.currentCost(offers.get(selected)).first().getCount());
        assertEquals(Items.ENCHANTED_BOOK, offers.get(selected).getResult().getItem());
    }
}
