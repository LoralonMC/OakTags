package dev.oakheart.oaktags.integration;

import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

/**
 * Manages model provider resolution for tag GUI icons.
 * Supports Nexo, ItemsAdder, vanilla CustomModelData, and modern Item Model providers.
 *
 * <p>Model ID format in config:
 * <ul>
 *   <li>{@code nexo:<id>} — Nexo item</li>
 *   <li>{@code itemsadder:<id>} — ItemsAdder item</li>
 *   <li>{@code model:<namespace:key>} — Modern Item Model (1.21.4+)</li>
 *   <li>{@code 1001} (integer) — Vanilla CustomModelData</li>
 * </ul>
 */
public class ModelProviderManager {

    private final Logger logger;

    public ModelProviderManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Apply a model to an item stack using the appropriate provider.
     *
     * @param item    the item stack to apply the model to
     * @param modelId the model ID string (e.g. "nexo:md_hedgehog_head", "model:oaktags:star", "1001")
     * @return true if the model was applied successfully
     */
    public boolean applyModelById(ItemStack item, String modelId) {
        if (modelId == null || modelId.isEmpty()) return false;
        return applyModelString(item, modelId);
    }

    private boolean applyModelString(ItemStack item, String modelId) {
        // Check if it's a plain integer (vanilla CustomModelData)
        try {
            int customModelData = Integer.parseInt(modelId);
            VanillaProvider vanillaProvider = new VanillaProvider(customModelData);
            return vanillaProvider.applyModel(item, "");
        } catch (NumberFormatException ignored) {
            // Not an integer, check for provider prefix
        }

        ModelProvider provider;
        String actualModelId = modelId;

        if (modelId.toLowerCase().startsWith("model:")) {
            provider = new ItemModelProvider();
            actualModelId = modelId.substring(6);

            if (!provider.isAvailable()) {
                logger.warning("Item Model provider requested but not available (requires Paper 1.21.4+)");
                logger.warning("Falling back to no custom model. Consider using CustomModelData instead.");
                return false;
            }
        } else if (modelId.toLowerCase().startsWith("nexo:")) {
            actualModelId = modelId.substring(5);

            provider = new NexoProvider(logger);
            if (!provider.isAvailable()) {
                logger.warning("Nexo provider requested but Nexo plugin is not available");
                return false;
            }
        } else if (modelId.toLowerCase().startsWith("itemsadder:")) {
            actualModelId = modelId.substring(11);

            provider = new ItemsAdderProvider(logger);
            if (!provider.isAvailable()) {
                logger.warning("ItemsAdder provider requested but ItemsAdder plugin is not available");
                return false;
            }
        } else {
            logger.warning("Unrecognized model-id format '" + modelId + "'");
            logger.warning("Use an integer for vanilla CustomModelData, 'model:namespace:key' for Item Model, 'nexo:id' for Nexo, or 'itemsadder:id' for ItemsAdder");
            return false;
        }

        boolean success = provider.applyModel(item, actualModelId);

        if (!success) {
            logger.warning("Failed to apply " + provider.getName() + " model '" + actualModelId + "'");
            logger.warning("Item will be created without custom model. Check your " + provider.getName() + " configuration.");
        }

        return success;
    }

    /**
     * Get the provider name for a model ID string (for logging/diagnostics).
     *
     * @param modelId the model ID string
     * @return the provider name
     */
    public String getProviderName(String modelId) {
        try {
            Integer.parseInt(modelId);
            return "Vanilla CustomModelData";
        } catch (NumberFormatException ignored) {
        }

        if (modelId.toLowerCase().startsWith("model:")) {
            return "Item Model";
        } else if (modelId.toLowerCase().startsWith("nexo:")) {
            return "Nexo";
        } else if (modelId.toLowerCase().startsWith("itemsadder:")) {
            return "ItemsAdder";
        } else {
            return "Unknown";
        }
    }
}
