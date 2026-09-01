package com.mdvcraft.mdvcrates.editor;

import com.mdvcraft.mdvcrates.model.Reward;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public final class EditorSession {
    private final String crateId;
    private final Inventory inventory;
    private final List<Reward> itemRewards;
    private final List<Reward> commandRewards;
    private int page;
    private boolean saved;

    public EditorSession(String crateId, Inventory inventory, List<Reward> itemRewards, List<Reward> commandRewards) {
        this.crateId = crateId;
        this.inventory = inventory;
        this.itemRewards = new ArrayList<>(itemRewards);
        this.commandRewards = new ArrayList<>(commandRewards);
    }

    public String crateId() { return crateId; }
    public Inventory inventory() { return inventory; }
    public List<Reward> itemRewards() { return itemRewards; }
    public List<Reward> commandRewards() { return commandRewards; }
    public int page() { return page; }
    public void page(int page) { this.page = Math.max(0, page); }
    public boolean saved() { return saved; }
    public void saved(boolean saved) { this.saved = saved; }
}
