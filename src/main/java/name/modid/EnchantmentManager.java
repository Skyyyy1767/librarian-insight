package name.modid;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.EntityHitResult;

/** Preserves the original mod's client-side trade-query queue. */
public final class EnchantmentManager {
    private final Map<Villager, @Nullable EnchantmentInfo> enchantments = new IdentityHashMap<>();
    private final Set<Villager> resolved = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Queue<Villager> queryQueue = new ArrayDeque<>();
    private @Nullable Villager currentVillager;
    private @Nullable Villager previousVillager;
    private boolean needsCleanup;
    private int clock;

    public EnchantmentManager() {
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
    }

    public void queueVillager(Villager villager) {
        resolved.remove(villager);
        if (!queryQueue.contains(villager)) {
            queryQueue.offer(villager);
        }
    }

    public boolean isWaitingForPacket() {
        return currentVillager != null;
    }

    public boolean isTrackingDone() {
        return queryQueue.isEmpty();
    }

    public void addEnchantToCurrentVillager(@Nullable EnchantmentInfo enchantment) {
        if (currentVillager != null) {
            enchantments.put(currentVillager, enchantment);
            resolved.add(currentVillager);
            currentVillager = null;
        }
    }

    public @Nullable EnchantmentInfo getEnchant(Villager villager) {
        return enchantments.get(villager);
    }

    public Collection<Villager> getTrackedVillagers() {
        return enchantments.keySet();
    }

    public void setNeedsCleanup() {
        needsCleanup = true;
    }

    private void clientTick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }

        if (clock % 40 == 0) {
            if (currentVillager != null && currentVillager == previousVillager) {
                currentVillager = null;
            }
            previousVillager = currentVillager;
            discoverNearbyLibrarians(minecraft);
            if (needsCleanup) {
                clean();
                needsCleanup = false;
            }
        }

        if (clock % 20 == 0 && currentVillager == null) {
            for (Villager villager : enchantments.keySet()) {
                if (villager.isAlive() && !resolved.contains(villager) && villager.distanceTo(minecraft.player) < 4.0F) {
                    queueVillager(villager);
                    break;
                }
            }
        }

        if (currentVillager == null) {
            Villager villager = queryQueue.poll();
            if (villager != null && villager.isAlive()) {
                queryVillager(minecraft, villager);
            }
        }
        clock++;
    }

    private void discoverNearbyLibrarians(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof Villager villager
                    && !villager.isBaby()
                    && villager.distanceTo(player) < 4.0F
                    && villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
                enchantments.putIfAbsent(villager, null);
            }
        }
    }

    private void queryVillager(Minecraft minecraft, Villager villager) {
        if (minecraft.gameMode == null || minecraft.player == null) {
            return;
        }
        currentVillager = villager;
        // The original mod sends an ordinary client interaction, then closes the
        // merchant screen when its offer packet arrives. This does not edit trades.
        minecraft.gameMode.interact(minecraft.player, villager, new EntityHitResult(villager), InteractionHand.MAIN_HAND);
    }

    private void clean() {
        enchantments.keySet().removeIf(villager -> !villager.isAlive()
                || !villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN));
        resolved.removeIf(villager -> !enchantments.containsKey(villager));
        queryQueue.removeIf(villager -> !villager.isAlive());
    }

    public void reset() {
        enchantments.clear();
        resolved.clear();
        queryQueue.clear();
        currentVillager = null;
        previousVillager = null;
        clock = 0;
    }
}
