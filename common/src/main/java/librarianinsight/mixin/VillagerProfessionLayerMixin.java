package librarianinsight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import librarianinsight.IconSpec;
import librarianinsight.LibrarianInsight;
import librarianinsight.EnchantmentInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerProfessionLayer.class)
public abstract class VillagerProfessionLayerMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void librarianInsight$renderIcon(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!LibrarianInsight.displayIcons || entity.isInvisible() || !(entity instanceof Villager villager)) {
            return;
        }
        EnchantmentInfo enchantment = LibrarianInsight.enchantmentManager.getEnchant(villager);
        if (enchantment == null) {
            return;
        }
        IconSpec icon = IconSpec.create(enchantment);
        int color = ((int) (icon.red() * 255.0F) << 16) | ((int) (icon.green() * 255.0F) << 8) | (int) (icon.blue() * 255.0F) | 0xFF000000;
        RenderLayerInvoker.librarianInsight$renderColoredCutoutModel(
                ((RenderLayer) (Object) this).getParentModel(), icon.background(), poseStack, buffers,
                packedLight, villager, color);
        RenderLayerInvoker.librarianInsight$renderColoredCutoutModel(
                ((RenderLayer) (Object) this).getParentModel(), icon.logo(), poseStack, buffers,
                packedLight, villager, 0xFFFFFFFF);
    }
}
