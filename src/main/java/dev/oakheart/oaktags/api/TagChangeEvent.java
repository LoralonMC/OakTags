package dev.oakheart.oaktags.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired when a player's tag data changes (tag granted, revoked, or active tag changed).
 * This event is informational and cannot be cancelled.
 */
public class TagChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Action {
        GRANT,
        REVOKE,
        EQUIP,
        UNEQUIP
    }

    private final UUID playerUuid;
    private final Action action;
    private final String tagId;

    public TagChangeEvent(@NotNull UUID playerUuid, @NotNull Action action, @Nullable String tagId) {
        this.playerUuid = playerUuid;
        this.action = action;
        this.tagId = tagId;
    }

    public @NotNull UUID getPlayerUuid() {
        return playerUuid;
    }

    public @NotNull Action getAction() {
        return action;
    }

    /**
     * The tag ID involved in the change, or null for UNEQUIP.
     */
    public @Nullable String getTagId() {
        return tagId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
