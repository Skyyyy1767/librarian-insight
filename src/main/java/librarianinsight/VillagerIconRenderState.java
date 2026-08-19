package librarianinsight;

import org.jspecify.annotations.Nullable;

/** Extra client render-state data installed by VillagerRenderStateMixin. */
public interface VillagerIconRenderState {
    @Nullable IconSpec librarianInsight$getIcon();
    void librarianInsight$setIcon(@Nullable IconSpec icon);
}
