package dev.oakheart.oaktags.placeholder;

import dev.oakheart.oaktags.config.ConfigManager;
import dev.oakheart.oaktags.managers.TagManager;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TagsExpansion extends PlaceholderExpansion {
    private final TagManager tagManager;
    private final ConfigManager configManager;
    private final String version;

    public TagsExpansion(TagManager tagManager, ConfigManager configManager, String version) {
        this.tagManager = tagManager;
        this.configManager = configManager;
        this.version = version;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "oaktags";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Loralon";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        return switch (params) {
            case "prefix" -> resolvePrefix(player);
            case "prefix+" -> {
                String result = resolvePrefix(player);
                if (result.isEmpty()) yield "";
                // Check visible text (strip legacy color codes and MiniMessage tags) for trailing space
                String visible = result.replaceAll("[§&][0-9a-fk-orA-FK-OR]|<[^>]+>", "");
                if (visible.isBlank()) yield "";
                yield visible.endsWith(" ") ? result : result + " ";
            }
            case "tag" -> tagManager.getActiveTagDisplay(player.getUniqueId());
            case "tag_name" -> {
                String id = tagManager.getActiveTagId(player.getUniqueId());
                yield id != null ? id : "";
            }
            case "unlocked" -> String.valueOf(tagManager.getUnlockedCount(player));
            case "total" -> String.valueOf(tagManager.getTotalVisibleCount());
            default -> {
                if (params.startsWith("has_")) {
                    String tagId = params.substring(4);
                    if (tagManager.getTag(tagId) != null) {
                        yield tagManager.hasTag(player, tagId) ? "true" : "false";
                    }
                    yield "false";
                }
                if (params.startsWith("count_")) {
                    String tagId = params.substring(6);
                    yield String.valueOf(tagManager.getClaimCount(tagId));
                }
                yield null;
            }
        };
    }

    private String resolvePrefix(Player player) {
        String activeDisplay = tagManager.getActiveTagDisplay(player.getUniqueId());
        String fallback = configManager.getPrefixFallback();
        String expandedFallback = PlaceholderAPI.setPlaceholders(player, fallback);

        if (activeDisplay.isEmpty()) {
            return expandedFallback;
        }

        String stackPerm = configManager.getPrefixStackPermission();
        if (stackPerm != null && !stackPerm.isEmpty()
                && player.hasPermission(stackPerm)) {
            return expandedFallback + activeDisplay;
        }

        return activeDisplay;
    }
}
