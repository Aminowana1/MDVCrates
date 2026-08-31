package com.mdvcraft.mdvcrates.config;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.hook.MMOItemsHook;
import com.mdvcraft.mdvcrates.model.*;
import com.mdvcraft.mdvcrates.util.ItemStackCodec;
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
    private YamlConfiguration yaml;
    private final Map<String, CrateDefinition> crates = new LinkedHashMap<>();

    public CrateRepository(MDVCratesPlugin plugin, MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.mmoItems = mmoItems;
        this.file = new File(plugin.getDataFolder(), "crates.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) plugin.saveResource("crates.yml", false);
        yaml = YamlConfiguration.loadConfiguration(file);
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
        yaml.set(path + ".key.mmoitems-type", "LLAVES");
        yaml.set(path + ".key.mmoitems-id", "LLAVE_" + id.toUpperCase(Locale.ROOT));
        yaml.set(path + ".locations", new ArrayList<String>());
        yaml.createSection(path + ".rewards");
        saveFile();
        reload();
        return true;
    }

    public synchronized void setLocations(String crateId, List<BlockKey> locations) {
        List<String> raw = locations.stream().map(BlockKey::serialize).toList();
        yaml.set("crates." + crateId + ".locations", raw);
        saveFile();
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
        if (block == null || !block.isBlock()) block = Material.CHEST;

        KeyDefinition key = new KeyDefinition(
                sec.getString("key.mmoitems-type", "LLAVES"),
                sec.getString("key.mmoitems-id", "LLAVE_" + id.toUpperCase(Locale.ROOT)));

        List<BlockKey> locations = new ArrayList<>();
        for (String raw : sec.getStringList("locations")) {
            BlockKey keyLoc = BlockKey.deserialize(raw);
            if (keyLoc != null) locations.add(keyLoc);
        }

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

        ConfigurationSection animations = sec.getConfigurationSection("animations");
        if (animations == null) animations = plugin.getConfig().getConfigurationSection("default-animations");
        return new CrateDefinition(id, enabled, displayName, block, key, locations, rewards, animations);
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
        int amount = Math.max(1, rs.getInt("amount", 1));
        Reward.Builder b = Reward.builder(id, type).weight(weight).amount(amount)
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

    private void saveFile() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar crates.yml", ex);
        }
    }
}
