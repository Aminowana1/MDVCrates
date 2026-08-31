package com.mdvcraft.mdvcrates.listener;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.model.CrateDefinition;
import com.mdvcraft.mdvcrates.viewer.RewardViewerHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class RewardViewerListener implements Listener {
    private final MDVCratesPlugin plugin;

    public RewardViewerListener(MDVCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof RewardViewerHolder holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        CrateDefinition crate = plugin.crateRepository().get(holder.crateId());
        if (crate == null) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 45 && holder.page() > 0) {
            plugin.rewardViewerManager().open(player, crate, holder.page() - 1);
        } else if (event.getRawSlot() == 53) {
            plugin.rewardViewerManager().open(player, crate, holder.page() + 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RewardViewerHolder) event.setCancelled(true);
    }
}
