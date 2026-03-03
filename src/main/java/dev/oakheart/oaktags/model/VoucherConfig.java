package dev.oakheart.oaktags.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class VoucherConfig {
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final boolean glow;

    public VoucherConfig(Material material, String name, List<String> lore, boolean glow) {
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.glow = glow;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isGlow() {
        return glow;
    }

    public VoucherConfig withMaterial(Material material) {
        return new VoucherConfig(material, this.name, this.lore, this.glow);
    }

    public VoucherConfig withName(String name) {
        return new VoucherConfig(this.material, name, this.lore, this.glow);
    }

    public VoucherConfig withLore(List<String> lore) {
        return new VoucherConfig(this.material, this.name, lore, this.glow);
    }

    public VoucherConfig withGlow(boolean glow) {
        return new VoucherConfig(this.material, this.name, this.lore, glow);
    }

    public VoucherConfig copy() {
        return new VoucherConfig(material, name, new ArrayList<>(lore), glow);
    }
}
