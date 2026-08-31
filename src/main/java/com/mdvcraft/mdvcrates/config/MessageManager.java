package com.mdvcraft.mdvcrates.config;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

public final class MessageManager {
    private final MDVCratesPlugin plugin;
    private YamlConfiguration yaml;

    public MessageManager(MDVCratesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public String raw(String key) {
        return yaml.getString(key, key);
    }

    public String format(String key, Map<String, String> replacements) {
        String value = raw(key);
        if (replacements != null) {
            for (var e : replacements.entrySet()) {
                value = value.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return Text.color(value);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        String prefix = Text.color(yaml.getString("prefix", ""));
        sender.sendMessage(prefix + format(key, replacements));
    }
}
