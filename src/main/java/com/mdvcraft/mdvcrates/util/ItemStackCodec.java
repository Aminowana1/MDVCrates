package com.mdvcraft.mdvcrates.util;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

public final class ItemStackCodec {
    private ItemStackCodec() {}

    public static String encode(ItemStack item) {
        if (item == null) return null;
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
