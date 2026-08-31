package com.mdvcraft.mdvcrates.util;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    private Text() {}

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static List<String> color(List<String> lines) {
        List<String> out = new ArrayList<>();
        if (lines != null) for (String line : lines) out.add(color(line));
        return out;
    }

    public static String itemName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "Objeto";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        String raw = item.getType().name().toLowerCase().replace('_', ' ');
        String[] p = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String x : p) {
            if (x.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));
        }
        return sb.toString();
    }
}
