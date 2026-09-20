package librarianinsight.forge.mixin;

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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recreates client block-entity membership callbacks without an extra runtime library. */
@Mixin(LevelChunk.class)
public abstract class ClientBlockEntityLifecycleMixin {
    @Shadow public abstract Level getLevel();
    @Shadow public abstract Map<BlockPos, BlockEntity> getBlockEntities();

    @Redirect(method = "setBlockEntity", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object librarianInsight$blockEntityAdded(Map<Object, Object> map, Object key, Object value) {
        Object previous = map.put(key, value);
        if (getLevel() instanceof ClientLevel && value != previous) {
            LibrarianInsight.lecternManager.blockEntityLoaded((BlockEntity) value);
            if (previous instanceof BlockEntity removed) {
                LibrarianInsight.lecternManager.blockEntityUnloaded(removed);
            }
        }
        return previous;
    }

    @Redirect(method = {
            "removeBlockEntity",
            "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;"
    }, at = @At(value = "INVOKE",
            target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object librarianInsight$blockEntityRemoved(Map<Object, Object> map, Object key) {
        Object removed = map.remove(key);
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
