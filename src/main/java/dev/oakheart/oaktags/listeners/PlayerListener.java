package dev.oakheart.oaktags.listeners;

import dev.oakheart.oaktags.OakTags;
import dev.oakheart.oaktags.gui.AdminGUI;
import dev.oakheart.oaktags.managers.TagManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerListener implements Listener {
    private final OakTags plugin;
    private final TagManager tagManager;

    public PlayerListener(OakTags plugin, TagManager tagManager) {
        this.plugin = plugin;
        this.tagManager = tagManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            tagManager.loadPlayer(event.getPlayer().getUniqueId());
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (AdminGUI.countViewers(uuid) > 0) return;
        tagManager.evictPlayer(uuid);
    }
}
