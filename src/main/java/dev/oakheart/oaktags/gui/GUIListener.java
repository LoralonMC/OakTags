package dev.oakheart.oaktags.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        if (event.getInventory().getHolder() instanceof TagsGUI gui) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                gui.handleClick(event.getSlot(), event.isRightClick());
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof ConfirmGUI gui) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                gui.handleClick(event.getSlot());
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof AdminGUI gui) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                gui.handleClick(event.getSlot(), event.isRightClick());
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof TagEditorGUI gui) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                gui.handleClick(event.getSlot(), event.isRightClick());
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TagsGUI
                || event.getInventory().getHolder() instanceof ConfirmGUI
                || event.getInventory().getHolder() instanceof AdminGUI
                || event.getInventory().getHolder() instanceof TagEditorGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TagsGUI) {
            TagsGUI.untrack(event.getPlayer().getUniqueId());
        } else if (event.getInventory().getHolder() instanceof ConfirmGUI) {
            ConfirmGUI.untrack(event.getPlayer().getUniqueId());
        } else if (event.getInventory().getHolder() instanceof AdminGUI gui) {
            gui.handleClose();
        } else if (event.getInventory().getHolder() instanceof TagEditorGUI) {
            TagEditorGUI.untrack(event.getPlayer().getUniqueId());
        }
    }
}
