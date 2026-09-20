package librarianinsight.trade;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/** Recognizes stock 26.3 offers and returns their theoretical natural minimum/range. */
public final class LibrarianMinimumPrice {
    private LibrarianMinimumPrice() {
    }

    /**
     * Resolves a packet-provided offer without using its adjusted current price as
     * the minimum. Unknown or non-stock shapes deliberately return empty.
     */
    public static Optional<Match> resolve(
            MerchantOffer offer,
            HolderLookup.Provider registries,
            LibrarianTradeMode mode,
            Optional<ResourceKey<VillagerType>> villagerType) {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(villagerType, "villagerType");

        if (offer.getResult().is(Items.ENCHANTED_BOOK)) {
            return resolveEnchantedBook(offer, registries, mode, villagerType);
        }

        for (LibrarianTradeReference reference : LibrarianTradeCatalog.possibleTrades(mode, villagerType)) {
            if (!reference.enchantedBookSelector() && matchesFixedOffer(offer, reference)) {
                return Optional.of(new Match(reference, reference.naturalCost().orElseThrow(), Optional.empty()));
            }
        }
        return Optional.empty();
    }

    public static Optional<LibrarianTradeReference.NaturalCost> minimumNaturalCost(
            MerchantOffer offer,
            HolderLookup.Provider registries,
            LibrarianTradeMode mode,
            Optional<ResourceKey<VillagerType>> villagerType) {
        return resolve(offer, registries, mode, villagerType).map(Match::naturalCost);
    }

    /** Uses MerchantOffer.getCostA(), which includes the live demand and special-price adjustment. */
    public static CurrentCost currentCost(MerchantOffer offer) {
        ItemStack first = offer.getCostA().copy();
        ItemStack second = offer.getCostB().copy();
        return new CurrentCost(first, second.isEmpty() ? Optional.empty() : Optional.of(second));
    }

    private static Optional<Match> resolveEnchantedBook(
            MerchantOffer offer,
            HolderLookup.Provider registries,
            LibrarianTradeMode mode,
            Optional<ResourceKey<VillagerType>> villagerType) {
        // The rebalance table is variant-gated. Without the villager's synchronized
        // type, treating an arbitrary book as one of those entries would be a guess.
        if (mode == LibrarianTradeMode.TRADE_REBALANCE && villagerType.isEmpty()) {
            return Optional.empty();
        }
        if (!matchesSignature(offer, new LibrarianTradeReference.OfferSignature(12, offer.getXp(), 0.2F))
                || LibrarianTradeCatalog.enchantedBookProfessionLevel(offer.getXp()) == 0
                || !matchesItemCost(offer.getItemCostA(), Items.EMERALD, null)
                || !matchesSecondItemCost(offer.getItemCostB(), Items.BOOK, 1)
                || offer.getResult().getCount() != 1) {
            return Optional.empty();
        }

        ItemEnchantments stored = offer.getResult().getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (stored.keySet().size() != 1) {
            return Optional.empty();
        }
        Holder<Enchantment> enchantment = stored.keySet().iterator().next();
        int enchantmentLevel = stored.getLevel(enchantment);
        int professionLevel = LibrarianTradeCatalog.enchantedBookProfessionLevel(offer.getXp());

        Optional<LibrarianEnchantedBookReference> enchantedReference = LibrarianTradeCatalog
                .enchantedBooks(registries, mode, villagerType)
                .stream()
                .filter(reference -> reference.enchantment().equals(enchantment))
                .filter(reference -> reference.enchantmentLevel() == enchantmentLevel)
                .filter(reference -> reference.professionLevels().contains(professionLevel))
                .findFirst();
        if (enchantedReference.isEmpty()) {
            return Optional.empty();
        }

        LibrarianEnchantedBookReference book = enchantedReference.get();
        if (!book.emeraldRange().contains(offer.getItemCostA().count())
                || !ItemStack.isSameItemSameComponents(offer.getResult(), book.iconStack())) {
            return Optional.empty();
        }

        Optional<LibrarianTradeReference> selector = LibrarianTradeCatalog
                .possibleTradesAtLevel(mode, villagerType, professionLevel)
                .stream()
                .filter(LibrarianTradeReference::enchantedBookSelector)
                .findFirst();
        return selector.map(reference -> new Match(reference, book.naturalCost(), Optional.of(book)));
    }

    private static boolean matchesFixedOffer(MerchantOffer offer, LibrarianTradeReference reference) {
        LibrarianTradeReference.NaturalCost naturalCost = reference.naturalCost().orElseThrow();
        LibrarianTradeReference.ItemRange first = naturalCost.first();
        if (!matchesSignature(offer, reference.signature())
                || !first.count().isFixed()
                || !matchesItemCost(offer.getItemCostA(), first.item(), first.count().minimum())) {
            return false;
        }

        Optional<LibrarianTradeReference.ItemRange> second = naturalCost.second();
        if (second.isPresent()) {
            LibrarianTradeReference.ItemRange expected = second.get();
            if (!expected.count().isFixed()
                    || !matchesSecondItemCost(offer.getItemCostB(), expected.item(), expected.count().minimum())) {
                return false;
            }
        } else if (offer.getItemCostB().isPresent()) {
            return false;
        }

        LibrarianTradeReference.ItemAmount result = reference.result();
        ItemStack expectedResult = new ItemStack(result.item(), result.count());
        return offer.getResult().getCount() == result.count()
                && ItemStack.isSameItemSameComponents(offer.getResult(), expectedResult);
    }

    private static boolean matchesSignature(MerchantOffer offer, LibrarianTradeReference.OfferSignature signature) {
        return offer.getMaxUses() == signature.maxUses()
                && offer.getXp() == signature.villagerXp()
                && Float.compare(offer.getPriceMultiplier(), signature.priceMultiplier()) == 0
                && offer.shouldRewardExp();
    }

    /** A null count accepts any positive base count; callers apply the exact range afterward. */
    private static boolean matchesItemCost(ItemCost actual, Item item, Integer count) {
        ItemStack expected = new ItemStack(item, count == null ? actual.count() : count);
        return actual.count() > 0
                && actual.components().alwaysMatches()
                && (count == null || actual.count() == count)
                && ItemStack.isSameItemSameComponents(actual.itemStack(), expected);
    }

    private static boolean matchesSecondItemCost(Optional<ItemCost> actual, Item item, int count) {
        return actual.isPresent() && matchesItemCost(actual.get(), item, count);
    }

    public record Match(
            LibrarianTradeReference trade,
            LibrarianTradeReference.NaturalCost naturalCost,
            Optional<LibrarianEnchantedBookReference> enchantedBook) {
        public Match {
            Objects.requireNonNull(trade, "trade");
            Objects.requireNonNull(naturalCost, "naturalCost");
            enchantedBook = Objects.requireNonNull(enchantedBook, "enchantedBook");
        }
    }

    /** Defensive copies keep the packet snapshot outside this value object mutable only by its owner. */
    public record CurrentCost(ItemStack first, Optional<ItemStack> second) {
        public CurrentCost {
            first = first.copy();
            second = second.map(ItemStack::copy);
        }

        @Override
        public ItemStack first() {
            return first.copy();
        }

        @Override
        public Optional<ItemStack> second() {
            return second.map(ItemStack::copy);
        }
    }
}
