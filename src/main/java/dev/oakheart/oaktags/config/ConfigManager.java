package dev.oakheart.oaktags.config;

import dev.oakheart.oaktags.OakTags;
import dev.oakheart.oaktags.model.TagDefinition;
import dev.oakheart.oaktags.model.UnlockType;
import dev.oakheart.oaktags.model.VoucherConfig;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigManager {
    private final OakTags plugin;
    private final Logger logger;
    private final File configFile;
    private final File tagsFile;
    private dev.oakheart.config.ConfigManager config;
    private dev.oakheart.config.ConfigManager tagsConfig;

    // General
    private boolean debugMode;
    private int batchWriteInterval;

    // GUI
    private String guiTitle;
    private int maxTagRows;
    private String openSound;
    private String equipSound;
    private String lockedSound;
    private String clickSound;

    // Nav bar slots
    private int navPrevSlot;
    private int navSortSlot;
    private int navReverseSlot;
    private int navPageIndicatorSlot;
    private int navClearSlot;
    private int navFilterSlot;
    private int navNextSlot;

    // Sort/filter display names
    private Map<String, String> sortModeNames;
    private Map<String, String> filterModeNames;

    // Tag display
    private String tagDisplayFormat;

    // GUI items
    private GuiItemConfig fillerItem;
    private Material lockedTagMaterial;
    private GuiItemConfig prevPageItem;
    private GuiItemConfig nextPageItem;
    private GuiItemConfig sortItem;
    private GuiItemConfig clearItem;
    private GuiItemConfig pageIndicatorItem;

    // Reverse button (two material states)
    private Material reverseAscMaterial;
    private Material reverseDescMaterial;
    private String reverseName;
    private String reverseAscLabel;
    private String reverseDescLabel;
    private List<String> reverseLore;

    // Filter button (material states)
    private Material filterAllMaterial;
    private Material filterFavoritesMaterial;
    private Material filterUnlockedMaterial;
    private Material filterLockedMaterial;
    private String filterName;
    private List<String> filterLore;

    // Tag lore (layout-based)
    private List<String> tagLoreLayout;
    private String tagLoreStatusActive;
    private String tagLoreStatusUnlocked;
    private String tagLoreStatusLocked;
    private String tagLoreClickEquip;
    private String tagLoreClickUnequip;
    private String tagLoreRightClickFavorite;
    private String tagLoreRightClickUnfavorite;
    private String tagLoreFavorite;

    // Default voucher
    private Material defaultVoucherMaterial;
    private String defaultVoucherName;
    private List<String> defaultVoucherLore;
    private boolean defaultVoucherGlow;

    // Tag defaults (for /tags create)
    private String defaultTagDisplay;
    private Material defaultTagMaterial;
    private String defaultTagCategory;
    private List<String> defaultTagLore;

    // Chat prefix
    private String prefixFallback;
    private String prefixStackPermission;

    // Confirm GUI
    private String confirmGuiTitle;
    private int confirmGuiSize;
    private GuiItemConfig confirmFillerItem;
    private GuiItemConfig confirmItem;
    private int confirmSlot;
    private GuiItemConfig denyItem;
    private int denySlot;
    private int tagInfoSlot;

    // Admin GUI
    private String adminGuiTitle;
    private List<String> adminTagLoreLayout;
    private String adminTagLoreStatusUnlocked;
    private String adminTagLoreStatusLocked;
    private String adminTagLoreClickGrant;
    private String adminTagLoreClickRevoke;
    private String adminTagLorePermissionUnlocked;
    private String adminTagLorePermissionLocked;
    private String adminTagLorePermissionOffline;

    // Categories
    private LinkedHashMap<String, TagDefinition> tagDefinitions;
    private LinkedHashMap<String, CategoryConfig> categories;

    // Cached derived values
    private List<String> categoryKeysList;
    private Map<String, String> categoryDisplayNamesMap;
    private VoucherConfig defaultVoucherConfigObj;

    public ConfigManager(OakTags plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.tagsFile = new File(plugin.getDataFolder(), "tags.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        if (!tagsFile.exists()) {
            plugin.saveResource("tags.yml", false);
        }

        try {
            config = dev.oakheart.config.ConfigManager.load(configFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }

        mergeDefaults();
        validate(config);
        cacheValues();
        loadTags();
    }

    public boolean reload() {
        try {
            config.reload();
        } catch (IOException e) {
            logger.warning("Failed to reload config.yml: " + e.getMessage());
            return false;
        }

        if (!validate(config)) {
            logger.warning("Configuration reload failed validation. Keeping previous configuration.");
            return false;
        }

        cacheValues();
        loadTags();
        logger.info("Configuration reloaded successfully.");
        return true;
    }

    private void mergeDefaults() {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream != null) {
                var defaults = dev.oakheart.config.ConfigManager.fromStream(stream);
                if (config.mergeDefaults(defaults)) {
                    config.save();
                    logger.info("Merged missing config keys from defaults.");
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to merge default config", e);
        }
    }

    private boolean validate(dev.oakheart.config.ConfigManager configToValidate) {
        List<String> warnings = new ArrayList<>();

        if (configToValidate.getInt("batch-write-interval", 300) < 10) {
            warnings.add("batch-write-interval is very low (< 10s), may cause performance issues");
        }

        if (!configToValidate.isSection("categories") ||
                configToValidate.getKeys("categories", false).isEmpty()) {
            warnings.add("No categories defined. Tags will have no category grouping.");
        }

        if (!warnings.isEmpty()) {
            logger.warning("=== Configuration Warnings ===");
            warnings.forEach(w -> logger.warning("  - " + w));
            logger.warning("==============================");
        }
        return true;
    }

    private void cacheValues() {
        // General
        debugMode = config.getBoolean("debug", false);
        batchWriteInterval = Math.max(10, config.getInt("batch-write-interval", 300));

        // GUI
        guiTitle = config.getString("gui.title", "<#6C757D>Chat Tags");
        maxTagRows = Math.max(1, Math.min(5, config.getInt("gui.max-tag-rows", 5)));
        openSound = config.getString("gui.open-sound", "minecraft:block.chest.open");
        equipSound = config.getString("gui.equip-sound", "minecraft:entity.player.levelup");
        lockedSound = config.getString("gui.locked-sound", "minecraft:block.note_block.bass");
        clickSound = config.getString("gui.click-sound", "minecraft:ui.button.click");

        // Nav bar slots
        navPrevSlot = config.getInt("gui.items.previous-page.slot", 0);
        navSortSlot = config.getInt("gui.items.sort.slot", 1);
        navReverseSlot = config.getInt("gui.items.reverse.slot", 2);
        navPageIndicatorSlot = config.getInt("gui.items.page-indicator.slot", 3);
        navClearSlot = config.getInt("gui.items.clear.slot", 4);
        navFilterSlot = config.getInt("gui.items.filter.slot", 6);
        navNextSlot = config.getInt("gui.items.next-page.slot", 8);

        // Sort/filter display names
        sortModeNames = new LinkedHashMap<>();
        sortModeNames.put("CATEGORY", config.getString("gui.sort-names.category", "Category"));
        sortModeNames.put("ALPHABETICAL", config.getString("gui.sort-names.alphabetical", "A-Z"));
        sortModeNames.put("NEWEST", config.getString("gui.sort-names.newest", "Newest"));
        sortModeNames.put("UNLOCKED_FIRST", config.getString("gui.sort-names.unlocked-first", "Unlocked First"));
        sortModeNames.put("MOST_CLAIMED", config.getString("gui.sort-names.most-claimed", "Most Claimed"));

        filterModeNames = new LinkedHashMap<>();
        filterModeNames.put("all", config.getString("gui.filter-names.all", "All"));
        filterModeNames.put("favorites", config.getString("gui.filter-names.favorites", "Favorites"));
        filterModeNames.put("unlocked", config.getString("gui.filter-names.unlocked", "Unlocked"));
        filterModeNames.put("locked", config.getString("gui.filter-names.locked", "Locked"));

        // Tag display format
        tagDisplayFormat = config.getString("gui.tag-display-format", "<tag> <#f2ebd7><player>");

        // GUI items
        fillerItem = parseGuiItem("gui.items.filler", Material.GRAY_STAINED_GLASS_PANE, "", List.of());
        lockedTagMaterial = parseMaterial("gui.items.locked-tag.material", Material.RED_STAINED_GLASS_PANE);
        prevPageItem = parseGuiItem("gui.items.previous-page", Material.ARROW, "<#f2ebd7>Previous Page", List.of());
        nextPageItem = parseGuiItem("gui.items.next-page", Material.ARROW, "<#f2ebd7>Next Page", List.of());
        sortItem = parseGuiItem("gui.items.sort", Material.HOPPER, "<#f2ebd7>Sort: <#FCD472><mode>", List.of());
        clearItem = parseGuiItem("gui.items.clear", Material.BARRIER, "<#f2ebd7>Clear Active Tag", List.of());
        pageIndicatorItem = parseGuiItem("gui.items.page-indicator", Material.PAPER,
                "<#6C757D>Page <#FCD472><current><#6C757D>/<#FCD472><total>", List.of());

        // Reverse button
        reverseAscMaterial = parseMaterial("gui.items.reverse.ascending-material", Material.ARROW);
        reverseDescMaterial = parseMaterial("gui.items.reverse.descending-material", Material.SPECTRAL_ARROW);
        reverseName = config.getString("gui.items.reverse.name", "<#f2ebd7>Order: <#FCD472><direction>");
        reverseAscLabel = config.getString("gui.items.reverse.ascending-label", "Ascending");
        reverseDescLabel = config.getString("gui.items.reverse.descending-label", "Descending");
        reverseLore = config.getStringList("gui.items.reverse.lore");

        // Filter button
        filterAllMaterial = parseMaterial("gui.items.filter.all-material", Material.COMPASS);
        filterFavoritesMaterial = parseMaterial("gui.items.filter.favorites-material", Material.GLOW_INK_SAC);
        filterUnlockedMaterial = parseMaterial("gui.items.filter.unlocked-material", Material.LIME_DYE);
        filterLockedMaterial = parseMaterial("gui.items.filter.locked-material", Material.GRAY_DYE);
        filterName = config.getString("gui.items.filter.name", "<#f2ebd7>Filter: <#FCD472><mode>");
        filterLore = config.getStringList("gui.items.filter.lore");

        // Tag lore (layout-based)
        tagLoreLayout = config.getStringList("gui.tag-lore.layout");
        if (tagLoreLayout.isEmpty()) {
            tagLoreLayout = List.of("<status>", "<tag_lore>", "", "<left_click_action>");
        }
        tagLoreStatusActive = config.getString("gui.tag-lore.status.active", "<#8FAA87>Currently equipped!");
        tagLoreStatusUnlocked = config.getString("gui.tag-lore.status.unlocked", "<#8FAA87>Unlocked");
        tagLoreStatusLocked = config.getString("gui.tag-lore.status.locked", "<#C27B6B>Locked");
        tagLoreClickEquip = config.getString("gui.tag-lore.left-click-action.equip", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴇǫᴜɪᴘ]");
        tagLoreClickUnequip = config.getString("gui.tag-lore.left-click-action.unequip", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ᴜɴᴇǫᴜɪᴘ]");
        tagLoreRightClickFavorite = config.getString("gui.tag-lore.right-click-action.favorite", "<#FCD472>[ʀɪɢʜᴛ-ᴄʟɪᴄᴋ ᴛᴏ ꜰᴀᴠᴏʀɪᴛᴇ]");
        tagLoreRightClickUnfavorite = config.getString("gui.tag-lore.right-click-action.unfavorite", "<#FCD472>[ʀɪɢʜᴛ-ᴄʟɪᴄᴋ ᴛᴏ ᴜɴꜰᴀᴠᴏʀɪᴛᴇ]");
        tagLoreFavorite = config.getString("gui.tag-lore.favorite", "<#FCD472>★ Favorite");

        // Voucher defaults
        defaultVoucherMaterial = parseMaterial("voucher.material", Material.NAME_TAG);
        defaultVoucherName = config.getString("voucher.name", "<tag> <#f2ebd7>Chat Tag");
        defaultVoucherLore = config.getStringList("voucher.lore");
        defaultVoucherGlow = config.getBoolean("voucher.glow", true);

        // Tag defaults (for /tags create)
        defaultTagDisplay = config.getString("tag-defaults.display", "<#FCD472>[<id>]");
        defaultTagMaterial = parseMaterial("tag-defaults.material", Material.NAME_TAG);
        defaultTagCategory = config.getString("tag-defaults.category", "general");
        defaultTagLore = config.getStringList("tag-defaults.lore");

        // Chat prefix
        prefixFallback = config.getString("prefix.fallback", "%luckperms_prefix%");
        prefixStackPermission = config.getString("prefix.stack-permission", "oaktags.stack");

        // Confirm GUI
        confirmGuiTitle = config.getString("confirm-gui.title", "<#6C757D>Confirm Redemption");
        int rawSize = config.getInt("confirm-gui.size", 27);
        confirmGuiSize = (rawSize >= 9 && rawSize <= 54 && rawSize % 9 == 0) ? rawSize : 27;
        confirmFillerItem = parseGuiItem("confirm-gui.filler", Material.GRAY_STAINED_GLASS_PANE, "", List.of());
        confirmItem = parseGuiItem("confirm-gui.confirm", Material.LIME_STAINED_GLASS_PANE,
                "<#8FAA87>Confirm", List.of("<#f2ebd7>Click to redeem this tag."));
        confirmSlot = config.getInt("confirm-gui.confirm.slot", 11);
        denyItem = parseGuiItem("confirm-gui.deny", Material.RED_STAINED_GLASS_PANE,
                "<#C27B6B>Cancel", List.of("<#f2ebd7>Click to cancel."));
        denySlot = config.getInt("confirm-gui.deny.slot", 15);
        tagInfoSlot = config.getInt("confirm-gui.tag-info.slot", 13);

        // Admin GUI
        adminGuiTitle = config.getString("admin-gui.title", "<#6C757D>Tags: <#FCD472><player>");
        adminTagLoreLayout = config.getStringList("admin-gui.tag-lore.layout");
        if (adminTagLoreLayout.isEmpty()) {
            adminTagLoreLayout = List.of("<status>", "<#6C757D>ID: <#FCD472><tag_id>", "<tag_lore>", "", "<click_action>");
        }
        adminTagLoreStatusUnlocked = config.getString("admin-gui.tag-lore.status.unlocked", "<#8FAA87>Unlocked");
        adminTagLoreStatusLocked = config.getString("admin-gui.tag-lore.status.locked", "<#C27B6B>Locked");
        adminTagLoreClickGrant = config.getString("admin-gui.tag-lore.click-action.grant", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ɢʀᴀɴᴛ]");
        adminTagLoreClickRevoke = config.getString("admin-gui.tag-lore.click-action.revoke", "<#FCD472>[ᴄʟɪᴄᴋ ᴛᴏ ʀᴇᴠᴏᴋᴇ]");
        adminTagLorePermissionUnlocked = config.getString("admin-gui.tag-lore.click-action.permission-unlocked",
                "<#6C757D>Managed by permissions <#8FAA87>(has access)");
        adminTagLorePermissionLocked = config.getString("admin-gui.tag-lore.click-action.permission-locked",
                "<#6C757D>Managed by permissions <#C27B6B>(no access)");
        adminTagLorePermissionOffline = config.getString("admin-gui.tag-lore.click-action.permission-offline",
                "<#6C757D>Managed by permissions <#D89B6A>(offline)");

        // Categories
        categories = new LinkedHashMap<>();
        if (config.isSection("categories")) {
            dev.oakheart.config.ConfigManager catSection = config.getSection("categories");
            List<Map.Entry<String, CategoryConfig>> catList = new ArrayList<>();
            for (String key : catSection.getKeys(false)) {
                dev.oakheart.config.ConfigManager catEntry = catSection.getSection(key);
                if (catEntry == null) continue;
                String displayName = catEntry.getString("display-name", key);
                int sortOrder = catEntry.getInt("sort-order", 99);
                String matStr = catEntry.getString("material", "NAME_TAG");
                Material mat = Material.matchMaterial(matStr);
                if (mat == null) mat = Material.NAME_TAG;
                catList.add(Map.entry(key, new CategoryConfig(displayName, sortOrder, mat)));
            }
            catList.sort(Comparator.comparingInt(e -> e.getValue().sortOrder()));
            catList.forEach(e -> categories.put(e.getKey(), e.getValue()));
        }

        // Cache derived values
        categoryKeysList = new ArrayList<>(categories.keySet());
        Map<String, String> names = new LinkedHashMap<>();
        categories.forEach((k, v) -> names.put(k, v.displayName()));
        categoryDisplayNamesMap = names;
        defaultVoucherConfigObj = new VoucherConfig(defaultVoucherMaterial, defaultVoucherName,
                defaultVoucherLore, defaultVoucherGlow);
    }

    private GuiItemConfig parseGuiItem(String path, Material defaultMaterial,
                                        String defaultName, List<String> defaultLore) {
        Material material = parseMaterial(path + ".material", defaultMaterial);
        String name = config.getString(path + ".name", defaultName);
        List<String> lore = config.contains(path + ".lore")
                ? config.getStringList(path + ".lore") : defaultLore;
        return new GuiItemConfig(material, name, lore);
    }

    private void loadTags() {
        try {
            tagsConfig = dev.oakheart.config.ConfigManager.load(tagsFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load tags.yml", e);
        }

        tagDefinitions = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int order = 0;

        for (String id : tagsConfig.getKeys(false)) {
            dev.oakheart.config.ConfigManager section = tagsConfig.getSection(id);
            if (section == null) continue;

            String display = section.getString("display");
            if (display == null || display.isEmpty()) {
                warnings.add("Tag '" + id + "' missing display, skipping");
                continue;
            }

            String category = section.getString("category", "general");
            if (!categories.containsKey(category)) {
                warnings.add("Tag '" + id + "' references undefined category '" + category + "'");
            }
            UnlockType unlockType = UnlockType.fromString(section.getString("unlock-type", "granted"));
            String unlockPermission = section.getString("unlock-permission", "tags." + id);
            boolean hidden = section.getBoolean("hidden", false);
            List<String> lore = section.getStringList("lore");
            String matStr = section.getString("material", "NAME_TAG");
            Material material = Material.matchMaterial(matStr);
            if (material == null) material = Material.NAME_TAG;

            // Per-tag voucher config
            VoucherConfig voucherConfig = null;
            if (section.isSection("voucher")) {
                dev.oakheart.config.ConfigManager vSection = section.getSection("voucher");
                String vMat = vSection.getString("material", defaultVoucherMaterial.name());
                Material vMaterial = Material.matchMaterial(vMat);
                if (vMaterial == null) vMaterial = defaultVoucherMaterial;
                String vName = vSection.getString("name", defaultVoucherName);
                List<String> vLore = vSection.contains("lore")
                        ? vSection.getStringList("lore") : defaultVoucherLore;
                boolean vGlow = vSection.getBoolean("glow", defaultVoucherGlow);
                voucherConfig = new VoucherConfig(vMaterial, vName, vLore, vGlow);
            }

            String modelId = section.getString("model-id", null);

            tagDefinitions.put(id, new TagDefinition(
                    id, display, category, unlockType, unlockPermission,
                    hidden, lore, material, modelId, order++, voucherConfig
            ));
        }

        if (!warnings.isEmpty()) {
            logger.warning("=== Tag Loading Warnings ===");
            warnings.forEach(w -> logger.warning("  - " + w));
            logger.warning("============================");
        }
        logger.info("Loaded " + tagDefinitions.size() + " tags from tags.yml.");
    }

    private Material parseMaterial(String path, Material fallback) {
        String str = config.getString(path, fallback.name());
        Material mat = Material.matchMaterial(str);
        if (mat == null) {
            logger.warning("Invalid material '" + str + "' at " + path + ", using " + fallback.name());
            return fallback;
        }
        return mat;
    }

    public void reloadTags() {
        loadTags();
    }

    public File getTagsFile() {
        return tagsFile;
    }

    // General getters
    public boolean isDebugMode() { return debugMode; }
    public int getBatchWriteInterval() { return batchWriteInterval; }

    // GUI getters
    public String getGuiTitle() { return guiTitle; }
    public int getMaxTagRows() { return maxTagRows; }
    public String getOpenSound() { return openSound; }
    public String getEquipSound() { return equipSound; }
    public String getLockedSound() { return lockedSound; }
    public String getClickSound() { return clickSound; }

    // Nav bar slot getters
    public int getNavPrevSlot() { return navPrevSlot; }
    public int getNavSortSlot() { return navSortSlot; }
    public int getNavReverseSlot() { return navReverseSlot; }
    public int getNavPageIndicatorSlot() { return navPageIndicatorSlot; }
    public int getNavClearSlot() { return navClearSlot; }
    public int getNavFilterSlot() { return navFilterSlot; }
    public int getNavNextSlot() { return navNextSlot; }

    // Sort/filter name getters
    public String getSortModeName(String enumName) { return sortModeNames.getOrDefault(enumName, enumName); }
    public String getFilterModeName(String key) { return filterModeNames.getOrDefault(key, key); }

    // Tag display format
    public String getTagDisplayFormat() { return tagDisplayFormat; }

    // GUI item getters
    public GuiItemConfig getFillerItem() { return fillerItem; }
    public Material getLockedTagMaterial() { return lockedTagMaterial; }
    public GuiItemConfig getPrevPageItem() { return prevPageItem; }
    public GuiItemConfig getNextPageItem() { return nextPageItem; }
    public GuiItemConfig getSortItem() { return sortItem; }
    public GuiItemConfig getClearItem() { return clearItem; }
    public GuiItemConfig getPageIndicatorItem() { return pageIndicatorItem; }

    // Reverse button getters
    public Material getReverseAscMaterial() { return reverseAscMaterial; }
    public Material getReverseDescMaterial() { return reverseDescMaterial; }
    public String getReverseName() { return reverseName; }
    public String getReverseAscLabel() { return reverseAscLabel; }
    public String getReverseDescLabel() { return reverseDescLabel; }
    public List<String> getReverseLore() { return reverseLore; }

    // Filter button getters
    public Material getFilterAllMaterial() { return filterAllMaterial; }
    public Material getFilterFavoritesMaterial() { return filterFavoritesMaterial; }
    public Material getFilterUnlockedMaterial() { return filterUnlockedMaterial; }
    public Material getFilterLockedMaterial() { return filterLockedMaterial; }
    public String getFilterName() { return filterName; }
    public List<String> getFilterLore() { return filterLore; }

    // Tag lore getters
    public List<String> getTagLoreLayout() { return tagLoreLayout; }
    public String getTagLoreStatusActive() { return tagLoreStatusActive; }
    public String getTagLoreStatusUnlocked() { return tagLoreStatusUnlocked; }
    public String getTagLoreStatusLocked() { return tagLoreStatusLocked; }
    public String getTagLoreClickEquip() { return tagLoreClickEquip; }
    public String getTagLoreClickUnequip() { return tagLoreClickUnequip; }
    public String getTagLoreRightClickFavorite() { return tagLoreRightClickFavorite; }
    public String getTagLoreRightClickUnfavorite() { return tagLoreRightClickUnfavorite; }
    public String getTagLoreFavorite() { return tagLoreFavorite; }

    // Tag/category getters
    public Map<String, TagDefinition> getTagDefinitions() { return Collections.unmodifiableMap(tagDefinitions); }
    public LinkedHashMap<String, CategoryConfig> getCategories() { return categories; }

    public List<String> getCategoryKeys() {
        return Collections.unmodifiableList(categoryKeysList);
    }

    public Map<String, String> getCategoryDisplayNames() {
        return Collections.unmodifiableMap(categoryDisplayNamesMap);
    }

    public int getCategorySortOrder(String category) {
        CategoryConfig cat = categories.get(category);
        return cat != null ? cat.sortOrder() : 99;
    }

    public Material getCategoryMaterial(String category) {
        CategoryConfig cat = categories.get(category);
        return cat != null ? cat.material() : Material.NAME_TAG;
    }

    // Admin GUI getters
    public String getAdminGuiTitle() { return adminGuiTitle; }
    public List<String> getAdminTagLoreLayout() { return adminTagLoreLayout; }
    public String getAdminTagLoreStatusUnlocked() { return adminTagLoreStatusUnlocked; }
    public String getAdminTagLoreStatusLocked() { return adminTagLoreStatusLocked; }
    public String getAdminTagLoreClickGrant() { return adminTagLoreClickGrant; }
    public String getAdminTagLoreClickRevoke() { return adminTagLoreClickRevoke; }
    public String getAdminTagLorePermissionUnlocked() { return adminTagLorePermissionUnlocked; }
    public String getAdminTagLorePermissionLocked() { return adminTagLorePermissionLocked; }
    public String getAdminTagLorePermissionOffline() { return adminTagLorePermissionOffline; }

    // Voucher getters
    public VoucherConfig getDefaultVoucherConfig() {
        return defaultVoucherConfigObj;
    }

    // Tag default getters (for /tags create)
    public String getDefaultTagDisplay() { return defaultTagDisplay; }
    public Material getDefaultTagMaterial() { return defaultTagMaterial; }
    public String getDefaultTagCategory() { return defaultTagCategory; }
    public List<String> getDefaultTagLore() { return defaultTagLore; }

    // Chat prefix getters
    public String getPrefixFallback() { return prefixFallback; }
    public String getPrefixStackPermission() { return prefixStackPermission; }

    // Confirm GUI getters
    public String getConfirmGuiTitle() { return confirmGuiTitle; }
    public int getConfirmGuiSize() { return confirmGuiSize; }
    public GuiItemConfig getConfirmFillerItem() { return confirmFillerItem; }
    public GuiItemConfig getConfirmItem() { return confirmItem; }
    public int getConfirmSlot() { return confirmSlot; }
    public GuiItemConfig getDenyItem() { return denyItem; }
    public int getDenySlot() { return denySlot; }
    public int getTagInfoSlot() { return tagInfoSlot; }

    public record GuiItemConfig(Material material, String name, List<String> lore) {}
    public record CategoryConfig(String displayName, int sortOrder, Material material) {}
}
