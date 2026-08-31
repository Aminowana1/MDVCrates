package com.mdvcraft.mdvcrates.editor;

import com.mdvcraft.mdvcrates.model.Reward;
import org.bukkit.inventory.Inventory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class EditorSession {
    private final String crateId;
    private final Inventory inventory;
    private final Map<Integer, Reward> itemRewards = new LinkedHashMap<>();
    private final Set<Integer> commandSlots = new HashSet<>();
    private boolean saved;

    public EditorSession(String crateId, Inventory inventory) {
        this.crateId = crateId;
        this.inventory = inventory;
    }

    public String crateId() { return crateId; }
    public Inventory inventory() { return inventory; }
    public Map<Integer, Reward> itemRewards() { return itemRewards; }
    public Set<Integer> commandSlots() { return commandSlots; }
    public boolean saved() { return saved; }
    public void saved(boolean saved) { this.saved = saved; }
}
