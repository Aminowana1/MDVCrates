package com.mdvcraft.mdvcrates.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;

public final class PlaceholderUtil {
    private static Method papiMethod;
    private static boolean lookedUp;

    private PlaceholderUtil() {}

    public static String apply(Player player, String input, Map<String, String> replacements) {
        String out = input == null ? "" : input;
        if (replacements != null) {
            for (Map.Entry<String, String> e : replacements.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        out = out.replace("{player}", player.getName()).replace("%player%", player.getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                if (!lookedUp) {
                    Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    papiMethod = clazz.getMethod("setPlaceholders", Player.class, String.class);
                    lookedUp = true;
                }
                if (papiMethod != null) out = (String) papiMethod.invoke(null, player, out);
            } catch (Throwable ignored) {
                lookedUp = true;
            }
        }
        return out;
    }
}
