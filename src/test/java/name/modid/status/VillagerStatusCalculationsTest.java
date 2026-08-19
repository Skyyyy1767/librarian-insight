package name.modid.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class VillagerStatusCalculationsTest {
    @Test
    void calculatesVanillaPriceComponents() {
        var result = VillagerStatusCalculations.price(new VillagerStatusCalculations.PriceInput(
                24, 3, 0.05F, 100, 0, 64
        ));

        assertEquals(24, result.base());
        assertEquals(3, result.demand());
        assertEquals(-5, result.reputation());
        assertEquals(-7, result.hero());
        assertEquals(15, result.current());
    }

    @Test
    void clampsDiscountedPricesToOne() {
        var result = VillagerStatusCalculations.price(new VillagerStatusCalculations.PriceInput(
                5, 0, 0.05F, 500, 4, 64
        ));

        assertEquals(1, result.current());
    }

    @Test
    void reportsSecondRestockCooldownWithoutPromisingRestock() {
        var result = VillagerStatusCalculations.restock(new VillagerStatusCalculations.RestockInput(
                1100, 1000, 1, false, 2, true, true, true
        ));

        assertEquals(1, result.usedToday());
        assertEquals(1, result.remaining());
        assertEquals(2300, result.cooldownTicks());
        assertEquals(VillagerStatusCalculations.RestockState.COOLDOWN, result.state());
    }

    @Test
    void reportsDailyLimitAndPendingDayReset() {
        var limited = VillagerStatusCalculations.restock(new VillagerStatusCalculations.RestockInput(
                5000, 3000, 2, false, 1, true, true, true
        ));
        var reset = VillagerStatusCalculations.restock(new VillagerStatusCalculations.RestockInput(
                16000, 3000, 2, true, 1, true, false, false
        ));

        assertEquals(VillagerStatusCalculations.RestockState.DAILY_LIMIT, limited.state());
        assertEquals(0, limited.remaining());
        assertEquals(0, reset.usedToday());
        assertEquals(2, reset.remaining());
        assertEquals(VillagerStatusCalculations.RestockState.MUST_REACH_LECTERN, reset.state());
    }
}
