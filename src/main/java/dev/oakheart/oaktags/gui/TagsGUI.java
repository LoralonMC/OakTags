package dev.oakheart.oaktags.gui;

import dev.oakheart.oaktags.OakTags;
import dev.oakheart.oaktags.config.ConfigManager;
import dev.oakheart.oaktags.config.ConfigManager.GuiItemConfig;
import dev.oakheart.oaktags.managers.TagManager;
import dev.oakheart.oaktags.message.MessageManager;
import dev.oakheart.oaktags.model.*;
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

public class TagsGUI implements InventoryHolder {
    private static final Map<UUID, TagsGUI> openGUIs = new ConcurrentHashMap<>();

    private final OakTags plugin;
    private final ConfigManager config;
    private final TagManager tagManager;
    private final MessageManager messages;
    private final Player player;

    private Inventory inventory;
    private List<TagDefinition> filteredTags;
    private int page;
    private int tagRows;
    private int inventorySize;

    public TagsGUI(OakTags plugin, Player player) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.tagManager = plugin.getTagManager();
        this.messages = plugin.getMessageManager();
        this.player = player;
        this.page = 0;
    }

    public void open() {
        refreshTagList();
        buildInventory();
        player.openInventory(inventory);
        openGUIs.put(player.getUniqueId(), this);
        playSound(config.getOpenSound());
    }

    public void refresh() {
        refreshTagList();
        int newSize = calculateInventorySize();
        if (newSize != inventorySize) {
            buildInventory();
            player.openInventory(inventory);
        } else {
            inventory.clear();
            populateInventory();
        }
    }

    private void refreshTagList() {
        PlayerTagData data = tagManager.getPlayerData(player.getUniqueId());
        if (data == null) return;
        filteredTags = tagManager.getSortedFilteredTags(player,
                data.getSortMode(), data.isSortReversed(), data.getFilterMode());

        int maxPage = getMaxPage();
        if (page > maxPage) page = maxPage;
    }

    private int calculateInventorySize() {
        int tagCount = filteredTags.size();
        tagRows = Math.min(config.getMaxTagRows(), Math.max(1, (int) Math.ceil(tagCount / 9.0)));
        return (tagRows + 1) * 9;
    }

    private void buildInventory() {
        inventorySize = calculateInventorySize();
        Component title = messages.deserialize(config.getGuiTitle());
        inventory = Bukkit.createInventory(this, inventorySize, title);
        populateInventory();
    }

    private void populateInventory() {
        int slotsForTags = tagRows * 9;
        int startIndex = page * slotsForTags;
        int endIndex = Math.min(startIndex + slotsForTags, filteredTags.size());

        for (int i = startIndex; i < endIndex; i++) {
            TagDefinition tag = filteredTags.get(i);
            int slot = i - startIndex;
            inventory.setItem(slot, createTagItem(tag));
        }

        int navRowStart = tagRows * 9;
        populateNavBar(navRowStart);
    }

    private void populateNavBar(int navRowStart) {
        PlayerTagData data = tagManager.getPlayerData(player.getUniqueId());
        if (data == null) return;

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

        // Sort button
        String sortDisplay = config.getSortModeName(data.getSortMode().name());
        inventory.setItem(navRowStart + config.getNavSortSlot(),
                createGuiItem(config.getSortItem(), Placeholder.unparsed("mode", sortDisplay)));

        // Reverse button
        String direction = data.isSortReversed()
                ? config.getReverseDescLabel() : config.getReverseAscLabel();
        Material reverseMat = data.isSortReversed()
                ? config.getReverseDescMaterial() : config.getReverseAscMaterial();
        inventory.setItem(navRowStart + config.getNavReverseSlot(),
                createGuiItem(reverseMat, config.getReverseName(), config.getReverseLore(),
                        Placeholder.unparsed("direction", direction)));

        // Clear button
        inventory.setItem(navRowStart + config.getNavClearSlot(),
                createGuiItem(config.getClearItem()));

        // Filter button
        Map<String, String> catNames = config.getCategoryDisplayNames();
        Map<String, String> filterNames = Map.of(
                "all", config.getFilterModeName("all"),
                "favorites", config.getFilterModeName("favorites"),
                "unlocked", config.getFilterModeName("unlocked"),
                "locked", config.getFilterModeName("locked"));
        String filterDisplay = data.getFilterMode().getDisplayName(catNames, filterNames);
        Material filterMat = getFilterMaterial(data.getFilterMode());
        inventory.setItem(navRowStart + config.getNavFilterSlot(),
                createGuiItem(filterMat, config.getFilterName(), config.getFilterLore(),
                        Placeholder.unparsed("mode", filterDisplay)));

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

    private ItemStack createTagItem(TagDefinition tag) {
        boolean unlocked = tagManager.hasTag(player, tag.getId());
        String activeTagId = tagManager.getActiveTagId(player.getUniqueId());
        boolean active = tag.getId().equals(activeTagId);

        Material material = unlocked ? tag.getMaterial() : config.getLockedTagMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.deserialize(config.getTagDisplayFormat(),
                Placeholder.parsed("tag", tag.getDisplay()),
                Placeholder.unparsed("player", player.getName())));

        // Resolve status text
        String statusText;
        if (active) {
            statusText = config.getTagLoreStatusActive();
        } else if (unlocked) {
            statusText = config.getTagLoreStatusUnlocked();
        } else {
            statusText = config.getTagLoreStatusLocked();
        }

        // Resolve click action text (empty for locked tags — line will be removed)
        String clickText;
        if (!unlocked) {
            clickText = "";
        } else if (active) {
            clickText = config.getTagLoreClickUnequip();
        } else {
            clickText = config.getTagLoreClickEquip();
        }

        // Resolve favorite text (empty when not favorited — line will be removed)
        PlayerTagData data = tagManager.getPlayerData(player.getUniqueId());
        boolean favorited = data != null && data.isFavorite(tag.getId());
        String favoriteText = favorited ? config.getTagLoreFavorite() : "";
        String rightClickText = favorited
                ? config.getTagLoreRightClickUnfavorite()
                : config.getTagLoreRightClickFavorite();

        TagResolver resolvers = TagResolver.resolver(
                Placeholder.parsed("status", statusText),
                Placeholder.parsed("left_click_action", clickText),
                Placeholder.parsed("right_click_action", rightClickText),
                Placeholder.parsed("favorite", favoriteText),
                Placeholder.unparsed("count", String.valueOf(tagManager.getClaimCount(tag.getId())))
        );

        List<Component> lore = messages.resolveLoreLayout(
                config.getTagLoreLayout(), tag.getLore(), resolvers);
        if (!lore.isEmpty()) meta.lore(lore);

        if (active) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuiItem(GuiItemConfig itemConfig, TagResolver... resolvers) {
        return createGuiItem(itemConfig.material(), itemConfig.name(), itemConfig.lore(), resolvers);
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore,
                                     TagResolver... resolvers) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (name == null || name.isEmpty()) {
            meta.displayName(Component.empty());
        } else {
            meta.displayName(messages.deserialize(name, resolvers));
        }

        if (lore != null && !lore.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(line.isEmpty() ? Component.empty() : messages.deserialize(line, resolvers));
            }
            if (!loreComponents.isEmpty()) meta.lore(loreComponents);
        }

        item.setItemMeta(meta);
        return item;
    }

    private Material getFilterMaterial(FilterMode filter) {
        if (filter.isAll()) return config.getFilterAllMaterial();
        if (filter.isFavorites()) return config.getFilterFavoritesMaterial();
        if (filter.isUnlocked()) return config.getFilterUnlockedMaterial();
        if (filter.isLocked()) return config.getFilterLockedMaterial();
        return config.getCategoryMaterial(filter.getValue());
    }

    public void handleClick(int slot, boolean rightClick) {
        PlayerTagData data = tagManager.getPlayerData(player.getUniqueId());
        if (data == null) return;

        int navRowStart = tagRows * 9;

        if (slot >= navRowStart) {
            int navSlot = slot - navRowStart;
            handleNavClick(navSlot, data, rightClick);
            return;
        }

        int slotsForTags = tagRows * 9;
        int index = page * slotsForTags + slot;
        if (index < 0 || index >= filteredTags.size()) return;

        TagDefinition tag = filteredTags.get(index);

        // Right-click toggles favorite (works on both locked and unlocked tags)
        if (rightClick) {
            boolean added = tagManager.toggleFavorite(player.getUniqueId(), tag.getId());
            messages.send(player, added ? "tag-favorited" : "tag-unfavorited");
            playSound(config.getClickSound());
            refresh();
            return;
        }

        boolean unlocked = tagManager.hasTag(player, tag.getId());

        if (!unlocked) {
            playSound(config.getLockedSound());
            return;
        }

        String activeTagId = tagManager.getActiveTagId(player.getUniqueId());
        if (tag.getId().equals(activeTagId)) {
            tagManager.clearActiveTag(player.getUniqueId());
            messages.send(player, "tag-cleared");
        } else {
            tagManager.setActiveTag(player.getUniqueId(), tag.getId());
            messages.send(player, "tag-equipped",
                    Placeholder.parsed("tag", tag.getDisplay()));
            playSound(config.getEquipSound());
        }
        refresh();
    }

    private void handleNavClick(int navSlot, PlayerTagData data, boolean rightClick) {
        if (navSlot == config.getNavPrevSlot()) {
            if (page > 0) {
                page = rightClick ? 0 : page - 1;
                playSound(config.getClickSound());
                refresh();
            }
        } else if (navSlot == config.getNavSortSlot()) {
            data.setSortMode(rightClick ? data.getSortMode().previous() : data.getSortMode().next());
            tagManager.markDirty(player.getUniqueId());
            page = 0;
            playSound(config.getClickSound());
            refresh();
        } else if (navSlot == config.getNavReverseSlot()) {
            data.setSortReversed(!data.isSortReversed());
            tagManager.markDirty(player.getUniqueId());
            playSound(config.getClickSound());
            refresh();
        } else if (navSlot == config.getNavClearSlot()) {
            tagManager.clearActiveTag(player.getUniqueId());
            messages.send(player, "tag-cleared");
            playSound(config.getClickSound());
            refresh();
        } else if (navSlot == config.getNavFilterSlot()) {
            List<String> catKeys = config.getCategoryKeys();
            data.setFilterMode(rightClick
                    ? data.getFilterMode().previous(catKeys)
                    : data.getFilterMode().next(catKeys));
            tagManager.markDirty(player.getUniqueId());
            page = 0;
            playSound(config.getClickSound());
            refresh();
        } else if (navSlot == config.getNavNextSlot()) {
            if (page < getMaxPage()) {
                page = rightClick ? getMaxPage() : page + 1;
                playSound(config.getClickSound());
                refresh();
            }
        }
    }

    private int getMaxPage() {
        int slotsForTags = tagRows * 9;
        if (slotsForTags == 0) return 0;
        return Math.max(0, (int) Math.ceil(filteredTags.size() / (double) slotsForTags) - 1);
    }

    private void playSound(String sound) {
        if (sound == null || sound.isEmpty()) return;
        try {
            player.playSound(Sound.sound(Key.key(sound), Sound.Source.MASTER, 1.0f, 1.0f));
        } catch (Exception ignored) {
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void track(UUID uuid, TagsGUI gui) {
        openGUIs.put(uuid, gui);
    }

    public static void untrack(UUID uuid) {
        openGUIs.remove(uuid);
    }

    public static TagsGUI getOpenGUI(UUID uuid) {
        return openGUIs.get(uuid);
    }

    public static void closeAll() {
        for (Map.Entry<UUID, TagsGUI> entry : openGUIs.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.closeInventory();
            }
        }
        openGUIs.clear();
    }
}
