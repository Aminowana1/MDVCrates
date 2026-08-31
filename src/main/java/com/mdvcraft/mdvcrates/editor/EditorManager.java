package com.mdvcraft.mdvcrates.editor;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.hook.MMOItemsHook;
import com.mdvcraft.mdvcrates.model.*;
import com.mdvcraft.mdvcrates.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class EditorManager {
    private final MDVCratesPlugin plugin;
    private final MMOItemsHook mmoItems;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();

    public EditorManager(MDVCratesPlugin plugin, MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.mmoItems = mmoItems;
    }

    public EditorSession session(Player player) { return sessions.get(player.getUniqueId()); }

    public void open(Player player, CrateDefinition crate) {
        int size = normalizeSize(plugin.getConfig().getInt("editor.size", 54));
        int rewardSlots = Math.min(plugin.getConfig().getInt("editor.reward-slots", 45), size - 9);
        CrateEditorHolder holder = new CrateEditorHolder(crate.id());
        Inventory inv = Bukkit.createInventory(holder, size, Text.color("&8MDVCrates: &6" + crate.id()));
        holder.bind(inv);
        EditorSession session = new EditorSession(crate.id(), inv);

        int slot = 0;
        for (Reward reward : crate.rewards()) {
            if (slot >= rewardSlots) break;
            if (reward.type() == RewardType.COMMAND) continue;
            ItemStack preview = plugin.rewardService().preview(reward);
            if (preview == null) continue;
            session.itemRewards().put(slot, reward);
            inv.setItem(slot, decorate(preview, crate, reward, false));
            slot++;
        }

        if (plugin.getConfig().getBoolean("editor.show-command-rewards", true)) {
            for (Reward reward : crate.rewards()) {
                if (slot >= rewardSlots) break;
                if (reward.type() != RewardType.COMMAND) continue;
                ItemStack preview = plugin.rewardService().preview(reward);
                if (preview == null) preview = new ItemStack(Material.NETHER_STAR);
                inv.setItem(slot, decorate(preview, crate, reward, true));
                session.commandSlots().add(slot);
                slot++;
            }
        }

        int base = size - 9;
        inv.setItem(base, button(Material.BOOK, "&e&lEditor de recompensas", List.of(
                "&7SHIFT + click en un objeto de tu inventario",
                "&7para añadir una copia como recompensa.",
                "",
                "&7Click en un premio de ítem para quitarlo.",
                "&7Los COMMAND se editan solo en crates.yml.")));
        inv.setItem(base + 4, button(Material.LIME_CONCRETE, "&a&lGuardar y cerrar", List.of("&7Guarda los ítems del editor.")));
        inv.setItem(base + 8, button(Material.BARRIER, "&c&lCerrar", List.of("&7También guarda automáticamente.")));

        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    public boolean addFromPlayerInventory(Player player, ItemStack original) {
        EditorSession session = session(player);
        if (session == null || original == null || original.getType().isAir()) return false;
        int rewardSlots = Math.min(plugin.getConfig().getInt("editor.reward-slots", 45), session.inventory().getSize() - 9);
        int free = -1;
        for (int i = 0; i < rewardSlots; i++) {
            if (!session.itemRewards().containsKey(i) && !session.commandSlots().contains(i)) { free = i; break; }
        }
        if (free < 0) {
            plugin.messages().send(player, "editor-full");
            return false;
        }

        double weight = plugin.getConfig().getDouble("editor.default-new-item-weight", 10.0);
        int amount = Math.max(1, original.getAmount());
        MMOItemsHook.MmoIdentity identity = mmoItems.identify(original);
        Reward reward;
        if (identity != null) {
            reward = Reward.builder("", RewardType.MMOITEM)
                    .weight(weight).amount(amount).mmoItems(identity.type(), identity.id()).build();
        } else {
            ItemStack stored = original.clone();
            stored.setAmount(1);
            reward = Reward.builder("", RewardType.ITEM)
                    .weight(weight).amount(amount).storedItem(stored).build();
        }
        session.itemRewards().put(free, reward);
        CrateDefinition crate = plugin.crateRepository().get(session.crateId());
        ItemStack visual = identity != null ? plugin.rewardService().preview(reward) : original.clone();
        if (visual == null) visual = original.clone();
        session.inventory().setItem(free, decorate(visual, crate, reward, false));
        session.saved(false);
        plugin.messages().send(player, "editor-added");
        return true;
    }

    public void removeSlot(Player player, int slot) {
        EditorSession session = session(player);
        if (session == null) return;
        if (session.commandSlots().contains(slot)) {
            player.sendMessage(Text.color("&eLas recompensas COMMAND se editan en crates.yml."));
            return;
        }
        if (session.itemRewards().remove(slot) != null) {
            session.inventory().setItem(slot, null);
            session.saved(false);
            plugin.messages().send(player, "editor-removed");
        }
    }

    public void save(Player player) {
        EditorSession session = session(player);
        if (session == null || session.saved()) return;
        List<Reward> list = new ArrayList<>();
        for (int slot : new TreeSet<>(session.itemRewards().keySet())) list.add(session.itemRewards().get(slot));
        plugin.crateRepository().replaceItemRewards(session.crateId(), list);
        plugin.crateManager().rebuildIndex();
        session.saved(true);
        plugin.messages().send(player, "editor-saved", Map.of("crate", session.crateId()));
    }

    public void close(Player player) {
        save(player);
        sessions.remove(player.getUniqueId());
    }

    public void discard(Player player) {
        sessions.remove(player.getUniqueId());
    }

    private ItemStack decorate(ItemStack source, CrateDefinition crate, Reward reward, boolean command) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.color(command ? "&6&l[COMMAND] &7Solo editable en crates.yml" : "&e&l[RECOMPENSA EDITABLE]"));
        lore.add(Text.color("&7Tipo: &f" + reward.type().name()));
        lore.add(Text.color("&7Peso: &e" + trim(reward.weight())));
        if (reward.chance() != null) lore.add(Text.color("&7Chance configurado: &b" + trim(reward.chance()) + "%"));
        if (reward.id() == null || reward.id().isBlank()) {
            lore.add(Text.color("&7Probabilidad efectiva: &8se calcula al guardar"));
        } else {
            double chance = plugin.rewardService().probabilityPercent(crate, reward);
            lore.add(Text.color("&7Probabilidad efectiva: &a" + String.format(Locale.US, "%.2f", chance) + "%"));
        }
        if (reward.type() == RewardType.MMOITEM) {
            lore.add(Text.color("&7MMOItems: &b" + reward.mmoItemsType() + ":" + reward.mmoItemsId()));
        }
        if (!command) lore.add(Text.color("&cClick izquierdo para quitar."));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(name));
        meta.setLore(Text.color(lore));
        item.setItemMeta(meta);
        return item;
    }

    private int normalizeSize(int requested) {
        int size = Math.max(27, Math.min(54, requested));
        return ((size + 8) / 9) * 9;
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) return Long.toString(Math.round(value));
        return String.format(Locale.US, "%.2f", value);
    }
}
