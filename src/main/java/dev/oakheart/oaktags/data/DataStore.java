package dev.oakheart.oaktags.data;

import dev.oakheart.oaktags.model.FilterMode;
import dev.oakheart.oaktags.model.SortMode;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface DataStore {
    void initialize();
    void close();

    // Player tags (granted type)
    Set<String> loadPlayerTags(UUID uuid);
    void grantTag(UUID uuid, String tagId, String grantedBy);
    void revokeTag(UUID uuid, String tagId);

    // Player settings
    PlayerSettings loadPlayerSettings(UUID uuid);
    void savePlayerSettings(UUID uuid, String activeTag, SortMode sortMode,
                            boolean sortReversed, FilterMode filterMode,
                            Set<String> favorites);
    void bulkSavePlayerSettings(Map<UUID, PlayerSettings> settings);

    // Claim counts
    Map<String, Integer> loadClaimCounts();

    boolean isOperational();

    record PlayerSettings(String activeTag, SortMode sortMode,
                          boolean sortReversed, FilterMode filterMode,
                          Set<String> favorites) {
        public static PlayerSettings defaults() {
            return new PlayerSettings(null, SortMode.UNLOCKED_FIRST, false,
                    new FilterMode(FilterMode.ALL), Set.of());
        }
    }
}
