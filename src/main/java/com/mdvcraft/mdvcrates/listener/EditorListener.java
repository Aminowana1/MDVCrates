package com.mdvcraft.mdvcrates.listener;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.editor.CrateEditorHolder;
import com.mdvcraft.mdvcrates.editor.EditorSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class EditorListener implements Listener {
    private final MDVCratesPlugin plugin;

    public EditorListener(MDVCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CrateEditorHolder)) return;
        EditorSession session = plugin.editorManager().session(player);
        if (session == null) return;

        int raw = event.getRawSlot();
        int topSize = top.getSize();
        int base = topSize - 9;
        int rewardSlots = Math.min(plugin.getConfig().getInt("editor.reward-slots", 45), topSize - 9);

        if (raw >= 0 && raw < topSize) {
            event.setCancelled(true);
            if (raw == base + 4) {
                plugin.editorManager().save(player);
                player.closeInventory();
                return;
            }
            if (raw == base + 8) {
                player.closeInventory();
                return;
            }
            if (raw < rewardSlots) plugin.editorManager().removeSlot(player, raw);
            return;
        }

        if (event.isShiftClick() && event.getClickedInventory() != null && event.getClickedInventory() != top) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current != null) plugin.editorManager().addFromPlayerInventory(player, current);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof CrateEditorHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof CrateEditorHolder)) return;
        plugin.editorManager().close(player);
    }
}
