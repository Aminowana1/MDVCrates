package com.mdvcraft.mdvcrates.model;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.Objects;

public record BlockKey(String world, int x, int y, int z) {
    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(Location location) {
        return new BlockKey(Objects.requireNonNull(location.getWorld()).getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public String serialize() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public static BlockKey deserialize(String raw) {
        if (raw == null) return null;
        String[] p = raw.split(";", 4);
        if (p.length != 4) return null;
        try {
            return new BlockKey(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
