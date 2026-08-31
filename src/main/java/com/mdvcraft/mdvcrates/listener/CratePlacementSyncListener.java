package com.mdvcraft.mdvcrates.listener;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Sincroniza crates colocadas cuando vuelve a cargar su chunk. Así un reload
 * nunca necesita forzar chunks ni pedir al admin que vuelva a colocar cajas.
 */
public final class CratePlacementSyncListener implements Listener {
    private final MDVCratesPlugin plugin;

    public CratePlacementSyncListener(MDVCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.crateManager().refreshChunk(event.getChunk());
    }
}
