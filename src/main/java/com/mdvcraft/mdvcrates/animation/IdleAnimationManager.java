package com.mdvcraft.mdvcrates.animation;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.model.BlockKey;
import com.mdvcraft.mdvcrates.model.CrateDefinition;
import com.mdvcraft.mdvcrates.service.CrateManager;
import com.mdvcraft.mdvcrates.service.OpeningManager;
import com.mdvcraft.mdvcrates.util.ParticleSpec;
import com.mdvcraft.mdvcrates.util.VecMath;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Random;

public final class IdleAnimationManager {
    private final MDVCratesPlugin plugin;
    private final CrateManager crates;
    private final OpeningManager openings;
    private BukkitTask task;
    private long elapsedTicks;

    public IdleAnimationManager(MDVCratesPlugin plugin, CrateManager crates, OpeningManager openings) {
        this.plugin = plugin;
        this.crates = crates;
        this.openings = openings;
    }

    public void start() {
        stop();
        long interval = Math.max(1, plugin.getConfig().getLong("idle-engine.tick-interval", 2));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(interval), 1L, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void tick(long interval) {
        elapsedTicks += interval;
        double maxView = plugin.getConfig().getDouble("idle-engine.max-view-distance", 24.0);
        for (Map.Entry<BlockKey, String> entry : crates.physicalCrates()) {
            BlockKey key = entry.getKey();
            if (openings.isLocked(key)) continue;
            CrateDefinition crate = plugin.crateRepository().get(entry.getValue());
            if (crate == null || !crate.enabled()) continue;
            Block block = crates.resolve(key);
            if (block == null || block.getType() != crate.blockMaterial()) continue;
            World world = block.getWorld();
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            if (world.getNearbyPlayers(center, maxView).isEmpty()) continue;
            ConfigurationSection idle = crate.animationSection() == null ? null : crate.animationSection().getConfigurationSection("idle");
            if (idle == null || !idle.getBoolean("enabled", true)) continue;
            drawRings(world, center, idle.getConfigurationSection("rings"));
            drawOrbits(world, center, idle.getConfigurationSection("orbits"), key);
        }
    }

    private void drawRings(World world, Location base, ConfigurationSection rings) {
        if (rings == null) return;
        for (String id : rings.getKeys(false)) {
            ConfigurationSection s = rings.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true)) continue;
            ParticleSpec particle = ParticleSpec.of(s.getString("particle", "END_ROD"), s.getInt("count", 1),
                    s.getDouble("spread", 0), s.getDouble("extra", 0), s.getConfigurationSection("data"));
            double radius = s.getDouble("radius", 1.0);
            double yOffset = s.getDouble("y-offset", 0.5);
            int points = Math.max(3, s.getInt("points", 18));
            double phaseSpeed = s.getDouble("phase-speed-deg-per-tick", 2.0);

            double tiltX = Math.toRadians(s.getDouble("tilt-deg.x", 0));
            double tiltY = Math.toRadians(s.getDouble("tilt-deg.y", 0));
            double tiltZ = Math.toRadians(s.getDouble("tilt-deg.z", 0));
            double rotX = Math.toRadians(s.getDouble("plane-rotation-deg-per-tick.x", 0) * elapsedTicks);
            double rotY = Math.toRadians(s.getDouble("plane-rotation-deg-per-tick.y", 0) * elapsedTicks);
            double rotZ = Math.toRadians(s.getDouble("plane-rotation-deg-per-tick.z", 0) * elapsedTicks);
            double phase = Math.toRadians(phaseSpeed * elapsedTicks);

            for (int i = 0; i < points; i++) {
                double angle = phase + Math.PI * 2.0 * i / points;
                Vector local = new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                Vector v = VecMath.rotateXYZ(local, tiltX + rotX, tiltY + rotY, tiltZ + rotZ);
                particle.spawn(world, base.getX() + v.getX(), base.getY() + yOffset + v.getY(), base.getZ() + v.getZ());
            }
        }
    }

    private void drawOrbits(World world, Location base, ConfigurationSection orbits, BlockKey key) {
        if (orbits == null) return;
        for (String id : orbits.getKeys(false)) {
            ConfigurationSection s = orbits.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true)) continue;
            ParticleSpec particle = ParticleSpec.of(s.getString("particle", "ENCHANT"), s.getInt("count", 1),
                    s.getDouble("spread", 0), s.getDouble("extra", 0), s.getConfigurationSection("data"));
            int orbiters = Math.max(1, s.getInt("orbiters", 1));
            double radius = s.getDouble("radius", 1.25);
            double yOffset = s.getDouble("y-offset", 0.5);
            double angular = Math.toRadians(s.getDouble("angular-speed-deg-per-tick", 4.0) * elapsedTicks);

            double tiltX = Math.toRadians(s.getDouble("tilt-deg.x", 0));
            double tiltY = Math.toRadians(s.getDouble("tilt-deg.y", 0));
            double tiltZ = Math.toRadians(s.getDouble("tilt-deg.z", 0));
            if (s.getBoolean("random-plane", true)) {
                Random random = new Random((key.serialize() + ":" + id).hashCode());
                tiltX += Math.toRadians(random.nextDouble() * 130 - 65);
                tiltY += Math.toRadians(random.nextDouble() * 180);
                tiltZ += Math.toRadians(random.nextDouble() * 130 - 65);
            }
            tiltX += Math.toRadians(s.getDouble("plane-rotation-deg-per-tick.x", 0) * elapsedTicks);
            tiltY += Math.toRadians(s.getDouble("plane-rotation-deg-per-tick.y", 0) * elapsedTicks);
            tiltZ += Math.toRadians(s.getDouble("plane-rotation-deg-per-tick.z", 0) * elapsedTicks);

            for (int i = 0; i < orbiters; i++) {
                double angle = angular + Math.PI * 2.0 * i / orbiters;
                Vector local = new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                Vector v = VecMath.rotateXYZ(local, tiltX, tiltY, tiltZ);
                particle.spawn(world, base.getX() + v.getX(), base.getY() + yOffset + v.getY(), base.getZ() + v.getZ());
            }
        }
    }
}
