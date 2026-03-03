package dev.oakheart.oaktags.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputListener implements Listener {
    private final Plugin plugin;
    private final Map<UUID, InputSession> pendingSessions = new ConcurrentHashMap<>();

    public ChatInputListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public void awaitInput(Player player, Consumer<String> callback, int timeoutSeconds) {
        UUID uuid = player.getUniqueId();
        cancelInput(uuid);

        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            InputSession session = pendingSessions.remove(uuid);
            if (session != null) {
                session.callback().accept(null);
            }
        }, timeoutSeconds * 20L);

        pendingSessions.put(uuid, new InputSession(callback, timeoutTask));
    }

    public void cancelInput(UUID uuid) {
        InputSession session = pendingSessions.remove(uuid);
        if (session != null) {
            session.timeoutTask().cancel();
        }
    }

    public boolean hasPendingInput(UUID uuid) {
        return pendingSessions.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        InputSession session = pendingSessions.remove(uuid);
        if (session == null) return;

        event.setCancelled(true);
        session.timeoutTask().cancel();

        String text = PlainTextComponentSerializer.plainText().serialize(event.message());

        if ("cancel".equalsIgnoreCase(text.trim())) {
            Bukkit.getScheduler().runTask(plugin, () -> session.callback().accept(null));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> session.callback().accept(text));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelInput(event.getPlayer().getUniqueId());
    }

    private record InputSession(Consumer<String> callback, BukkitTask timeoutTask) {}
}
