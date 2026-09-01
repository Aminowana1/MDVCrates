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
    private final ConfigurationSection viewerSection;
    private final ConfigurationSection nameDisplaySection;
    private final ConfigurationSection belowNameDisplaySection;
    private final ConfigurationSection broadcastSection;

    public CrateDefinition(String id, boolean enabled, String displayName, Material blockMaterial,
            KeyDefinition key, List<BlockKey> locations, List<Reward> rewards,
            ConfigurationSection animationSection, ConfigurationSection viewerSection,
            ConfigurationSection nameDisplaySection, ConfigurationSection belowNameDisplaySection,
            ConfigurationSection broadcastSection) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
        this.blockMaterial = blockMaterial;
        this.key = key;
        this.locations = Collections.unmodifiableList(new ArrayList<>(locations));
        this.rewards = Collections.unmodifiableList(new ArrayList<>(rewards));
        this.animationSection = animationSection;
        this.viewerSection = viewerSection;
        this.nameDisplaySection = nameDisplaySection;
        this.belowNameDisplaySection = belowNameDisplaySection;
        this.broadcastSection = broadcastSection;
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    public String displayName() {
        return displayName;
    }

    public Material blockMaterial() {
        return blockMaterial;
    }

    public KeyDefinition key() {
        return key;
    }

    public List<BlockKey> locations() {
        return locations;
    }

    public List<Reward> rewards() {
        return rewards;
    }

    public ConfigurationSection animationSection() {
        return animationSection;
    }

    public ConfigurationSection viewerSection() {
        return viewerSection;
    }

    public ConfigurationSection nameDisplaySection() {
        return nameDisplaySection;
    }

    public ConfigurationSection belowNameDisplaySection() {
        return belowNameDisplaySection;
    }

    public ConfigurationSection broadcastSection() {
        return broadcastSection;
    }
}
