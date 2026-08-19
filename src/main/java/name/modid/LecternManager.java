package name.modid;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/** Associates the original mod's nearest tracked librarian with each lectern. */
public final class LecternManager {
    public record DisplayText(String text, boolean maxed, int emeraldCost, int bookCost) {}

    private final Map<BlockPos, @Nullable DisplayText> displays = new HashMap<>();
    private final Set<BlockPos> pendingPlacements = new HashSet<>();
    private int clock;

    public LecternManager() {
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof LecternBlockEntity) {
                removeLectern(blockEntity.getBlockPos());
            }
        });
    }

    public @Nullable DisplayText getTextOfLectern(BlockPos pos) {
        if (pendingPlacements.contains(pos)) {
            return null;
        }
        if (!displays.containsKey(pos)) {
            updateOne(pos);
        }
        return displays.get(pos);
    }

    public void updateAllJobSites() {
        // This method is called after fresh merchant offers arrive. Newly placed
        // lecterns may now participate in the normal nearest-librarian update.
        pendingPlacements.clear();
        displays.keySet().forEach(this::updateOne);
    }

    /** Removes all name and price data associated with a departed lectern. */
    public void removeLectern(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        displays.remove(immutablePos);
        // Keep only a data-free tombstone so a replacement cannot lazily rebuild
        // the old nearby-villager display before fresh offers arrive.
        pendingPlacements.add(immutablePos);
    }

    /** Keeps a newly placed lectern blank until fresh merchant offers arrive. */
    public void prepareForPlacement(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        displays.put(immutablePos, null);
        pendingPlacements.add(immutablePos);
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
            pendingPlacements.clear();
            clock = 0;
        } else if (++clock >= 40) {
            displays.keySet().stream()
                    .filter(pos -> !pendingPlacements.contains(pos))
                    .forEach(this::updateOne);
            clock = 0;
        }
    }
}
