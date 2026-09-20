package librarianinsight;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/** Original icon/color convention, with a generic fallback for new enchantments. */
public record IconSpec(float red, float green, float blue, ResourceLocation background, ResourceLocation logo) {
    private static final Set<String> SUPPORTED = Set.of(
            "fire_aspect", "looting", "sharpness", "smite", "thorns", "bane_of_arthropods",
            "protection", "fire_protection", "feather_falling", "blast_protection",
            "projectile_protection", "respiration", "aqua_affinity", "depth_strider",
            "frost_walker", "soul_speed", "efficiency", "silk_touch", "unbreaking", "fortune");
    private static final ResourceLocation BACKGROUND = texture("back");
    private static final ResourceLocation QUESTION = texture("question");

    public static IconSpec create(EnchantmentInfo info) {
        Holder<Enchantment> holder = info.enchantment();
        float[] color = info.isMaxLevel() ? new float[] {1.0F, 0.53F, 0.0F} : switch (info.level()) {
            case 1 -> new float[] {0.65F, 0.65F, 0.65F};
            case 2 -> new float[] {0.20F, 0.76F, 0.15F};
            case 3 -> new float[] {0.0F, 0.32F, 1.0F};
            case 4 -> new float[] {0.78F, 0.0F, 1.0F};
            default -> new float[] {1.0F, 0.53F, 0.0F};
        };
        String path = holder.unwrapKey().map(key -> key.location().getPath()).orElse("");
        ResourceLocation logo = SUPPORTED.contains(path) ? texture(path) : QUESTION;
        return new IconSpec(color[0], color[1], color[2], BACKGROUND, logo);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("librarian_insight", "textures/entity/villager/enchant_icons/" + name + ".png");
    }
}
