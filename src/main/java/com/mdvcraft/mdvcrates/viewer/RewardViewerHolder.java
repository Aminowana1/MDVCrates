package com.mdvcraft.mdvcrates.viewer;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class RewardViewerHolder implements InventoryHolder {
    private final String crateId;
    private final int page;
    private Inventory inventory;

    public RewardViewerHolder(String crateId, int page) {
        this.crateId = crateId;
        this.page = page;
    }

    public String crateId() { return crateId; }
    public int page() { return page; }
    public void bind(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
