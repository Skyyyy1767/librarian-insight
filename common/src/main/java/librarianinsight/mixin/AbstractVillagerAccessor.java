package librarianinsight.mixin;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Avoids calling getOffers(), which can generate trades when no offers exist yet. */
@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccessor {
    @Accessor("offers")
    @Nullable MerchantOffers librarianInsight$getExistingOffers();
}
