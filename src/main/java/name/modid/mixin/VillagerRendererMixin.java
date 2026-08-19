package name.modid.mixin;

import name.modid.EnchantmentInfo;
import name.modid.IconSpec;
import name.modid.VisibleLibrarianTrades;
import name.modid.VillagerIconRenderState;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerRenderer.class)
public class VillagerRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void visibleLibrarianTrades$copyIcon(Villager villager, VillagerRenderState state, float partialTicks, CallbackInfo ci) {
        EnchantmentInfo enchantment = VisibleLibrarianTrades.enchantmentManager.getEnchant(villager);
        ((VillagerIconRenderState) state).visibleLibrarianTrades$setIcon(enchantment == null ? null : IconSpec.create(enchantment));
    }
}
