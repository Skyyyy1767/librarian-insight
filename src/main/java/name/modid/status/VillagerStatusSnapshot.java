package name.modid.status;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Immutable data copied from the integrated server and safe for the client GUI. */
public record VillagerStatusSnapshot(
        UUID villagerUuid,
        boolean serverConfirmedOwner,
        Optional<BlockPos> jobSite,
        float health,
        float maximumHealth,
        double distanceToLectern,
        boolean withinWorkingRange,
        Activity activity,
        boolean workingAtLectern,
        int professionLevel,
        int villagerXp,
        Restock restock,
        Gossip gossip,
        Hero hero,
        List<Trade> trades,
        long serverGameTime
) {
    public VillagerStatusSnapshot {
        jobSite = jobSite.map(BlockPos::immutable);
        trades = List.copyOf(trades);
    }

    public int availableTrades() {
        return (int)trades.stream().filter(trade -> !trade.outOfStock()).count();
    }

    public int outOfStockTrades() {
        return trades.size() - availableTrades();
    }

    public int tradesNeedingRestock() {
        return (int)trades.stream().filter(trade -> trade.uses() > 0).count();
    }

    public enum Activity {
        WORK,
        MEET,
        REST,
        IDLE,
        PANIC,
        PRE_RAID,
        RAID,
        HIDE,
        PLAY,
        SLEEPING,
        UNKNOWN
    }

    public enum GossipKind {
        MAJOR_POSITIVE,
        MINOR_POSITIVE,
        TRADING,
        MINOR_NEGATIVE,
        MAJOR_NEGATIVE
    }

    public record Restock(
            int usedToday,
            int remaining,
            long cooldownTicks,
            boolean resetPending,
            VillagerStatusCalculations.RestockState state,
            Optional<Long> lastActualRestockGameTime
    ) {
    }

    public record Gossip(int reputation, Map<GossipKind, Integer> values) {
        public Gossip {
            EnumMap<GossipKind, Integer> copy = new EnumMap<>(GossipKind.class);
            copy.putAll(values);
            values = Map.copyOf(copy);
        }

        public int value(GossipKind kind) {
            return values.getOrDefault(kind, 0);
        }
    }

    public record Hero(boolean active, int level, int remainingTicks, boolean infinite) {
        public static Hero inactive() {
            return new Hero(false, 0, 0, false);
        }
    }

    public static final class Trade {
        private final ItemStack baseCost;
        private final Optional<ItemStack> secondCost;
        private final ItemStack result;
        private final int uses;
        private final int maximumUses;
        private final VillagerStatusCalculations.PriceResult price;

        public Trade(
                ItemStack baseCost,
                Optional<ItemStack> secondCost,
                ItemStack result,
                int uses,
                int maximumUses,
                VillagerStatusCalculations.PriceResult price
        ) {
            this.baseCost = baseCost.copy();
            this.secondCost = secondCost.map(ItemStack::copy);
            this.result = result.copy();
            this.uses = uses;
            this.maximumUses = maximumUses;
            this.price = price;
        }

        public ItemStack baseCost() {
            return baseCost.copy();
        }

        public Optional<ItemStack> secondCost() {
            return secondCost.map(ItemStack::copy);
        }

        public ItemStack result() {
            return result.copy();
        }

        public int uses() {
            return uses;
        }

        public int maximumUses() {
            return maximumUses;
        }

        public boolean outOfStock() {
            return uses >= maximumUses;
        }

        public VillagerStatusCalculations.PriceResult price() {
            return price;
        }
    }
}
