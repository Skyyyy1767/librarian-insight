package name.modid;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

public record EnchantmentInfo(Holder<Enchantment> enchantment, int level) {
    public boolean isMaxLevel() {
        return level == enchantment.value().getMaxLevel();
    }
}
