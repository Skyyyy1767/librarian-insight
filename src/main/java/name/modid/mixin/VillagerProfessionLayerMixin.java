package name.modid.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import name.modid.IconSpec;
import name.modid.VisibleLibrarianTrades;
import name.modid.VillagerIconRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerProfessionLayer.class)
public abstract class VillagerProfessionLayerMixin {
    @Inject(method = "submit", at = @At("TAIL"))
    private void visibleLibrarianTrades$submitIcon(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            LivingEntityRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (!VisibleLibrarianTrades.displayIcons || state.isInvisible || !(state instanceof VillagerRenderState villagerState)) {
            return;
        }
        IconSpec icon = ((VillagerIconRenderState) villagerState).visibleLibrarianTrades$getIcon();
        if (icon == null) {
            return;
        }
        EntityModel<LivingEntityRenderState> model = (EntityModel<LivingEntityRenderState>) ((RenderLayer<?, ?>) (Object) this).getParentModel();
        int color = ((int) (icon.red() * 255.0F) << 16) | ((int) (icon.green() * 255.0F) << 8) | (int) (icon.blue() * 255.0F) | 0xFF000000;
        RenderLayerInvoker.visibleLibrarianTrades$renderColoredCutoutModel(model, icon.background(), poseStack, collector, lightCoords, state, color, 4);
        RenderLayerInvoker.visibleLibrarianTrades$renderColoredCutoutModel(model, icon.logo(), poseStack, collector, lightCoords, state, 0xFFFFFFFF, 5);
    }
}
