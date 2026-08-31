package com.mdvcraft.mdvcrates.util;

import org.bukkit.Material;

public final class CrateBlocks {
    private CrateBlocks() {}

    public static boolean isSupported(Material material) {
        if (material == null || !material.isBlock()) return false;
        if (material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.ENDER_CHEST) return true;
        return material.name().endsWith("_SHULKER_BOX") || material == Material.SHULKER_BOX;
    }
}
