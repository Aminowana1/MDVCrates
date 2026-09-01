package com.mdvcraft.mdvcrates.util;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class ParticleSpec {
    private final Particle particle;
    private final int count;
    private final double spread;
    private final double extra;
    private final Object data;

    public ParticleSpec(Particle particle, int count, double spread, double extra, Object data) {
        this.particle = particle;
        this.count = Math.max(0, count);
        this.spread = Math.max(0, spread);
        this.extra = extra;
        this.data = data;
    }

    public Particle particle() { return particle; }

    public void spawn(World world, double x, double y, double z) {
        try {
            if (data == null) {
                world.spawnParticle(particle, x, y, z, count, spread, spread, spread, extra);
            } else {
                world.spawnParticle((Particle) particle, x, y, z, count, spread, spread, spread, extra, data);
            }
        } catch (Throwable ignored) {
            world.spawnParticle(Particle.END_ROD, x, y, z, Math.max(1, count), spread, spread, spread, 0.0);
        }
    }

    public static ParticleSpec from(ConfigurationSection section, String pathPrefix, String fallbackParticle) {
        String name = section == null ? fallbackParticle : section.getString(pathPrefix + "particle", fallbackParticle);
        int count = section == null ? 1 : section.getInt(pathPrefix + "count", 1);
        double spread = section == null ? 0.0 : section.getDouble(pathPrefix + "spread", 0.0);
        double extra = configuredSpeed(section, pathPrefix + "particle-speed", pathPrefix + "extra", 0.0);
        ConfigurationSection dataSec = section == null ? null : section.getConfigurationSection(pathPrefix + "data");
        return of(name, count, spread, extra, dataSec);
    }


    /**
     * Lee la velocidad propia de la partícula. Desde 1.2.1 `particle-speed` es
     * el nombre recomendado. `extra` se conserva como alias compatible con
     * configuraciones anteriores. En Bukkit este valor corresponde al parámetro
     * extra/speed de spawnParticle; no controla la duración de vida del cliente.
     */
    public static double configuredSpeed(ConfigurationSection section, String speedPath, String legacyExtraPath, double fallback) {
        if (section == null) return fallback;
        if (speedPath != null && section.contains(speedPath)) return section.getDouble(speedPath, fallback);
        if (legacyExtraPath != null && section.contains(legacyExtraPath)) return section.getDouble(legacyExtraPath, fallback);
        return fallback;
    }

    public static ParticleSpec of(String name, int count, double spread, double extra, ConfigurationSection dataSec) {
        Particle p;
        try {
            p = Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            p = Particle.END_ROD;
        }
        Object data = createData(p, dataSec);
        return new ParticleSpec(p, count, spread, extra, data);
    }

    private static Object createData(Particle p, ConfigurationSection sec) {
        try {
            Class<?> type = p.getDataType();
            if (type == Void.class) return null;
            if (type == Color.class) {
                int r = sec == null ? 255 : sec.getInt("red", 255);
                int g = sec == null ? 255 : sec.getInt("green", 255);
                int b = sec == null ? 255 : sec.getInt("blue", 255);
                return Color.fromRGB(clamp(r), clamp(g), clamp(b));
            }
            if (type == Particle.DustOptions.class) {
                int r = sec == null ? 255 : sec.getInt("red", 255);
                int g = sec == null ? 255 : sec.getInt("green", 255);
                int b = sec == null ? 255 : sec.getInt("blue", 255);
                float size = (float) (sec == null ? 1.0 : sec.getDouble("size", 1.0));
                return new Particle.DustOptions(Color.fromRGB(clamp(r), clamp(g), clamp(b)), size);
            }
            if (type == Particle.DustTransition.class) {
                Color from = Color.fromRGB(
                        clamp(sec == null ? 255 : sec.getInt("from-red", 255)),
                        clamp(sec == null ? 255 : sec.getInt("from-green", 255)),
                        clamp(sec == null ? 255 : sec.getInt("from-blue", 255)));
                Color to = Color.fromRGB(
                        clamp(sec == null ? 0 : sec.getInt("to-red", 0)),
                        clamp(sec == null ? 255 : sec.getInt("to-green", 255)),
                        clamp(sec == null ? 255 : sec.getInt("to-blue", 255)));
                float size = (float) (sec == null ? 1.0 : sec.getDouble("size", 1.0));
                return new Particle.DustTransition(from, to, size);
            }
            if (BlockData.class.isAssignableFrom(type)) {
                Material mat = parseMaterial(sec == null ? null : sec.getString("material"), Material.STONE);
                return mat.createBlockData();
            }
            if (ItemStack.class.isAssignableFrom(type)) {
                Material mat = parseMaterial(sec == null ? null : sec.getString("material"), Material.DIAMOND);
                return new ItemStack(mat);
            }
            if (type == Float.class || type == float.class) {
                return (float) (sec == null ? 0.0 : sec.getDouble("value", 0.0));
            }
            if (type == Integer.class || type == int.class) {
                return sec == null ? 0 : sec.getInt("value", 0);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static Material parseMaterial(String raw, Material fallback) {
        if (raw == null) return fallback;
        Material m = Material.matchMaterial(raw);
        return m == null ? fallback : m;
    }
}
