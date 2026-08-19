package name.modid;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/** Associates the original mod's nearest tracked librarian with each lectern. */
public final class LecternManager {
    public record DisplayText(String text, boolean maxed, int emeraldCost, int bookCost) {}

    private final Map<BlockPos, @Nullable DisplayText> displays = new HashMap<>();
    private int clock;

    public LecternManager() {
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
    }

    public @Nullable DisplayText getTextOfLectern(BlockPos pos) {
        if (!displays.containsKey(pos)) {
            updateOne(pos);
        }
        return displays.get(pos);
    }

    public void updateAllJobSites() {
        displays.keySet().forEach(this::updateOne);
    }

    public void updateOne(BlockPos pos) {
        Villager closest = null;
        float nearest = 2.5F;
        for (Villager villager : VisibleLibrarianTrades.enchantmentManager.getTrackedVillagers()) {
            float distance = (float) Math.sqrt(pos.distSqr(villager.blockPosition()));
            if (distance < nearest) {
                nearest = distance;
                closest = villager;
            }
        }
        displays.put(pos, closest == null ? null : format(VisibleLibrarianTrades.enchantmentManager.getEnchant(closest)));
    }

    private static DisplayText format(@Nullable EnchantmentInfo enchantment) {
        if (enchantment == null) {
            return new DisplayText(Items.BOOKSHELF.getName(Items.BOOKSHELF.getDefaultInstance()).getString(), false, 0, 0);
        }
        String name = enchantment.enchantment().unwrapKey()
                .map(key -> Component.translatable(Util.makeDescriptionId("enchantment", key.identifier())).getString())
                .orElseGet(() -> Enchantment.getFullname(enchantment.enchantment(), enchantment.level()).getString());
        if (enchantment.enchantment().value().getMaxLevel() != 1) {
            name += " " + Component.translatable("enchantment.level." + enchantment.level()).getString();
        }
        return new DisplayText(name, enchantment.isMaxLevel(), enchantment.emeraldCost(), enchantment.bookCost());
    }

    private void clientTick(Minecraft minecraft) {
        if (minecraft.level == null) {
            displays.clear();
            clock = 0;
        } else if (++clock >= 40) {
            updateAllJobSites();
            clock = 0;
        }
    }
}
