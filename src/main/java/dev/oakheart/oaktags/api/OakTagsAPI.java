package dev.oakheart.oaktags.api;

import dev.oakheart.oaktags.managers.TagManager;
import dev.oakheart.oaktags.model.PlayerTagData;
import dev.oakheart.oaktags.model.TagDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Public API for OakTags — allows other plugins to query tag data.
 *
 * <p>Obtain the instance via {@link #getInstance()}. The API is available
 * after OakTags has finished enabling.</p>
 *
 * <h3>Example usage:</h3>
 * <pre>{@code
 * OakTagsAPI api = OakTagsAPI.getInstance();
 * if (api != null) {
 *     String activeTag = api.getActiveTagId(playerUUID);
 *     Set<String> tags = api.getGrantedTags(playerUUID);
 * }
 * }</pre>
 */
public class OakTagsAPI {

    private static OakTagsAPI instance;

    private final TagManager tagManager;

    /**
     * @param tagManager the internal tag manager — called by OakTags during startup
     */
    public OakTagsAPI(@NotNull TagManager tagManager) {
        this.tagManager = tagManager;
        instance = this;
    }

    /**
     * Get the OakTags API instance.
     *
     * @return the API instance, or null if OakTags is not loaded
     */
    @Nullable
    public static OakTagsAPI getInstance() {
        return instance;
    }

    /**
     * Unregister the API instance. Called by OakTags on disable.
     */
    public static void unregister() {
        instance = null;
    }

    // ── Player Tag Queries ───────────────────────────────────────────────

    /**
     * Get the set of granted tag IDs for a player.
     * Only includes tags with unlock type GRANTED — does not include permission-based tags.
     * The player must be online or have been loaded into the cache.
     *
     * @param uuid the player's UUID
     * @return an unmodifiable set of granted tag IDs, or an empty set if not found
     */
    @NotNull
    public Set<String> getGrantedTags(@NotNull UUID uuid) {
        PlayerTagData data = tagManager.getPlayerData(uuid);
        if (data == null) return Set.of();
        return Collections.unmodifiableSet(new HashSet<>(data.getGrantedTagIds()));
    }

    /**
     * Get all unlocked tag IDs for a player (granted + permission-based).
     * Requires the player to be online for permission checks.
     *
     * @param uuid the player's UUID
     * @return an unmodifiable set of all unlocked tag IDs, or an empty set if player is offline/not cached
     */
    @NotNull
    public Set<String> getUnlockedTags(@NotNull UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            // Offline: can only return granted tags (no permission checks possible)
            return getGrantedTags(uuid);
        }

        Set<String> unlocked = new HashSet<>();
        for (TagDefinition tag : tagManager.getTagRegistry().values()) {
            if (tagManager.hasTag(player, tag.getId())) {
                unlocked.add(tag.getId());
            }
        }
        return Collections.unmodifiableSet(unlocked);
    }

    /**
     * Check if a player has a specific tag unlocked.
     * For online players, checks both granted and permission-based tags.
     * For offline/uncached players, only checks granted tags.
     *
     * @param uuid  the player's UUID
     * @param tagId the tag ID to check
     * @return true if the player has the tag
     */
    public boolean hasTag(@NotNull UUID uuid, @NotNull String tagId) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return tagManager.hasTag(player, tagId);
        }

        // Offline: only check granted
        PlayerTagData data = tagManager.getPlayerData(uuid);
        return data != null && data.hasGrantedTag(tagId);
    }

    /**
     * Get the player's active (equipped) tag ID.
     *
     * @param uuid the player's UUID
     * @return the active tag ID, or null if none is active or player is not cached
     */
    @Nullable
    public String getActiveTagId(@NotNull UUID uuid) {
        return tagManager.getActiveTagId(uuid);
    }

    /**
     * Get the display string (MiniMessage format) of the player's active tag.
     *
     * @param uuid the player's UUID
     * @return the display string, or an empty string if no active tag
     */
    @NotNull
    public String getActiveTagDisplay(@NotNull UUID uuid) {
        return tagManager.getActiveTagDisplay(uuid);
    }

    // ── Tag Definition Queries ───────────────────────────────────────────

    /**
     * Get a tag definition by its ID.
     *
     * @param tagId the tag ID
     * @return a snapshot of the tag definition, or null if not found
     */
    @Nullable
    public TagInfo getTag(@NotNull String tagId) {
        TagDefinition def = tagManager.getTag(tagId);
        return def != null ? TagInfo.fromDefinition(def) : null;
    }

    /**
     * Get all registered tag definitions.
     *
     * @return an unmodifiable collection of tag info snapshots
     */
    @NotNull
    public Collection<TagInfo> getAllTags() {
        List<TagInfo> tags = new ArrayList<>();
        for (TagDefinition def : tagManager.getTagRegistry().values()) {
            tags.add(TagInfo.fromDefinition(def));
        }
        return Collections.unmodifiableList(tags);
    }

    /**
     * Get the total number of registered tags (excluding hidden).
     *
     * @return the count of visible tags
     */
    public int getVisibleTagCount() {
        return tagManager.getTotalVisibleCount();
    }

    /**
     * Get the number of times a tag has been claimed (granted to players).
     *
     * @param tagId the tag ID
     * @return the claim count
     */
    public int getClaimCount(@NotNull String tagId) {
        return tagManager.getClaimCount(tagId);
    }

    // ── Tag Modification ─────────────────────────────────────────────────

    /**
     * Grant a tag to a player. Only works for GRANTED type tags.
     * The player must be online or have been loaded into the cache.
     *
     * @param uuid  the player's UUID
     * @param tagId the tag ID to grant
     * @return true if the tag was successfully granted, false if already owned or invalid
     */
    public boolean grantTag(@NotNull UUID uuid, @NotNull String tagId) {
        return tagManager.grantTag(uuid, tagId, "API");
    }

    /**
     * Revoke a tag from a player. Only works for GRANTED type tags.
     * The player must be online or have been loaded into the cache.
     *
     * @param uuid  the player's UUID
     * @param tagId the tag ID to revoke
     * @return true if the tag was successfully revoked, false if not owned or invalid
     */
    public boolean revokeTag(@NotNull UUID uuid, @NotNull String tagId) {
        return tagManager.revokeTag(uuid, tagId);
    }

    /**
     * Set a player's active tag. The tag must be unlocked by the player.
     * Pass null to clear the active tag.
     *
     * @param uuid  the player's UUID
     * @param tagId the tag ID to activate, or null to clear
     */
    public void setActiveTag(@NotNull UUID uuid, @Nullable String tagId) {
        tagManager.setActiveTag(uuid, tagId);
    }
}
