package librarianinsight.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import static librarianinsight.trade.LibrarianTradeReference.CountRange;
import static librarianinsight.trade.LibrarianTradeReference.Direction;
import static librarianinsight.trade.LibrarianTradeReference.ItemAmount;
import static librarianinsight.trade.LibrarianTradeReference.NaturalCost;
import static librarianinsight.trade.LibrarianTradeReference.OfferSignature;

/** Stock Minecraft 26.2 librarian possibilities and natural price ranges. */
public final class LibrarianTradeCatalog {
    private static final Set<ResourceKey<VillagerType>> ALL_VILLAGER_TYPES = Set.of(
            VillagerType.DESERT,
            VillagerType.JUNGLE,
            VillagerType.PLAINS,
            VillagerType.SAVANNA,
            VillagerType.SNOW,
            VillagerType.SWAMP,
            VillagerType.TAIGA
    );

    /* VanillaEnchantmentTagsProvider: TRADEABLE = NON_TREASURE plus these four treasure entries. */
    private static final List<ResourceKey<Enchantment>> STANDARD_TRADEABLE_ENCHANTMENTS = List.of(
            Enchantments.PROTECTION,
            Enchantments.FIRE_PROTECTION,
            Enchantments.FEATHER_FALLING,
            Enchantments.BLAST_PROTECTION,
            Enchantments.PROJECTILE_PROTECTION,
            Enchantments.RESPIRATION,
            Enchantments.AQUA_AFFINITY,
            Enchantments.THORNS,
            Enchantments.DEPTH_STRIDER,
            Enchantments.SHARPNESS,
            Enchantments.SMITE,
            Enchantments.BANE_OF_ARTHROPODS,
            Enchantments.KNOCKBACK,
            Enchantments.FIRE_ASPECT,
            Enchantments.LOOTING,
            Enchantments.SWEEPING_EDGE,
            Enchantments.EFFICIENCY,
            Enchantments.SILK_TOUCH,
            Enchantments.UNBREAKING,
            Enchantments.FORTUNE,
            Enchantments.POWER,
            Enchantments.PUNCH,
            Enchantments.FLAME,
            Enchantments.INFINITY,
            Enchantments.LUCK_OF_THE_SEA,
            Enchantments.LURE,
            Enchantments.LOYALTY,
            Enchantments.IMPALING,
            Enchantments.RIPTIDE,
            Enchantments.CHANNELING,
            Enchantments.MULTISHOT,
            Enchantments.QUICK_CHARGE,
            Enchantments.PIERCING,
            Enchantments.DENSITY,
            Enchantments.BREACH,
            Enchantments.LUNGE,
            Enchantments.BINDING_CURSE,
            Enchantments.VANISHING_CURSE,
            Enchantments.FROST_WALKER,
            Enchantments.MENDING
    );

    private static final Map<ResourceKey<VillagerType>, List<ResourceKey<Enchantment>>> REBALANCE_COMMON_ENCHANTMENTS = Map.of(
            VillagerType.DESERT, List.of(Enchantments.FIRE_PROTECTION, Enchantments.THORNS, Enchantments.INFINITY),
            VillagerType.JUNGLE, List.of(Enchantments.FEATHER_FALLING, Enchantments.PROJECTILE_PROTECTION, Enchantments.POWER),
            VillagerType.PLAINS, List.of(Enchantments.PUNCH, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS),
            VillagerType.SAVANNA, List.of(Enchantments.KNOCKBACK, Enchantments.BINDING_CURSE, Enchantments.SWEEPING_EDGE),
            VillagerType.SNOW, List.of(Enchantments.AQUA_AFFINITY, Enchantments.LOOTING, Enchantments.FROST_WALKER),
            VillagerType.SWAMP, List.of(Enchantments.DEPTH_STRIDER, Enchantments.RESPIRATION, Enchantments.VANISHING_CURSE),
            VillagerType.TAIGA, List.of(Enchantments.BLAST_PROTECTION, Enchantments.FIRE_ASPECT, Enchantments.FLAME)
    );

    private static final Map<ResourceKey<VillagerType>, MasterBook> REBALANCE_MASTER_BOOKS = Map.of(
            VillagerType.DESERT, new MasterBook(Enchantments.EFFICIENCY, 3, true),
            VillagerType.JUNGLE, new MasterBook(Enchantments.UNBREAKING, 2, true),
            VillagerType.PLAINS, new MasterBook(Enchantments.PROTECTION, 3, true),
            VillagerType.SAVANNA, new MasterBook(Enchantments.SHARPNESS, 3, true),
            VillagerType.SNOW, new MasterBook(Enchantments.SILK_TOUCH, 1, false),
            VillagerType.SWAMP, new MasterBook(Enchantments.MENDING, 1, false),
            VillagerType.TAIGA, new MasterBook(Enchantments.FORTUNE, 2, true)
    );

    private static final List<LibrarianTradeReference> STANDARD_TRADES = buildPossibleTrades(false);
    private static final List<LibrarianTradeReference> REBALANCE_TRADES = buildPossibleTrades(true);

    private LibrarianTradeCatalog() {
    }

    /** Returns icon rows in profession-level order, filtered for a known villager variant when supplied. */
    public static List<LibrarianTradeReference> possibleTrades(
            LibrarianTradeMode mode,
            Optional<ResourceKey<VillagerType>> villagerType) {
        List<LibrarianTradeReference> source = mode == LibrarianTradeMode.TRADE_REBALANCE
                ? REBALANCE_TRADES
                : STANDARD_TRADES;
        return source.stream().filter(trade -> trade.isAvailableFor(villagerType)).toList();
    }

    public static List<LibrarianTradeReference> possibleTradesAtLevel(
            LibrarianTradeMode mode,
            Optional<ResourceKey<VillagerType>> villagerType,
            int professionLevel) {
        return possibleTrades(mode, villagerType).stream()
                .filter(trade -> trade.professionLevel() == professionLevel)
                .toList();
    }

    /**
     * Returns every exact enchantment/level choice behind the enchanted-book icon.
     * With no variant, the rebalance result contains all seven variant-specific sets.
     */
    public static List<LibrarianEnchantedBookReference> enchantedBooks(
            HolderLookup.Provider registries,
            LibrarianTradeMode mode,
            Optional<ResourceKey<VillagerType>> villagerType) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        List<LibrarianEnchantedBookReference> result = new ArrayList<>();

        if (mode == LibrarianTradeMode.STANDARD) {
            for (ResourceKey<Enchantment> key : STANDARD_TRADEABLE_ENCHANTMENTS) {
                addAllLevels(
                        result,
                        enchantments.getOrThrow(key),
                        List.of(1, 2, 3, 4),
                        Set.of(),
                        LibrarianEnchantedBookReference.PriceGeneration.RANDOM_ENCHANTED_BOOK
                );
            }
        } else {
            List<ResourceKey<VillagerType>> types = villagerType
                    .map(List::of)
                    .orElseGet(() -> ALL_VILLAGER_TYPES.stream()
                            .sorted(Comparator.comparing(type -> type.identifier().toString()))
                            .toList());
            for (ResourceKey<VillagerType> type : types) {
                List<ResourceKey<Enchantment>> common = REBALANCE_COMMON_ENCHANTMENTS.get(type);
                MasterBook master = REBALANCE_MASTER_BOOKS.get(type);
                if (common == null || master == null) {
                    continue;
                }
                for (ResourceKey<Enchantment> key : common) {
                    addAllLevels(
                            result,
                            enchantments.getOrThrow(key),
                            List.of(1, 2, 3),
                            Set.of(type),
                            LibrarianEnchantedBookReference.PriceGeneration.RANDOM_ENCHANTED_BOOK
                    );
                }
                Holder<Enchantment> enchantment = enchantments.getOrThrow(master.enchantment());
                CountRange range = master.fixedCostProvider()
                        ? rebalanceFixedMasterEmeraldRange(master.level())
                        : randomEnchantedBookEmeraldRange(enchantment, master.level());
                result.add(new LibrarianEnchantedBookReference(
                        enchantment,
                        master.level(),
                        enchantedBookCost(range),
                        List.of(5),
                        Set.of(type),
                        master.fixedCostProvider()
                                ? LibrarianEnchantedBookReference.PriceGeneration.REBALANCE_FIXED_MASTER
                                : LibrarianEnchantedBookReference.PriceGeneration.RANDOM_ENCHANTED_BOOK
                ));
            }
        }

        return result.stream()
                .sorted(Comparator
                        .comparing((LibrarianEnchantedBookReference book) -> book.enchantment().unwrapKey()
                                .map(key -> key.identifier().toString())
                                .orElse(""))
                        .thenComparingInt(LibrarianEnchantedBookReference::enchantmentLevel)
                        .thenComparing(book -> book.villagerTypes().stream()
                                .findFirst()
                                .map(type -> type.identifier().toString())
                                .orElse("")))
                .toList();
    }

    /** Exact 26.2 EnchantRandomlyFunction cost, including treasure doubling and the 64-item clamp. */
    public static CountRange randomEnchantedBookEmeraldRange(Holder<Enchantment> enchantment, int level) {
        requireValidLevel(enchantment, level);
        int multiplier = enchantment.is(EnchantmentTags.DOUBLE_TRADE_PRICE) ? 2 : 1;
        int minimum = (2 + 3 * level) * multiplier;
        // RandomSource.nextInt(5 + level * 10) is exclusive at its upper bound.
        int maximum = (6 + 13 * level) * multiplier;
        return clampedEmeraldRange(minimum, maximum);
    }

    /** Exact fixed-master NumberProvider range; UniformGenerator's integer upper bound is inclusive. */
    public static CountRange rebalanceFixedMasterEmeraldRange(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("enchantment level must be positive");
        }
        int minimum = 2 + 3 * level;
        int maximum = minimum + 5 + level * 10;
        return clampedEmeraldRange(minimum, maximum);
    }

    public static List<ResourceKey<Enchantment>> standardTradeableEnchantmentKeys() {
        return STANDARD_TRADEABLE_ENCHANTMENTS;
    }

    public static List<ResourceKey<Enchantment>> rebalanceCommonEnchantmentKeys(ResourceKey<VillagerType> villagerType) {
        return REBALANCE_COMMON_ENCHANTMENTS.getOrDefault(villagerType, List.of());
    }

    public static Optional<ResourceKey<Enchantment>> rebalanceMasterEnchantmentKey(ResourceKey<VillagerType> villagerType) {
        return Optional.ofNullable(REBALANCE_MASTER_BOOKS.get(villagerType)).map(MasterBook::enchantment);
    }

    private static void addAllLevels(
            List<LibrarianEnchantedBookReference> output,
            Holder<Enchantment> enchantment,
            List<Integer> professionLevels,
            Set<ResourceKey<VillagerType>> villagerTypes,
            LibrarianEnchantedBookReference.PriceGeneration generation) {
        for (int level = enchantment.value().getMinLevel(); level <= enchantment.value().getMaxLevel(); level++) {
            output.add(new LibrarianEnchantedBookReference(
                    enchantment,
                    level,
                    enchantedBookCost(randomEnchantedBookEmeraldRange(enchantment, level)),
                    professionLevels,
                    villagerTypes,
                    generation
            ));
        }
    }

    private static NaturalCost enchantedBookCost(CountRange emeraldRange) {
        return NaturalCost.two(Items.EMERALD, emeraldRange, Items.BOOK, 1);
    }

    private static CountRange clampedEmeraldRange(int minimum, int maximum) {
        int stackLimit = Items.EMERALD.getDefaultMaxStackSize();
        return new CountRange(Math.min(minimum, stackLimit), Math.min(maximum, stackLimit));
    }

    private static void requireValidLevel(Holder<Enchantment> enchantment, int level) {
        if (level < enchantment.value().getMinLevel() || level > enchantment.value().getMaxLevel()) {
            throw new IllegalArgumentException("level is outside the enchantment's supported range");
        }
    }

    private static List<LibrarianTradeReference> buildPossibleTrades(boolean tradeRebalance) {
        List<LibrarianTradeReference> trades = new ArrayList<>();
        trades.add(buy("paper", 1, Items.PAPER, 24, 16, 2));
        trades.add(sell("bookshelf", 1, Items.EMERALD, 9, Items.BOOKSHELF, 1, 12, 1));
        trades.add(bookSelector(1, tradeRebalance));

        trades.add(buy("book", 2, Items.BOOK, 4, 12, 10));
        trades.add(sell("lantern", 2, Items.EMERALD, 1, Items.LANTERN, 1, 12, 5));
        trades.add(bookSelector(2, tradeRebalance));

        trades.add(buy("ink_sac", 3, Items.INK_SAC, 5, 12, 20));
        trades.add(sell("glass", 3, Items.EMERALD, 1, Items.GLASS, 4, 12, 10));
        trades.add(bookSelector(3, tradeRebalance));

        trades.add(buy("writable_book", 4, Items.WRITABLE_BOOK, 2, 12, 30));
        trades.add(sell("clock", 4, Items.EMERALD, 5, Items.CLOCK, 1, 12, 15));
        trades.add(sell("compass", 4, Items.EMERALD, 4, Items.COMPASS, 1, 12, 15));
        if (!tradeRebalance) {
            trades.add(bookSelector(4, false));
        }

        trades.add(sell("yellow_candle", 5, Items.EMERALD, 3, Items.DYED_CANDLE.yellow(), 1, 12, 30));
        trades.add(sell("red_candle", 5, Items.EMERALD, 3, Items.DYED_CANDLE.red(), 1, 12, 30));
        if (tradeRebalance) {
            trades.add(bookSelector(5, true));
        }
        return List.copyOf(trades);
    }

    private static LibrarianTradeReference buy(
            String id,
            int professionLevel,
            Item input,
            int inputCount,
            int maxUses,
            int villagerXp) {
        return new LibrarianTradeReference(
                id,
                professionLevel,
                Direction.LIBRARIAN_BUYS,
                input,
                Optional.of(NaturalCost.one(input, inputCount)),
                new ItemAmount(Items.EMERALD, 1),
                false,
                Set.of(),
                new OfferSignature(maxUses, villagerXp, 0.05F)
        );
    }

    private static LibrarianTradeReference sell(
            String id,
            int professionLevel,
            Item input,
            int inputCount,
            Item result,
            int resultCount,
            int maxUses,
            int villagerXp) {
        return new LibrarianTradeReference(
                id,
                professionLevel,
                Direction.LIBRARIAN_SELLS,
                result,
                Optional.of(NaturalCost.one(input, inputCount)),
                new ItemAmount(result, resultCount),
                false,
                Set.of(),
                new OfferSignature(maxUses, villagerXp, 0.05F)
        );
    }

    private static LibrarianTradeReference bookSelector(int professionLevel, boolean tradeRebalance) {
        return new LibrarianTradeReference(
                "enchanted_book",
                professionLevel,
                Direction.LIBRARIAN_SELLS,
                Items.ENCHANTED_BOOK,
                Optional.empty(),
                new ItemAmount(Items.ENCHANTED_BOOK, 1),
                true,
                tradeRebalance ? ALL_VILLAGER_TYPES : Set.of(),
                new OfferSignature(12, enchantedBookVillagerXp(professionLevel), 0.2F)
        );
    }

    static int enchantedBookProfessionLevel(int villagerXp) {
        return switch (villagerXp) {
            case 1 -> 1;
            case 5 -> 2;
            case 10 -> 3;
            case 15 -> 4;
            case 30 -> 5;
            default -> 0;
        };
    }

    private static int enchantedBookVillagerXp(int professionLevel) {
        return switch (professionLevel) {
            case 1 -> 1;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 15;
            case 5 -> 30;
            default -> throw new IllegalArgumentException("invalid librarian level");
        };
    }

    private record MasterBook(ResourceKey<Enchantment> enchantment, int level, boolean fixedCostProvider) {
    }
}
