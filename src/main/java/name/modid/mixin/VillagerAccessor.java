package name.modid.mixin;

import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to authoritative restock bookkeeping on the server thread. */
@Mixin(Villager.class)
public interface VillagerAccessor {
    @Accessor("lastRestockGameTime")
    long vlt$getLastRestockGameTime();

    @Accessor("numberOfRestocksToday")
    int vlt$getNumberOfRestocksToday();

    @Accessor("lastRestockCheckDay")
    long vlt$getLastRestockCheckDay();
}
