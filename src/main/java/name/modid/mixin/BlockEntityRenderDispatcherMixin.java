package name.modid.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.LecternRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import name.modid.LecternManager;
import name.modid.VisibleLibrarianTrades;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    private static final float NAME_AREA_WIDTH = 82.0F;
    private static final float NAME_CENTER_Y = 0.5F;
    private static final float MIN_SINGLE_LINE_SCALE = 0.78F;
    private static final float MIN_TWO_LINE_SCALE = 0.68F;
    private static final float LINE_SPACING = 10.0F;
    private static final float PRICE_ICON_SIZE = 8.0F;
    private static final float ICON_AMOUNT_GAP = 4.0F;
    private static final float PRICE_GROUP_GAP = 11.0F;
    private static final float EMERALD_AMOUNT_Y_OFFSET = 2.0F;

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
        boolean hasPrice = VisibleLibrarianTrades.priceDisplay.isEnabled() && display.emeraldCost() > 0;
        int rowCount = layout.lines().length + (hasPrice ? 1 : 0);
        float firstY = NAME_CENTER_Y - ((rowCount - 1) * LINE_SPACING) / 2.0F;
        for (int index = 0; index < layout.lines().length; index++) {
            String line = layout.lines()[index];
            float width = font.width(line);
            poseStack.pushPose();
            poseStack.translate(0.0F, firstY + index * LINE_SPACING, 0.0F);
            poseStack.scale(layout.scale(), layout.scale(), 1.0F);
            collector.submitText(
                    poseStack,
                    -width / 2.0F,
                    -font.lineHeight / 2.0F,
                    Component.literal(line).getVisualOrderText(),
                    false,
                    Font.DisplayMode.NORMAL,
                    lecternState.lightCoords,
                    VisibleLibrarianTrades.priceDisplay.getTextColor().argb(),
                    0,
                    display.maxed() ? 0xFFFF8800 : 0xFFFFFFFF
            );
            poseStack.popPose();
        }
        if (hasPrice) {
            renderPriceRow(
                    poseStack,
                    collector,
                    font,
                    lecternState.lightCoords,
                    firstY + layout.lines().length * LINE_SPACING,
                    display.emeraldCost(),
                    display.bookCost()
            );
        }
        poseStack.popPose();
    }

    private static void renderPriceRow(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Font font,
            int light,
            float centerY,
            int emeraldCost,
            int bookCost
    ) {
        String emeraldText = quantityText(emeraldCost);
        String bookText = quantityText(bookCost);
        float emeraldPart = pricePartWidth(font, emeraldText);
        float bookPart = bookCost > 0 ? PRICE_ICON_SIZE + (bookText.isEmpty() ? 0.0F : ICON_AMOUNT_GAP + font.width(bookText)) : 0.0F;
        float rowWidth = emeraldPart + (bookCost > 0 ? PRICE_GROUP_GAP + bookPart : 0.0F);
        float cursor = -rowWidth / 2.0F;

        renderItemIcon(poseStack, collector, new ItemStack(Items.EMERALD), cursor + PRICE_ICON_SIZE / 2.0F, centerY);
        cursor += PRICE_ICON_SIZE;
        if (!emeraldText.isEmpty()) {
            cursor += ICON_AMOUNT_GAP;
            renderPriceText(poseStack, collector, font, emeraldText, cursor, centerY + EMERALD_AMOUNT_Y_OFFSET, light);
            cursor += font.width(emeraldText);
        }

        if (bookCost > 0) {
            cursor += PRICE_GROUP_GAP;
            renderItemIcon(poseStack, collector, new ItemStack(Items.BOOK), cursor + PRICE_ICON_SIZE / 2.0F, centerY);
            cursor += PRICE_ICON_SIZE;
            if (!bookText.isEmpty()) {
                cursor += ICON_AMOUNT_GAP;
                renderPriceText(poseStack, collector, font, bookText, cursor, centerY, light);
            }
        }
    }

    private static String quantityText(int count) {
        return count == 1 ? "" : Integer.toString(count);
    }

    private static float pricePartWidth(Font font, String quantity) {
        return PRICE_ICON_SIZE + (quantity.isEmpty() ? 0.0F : ICON_AMOUNT_GAP + font.width(quantity));
    }

    private static void renderItemIcon(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            ItemStack stack,
            float centerX,
            float centerY
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ItemStackRenderState itemState = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(
                itemState,
                stack,
                ItemDisplayContext.GUI,
                minecraft.level,
                minecraft.player,
                0
        );
        TextureAtlasSprite sprite = itemState.pickParticleMaterial(RandomSource.create(0L)).sprite();
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, -0.1F);
        poseStack.scale(PRICE_ICON_SIZE, PRICE_ICON_SIZE, 1.0F);
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucentEmissive(sprite.atlasLocation()),
                (pose, vertices) -> renderSpriteQuad(pose, vertices, sprite)
        );
        poseStack.popPose();
    }

    private static void renderSpriteQuad(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            TextureAtlasSprite sprite
    ) {
        iconVertex(vertices, pose, -0.5F, -0.5F, sprite.getU0(), sprite.getV0());
        iconVertex(vertices, pose, 0.5F, -0.5F, sprite.getU1(), sprite.getV0());
        iconVertex(vertices, pose, 0.5F, 0.5F, sprite.getU1(), sprite.getV1());
        iconVertex(vertices, pose, -0.5F, 0.5F, sprite.getU0(), sprite.getV1());
    }

    private static void iconVertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            float x,
            float y,
            float u,
            float v
    ) {
        vertices.addVertex(pose, x, y, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static void renderPriceText(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Font font,
            String text,
            float x,
            float centerY,
            int light
    ) {
        collector.submitText(
                poseStack,
                x,
                centerY - font.lineHeight / 2.0F,
                Component.literal(text).getVisualOrderText(),
                false,
                Font.DisplayMode.NORMAL,
                light,
                0xFF000000,
                0,
                0xFFFFFFFF
        );
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
