package dev.oakheart.oaktags.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.oakheart.oaktags.OakTags;
import dev.oakheart.oaktags.config.ConfigManager;
import dev.oakheart.oaktags.gui.AdminGUI;
import dev.oakheart.oaktags.gui.TagEditorGUI;
import dev.oakheart.oaktags.gui.TagsGUI;
import dev.oakheart.oaktags.listeners.ChatInputListener;
import dev.oakheart.oaktags.managers.TagManager;
import dev.oakheart.oaktags.message.MessageManager;
import dev.oakheart.oaktags.model.PlayerTagData;
import dev.oakheart.oaktags.model.TagDefinition;
import dev.oakheart.oaktags.model.UnlockType;
import dev.oakheart.oaktags.model.VoucherConfig;
import dev.oakheart.oaktags.util.TagsYamlWriter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

@SuppressWarnings("UnstableApiUsage")
public class TagsCommand {
    private final OakTags plugin;
    private final ConfigManager config;
    private final TagManager tagManager;
    private final MessageManager messages;
    private final ChatInputListener chatInputListener;
    private final TagsYamlWriter yamlWriter;

    private static final Pattern VALID_TAG_ID = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (ctx, builder) -> {
        String input = builder.getRemainingLowerCase();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(input)) {
                builder.suggest(p.getName());
            }
        }
        return builder.buildFuture();
    };

    private SuggestionProvider<CommandSourceStack> grantableTagSuggestions() {
        return (ctx, builder) -> {
            String input = builder.getRemainingLowerCase();
            for (TagDefinition tag : tagManager.getTagRegistry().values()) {
                if (tag.getUnlockType() == UnlockType.GRANTED && tag.getId().toLowerCase().startsWith(input)) {
                    builder.suggest(tag.getId());
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> allTagSuggestions() {
        return (ctx, builder) -> {
            String input = builder.getRemainingLowerCase();
            for (String id : tagManager.getTagRegistry().keySet()) {
                if (id.toLowerCase().startsWith(input)) {
                    builder.suggest(id);
                }
            }
            return builder.buildFuture();
        };
    }

    public TagsCommand(OakTags plugin, ConfigManager config, TagManager tagManager,
                       MessageManager messages, ChatInputListener chatInputListener,
                       TagsYamlWriter yamlWriter) {
        this.plugin = plugin;
        this.config = config;
        this.tagManager = tagManager;
        this.messages = messages;
        this.chatInputListener = chatInputListener;
        this.yamlWriter = yamlWriter;
    }

    public void register() {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(buildCommand(), "Chat tag system", List.of("tag"));
            commands.register(buildRedeemBridgeCommand(),
                    "Bridge command for ExecutableItems/DeluxeMenu tag redemption");
        });
    }

    private LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("tags")
                .requires(src -> src.getSender().hasPermission("oaktags.use"))
                .executes(ctx -> {
                    handleOpen(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            handleHelp(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("oaktags.reload"))
                        .executes(ctx -> {
                            handleReload(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("give")
                        .requires(src -> src.getSender().hasPermission("oaktags.give"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_SUGGESTIONS)
                                .then(Commands.argument("tag", StringArgumentType.word())
                                        .suggests(grantableTagSuggestions())
                                        .executes(ctx -> {
                                            handleGive(ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "tag"));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("revoke")
                        .requires(src -> src.getSender().hasPermission("oaktags.revoke"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_SUGGESTIONS)
                                .then(Commands.argument("tag", StringArgumentType.word())
                                        .suggests(grantableTagSuggestions())
                                        .executes(ctx -> {
                                            handleRevoke(ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "tag"));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("create")
                        .requires(src -> src.getSender() instanceof Player
                                && src.getSender().hasPermission("oaktags.create"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    handleCreate((Player) ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "id"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("edit")
                        .requires(src -> src.getSender() instanceof Player
                                && src.getSender().hasPermission("oaktags.create"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(allTagSuggestions())
                                .executes(ctx -> {
                                    handleEdit((Player) ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "id"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("delete")
                        .requires(src -> src.getSender().hasPermission("oaktags.delete"))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(allTagSuggestions())
                                .executes(ctx -> {
                                    handleDelete(ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "id"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("voucher")
                        .requires(src -> src.getSender().hasPermission("oaktags.voucher"))
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .suggests(allTagSuggestions())
                                .executes(ctx -> {
                                    handleVoucher(ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "tag"),
                                            null, 1);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(PLAYER_SUGGESTIONS)
                                        .executes(ctx -> {
                                            handleVoucher(ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "tag"),
                                                    StringArgumentType.getString(ctx, "player"), 1);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> {
                                                    handleVoucher(ctx.getSource().getSender(),
                                                            StringArgumentType.getString(ctx, "tag"),
                                                            StringArgumentType.getString(ctx, "player"),
                                                            IntegerArgumentType.getInteger(ctx, "amount"));
                                                    return Command.SINGLE_SUCCESS;
                                                })))))
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission("oaktags.list"))
                        .executes(ctx -> {
                            handleList(ctx.getSource().getSender(), null);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_SUGGESTIONS)
                                .executes(ctx -> {
                                    handleList(ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "player"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(src -> src.getSender() instanceof Player
                                && src.getSender().hasPermission("oaktags.admin"))
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> {
                            handleAdminOpen((Player) ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "player"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private void handleOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.sendCommand(sender, "player-only");
            return;
        }
        if (tagManager.getPlayerData(player.getUniqueId()) == null) {
            return;
        }
        new TagsGUI(plugin, player).open();
    }

    private void handleHelp(CommandSender sender) {
        messages.sendCommand(sender, "help-header");
        messages.sendCommand(sender, "help-use");
        if (sender.hasPermission("oaktags.reload")) messages.sendCommand(sender, "help-reload");
        if (sender.hasPermission("oaktags.give")) messages.sendCommand(sender, "help-give");
        if (sender.hasPermission("oaktags.revoke")) messages.sendCommand(sender, "help-revoke");
        if (sender.hasPermission("oaktags.create")) {
            messages.sendCommand(sender, "help-create");
            messages.sendCommand(sender, "help-edit");
        }
        if (sender.hasPermission("oaktags.delete")) messages.sendCommand(sender, "help-delete");
        if (sender.hasPermission("oaktags.voucher")) messages.sendCommand(sender, "help-voucher");
        if (sender.hasPermission("oaktags.list")) messages.sendCommand(sender, "help-list");
        if (sender.hasPermission("oaktags.admin")) messages.sendCommand(sender, "help-admin");
    }

    private void handleAdminOpen(Player admin, String playerName) {
        Player onlineTarget = Bukkit.getPlayerExact(playerName);
        if (onlineTarget != null) {
            if (tagManager.getPlayerData(onlineTarget.getUniqueId()) == null) return;
            new AdminGUI(plugin, admin, onlineTarget.getUniqueId(),
                    onlineTarget.getName(), true).open();
            return;
        }

        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayerIfCached(playerName);
        if (offlineTarget == null) {
            messages.sendCommand(admin, "player-not-found");
            return;
        }

        UUID targetUuid = offlineTarget.getUniqueId();
        String targetName = offlineTarget.getName() != null ? offlineTarget.getName() : playerName;

        if (tagManager.getPlayerData(targetUuid) != null) {
            new AdminGUI(plugin, admin, targetUuid, targetName, false).open();
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            tagManager.loadPlayer(targetUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!admin.isOnline()) return;
                if (tagManager.getPlayerData(targetUuid) == null) {
                    messages.sendCommand(admin, "player-not-found");
                    return;
                }
                new AdminGUI(plugin, admin, targetUuid, targetName, false).open();
            });
        });
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.reloadPluginConfig();
            messages.sendCommand(sender, "reload-success");
        } catch (Exception e) {
            messages.sendCommand(sender, "reload-failed");
            plugin.getLogger().log(Level.SEVERE, "Reload failed", e);
        }
    }

    private void handleGive(CommandSender sender, String playerName, String tagId) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            messages.sendCommand(sender, "player-not-found");
            return;
        }

        TagDefinition tag = tagManager.getTag(tagId);
        if (tag == null) {
            messages.sendCommand(sender, "tag-not-found",
                    Placeholder.unparsed("tag_id", tagId));
            return;
        }

        if (tag.getUnlockType() != UnlockType.GRANTED) {
            messages.sendCommand(sender, "tag-not-granted-type");
            return;
        }

        if (tagManager.hasTag(target, tagId)) {
            messages.sendCommand(sender, "player-already-has-tag",
                    Placeholder.unparsed("player", target.getName()));
            return;
        }

        tagManager.grantTag(target.getUniqueId(), tagId, sender.getName());

        messages.sendCommand(sender, "tag-given",
                Placeholder.parsed("tag", tag.getDisplay()),
                Placeholder.unparsed("player", target.getName()));

        messages.sendCommand(target, "tag-received",
                Placeholder.parsed("tag", tag.getDisplay()));
    }

    private void handleRevoke(CommandSender sender, String playerName, String tagId) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            messages.sendCommand(sender, "player-not-found");
            return;
        }

        TagDefinition tag = tagManager.getTag(tagId);
        if (tag == null) {
            messages.sendCommand(sender, "tag-not-found",
                    Placeholder.unparsed("tag_id", tagId));
            return;
        }

        if (tag.getUnlockType() != UnlockType.GRANTED) {
            messages.sendCommand(sender, "tag-not-granted-type");
            return;
        }

        if (!tagManager.hasTag(target, tagId)) {
            messages.sendCommand(sender, "player-doesnt-have-tag",
                    Placeholder.unparsed("player", target.getName()));
            return;
        }

        tagManager.revokeTag(target.getUniqueId(), tagId);

        messages.sendCommand(sender, "tag-revoked",
                Placeholder.parsed("tag", tag.getDisplay()),
                Placeholder.unparsed("player", target.getName()));

        messages.sendCommand(target, "tag-revoked-notify",
                Placeholder.parsed("tag", tag.getDisplay()));
    }

    private void handleCreate(Player player, String id) {
        if (!VALID_TAG_ID.matcher(id).matches()) {
            messages.sendCommand(player, "invalid-tag-id",
                    Placeholder.unparsed("tag_id", id));
            return;
        }

        if (tagManager.getTag(id) != null) {
            messages.sendCommand(player, "tag-id-exists",
                    Placeholder.unparsed("tag_id", id));
            return;
        }

        String display = config.getDefaultTagDisplay().replace("<id>", id);
        TagDefinition tag = new TagDefinition(id, display,
                config.getDefaultTagCategory(), UnlockType.GRANTED, null, false,
                new ArrayList<>(config.getDefaultTagLore()), config.getDefaultTagMaterial(), 10000, null);

        new TagEditorGUI(plugin, player, tag, true).open();
    }

    private void handleEdit(Player player, String id) {
        TagDefinition tag = tagManager.getTag(id);
        if (tag == null) {
            messages.sendCommand(player, "tag-not-found",
                    Placeholder.unparsed("tag_id", id));
            return;
        }

        new TagEditorGUI(plugin, player, tag.copy(), false).open();
    }

    private void handleDelete(CommandSender sender, String id) {
        TagDefinition tag = tagManager.getTag(id);
        if (tag == null) {
            messages.sendCommand(sender, "tag-not-found",
                    Placeholder.unparsed("tag_id", id));
            return;
        }

        if (yamlWriter.deleteTag(id)) {
            tagManager.removeTagFromActivePlayers(id);
            tagManager.refreshRegistry();
            messages.sendCommand(sender, "tag-deleted",
                    Placeholder.unparsed("tag_id", id));
        } else {
            messages.sendCommand(sender, "editor-save-failed");
        }
    }

    private void handleVoucher(CommandSender sender, String tagId, String playerName, int amount) {
        TagDefinition tag = tagManager.getTag(tagId);
        if (tag == null) {
            messages.sendCommand(sender, "tag-not-found",
                    Placeholder.unparsed("tag_id", tagId));
            return;
        }

        Player target;
        if (playerName != null) {
            target = Bukkit.getPlayer(playerName);
            if (target == null) {
                messages.sendCommand(sender, "player-not-found");
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            messages.sendCommand(sender, "player-only");
            return;
        }

        ItemStack voucher = createVoucher(tag, amount);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(voucher);
        for (ItemStack drop : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }

        messages.sendCommand(sender, "voucher-given",
                Placeholder.parsed("tag", tag.getDisplay()),
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("amount", String.valueOf(amount)));

        if (!target.equals(sender)) {
            messages.sendCommand(target, "voucher-received",
                    Placeholder.parsed("tag", tag.getDisplay()),
                    Placeholder.unparsed("amount", String.valueOf(amount)));
        }
    }

    private ItemStack createVoucher(TagDefinition tag, int amount) {
        VoucherConfig vc = tag.getVoucherConfig();
        if (vc == null) {
            vc = config.getDefaultVoucherConfig();
        }

        ItemStack item = new ItemStack(vc.getMaterial(), amount);
        ItemMeta meta = item.getItemMeta();

        String name = vc.getName();
        meta.displayName(messages.deserialize(name,
                Placeholder.parsed("tag", tag.getDisplay())));

        List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
        for (String loreLine : vc.getLore()) {
            loreComponents.add(messages.deserialize(loreLine,
                    Placeholder.parsed("tag", tag.getDisplay())));
        }
        meta.lore(loreComponents);

        if (vc.isGlow()) {
            meta.setEnchantmentGlintOverride(true);
        }

        NamespacedKey key = new NamespacedKey(plugin, "tag_voucher_id");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag.getId());

        item.setItemMeta(meta);
        return item;
    }

    private void handleList(CommandSender sender, String playerName) {
        if (playerName == null) {
            if (sender instanceof Player p) {
                sendTagList(sender, p.getName(), p.getUniqueId(), p);
            } else {
                messages.sendCommand(sender, "player-only");
            }
            return;
        }

        Player onlineTarget = Bukkit.getPlayerExact(playerName);
        if (onlineTarget != null) {
            sendTagList(sender, onlineTarget.getName(), onlineTarget.getUniqueId(), onlineTarget);
            return;
        }

        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayerIfCached(playerName);
        if (offlineTarget == null) {
            messages.sendCommand(sender, "player-not-found");
            return;
        }

        UUID targetUuid = offlineTarget.getUniqueId();
        String targetName = offlineTarget.getName() != null ? offlineTarget.getName() : playerName;

        if (tagManager.getPlayerData(targetUuid) != null) {
            sendTagList(sender, targetName, targetUuid, null);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            tagManager.loadPlayer(targetUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (tagManager.getPlayerData(targetUuid) == null) {
                    messages.sendCommand(sender, "player-not-found");
                    return;
                }
                sendTagList(sender, targetName, targetUuid, null);
                if (Bukkit.getPlayer(targetUuid) == null
                        && AdminGUI.countViewers(targetUuid) == 0) {
                    tagManager.evictPlayer(targetUuid);
                }
            });
        });
    }

    // ── ExecutableItems / DeluxeMenu bridge ──────────────────────────────
    // Called as: [console] oaktags-redeem <player> tags.<id>
    // Grants the tag silently — DeluxeMenu handles player messaging.

    private LiteralCommandNode<CommandSourceStack> buildRedeemBridgeCommand() {
        return Commands.literal("oaktags-redeem")
                .requires(src -> !(src.getSender() instanceof Player))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("permission", StringArgumentType.word())
                                .executes(ctx -> {
                                    handleRedeem(
                                            StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "permission"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private void handleRedeem(String playerName, String permission) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            return;
        }

        String stripped = permission.startsWith("tags.") ? permission.substring(5) : permission;
        String tagId = resolveRedeemTagId(stripped);
        if (tagId == null) {
            plugin.getLogger().warning("oaktags-redeem: No matching tag for '" + permission + "'");
            return;
        }

        if (tagManager.hasTag(target, tagId)) {
            return;
        }

        tagManager.grantTag(target.getUniqueId(), tagId, "ei-voucher");
    }

    private String resolveRedeemTagId(String stripped) {
        // Try exact match (must be a GRANTED tag)
        TagDefinition exact = tagManager.getTag(stripped);
        if (exact != null && exact.getUnlockType() == UnlockType.GRANTED) {
            return stripped;
        }

        // Try matching against hyphen-stripped tag IDs
        // (EI configs use LuckPerms-style permissions that strip hyphens: "soul-crusher" → "soulcrusher")
        for (TagDefinition tag : tagManager.getTagRegistry().values()) {
            if (tag.getUnlockType() != UnlockType.GRANTED) continue;
            if (tag.getId().replace("-", "").equalsIgnoreCase(stripped)) {
                return tag.getId();
            }
        }

        return null;
    }

    private void sendTagList(CommandSender sender, String playerName, UUID uuid, Player onlinePlayer) {
        List<TagDefinition> unlocked = new ArrayList<>();
        for (TagDefinition tag : tagManager.getTagRegistry().values()) {
            if (onlinePlayer != null) {
                if (tagManager.hasTag(onlinePlayer, tag.getId())) {
                    unlocked.add(tag);
                }
            } else {
                if (tag.getUnlockType() == UnlockType.GRANTED) {
                    PlayerTagData data = tagManager.getPlayerData(uuid);
                    if (data != null && data.hasGrantedTag(tag.getId())) {
                        unlocked.add(tag);
                    }
                }
            }
        }

        messages.sendCommand(sender, "list-header",
                Placeholder.unparsed("player", playerName));

        if (unlocked.isEmpty()) {
            messages.sendCommand(sender, "list-empty");
        } else {
            for (TagDefinition tag : unlocked) {
                messages.sendCommand(sender, "list-entry",
                        Placeholder.parsed("tag", tag.getDisplay()));
            }
        }
    }
}
