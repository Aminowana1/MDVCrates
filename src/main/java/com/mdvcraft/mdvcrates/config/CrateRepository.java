package com.mdvcraft.mdvcrates.config;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.hook.MMOItemsHook;
import com.mdvcraft.mdvcrates.model.*;
import com.mdvcraft.mdvcrates.util.CrateBlocks;
import com.mdvcraft.mdvcrates.util.ItemStackCodec;
import com.mdvcraft.mdvcrates.util.Text;
import com.mdvcraft.mdvcrates.util.VanillaItemUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

public final class CrateRepository {
    private final MDVCratesPlugin plugin;
    private final MMOItemsHook mmoItems;
    private final File cratesDirectory;
    private final File legacyFile;
    private final File placementsFile;
    private YamlConfiguration placementsYaml;
    private final Map<String, CrateDefinition> crates = new LinkedHashMap<>();
    private final Map<String, CrateFile> crateFiles = new LinkedHashMap<>();

    public CrateRepository(MDVCratesPlugin plugin, MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.mmoItems = mmoItems;
        this.cratesDirectory = new File(plugin.getDataFolder(), "crates");
        this.legacyFile = new File(plugin.getDataFolder(), "crates.yml");
        this.placementsFile = new File(plugin.getDataFolder(), "placements.yml");
        reload();
    }

    public synchronized void reload() {
        if (!plugin.getDataFolder().exists())
            plugin.getDataFolder().mkdirs();
        if (!cratesDirectory.exists() && !cratesDirectory.mkdirs()) {
            plugin.getLogger().severe("No se pudo crear la carpeta crates/.");
        }
        if (!placementsFile.exists())
            plugin.saveResource("placements.yml", false);
        placementsYaml = YamlConfiguration.loadConfiguration(placementsFile);

        // 1.2.0: cada crate vive en plugins/MDVCrates/crates/<id>.yml.
        // Si existe el antiguo crates.yml, se separa automáticamente y luego se
        // conserva como backup para que no se pierda nada.
        migrateLegacyCratesFile();
        ensureExampleCrate();

        crates.clear();
        crateFiles.clear();

        for (File crateFile : listCrateFiles()) {
            String id = stripYamlExtension(crateFile.getName());
            if (id.isBlank())
                continue;

            YamlConfiguration crateYaml = YamlConfiguration.loadConfiguration(crateFile);
            boolean changed = false;
            changed |= migrateLegacyLocations(id, crateYaml);
            changed |= migratePlainVanillaItemRewards(id, crateYaml);
            if (changed)
                saveCrateFile(crateFile, crateYaml);

            CrateDefinition def = parse(id, crateYaml);
            String key = id.toLowerCase(Locale.ROOT);
            if (crateFiles.containsKey(key)) {
                plugin.getLogger().warning("Hay más de un archivo de crate con la ID '" + id + "'. Se ignorará "
                        + crateFile.getName() + ".");
                continue;
            }
            crateFiles.put(key, new CrateFile(crateFile, crateYaml));
            crates.put(key, def);
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
        if (id == null || id.isBlank() || exists(id))
            return false;
        if (!cratesDirectory.exists())
            cratesDirectory.mkdirs();

        File file = new File(cratesDirectory, id + ".yml");
        if (file.exists())
            return false;

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("display-name", "&6&l" + id);
        yaml.set("block-material", "CHEST");
        yaml.set("key.mmoitems-type", "LLAVE");
        yaml.set("key.mmoitems-id", "LLAVE_" + id.toUpperCase(Locale.ROOT));
        yaml.set("viewer.show-percentages", true);
        yaml.set("name-display.enabled", true);
        yaml.set("name-display.text", "{display-name}");
        yaml.set("name-display.offset.x", 0.5);
        yaml.set("name-display.offset.y", 1.85);
        yaml.set("name-display.offset.z", 0.5);
        yaml.set("name-display.hide-during-opening", false);
        yaml.createSection("rewards");
        if (!saveCrateFile(file, yaml))
            return false;

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
        CrateFile crateFile = crateFiles.get(crateId.toLowerCase(Locale.ROOT));
        if (crateFile == null) {
            plugin.getLogger()
                    .warning("No se pudo guardar rewards: no existe el archivo de la crate '" + crateId + "'.");
            return;
        }

        YamlConfiguration yaml = crateFile.yaml();
        String rewardsPath = "rewards";
        ConfigurationSection sec = yaml.getConfigurationSection(rewardsPath);
        if (sec == null)
            sec = yaml.createSection(rewardsPath);

        // Las recompensas COMMAND solo se editan manualmente en el YAML de esta crate.
        for (String key : new HashSet<>(sec.getKeys(false))) {
            String type = sec.getString(key + ".type", "ITEM");
            if (!"COMMAND".equalsIgnoreCase(type))
                sec.set(key, null);
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
            if (reward.chance() != null)
                yaml.set(p + ".chance", reward.chance());
            else
                yaml.set(p + ".chance", null);
            yaml.set(p + ".amount", reward.amount());
            if (reward.displayName() != null && !reward.displayName().isBlank()) {
                yaml.set(p + ".name", reward.displayName());
            }

            if (reward.type() == RewardType.MMOITEM) {
                yaml.set(p + ".mmoitems-type", reward.mmoItemsType());
                yaml.set(p + ".mmoitems-id", reward.mmoItemsId());
            } else if (reward.type() == RewardType.VANILLA && reward.vanillaMaterial() != null) {
                yaml.set(p + ".material", reward.vanillaMaterial().name());
            } else if (reward.type() == RewardType.ITEM && reward.storedItem() != null) {
                yaml.set(p + ".item.format", "BUKKIT_BYTES_BASE64");
                yaml.set(p + ".item.data", ItemStackCodec.encode(reward.storedItem()));
            }
        }

        if (saveCrateFile(crateFile.file(), yaml))
            reload();
    }

    /** Devuelve la raíz del YAML individual de la crate. */
    public ConfigurationSection rawSection(String crateId) {
        CrateFile crateFile = crateId == null ? null : crateFiles.get(crateId.toLowerCase(Locale.ROOT));
        return crateFile == null ? null : crateFile.yaml();
    }

    private CrateDefinition parse(String id, ConfigurationSection sec) {
        boolean enabled = sec.getBoolean("enabled", true);
        String displayName = sec.getString("display-name", id);
        Material block = Material.matchMaterial(sec.getString("block-material", "CHEST"));
        if (!CrateBlocks.isSupported(block)) {
            plugin.getLogger().warning("Crate '" + id
                    + "' usa block-material no soportado; se usará CHEST. Soportados: CHEST, TRAPPED_CHEST, ENDER_CHEST y SHULKER_BOX de cualquier color.");
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
                if (rs == null)
                    continue;
                Reward reward = parseReward(id, rewardId, rs);
                if (reward != null)
                    rewards.add(reward);
            }
        }

        double explicitChanceTotal = rewards.stream().filter(Reward::hasExplicitChance).mapToDouble(r -> r.chance())
                .sum();
        if (explicitChanceTotal > 100.0001) {
            plugin.getLogger().warning("Crate '" + id + "' suma " + explicitChanceTotal
                    + "% en chance explícito. MDVCrates normalizará esas probabilidades a 100% y los rewards solo por peso quedarán en 0%.");
        }

        ConfigurationSection animations = sec.getConfigurationSection("animations");
        if (animations == null)
            animations = plugin.getConfig().getConfigurationSection("default-animations");
        ConfigurationSection viewer = sec.getConfigurationSection("viewer");
        if (viewer == null)
            viewer = plugin.getConfig().getConfigurationSection("default-viewer");
        ConfigurationSection nameDisplay = sec.getConfigurationSection("name-display");
        if (nameDisplay == null)
            nameDisplay = plugin.getConfig().getConfigurationSection("default-name-display");
        ConfigurationSection belowNameDisplay = sec.getConfigurationSection("below-name-display");
        if (belowNameDisplay == null)
            belowNameDisplay = plugin.getConfig().getConfigurationSection("below-name-display");
        ConfigurationSection broadcast = sec.getConfigurationSection("broadcast");
        if (broadcast == null)
            broadcast = plugin.getConfig().getConfigurationSection("default-broadcast");
        return new CrateDefinition(id, enabled, displayName, block, key, locations, rewards, animations, viewer,
                nameDisplay, belowNameDisplay, broadcast);
    }

    private Reward parseReward(String crateId, String id, ConfigurationSection rs) {
        RewardType type;
        try {
            type = RewardType.valueOf(rs.getString("type", "ITEM").toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            plugin.getLogger().warning("Reward '" + crateId + ":" + id + "' tiene type inválido.");
            return null;
        }
        double weight = Math.max(0, rs.getDouble("weight", 1.0));

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
            case VANILLA -> {
                Material material = Material.matchMaterial(rs.getString("material", ""));
                if (material == null || material.isAir() || !material.isItem()) {
                    plugin.getLogger().warning("Reward VANILLA '" + crateId + ":" + id + "' usa material inválido.");
                    return null;
                }
                b.vanillaMaterial(material);
            }
            case ITEM -> {
                String encoded = rs.getString("item.data");
                ItemStack item = ItemStackCodec.decode(encoded);
                if (item == null) {
                    plugin.getLogger().warning("Reward ITEM '" + crateId + ":" + id + "' no pudo deserializarse.");
                    return null;
                }
                b.storedItem(item);
            }
            case COMMAND -> {
                b.commands(rs.getStringList("commands"));
                b.commandPreview(buildPreview(rs.getConfigurationSection("preview"),
                        rs.getString("name", "&6Recompensa especial")));
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
            if (parsed != null && parsed.isItem())
                material = parsed;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(sec == null ? fallbackName : sec.getString("name", fallbackName)));
            if (sec != null)
                meta.setLore(Text.color(sec.getStringList("lore")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<BlockKey> loadLocations(String crateId) {
        List<BlockKey> locations = new ArrayList<>();
        for (String raw : placementsYaml.getStringList("placements." + crateId)) {
            BlockKey keyLoc = BlockKey.deserialize(raw);
            if (keyLoc != null)
                locations.add(keyLoc);
        }
        return locations;
    }

    private boolean migrateLegacyLocations(String crateId, YamlConfiguration crateYaml) {
        String placementPath = "placements." + crateId;
        boolean crateChanged = false;

        if (!placementsYaml.contains(placementPath)) {
            List<String> legacy = crateYaml.getStringList("locations");
            placementsYaml.set(placementPath, new ArrayList<>(legacy));
            if (!legacy.isEmpty()) {
                plugin.getLogger().info(
                        "Migradas " + legacy.size() + " ubicación(es) de la crate '" + crateId + "' a placements.yml.");
            }
            savePlacementsFile();
        }

        if (crateYaml.contains("locations")) {
            crateYaml.set("locations", null);
            crateChanged = true;
        }
        return crateChanged;
    }

    /**
     * Convierte automáticamente los antiguos ITEM/Base64 que realmente son un
     * stack vanilla sin metadata a VANILLA + material. Los ItemStack custom se
     * mantienen intactos en Base64.
     */
    private boolean migratePlainVanillaItemRewards(String crateId, YamlConfiguration crateYaml) {
        ConfigurationSection rewards = crateYaml.getConfigurationSection("rewards");
        if (rewards == null)
            return false;

        boolean changed = false;
        int migrated = 0;
        for (String rewardId : rewards.getKeys(false)) {
            ConfigurationSection rs = rewards.getConfigurationSection(rewardId);
            if (rs == null || !"ITEM".equalsIgnoreCase(rs.getString("type", "ITEM")))
                continue;

            ItemStack item = ItemStackCodec.decode(rs.getString("item.data"));
            if (!VanillaItemUtil.isPlainVanilla(item))
                continue;

            rs.set("type", "VANILLA");
            rs.set("material", item.getType().name());
            rs.set("item", null);
            changed = true;
            migrated++;
        }
        if (migrated > 0) {
            plugin.getLogger().info("Crate '" + crateId + "': migradas " + migrated
                    + " recompensa(s) vanilla de Base64 a material simple.");
        }
        return changed;
    }

    private void migrateLegacyCratesFile() {
        if (!legacyFile.exists() || !legacyFile.isFile())
            return;

        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection root = legacy.getConfigurationSection("crates");
        if (root == null) {
            plugin.getLogger()
                    .warning("Existe crates.yml pero no contiene la sección 'crates'. No se migrará automáticamente.");
            return;
        }

        int migrated = 0;
        boolean failed = false;
        for (String id : root.getKeys(false)) {
            ConfigurationSection source = root.getConfigurationSection(id);
            if (source == null)
                continue;

            File destination = new File(cratesDirectory, id + ".yml");
            if (destination.exists())
                continue;

            YamlConfiguration target = new YamlConfiguration();
            copySection(source, target);
            if (saveCrateFile(destination, target))
                migrated++;
            else
                failed = true;
        }

        if (failed) {
            plugin.getLogger().warning(
                    "La migración de crates.yml no terminó correctamente. Se conserva el archivo original para reintentar.");
            return;
        }

        File backup = nextLegacyBackupFile();
        try {
            Files.move(legacyFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Migración 1.2.0 completada: " + migrated
                    + " crate(s) separadas en crates/. Backup: " + backup.getName());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Las crates se migraron, pero no se pudo renombrar crates.yml como backup. No se borró ningún dato.",
                    ex);
        }
    }

    private void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            if (source.isConfigurationSection(key)) {
                ConfigurationSection childSource = source.getConfigurationSection(key);
                ConfigurationSection childTarget = target.createSection(key);
                if (childSource != null)
                    copySection(childSource, childTarget);
            } else {
                target.set(key, source.get(key));
            }
        }
    }

    private void ensureExampleCrate() {
        if (!listCrateFiles().isEmpty())
            return;
        try {
            plugin.saveResource("crates/caja1.yml", false);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("No se encontró el recurso crates/caja1.yml dentro del JAR.");
        }
    }

    private List<File> listCrateFiles() {
        File[] files = cratesDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0)
            return List.of();
        return Arrays.stream(files)
                .filter(File::isFile)
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String stripYamlExtension(String name) {
        if (name == null)
            return "";
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
    }

    private File nextLegacyBackupFile() {
        File preferred = new File(plugin.getDataFolder(), "crates.yml.migrated-backup");
        if (!preferred.exists())
            return preferred;
        int i = 2;
        while (true) {
            File candidate = new File(plugin.getDataFolder(), "crates.yml.migrated-backup-" + i);
            if (!candidate.exists())
                return candidate;
            i++;
        }
    }

    private void savePlacementsFile() {
        try {
            placementsYaml.save(placementsFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar placements.yml", ex);
        }
    }

    private boolean saveCrateFile(File file, YamlConfiguration yaml) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists())
                parent.mkdirs();
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar " + file.getPath(), ex);
            return false;
        }
    }

    private record CrateFile(File file, YamlConfiguration yaml) {
    }
}
