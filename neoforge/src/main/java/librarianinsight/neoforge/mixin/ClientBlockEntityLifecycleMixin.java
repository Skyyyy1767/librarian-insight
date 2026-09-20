package librarianinsight.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Map;
import librarianinsight.LibrarianInsight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tracks actual chunk membership, including replacements and whole-chunk unloads. */
@Mixin(LevelChunk.class)
public abstract class ClientBlockEntityLifecycleMixin {
    @Shadow public abstract Level getLevel();
    @Shadow public abstract Map<BlockPos, BlockEntity> getBlockEntities();

    @ModifyExpressionValue(method = "setBlockEntity", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object librarianInsight$blockEntityAdded(Object previous, BlockEntity added) {
        if (getLevel() instanceof ClientLevel && added != previous) {
            LibrarianInsight.lecternManager.blockEntityLoaded(added);
            if (previous instanceof BlockEntity removed) {
                LibrarianInsight.lecternManager.blockEntityUnloaded(removed);
            }
        }
        return previous;
    }

    @ModifyExpressionValue(method = {
            "removeBlockEntity",
            "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;"
    }, at = @At(value = "INVOKE",
            target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object librarianInsight$blockEntityRemoved(Object removed) {
        if (getLevel() instanceof ClientLevel && removed instanceof BlockEntity blockEntity) {
            LibrarianInsight.lecternManager.blockEntityUnloaded(blockEntity);
        }
        return removed;
    }

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void librarianInsight$chunkUnloaded(CallbackInfo callback) {
        if (getLevel() instanceof ClientLevel) {
            getBlockEntities().values().forEach(LibrarianInsight.lecternManager::blockEntityUnloaded);
        }
    }
}
