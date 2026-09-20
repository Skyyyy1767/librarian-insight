package librarianinsight.client;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import librarianinsight.LibrarianInsight;
import librarianinsight.mixin.AbstractVillagerAccessor;
import librarianinsight.mixin.VillagerAccessor;
import librarianinsight.status.VillagerStatusCalculations;
import librarianinsight.status.VillagerStatusSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/** Asynchronously copies authoritative integrated-server data for the client UI. */
public final class IntegratedVillagerStatusService {
    private long nextRequestId;

    public long request(
            BlockPos lecternPos,
            UUID fallbackVillager,
            Consumer<Result> callback
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        long requestId = ++nextRequestId;
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.level == null || minecraft.player == null) {
            callback.accept(new Result(requestId, Availability.REMOTE_SERVER, null));
            return requestId;
        }

        ResourceKey<Level> dimension = minecraft.level.dimension();
        UUID playerUuid = minecraft.player.getUUID();
        BlockPos immutableLectern = lecternPos.immutable();
        CompletableFuture<Result> future = server.submit(() -> build(
                requestId, server, dimension, immutableLectern, playerUuid, fallbackVillager
        ));
        future.whenComplete((result, error) -> minecraft.execute(() -> {
            if (error != null) {
                LibrarianInsight.LOGGER.warn("Could not read integrated-server villager status", error);
                callback.accept(new Result(requestId, Availability.ERROR, null));
            } else {
                callback.accept(result);
            }
        }));
        return requestId;
    }

    private static Result build(
            long requestId,
            IntegratedServer server,
            ResourceKey<Level> dimension,
            BlockPos lecternPos,
            UUID playerUuid,
            UUID fallbackVillager
    ) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return new Result(requestId, Availability.NO_LEVEL, null);
        }

        List<Villager> exactOwners = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Villager villager
                    && villager.isAlive()
                    && villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN
                    && jobSiteMatches(villager, dimension, lecternPos)) {
                exactOwners.add(villager);
            }
        }
        if (exactOwners.size() > 1) {
            return new Result(requestId, Availability.OWNER_CONFLICT, null);
        }

        boolean exact = exactOwners.size() == 1;
        Villager villager = exact ? exactOwners.getFirst() : findFallback(level, fallbackVillager);
        if (villager == null) {
            return new Result(requestId, Availability.NO_LOADED_OWNER, null);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        VillagerStatusSnapshot snapshot = snapshot(level, villager, player, lecternPos, exact);
        return new Result(requestId, exact ? Availability.INTEGRATED_EXACT : Availability.INTEGRATED_FALLBACK, snapshot);
    }

    private static boolean jobSiteMatches(
            Villager villager,
            ResourceKey<Level> dimension,
            BlockPos lecternPos
    ) {
        Optional<GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        return jobSite.isPresent()
                && jobSite.get().dimension() == dimension
                && jobSite.get().pos().equals(lecternPos);
    }

    private static Villager findFallback(ServerLevel level, UUID villagerUuid) {
        if (villagerUuid == null) {
            return null;
        }
        Entity entity = level.getEntity(villagerUuid);
        return entity instanceof Villager villager
                && villager.isAlive()
                && villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN
                ? villager
                : null;
    }

    private static VillagerStatusSnapshot snapshot(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            BlockPos lecternPos,
            boolean exactOwner
    ) {
        long gameTime = level.getGameTime();
        Optional<GlobalPos> serverJobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        Optional<BlockPos> jobSite = serverJobSite
                .filter(globalPos -> globalPos.dimension() == level.dimension())
                .map(GlobalPos::pos);
        double distance = Math.sqrt(villager.distanceToSqr(
                lecternPos.getX() + 0.5D,
                lecternPos.getY() + 0.5D,
                lecternPos.getZ() + 0.5D
        ));
        boolean atActualJobSite = jobSite.isPresent()
                && jobSite.get().closerToCenterThan(villager.position(), 1.73D);
        boolean withinRange = exactOwner && lecternPos.closerToCenterThan(villager.position(), 1.73D);
        boolean workingAtLectern = exactOwner && villager.getBrain().getRunningBehaviors().stream()
                .anyMatch(behavior -> behavior instanceof WorkAtPoi);
        VillagerStatusSnapshot.Activity activity = activityOf(villager);

        int reputation = player == null ? 0 : villager.getGossips().getReputation(player.getUUID(), type -> true);
        VillagerStatusSnapshot.Gossip gossip = gossip(villager, player, reputation);
        VillagerStatusSnapshot.Hero hero = hero(player);
        int heroAmplifier = hero.active() ? hero.level() - 1 : -1;

        MerchantOffers offers = ((AbstractVillagerAccessor)villager).librarianInsight$getExistingOffers();
        List<VillagerStatusSnapshot.Trade> trades = new ArrayList<>();
        if (offers != null) {
            for (MerchantOffer offer : offers) {
                ItemStack baseCost = offer.getBaseCostA().copy();
                VillagerStatusCalculations.PriceResult price = VillagerStatusCalculations.price(
                        new VillagerStatusCalculations.PriceInput(
                                baseCost.getCount(),
                                offer.getDemand(),
                                offer.getPriceMultiplier(),
                                reputation,
                                heroAmplifier,
                                baseCost.getMaxStackSize()
                        )
                );
                ItemStack displayedCost = baseCost.copy();
                displayedCost.setCount(price.current());
                trades.add(new VillagerStatusSnapshot.Trade(
                        displayedCost,
                        offer.getItemCostB().map(cost -> cost.itemStack().copy()),
                        offer.getResult().copy(),
                        offer.getUses(),
                        offer.getMaxUses(),
                        price
                ));
            }
        }

        VillagerAccessor restockAccess = (VillagerAccessor)villager;
        long lastRestock = restockAccess.librarianInsight$getLastRestockGameTime();
        int rawRestocks = restockAccess.librarianInsight$getNumberOfRestocksToday();
        long currentDay = level.getDayTime() / 24000L;
        long lastCheckDay = restockAccess.librarianInsight$getLastRestockCheckDay() / 24000L;
        boolean resetPending = gameTime > lastRestock + 12000L
                || lastCheckDay > 0L && currentDay > lastCheckDay;
        int needsRestock = (int)trades.stream().filter(trade -> trade.uses() > 0).count();
        boolean workActivity = activity == VillagerStatusSnapshot.Activity.WORK;
        VillagerStatusCalculations.RestockResult calculatedRestock = VillagerStatusCalculations.restock(
                new VillagerStatusCalculations.RestockInput(
                        gameTime,
                        lastRestock,
                        rawRestocks,
                        resetPending,
                        needsRestock,
                        jobSite.isPresent(),
                        atActualJobSite,
                        workActivity
                )
        );
        Optional<Long> lastActualRestock = rawRestocks > 0 && !resetPending
                ? Optional.of(lastRestock)
                : Optional.empty();
        VillagerStatusSnapshot.Restock restock = new VillagerStatusSnapshot.Restock(
                calculatedRestock.usedToday(),
                calculatedRestock.remaining(),
                calculatedRestock.cooldownTicks(),
                calculatedRestock.dayResetPending(),
                calculatedRestock.state(),
                lastActualRestock
        );

        return new VillagerStatusSnapshot(
                villager.getUUID(),
                exactOwner,
                jobSite,
                villager.getHealth(),
                villager.getMaxHealth(),
                distance,
                withinRange,
                activity,
                workingAtLectern,
                villager.getVillagerData().getLevel(),
                villager.getVillagerXp(),
                restock,
                gossip,
                hero,
                trades,
                gameTime
        );
    }

    private static VillagerStatusSnapshot.Gossip gossip(
            Villager villager,
            ServerPlayer player,
            int reputation
    ) {
        EnumMap<VillagerStatusSnapshot.GossipKind, Integer> values =
                new EnumMap<>(VillagerStatusSnapshot.GossipKind.class);
        if (player != null) {
            Object2IntMap<GossipType> entries = villager.getGossips().getGossipEntries().get(player.getUUID());
            if (entries != null) {
                values.put(VillagerStatusSnapshot.GossipKind.MAJOR_POSITIVE, entries.getInt(GossipType.MAJOR_POSITIVE));
                values.put(VillagerStatusSnapshot.GossipKind.MINOR_POSITIVE, entries.getInt(GossipType.MINOR_POSITIVE));
                values.put(VillagerStatusSnapshot.GossipKind.TRADING, entries.getInt(GossipType.TRADING));
                values.put(VillagerStatusSnapshot.GossipKind.MINOR_NEGATIVE, entries.getInt(GossipType.MINOR_NEGATIVE));
                values.put(VillagerStatusSnapshot.GossipKind.MAJOR_NEGATIVE, entries.getInt(GossipType.MAJOR_NEGATIVE));
            }
        }
        return new VillagerStatusSnapshot.Gossip(reputation, values);
    }

    private static VillagerStatusSnapshot.Hero hero(ServerPlayer player) {
        if (player == null) {
            return VillagerStatusSnapshot.Hero.inactive();
        }
        MobEffectInstance effect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        return effect == null
                ? VillagerStatusSnapshot.Hero.inactive()
                : new VillagerStatusSnapshot.Hero(
                        true,
                        effect.getAmplifier() + 1,
                        effect.getDuration(),
                        effect.isInfiniteDuration()
                );
    }

    private static VillagerStatusSnapshot.Activity activityOf(Villager villager) {
        if (villager.isSleeping()) {
            return VillagerStatusSnapshot.Activity.SLEEPING;
        }
        Optional<Activity> activity = villager.getBrain().getActiveNonCoreActivity();
        if (activity.isEmpty()) {
            return VillagerStatusSnapshot.Activity.UNKNOWN;
        }
        Activity current = activity.get();
        if (current == Activity.WORK) return VillagerStatusSnapshot.Activity.WORK;
        if (current == Activity.MEET) return VillagerStatusSnapshot.Activity.MEET;
        if (current == Activity.REST) return VillagerStatusSnapshot.Activity.REST;
        if (current == Activity.IDLE) return VillagerStatusSnapshot.Activity.IDLE;
        if (current == Activity.PANIC) return VillagerStatusSnapshot.Activity.PANIC;
        if (current == Activity.PRE_RAID) return VillagerStatusSnapshot.Activity.PRE_RAID;
        if (current == Activity.RAID) return VillagerStatusSnapshot.Activity.RAID;
        if (current == Activity.HIDE) return VillagerStatusSnapshot.Activity.HIDE;
        if (current == Activity.PLAY) return VillagerStatusSnapshot.Activity.PLAY;
        return VillagerStatusSnapshot.Activity.UNKNOWN;
    }

    public enum Availability {
        INTEGRATED_EXACT,
        INTEGRATED_FALLBACK,
        REMOTE_SERVER,
        NO_LEVEL,
        NO_LOADED_OWNER,
        OWNER_CONFLICT,
        ERROR
    }

    public record Result(
            long requestId,
            Availability availability,
            VillagerStatusSnapshot snapshot
    ) {
    }
}
