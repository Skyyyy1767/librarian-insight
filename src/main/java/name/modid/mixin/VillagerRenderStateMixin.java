package name.modid.mixin;

import name.modid.IconSpec;
import name.modid.VillagerIconRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VillagerRenderState.class)
public class VillagerRenderStateMixin implements VillagerIconRenderState {
    @Unique private @Nullable IconSpec visibleLibrarianTrades$icon;

    @Override
    public @Nullable IconSpec visibleLibrarianTrades$getIcon() {
        return visibleLibrarianTrades$icon;
    }

    @Override
    public void visibleLibrarianTrades$setIcon(@Nullable IconSpec icon) {
        visibleLibrarianTrades$icon = icon;
    }
}
