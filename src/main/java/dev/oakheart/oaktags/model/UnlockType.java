package dev.oakheart.oaktags.model;

public enum UnlockType {
    PERMISSION,
    GRANTED;

    public static UnlockType fromString(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GRANTED;
        }
    }
}
