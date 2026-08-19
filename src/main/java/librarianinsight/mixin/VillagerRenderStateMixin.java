package librarianinsight.mixin;

import librarianinsight.IconSpec;
import librarianinsight.VillagerIconRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VillagerRenderState.class)
public class VillagerRenderStateMixin implements VillagerIconRenderState {
    @Unique private @Nullable IconSpec librarianInsight$icon;

    @Override
    public @Nullable IconSpec librarianInsight$getIcon() {
        return librarianInsight$icon;
    }

    @Override
    public void librarianInsight$setIcon(@Nullable IconSpec icon) {
        librarianInsight$icon = icon;
    }
}
