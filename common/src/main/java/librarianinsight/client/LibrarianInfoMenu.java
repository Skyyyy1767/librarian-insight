package librarianinsight.client;

import librarianinsight.LibrarianInsight;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Client-only entry point for the standalone librarian reference screen. */
public final class LibrarianInfoMenu {
    private LibrarianInfoMenu() {
    }

    public static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.LECTERN) || state.getValue(LecternBlock.HAS_BOOK)) {
            return InteractionResult.PASS;
        }
        if (player.getItemInHand(hand).is(ItemTags.LECTERN_BOOKS)) {
            return InteractionResult.PASS;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (LibrarianInsight.lecternManager != null
                && LibrarianInsight.enchantmentManager != null) {
            minecraft.gui.setScreen(new LibrarianInfoScreen(pos));
            // FAIL stops the client from sending a use packet; this screen is
            // informational and must not invoke or alter the lectern server-side.
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}
