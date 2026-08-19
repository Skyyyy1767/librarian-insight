package name.modid;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.item.trading.MerchantOffers;

/** Preserves the original mod's client-side trade-query queue. */
public final class EnchantmentManager {
    private final Map<Villager, @Nullable EnchantmentInfo> enchantments = new IdentityHashMap<>();
    private final Set<Villager> resolved = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<UUID, @Nullable EnchantmentInfo> enchantmentsByUuid = new HashMap<>();
    private final Set<UUID> resolvedUuids = new java.util.HashSet<>();
    private final Queue<Villager> queryQueue = new ArrayDeque<>();
    private final Map<UUID, KnownLibrarianSnapshot> offerSnapshots = new HashMap<>();
    private @Nullable Villager currentVillager;
    private @Nullable Villager previousVillager;
    private int expectedMerchantContainerId = -1;
    private @Nullable ClientLevel trackedLevel;
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
            enchantmentsByUuid.put(currentVillager.getUUID(), enchantment);
            resolvedUuids.add(currentVillager.getUUID());
            currentVillager = null;
            expectedMerchantContainerId = -1;
        }
    }

    /** Associates the next offer packet with the serialized background query. */
    public void expectMerchantContainer(int containerId) {
        if (currentVillager != null && expectedMerchantContainerId == -1) {
            expectedMerchantContainerId = containerId;
        }
    }

    public boolean acceptsMerchantPacket(int containerId) {
        return currentVillager != null && expectedMerchantContainerId == containerId;
    }

    /** Captures every currently unlocked offer without changing the legacy extraction path. */
    public void snapshotCurrentOffers(
            MerchantOffers offers,
            int villagerLevel,
            int villagerXp,
            boolean showProgress,
            boolean canRestock
    ) {
        if (currentVillager == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        offerSnapshots.put(currentVillager.getUUID(), new KnownLibrarianSnapshot(
                currentVillager.getUUID(), offers,
                currentVillager.getVillagerData().type().unwrapKey(),
                villagerLevel, villagerXp,
                showProgress, canRestock, gameTime
        ));
    }

    public @Nullable KnownLibrarianSnapshot getOfferSnapshot(UUID villagerUuid) {
        return offerSnapshots.get(villagerUuid);
    }

    /** Requests a fresh adjusted-price packet when the detailed menu is opened. */
    public void requestOfferRefresh(UUID villagerUuid) {
        for (Villager villager : enchantments.keySet()) {
            if (villager.getUUID().equals(villagerUuid) && villager.isAlive()
                    && villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
                queueVillager(villager);
                return;
            }
        }
    }

    public @Nullable EnchantmentInfo getEnchant(Villager villager) {
        return enchantments.get(villager);
    }

    /** Returns the last resolved label data even when the client entity is unloaded. */
    public @Nullable EnchantmentInfo getEnchant(UUID villagerUuid) {
        return enchantmentsByUuid.get(villagerUuid);
    }

    public boolean hasResolvedEnchant(UUID villagerUuid) {
        return resolvedUuids.contains(villagerUuid);
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
        if (trackedLevel != minecraft.level) {
            reset();
            trackedLevel = minecraft.level;
        }

        if (clock % 40 == 0) {
            if (currentVillager != null && currentVillager == previousVillager) {
                currentVillager = null;
                expectedMerchantContainerId = -1;
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
                if (!enchantments.containsKey(villager)) {
                    UUID uuid = villager.getUUID();
                    enchantments.put(villager, enchantmentsByUuid.get(uuid));
                    if (resolvedUuids.contains(uuid)) {
                        resolved.add(villager);
                    }
                }
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
        Set<UUID> invalidUuids = new java.util.HashSet<>();
        for (Villager villager : enchantments.keySet()) {
            if (!villager.isAlive()
                    || !villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
                invalidUuids.add(villager.getUUID());
            }
        }
        enchantments.keySet().removeIf(villager -> !villager.isAlive()
                || !villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN));
        invalidUuids.forEach(uuid -> {
            enchantmentsByUuid.remove(uuid);
            resolvedUuids.remove(uuid);
            offerSnapshots.remove(uuid);
        });
        if (VisibleLibrarianTrades.lecternManager != null) {
            invalidUuids.forEach(VisibleLibrarianTrades.lecternManager::invalidateVillager);
        }
        resolved.removeIf(villager -> !enchantments.containsKey(villager));
        queryQueue.removeIf(villager -> !villager.isAlive());
    }

    public void invalidateVillager(UUID villagerUuid) {
        enchantments.keySet().removeIf(villager -> villager.getUUID().equals(villagerUuid));
        resolved.removeIf(villager -> villager.getUUID().equals(villagerUuid));
        queryQueue.removeIf(villager -> villager.getUUID().equals(villagerUuid));
        enchantmentsByUuid.remove(villagerUuid);
        resolvedUuids.remove(villagerUuid);
        offerSnapshots.remove(villagerUuid);
        if (currentVillager != null && currentVillager.getUUID().equals(villagerUuid)) {
            currentVillager = null;
            expectedMerchantContainerId = -1;
        }
        if (previousVillager != null && previousVillager.getUUID().equals(villagerUuid)) {
            previousVillager = null;
        }
    }

    /**
     * Drops only the transient client entity reference. UUID-keyed trade data is
     * retained because a normal tracking/chunk unload is not evidence that the
     * librarian or its workstation assignment became invalid.
     */
    public void detachVillagerEntity(UUID villagerUuid) {
        enchantments.keySet().removeIf(villager -> villager.getUUID().equals(villagerUuid));
        resolved.removeIf(villager -> villager.getUUID().equals(villagerUuid));
        queryQueue.removeIf(villager -> villager.getUUID().equals(villagerUuid));
        if (currentVillager != null && currentVillager.getUUID().equals(villagerUuid)) {
            currentVillager = null;
            expectedMerchantContainerId = -1;
        }
        if (previousVillager != null && previousVillager.getUUID().equals(villagerUuid)) {
            previousVillager = null;
        }
    }

    public void reset() {
        enchantments.clear();
        resolved.clear();
        enchantmentsByUuid.clear();
        resolvedUuids.clear();
        queryQueue.clear();
        offerSnapshots.clear();
        currentVillager = null;
        previousVillager = null;
        expectedMerchantContainerId = -1;
        trackedLevel = null;
        clock = 0;
    }
}
