package librarianinsight.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class LibrarianMinimumPriceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        DataComponentMap stackable = DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64)
                .build();
        for (var item : new net.minecraft.world.item.Item[] {
                Items.PAPER, Items.EMERALD, Items.BOOK, Items.ENCHANTED_BOOK
        }) {
            if (!item.builtInRegistryHolder().areComponentsBound()) {
                item.builtInRegistryHolder().bindComponents(stackable);
            }
        }
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
