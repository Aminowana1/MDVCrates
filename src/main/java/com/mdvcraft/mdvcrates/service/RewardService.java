package com.mdvcraft.mdvcrates.service;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.hook.MMOItemsHook;
import com.mdvcraft.mdvcrates.model.*;
import com.mdvcraft.mdvcrates.util.PlaceholderUtil;
import com.mdvcraft.mdvcrates.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RewardService {
    private final MDVCratesPlugin plugin;
    private final MMOItemsHook mmoItems;

    public RewardService(MDVCratesPlugin plugin, MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.mmoItems = mmoItems;
    }

    public List<Reward> validRewards(CrateDefinition crate) {
        List<Reward> valid = new ArrayList<>();
        for (Reward reward : crate.rewards()) {
            if (reward.weight() <= 0) continue;
            if (reward.type() == RewardType.COMMAND) {
                if (!reward.commands().isEmpty()) valid.add(reward);
            } else if (preview(reward) != null) {
                valid.add(reward);
            }
        }
        return valid;
    }

    public Reward select(CrateDefinition crate) {
        List<Reward> valid = validRewards(crate);
        double total = valid.stream().mapToDouble(Reward::weight).sum();
        if (valid.isEmpty() || total <= 0) return null;
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0;
        for (Reward reward : valid) {
            cursor += reward.weight();
            if (roll < cursor) return reward;
        }
        return valid.get(valid.size() - 1);
    }

    public Reward randomVisual(CrateDefinition crate) {
        List<Reward> valid = validRewards(crate);
        if (valid.isEmpty()) return null;
        return valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
    }

    public ItemStack preview(Reward reward) {
        if (reward == null) return null;
        return switch (reward.type()) {
            case MMOITEM -> mmoItems.getItem(reward.mmoItemsType(), reward.mmoItemsId(), Math.min(reward.amount(), 64));
            case ITEM -> {
                ItemStack item = reward.storedItem();
                if (item != null) item.setAmount(Math.min(reward.amount(), Math.max(1, item.getMaxStackSize())));
                yield item;
            }
            case COMMAND -> reward.commandPreview();
        };
    }

    public String displayName(Reward reward) {
        if (reward.displayName() != null && !reward.displayName().isBlank()) return Text.color(reward.displayName());
        ItemStack preview = preview(reward);
        return preview == null ? reward.id() : Text.itemName(preview);
    }

    public PendingReward snapshot(String crateId, Reward reward, int reservedSlot) {
        ItemStack item = reward.type() == RewardType.ITEM ? reward.storedItem() : null;
        return new PendingReward(
                UUID.randomUUID().toString(), crateId, reward.type(), displayName(reward), reward.amount(),
                reward.mmoItemsType(), reward.mmoItemsId(), item, reward.commands(), reservedSlot);
    }

    public boolean deliver(Player player, PendingReward reward) {
        if (reward.type() == RewardType.COMMAND) {
            for (String command : reward.commands()) {
                String parsed = PlaceholderUtil.apply(player, command, Map.of("crate", reward.crateId()));
                if (parsed.startsWith("/")) parsed = parsed.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
            return true;
        }

        ItemStack base;
        if (reward.type() == RewardType.MMOITEM) {
            base = mmoItems.getItem(reward.mmoItemsType(), reward.mmoItemsId(), 1);
        } else {
            base = reward.item();
            if (base != null) base.setAmount(1);
        }
        if (base == null || base.getType() == Material.AIR) return false;

        int remaining = Math.max(1, reward.amount());
        int reserved = reward.reservedSlot();
        int max = Math.max(1, base.getMaxStackSize());

        if (reserved >= 0 && reserved < player.getInventory().getStorageContents().length) {
            ItemStack occupant = player.getInventory().getItem(reserved);
            if (occupant != null && !occupant.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), occupant.clone());
            }
            int firstAmount = Math.min(max, remaining);
            ItemStack first = base.clone();
            first.setAmount(firstAmount);
            player.getInventory().setItem(reserved, first);
            remaining -= firstAmount;
        }

        while (remaining > 0) {
            int amount = Math.min(max, remaining);
            ItemStack part = base.clone();
            part.setAmount(amount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(part);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= amount;
        }
        return true;
    }
}
