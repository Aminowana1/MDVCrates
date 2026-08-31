package com.mdvcraft.mdvcrates.listener;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PlayerSafetyListener implements Listener {
    private final MDVCratesPlugin plugin;

    public PlayerSafetyListener(MDVCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.openingManager().interrupt(event.getPlayer(), false);
        plugin.editorManager().discard(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        plugin.openingManager().interrupt(event.getPlayer(), false);
        plugin.editorManager().discard(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.openingManager().interrupt(event.getEntity(), false);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.openingManager().get(event.getPlayer()) == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.openingManager().interrupt(event.getPlayer(), true));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleDelivery(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleDelivery(event.getPlayer());
    }

    private void scheduleDelivery(Player player) {
        long delay = Math.max(1L, plugin.getConfig().getLong("settings.deliver-pending-on-join-delay-ticks", 20));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            int delivered = plugin.pendingRewardService().deliverAll(player);
            if (delivered > 0) plugin.messages().send(player, "pending-delivered");
        }, delay);
    }
}
