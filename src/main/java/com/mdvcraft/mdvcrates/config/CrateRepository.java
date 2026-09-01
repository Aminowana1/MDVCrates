package com.mdvcraft.mdvcrates.config;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.hook.MMOItemsHook;
import com.mdvcraft.mdvcrates.model.*;
import com.mdvcraft.mdvcrates.util.ItemStackCodec;
import com.mdvcraft.mdvcrates.util.CrateBlocks;
import com.mdvcraft.mdvcrates.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public final class CrateRepository {
    private final MDVCratesPlugin plugin;
    private final MMOItemsHook mmoItems;
    private final File file;
    private final File placementsFile;
    private YamlConfiguration yaml;
    private YamlConfiguration placementsYaml;
    private final Map<String, CrateDefinition> crates = new LinkedHashMap<>();

    public CrateRepository(MDVCratesPlugin plugin, MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.mmoItems = mmoItems;
        this.file = new File(plugin.getDataFolder(), "crates.yml");
        this.placementsFile = new File(plugin.getDataFolder(), "placements.yml");
        reload();
    }

    public synchronized void reload() {
        if (!file.exists()) plugin.saveResource("crates.yml", false);
        if (!placementsFile.exists()) plugin.saveResource("placements.yml", false);

        yaml = YamlConfiguration.loadConfiguration(file);
        placementsYaml = YamlConfiguration.loadConfiguration(placementsFile);

        // 1.1.2+: las ubicaciones físicas viven fuera de crates.yml.
        // Esto permite reemplazar/editar una definición y hacer /mdvcrates reload
        // sin tener que volver a colocar las crates que ya existen en el mundo.
        migrateLegacyLocations();

        crates.clear();
        ConfigurationSection root = yaml.getConfigurationSection("crates");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            CrateDefinition def = parse(id, sec);
            crates.put(id.toLowerCase(Locale.ROOT), def);
        }
    }

    public Collection<CrateDefinition> all() {
        return Collections.unmodifiableCollection(crates.values());
    }

    public CrateDefinition get(String id) {
        return id == null ? null : crates.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public synchronized boolean create(String id) {
        if (id == null || id.isBlank() || exists(id)) return false;
        String path = "crates." + id;
        yaml.set(path + ".enabled", true);
        yaml.set(path + ".display-name", "&6&l" + id);
        yaml.set(path + ".block-material", "CHEST");
        yaml.set(path + ".key.mmoitems-type", "LLAVE");
        yaml.set(path + ".key.mmoitems-id", "LLAVE_" + id.toUpperCase(Locale.ROOT));
        yaml.set(path + ".viewer.show-percentages", true);
        yaml.set(path + ".name-display.enabled", true);
        yaml.set(path + ".name-display.text", "{display-name}");
        yaml.set(path + ".name-display.offset.x", 0.5);
        yaml.set(path + ".name-display.offset.y", 1.85);
        yaml.set(path + ".name-display.offset.z", 0.5);
        yaml.set(path + ".name-display.hide-during-opening", false);
        yaml.createSection(path + ".rewards");
        saveFile();
        placementsYaml.set("placements." + id, new ArrayList<String>());
        savePlacementsFile();
        reload();
        return true;
    }

    public synchronized void setLocations(String crateId, List<BlockKey> locations) {
        List<String> raw = locations.stream().map(BlockKey::serialize).toList();
        placementsYaml.set("placements." + crateId, raw);
        savePlacementsFile();
        reload();
    }

    public synchronized void replaceItemRewards(String crateId, List<Reward> editedRewards) {
        String rewardsPath = "crates." + crateId + ".rewards";
        ConfigurationSection sec = yaml.getConfigurationSection(rewardsPath);
        if (sec == null) sec = yaml.createSection(rewardsPath);

        // Las recompensas COMMAND solo se editan manualmente en YAML.
        for (String key : new HashSet<>(sec.getKeys(false))) {
            String type = sec.getString(key + ".type", "ITEM");
            if (!"COMMAND".equalsIgnoreCase(type)) sec.set(key, null);
        }

        Set<String> used = new HashSet<>(sec.getKeys(false));
        int counter = 1;
        for (Reward reward : editedRewards) {
            String id = reward.id();
            if (id == null || id.isBlank() || used.contains(id)) {
                do {
                    id = "item_" + counter++;
                } while (used.contains(id));
            }
            used.add(id);
            String p = rewardsPath + "." + id;
            yaml.set(p + ".type", reward.type().name());
            yaml.set(p + ".weight", reward.weight());
            if (reward.chance() != null) yaml.set(p + ".chance", reward.chance());
            else yaml.set(p + ".chance", null);
            yaml.set(p + ".amount", reward.amount());
            if (reward.displayName() != null && !reward.displayName().isBlank()) {
                yaml.set(p + ".name", reward.displayName());
            }
            if (reward.type() == RewardType.MMOITEM) {
                yaml.set(p + ".mmoitems-type", reward.mmoItemsType());
                yaml.set(p + ".mmoitems-id", reward.mmoItemsId());
            } else if (reward.type() == RewardType.ITEM && reward.storedItem() != null) {
                yaml.set(p + ".item.format", "BUKKIT_BYTES_BASE64");
                yaml.set(p + ".item.data", ItemStackCodec.encode(reward.storedItem()));
            }
        }
        saveFile();
        reload();
    }

    public ConfigurationSection rawSection(String crateId) {
        return yaml.getConfigurationSection("crates." + crateId);
    }

    private CrateDefinition parse(String id, ConfigurationSection sec) {
        boolean enabled = sec.getBoolean("enabled", true);
        String displayName = sec.getString("display-name", id);
        Material block = Material.matchMaterial(sec.getString("block-material", "CHEST"));
        if (!CrateBlocks.isSupported(block)) {
            plugin.getLogger().warning("Crate '" + id + "' usa block-material no soportado; se usará CHEST. Soportados: CHEST, TRAPPED_CHEST, ENDER_CHEST y SHULKER_BOX de cualquier color.");
            block = Material.CHEST;
        }

        KeyDefinition key = new KeyDefinition(
                sec.getString("key.mmoitems-type", "LLAVE"),
                sec.getString("key.mmoitems-id", "LLAVE_" + id.toUpperCase(Locale.ROOT)));

        List<BlockKey> locations = loadLocations(id);

        List<Reward> rewards = new ArrayList<>();
        ConfigurationSection rewardsSec = sec.getConfigurationSection("rewards");
        if (rewardsSec != null) {
            for (String rewardId : rewardsSec.getKeys(false)) {
                ConfigurationSection rs = rewardsSec.getConfigurationSection(rewardId);
                if (rs == null) continue;
                Reward reward = parseReward(rewardId, rs);
                if (reward != null) rewards.add(reward);
            }
        }

        double explicitChanceTotal = rewards.stream().filter(Reward::hasExplicitChance).mapToDouble(r -> r.chance()).sum();
        if (explicitChanceTotal > 100.0001) {
            plugin.getLogger().warning("Crate '" + id + "' suma " + explicitChanceTotal + "% en chance explícito. MDVCrates normalizará esas probabilidades a 100% y los rewards solo por peso quedarán en 0%.");
        }

        ConfigurationSection animations = sec.getConfigurationSection("animations");
        if (animations == null) animations = plugin.getConfig().getConfigurationSection("default-animations");
        ConfigurationSection viewer = sec.getConfigurationSection("viewer");
        if (viewer == null) viewer = plugin.getConfig().getConfigurationSection("default-viewer");
        ConfigurationSection nameDisplay = sec.getConfigurationSection("name-display");
        if (nameDisplay == null) nameDisplay = plugin.getConfig().getConfigurationSection("default-name-display");
        return new CrateDefinition(id, enabled, displayName, block, key, locations, rewards, animations, viewer, nameDisplay);
    }

    private Reward parseReward(String id, ConfigurationSection rs) {
        RewardType type;
        try {
            type = RewardType.valueOf(rs.getString("type", "ITEM").toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            plugin.getLogger().warning("Reward '" + id + "' tiene type inválido.");
            return null;
        }
        double weight = Math.max(0, rs.getDouble("weight", 1.0));

        // chance/probability es opcional. No usar un ternario mezclando double y null:
        // Java puede intentar desempaquetar el null como Double#doubleValue(), lo que
        // provocaba un NPE al guardar desde el editor cualquier reward nuevo que
        // utilizara solo weight (por ejemplo un MMOItem recién añadido).
        Double chance = null;
        if (rs.contains("chance")) {
            chance = clampPercent(rs.getDouble("chance"));
        } else if (rs.contains("probability")) {
            chance = clampPercent(rs.getDouble("probability"));
        }

        int amount = Math.max(1, rs.getInt("amount", 1));
        Reward.Builder b = Reward.builder(id, type).weight(weight).chance(chance).amount(amount)
                .displayName(rs.getString("name"));

        switch (type) {
            case MMOITEM -> b.mmoItems(rs.getString("mmoitems-type"), rs.getString("mmoitems-id"));
            case ITEM -> {
                String encoded = rs.getString("item.data");
                ItemStack item = ItemStackCodec.decode(encoded);
                if (item == null) {
                    plugin.getLogger().warning("Reward ITEM '" + id + "' no pudo deserializarse.");
                    return null;
                }
                b.storedItem(item);
            }
            case COMMAND -> {
                b.commands(rs.getStringList("commands"));
                b.commandPreview(buildPreview(rs.getConfigurationSection("preview"), rs.getString("name", "&6Recompensa especial")));
            }
        }
        return b.build();
    }

    private static double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private ItemStack buildPreview(ConfigurationSection sec, String fallbackName) {
        Material material = Material.NETHER_STAR;
        if (sec != null) {
            Material parsed = Material.matchMaterial(sec.getString("material", "NETHER_STAR"));
            if (parsed != null && parsed.isItem()) material = parsed;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(sec == null ? fallbackName : sec.getString("name", fallbackName)));
            if (sec != null) meta.setLore(Text.color(sec.getStringList("lore")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<BlockKey> loadLocations(String crateId) {
        List<BlockKey> locations = new ArrayList<>();
        for (String raw : placementsYaml.getStringList("placements." + crateId)) {
            BlockKey keyLoc = BlockKey.deserialize(raw);
            if (keyLoc != null) locations.add(keyLoc);
        }
        return locations;
    }

    /**
     * Migra una sola vez las listas locations: antiguas de crates.yml.
     * Si placements.yml ya conoce una crate, incluso con lista vacía, esa
     * entrada manda y locations: deja de afectar a las colocaciones reales.
     */
    private void migrateLegacyLocations() {
        ConfigurationSection root = yaml.getConfigurationSection("crates");
        if (root == null) return;

        boolean changed = false;
        for (String id : root.getKeys(false)) {
            String placementPath = "placements." + id;
            if (placementsYaml.contains(placementPath)) continue;

            ConfigurationSection sec = root.getConfigurationSection(id);
            List<String> legacy = sec == null ? List.of() : sec.getStringList("locations");
            placementsYaml.set(placementPath, new ArrayList<>(legacy));
            if (!legacy.isEmpty()) {
                plugin.getLogger().info("Migradas " + legacy.size() + " ubicación(es) de la crate '" + id + "' a placements.yml.");
            }
            changed = true;
        }
        if (changed) savePlacementsFile();
    }

    private void savePlacementsFile() {
        try {
            placementsYaml.save(placementsFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar placements.yml", ex);
        }
    }

    private void saveFile() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar crates.yml", ex);
        }
    }
}
