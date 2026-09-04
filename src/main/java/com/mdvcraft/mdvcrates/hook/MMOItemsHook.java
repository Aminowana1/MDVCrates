package com.mdvcraft.mdvcrates.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Hook por reflexión para no obligar a Maven a resolver MMOItems.
 *
 * Entrega real: usa MMOItems#getItem(String,String), conservando el build normal y sus
 * modifiers aleatorios.
 *
 * Preview general (ruleta/editor): conserva exactamente la ruta previa de MDVCrates.
 *
 * Preview del VISUALIZADOR: usa el mismo patrón que las GUIs de MMOItems,
 * construyendo MMOItemBuilder(template, 0, null, true) desde el inicio. Así el
 * modifier group no se recolecta y no aparecen prefijos/sufijos/calidades aleatorias
 * como Roto/Tosco/Bueno. Además evita evaluar las fórmulas de esos modifiers.
 */
public final class MMOItemsHook {
    private final Logger logger;
    private final Map<String, ItemStack> viewerPreviewCache = new HashMap<>();

    private boolean available;
    private Method getTypeName;
    private Method getId;
    private Field pluginField;
    private Method getItemByStrings;

    // Ruta previa de preview general (se conserva para no cambiar ruleta/editor).
    private Method getTypes;
    private Method typeManagerGet;
    private Method getMmoItem;
    private Method newBuilder;
    private Method buildDisplay;

    // Ruta específica del viewer, sin modifiers.
    private Method getTemplates;
    private Method templateManagerGetTemplate;
    private Class<?> mmoItemBuilderClass;

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
        getTemplates = null;
        templateManagerGetTemplate = null;
        mmoItemBuilderClass = null;
        viewerPreviewCache.clear();

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
            initApi(plugin);
            logger.info("Hook de MMOItems activo" + (viewerPreviewApiReady()
                    ? " (viewer sin modifiers activo)."
                    : "."));
        } catch (Throwable ex) {
            logger.warning("MMOItems está instalado pero no se pudo inicializar el hook: " + ex.getMessage());
        }
    }

    private void initApi(Object plugin) {
        try {
            getTypes = plugin.getClass().getMethod("getTypes");
            Object typeManager = getTypes.invoke(plugin);
            typeManagerGet = Arrays.stream(typeManager.getClass().getMethods())
                    .filter(m -> m.getName().equals("get")
                            && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class)
                    .findFirst().orElse(null);
            if (typeManagerGet == null) return;

            // Se mantiene para preview general previo.
            getMmoItem = Arrays.stream(plugin.getClass().getMethods())
                    .filter(m -> m.getName().equals("getMMOItem")
                            && m.getParameterCount() == 2
                            && m.getParameterTypes()[1] == String.class
                            && m.getParameterTypes()[0] != String.class)
                    .findFirst().orElse(null);

            // API de templates para viewer/validación sin generar modifiers.
            getTemplates = plugin.getClass().getMethod("getTemplates");
            Object templateManager = getTemplates.invoke(plugin);
            templateManagerGetTemplate = Arrays.stream(templateManager.getClass().getMethods())
                    .filter(m -> m.getName().equals("getTemplate")
                            && m.getParameterCount() == 2
                            && m.getParameterTypes()[1] == String.class
                            && m.getParameterTypes()[0] != String.class)
                    .findFirst().orElse(null);

            mmoItemBuilderClass = Class.forName("net.Indyuce.mmoitems.api.item.build.MMOItemBuilder");
        } catch (Throwable ignored) {
            // El hook de entrega puede seguir funcionando aunque la API de preview cambie.
            getTemplates = null;
            templateManagerGetTemplate = null;
            mmoItemBuilderClass = null;
        }
    }

    private boolean templateApiReady() {
        return getTypes != null
                && typeManagerGet != null
                && getTemplates != null
                && templateManagerGetTemplate != null;
    }

    private boolean viewerPreviewApiReady() {
        return templateApiReady() && mmoItemBuilderClass != null;
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

    /**
     * Comprueba que el template MMOItem exista SIN generar el objeto.
     * Esto evita tirar modifiers solo para calcular la lista/probabilidades de la crate.
     */
    public boolean exists(String type, String id) {
        if (!available || type == null || id == null || !templateApiReady()) return false;
        try {
            Object plugin = pluginField.get(null);
            Object typeManager = getTypes.invoke(plugin);
            Object itemType = typeManagerGet.invoke(typeManager, type);
            if (itemType == null) return false;
            Object templateManager = getTemplates.invoke(plugin);
            return templateManagerGetTemplate.invoke(templateManager, itemType, id) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Generación REAL de la recompensa. Conserva modifiers/tier/calidad aleatorios.
     */
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

    /**
     * Preview GENERAL previo de MDVCrates. Se conserva para no alterar ruleta/editor.
     */
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
                        .filter(m -> m.getName().equals("build")
                                && m.getParameterCount() == 1
                                && (m.getParameterTypes()[0] == boolean.class
                                || m.getParameterTypes()[0] == Boolean.class))
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

    /**
     * Preview EXCLUSIVO del visualizador de recompensas.
     *
     * Replica el patrón de GUI de MMOItems:
     * new MMOItemBuilder(template, 0, null, true).build().newBuilder().build(true)
     *
     * El primer true es el importante: el constructor no recolecta el modifier group.
     * Por diseño NO existe fallback a getItem(), ya que eso volvería a generar
     * Roto/Tosco/Bueno/etc. y a evaluar sus fórmulas.
     */
    public ItemStack getViewerPreviewItem(String type, String id, int amount) {
        if (!available || type == null || id == null || !viewerPreviewApiReady()) return null;

        String cacheKey = type.toUpperCase() + ":" + id.toUpperCase();
        ItemStack cached = viewerPreviewCache.get(cacheKey);
        if (cached != null) {
            ItemStack clone = cached.clone();
            clone.setAmount(Math.max(1, amount));
            return clone;
        }

        try {
            Object plugin = pluginField.get(null);

            Object typeManager = getTypes.invoke(plugin);
            Object itemType = typeManagerGet.invoke(typeManager, type);
            if (itemType == null) return null;

            Object templateManager = getTemplates.invoke(plugin);
            Object template = templateManagerGetTemplate.invoke(templateManager, itemType, id);
            if (template == null) return null;

            Constructor<?> displayConstructor = Arrays.stream(mmoItemBuilderClass.getConstructors())
                    .filter(c -> {
                        Class<?>[] p = c.getParameterTypes();
                        return p.length == 4
                                && p[0].isAssignableFrom(template.getClass())
                                && (p[1] == int.class || p[1] == Integer.class)
                                && (p[3] == boolean.class || p[3] == Boolean.class);
                    })
                    .findFirst().orElse(null);
            if (displayConstructor == null) return null;

            Object mmoItemBuilder = displayConstructor.newInstance(template, 0, null, true);
            Method buildMmoItem = mmoItemBuilder.getClass().getMethod("build");
            Object mmoItem = buildMmoItem.invoke(mmoItemBuilder);
            if (mmoItem == null) return null;

            Method mmoItemNewBuilder = mmoItem.getClass().getMethod("newBuilder");
            Object itemStackBuilder = mmoItemNewBuilder.invoke(mmoItem);
            if (itemStackBuilder == null) return null;

            Method buildStackDisplay = Arrays.stream(itemStackBuilder.getClass().getMethods())
                    .filter(m -> m.getName().equals("build")
                            && m.getParameterCount() == 1
                            && (m.getParameterTypes()[0] == boolean.class
                            || m.getParameterTypes()[0] == Boolean.class))
                    .findFirst().orElse(null);
            if (buildStackDisplay == null) return null;

            Object built = buildStackDisplay.invoke(itemStackBuilder, true);
            if (!(built instanceof ItemStack stack) || stack.getType().isAir()) return null;

            ItemStack clean = stack.clone();
            clean.setAmount(1);
            viewerPreviewCache.put(cacheKey, clean);

            ItemStack out = clean.clone();
            out.setAmount(Math.max(1, amount));
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public record MmoIdentity(String type, String id) {}
}
