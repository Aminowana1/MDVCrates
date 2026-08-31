package com.mdvcraft.mdvcrates.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Hook por reflexión para no obligar a Maven a resolver MMOItems.
 * En runtime usa los métodos públicos de MMOItems: getTypeName, getID y getItem(String,String).
 */
public final class MMOItemsHook {
    private final Logger logger;
    private boolean available;
    private Method getTypeName;
    private Method getId;
    private Field pluginField;
    private Method getItemByStrings;

    public MMOItemsHook(Logger logger) {
        this.logger = logger;
        reload();
    }

    public void reload() {
        available = false;
        getTypeName = null;
        getId = null;
        pluginField = null;
        getItemByStrings = null;
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            logger.warning("MMOItems no está activo. Las crates con llaves/rewards MMOItems no podrán abrirse.");
            return;
        }
        try {
            Class<?> clazz = Class.forName("net.Indyuce.mmoitems.MMOItems");
            getTypeName = clazz.getMethod("getTypeName", ItemStack.class);
            getId = clazz.getMethod("getID", ItemStack.class);
            pluginField = clazz.getField("plugin");
            Object plugin = pluginField.get(null);
            getItemByStrings = plugin.getClass().getMethod("getItem", String.class, String.class);
            available = true;
            logger.info("Hook de MMOItems activo.");
        } catch (Throwable ex) {
            logger.warning("MMOItems está instalado pero no se pudo inicializar el hook: " + ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public MmoIdentity identify(ItemStack item) {
        if (!available || item == null || item.getType().isAir()) return null;
        try {
            String type = (String) getTypeName.invoke(null, item);
            String id = (String) getId.invoke(null, item);
            if (type == null || id == null || type.isBlank() || id.isBlank()) return null;
            return new MmoIdentity(type, id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean matches(ItemStack item, String type, String id) {
        MmoIdentity identity = identify(item);
        return identity != null
                && identity.type().equalsIgnoreCase(type)
                && identity.id().equalsIgnoreCase(id);
    }

    public ItemStack getItem(String type, String id, int amount) {
        if (!available || type == null || id == null) return null;
        try {
            Object plugin = pluginField.get(null);
            ItemStack stack = (ItemStack) getItemByStrings.invoke(plugin, type, id);
            if (stack == null || stack.getType().isAir()) return null;
            stack = stack.clone();
            stack.setAmount(Math.max(1, amount));
            return stack;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public record MmoIdentity(String type, String id) {}
}
