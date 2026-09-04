package com.mdvcraft.mdvcrates.model;

import org.bukkit.configuration.ConfigurationSection;

public final class BroadcastDefinition {
    private final boolean enabled;
    private final String text;
    private final String sound;
    private final boolean soundEnabled;

    public BroadcastDefinition(boolean enabled, String text, String sound, boolean soundEnabled) {
        this.enabled = enabled;
        this.text = text;
        this.sound = sound;
        this.soundEnabled = soundEnabled;
    }

    public static BroadcastDefinition of(ConfigurationSection section) {
        if (section == null)
            return new BroadcastDefinition(false, "", "", false);
        
        boolean enabled = section.getBoolean("enabled", false);
        String text = section.getString("text", "");
        String sound = section.getString("sound", "ENTITY_PLAYER_LEVELUP");
        boolean soundEnabled = section.getBoolean("sound-enabled", false);
        
        return new BroadcastDefinition(enabled, text, sound, soundEnabled);
    }

    public boolean enabled() { return enabled; }
    public String text() { return text; }
    public String sound() { return sound; }
    public boolean soundEnabled() { return soundEnabled; }
}
