package librarianinsight.mixin;

import librarianinsight.LibrarianInsight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Distinguishes an actual lectern removal from a client chunk unload. */
@Mixin(LecternBlock.class)
public abstract class LecternBlockMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void librarianInsight$removeLecternState(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston,
            CallbackInfo ci
    ) {
        if (level.isClientSide() && !newState.is(state.getBlock())) {
            LibrarianInsight.lecternManager.removeLectern(pos);
        }
    }
}
