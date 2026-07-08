package dev.oakheart.oaktags.listeners;

import dev.oakheart.oaktags.OakTags;
import dev.oakheart.oaktags.gui.ConfirmGUI;
import dev.oakheart.oaktags.managers.TagManager;
import dev.oakheart.message.MessageManager;
import dev.oakheart.oaktags.model.TagDefinition;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class VoucherListener implements Listener {
    private final OakTags plugin;
    private final TagManager tagManager;
    private final MessageManager messages;
    private final NamespacedKey voucherKey;

    public VoucherListener(OakTags plugin, TagManager tagManager, MessageManager messages) {
        this.plugin = plugin;
        this.tagManager = tagManager;
        this.messages = messages;
        this.voucherKey = new NamespacedKey(plugin, OakTags.VOUCHER_KEY_ID);
    }

    // Not ignoreCancelled: right-clicking air leaves the interacted-block result as DENY,
    // which makes the event report cancelled by default, so ignoreCancelled would skip
    // RIGHT_CLICK_AIR entirely and vouchers could only be redeemed against a block.
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        String tagId = getVoucherTagId(item);
        if (tagId == null) return;

        event.setCancelled(true);
        handleVoucherUse(player, tagId);
    }

    // Not ignoreCancelled: we must always cancel here so a voucher name tag can never
    // rename a mob, and redemption stays available even if another plugin cancelled first.
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item == null) return;

        String tagId = getVoucherTagId(item);
        if (tagId == null) return;

        // Prevent naming mobs with voucher name tags
        event.setCancelled(true);
        handleVoucherUse(player, tagId);
    }

    private String getVoucherTagId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(voucherKey, PersistentDataType.STRING);
    }

    private void handleVoucherUse(Player player, String tagId) {
        TagDefinition tag = tagManager.getTag(tagId);
        if (tag == null) {
            messages.send(player, "voucher-invalid");
            return;
        }

        // Check if player already has this tag
        if (tagManager.hasTag(player, tagId)) {
            messages.send(player, "tag-already-owned");
            return;
        }

        // Open confirmation GUI
        new ConfirmGUI(plugin, player, tagId).open();
    }
}
