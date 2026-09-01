package com.mdvcraft.mdvcrates.editor;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.hook.MMOItemsHook;
import com.mdvcraft.mdvcrates.model.*;
import com.mdvcraft.mdvcrates.util.Text;
import com.mdvcraft.mdvcrates.util.VanillaItemUtil;
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
        CrateEditorHolder holder = new CrateEditorHolder(crate.id());
        Inventory inv = Bukkit.createInventory(holder, size, Text.color("&8MDVCrates: &6" + crate.id()));
        holder.bind(inv);

        List<Reward> editable = crate.rewards().stream()
                .filter(r -> r.type() != RewardType.COMMAND)
                .toList();
        List<Reward> commands = crate.rewards().stream()
                .filter(r -> r.type() == RewardType.COMMAND)
                .toList();

        EditorSession session = new EditorSession(crate.id(), inv, editable, commands);
        sessions.put(player.getUniqueId(), session);
        render(player, session);
        player.openInventory(inv);
    }

    public boolean addFromPlayerInventory(Player player, ItemStack original) {
        EditorSession session = session(player);
        if (session == null || original == null || original.getType().isAir()) return false;

        double weight = plugin.getConfig().getDouble("editor.default-new-item-weight", 10.0);
        int amount = Math.max(1, original.getAmount());
        MMOItemsHook.MmoIdentity identity = mmoItems.identify(original);
        Reward reward;

        if (identity != null) {
            reward = Reward.builder("", RewardType.MMOITEM)
                    .weight(weight).amount(amount).mmoItems(identity.type(), identity.id()).build();
        } else if (VanillaItemUtil.isPlainVanilla(original)) {
            reward = Reward.builder("", RewardType.VANILLA)
                    .weight(weight).amount(amount).vanillaMaterial(original.getType()).build();
        } else {
            ItemStack stored = original.clone();
            stored.setAmount(1);
            reward = Reward.builder("", RewardType.ITEM)
                    .weight(weight).amount(amount).storedItem(stored).build();
        }

        session.itemRewards().add(reward);
        session.saved(false);

        int pageSize = pageSize(session);
        session.page((session.itemRewards().size() - 1) / pageSize);
        render(player, session);
        plugin.messages().send(player, "editor-added");
        return true;
    }

    public void removeSlot(Player player, int slot) {
        EditorSession session = session(player);
        if (session == null) return;
        int pageSize = pageSize(session);
        if (slot < 0 || slot >= pageSize) return;

        int displayIndex = session.page() * pageSize + slot;
        if (displayIndex < session.itemRewards().size()) {
            session.itemRewards().remove(displayIndex);
            session.saved(false);
            normalizePage(session);
            render(player, session);
            plugin.messages().send(player, "editor-removed");
            return;
        }

        if (showCommandRewards() && displayIndex < session.itemRewards().size() + session.commandRewards().size()) {
            player.sendMessage(Text.color("&eLas recompensas COMMAND se editan en crates/" + session.crateId() + ".yml."));
        }
    }

    public void previousPage(Player player) {
        EditorSession session = session(player);
        if (session == null || session.page() <= 0) return;
        session.page(session.page() - 1);
        render(player, session);
    }

    public void nextPage(Player player) {
        EditorSession session = session(player);
        if (session == null) return;
        int maxPage = maxPage(session);
        if (session.page() >= maxPage) return;
        session.page(session.page() + 1);
        render(player, session);
    }

    public void save(Player player) {
        EditorSession session = session(player);
        if (session == null || session.saved()) return;
        plugin.crateRepository().replaceItemRewards(session.crateId(), new ArrayList<>(session.itemRewards()));
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

    private void render(Player player, EditorSession session) {
        CrateDefinition crate = plugin.crateRepository().get(session.crateId());
        if (crate == null) return;

        Inventory inv = session.inventory();
        int pageSize = pageSize(session);
        int base = inv.getSize() - 9;
        int maxPage = maxPage(session);
        if (session.page() > maxPage) session.page(maxPage);

        for (int i = 0; i < base; i++) inv.setItem(i, null);
        for (int i = base; i < inv.getSize(); i++) inv.setItem(i, null);

        int from = session.page() * pageSize;
        int totalItems = session.itemRewards().size();
        int totalCommands = showCommandRewards() ? session.commandRewards().size() : 0;
        int totalDisplay = totalItems + totalCommands;
        int to = Math.min(totalDisplay, from + pageSize);

        for (int displayIndex = from; displayIndex < to; displayIndex++) {
            Reward reward;
            boolean command;
            if (displayIndex < totalItems) {
                reward = session.itemRewards().get(displayIndex);
                command = false;
            } else {
                reward = session.commandRewards().get(displayIndex - totalItems);
                command = true;
            }

            ItemStack preview = plugin.rewardService().preview(reward);
            if (preview == null) preview = command ? new ItemStack(Material.NETHER_STAR) : new ItemStack(Material.BARRIER);
            inv.setItem(displayIndex - from, decorate(preview, crate, reward, command));
        }

        if (session.page() > 0) {
            inv.setItem(base, button(Material.ARROW, "&e&lPágina anterior", List.of("&7Página " + session.page() + "/" + (maxPage + 1))));
        }
        inv.setItem(base + 1, button(Material.BOOK, "&e&lEditor de recompensas", List.of(
                "&7SHIFT + click en un objeto de tu inventario",
                "&7para añadir una copia como recompensa.",
                "",
                "&7Los premios pueden ocupar varias páginas.",
                "&7Click en un premio de ítem para quitarlo.",
                "&7Los COMMAND se editan en crates/" + session.crateId() + ".yml.")));
        inv.setItem(base + 4, button(Material.LIME_CONCRETE, "&a&lGuardar y cerrar", List.of(
                "&7Guarda todas las páginas.",
                "&7Recompensas editables: &f" + session.itemRewards().size(),
                "&7Página: &f" + (session.page() + 1) + "/" + (maxPage + 1))));
        if (session.page() < maxPage) {
            inv.setItem(base + 7, button(Material.ARROW, "&e&lPágina siguiente", List.of("&7Página " + (session.page() + 2) + "/" + (maxPage + 1))));
        }
        inv.setItem(base + 8, button(Material.BARRIER, "&c&lCerrar", List.of("&7También guarda automáticamente.")));
    }

    private ItemStack decorate(ItemStack source, CrateDefinition crate, Reward reward, boolean command) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.color(command ? "&6&l[COMMAND] &7Solo editable en YAML" : "&e&l[RECOMPENSA EDITABLE]"));
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
        } else if (reward.type() == RewardType.VANILLA && reward.vanillaMaterial() != null) {
            lore.add(Text.color("&7Material: &b" + reward.vanillaMaterial().name()));
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
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(Text.color(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private int normalizeSize(int requested) {
        int size = Math.max(27, Math.min(54, requested));
        return ((size + 8) / 9) * 9;
    }

    private int pageSize(EditorSession session) {
        int usable = session.inventory().getSize() - 9;
        int configured = plugin.getConfig().getInt("editor.rewards-per-page",
                plugin.getConfig().getInt("editor.reward-slots", 45));
        return Math.max(1, Math.min(configured, usable));
    }

    private int displayCount(EditorSession session) {
        return session.itemRewards().size() + (showCommandRewards() ? session.commandRewards().size() : 0);
    }

    private int maxPage(EditorSession session) {
        int count = Math.max(1, displayCount(session));
        return Math.max(0, (count - 1) / pageSize(session));
    }

    private void normalizePage(EditorSession session) {
        if (session.page() > maxPage(session)) session.page(maxPage(session));
    }

    private boolean showCommandRewards() {
        return plugin.getConfig().getBoolean("editor.show-command-rewards", true);
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) return Long.toString(Math.round(value));
        return String.format(Locale.US, "%.2f", value);
    }
}
