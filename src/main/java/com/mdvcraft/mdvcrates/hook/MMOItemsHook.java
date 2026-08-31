package com.mdvcraft.mdvcrates.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Hook por reflexión para no obligar a Maven a resolver MMOItems.
 *
 * Entrega: usa MMOItems#getItem(String,String), conservando el build normal y sus
 * modifiers aleatorios.
 * Preview: intenta MMOItem#newBuilder().build(true), pensado por MMOItems para
 * representaciones visuales/GUI sin reutilizar el ItemStack concreto que el admin
 * insertó en el editor. Si la API cambia, cae de forma segura al build normal.
 */
public final class MMOItemsHook {
    private final Logger logger;
    private boolean available;
    private Method getTypeName;
    private Method getId;
    private Field pluginField;
    private Method getItemByStrings;

    private Method getTypes;
    private Method typeManagerGet;
    private Method getMmoItem;
    private Method newBuilder;
    private Method buildDisplay;

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
        getTypes = null;
        typeManagerGet = null;
        getMmoItem = null;
        newBuilder = null;
        buildDisplay = null;

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
            initDisplayApi(plugin);
            logger.info("Hook de MMOItems activo" + (buildDisplay != null ? " (preview limpio activo)." : "."));
        } catch (Throwable ex) {
            logger.warning("MMOItems está instalado pero no se pudo inicializar el hook: " + ex.getMessage());
        }
    }

    private void initDisplayApi(Object plugin) {
        try {
            getTypes = plugin.getClass().getMethod("getTypes");
            Object typeManager = getTypes.invoke(plugin);
            typeManagerGet = Arrays.stream(typeManager.getClass().getMethods())
                    .filter(m -> m.getName().equals("get") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class)
                    .findFirst().orElse(null);
            if (typeManagerGet == null) return;

            getMmoItem = Arrays.stream(plugin.getClass().getMethods())
                    .filter(m -> m.getName().equals("getMMOItem") && m.getParameterCount() == 2
                            && m.getParameterTypes()[1] == String.class && m.getParameterTypes()[0] != String.class)
                    .findFirst().orElse(null);
            if (getMmoItem == null) return;

            // Los tipos concretos se resuelven en runtime; inicializamos newBuilder/build
            // en la primera llamada porque no necesitamos depender de sus clases en Maven.
        } catch (Throwable ignored) {
            getTypes = null;
            typeManagerGet = null;
            getMmoItem = null;
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

    public ItemStack getPreviewItem(String type, String id, int amount) {
        if (!available || type == null || id == null) return null;
        try {
            Object plugin = pluginField.get(null);
            if (getTypes == null || typeManagerGet == null || getMmoItem == null) return getItem(type, id, amount);
            Object typeManager = getTypes.invoke(plugin);
            Object itemType = typeManagerGet.invoke(typeManager, type);
            if (itemType == null) return getItem(type, id, amount);
            Object mmoItem = getMmoItem.invoke(plugin, itemType, id);
            if (mmoItem == null) return null;

            if (newBuilder == null || !newBuilder.getDeclaringClass().isAssignableFrom(mmoItem.getClass())) {
                newBuilder = mmoItem.getClass().getMethod("newBuilder");
            }
            Object builder = newBuilder.invoke(mmoItem);
            if (builder == null) return getItem(type, id, amount);
            if (buildDisplay == null || !buildDisplay.getDeclaringClass().isAssignableFrom(builder.getClass())) {
                buildDisplay = Arrays.stream(builder.getClass().getMethods())
                        .filter(m -> m.getName().equals("build") && m.getParameterCount() == 1
                                && (m.getParameterTypes()[0] == boolean.class || m.getParameterTypes()[0] == Boolean.class))
                        .findFirst().orElse(null);
            }
            if (buildDisplay == null) return getItem(type, id, amount);
            Object built = buildDisplay.invoke(builder, true);
            if (!(built instanceof ItemStack stack) || stack.getType().isAir()) return getItem(type, id, amount);
            stack = stack.clone();
            stack.setAmount(Math.max(1, amount));
            return stack;
        } catch (Throwable ignored) {
            return getItem(type, id, amount);
        }
    }

    public record MmoIdentity(String type, String id) {}
}
