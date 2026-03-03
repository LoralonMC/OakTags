package dev.oakheart.oaktags.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTagData {
    private final Set<String> grantedTagIds;
    private final Set<String> favoriteTagIds;
    private volatile String activeTagId;
    private volatile SortMode sortMode;
    private volatile boolean sortReversed;
    private volatile FilterMode filterMode;

    public PlayerTagData() {
        this.grantedTagIds = ConcurrentHashMap.newKeySet();
        this.favoriteTagIds = ConcurrentHashMap.newKeySet();
        this.activeTagId = null;
        this.sortMode = SortMode.UNLOCKED_FIRST;
        this.sortReversed = false;
        this.filterMode = new FilterMode(FilterMode.ALL);
    }

    public Set<String> getGrantedTagIds() {
        return grantedTagIds;
    }

    public String getActiveTagId() {
        return activeTagId;
    }

    public void setActiveTagId(String activeTagId) {
        this.activeTagId = activeTagId;
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public void setSortMode(SortMode sortMode) {
        this.sortMode = sortMode;
    }

    public boolean isSortReversed() {
        return sortReversed;
    }

    public void setSortReversed(boolean sortReversed) {
        this.sortReversed = sortReversed;
    }

    public FilterMode getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
    }

    public boolean hasGrantedTag(String tagId) {
        return grantedTagIds.contains(tagId);
    }

    public boolean addGrantedTag(String tagId) {
        return grantedTagIds.add(tagId);
    }

    public boolean removeGrantedTag(String tagId) {
        return grantedTagIds.remove(tagId);
    }

    public Set<String> getFavoriteTagIds() {
        return favoriteTagIds;
    }

    public boolean isFavorite(String tagId) {
        return favoriteTagIds.contains(tagId);
    }

    /**
     * Toggles a tag's favorite status.
     * @return true if the tag was added to favorites, false if removed
     */
    public boolean toggleFavorite(String tagId) {
        if (favoriteTagIds.remove(tagId)) {
            return false;
        }
        favoriteTagIds.add(tagId);
        return true;
    }
}
