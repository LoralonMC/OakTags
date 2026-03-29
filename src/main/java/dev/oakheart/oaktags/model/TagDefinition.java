package dev.oakheart.oaktags.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class TagDefinition {
    private final String id;
    private String display;
    private String category;
    private UnlockType unlockType;
    private String unlockPermission;
    private boolean hidden;
    private List<String> lore;
    private Material material;
    private String modelId;
    private int sortOrder;
    private VoucherConfig voucherConfig;

    public TagDefinition(String id, String display, String category, UnlockType unlockType,
                         String unlockPermission, boolean hidden, List<String> lore,
                         Material material, String modelId, int sortOrder,
                         VoucherConfig voucherConfig) {
        this.id = id;
        this.display = display;
        this.category = category;
        this.unlockType = unlockType;
        this.unlockPermission = unlockPermission;
        this.hidden = hidden;
        this.lore = lore;
        this.material = material;
        this.modelId = modelId;
        this.sortOrder = sortOrder;
        this.voucherConfig = voucherConfig;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public UnlockType getUnlockType() {
        return unlockType;
    }

    public void setUnlockType(UnlockType unlockType) {
        this.unlockType = unlockType;
    }

    public String getUnlockPermission() {
        return unlockPermission;
    }

    public void setUnlockPermission(String unlockPermission) {
        this.unlockPermission = unlockPermission;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = lore;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public VoucherConfig getVoucherConfig() {
        return voucherConfig;
    }

    public void setVoucherConfig(VoucherConfig voucherConfig) {
        this.voucherConfig = voucherConfig;
    }

    public TagDefinition copy() {
        return new TagDefinition(
                id, display, category, unlockType, unlockPermission,
                hidden, new ArrayList<>(lore), material, modelId, sortOrder,
                voucherConfig != null ? voucherConfig.copy() : null
        );
    }
}
