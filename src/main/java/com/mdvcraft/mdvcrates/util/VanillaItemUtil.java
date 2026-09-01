package com.mdvcraft.mdvcrates.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Distingue un stack vanilla "simple" de un ItemStack que necesita conservar
 * metadata/componentes. Los stacks vanilla simples se pueden representar en
 * YAML únicamente con su Material + amount.
 */
public final class VanillaItemUtil {
    private VanillaItemUtil() {}

    public static boolean isPlainVanilla(ItemStack item) {
        if (item == null) return false;
        Material material = item.getType();
        if (!material.isItem() || material.isAir()) return false;

        // isSimilar ignora la cantidad y compara tipo + metadata. Compararlo
        // contra un stack nuevo del mismo Material evita clasificar como VANILLA
        // un objeto renombrado, encantado, dañado o con componentes custom.
        return item.isSimilar(new ItemStack(material));
    }
}
