package com.mdvcraft.mdvcrates.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Reward {
    private final String id;
    private final RewardType type;
    private final double weight;
    private final Double chance;
    private final int amount;
    private final String displayName;
    private final String mmoItemsType;
    private final String mmoItemsId;
    private final Material vanillaMaterial;
    private final ItemStack storedItem;
    private final List<String> commands;
    private final ItemStack commandPreview;

    private Reward(Builder b) {
        this.id = b.id;
        this.type = b.type;
        this.weight = Math.max(0, b.weight);
        this.chance = b.chance == null ? null : Math.max(0, Math.min(100, b.chance));
        this.amount = Math.max(1, b.amount);
        this.displayName = b.displayName;
        this.mmoItemsType = b.mmoItemsType;
        this.mmoItemsId = b.mmoItemsId;
        this.vanillaMaterial = b.vanillaMaterial;
        this.storedItem = b.storedItem == null ? null : b.storedItem.clone();
        this.commands = Collections.unmodifiableList(new ArrayList<>(b.commands));
        this.commandPreview = b.commandPreview == null ? null : b.commandPreview.clone();
    }

    public String id() { return id; }
    public RewardType type() { return type; }
    public double weight() { return weight; }
    public Double chance() { return chance; }
    public boolean hasExplicitChance() { return chance != null; }
    public int amount() { return amount; }
    public String displayName() { return displayName; }
    public String mmoItemsType() { return mmoItemsType; }
    public String mmoItemsId() { return mmoItemsId; }
    public Material vanillaMaterial() { return vanillaMaterial; }
    public ItemStack storedItem() { return storedItem == null ? null : storedItem.clone(); }
    public List<String> commands() { return commands; }
    public ItemStack commandPreview() { return commandPreview == null ? null : commandPreview.clone(); }

    public Builder toBuilder() {
        return builder(id, type)
                .weight(weight)
                .chance(chance)
                .amount(amount)
                .displayName(displayName)
                .mmoItems(mmoItemsType, mmoItemsId)
                .vanillaMaterial(vanillaMaterial)
                .storedItem(storedItem)
                .commands(commands)
                .commandPreview(commandPreview);
    }

    public static Builder builder(String id, RewardType type) {
        return new Builder(id, type);
    }

    public static final class Builder {
        private final String id;
        private final RewardType type;
        private double weight = 1.0;
        private Double chance;
        private int amount = 1;
        private String displayName;
        private String mmoItemsType;
        private String mmoItemsId;
        private Material vanillaMaterial;
        private ItemStack storedItem;
        private List<String> commands = new ArrayList<>();
        private ItemStack commandPreview;

        private Builder(String id, RewardType type) {
            this.id = id;
            this.type = type;
        }

        public Builder weight(double weight) { this.weight = weight; return this; }
        public Builder chance(Double chance) { this.chance = chance; return this; }
        public Builder amount(int amount) { this.amount = amount; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder mmoItems(String type, String id) { this.mmoItemsType = type; this.mmoItemsId = id; return this; }
        public Builder vanillaMaterial(Material material) { this.vanillaMaterial = material; return this; }
        public Builder storedItem(ItemStack item) { this.storedItem = item == null ? null : item.clone(); return this; }
        public Builder commands(List<String> commands) { this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands); return this; }
        public Builder commandPreview(ItemStack item) { this.commandPreview = item == null ? null : item.clone(); return this; }
        public Reward build() { return new Reward(this); }
    }
}
