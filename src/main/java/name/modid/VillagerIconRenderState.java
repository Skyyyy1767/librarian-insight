package name.modid;

import org.jspecify.annotations.Nullable;

/** Extra client render-state data installed by VillagerRenderStateMixin. */
public interface VillagerIconRenderState {
    @Nullable IconSpec visibleLibrarianTrades$getIcon();
    void visibleLibrarianTrades$setIcon(@Nullable IconSpec icon);
}
