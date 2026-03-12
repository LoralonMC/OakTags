package dev.oakheart.oaktags.api;

import dev.oakheart.oaktags.model.TagDefinition;
import dev.oakheart.oaktags.model.UnlockType;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An immutable snapshot of a tag definition, safe to expose via the API.
 *
 * @param id               the unique tag identifier
 * @param display          the display string (MiniMessage format)
 * @param category         the tag category
 * @param unlockType       how the tag is unlocked (PERMISSION or GRANTED)
 * @param unlockPermission the permission node (only relevant for PERMISSION type)
 * @param hidden           whether the tag is hidden when locked
 * @param lore             the tag's lore lines (MiniMessage format)
 * @param material         the GUI material for this tag
 */
public record TagInfo(
        @NotNull String id,
        @NotNull String display,
        @NotNull String category,
        @NotNull UnlockType unlockType,
        @Nullable String unlockPermission,
        boolean hidden,
        @NotNull List<String> lore,
        @NotNull Material material
) {
    static TagInfo fromDefinition(TagDefinition def) {
        return new TagInfo(
                def.getId(),
                def.getDisplay(),
                def.getCategory(),
                def.getUnlockType(),
                def.getUnlockPermission(),
                def.isHidden(),
                List.copyOf(def.getLore()),
                def.getMaterial()
        );
    }
}
