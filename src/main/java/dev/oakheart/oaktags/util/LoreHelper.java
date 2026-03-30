package dev.oakheart.oaktags.util;

import dev.oakheart.message.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI-specific utilities for building lore from layout templates.
 */
public final class LoreHelper {

    private LoreHelper() {}

    /**
     * Resolve a lore layout template into a list of Components.
     * Expands {@code <tag_lore>} macros and removes trailing blank lines.
     */
    public static List<Component> resolveLoreLayout(MessageManager messages, List<String> layout,
                                                     List<String> tagLore, TagResolver resolvers) {
        List<Component> result = new ArrayList<>();

        for (String line : layout) {
            if (line.isEmpty()) {
                result.add(Component.empty());
                continue;
            }

            if (line.strip().equals("<tag_lore>")) {
                for (String loreLine : tagLore) {
                    result.add(messages.deserialize(loreLine, resolvers));
                }
                continue;
            }

            Component component = messages.deserialize(line, resolvers);
            String plain = PlainTextComponentSerializer.plainText().serialize(component);
            if (!plain.isBlank()) {
                result.add(component);
            }
        }

        // Trim trailing empty Components
        while (!result.isEmpty() &&
                PlainTextComponentSerializer.plainText().serialize(result.getLast()).isBlank()) {
            result.removeLast();
        }

        return result;
    }

    /**
     * Deserialize a list of MiniMessage strings into Components,
     * skipping null/empty entries.
     */
    public static List<Component> deserializeList(MessageManager messages, List<String> texts,
                                                   TagResolver... resolvers) {
        List<Component> components = new ArrayList<>();
        for (String text : texts) {
            if (text != null && !text.isEmpty()) {
                components.add(messages.deserialize(text, resolvers));
            }
        }
        return components;
    }
}
