package com.mdvcraft.mdvcrates.animation;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.model.BlockKey;
import com.mdvcraft.mdvcrates.model.CrateDefinition;
import com.mdvcraft.mdvcrates.service.CrateManager;
import com.mdvcraft.mdvcrates.service.OpeningManager;
import com.mdvcraft.mdvcrates.util.ParticleSpec;
import com.mdvcraft.mdvcrates.util.Text;
import com.mdvcraft.mdvcrates.util.VecMath;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class IdleAnimationManager {
    private final MDVCratesPlugin plugin;
    private final CrateManager crates;
    private final OpeningManager openings;
    private final Map<BlockKey, TextDisplay> nameDisplays = new HashMap<>();
    private BukkitTask task;
    private long elapsedTicks;
    private long engineInterval = 1;

    public IdleAnimationManager(MDVCratesPlugin plugin, CrateManager crates, OpeningManager openings) {
        this.plugin = plugin;
        this.crates = crates;
        this.openings = openings;
    }

    public void start() {
        stop();
        elapsedTicks = 0;
        engineInterval = Math.max(1, plugin.getConfig().getLong("idle-engine.tick-interval", 1));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(engineInterval), 1L, engineInterval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (TextDisplay display : new ArrayList<>(nameDisplays.values())) removeDisplay(display);
        nameDisplays.clear();
    }

    private void tick(long interval) {
        long previousTicks = elapsedTicks;
        elapsedTicks += interval;
        double maxView = plugin.getConfig().getDouble("idle-engine.max-view-distance", 24.0);
        Set<BlockKey> visibleThisTick = new HashSet<>();

        for (Map.Entry<BlockKey, String> entry : crates.physicalCrates()) {
            BlockKey key = entry.getKey();
            CrateDefinition crate = plugin.crateRepository().get(entry.getValue());
            if (crate == null || !crate.enabled()) {
                removeName(key);
                continue;
            }
            Block block = crates.resolve(key);
            if (block == null || block.getType() != crate.blockMaterial()) {
                removeName(key);
                continue;
            }
            World world = block.getWorld();
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            if (world.getNearbyPlayers(center, maxView).isEmpty()) {
                removeName(key);
                continue;
            }
            visibleThisTick.add(key);

            // El nombre y las animaciones idle son independientes de la apertura.
            // Solo el nombre puede ocultarse durante opening si así lo pide la crate.
            syncNameDisplay(key, block, crate, openings.isLocked(key));

            ConfigurationSection idle = crate.animationSection() == null ? null : crate.animationSection().getConfigurationSection("idle");
            if (idle == null || !idle.getBoolean("enabled", true)) continue;
            drawRings(world, center, idle.getConfigurationSection("rings"), previousTicks);
            drawOrbits(world, center, idle.getConfigurationSection("orbits"), key, previousTicks);
            drawRandomPoints(world, center, idle.getConfigurationSection("random-points"), previousTicks);
        }

        // Elimina hologramas de crates que ya no están cargadas/visibles/registradas.
        for (BlockKey key : new ArrayList<>(nameDisplays.keySet())) {
            if (!visibleThisTick.contains(key)) removeName(key);
        }
    }

    private boolean shouldRun(ConfigurationSection section, long previousTicks) {
        int effectInterval = Math.max(1, section.getInt("interval-ticks", 1));
        return elapsedTicks / effectInterval != previousTicks / effectInterval;
    }

    private void drawRings(World world, Location base, ConfigurationSection rings, long previousTicks) {
        if (rings == null) return;
        for (String id : rings.getKeys(false)) {
            ConfigurationSection s = rings.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true) || !shouldRun(s, previousTicks)) continue;
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

    private void drawOrbits(World world, Location base, ConfigurationSection orbits, BlockKey key, long previousTicks) {
        if (orbits == null) return;
        for (String id : orbits.getKeys(false)) {
            ConfigurationSection s = orbits.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true) || !shouldRun(s, previousTicks)) continue;
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

    /**
     * Nuevo efecto idle: genera puntos aleatorios dentro de un volumen alrededor
     * de la crate. Cada efecto tiene su propio interval-ticks y points-per-spawn.
     */
    private void drawRandomPoints(World world, Location base, ConfigurationSection randomPoints, long previousTicks) {
        if (randomPoints == null) return;
        for (String id : randomPoints.getKeys(false)) {
            ConfigurationSection s = randomPoints.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true) || !shouldRun(s, previousTicks)) continue;

            ParticleSpec particle = ParticleSpec.of(s.getString("particle", "ENCHANT"), s.getInt("count", 1),
                    s.getDouble("spread", 0), s.getDouble("extra", 0), s.getConfigurationSection("data"));
            int points = Math.max(0, s.getInt("points-per-spawn", 1));
            double radius = Math.max(0, s.getDouble("radius", 1.5));
            double verticalRadius = Math.max(0, s.getDouble("vertical-radius", radius));
            double yOffset = s.getDouble("center-y-offset", 0.5);
            boolean surfaceOnly = s.getBoolean("surface-only", false);
            String shape = s.getString("shape", "SPHERE").toUpperCase(Locale.ROOT);

            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < points; i++) {
                Vector offset;
                if (shape.equals("CYLINDER")) {
                    double angle = random.nextDouble(Math.PI * 2.0);
                    double radial = radius * (surfaceOnly ? 1.0 : Math.sqrt(random.nextDouble()));
                    double y = verticalRadius <= 0 ? 0 : random.nextDouble(-verticalRadius, verticalRadius);
                    offset = new Vector(Math.cos(angle) * radial, y, Math.sin(angle) * radial);
                } else {
                    double u = random.nextDouble(-1.0, 1.0);
                    double theta = random.nextDouble(Math.PI * 2.0);
                    double rxy = Math.sqrt(1 - u * u);
                    double radial = surfaceOnly ? 1.0 : Math.cbrt(random.nextDouble());
                    offset = new Vector(
                            Math.cos(theta) * rxy * radius * radial,
                            u * verticalRadius * radial,
                            Math.sin(theta) * rxy * radius * radial);
                }
                particle.spawn(world, base.getX() + offset.getX(), base.getY() + yOffset + offset.getY(), base.getZ() + offset.getZ());
            }
        }
    }

    private void syncNameDisplay(BlockKey key, Block block, CrateDefinition crate, boolean opening) {
        ConfigurationSection s = crate.nameDisplaySection();
        if (s == null || !s.getBoolean("enabled", true) || (opening && s.getBoolean("hide-during-opening", false))) {
            removeName(key);
            return;
        }

        TextDisplay display = nameDisplays.get(key);
        Location loc = block.getLocation().add(
                s.getDouble("offset.x", 0.5),
                s.getDouble("offset.y", 1.85),
                s.getDouble("offset.z", 0.5));
        String rawText = s.getString("text", "{display-name}");
        String text = Text.color(rawText
                .replace("{display-name}", crate.displayName())
                .replace("{crate}", crate.id())
                .replace("{id}", crate.id()));

        if (display == null || !display.isValid() || display.getWorld() != block.getWorld()) {
            removeName(key);
            display = block.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setGravity(false);
                d.setInvulnerable(true);
                d.addScoreboardTag("mdvcrates_visual");
                d.addScoreboardTag("mdvcrates_name");
            });
            nameDisplays.put(key, display);
        } else if (display.getLocation().distanceSquared(loc) > 0.0001) {
            display.teleport(loc);
        }

        display.setText(text);
        display.setBillboard(parseBillboard(s.getString("billboard", "CENTER")));
        display.setShadowed(s.getBoolean("shadowed", true));
        display.setSeeThrough(s.getBoolean("see-through", false));
        display.setDefaultBackground(s.getBoolean("default-background", false));
        display.setLineWidth(Math.max(1, s.getInt("line-width", 200)));
        display.setViewRange((float) Math.max(0.1, s.getDouble("view-range", 1.0)));
    }

    private Display.Billboard parseBillboard(String raw) {
        try { return Display.Billboard.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return Display.Billboard.CENTER; }
    }

    private void removeName(BlockKey key) {
        TextDisplay display = nameDisplays.remove(key);
        removeDisplay(display);
    }

    private void removeDisplay(TextDisplay display) {
        if (display != null && display.isValid()) display.remove();
    }
}
