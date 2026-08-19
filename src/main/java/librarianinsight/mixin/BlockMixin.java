package librarianinsight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import librarianinsight.LibrarianInsight;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "setPlacedBy", at = @At("HEAD"))
    private void librarianInsight$cleanAfterLecternPlacement(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (level.isClientSide() && stack.is(Items.LECTERN)) {
            LibrarianInsight.lecternManager.prepareForPlacement(pos);
            LibrarianInsight.enchantmentManager.setNeedsCleanup();
        }
    }
}
