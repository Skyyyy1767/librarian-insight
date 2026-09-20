package librarianinsight.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import librarianinsight.EnchantmentInfo;
import librarianinsight.EnchantmentManager;
import librarianinsight.LibrarianInsight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleMerchantOffers", at = @At("HEAD"))
    private void librarianInsight$handleMerchantOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
        EnchantmentManager manager = LibrarianInsight.enchantmentManager;
        if (!manager.acceptsMerchantPacket(packet.getContainerId())) {
            return;
        }
        manager.snapshotCurrentOffers(
                packet.getOffers(),
                packet.getVillagerLevel(),
                packet.getVillagerXp(),
                packet.showProgress(),
                packet.canRestock()
        );
        EnchantmentInfo found = null;
        findEnchantedBook:
        for (MerchantOffer offer : packet.getOffers()) {
            ItemStack result = offer.getResult();
            if (result.is(Items.ENCHANTED_BOOK)) {
                ItemEnchantments enchantments = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                for (Holder<Enchantment> enchantment : enchantments.keySet()) {
                    ItemStack costA = offer.getCostA();
                    ItemStack costB = offer.getCostB();
                    int emeraldCost = countItem(costA, costB, Items.EMERALD);
                    int bookCost = countItem(costA, costB, Items.BOOK);
                    found = new EnchantmentInfo(
                            enchantment,
                            enchantments.getLevel(enchantment),
                            emeraldCost,
                            bookCost
                    );
                    break findEnchantedBook;
                }
            }
        }
        manager.addEnchantToCurrentVillager(found);
        if (manager.isTrackingDone()) {
            LibrarianInsight.lecternManager.updateAllJobSites();
        }
    }

    private static int countItem(ItemStack first, ItemStack second, net.minecraft.world.item.Item item) {
        return (first.is(item) ? first.getCount() : 0) + (second.is(item) ? second.getCount() : 0);
    }

    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void librarianInsight$closeTrackedMerchant(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (LibrarianInsight.enchantmentManager.isWaitingForPacket() && packet.getType() == MenuType.MERCHANT) {
            LibrarianInsight.enchantmentManager.expectMerchantContainer(packet.getContainerId());
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getConnection() != null) {
                minecraft.getConnection().send(new ServerboundContainerClosePacket(packet.getContainerId()));
            }
            ci.cancel();
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void librarianInsight$queueTradeRefresh(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && packet.getEventId() == 14) {
            Entity entity = packet.getEntity(minecraft.level);
            if (entity instanceof Villager villager) {
                LibrarianInsight.lecternManager.noteVillagerEvent(villager);
                LibrarianInsight.enchantmentManager.queueVillager(villager);
            }
        }
    }

    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void librarianInsight$observeLibrarianWork(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (packet.getSound().value() == SoundEvents.VILLAGER_WORK_LIBRARIAN) {
            LibrarianInsight.lecternManager.noteLibrarianWorkSound(
                    packet.getX(), packet.getY(), packet.getZ()
            );
        }
    }
}
