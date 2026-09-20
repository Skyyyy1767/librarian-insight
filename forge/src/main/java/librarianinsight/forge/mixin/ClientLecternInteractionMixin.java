package librarianinsight.forge.mixin;

import librarianinsight.client.LibrarianInfoMenu;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opens the read-only lectern screen before Forge enters packet prediction. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientLecternInteractionMixin {
    @Inject(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startPrediction(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V"),
            cancellable = true)
    private void librarianInsight$openMenu(LocalPlayer player, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> callback) {
        if (!player.isSpectator()) {
            InteractionResult result = LibrarianInfoMenu.useBlock(player, player.level(), hand, hit);
            if (result == InteractionResult.FAIL) {
                callback.setReturnValue(result);
            }
        }
    }
}
