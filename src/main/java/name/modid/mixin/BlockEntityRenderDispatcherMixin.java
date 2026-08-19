package name.modid.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.LecternRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import name.modid.LecternManager;
import name.modid.VisibleLibrarianTrades;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @Inject(method = "submit", at = @At("TAIL"))
    private void submitLecternText(
            BlockEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo ci
    ) {
        if (!(state instanceof LecternRenderState lecternState)) {
            return;
        }

        LecternManager.DisplayText display = VisibleLibrarianTrades.lecternManager.getTextOfLectern(lecternState.blockPos);
        if (display == null) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        String text = display.text();
        float width = font.width(text);

        poseStack.pushPose();
        // Exact transform order from Saphjyr's 1.19.2 renderer:
        // translate -> Y rotation -> translate -> scale -> X rotation.
        // 26.2 stores FACING.getClockWise().toYRot(), which is 90 degrees
        // ahead of the original FACING.asRotation() value.
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - lecternState.yRot));
        poseStack.translate(0.0F, 0.40F, 0.350F);
        poseStack.scale(0.010416667F, -0.010416667F, 0.010416667F);
        poseStack.mulPose(Axis.XP.rotationDegrees(67.5F));
        collector.submitText(
                poseStack,
                -width / 2.0F,
                0.0F,
                Component.literal(text).getVisualOrderText(),
                false,
                Font.DisplayMode.NORMAL,
                lecternState.lightCoords,
                display.maxed() ? 0xFFFFFFFF : 0xFF000000,
                0,
                display.maxed() ? 0xFFFF8800 : 0xFFFFFFFF
        );
        poseStack.popPose();
    }
}
