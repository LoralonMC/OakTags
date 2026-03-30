package dev.oakheart.oaktags;

import dev.oakheart.oaktags.api.OakTagsAPI;
import dev.oakheart.oaktags.commands.TagsCommand;
import dev.oakheart.oaktags.config.ConfigManager;
import dev.oakheart.oaktags.data.DataStore;
import dev.oakheart.oaktags.data.SQLiteDataStore;
import dev.oakheart.oaktags.gui.AdminGUI;
import dev.oakheart.oaktags.gui.ConfirmGUI;
import dev.oakheart.oaktags.gui.TagEditorGUI;
import dev.oakheart.oaktags.gui.TagsGUI;
import dev.oakheart.oaktags.listeners.ChatInputListener;
import dev.oakheart.oaktags.listeners.PlayerListener;
import dev.oakheart.oaktags.listeners.VoucherListener;
import dev.oakheart.oaktags.managers.TagManager;
import dev.oakheart.oaktags.placeholder.TagsExpansion;
import dev.oakheart.oaktags.util.TagsYamlWriter;
import dev.oakheart.message.MessageManager;
import dev.oakheart.util.DebugLogger;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class OakTags extends JavaPlugin {
    public static final String VOUCHER_KEY_ID = "tag_voucher_id";

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DebugLogger debugLogger;
    private DataStore dataStore;
    private TagManager tagManager;
    private ChatInputListener chatInputListener;
    private TagsYamlWriter tagsYamlWriter;

    @Override
    public void onEnable() {
        try {
            initializeComponents();
            registerListeners();
            registerCommands();
            initializeMetrics();
            registerPlaceholders();
            loadOnlinePlayers();
            getLogger().info("OakTags v" + getPluginMeta().getVersion() + " has been enabled!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable OakTags", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        OakTagsAPI.unregister();
        TagEditorGUI.closeAll();
        AdminGUI.closeAll();
        TagsGUI.closeAll();
        ConfirmGUI.closeAll();
        if (tagManager != null) tagManager.shutdown();
        if (dataStore != null) dataStore.close();
        getLogger().info("OakTags has been disabled!");
    }

    private void initializeComponents() {
        configManager = new ConfigManager(this);
        configManager.load();

        debugLogger = new DebugLogger(getLogger(), configManager::isDebugMode);

        messageManager = new MessageManager(this, getLogger());
        messageManager.load();

        dataStore = new SQLiteDataStore(getLogger(), getDataFolder());
        dataStore.initialize();
        if (!dataStore.isOperational()) {
            throw new IllegalStateException("Database failed to initialize");
        }

        tagManager = new TagManager(this, configManager, dataStore);
        tagManager.initialize();

        new OakTagsAPI(tagManager);

        tagsYamlWriter = new TagsYamlWriter(configManager.getTagsFile(), getLogger());
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new dev.oakheart.oaktags.gui.GUIListener(), this);
        getServer().getPluginManager().registerEvents(
                new VoucherListener(this, tagManager, messageManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerListener(this, tagManager), this);
        chatInputListener = new ChatInputListener(this);
        getServer().getPluginManager().registerEvents(chatInputListener, this);
    }

    private void registerCommands() {
        new TagsCommand(this, configManager, tagManager, messageManager,
                chatInputListener, tagsYamlWriter).register();
    }

    private void initializeMetrics() {
        new Metrics(this, 29805);
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TagsExpansion(tagManager, configManager, getPluginMeta().getVersion()).register();
            getLogger().info("PlaceholderAPI integration enabled.");
        } else {
            getLogger().info("PlaceholderAPI not found. Placeholders will not be available.");
        }

        if (Bukkit.getPluginManager().getPlugin("Nexo") != null) {
            getLogger().info("Nexo detected. Nexo model provider available for tag icons.");
        }
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            getLogger().info("ItemsAdder detected. ItemsAdder model provider available for tag icons.");
        }
    }

    private void loadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                tagManager.loadPlayer(player.getUniqueId());
            });
        }
    }

    public void reloadPluginConfig() {
        TagEditorGUI.closeAll();
        AdminGUI.closeAll();
        TagsGUI.closeAll();
        ConfirmGUI.closeAll();

        if (!configManager.reload()) {
            throw new RuntimeException("Config reload failed validation");
        }

        messageManager.reload();
        tagManager.reload();

        getLogger().info("OakTags configuration reloaded.");
    }

    public void debug(String message) {
        if (debugLogger != null) {
            debugLogger.log(message);
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public TagManager getTagManager() {
        return tagManager;
    }

    public ChatInputListener getChatInputListener() {
        return chatInputListener;
    }

    public TagsYamlWriter getTagsYamlWriter() {
        return tagsYamlWriter;
    }
}
