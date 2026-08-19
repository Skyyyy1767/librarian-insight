package librarianinsight.mixin;

import librarianinsight.EnchantmentInfo;
import librarianinsight.IconSpec;
import librarianinsight.LibrarianInsight;
import librarianinsight.VillagerIconRenderState;
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
    private void librarianInsight$copyIcon(Villager villager, VillagerRenderState state, float partialTicks, CallbackInfo ci) {
        EnchantmentInfo enchantment = LibrarianInsight.enchantmentManager.getEnchant(villager);
        ((VillagerIconRenderState) state).librarianInsight$setIcon(enchantment == null ? null : IconSpec.create(enchantment));
    }
}
