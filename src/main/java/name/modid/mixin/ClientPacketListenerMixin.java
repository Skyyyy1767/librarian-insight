package name.modid.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import name.modid.EnchantmentInfo;
import name.modid.EnchantmentManager;
import name.modid.VisibleLibrarianTrades;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleMerchantOffers", at = @At("HEAD"))
    private void visibleLibrarianTrades$handleMerchantOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
        EnchantmentManager manager = VisibleLibrarianTrades.enchantmentManager;
        if (!manager.isWaitingForPacket()) {
            return;
        }
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
            VisibleLibrarianTrades.lecternManager.updateAllJobSites();
        }
    }

    private static int countItem(ItemStack first, ItemStack second, net.minecraft.world.item.Item item) {
        return (first.is(item) ? first.getCount() : 0) + (second.is(item) ? second.getCount() : 0);
    }

    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void visibleLibrarianTrades$closeTrackedMerchant(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (VisibleLibrarianTrades.enchantmentManager.isWaitingForPacket() && packet.getType() == MenuType.MERCHANT) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getConnection() != null) {
                minecraft.getConnection().send(new ServerboundContainerClosePacket(packet.getContainerId()));
            }
            ci.cancel();
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void visibleLibrarianTrades$queueTradeRefresh(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && packet.getEventId() == 14) {
            Entity entity = packet.getEntity(minecraft.level);
            if (entity instanceof Villager villager) {
                VisibleLibrarianTrades.enchantmentManager.queueVillager(villager);
            }
        }
    }
}
