package dev.oakheart.oaktags.model;

public enum SortMode {
    CATEGORY,
    ALPHABETICAL,
    NEWEST,
    UNLOCKED_FIRST,
    MOST_CLAIMED;

    public SortMode next() {
        SortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public SortMode previous() {
        SortMode[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    public static SortMode fromString(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CATEGORY;
        }
    }
}
