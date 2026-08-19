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
    private static final float NAME_AREA_WIDTH = 82.0F;
    private static final float NAME_CENTER_Y = 4.5F;
    private static final float MIN_SINGLE_LINE_SCALE = 0.78F;
    private static final float MIN_TWO_LINE_SCALE = 0.68F;
    private static final float LINE_SPACING = 10.0F;

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
        TextLayout layout = createLayout(font, text);

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
        float firstY = NAME_CENTER_Y - ((layout.lines().length - 1) * LINE_SPACING * layout.scale()) / 2.0F;
        for (int index = 0; index < layout.lines().length; index++) {
            String line = layout.lines()[index];
            float width = font.width(line);
            poseStack.pushPose();
            poseStack.translate(0.0F, firstY + index * LINE_SPACING * layout.scale(), 0.0F);
            poseStack.scale(layout.scale(), layout.scale(), 1.0F);
            collector.submitText(
                    poseStack,
                    -width / 2.0F,
                    -font.lineHeight / 2.0F,
                    Component.literal(line).getVisualOrderText(),
                    false,
                    Font.DisplayMode.NORMAL,
                    lecternState.lightCoords,
                    display.maxed() ? 0xFFFFFFFF : 0xFF000000,
                    0,
                    display.maxed() ? 0xFFFF8800 : 0xFFFFFFFF
            );
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static TextLayout createLayout(Font font, String text) {
        float fullWidth = font.width(text);
        float singleScale = Math.min(1.0F, NAME_AREA_WIDTH / Math.max(1.0F, fullWidth));
        if (singleScale >= MIN_SINGLE_LINE_SCALE) {
            return new TextLayout(new String[] {text}, singleScale);
        }

        String[] lines = balancedLines(font, text);
        float widest = Math.max(font.width(lines[0]), font.width(lines[1]));
        float scale = Math.min(1.0F, NAME_AREA_WIDTH / Math.max(1.0F, widest));

        // Normal enchantment names remain above this threshold. Keeping the final
        // fit calculation unbounded guarantees that even resource-pack-added names
        // cannot cross the lectern edge.
        if (scale < MIN_TWO_LINE_SCALE) {
            scale = NAME_AREA_WIDTH / Math.max(1.0F, widest);
        }
        return new TextLayout(lines, scale);
    }

    private static String[] balancedLines(Font font, String text) {
        int bestSplit = -1;
        float bestScore = Float.MAX_VALUE;
        for (int index = 1; index < text.length() - 1; index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                continue;
            }
            String first = text.substring(0, index).stripTrailing();
            String second = text.substring(index + 1).stripLeading();
            float firstWidth = font.width(first);
            float secondWidth = font.width(second);
            float score = Math.max(firstWidth, secondWidth) * 2.0F + Math.abs(firstWidth - secondWidth);
            if (!first.isEmpty() && !second.isEmpty() && score < bestScore) {
                bestScore = score;
                bestSplit = index;
            }
        }

        if (bestSplit < 0) {
            bestSplit = Math.max(1, text.length() / 2);
            return new String[] {text.substring(0, bestSplit), text.substring(bestSplit)};
        }
        return new String[] {text.substring(0, bestSplit).stripTrailing(), text.substring(bestSplit + 1).stripLeading()};
    }

    private record TextLayout(String[] lines, float scale) {}
}
