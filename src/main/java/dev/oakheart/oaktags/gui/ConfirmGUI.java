package dev.oakheart.oaktags.gui;

import dev.oakheart.oaktags.OakTags;
import dev.oakheart.oaktags.config.ConfigManager;
import dev.oakheart.oaktags.config.ConfigManager.GuiItemConfig;
import dev.oakheart.oaktags.managers.TagManager;
import dev.oakheart.oaktags.message.MessageManager;
import dev.oakheart.oaktags.model.TagDefinition;
import dev.oakheart.oaktags.model.VoucherConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfirmGUI implements InventoryHolder {
    private static final Map<UUID, ConfirmGUI> openGUIs = new ConcurrentHashMap<>();

    private final OakTags plugin;
    private final Player player;
    private final String tagId;
    private Inventory inventory;

    public ConfirmGUI(OakTags plugin, Player player, String tagId) {
        this.plugin = plugin;
        this.player = player;
        this.tagId = tagId;
    }

    public void open() {
        ConfigManager config = plugin.getConfigManager();
        MessageManager messages = plugin.getMessageManager();
        TagManager tagManager = plugin.getTagManager();
        TagDefinition tag = tagManager.getTag(tagId);
        if (tag == null) return;

        Component title = messages.deserialize(config.getConfirmGuiTitle());
        int size = config.getConfirmGuiSize();
        inventory = Bukkit.createInventory(this, size, title);

        // Fill with filler
        GuiItemConfig fillerConfig = config.getConfirmFillerItem();
        ItemStack filler = new ItemStack(fillerConfig.material());
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }

        // Confirm button
        inventory.setItem(config.getConfirmSlot(), createButton(config.getConfirmItem(), messages));

        // Tag info — rendered like the voucher item
        VoucherConfig vc = tag.getVoucherConfig();
        if (vc == null) vc = config.getDefaultVoucherConfig();
        ItemStack tagItem = new ItemStack(vc.getMaterial());
        ItemMeta tagMeta = tagItem.getItemMeta();
        tagMeta.displayName(messages.deserialize(vc.getName(),
                Placeholder.parsed("tag", tag.getDisplay())));
        List<Component> tagLore = new ArrayList<>();
        for (String loreLine : vc.getLore()) {
            tagLore.add(messages.deserialize(loreLine,
                    Placeholder.parsed("tag", tag.getDisplay())));
        }
        if (!tagLore.isEmpty()) tagMeta.lore(tagLore);
        if (vc.isGlow()) {
            tagMeta.setEnchantmentGlintOverride(true);
        }
        tagItem.setItemMeta(tagMeta);
        inventory.setItem(config.getTagInfoSlot(), tagItem);

        // Deny button
        inventory.setItem(config.getDenySlot(), createButton(config.getDenyItem(), messages));

        player.openInventory(inventory);
        openGUIs.put(player.getUniqueId(), this);
    }

    private ItemStack createButton(GuiItemConfig itemConfig, MessageManager messages) {
        ItemStack item = new ItemStack(itemConfig.material());
        ItemMeta meta = item.getItemMeta();

        if (itemConfig.name() != null && !itemConfig.name().isEmpty()) {
            meta.displayName(messages.deserialize(itemConfig.name()));
        } else {
            meta.displayName(Component.empty());
        }

        if (!itemConfig.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : itemConfig.lore()) {
                if (!line.isEmpty()) lore.add(messages.deserialize(line));
            }
            if (!lore.isEmpty()) meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    public void handleClick(int slot) {
        ConfigManager config = plugin.getConfigManager();
        if (slot == config.getConfirmSlot()) {
            handleConfirm();
        } else if (slot == config.getDenySlot()) {
            player.closeInventory();
        }
    }

    private void handleConfirm() {
        TagManager tagManager = plugin.getTagManager();
        MessageManager messages = plugin.getMessageManager();

        TagDefinition tag = tagManager.getTag(tagId);
        if (tag == null) {
            messages.send(player, "voucher-invalid");
            player.closeInventory();
            return;
        }

        if (tagManager.hasTag(player, tagId)) {
            messages.send(player, "tag-already-owned");
            player.closeInventory();
            return;
        }

        // Find and consume the voucher — verify it's still in the player's hand
        ItemStack voucher = findVoucherInHands();
        if (voucher == null) {
            messages.send(player, "voucher-not-found");
            player.closeInventory();
            return;
        }

        if (voucher.getAmount() > 1) {
            voucher.setAmount(voucher.getAmount() - 1);
        } else {
            // Determine which hand holds it and clear
            if (voucher.equals(player.getInventory().getItemInMainHand())) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        }

        tagManager.grantTag(player.getUniqueId(), tagId, "voucher");

        messages.send(player, "voucher-redeemed",
                Placeholder.parsed("tag", tag.getDisplay()));

        player.closeInventory();
    }

    private ItemStack findVoucherInHands() {
        NamespacedKey key = new NamespacedKey(plugin, "tag_voucher_id");
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isVoucherForTag(mainHand, key)) return mainHand;
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isVoucherForTag(offHand, key)) return offHand;
        return null;
    }

    private boolean isVoucherForTag(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return tagId.equals(id);
    }

    public String getTagId() {
        return tagId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void untrack(UUID uuid) {
        openGUIs.remove(uuid);
    }

    public static void closeAll() {
        for (Map.Entry<UUID, ConfirmGUI> entry : openGUIs.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.closeInventory();
            }
        }
        openGUIs.clear();
    }
}
