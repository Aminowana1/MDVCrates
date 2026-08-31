package com.mdvcraft.mdvcrates.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CrateEditorHolder implements InventoryHolder {
    private final String crateId;
    private Inventory inventory;

    public CrateEditorHolder(String crateId) {
        this.crateId = crateId;
    }

    public String crateId() { return crateId; }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
