package com.mdvcraft.mdvcrates.service;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.animation.OpeningSession;
import com.mdvcraft.mdvcrates.config.MessageManager;
import com.mdvcraft.mdvcrates.hook.MMOItemsHook;
import com.mdvcraft.mdvcrates.model.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class OpeningManager {
    private final MDVCratesPlugin plugin;
    private final MMOItemsHook mmoItems;
    private final RewardService rewards;
    private final PendingRewardService pending;
    private final MessageManager messages;
    private final Map<BlockKey, OpeningSession> byCrate = new HashMap<>();
    private final Map<UUID, OpeningSession> byPlayer = new HashMap<>();

    public OpeningManager(MDVCratesPlugin plugin, MMOItemsHook mmoItems, RewardService rewards,
                          PendingRewardService pending, MessageManager messages) {
        this.plugin = plugin;
        this.mmoItems = mmoItems;
        this.rewards = rewards;
        this.pending = pending;
        this.messages = messages;
    }

    public boolean isLocked(BlockKey key) {
        return byCrate.containsKey(key);
    }

    public OpeningSession get(Player player) {
        return byPlayer.get(player.getUniqueId());
    }


    public boolean hasCorrectKey(Player player, CrateDefinition crate) {
        if (player == null || crate == null || !crate.key().isConfigured()) return false;
        ItemStack hand = player.getInventory().getItemInMainHand();
        return hand != null && hand.getType() != Material.AIR
                && mmoItems.matches(hand, crate.key().mmoItemsType(), crate.key().mmoItemsId());
    }

    public void tryOpen(Player player, Block block, CrateDefinition crate) {
        if (!player.hasPermission("mdvcrates.use")) {
            messages.send(player, "no-permission");
            return;
        }
        if (!crate.enabled()) {
            messages.send(player, "crate-disabled");
            return;
        }
        BlockKey key = BlockKey.of(block);
        if (block.getType() != crate.blockMaterial() || !plugin.crateManager().isRegisteredLocation(key)) {
            messages.send(player, "crate-disabled");
            return;
        }
        if (byCrate.containsKey(key)) {
            messages.send(player, "crate-busy");
            return;
        }
        if (byPlayer.containsKey(player.getUniqueId())) {
            messages.send(player, "player-busy");
            return;
        }
        if (!hasCorrectKey(player, crate)) {
            messages.send(player, "wrong-key");
            return;
        }
        int reserved = player.getInventory().firstEmpty();
        if (plugin.getConfig().getBoolean("settings.require-free-slot", true) && reserved < 0) {
            messages.send(player, "no-free-slot");
            return;
        }
        Reward winner = rewards.select(crate);
        if (winner == null) {
            messages.send(player, "no-rewards");
            return;
        }
        if (reserved < 0) reserved = Math.max(0, player.getInventory().getHeldItemSlot());

        PendingReward snapshot = rewards.snapshot(crate.id(), winner, reserved);

        // Se persiste el ganador ANTES de consumir la llave. Si el proceso se
        // interrumpe, incluso durante un apagado inesperado posterior, pending.yml
        // conserva qué recompensa pertenece a esta apertura.
        if (!pending.add(player.getUniqueId(), snapshot)) {
            messages.send(player, "pending-save-failed");
            return;
        }
        consumeOneKey(player);

        try {
            OpeningSession session = new OpeningSession(plugin, this, player, block, crate, winner, snapshot, rewards);
            byCrate.put(key, session);
            byPlayer.put(player.getUniqueId(), session);
            messages.send(player, "open-start", Map.of("crate", crate.displayName()));
            broadcastOpening(player, crate);
            session.start();
        } catch (Throwable ex) {
            plugin.getLogger().severe("No se pudo iniciar una apertura: " + ex.getMessage());
            pending.add(player.getUniqueId(), snapshot);
            byCrate.remove(key);
            byPlayer.remove(player.getUniqueId());
        }
    }

    private void consumeOneKey(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else hand.setAmount(hand.getAmount() - 1);
        player.updateInventory();
    }

    public void completed(OpeningSession session) {
        byCrate.remove(session.blockKey(), session);
        byPlayer.remove(session.playerId(), session);
    }

    public void interrupt(Player player, boolean deliverNowIfOnline) {
        OpeningSession session = byPlayer.get(player.getUniqueId());
        if (session == null) return;
        session.interrupt(deliverNowIfOnline);
    }

    public void interruptAll(boolean deliverOnline) {
        for (OpeningSession session : new ArrayList<>(byPlayer.values())) {
            session.interrupt(deliverOnline && session.player() != null && session.player().isOnline());
        }
    }

    public void queue(UUID playerId, PendingReward reward) {
        // add() usa el mismo entryId, por lo que re-persistir es idempotente.
        pending.add(playerId, reward);
    }

    public void completePending(UUID playerId, String entryId) {
        pending.complete(playerId, entryId);
    }

    public PendingRewardService pending() {
        return pending;
    }

    private void broadcastOpening(Player player, CrateDefinition crate) {
        BroadcastDefinition broadcast = BroadcastDefinition.of(crate.broadcastSection());
        if (!broadcast.enabled() || broadcast.text().isEmpty())
            return;

        String message = broadcast.text()
                .replace("{player}", player.getName())
                .replace("{display-name}", crate.displayName());
        
        // Colorize and broadcast to all players
        message = com.mdvcraft.mdvcrates.util.Text.color(message);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.sendMessage(message);
        }

        // Play sound if enabled
        if (broadcast.soundEnabled()) {
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(broadcast.sound().toUpperCase(java.util.Locale.ROOT));
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    online.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                }
            } catch (Exception ignored) {
                // Invalid sound name, skip
            }
        }
    }
}
