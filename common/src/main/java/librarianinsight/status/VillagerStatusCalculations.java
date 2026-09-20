package librarianinsight.status;

/** Pure calculations mirroring the relevant vanilla 26.3 price and restock rules. */
public final class VillagerStatusCalculations {
    public static final int MAX_RESTOCKS = 2;
    public static final long SECOND_RESTOCK_DELAY_TICKS = 2400L;

    private VillagerStatusCalculations() {
    }

    public static RestockResult restock(RestockInput input) {
        int used = input.dayResetPending() ? 0 : clamp(input.rawRestocksToday(), 0, MAX_RESTOCKS);
        int remaining = MAX_RESTOCKS - used;
        long cooldown = used == 1
                ? Math.max(0L, input.lastRestockGameTime() + SECOND_RESTOCK_DELAY_TICKS - input.gameTime())
                : 0L;

        RestockState state;
        if (input.tradesNeedingRestock() == 0) {
            state = RestockState.NOT_NEEDED;
        } else if (!input.hasWorkstation()) {
            state = RestockState.NO_WORKSTATION;
        } else if (used >= MAX_RESTOCKS) {
            state = RestockState.DAILY_LIMIT;
        } else if (cooldown > 0L) {
            state = RestockState.COOLDOWN;
        } else if (!input.withinWorkingRange()) {
            state = RestockState.MUST_REACH_LECTERN;
        } else if (!input.workActivity()) {
            state = RestockState.WAITING_FOR_WORK;
        } else {
            state = RestockState.READY_WHEN_WORKS;
        }
        return new RestockResult(used, remaining, cooldown, input.dayResetPending(), state);
    }

    public static PriceResult price(PriceInput input) {
        int demand = Math.max(0, (int)Math.floor(input.basePrice() * input.demand() * input.priceMultiplier()));
        int reputation = -(int)Math.floor(input.reputation() * input.priceMultiplier());
        int hero = input.heroAmplifier() < 0
                ? 0
                : -Math.max((int)Math.floor((0.3D + 0.0625D * input.heroAmplifier()) * input.basePrice()), 1);
        int unclamped = input.basePrice() + demand + reputation + hero;
        int current = clamp(unclamped, 1, Math.max(1, input.maximumStackSize()));
        return new PriceResult(input.basePrice(), demand, reputation, hero, current);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum RestockState {
        NOT_NEEDED,
        READY_WHEN_WORKS,
        COOLDOWN,
        DAILY_LIMIT,
        NO_WORKSTATION,
        MUST_REACH_LECTERN,
        WAITING_FOR_WORK
    }

    public record RestockInput(
            long gameTime,
            long lastRestockGameTime,
            int rawRestocksToday,
            boolean dayResetPending,
            int tradesNeedingRestock,
            boolean hasWorkstation,
            boolean withinWorkingRange,
            boolean workActivity
    ) {
    }

    public record RestockResult(
            int usedToday,
            int remaining,
            long cooldownTicks,
            boolean dayResetPending,
            RestockState state
    ) {
    }

    public record PriceInput(
            int basePrice,
            int demand,
            float priceMultiplier,
            int reputation,
            int heroAmplifier,
            int maximumStackSize
    ) {
    }

    public record PriceResult(
            int base,
            int demand,
            int reputation,
            int hero,
            int current
    ) {
        public int knownAdjustment() {
            return demand + reputation + hero;
        }
    }
}
