package librarianinsight;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

public record EnchantmentInfo(Holder<Enchantment> enchantment, int level, int emeraldCost, int bookCost) {
    public boolean isMaxLevel() {
        return level == enchantment.value().getMaxLevel();
    }
}
