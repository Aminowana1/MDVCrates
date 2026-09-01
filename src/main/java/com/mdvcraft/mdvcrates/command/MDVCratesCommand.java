package com.mdvcraft.mdvcrates.command;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.model.CrateDefinition;
import com.mdvcraft.mdvcrates.util.Text;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public final class MDVCratesCommand implements CommandExecutor, TabCompleter {
    private final MDVCratesPlugin plugin;

    public MDVCratesCommand(MDVCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mdvcrates.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(sender, args);
            case "editor" -> editor(sender, args);
            case "place" -> place(sender, args);
            case "remove" -> remove(sender);
            case "move" -> move(sender, args);
            case "reload" -> {
                plugin.reloadEverything();
                plugin.messages().send(sender, "admin-reloaded");
            }
            case "list" -> list(sender);
            default -> help(sender);
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(Text.color("&cUso: /mdvcrates create <id>")); return; }
        String id = sanitize(args[1]);
        if (id.isBlank()) { sender.sendMessage(Text.color("&cID inválida.")); return; }
        if (!plugin.crateRepository().create(id)) {
            plugin.messages().send(sender, "admin-exists", Map.of("crate", id));
            return;
        }
        plugin.crateManager().rebuildIndex();
        plugin.messages().send(sender, "admin-created", Map.of("crate", id));
    }

    private void editor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { plugin.messages().send(sender, "player-only"); return; }
        if (args.length < 2) { player.sendMessage(Text.color("&cUso: /mdvcrates editor <id>")); return; }
        CrateDefinition crate = plugin.crateRepository().get(args[1]);
        if (crate == null) { plugin.messages().send(player, "crate-not-found", Map.of("crate", args[1])); return; }
        plugin.editorManager().open(player, crate);
    }

    private void place(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { plugin.messages().send(sender, "player-only"); return; }
        if (args.length < 2) { player.sendMessage(Text.color("&cUso: /mdvcrates place <id>")); return; }
        CrateDefinition crate = plugin.crateRepository().get(args[1]);
        if (crate == null) { plugin.messages().send(player, "crate-not-found", Map.of("crate", args[1])); return; }
        Block destination = plugin.crateManager().targetPlacementBlock(player);
        if (destination == null) { plugin.messages().send(player, "admin-target-occupied"); return; }
        if (!plugin.crateManager().place(player, crate.id(), destination)) {
            plugin.messages().send(player, "admin-target-occupied");
            return;
        }
        plugin.messages().send(player, "admin-placed", Map.of("crate", crate.id()));
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) { plugin.messages().send(sender, "player-only"); return; }
        Block target = player.getTargetBlockExact(6);
        if (target == null) { plugin.messages().send(player, "admin-look-block"); return; }
        if (!plugin.crateManager().remove(target, true)) {
            plugin.messages().send(player, "admin-not-looking-crate");
            return;
        }
        plugin.messages().send(player, "admin-removed");
    }

    private void move(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { plugin.messages().send(sender, "player-only"); return; }
        if (args.length < 2) { player.sendMessage(Text.color("&cUso: /mdvcrates move <id>")); return; }
        CrateDefinition crate = plugin.crateRepository().get(args[1]);
        if (crate == null) { plugin.messages().send(player, "crate-not-found", Map.of("crate", args[1])); return; }
        Block destination = plugin.crateManager().targetPlacementBlock(player);
        if (destination == null) { plugin.messages().send(player, "admin-target-occupied"); return; }
        if (!plugin.crateManager().moveNearest(player, crate.id(), destination)) {
            player.sendMessage(Text.color("&cNo encontré una instancia cercana de esa crate o el destino no es válido."));
            return;
        }
        plugin.messages().send(player, "admin-moved", Map.of("crate", crate.id()));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(Text.color("&6&lMDVCrates &7- crates cargadas:"));
        for (CrateDefinition crate : plugin.crateRepository().all()) {
            sender.sendMessage(Text.color("&8- &e" + crate.id() + " &7| ubicaciones: &f" + crate.locations().size()
                    + " &7| rewards: &f" + crate.rewards().size() + " &7| " + (crate.enabled() ? "&aON" : "&cOFF")));
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.color("&6&lMDVCrates 1.2.0"));
        sender.sendMessage(Text.color("&e/mdvcrates create <id> &7- crea config"));
        sender.sendMessage(Text.color("&e/mdvcrates editor <id> &7- editor de recompensas"));
        sender.sendMessage(Text.color("&e/mdvcrates place <id> &7- coloca sobre el bloque mirado"));
        sender.sendMessage(Text.color("&e/mdvcrates remove &7- elimina la crate mirada"));
        sender.sendMessage(Text.color("&e/mdvcrates move <id> &7- mueve la instancia cercana"));
        sender.sendMessage(Text.color("&e/mdvcrates reload"));
        sender.sendMessage(Text.color("&e/mdvcrates list"));
    }

    private String sanitize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mdvcrates.admin")) return List.of();
        if (args.length == 1) return filter(List.of("create", "editor", "place", "remove", "move", "reload", "list"), args[0]);
        if (args.length == 2 && Set.of("editor", "place", "move").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> ids = plugin.crateRepository().all().stream().map(CrateDefinition::id).toList();
            return filter(ids, args[1]);
        }
        return List.of();
    }

    private List<String> filter(Collection<String> values, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
    }
}
