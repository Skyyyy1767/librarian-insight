package librarianinsight;

import java.util.UUID;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.ItemCost;

/**
 * A defensive, read-only snapshot of the complete offer packet received for one
 * librarian. Merchant offers are mutable, so callers only receive deep copies.
 */
public final class KnownLibrarianSnapshot {
    private final UUID villagerUuid;
    private final MerchantOffers offers;
    private final Optional<ResourceKey<VillagerType>> villagerType;
    private final int villagerLevel;
    private final int villagerXp;
    private final boolean showProgress;
    private final boolean canRestock;
    private final long receivedGameTime;

    public KnownLibrarianSnapshot(
            UUID villagerUuid,
            MerchantOffers offers,
            Optional<ResourceKey<VillagerType>> villagerType,
            int villagerLevel,
            int villagerXp,
            boolean showProgress,
            boolean canRestock,
            long receivedGameTime
    ) {
        this.villagerUuid = villagerUuid;
        this.offers = deepCopyOffers(offers);
        this.villagerType = Optional.ofNullable(villagerType).orElseGet(Optional::empty);
        this.villagerLevel = villagerLevel;
        this.villagerXp = villagerXp;
        this.showProgress = showProgress;
        this.canRestock = canRestock;
        this.receivedGameTime = receivedGameTime;
    }

    public UUID villagerUuid() {
        return villagerUuid;
    }

    /** Returns a deep copy so the cached packet state cannot be mutated by a screen. */
    public MerchantOffers offersCopy() {
        return deepCopyOffers(offers);
    }

    public Optional<ResourceKey<VillagerType>> villagerType() {
        return villagerType;
    }

    public int villagerLevel() {
        return villagerLevel;
    }

    public int villagerXp() {
        return villagerXp;
    }

    public boolean showProgress() {
        return showProgress;
    }

    public boolean canRestock() {
        return canRestock;
    }

    public long receivedGameTime() {
        return receivedGameTime;
    }

    private static MerchantOffers deepCopyOffers(MerchantOffers source) {
        MerchantOffers result = new MerchantOffers();
        for (MerchantOffer offer : source) {
            ItemCost first = copyCost(offer.getItemCostA());
            Optional<ItemCost> second = offer.getItemCostB().map(KnownLibrarianSnapshot::copyCost);
            MerchantOffer copy = new MerchantOffer(
                    first,
                    second,
                    offer.getResult().copy(),
                    offer.getUses(),
                    offer.getMaxUses(),
                    offer.getXp(),
                    offer.getPriceMultiplier(),
                    offer.getDemand()
            );
            copy.setSpecialPriceDiff(offer.getSpecialPriceDiff());
            result.add(copy);
        }
        return result;
    }

    private static ItemCost copyCost(ItemCost cost) {
        return new ItemCost(cost.item(), cost.count(), cost.components());
    }
}
