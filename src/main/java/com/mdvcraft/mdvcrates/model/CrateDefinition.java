package com.mdvcraft.mdvcrates.model;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CrateDefinition {
    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final Material blockMaterial;
    private final KeyDefinition key;
    private final List<BlockKey> locations;
    private final List<Reward> rewards;
    private final ConfigurationSection animationSection;

    public CrateDefinition(String id, boolean enabled, String displayName, Material blockMaterial,
                           KeyDefinition key, List<BlockKey> locations, List<Reward> rewards,
                           ConfigurationSection animationSection) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
        this.blockMaterial = blockMaterial;
        this.key = key;
        this.locations = Collections.unmodifiableList(new ArrayList<>(locations));
        this.rewards = Collections.unmodifiableList(new ArrayList<>(rewards));
        this.animationSection = animationSection;
    }

    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public String displayName() { return displayName; }
    public Material blockMaterial() { return blockMaterial; }
    public KeyDefinition key() { return key; }
    public List<BlockKey> locations() { return locations; }
    public List<Reward> rewards() { return rewards; }
    public ConfigurationSection animationSection() { return animationSection; }
}
