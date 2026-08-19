package name.modid.trade;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/** Exact enchantment/level choice shown beneath an enchanted-book selector. */
public record LibrarianEnchantedBookReference(
        Holder<Enchantment> enchantment,
        int enchantmentLevel,
        LibrarianTradeReference.NaturalCost naturalCost,
        List<Integer> professionLevels,
        Set<ResourceKey<VillagerType>> villagerTypes,
        PriceGeneration priceGeneration) {

    public LibrarianEnchantedBookReference {
        Objects.requireNonNull(enchantment, "enchantment");
        Objects.requireNonNull(naturalCost, "naturalCost");
        professionLevels = List.copyOf(professionLevels);
        villagerTypes = Set.copyOf(villagerTypes);
        Objects.requireNonNull(priceGeneration, "priceGeneration");
        if (enchantmentLevel < enchantment.value().getMinLevel()
                || enchantmentLevel > enchantment.value().getMaxLevel()) {
            throw new IllegalArgumentException("level is outside the enchantment's supported range");
        }
        if (professionLevels.isEmpty() || professionLevels.stream().anyMatch(level -> level < 1 || level > 5)) {
            throw new IllegalArgumentException("invalid profession-level availability");
        }
    }

    public ItemStack iconStack() {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        stack.enchant(enchantment, enchantmentLevel);
        return stack;
    }

    public boolean isAvailableFor(ResourceKey<VillagerType> villagerType) {
        return villagerTypes.isEmpty() || villagerTypes.contains(villagerType);
    }

    public LibrarianTradeReference.CountRange emeraldRange() {
        return naturalCost.first().count();
    }

    public enum PriceGeneration {
        RANDOM_ENCHANTED_BOOK,
        REBALANCE_FIXED_MASTER
    }
}
