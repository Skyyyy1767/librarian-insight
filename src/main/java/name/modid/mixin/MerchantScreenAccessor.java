package name.modid.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the vanilla screen's currently selected offer index. */
@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {
    @Accessor("shopItem")
    int vlt$getShopItem();
}
