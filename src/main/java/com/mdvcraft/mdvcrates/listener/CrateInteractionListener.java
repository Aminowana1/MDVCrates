package com.mdvcraft.mdvcrates.listener;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.model.CrateDefinition;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class CrateInteractionListener implements Listener {
    private final MDVCratesPlugin plugin;

    public CrateInteractionListener(MDVCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        CrateDefinition crate = plugin.crateManager().at(event.getClickedBlock());
        if (crate == null) return;
        event.setCancelled(true);

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        plugin.openingManager().tryOpen(event.getPlayer(), event.getClickedBlock(), crate);
    }
}
