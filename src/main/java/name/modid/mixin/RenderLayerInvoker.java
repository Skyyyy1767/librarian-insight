package name.modid.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderLayer.class)
public interface RenderLayerInvoker {
    @Invoker("renderColoredCutoutModel")
    static <S extends LivingEntityRenderState> void visibleLibrarianTrades$renderColoredCutoutModel(
            Model<? super S> model, Identifier texture, PoseStack poseStack, SubmitNodeCollector collector,
            int lightCoords, S state, int color, int order) {
        throw new AssertionError();
    }
}
