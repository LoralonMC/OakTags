package dev.oakheart.oaktags.managers;

import dev.oakheart.oaktags.OakTags;
import dev.oakheart.oaktags.config.ConfigManager;
import dev.oakheart.oaktags.data.DataStore;
import dev.oakheart.oaktags.model.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TagManager {
    private final OakTags plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final DataStore dataStore;

    private volatile LinkedHashMap<String, TagDefinition> tagRegistry;
    private final ConcurrentHashMap<UUID, PlayerTagData> playerCache;
    private final Set<UUID> dirtySettings;
    private final Map<String, Integer> claimCounts;
    private final ConcurrentHashMap<UUID, Integer> unlockedCountCache;
    private volatile int cachedTotalVisibleCount;
    private BukkitTask batchSaveTask;

    public TagManager(OakTags plugin, ConfigManager configManager, DataStore dataStore) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.dataStore = dataStore;
        this.playerCache = new ConcurrentHashMap<>();
        this.dirtySettings = ConcurrentHashMap.newKeySet();
        this.claimCounts = new ConcurrentHashMap<>();
        this.unlockedCountCache = new ConcurrentHashMap<>();
    }

    public void initialize() {
        buildRegistry();
        loadClaimCounts();
        startBatchSaveTask();
    }

    public void shutdown() {
        if (batchSaveTask != null) {
            batchSaveTask.cancel();
        }
        flushDirtySettings();
    }

    public void reload() {
        buildRegistry();
        restartBatchSaveTask();
    }

    private void buildRegistry() {
        tagRegistry = new LinkedHashMap<>(configManager.getTagDefinitions());
        cachedTotalVisibleCount = (int) tagRegistry.values().stream()
                .filter(t -> !t.isHidden()).count();
        unlockedCountCache.clear();
        logger.info("Tag registry built: " + tagRegistry.size() + " tags.");
    }

    private void loadClaimCounts() {
        claimCounts.clear();
        claimCounts.putAll(dataStore.loadClaimCounts());
    }

    public void loadPlayer(UUID uuid) {
        Set<String> grantedTags = dataStore.loadPlayerTags(uuid);
        DataStore.PlayerSettings settings = dataStore.loadPlayerSettings(uuid);

        PlayerTagData data = new PlayerTagData();
        data.getGrantedTagIds().addAll(grantedTags);
        data.setActiveTagId(settings.activeTag());
        data.setSortMode(settings.sortMode());
        data.setSortReversed(settings.sortReversed());
        data.setFilterMode(settings.filterMode());
        data.getFavoriteTagIds().addAll(settings.favorites());

        // Validate active tag still exists
        if (data.getActiveTagId() != null && !tagRegistry.containsKey(data.getActiveTagId())) {
            data.setActiveTagId(null);
            dirtySettings.add(uuid);
        }

        playerCache.put(uuid, data);
    }

    public void evictPlayer(UUID uuid) {
        PlayerTagData data = playerCache.remove(uuid);
        unlockedCountCache.remove(uuid);
        if (dirtySettings.remove(uuid) && data != null) {
            DataStore.PlayerSettings snapshot = new DataStore.PlayerSettings(
                    data.getActiveTagId(), data.getSortMode(),
                    data.isSortReversed(), data.getFilterMode(),
                    new HashSet<>(data.getFavoriteTagIds()));
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    dataStore.savePlayerSettings(uuid, snapshot.activeTag(),
                            snapshot.sortMode(), snapshot.sortReversed(),
                            snapshot.filterMode(), snapshot.favorites());
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to save player settings for " + uuid, e);
                }
            });
        }
    }

    public boolean hasTag(Player player, String tagId) {
        TagDefinition tag = tagRegistry.get(tagId);
        if (tag == null) return false;

        return switch (tag.getUnlockType()) {
            case PERMISSION -> player.hasPermission(tag.getUnlockPermission());
            case GRANTED -> {
                PlayerTagData data = playerCache.get(player.getUniqueId());
                yield data != null && data.hasGrantedTag(tagId);
            }
        };
    }

    public boolean grantTag(UUID uuid, String tagId, String grantedBy) {
        TagDefinition tag = tagRegistry.get(tagId);
        if (tag == null || tag.getUnlockType() != UnlockType.GRANTED) return false;

        PlayerTagData data = playerCache.get(uuid);
        if (data == null) return false;

        // Atomic check-and-add via ConcurrentHashMap.newKeySet().add()
        // Returns false if already present, preventing double-grant
        if (!data.addGrantedTag(tagId)) return false;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            dataStore.grantTag(uuid, tagId, grantedBy);
        });

        unlockedCountCache.remove(uuid);
        claimCounts.merge(tagId, 1, Integer::sum);
        return true;
    }

    public boolean revokeTag(UUID uuid, String tagId) {
        TagDefinition tag = tagRegistry.get(tagId);
        if (tag == null || tag.getUnlockType() != UnlockType.GRANTED) return false;

        PlayerTagData data = playerCache.get(uuid);
        if (data == null) return false;

        // Atomic check-and-remove via ConcurrentHashMap.newKeySet().remove()
        // Returns false if not present, preventing double-revoke
        if (!data.removeGrantedTag(tagId)) return false;

        // Clear active tag if it was the revoked tag
        if (tagId.equals(data.getActiveTagId())) {
            data.setActiveTagId(null);
            dirtySettings.add(uuid);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            dataStore.revokeTag(uuid, tagId);
        });

        unlockedCountCache.remove(uuid);
        claimCounts.computeIfPresent(tagId, (k, v) -> Math.max(0, v - 1));
        return true;
    }

    public void setActiveTag(UUID uuid, String tagId) {
        PlayerTagData data = playerCache.get(uuid);
        if (data == null) return;
        data.setActiveTagId(tagId);
        dirtySettings.add(uuid);
    }

    public void clearActiveTag(UUID uuid) {
        setActiveTag(uuid, null);
    }

    public String getActiveTagId(UUID uuid) {
        PlayerTagData data = playerCache.get(uuid);
        return data != null ? data.getActiveTagId() : null;
    }

    public String getActiveTagDisplay(UUID uuid) {
        PlayerTagData data = playerCache.get(uuid);
        if (data == null || data.getActiveTagId() == null) return "";
        TagDefinition tag = tagRegistry.get(data.getActiveTagId());
        return tag != null ? tag.getDisplay() : "";
    }

    public PlayerTagData getPlayerData(UUID uuid) {
        return playerCache.get(uuid);
    }

    /**
     * Get player data from cache, or load from database without caching.
     * Used by the API to serve data for offline players.
     */
    public PlayerTagData getOrLoadPlayerData(UUID uuid) {
        PlayerTagData cached = playerCache.get(uuid);
        if (cached != null) return cached;

        // Load from database without caching
        Set<String> grantedTags = dataStore.loadPlayerTags(uuid);
        DataStore.PlayerSettings settings = dataStore.loadPlayerSettings(uuid);

        PlayerTagData data = new PlayerTagData();
        data.getGrantedTagIds().addAll(grantedTags);
        data.setActiveTagId(settings.activeTag());
        data.setSortMode(settings.sortMode());
        data.setSortReversed(settings.sortReversed());
        data.setFilterMode(settings.filterMode());
        data.getFavoriteTagIds().addAll(settings.favorites());

        return data;
    }

    public void markDirty(UUID uuid) {
        dirtySettings.add(uuid);
    }

    public TagDefinition getTag(String tagId) {
        return tagRegistry.get(tagId);
    }

    public LinkedHashMap<String, TagDefinition> getTagRegistry() {
        return tagRegistry;
    }

    public int getClaimCount(String tagId) {
        return claimCounts.getOrDefault(tagId, 0);
    }

    public int getUnlockedCount(Player player) {
        Integer cached = unlockedCountCache.get(player.getUniqueId());
        if (cached != null) return cached;

        int count = 0;
        for (TagDefinition tag : tagRegistry.values()) {
            if (hasTag(player, tag.getId())) count++;
        }
        unlockedCountCache.put(player.getUniqueId(), count);
        return count;
    }

    public int getTotalVisibleCount() {
        return cachedTotalVisibleCount;
    }

    public List<TagDefinition> getSortedFilteredTags(Player player, SortMode sortMode,
                                                      boolean reversed, FilterMode filterMode) {
        List<TagDefinition> result = new ArrayList<>();
        PlayerTagData data = playerCache.get(player.getUniqueId());

        for (TagDefinition tag : tagRegistry.values()) {
            // Hidden + locked = not visible
            if (tag.isHidden() && !hasTag(player, tag.getId())) continue;

            // Hide permission tags in Most Claimed mode (they always show 0)
            if (sortMode == SortMode.MOST_CLAIMED && tag.getUnlockType() == UnlockType.PERMISSION) continue;

            // Apply filter
            if (!filterMode.isAll()) {
                if (filterMode.isFavorites() && (data == null || !data.isFavorite(tag.getId()))) continue;
                if (filterMode.isUnlocked() && !hasTag(player, tag.getId())) continue;
                if (filterMode.isLocked() && hasTag(player, tag.getId())) continue;
                if (filterMode.isCategory() && !tag.getCategory().equals(filterMode.getValue())) continue;
            }

            result.add(tag);
        }

        // Sort
        Comparator<TagDefinition> comparator = switch (sortMode) {
            case CATEGORY -> Comparator
                    .comparingInt((TagDefinition t) -> configManager.getCategorySortOrder(t.getCategory()))
                    .thenComparingInt(TagDefinition::getSortOrder);
            case ALPHABETICAL -> Comparator.comparing(TagDefinition::getId, String.CASE_INSENSITIVE_ORDER);
            case NEWEST -> Comparator.comparingInt(TagDefinition::getSortOrder).reversed();
            case UNLOCKED_FIRST -> Comparator
                    .comparing((TagDefinition t) -> hasTag(player, t.getId()) ? 0 : 1)
                    .thenComparingInt(t -> configManager.getCategorySortOrder(t.getCategory()))
                    .thenComparingInt(TagDefinition::getSortOrder);
            case MOST_CLAIMED -> Comparator
                    .comparingInt((TagDefinition t) -> getClaimCount(t.getId()))
                    .reversed()
                    .thenComparingInt(TagDefinition::getSortOrder);
        };

        if (reversed) {
            comparator = comparator.reversed();
        }

        result.sort(comparator);
        return result;
    }

    public void refreshRegistry() {
        configManager.reloadTags();
        buildRegistry();
    }

    public void removeTagFromActivePlayers(String tagId) {
        for (Map.Entry<UUID, PlayerTagData> entry : playerCache.entrySet()) {
            PlayerTagData data = entry.getValue();
            if (tagId.equals(data.getActiveTagId())) {
                data.setActiveTagId(null);
                dirtySettings.add(entry.getKey());
            }
        }
    }

    private void startBatchSaveTask() {
        int interval = configManager.getBatchWriteInterval() * 20;
        batchSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::flushDirtySettings, interval, interval);
    }

    private void restartBatchSaveTask() {
        if (batchSaveTask != null) {
            batchSaveTask.cancel();
        }
        startBatchSaveTask();
    }

    private void flushDirtySettings() {
        Set<UUID> toSave = new HashSet<>(dirtySettings);
        dirtySettings.removeAll(toSave);

        Map<UUID, DataStore.PlayerSettings> batch = new HashMap<>();
        for (UUID uuid : toSave) {
            PlayerTagData data = playerCache.get(uuid);
            if (data != null) {
                batch.put(uuid, new DataStore.PlayerSettings(
                        data.getActiveTagId(), data.getSortMode(),
                        data.isSortReversed(), data.getFilterMode(),
                        new HashSet<>(data.getFavoriteTagIds())));
            }
        }

        if (!batch.isEmpty()) {
            try {
                dataStore.bulkSavePlayerSettings(batch);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to batch save player settings", e);
            }
        }
    }

    public boolean toggleFavorite(UUID uuid, String tagId) {
        PlayerTagData data = playerCache.get(uuid);
        if (data == null) return false;
        boolean added = data.toggleFavorite(tagId);
        dirtySettings.add(uuid);
        return added;
    }

}
