package dev.oakheart.oaktags.gui;

import dev.oakheart.oaktags.OakTags;
import dev.oakheart.oaktags.config.ConfigManager;
import dev.oakheart.oaktags.listeners.ChatInputListener;
import dev.oakheart.oaktags.managers.TagManager;
import dev.oakheart.oaktags.message.MessageManager;
import dev.oakheart.oaktags.model.TagDefinition;
import dev.oakheart.oaktags.model.UnlockType;
import dev.oakheart.oaktags.model.VoucherConfig;
import dev.oakheart.oaktags.util.TagsYamlWriter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TagEditorGUI implements InventoryHolder {
    private static final Map<UUID, TagEditorGUI> openEditors = new ConcurrentHashMap<>();

    // Slot constants
    private static final int SLOT_DISPLAY = 0;
    private static final int SLOT_CATEGORY = 1;
    private static final int SLOT_MATERIAL = 2;
    private static final int SLOT_UNLOCK_TYPE = 3;
    private static final int SLOT_PERMISSION = 4;
    private static final int SLOT_HIDDEN = 5;
    private static final int SLOT_LORE = 6;
    private static final int SLOT_TAG_PREVIEW = 13;
    private static final int SLOT_V_MATERIAL = 18;
    private static final int SLOT_V_NAME = 19;
    private static final int SLOT_V_LORE = 20;
    private static final int SLOT_V_GLOW = 21;
    private static final int SLOT_VOUCHER_PREVIEW = 31;
    private static final int SLOT_SAVE = 39;
    private static final int SLOT_DELETE = 41;
    private static final int SLOT_CANCEL = 43;

    private static final int INPUT_TIMEOUT = 60;

    private final OakTags plugin;
    private final Player player;
    private final TagDefinition workingCopy;
    private final boolean isNewTag;
    private Inventory inventory;

    public TagEditorGUI(OakTags plugin, Player player, TagDefinition workingCopy, boolean isNewTag) {
        this.plugin = plugin;
        this.player = player;
        this.workingCopy = workingCopy;
        this.isNewTag = isNewTag;
    }

    public void open() {
        MessageManager messages = plugin.getMessageManager();
        Component title = messages.deserialize("<#6C757D>Tag Editor: <#FCD472>" + workingCopy.getId());
        inventory = Bukkit.createInventory(this, 45, title);
        populate();
        player.openInventory(inventory);
        openEditors.put(player.getUniqueId(), this);
    }

    private void populate() {
        MessageManager messages = plugin.getMessageManager();
        ConfigManager config = plugin.getConfigManager();

        // Fill all with filler
        ItemStack filler = createFiller(messages, config);
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, filler);
        }

        // Row 0: Editor buttons
        inventory.setItem(SLOT_DISPLAY, createEditorButton(Material.OAK_SIGN,
                "<#f2ebd7>Display",
                List.of("<#6C757D>Current: " + workingCopy.getDisplay(),
                        "", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴇᴅɪᴛ]"),
                messages));

        String categoryDisplay = config.getCategoryDisplayNames()
                .getOrDefault(workingCopy.getCategory(), workingCopy.getCategory());
        inventory.setItem(SLOT_CATEGORY, createEditorButton(
                config.getCategoryMaterial(workingCopy.getCategory()),
                "<#f2ebd7>Category",
                List.of("<#6C757D>Current: <#FCD472>" + categoryDisplay,
                        "", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴄʏᴄʟᴇ]"),
                messages));

        inventory.setItem(SLOT_MATERIAL, createEditorButton(workingCopy.getMaterial(),
                "<#f2ebd7>Material",
                List.of("<#6C757D>Current: <#FCD472>" + workingCopy.getMaterial().name(),
                        "", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴇᴅɪᴛ]"),
                messages));

        inventory.setItem(SLOT_UNLOCK_TYPE, createEditorButton(
                workingCopy.getUnlockType() == UnlockType.GRANTED ? Material.CHEST : Material.TRIPWIRE_HOOK,
                "<#f2ebd7>Unlock Type",
                List.of("<#6C757D>Current: <#FCD472>" + workingCopy.getUnlockType().name().toLowerCase(),
                        "", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴛᴏɢɢʟᴇ]"),
                messages));

        if (workingCopy.getUnlockType() == UnlockType.PERMISSION) {
            inventory.setItem(SLOT_PERMISSION, createEditorButton(Material.PAPER,
                    "<#f2ebd7>Permission",
                    List.of("<#6C757D>Current: <#FCD472>" + workingCopy.getUnlockPermission(),
                            "", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴇᴅɪᴛ]"),
                    messages));
        }

        inventory.setItem(SLOT_HIDDEN, createEditorButton(
                workingCopy.isHidden() ? Material.ENDER_EYE : Material.ENDER_PEARL,
                "<#f2ebd7>Hidden",
                List.of("<#6C757D>Current: " + (workingCopy.isHidden() ? "<#8FAA87>true" : "<#C27B6B>false"),
                        "", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴛᴏɢɢʟᴇ]"),
                messages));

        List<String> loreSummary = new ArrayList<>();
        if (workingCopy.getLore().isEmpty()) {
            loreSummary.add("<#6C757D>No lore lines set.");
        } else {
            for (String line : workingCopy.getLore()) {
                loreSummary.add("<#6C757D>- <#f2ebd7>" + line);
            }
        }
        loreSummary.add("");
        loreSummary.add("<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴇᴅɪᴛ]");
        inventory.setItem(SLOT_LORE, createEditorButton(Material.WRITABLE_BOOK,
                "<#f2ebd7>Lore", loreSummary, messages));

        // Row 1: Tag preview
        inventory.setItem(SLOT_TAG_PREVIEW, createTagPreview(messages, config));

        // Row 2: Voucher editor buttons
        VoucherConfig vc = getEffectiveVoucher(config);

        inventory.setItem(SLOT_V_MATERIAL, createEditorButton(vc.getMaterial(),
                "<#f2ebd7>Voucher Material",
                List.of("<#6C757D>Current: <#FCD472>" + vc.getMaterial().name(),
                        "", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴇᴅɪᴛ]"),
                messages));

        // Resolve <tag> in voucher name/lore so MiniMessage doesn't strip it
        String resolvedVName = vc.getName().replace("<tag>", workingCopy.getDisplay());
        inventory.setItem(SLOT_V_NAME, createEditorButton(Material.NAME_TAG,
                "<#f2ebd7>Voucher Name",
                List.of("<#6C757D>Current: " + resolvedVName,
                        "", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴇᴅɪᴛ]"),
                messages));

        List<String> vLoreSummary = new ArrayList<>();
        if (vc.getLore().isEmpty()) {
            vLoreSummary.add("<#6C757D>No lore lines set.");
        } else {
            for (String line : vc.getLore()) {
                String resolvedLine = line.replace("<tag>", workingCopy.getDisplay());
                vLoreSummary.add("<#6C757D>- " + resolvedLine);
            }
        }
        vLoreSummary.add("");
        vLoreSummary.add("<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴇᴅɪᴛ]");
        inventory.setItem(SLOT_V_LORE, createEditorButton(Material.WRITABLE_BOOK,
                "<#f2ebd7>Voucher Lore", vLoreSummary, messages));

        inventory.setItem(SLOT_V_GLOW, createEditorButton(
                vc.isGlow() ? Material.GLOWSTONE : Material.REDSTONE_LAMP,
                "<#f2ebd7>Voucher Glow",
                List.of("<#6C757D>Current: " + (vc.isGlow() ? "<#8FAA87>true" : "<#C27B6B>false"),
                        "", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴛᴏɢɢʟᴇ]"),
                messages));

        // Row 3: Voucher preview
        inventory.setItem(SLOT_VOUCHER_PREVIEW, createVoucherPreview(messages, config));

        // Row 4: Action buttons
        inventory.setItem(SLOT_SAVE, createEditorButton(Material.LIME_STAINED_GLASS_PANE,
                "<#8FAA87>Save", List.of("<#f2ebd7>Write tag to tags.yml"), messages));

        if (!isNewTag) {
            inventory.setItem(SLOT_DELETE, createEditorButton(Material.RED_STAINED_GLASS_PANE,
                    "<#C27B6B>Delete", List.of("<#f2ebd7>Remove tag from tags.yml"), messages));
        }

        inventory.setItem(SLOT_CANCEL, createEditorButton(Material.BARRIER,
                "<#C27B6B>Cancel", List.of("<#f2ebd7>Discard changes"), messages));
    }

    private ItemStack createFiller(MessageManager messages, ConfigManager config) {
        ConfigManager.GuiItemConfig fillerConfig = config.getFillerItem();
        ItemStack filler = new ItemStack(fillerConfig.material());
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);
        return filler;
    }

    private ItemStack createEditorButton(Material material, String name, List<String> lore,
                                          MessageManager messages) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.deserialize(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(messages.deserialize(line));
            }
            meta.lore(loreComponents);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTagPreview(MessageManager messages, ConfigManager config) {
        ItemStack item = new ItemStack(workingCopy.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(messages.deserialize(config.getTagDisplayFormat(),
                Placeholder.parsed("tag", workingCopy.getDisplay()),
                Placeholder.unparsed("player", player.getName())));

        List<Component> lore = new ArrayList<>();
        lore.add(messages.deserialize("<#8FAA87>Unlocked"));
        for (String loreLine : workingCopy.getLore()) {
            lore.add(messages.deserialize(loreLine, Placeholder.unparsed("count", "0")));
        }
        lore.add(Component.empty());
        lore.add(messages.deserialize("<#6C757D>ᴛᴀɢ ᴘʀᴇᴠɪᴇᴡ"));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createVoucherPreview(MessageManager messages, ConfigManager config) {
        VoucherConfig vc = getEffectiveVoucher(config);

        ItemStack item = new ItemStack(vc.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(messages.deserialize(vc.getName(),
                Placeholder.parsed("tag", workingCopy.getDisplay())));

        List<Component> lore = new ArrayList<>();
        for (String loreLine : vc.getLore()) {
            lore.add(messages.deserialize(loreLine,
                    Placeholder.parsed("tag", workingCopy.getDisplay())));
        }
        if (!lore.isEmpty()) meta.lore(lore);

        if (vc.isGlow()) {
            meta.setEnchantmentGlintOverride(true);
        }

        item.setItemMeta(meta);
        return item;
    }

    private VoucherConfig getEffectiveVoucher(ConfigManager config) {
        VoucherConfig vc = workingCopy.getVoucherConfig();
        return vc != null ? vc : config.getDefaultVoucherConfig();
    }

    private void ensureVoucherConfig() {
        if (workingCopy.getVoucherConfig() == null) {
            workingCopy.setVoucherConfig(plugin.getConfigManager().getDefaultVoucherConfig().copy());
        }
    }

    public void handleClick(int slot, boolean rightClick) {
        switch (slot) {
            case SLOT_DISPLAY -> promptChatInput(
                    "<#6C757D>[<#6B7A5E>ᴛᴀɢꜱ<#6C757D>] <#f2ebd7>Type the new <#FCD472>display<#f2ebd7> (MiniMessage), or <#FCD472>cancel<#f2ebd7>:",
                    workingCopy.getDisplay(),
                    input -> {
                        workingCopy.setDisplay(input);
                        open();
                    });
            case SLOT_CATEGORY -> {
                List<String> catKeys = plugin.getConfigManager().getCategoryKeys();
                if (catKeys.isEmpty()) return;
                int idx = catKeys.indexOf(workingCopy.getCategory());
                int size = catKeys.size();
                int next = rightClick ? (idx - 1 + size) % size : (idx + 1) % size;
                workingCopy.setCategory(catKeys.get(next));
                refreshInPlace();
            }
            case SLOT_MATERIAL -> promptChatInput(
                    "<#6C757D>[<#6B7A5E>ᴛᴀɢꜱ<#6C757D>] <#f2ebd7>Type the new <#FCD472>material<#f2ebd7> name, or <#FCD472>cancel<#f2ebd7>:",
                    workingCopy.getMaterial().name(),
                    input -> handleMaterialInput(input, mat -> {
                        workingCopy.setMaterial(mat);
                        open();
                    }));
            case SLOT_UNLOCK_TYPE -> {
                if (workingCopy.getUnlockType() == UnlockType.GRANTED) {
                    workingCopy.setUnlockType(UnlockType.PERMISSION);
                    if (workingCopy.getUnlockPermission() == null || workingCopy.getUnlockPermission().isEmpty()) {
                        workingCopy.setUnlockPermission("tags." + workingCopy.getId());
                    }
                } else {
                    workingCopy.setUnlockType(UnlockType.GRANTED);
                }
                refreshInPlace();
            }
            case SLOT_PERMISSION -> {
                if (workingCopy.getUnlockType() != UnlockType.PERMISSION) return;
                promptChatInput(
                        "<#6C757D>[<#6B7A5E>ᴛᴀɢꜱ<#6C757D>] <#f2ebd7>Type the new <#FCD472>permission<#f2ebd7> node, or <#FCD472>cancel<#f2ebd7>:",
                        workingCopy.getUnlockPermission(),
                        input -> {
                            workingCopy.setUnlockPermission(input);
                            open();
                        });
            }
            case SLOT_HIDDEN -> {
                workingCopy.setHidden(!workingCopy.isHidden());
                refreshInPlace();
            }
            case SLOT_LORE -> {
                String currentLore = workingCopy.getLore().isEmpty()
                        ? null : String.join(" | ", workingCopy.getLore());
                promptChatInput(
                    "<#6C757D>[<#6B7A5E>ᴛᴀɢꜱ<#6C757D>] <#f2ebd7>Type lore lines separated by <#FCD472>|<#f2ebd7>, <#FCD472>clear<#f2ebd7> to remove all, or <#FCD472>cancel<#f2ebd7>:",
                    currentLore,
                    input -> {
                        if ("clear".equalsIgnoreCase(input.trim())) {
                            workingCopy.setLore(new ArrayList<>());
                        } else {
                            List<String> lines = new ArrayList<>(Arrays.asList(input.split("\\|")));
                            lines.replaceAll(String::trim);
                            workingCopy.setLore(lines);
                        }
                        open();
                    });
            }
            case SLOT_V_MATERIAL -> {
                VoucherConfig currentVc = getEffectiveVoucher(plugin.getConfigManager());
                promptChatInput(
                    "<#6C757D>[<#6B7A5E>ᴛᴀɢꜱ<#6C757D>] <#f2ebd7>Type the new <#FCD472>voucher material<#f2ebd7>, or <#FCD472>cancel<#f2ebd7>:",
                    currentVc.getMaterial().name(),
                    input -> handleMaterialInput(input, mat -> {
                        ensureVoucherConfig();
                        workingCopy.setVoucherConfig(workingCopy.getVoucherConfig().withMaterial(mat));
                        open();
                    }));
            }
            case SLOT_V_NAME -> {
                VoucherConfig currentVc = getEffectiveVoucher(plugin.getConfigManager());
                promptChatInput(
                    "<#6C757D>[<#6B7A5E>ᴛᴀɢꜱ<#6C757D>] <#f2ebd7>Type the new <#FCD472>voucher name<#f2ebd7> (MiniMessage), or <#FCD472>cancel<#f2ebd7>:",
                    currentVc.getName(),
                    input -> {
                        ensureVoucherConfig();
                        workingCopy.setVoucherConfig(workingCopy.getVoucherConfig().withName(input));
                        open();
                    });
            }
            case SLOT_V_LORE -> {
                VoucherConfig currentVc = getEffectiveVoucher(plugin.getConfigManager());
                String currentVLore = currentVc.getLore().isEmpty()
                        ? null : String.join(" | ", currentVc.getLore());
                promptChatInput(
                    "<#6C757D>[<#6B7A5E>ᴛᴀɢꜱ<#6C757D>] <#f2ebd7>Type voucher lore lines separated by <#FCD472>|<#f2ebd7>, <#FCD472>clear<#f2ebd7> to remove all, or <#FCD472>cancel<#f2ebd7>:",
                    currentVLore,
                    input -> {
                        ensureVoucherConfig();
                        if ("clear".equalsIgnoreCase(input.trim())) {
                            workingCopy.setVoucherConfig(workingCopy.getVoucherConfig().withLore(new ArrayList<>()));
                        } else {
                            List<String> lines = new ArrayList<>(Arrays.asList(input.split("\\|")));
                            lines.replaceAll(String::trim);
                            workingCopy.setVoucherConfig(workingCopy.getVoucherConfig().withLore(lines));
                        }
                        open();
                    });
            }
            case SLOT_V_GLOW -> {
                ensureVoucherConfig();
                workingCopy.setVoucherConfig(workingCopy.getVoucherConfig().withGlow(
                        !workingCopy.getVoucherConfig().isGlow()));
                refreshInPlace();
            }
            case SLOT_SAVE -> handleSave();
            case SLOT_DELETE -> handleDelete();
            case SLOT_CANCEL -> {
                player.closeInventory();
                plugin.getMessageManager().sendCommand(player, "editor-cancelled");
            }
        }
    }

    private void refreshInPlace() {
        inventory.clear();
        populate();
    }

    private void promptChatInput(String prompt, String currentValue,
                                  java.util.function.Consumer<String> onInput) {
        player.closeInventory();
        MessageManager messages = plugin.getMessageManager();
        player.sendMessage(messages.deserialize(prompt));

        if (currentValue != null && !currentValue.isEmpty()) {
            Component editButton = messages.deserialize("<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴇᴅɪᴛ ᴄᴜʀʀᴇɴᴛ]")
                    .clickEvent(ClickEvent.suggestCommand(currentValue));
            player.sendMessage(editButton);
        }

        ChatInputListener chatInput = plugin.getChatInputListener();
        chatInput.awaitInput(player, input -> {
            if (input == null) {
                messages.sendCommand(player, "editor-input-cancelled");
                open();
                return;
            }
            onInput.accept(input);
        }, INPUT_TIMEOUT);
    }

    private void handleMaterialInput(String input, java.util.function.Consumer<Material> onValid) {
        Material mat = Material.matchMaterial(input.trim().toUpperCase());
        if (mat == null) {
            MessageManager messages = plugin.getMessageManager();
            messages.sendCommand(player, "invalid-material",
                    Placeholder.unparsed("value", input.trim()));
            // Re-prompt
            promptChatInput(
                    "<#6C757D>[<#6B7A5E>ᴛᴀɢꜱ<#6C757D>] <#f2ebd7>Type a valid <#FCD472>material<#f2ebd7> name, or <#FCD472>cancel<#f2ebd7>:",
                    null,
                    retryInput -> handleMaterialInput(retryInput, onValid));
            return;
        }
        onValid.accept(mat);
    }

    private void handleSave() {
        TagsYamlWriter yamlWriter = plugin.getTagsYamlWriter();
        TagManager tagManager = plugin.getTagManager();
        MessageManager messages = plugin.getMessageManager();

        boolean success;
        if (isNewTag) {
            success = yamlWriter.writeNewTag(workingCopy);
        } else {
            success = yamlWriter.replaceTagBlock(workingCopy);
        }

        if (success) {
            tagManager.refreshRegistry();
            messages.sendCommand(player, "editor-saved",
                    Placeholder.unparsed("tag_id", workingCopy.getId()));
        } else {
            messages.sendCommand(player, "editor-save-failed");
        }

        player.closeInventory();
    }

    private void handleDelete() {
        if (isNewTag) return;

        player.closeInventory();
        MessageManager messages = plugin.getMessageManager();
        player.sendMessage(messages.deserialize(
                "<#6C757D>[<#6B7A5E>ᴛᴀɢꜱ<#6C757D>] <#D89B6A>Type <#FCD472>confirm<#D89B6A> to delete <#FCD472>"
                        + workingCopy.getId() + "<#D89B6A>, or <#FCD472>cancel<#D89B6A>:"));

        ChatInputListener chatInput = plugin.getChatInputListener();
        chatInput.awaitInput(player, input -> {
            if (input != null && "confirm".equalsIgnoreCase(input.trim())) {
                TagsYamlWriter yamlWriter = plugin.getTagsYamlWriter();
                TagManager tagManager = plugin.getTagManager();

                if (yamlWriter.deleteTag(workingCopy.getId())) {
                    tagManager.removeTagFromActivePlayers(workingCopy.getId());
                    tagManager.refreshRegistry();
                    messages.sendCommand(player, "tag-deleted",
                            Placeholder.unparsed("tag_id", workingCopy.getId()));
                } else {
                    messages.sendCommand(player, "editor-save-failed");
                }
            } else {
                messages.sendCommand(player, "editor-input-cancelled");
            }
        }, INPUT_TIMEOUT);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void untrack(UUID uuid) {
        openEditors.remove(uuid);
    }

    public static void closeAll() {
        for (Map.Entry<UUID, TagEditorGUI> entry : openEditors.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.closeInventory();
            }
        }
        openEditors.clear();
    }
}
