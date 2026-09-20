package librarianinsight.trade;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

/** Selects the stock 1.21.1 librarian trade table. */
public enum LibrarianTradeMode {
    STANDARD,
    TRADE_REBALANCE;

    public static LibrarianTradeMode fromEnabledFeatures(FeatureFlagSet enabledFeatures) {
        return enabledFeatures.contains(FeatureFlags.TRADE_REBALANCE) ? TRADE_REBALANCE : STANDARD;
    }
}
