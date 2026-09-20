package librarianinsight.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to authoritative restock bookkeeping on the server thread. */
@Mixin(Villager.class)
public interface VillagerAccessor {
    @Accessor("lastRestockGameTime")
    long librarianInsight$getLastRestockGameTime();

    @Accessor("numberOfRestocksToday")
    int librarianInsight$getNumberOfRestocksToday();

    @Accessor("lastRestockCheckDayTime")
    long librarianInsight$getLastRestockCheckDay();
}
