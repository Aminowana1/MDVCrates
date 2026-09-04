package com.mdvcraft.mdvcrates.viewer;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.model.CrateDefinition;
import com.mdvcraft.mdvcrates.model.Reward;
import com.mdvcraft.mdvcrates.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RewardViewerManager {
    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private final MDVCratesPlugin plugin;

    public RewardViewerManager(MDVCratesPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, CrateDefinition crate, int requestedPage) {
        Map<String, Double> probabilities = plugin.rewardService().probabilities(crate);
        List<Reward> rewards = plugin.rewardService().validRewards(crate).stream()
                .filter(r -> probabilities.getOrDefault(r.id(), 0.0) > 0.0000001)
                .toList();
        int maxPage = Math.max(0, (rewards.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(maxPage, requestedPage));
        ConfigurationSection viewer = crate.viewerSection();
        boolean showPercentages = viewer == null || viewer.getBoolean("show-percentages", true);
        String rawTitle = viewer == null ? "&8Recompensas: {crate}" : viewer.getString("title", "&8Recompensas: {crate}");
        String title = Text.color(rawTitle.replace("{crate}", crate.id()).replace("{display-name}", crate.displayName()));

        RewardViewerHolder holder = new RewardViewerHolder(crate.id(), page);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.bind(inv);

        int from = page * PAGE_SIZE;
        int to = Math.min(rewards.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Reward reward = rewards.get(i);
            ItemStack preview = plugin.rewardService().viewerPreview(reward);
            if (preview == null) preview = new ItemStack(Material.BARRIER);
            inv.setItem(i - from, decorate(crate, reward, preview, showPercentages));
        }

        if (page > 0) inv.setItem(45, button(Material.ARROW, "&ePágina anterior", List.of("&7Click para volver.")));
        inv.setItem(49, button(Material.CHEST, crate.displayName(), List.of(
                "&7Recompensas posibles: &f" + rewards.size(),
                "&7Página: &f" + (page + 1) + "/" + (maxPage + 1),
                showPercentages ? "&7Los porcentajes están visibles." : "&7Los porcentajes están ocultos.")));
        if (page < maxPage) inv.setItem(53, button(Material.ARROW, "&ePágina siguiente", List.of("&7Click para avanzar.")));

        player.openInventory(inv);
    }

    private ItemStack decorate(CrateDefinition crate, Reward reward, ItemStack source, boolean showPercentages) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String baseName = reward.displayName() != null && !reward.displayName().isBlank()
                ? Text.color(reward.displayName())
                : Text.itemName(source);
        meta.setDisplayName(reward.amount() > 1
                ? baseName + Text.color(" &ax" + reward.amount())
                : baseName);
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.color("&6&lRecompensa posible"));
        if (reward.amount() > 1) lore.add(Text.color("&7Cantidad: &ax" + reward.amount()));
        if (showPercentages) {
            double chance = plugin.rewardService().probabilityPercent(crate, reward);
            lore.add(Text.color("&7Probabilidad: &e" + String.format(Locale.US, "%.2f", chance) + "%"));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(Text.color(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
