package com.mdvcraft.mdvcrates.service;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.model.PendingReward;
import com.mdvcraft.mdvcrates.model.RewardType;
import com.mdvcraft.mdvcrates.util.ItemStackCodec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public final class PendingRewardService {
    private final MDVCratesPlugin plugin;
    private final RewardService rewards;
    private final File file;
    private YamlConfiguration yaml;

    public PendingRewardService(MDVCratesPlugin plugin, RewardService rewards) {
        this.plugin = plugin;
        this.rewards = rewards;
        this.file = new File(plugin.getDataFolder(), "pending.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized boolean add(UUID playerId, PendingReward reward) {
        String p = "players." + playerId + "." + reward.entryId();
        yaml.set(p + ".crate", reward.crateId());
        yaml.set(p + ".type", reward.type().name());
        yaml.set(p + ".name", reward.name());
        yaml.set(p + ".amount", reward.amount());
        yaml.set(p + ".reserved-slot", reward.reservedSlot());
        yaml.set(p + ".mmoitems-type", reward.mmoItemsType());
        yaml.set(p + ".mmoitems-id", reward.mmoItemsId());
        yaml.set(p + ".commands", reward.commands());
        yaml.set(p + ".item", reward.item() == null ? null : ItemStackCodec.encode(reward.item()));
        if (save()) return true;

        // Si el disco falla, no dejamos el pendiente solamente en memoria:
        // la apertura se abortará y la llave seguirá intacta.
        remove(playerId, reward.entryId(), false);
        return false;
    }

    public synchronized int deliverAll(Player player) {
        List<PendingReward> pending = get(player.getUniqueId());
        int delivered = 0;
        for (PendingReward reward : pending) {
            if (rewards.deliver(player, reward)) {
                remove(player.getUniqueId(), reward.entryId(), false);
                delivered++;
            }
        }
        if (delivered > 0) save();
        return delivered;
    }

    public synchronized List<PendingReward> get(UUID playerId) {
        ConfigurationSection sec = yaml.getConfigurationSection("players." + playerId);
        if (sec == null) return List.of();
        List<PendingReward> out = new ArrayList<>();
        for (String entry : sec.getKeys(false)) {
            ConfigurationSection rs = sec.getConfigurationSection(entry);
            if (rs == null) continue;
            try {
                RewardType type = RewardType.valueOf(rs.getString("type", "ITEM"));
                out.add(new PendingReward(
                        entry,
                        rs.getString("crate", "unknown"),
                        type,
                        rs.getString("name", "Recompensa"),
                        rs.getInt("amount", 1),
                        rs.getString("mmoitems-type"),
                        rs.getString("mmoitems-id"),
                        ItemStackCodec.decode(rs.getString("item")),
                        rs.getStringList("commands"),
                        rs.getInt("reserved-slot", -1)
                ));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public synchronized void complete(UUID playerId, String entryId) {
        remove(playerId, entryId, true);
    }

    private void remove(UUID playerId, String entryId, boolean saveNow) {
        yaml.set("players." + playerId + "." + entryId, null);
        ConfigurationSection parent = yaml.getConfigurationSection("players." + playerId);
        if (parent != null && parent.getKeys(false).isEmpty()) yaml.set("players." + playerId, null);
        if (saveNow) save();
    }

    public synchronized boolean save() {
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar pending.yml", ex);
            return false;
        }
    }
}
