package librarianinsight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.Vec3;

/** Associates the original mod's nearest tracked librarian with each lectern. */
public final class LecternManager {
    public record DisplayText(String text, boolean maxed, int emeraldCost, int bookCost) {}

    private final Map<BlockPos, DisplayText> displays = new HashMap<>();
    private final Set<BlockPos> pendingPlacements = new HashSet<>();
    private final Set<BlockPos> knownLecterns = new HashSet<>();
    private final Map<BlockPos, LecternAssociation> associations = new HashMap<>();
    private final Map<UUID, BlockPos> lecternByVillager = new HashMap<>();
    private final Map<UUID, Boolean> librarianProfession = new HashMap<>();
    private final Map<UUID, ClaimSignal> recentClaimSignals = new HashMap<>();
    private final Map<BlockPos, Long> recentPlacements = new HashMap<>();
    private final Set<BlockPos> ambiguousLecterns = new HashSet<>();
    private ClientLevel trackedLevel;
    private int clock;

    private record ClaimSignal(long gameTime, Vec3 villagerPosition, boolean wasLibrarian) {}

    public void blockEntityLoaded(BlockEntity blockEntity) {
        if (blockEntity instanceof LecternBlockEntity) {
            knownLecterns.add(blockEntity.getBlockPos().immutable());
        }
    }

    public void blockEntityUnloaded(BlockEntity blockEntity) {
        if (blockEntity instanceof LecternBlockEntity) {
            removeLectern(blockEntity.getBlockPos());
        }
    }

    public void entityUnloaded(Entity entity) {
        if (entity instanceof Villager villager) {
            if (villager.isAlive()) {
                // Tracking/chunk unloads retain UUID associations and offer data.
                LibrarianInsight.enchantmentManager.detachVillagerEntity(villager.getUUID());
            } else {
                invalidateVillager(villager.getUUID());
                LibrarianInsight.enchantmentManager.invalidateVillager(villager.getUUID());
            }
        }
    }
    public DisplayText getTextOfLectern(BlockPos pos) {
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
        knownLecterns.remove(immutablePos);
        recentPlacements.remove(immutablePos);
        removeAssociation(immutablePos);
        // Keep only a data-free tombstone so a replacement cannot lazily rebuild
        // the old nearby-villager display before fresh offers arrive.
        pendingPlacements.add(immutablePos);
    }

    /** Keeps a newly placed lectern blank until fresh merchant offers arrive. */
    public void prepareForPlacement(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        removeAssociation(immutablePos);
        knownLecterns.add(immutablePos);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            recentPlacements.put(immutablePos, minecraft.level.getGameTime());
        }
        displays.put(immutablePos, null);
        pendingPlacements.add(immutablePos);
    }

    /** Records exact villager identity from the client-visible entity-status packet. */
    public void noteVillagerEvent(Villager villager) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        recentClaimSignals.put(villager.getUUID(), new ClaimSignal(
                minecraft.level.getGameTime(),
                villager.position(),
                villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN
        ));
        if (villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN) {
            List<BlockPos> candidates = associationCandidates(villager.position(), 2.0);
            long now = minecraft.level.getGameTime();
            List<BlockPos> recentCandidates = candidates.stream()
                    .filter(pos -> {
                        Long placedAt = recentPlacements.get(pos);
                        return placedAt != null && now >= placedAt && now - placedAt <= 1200L;
                    })
                    .toList();
            if (recentCandidates.size() == 1) {
                bind(recentCandidates.getFirst(), villager.getUUID(), LecternAssociation.Confidence.CLAIM_CORRELATED);
            } else if (recentCandidates.size() > 1) {
                ambiguousLecterns.addAll(recentCandidates);
            }
        }
    }

    /**
     * A work sound contains only coordinates. It is accepted as association
     * evidence only when one librarian and one lectern uniquely match them.
     */
    public void noteLibrarianWorkSound(double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 soundPosition = new Vec3(x, y, z);
        List<Villager> soundingVillagers = new ArrayList<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof Villager villager
                    && villager.isAlive()
                    && villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN
                    && villager.position().distanceToSqr(soundPosition) <= 0.36) {
                soundingVillagers.add(villager);
            }
        }
        if (soundingVillagers.size() != 1) {
            return;
        }
        Villager villager = soundingVillagers.getFirst();
        List<BlockPos> candidates = associationCandidates(soundPosition, 1.73);
        if (candidates.size() == 1) {
            bind(candidates.getFirst(), villager.getUUID(), LecternAssociation.Confidence.WORK_OBSERVED);
        } else if (candidates.size() > 1) {
            ambiguousLecterns.addAll(candidates);
        }
    }

    /** Returns the retained UUID first, using the legacy nearest rule only if absent. */
    public LecternAssociation getAssociationForMenu(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        LecternAssociation retained = associations.get(immutablePos);
        if (retained != null) {
            return retained;
        }
        Villager closest = null;
        float nearest = 2.5F;
        int candidates = 0;
        for (Villager villager : LibrarianInsight.enchantmentManager.getTrackedVillagers()) {
            if (!villager.isAlive()
                    || villager.getVillagerData().getProfession() != VillagerProfession.LIBRARIAN) {
                continue;
            }
            float distance = (float)Math.sqrt(pos.distSqr(villager.blockPosition()));
            if (distance < 2.5F) {
                candidates++;
                if (distance < nearest) {
                    nearest = distance;
                    closest = villager;
                }
            }
        }
        if (candidates > 1) {
            ambiguousLecterns.add(immutablePos);
        }
        if (closest == null) {
            return null;
        }
        bind(immutablePos, closest.getUUID(), LecternAssociation.Confidence.FALLBACK);
        if (candidates > 1) {
            ambiguousLecterns.add(immutablePos);
        }
        return associations.get(immutablePos);
    }

    /** Returns learned evidence without creating a nearest-villager fallback. */
    public LecternAssociation getRetainedAssociation(BlockPos pos) {
        return associations.get(pos);
    }

    public boolean isAssociationAmbiguous(BlockPos pos) {
        return ambiguousLecterns.contains(pos);
    }

    /** Installs an authoritative single-player JOB_SITE association on the client thread. */
    public void confirmIntegratedServerAssociation(BlockPos pos, UUID villagerUuid) {
        bind(pos, villagerUuid, LecternAssociation.Confidence.SERVER_CONFIRMED);
    }

    /** Clears an exact link only after the integrated server reports a different JOB_SITE. */
    public void invalidateIntegratedServerAssociation(BlockPos pos, UUID villagerUuid) {
        BlockPos immutablePos = pos.immutable();
        LecternAssociation association = associations.get(immutablePos);
        if (association != null
                && association.villagerUuid().equals(villagerUuid)
                && association.confidence() == LecternAssociation.Confidence.SERVER_CONFIRMED) {
            removeAssociation(immutablePos);
            displays.put(immutablePos, null);
        }
    }

    public void invalidateVillager(UUID villagerUuid) {
        BlockPos associatedLectern = lecternByVillager.remove(villagerUuid);
        if (associatedLectern != null) {
            associations.remove(associatedLectern);
            displays.put(associatedLectern, null);
        }
        librarianProfession.remove(villagerUuid);
        recentClaimSignals.remove(villagerUuid);
    }

    private void observeProfessionChanges(Minecraft minecraft) {
        long now = minecraft.level.getGameTime();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof Villager villager)) {
                continue;
            }
            UUID uuid = villager.getUUID();
            if (!villager.isAlive()) {
                invalidateVillager(uuid);
                LibrarianInsight.enchantmentManager.invalidateVillager(uuid);
                continue;
            }
            boolean isLibrarian = villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN;
            Boolean wasLibrarian = librarianProfession.put(uuid, isLibrarian);
            if (!isLibrarian && Boolean.TRUE.equals(wasLibrarian)) {
                invalidateVillager(uuid);
                LibrarianInsight.enchantmentManager.invalidateVillager(uuid);
                continue;
            }
            if (!isLibrarian) {
                continue;
            }
            ClaimSignal signal = recentClaimSignals.get(uuid);
            boolean observedTransition = Boolean.FALSE.equals(wasLibrarian)
                    || (wasLibrarian == null && signal != null && !signal.wasLibrarian());
            if (observedTransition && signal != null && now - signal.gameTime() <= 10L) {
                List<BlockPos> candidates = associationCandidates(signal.villagerPosition(), 2.0);
                if (candidates.size() == 1) {
                    bind(candidates.getFirst(), uuid, LecternAssociation.Confidence.CLAIM_OBSERVED);
                } else if (candidates.size() > 1) {
                    ambiguousLecterns.addAll(candidates);
                }
                recentClaimSignals.remove(uuid);
            }
        }
        recentClaimSignals.entrySet().removeIf(entry -> now - entry.getValue().gameTime() > 20L);
        recentPlacements.entrySet().removeIf(entry -> now - entry.getValue() > 1200L);
    }

    private List<BlockPos> associationCandidates(Vec3 villagerPosition, double radius) {
        double radiusSquared = radius * radius;
        List<BlockPos> candidates = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return candidates;
        }
        int extent = (int)Math.ceil(radius) + 1;
        BlockPos center = BlockPos.containing(villagerPosition);
        for (BlockPos cursor : BlockPos.betweenClosed(
                center.offset(-extent, -extent, -extent),
                center.offset(extent, extent, extent))) {
            if (minecraft.level.getBlockState(cursor).is(Blocks.LECTERN)
                    && Vec3.atCenterOf(cursor).distanceToSqr(villagerPosition) < radiusSquared) {
                BlockPos pos = cursor.immutable();
                candidates.add(pos);
                knownLecterns.add(pos);
            }
        }
        return candidates;
    }

    private void bind(BlockPos pos, UUID villagerUuid, LecternAssociation.Confidence confidence) {
        BlockPos immutablePos = pos.immutable();
        LecternAssociation existingAtPosition = associations.get(immutablePos);
        if (existingAtPosition != null) {
            if (existingAtPosition.villagerUuid().equals(villagerUuid)
                    && existingAtPosition.confidence().atLeast(confidence)) {
                return;
            }
            if (!existingAtPosition.villagerUuid().equals(villagerUuid)
                    && existingAtPosition.confidence().strongerThan(confidence)) {
                return;
            }
        }
        BlockPos oldPosition = lecternByVillager.get(villagerUuid);
        if (oldPosition != null && !oldPosition.equals(immutablePos)) {
            LecternAssociation oldAssociation = associations.get(oldPosition);
            if (oldAssociation != null && oldAssociation.confidence().strongerThan(confidence)) {
                return;
            }
            associations.remove(oldPosition);
            displays.put(oldPosition, null);
        }
        if (existingAtPosition != null) {
            lecternByVillager.remove(existingAtPosition.villagerUuid());
            if (!existingAtPosition.villagerUuid().equals(villagerUuid)) {
                displays.put(immutablePos, null);
            }
        }
        associations.put(immutablePos, new LecternAssociation(villagerUuid, confidence));
        lecternByVillager.put(villagerUuid, immutablePos);
        if (confidence != LecternAssociation.Confidence.FALLBACK) {
            ambiguousLecterns.remove(immutablePos);
        }
    }

    private void removeAssociation(BlockPos pos) {
        LecternAssociation removed = associations.remove(pos);
        if (removed != null) {
            lecternByVillager.remove(removed.villagerUuid(), pos);
        }
        ambiguousLecterns.remove(pos);
    }

    public void updateOne(BlockPos pos) {
        LecternAssociation association = associations.get(pos);
        if (association == null) {
            association = getAssociationForMenu(pos);
        }
        if (association == null
                || !LibrarianInsight.enchantmentManager.hasResolvedEnchant(association.villagerUuid())) {
            displays.put(pos, null);
            return;
        }
        displays.put(pos, format(LibrarianInsight.enchantmentManager.getEnchant(association.villagerUuid())));
    }

    private static DisplayText format(EnchantmentInfo enchantment) {
        if (enchantment == null) {
            return new DisplayText(Items.BOOKSHELF.getName(Items.BOOKSHELF.getDefaultInstance()).getString(), false, 0, 0);
        }
        String name = enchantment.enchantment().unwrapKey()
                .map(key -> Component.translatable(Util.makeDescriptionId("enchantment", key.location())).getString())
                .orElseGet(() -> Enchantment.getFullname(enchantment.enchantment(), enchantment.level()).getString());
        if (enchantment.enchantment().value().getMaxLevel() != 1) {
            name += " " + Component.translatable("enchantment.level." + enchantment.level()).getString();
        }
        return new DisplayText(name, enchantment.isMaxLevel(), enchantment.emeraldCost(), enchantment.bookCost());
    }

    public void clientTick(Minecraft minecraft) {
        if (minecraft.level == null) {
            resetAll();
        } else {
            if (trackedLevel != minecraft.level) {
                resetAll();
                trackedLevel = minecraft.level;
            }
            observeProfessionChanges(minecraft);
            if (++clock < 40) {
                return;
            }
            displays.keySet().stream()
                    .filter(pos -> !pendingPlacements.contains(pos))
                    .forEach(this::updateOne);
            clock = 0;
        }
    }

    private void resetAll() {
        displays.clear();
        pendingPlacements.clear();
        knownLecterns.clear();
        associations.clear();
        lecternByVillager.clear();
        librarianProfession.clear();
        recentClaimSignals.clear();
        recentPlacements.clear();
        ambiguousLecterns.clear();
        trackedLevel = null;
        clock = 0;
    }
}
