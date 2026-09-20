package librarianinsight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderLayer.class)
public interface RenderLayerInvoker {
    @Invoker("renderColoredCutoutModel")
    static <T extends LivingEntity> void librarianInsight$renderColoredCutoutModel(
            EntityModel<T> model, ResourceLocation texture, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, T entity, int color) {
        throw new AssertionError();
    }
}
