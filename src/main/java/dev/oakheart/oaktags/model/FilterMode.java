package dev.oakheart.oaktags.model;

import java.util.List;
import java.util.Map;

public class FilterMode {
    public static final String ALL = "all";
    public static final String FAVORITES = "favorites";
    public static final String UNLOCKED = "unlocked";
    public static final String LOCKED = "locked";

    private final String value;

    public FilterMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isAll() {
        return ALL.equals(value);
    }

    public boolean isUnlocked() {
        return UNLOCKED.equals(value);
    }

    public boolean isLocked() {
        return LOCKED.equals(value);
    }

    public boolean isFavorites() {
        return FAVORITES.equals(value);
    }

    public boolean isCategory() {
        return !isAll() && !isFavorites() && !isUnlocked() && !isLocked();
    }

    public String getDisplayName(Map<String, String> categoryDisplayNames,
                                  Map<String, String> filterModeNames) {
        if (isAll() || isFavorites() || isUnlocked() || isLocked()) {
            return filterModeNames.getOrDefault(value, value);
        }
        return categoryDisplayNames.getOrDefault(value, value);
    }

    public FilterMode next(List<String> categoryKeys) {
        // Cycle: all -> favorites -> each category key -> unlocked -> locked -> all
        if (ALL.equals(value)) {
            return new FilterMode(FAVORITES);
        }
        if (FAVORITES.equals(value)) {
            return new FilterMode(categoryKeys.isEmpty() ? UNLOCKED : categoryKeys.getFirst());
        }
        int catIndex = categoryKeys.indexOf(value);
        if (catIndex >= 0 && catIndex < categoryKeys.size() - 1) {
            return new FilterMode(categoryKeys.get(catIndex + 1));
        }
        if (catIndex == categoryKeys.size() - 1) {
            return new FilterMode(UNLOCKED);
        }
        if (UNLOCKED.equals(value)) {
            return new FilterMode(LOCKED);
        }
        return new FilterMode(ALL);
    }

    public FilterMode previous(List<String> categoryKeys) {
        // Reverse cycle: all <- favorites <- each category key <- unlocked <- locked <- all
        if (ALL.equals(value)) {
            return new FilterMode(LOCKED);
        }
        if (LOCKED.equals(value)) {
            return new FilterMode(UNLOCKED);
        }
        if (UNLOCKED.equals(value)) {
            return new FilterMode(categoryKeys.isEmpty() ? FAVORITES : categoryKeys.getLast());
        }
        int catIndex = categoryKeys.indexOf(value);
        if (catIndex > 0) {
            return new FilterMode(categoryKeys.get(catIndex - 1));
        }
        if (catIndex == 0) {
            return new FilterMode(FAVORITES);
        }
        if (FAVORITES.equals(value)) {
            return new FilterMode(ALL);
        }
        return new FilterMode(ALL);
    }

    public static FilterMode fromString(String value) {
        return new FilterMode(value == null ? ALL : value);
    }
}
