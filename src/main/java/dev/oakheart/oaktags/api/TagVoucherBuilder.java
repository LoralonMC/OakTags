package dev.oakheart.oaktags.api;

import dev.oakheart.oaktags.OakTags;
import dev.oakheart.oaktags.model.TagDefinition;
import dev.oakheart.oaktags.model.VoucherConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for other plugins (like OakLog) to create tag voucher ItemStacks.
 *
 * <h3>Example usage:</h3>
 * <pre>{@code
 * ItemStack voucher = new TagVoucherBuilder("vip").build();
 * if (voucher != null) {
 *     player.getInventory().addItem(voucher);
 * }
 * }</pre>
 */
public class TagVoucherBuilder {

    private final String tagId;

    /**
     * Create a new voucher builder for the given tag ID.
     *
     * @param tagId the tag definition ID
     */
    public TagVoucherBuilder(@NotNull String tagId) {
        this.tagId = tagId;
    }

    /**
     * Build a voucher ItemStack for the configured tag.
     *
     * @return the voucher ItemStack, or null if the tag is not found or API is unavailable
     */
    @Nullable
    public ItemStack build() {
        OakTagsAPI api = OakTagsAPI.getInstance();
        if (api == null) return null;

        OakTags plugin = JavaPlugin.getPlugin(OakTags.class);
        TagDefinition def = plugin.getTagManager().getTag(tagId);
        if (def == null) return null;

        VoucherConfig vc = def.getVoucherConfig();
        if (vc == null) vc = plugin.getConfigManager().getDefaultVoucherConfig();
        if (vc == null) return null;

        MiniMessage mm = MiniMessage.miniMessage();

        ItemStack item = new ItemStack(vc.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize(vc.getName(),
                        Placeholder.parsed("tag", def.getDisplay()))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));

        List<Component> loreComponents = new ArrayList<>();
        for (String loreLine : vc.getLore()) {
            loreComponents.add(mm.deserialize(loreLine,
                            Placeholder.parsed("tag", def.getDisplay()))
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        meta.lore(loreComponents);

        if (vc.isGlow()) {
            meta.setEnchantmentGlintOverride(true);
        }

        NamespacedKey key = new NamespacedKey(plugin, OakTags.VOUCHER_KEY_ID);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tagId);

        item.setItemMeta(meta);
        return item;
    }
}
