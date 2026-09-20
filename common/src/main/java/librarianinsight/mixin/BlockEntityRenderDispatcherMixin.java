package librarianinsight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import librarianinsight.LecternManager;
import librarianinsight.LibrarianInsight;
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
    private static final float PRICE_ICON_MODEL_SCALE = PRICE_ICON_SIZE * 2.0F;
    private static final float ICON_AMOUNT_GAP = 4.0F;
    private static final float PRICE_GROUP_GAP = 11.0F;
    private static final float EMERALD_AMOUNT_Y_OFFSET = 2.0F;

    @Inject(method = "render", at = @At("TAIL"))
    private <E extends BlockEntity> void renderLecternText(
            E blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            CallbackInfo ci
    ) {
        if (!(blockEntity instanceof LecternBlockEntity lectern)) {
            return;
        }

        LecternManager.DisplayText display = LibrarianInsight.lecternManager.getTextOfLectern(lectern.getBlockPos());
        if (display == null) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int light = lectern.getLevel() == null
                ? 0x00F000F0
                : LevelRenderer.getLightColor(lectern.getLevel(), lectern.getBlockPos());
        String text = display.text();
        TextLayout layout = createLayout(font, text);

        poseStack.pushPose();
        // Exact transform order from Saphjyr's 1.19.2 renderer:
        // translate -> Y rotation -> translate -> scale -> X rotation.
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                -lectern.getBlockState().getValue(LecternBlock.FACING).toYRot()));
        poseStack.translate(0.0F, 0.40F, 0.350F);
        poseStack.scale(0.010416667F, -0.010416667F, 0.010416667F);
        poseStack.mulPose(Axis.XP.rotationDegrees(67.5F));
        boolean hasPrice = LibrarianInsight.priceDisplay.isEnabled() && display.emeraldCost() > 0;
        int rowCount = layout.lines().length + (hasPrice ? 1 : 0);
        float firstY = NAME_CENTER_Y - ((rowCount - 1) * LINE_SPACING) / 2.0F;
        for (int index = 0; index < layout.lines().length; index++) {
            String line = layout.lines()[index];
            float width = font.width(line);
            poseStack.pushPose();
            poseStack.translate(0.0F, firstY + index * LINE_SPACING, 0.0F);
            poseStack.scale(layout.scale(), layout.scale(), 1.0F);
            font.drawInBatch(
                    Component.literal(line),
                    -width / 2.0F,
                    -font.lineHeight / 2.0F,
                    LibrarianInsight.priceDisplay.getTextColor().argb(),
                    false,
                    poseStack.last().pose(),
                    buffers,
                    Font.DisplayMode.NORMAL,
                    0,
                    light
            );
            poseStack.popPose();
        }
        if (hasPrice) {
            renderPriceRow(
                    poseStack,
                    buffers,
                    font,
                    light,
                    firstY + layout.lines().length * LINE_SPACING,
                    display.emeraldCost(),
                    display.bookCost()
            );
        }
        poseStack.popPose();
    }

    private static void renderPriceRow(
            PoseStack poseStack,
            MultiBufferSource buffers,
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

        renderItemIcon(poseStack, buffers, new ItemStack(Items.EMERALD), cursor + PRICE_ICON_SIZE / 2.0F, centerY);
        cursor += PRICE_ICON_SIZE;
        if (!emeraldText.isEmpty()) {
            cursor += ICON_AMOUNT_GAP;
            renderPriceText(poseStack, buffers, font, emeraldText, cursor, centerY + EMERALD_AMOUNT_Y_OFFSET, light);
            cursor += font.width(emeraldText);
        }

        if (bookCost > 0) {
            cursor += PRICE_GROUP_GAP;
            renderItemIcon(poseStack, buffers, new ItemStack(Items.BOOK), cursor + PRICE_ICON_SIZE / 2.0F, centerY);
            cursor += PRICE_ICON_SIZE;
            if (!bookText.isEmpty()) {
                cursor += ICON_AMOUNT_GAP;
                renderPriceText(poseStack, buffers, font, bookText, cursor, centerY, light);
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
            MultiBufferSource buffers,
            ItemStack stack,
            float centerX,
            float centerY
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, -0.1F);
        poseStack.scale(-PRICE_ICON_MODEL_SCALE, -PRICE_ICON_MODEL_SCALE, 0.01F);
        minecraft.getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffers,
                minecraft.level,
                0
        );
        poseStack.popPose();
    }

    private static void renderPriceText(
            PoseStack poseStack,
            MultiBufferSource buffers,
            Font font,
            String text,
            float x,
            float centerY,
            int light
    ) {
        font.drawInBatch(
                Component.literal(text),
                x,
                centerY - font.lineHeight / 2.0F,
                0xFF000000,
                false,
                poseStack.last().pose(),
                buffers,
                Font.DisplayMode.NORMAL,
                0,
                light
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
