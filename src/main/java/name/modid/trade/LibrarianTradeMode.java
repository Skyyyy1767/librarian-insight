package name.modid.trade;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

/** Selects the stock 26.2 librarian trade table. */
public enum LibrarianTradeMode {
    STANDARD,
    TRADE_REBALANCE;

    public static LibrarianTradeMode fromEnabledFeatures(FeatureFlagSet enabledFeatures) {
        return enabledFeatures.contains(FeatureFlags.TRADE_REBALANCE) ? TRADE_REBALANCE : STANDARD;
    }
}
