package com.mdvcraft.mdvcrates.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record PendingReward(
        String entryId,
        String crateId,
        RewardType type,
        String name,
        int amount,
        String mmoItemsType,
        String mmoItemsId,
        ItemStack item,
        List<String> commands,
        int reservedSlot
) {
    public PendingReward {
        item = item == null ? null : item.clone();
        commands = commands == null ? List.of() : List.copyOf(new ArrayList<>(commands));
    }

    @Override
    public ItemStack item() {
        return item == null ? null : item.clone();
    }
}
