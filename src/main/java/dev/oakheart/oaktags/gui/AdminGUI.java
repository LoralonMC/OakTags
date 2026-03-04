package dev.oakheart.oaktags.gui;

import dev.oakheart.oaktags.OakTags;
import dev.oakheart.oaktags.config.ConfigManager;
import dev.oakheart.oaktags.config.ConfigManager.GuiItemConfig;
import dev.oakheart.oaktags.managers.TagManager;
import dev.oakheart.oaktags.message.MessageManager;
import dev.oakheart.oaktags.model.PlayerTagData;
import dev.oakheart.oaktags.model.TagDefinition;
import dev.oakheart.oaktags.model.UnlockType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdminGUI implements InventoryHolder {
    private static final Map<UUID, AdminGUI> openGUIs = new ConcurrentHashMap<>();

    private final OakTags plugin;
    private final ConfigManager config;
    private final TagManager tagManager;
    private final MessageManager messages;
    private final Player admin;
    private final UUID targetUuid;
    private final String targetName;
    private final boolean targetOnline;

    private Inventory inventory;
    private List<TagDefinition> allTags;
    private int page;
    private int tagRows;
    private int inventorySize;

    public AdminGUI(OakTags plugin, Player admin, UUID targetUuid, String targetName, boolean targetOnline) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.tagManager = plugin.getTagManager();
        this.messages = plugin.getMessageManager();
        this.admin = admin;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.targetOnline = targetOnline;
        this.page = 0;
    }

    public void open() {
        refreshTagList();
        buildInventory();
        admin.openInventory(inventory);
        openGUIs.put(admin.getUniqueId(), this);
        playSound(config.getOpenSound());
    }

    private void refresh() {
        refreshTagList();
        int newSize = calculateInventorySize();
        if (newSize != inventorySize) {
            buildInventory();
            admin.openInventory(inventory);
        } else {
            inventory.clear();
            populateInventory();
        }
    }

    private void refreshTagList() {
        allTags = new ArrayList<>(tagManager.getTagRegistry().values());
        allTags.sort(Comparator.comparing(TagDefinition::getId, String.CASE_INSENSITIVE_ORDER));

        int maxPage = getMaxPage();
        if (page > maxPage) page = maxPage;
    }

    private int calculateInventorySize() {
        int tagCount = allTags.size();
        tagRows = Math.min(config.getMaxTagRows(), Math.max(1, (int) Math.ceil(tagCount / 9.0)));
        return (tagRows + 1) * 9;
    }

    private void buildInventory() {
        inventorySize = calculateInventorySize();
        Component title = messages.deserialize(config.getAdminGuiTitle(),
                Placeholder.unparsed("player", targetName));
        inventory = Bukkit.createInventory(this, inventorySize, title);
        populateInventory();
    }

    private void populateInventory() {
        int slotsForTags = tagRows * 9;
        int startIndex = page * slotsForTags;
        int endIndex = Math.min(startIndex + slotsForTags, allTags.size());

        for (int i = startIndex; i < endIndex; i++) {
            TagDefinition tag = allTags.get(i);
            int slot = i - startIndex;
            inventory.setItem(slot, createAdminTagItem(tag));
        }

        int navRowStart = tagRows * 9;
        populateNavBar(navRowStart);
    }

    private void populateNavBar(int navRowStart) {
        // Fill nav bar with filler
        ItemStack filler = createGuiItem(config.getFillerItem());
        for (int i = 0; i < 9; i++) {
            inventory.setItem(navRowStart + i, filler);
        }

        // Previous page
        if (page > 0) {
            inventory.setItem(navRowStart + config.getNavPrevSlot(),
                    createGuiItem(config.getPrevPageItem()));
        }

        // Next page
        if (page < getMaxPage()) {
            inventory.setItem(navRowStart + config.getNavNextSlot(),
                    createGuiItem(config.getNextPageItem()));
        }

        // Page indicator
        if (getMaxPage() > 0) {
            inventory.setItem(navRowStart + config.getNavPageIndicatorSlot(),
                    createGuiItem(config.getPageIndicatorItem(),
                            Placeholder.unparsed("current", String.valueOf(page + 1)),
                            Placeholder.unparsed("total", String.valueOf(getMaxPage() + 1))));
        }
    }

    private ItemStack createAdminTagItem(TagDefinition tag) {
        boolean unlocked = isTagUnlocked(tag);

        Material material = unlocked ? tag.getMaterial() : config.getLockedTagMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.deserialize(config.getTagDisplayFormat(),
                Placeholder.parsed("tag", tag.getDisplay()),
                Placeholder.unparsed("player", targetName)));

        // Resolve status text
        String statusText = unlocked
                ? config.getAdminTagLoreStatusUnlocked()
                : config.getAdminTagLoreStatusLocked();

        // Resolve click action text
        String clickText;
        if (tag.getUnlockType() == UnlockType.GRANTED) {
            clickText = unlocked
                    ? config.getAdminTagLoreClickRevoke()
                    : config.getAdminTagLoreClickGrant();
        } else {
            // Permission-based tag
            if (targetOnline) {
                clickText = unlocked
                        ? config.getAdminTagLorePermissionUnlocked()
                        : config.getAdminTagLorePermissionLocked();
            } else {
                clickText = config.getAdminTagLorePermissionOffline();
            }
        }

        TagResolver resolvers = TagResolver.resolver(
                Placeholder.parsed("status", statusText),
                Placeholder.parsed("click_action", clickText),
                Placeholder.unparsed("tag_id", tag.getId()),
                Placeholder.unparsed("count", String.valueOf(tagManager.getClaimCount(tag.getId())))
        );

        List<Component> lore = messages.resolveLoreLayout(
                config.getAdminTagLoreLayout(), tag.getLore(), resolvers);
        if (!lore.isEmpty()) meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private boolean isTagUnlocked(TagDefinition tag) {
        if (tag.getUnlockType() == UnlockType.GRANTED) {
            PlayerTagData data = tagManager.getPlayerData(targetUuid);
            return data != null && data.hasGrantedTag(tag.getId());
        }
        // Permission-based: check if online player has permission
        if (targetOnline) {
            Player target = Bukkit.getPlayer(targetUuid);
            return target != null && target.hasPermission(tag.getUnlockPermission());
        }
        return false;
    }

    public void handleClick(int slot, boolean rightClick) {
        int navRowStart = tagRows * 9;

        if (slot >= navRowStart) {
            handleNavClick(slot - navRowStart, rightClick);
            return;
        }

        int slotsForTags = tagRows * 9;
        int index = page * slotsForTags + slot;
        if (index < 0 || index >= allTags.size()) return;

        TagDefinition tag = allTags.get(index);

        if (tag.getUnlockType() == UnlockType.PERMISSION) {
            playSound(config.getLockedSound());
            messages.sendCommand(admin, "admin-permission-managed");
            return;
        }

        boolean unlocked = isTagUnlocked(tag);
        if (unlocked) {
            // Revoke
            tagManager.revokeTag(targetUuid, tag.getId());
            messages.sendCommand(admin, "tag-revoked",
                    Placeholder.parsed("tag", tag.getDisplay()),
                    Placeholder.unparsed("player", targetName));

            // Notify target if online
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null) {
                messages.sendCommand(target, "tag-revoked-notify",
                        Placeholder.parsed("tag", tag.getDisplay()));
            }
        } else {
            // Grant
            tagManager.grantTag(targetUuid, tag.getId(), admin.getName());
            messages.sendCommand(admin, "tag-given",
                    Placeholder.parsed("tag", tag.getDisplay()),
                    Placeholder.unparsed("player", targetName));

            // Notify target if online
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null) {
                messages.sendCommand(target, "tag-received",
                        Placeholder.parsed("tag", tag.getDisplay()));
            }
        }

        playSound(config.getClickSound());
        refresh();
    }

    private void handleNavClick(int navSlot, boolean rightClick) {
        if (navSlot == config.getNavPrevSlot()) {
            if (page > 0) {
                page = rightClick ? 0 : page - 1;
                playSound(config.getClickSound());
                refresh();
            }
        } else if (navSlot == config.getNavNextSlot()) {
            if (page < getMaxPage()) {
                page = rightClick ? getMaxPage() : page + 1;
                playSound(config.getClickSound());
                refresh();
            }
        }
    }

    public void handleClose() {
        openGUIs.remove(admin.getUniqueId());
        if (Bukkit.getPlayer(targetUuid) == null && countViewers(targetUuid) == 0) {
            tagManager.evictPlayer(targetUuid);
        }
    }

    private int getMaxPage() {
        int slotsForTags = tagRows * 9;
        if (slotsForTags == 0) return 0;
        return Math.max(0, (int) Math.ceil(allTags.size() / (double) slotsForTags) - 1);
    }

    private void playSound(String sound) {
        if (sound == null || sound.isEmpty()) return;
        try {
            admin.playSound(Sound.sound(Key.key(sound), Sound.Source.MASTER, 1.0f, 1.0f));
        } catch (Exception ignored) {
        }
    }

    private ItemStack createGuiItem(GuiItemConfig itemConfig, TagResolver... resolvers) {
        ItemStack item = new ItemStack(itemConfig.material());
        ItemMeta meta = item.getItemMeta();

        if (itemConfig.name() == null || itemConfig.name().isEmpty()) {
            meta.displayName(Component.empty());
        } else {
            meta.displayName(messages.deserialize(itemConfig.name(), resolvers));
        }

        if (itemConfig.lore() != null && !itemConfig.lore().isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : itemConfig.lore()) {
                loreComponents.add(line.isEmpty() ? Component.empty() : messages.deserialize(line, resolvers));
            }
            if (!loreComponents.isEmpty()) meta.lore(loreComponents);
        }

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public static int countViewers(UUID targetUuid) {
        int count = 0;
        for (AdminGUI gui : openGUIs.values()) {
            if (gui.targetUuid.equals(targetUuid)) count++;
        }
        return count;
    }

    public static void closeAll() {
        for (Map.Entry<UUID, AdminGUI> entry : openGUIs.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.closeInventory();
            }
        }
        openGUIs.clear();
    }
}
