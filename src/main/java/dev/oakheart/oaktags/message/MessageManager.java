package dev.oakheart.oaktags.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class MessageManager {
    private final Logger logger;
    private final MiniMessage miniMessage;
    private final Map<String, Component> cache;
    private FileConfiguration config;

    public MessageManager(Logger logger) {
        this.logger = logger;
        this.miniMessage = MiniMessage.miniMessage();
        this.cache = new ConcurrentHashMap<>();
    }

    public void setConfig(FileConfiguration config) {
        this.config = config;
        this.cache.clear();
    }

    public Optional<Component> getMessage(String key) {
        Component cached = cache.get(key);
        if (cached != null) return Optional.of(cached);

        String text = getMessageString(key);
        if (text == null || text.isEmpty()) return Optional.empty();

        Component component = deserialize(text);
        cache.put(key, component);
        return Optional.of(component);
    }

    public Optional<Component> getMessage(String key, TagResolver... resolvers) {
        String text = getMessageString(key);
        if (text == null || text.isEmpty()) return Optional.empty();
        return Optional.of(deserialize(text, resolvers));
    }

    public void send(CommandSender sender, String key) {
        getMessage(key).ifPresent(component -> deliver(sender, key, component));
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        getMessage(key, resolvers).ifPresent(component -> deliver(sender, key, component));
    }

    public void sendCommand(CommandSender sender, String key) {
        getMessage("commands." + key).ifPresent(sender::sendMessage);
    }

    public void sendCommand(CommandSender sender, String key, TagResolver... resolvers) {
        getMessage("commands." + key, resolvers).ifPresent(sender::sendMessage);
    }

    public String getRawMessage(String key) {
        return getMessageString(key);
    }

    public Component deserialize(String text, TagResolver... resolvers) {
        return miniMessage.deserialize(text, resolvers)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public List<Component> deserializeList(List<String> texts, TagResolver... resolvers) {
        List<Component> components = new ArrayList<>();
        for (String text : texts) {
            if (text != null && !text.isEmpty()) {
                components.add(deserialize(text, resolvers));
            }
        }
        return components;
    }

    public List<Component> resolveLoreLayout(List<String> layout, List<String> tagLore,
                                               TagResolver resolvers) {
        List<Component> result = new ArrayList<>();

        for (String line : layout) {
            // Empty string = intentional blank line
            if (line.isEmpty()) {
                result.add(Component.empty());
                continue;
            }

            // <tag_lore> macro — expands to 0+ lines from the tag's lore
            if (line.strip().equals("<tag_lore>")) {
                for (String loreLine : tagLore) {
                    result.add(deserialize(loreLine, resolvers));
                }
                continue;
            }

            // Regular template line — deserialize with resolvers
            Component component = deserialize(line, resolvers);
            String plain = PlainTextComponentSerializer.plainText().serialize(component);
            if (!plain.isBlank()) {
                result.add(component);
            }
        }

        // Trim trailing empty Components
        while (!result.isEmpty() &&
                PlainTextComponentSerializer.plainText().serialize(result.get(result.size() - 1)).isBlank()) {
            result.remove(result.size() - 1);
        }

        return result;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    private void deliver(CommandSender sender, String key, Component component) {
        String display = getMessageDisplay(key);
        if ("action_bar".equals(display) && sender instanceof Player player) {
            player.sendActionBar(component);
        } else {
            sender.sendMessage(component);
        }
    }

    private String getMessageDisplay(String key) {
        return config.getString("messages." + key + ".display", "chat");
    }

    private String getMessageString(String key) {
        if (key.startsWith("commands.") || key.startsWith("gui.")) {
            return config.getString("messages." + key, "");
        }
        String text = config.getString("messages." + key + ".text", null);
        if (text != null) return text;
        return config.getString("messages." + key, "");
    }
}
