package librarianinsight.trade;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable presentation data for one icon in the possible-trades browser.
 * Enchanted-book selector rows intentionally have no natural cost until an
 * enchantment and level are selected.
 */
public record LibrarianTradeReference(
        String id,
        int professionLevel,
        Direction direction,
        Item iconItem,
        Optional<NaturalCost> naturalCost,
        ItemAmount result,
        boolean enchantedBookSelector,
        Set<ResourceKey<VillagerType>> villagerTypes,
        OfferSignature signature) {

    public LibrarianTradeReference {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(iconItem, "iconItem");
        naturalCost = Objects.requireNonNull(naturalCost, "naturalCost");
        Objects.requireNonNull(result, "result");
        villagerTypes = Set.copyOf(villagerTypes);
        Objects.requireNonNull(signature, "signature");
        if (professionLevel < 1 || professionLevel > 5) {
            throw new IllegalArgumentException("professionLevel must be between 1 and 5");
        }
        if (enchantedBookSelector == naturalCost.isPresent()) {
            throw new IllegalArgumentException("only enchanted-book selectors may omit their natural cost");
        }
    }

    public ItemStack iconStack() {
        return iconItem.getDefaultInstance();
    }

    public boolean isAvailableFor(Optional<ResourceKey<VillagerType>> villagerType) {
        return villagerTypes.isEmpty() || villagerType.isEmpty() || villagerTypes.contains(villagerType.get());
    }

    public enum Direction {
        LIBRARIAN_BUYS,
        LIBRARIAN_SELLS
    }

    public record CountRange(int minimum, int maximum) {
        public CountRange {
            if (minimum < 1 || maximum < minimum) {
                throw new IllegalArgumentException("invalid positive count range: " + minimum + "-" + maximum);
            }
        }

        public static CountRange exact(int count) {
            return new CountRange(count, count);
        }

        public boolean contains(int count) {
            return count >= minimum && count <= maximum;
        }

        public boolean isFixed() {
            return minimum == maximum;
        }
    }

    public record ItemRange(Item item, CountRange count) {
        public ItemRange {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(count, "count");
        }

        public static ItemRange exact(Item item, int count) {
            return new ItemRange(item, CountRange.exact(count));
        }
    }

    /** Inputs paid by the player before reputation, demand, or other modifiers. */
    public record NaturalCost(ItemRange first, Optional<ItemRange> second) {
        public NaturalCost {
            Objects.requireNonNull(first, "first");
            second = Objects.requireNonNull(second, "second");
        }

        public static NaturalCost one(Item item, int count) {
            return new NaturalCost(ItemRange.exact(item, count), Optional.empty());
        }

        public static NaturalCost one(Item item, CountRange count) {
            return new NaturalCost(new ItemRange(item, count), Optional.empty());
        }

        public static NaturalCost two(Item firstItem, CountRange firstCount, Item secondItem, int secondCount) {
            return new NaturalCost(
                    new ItemRange(firstItem, firstCount),
                    Optional.of(ItemRange.exact(secondItem, secondCount))
            );
        }
    }

    public record ItemAmount(Item item, int count) {
        public ItemAmount {
            Objects.requireNonNull(item, "item");
            if (count < 1) {
                throw new IllegalArgumentException("item count must be positive");
            }
        }
    }

    /** Fields transmitted in MerchantOffer that let Librarian Insight conservatively recognize stock trades. */
    public record OfferSignature(int maxUses, int villagerXp, float priceMultiplier) {
        public OfferSignature {
            if (maxUses < 1 || villagerXp < 0 || priceMultiplier < 0.0F) {
                throw new IllegalArgumentException("invalid vanilla offer signature");
            }
        }
    }
}
