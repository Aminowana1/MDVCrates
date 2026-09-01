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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class IdleAnimationManager {
    private final MDVCratesPlugin plugin;
    private final CrateManager crates;
    private final OpeningManager openings;
    private final Map<BlockKey, TextDisplay> nameDisplays = new HashMap<>();
    private final Map<VisualKey, ItemDisplay> itemDisplays = new HashMap<>();
    private final Map<BlockKey, Long> openingStartTicks = new HashMap<>();
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
        for (ItemDisplay display : new ArrayList<>(itemDisplays.values())) removeDisplay(display);
        nameDisplays.clear();
        itemDisplays.clear();
        openingStartTicks.clear();
    }

    private void tick(long interval) {
        long previousTicks = elapsedTicks;
        elapsedTicks += interval;
        double maxView = plugin.getConfig().getDouble("idle-engine.max-view-distance", 24.0);
        Set<BlockKey> visibleThisTick = new HashSet<>();
        Set<VisualKey> itemVisualsThisTick = new HashSet<>();

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

            boolean opening = openings.isLocked(key);
            if (opening) openingStartTicks.putIfAbsent(key, elapsedTicks);
            else openingStartTicks.remove(key);

            // El nombre y las animaciones idle son independientes de la apertura.
            // Solo el nombre puede ocultarse durante opening si así lo pide la crate.
            syncNameDisplay(key, block, crate, opening);

            ConfigurationSection idle = crate.animationSection() == null ? null : crate.animationSection().getConfigurationSection("idle");
            if (idle == null || !idle.getBoolean("enabled", true)) continue;

            // 1.2.2: ItemDisplays estáticos, por ejemplo un cristal flotando encima.
            syncStaticItemDisplays(block, idle.getConfigurationSection("item-displays"), key, opening, itemVisualsThisTick);

            drawRings(world, center, idle.getConfigurationSection("rings"), previousTicks);
            drawOrbits(world, center, idle.getConfigurationSection("orbits"), key, opening, previousTicks, itemVisualsThisTick);
            drawRandomPoints(world, center, idle.getConfigurationSection("random-points"), previousTicks);
        }

        // Elimina hologramas de crates que ya no están cargadas/visibles/registradas.
        for (BlockKey key : new ArrayList<>(nameDisplays.keySet())) {
            if (!visibleThisTick.contains(key)) removeName(key);
        }

        // ItemDisplays idle son entidades temporales administradas por el plugin.
        // Si la crate deja de estar visible, se elimina el display y se recreará
        // cuando vuelva a haber un jugador cerca. Así no quedan entidades basura.
        for (VisualKey visualKey : new ArrayList<>(itemDisplays.keySet())) {
            if (!itemVisualsThisTick.contains(visualKey)) removeItemVisual(visualKey);
        }

        // Limpia estados de apertura que ya terminaron.
        openingStartTicks.keySet().removeIf(key -> !openings.isLocked(key));
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
                    s.getDouble("spread", 0), ParticleSpec.configuredSpeed(s, "particle-speed", "extra", 0.0),
                    s.getConfigurationSection("data"));
            double radius = s.getDouble("radius", 1.0);
            double yOffset = s.getDouble("y-offset", 0.5);
            int points = Math.max(3, s.getInt("points", 18));
            double phaseSpeed = s.getDouble("phase-speed-deg-per-tick", 2.0);

            // Desplazamiento fijo del centro del ring respecto al centro de la crate.
            double centerX = s.getDouble("center-offset.x", 0.0);
            double centerY = s.getDouble("center-offset.y", 0.0);
            double centerZ = s.getDouble("center-offset.z", 0.0);

            // Permite que TODO el ring orbite horizontalmente alrededor de la crate
            // sin modificar la orientación de su plano.
            ConfigurationSection centerOrbit = s.getConfigurationSection("center-orbit");
            if (centerOrbit != null && centerOrbit.getBoolean("enabled", false)) {
                double orbitRadius = Math.max(0.0, centerOrbit.getDouble("radius", 0.0));
                double orbitSpeed = centerOrbit.getDouble("speed-deg-per-tick", 0.0);
                double orbitPhase = centerOrbit.getDouble("phase-deg", 0.0);
                double orbitAngle = Math.toRadians(orbitPhase + orbitSpeed * elapsedTicks);
                centerX += Math.cos(orbitAngle) * orbitRadius;
                centerZ += Math.sin(orbitAngle) * orbitRadius;
                centerY += centerOrbit.getDouble("y-offset", 0.0);
            }

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
                particle.spawn(world,
                        base.getX() + centerX + v.getX(),
                        base.getY() + yOffset + centerY + v.getY(),
                        base.getZ() + centerZ + v.getZ());
            }
        }
    }

    /**
     * Orbits puede dibujar partículas como antes o mover ItemDisplays reales.
     * Si existe `item-display:` y está enabled, cada orbiter es UNA entidad que
     * se teletransporta por la trayectoria, en vez de crear partículas nuevas.
     */
    private void drawOrbits(World world, Location base, ConfigurationSection orbits, BlockKey key, boolean opening,
                            long previousTicks, Set<VisualKey> itemVisualsThisTick) {
        if (orbits == null) return;
        for (String id : orbits.getKeys(false)) {
            ConfigurationSection s = orbits.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true)) continue;

            ConfigurationSection displaySection = s.getConfigurationSection("item-display");
            boolean useItemDisplay = displaySection != null && displaySection.getBoolean("enabled", true);

            // Los ItemDisplays se actualizan en cada tick del motor para que el
            // movimiento sea continuo. interval-ticks sigue aplicando a partículas.
            if (!useItemDisplay && !shouldRun(s, previousTicks)) continue;

            int orbiters = Math.max(1, s.getInt("orbiters", 1));
            double radius = s.getDouble("radius", 1.25);
            double yOffset = s.getDouble("y-offset", 0.5);
            double phaseDeg = s.getDouble("phase-deg", 0.0);
            double angular = Math.toRadians(phaseDeg + s.getDouble("angular-speed-deg-per-tick", 4.0) * elapsedTicks);

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

            ParticleSpec particle = null;
            if (!useItemDisplay) {
                particle = ParticleSpec.of(s.getString("particle", "ENCHANT"), s.getInt("count", 1),
                        s.getDouble("spread", 0), ParticleSpec.configuredSpeed(s, "particle-speed", "extra", 0.0),
                        s.getConfigurationSection("data"));
            }

            for (int i = 0; i < orbiters; i++) {
                double angle = angular + Math.PI * 2.0 * i / orbiters;
                Vector local = new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                Vector v = VecMath.rotateXYZ(local, tiltX, tiltY, tiltZ);
                Location loc = new Location(world,
                        base.getX() + v.getX(),
                        base.getY() + yOffset + v.getY(),
                        base.getZ() + v.getZ());

                if (useItemDisplay) {
                    if (opening && displaySection.getBoolean("hide-during-opening", false)) continue;
                    loc = applyOpeningMovement(loc, displaySection, key);
                    VisualKey visualKey = new VisualKey(key, "orbit:" + id, i);
                    itemVisualsThisTick.add(visualKey);
                    syncItemDisplay(visualKey, loc, displaySection);
                } else if (particle != null) {
                    particle.spawn(world, loc.getX(), loc.getY(), loc.getZ());
                }
            }
        }
    }

    /**
     * ItemDisplays estáticos configurables por crate. El offset usa como origen
     * la esquina inferior del bloque, igual que name-display y roll.display-offset.
     */
    private void syncStaticItemDisplays(Block block, ConfigurationSection displays, BlockKey key, boolean opening,
                                        Set<VisualKey> itemVisualsThisTick) {
        if (displays == null) return;
        for (String id : displays.getKeys(false)) {
            ConfigurationSection s = displays.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true)) continue;
            if (opening && s.getBoolean("hide-during-opening", false)) continue;

            Location loc = block.getLocation().add(
                    s.getDouble("offset.x", 0.5),
                    s.getDouble("offset.y", 1.5),
                    s.getDouble("offset.z", 0.5));
            loc = applyOpeningMovement(loc, s, key);

            VisualKey visualKey = new VisualKey(key, "static:" + id, 0);
            itemVisualsThisTick.add(visualKey);
            syncItemDisplay(visualKey, loc, s);
        }
    }

    /**
     * Movimiento relativo activado mientras la crate está en opening.
     * Ejemplo: offset.y=1 y duration-ticks=20 eleva el display 1 bloque en 1 s.
     */
    private Location applyOpeningMovement(Location base, ConfigurationSection visualSection, BlockKey key) {
        ConfigurationSection movement = visualSection.getConfigurationSection("opening-movement");
        if (movement == null || !movement.getBoolean("enabled", false)) return base;

        Long openingStart = openingStartTicks.get(key);
        if (openingStart == null) return base;

        long age = Math.max(0L, elapsedTicks - openingStart);
        int delay = Math.max(0, movement.getInt("delay-ticks", 0));
        int duration = Math.max(1, movement.getInt("duration-ticks", 20));
        if (age <= delay) return base;

        double progress = Math.max(0.0, Math.min(1.0, (age - delay) / (double) duration));
        double eased = movementCurve(progress, movement.getString("curve", "LINEAR"));
        return base.add(
                movement.getDouble("offset.x", 0.0) * eased,
                movement.getDouble("offset.y", 0.0) * eased,
                movement.getDouble("offset.z", 0.0) * eased);
    }

    private double movementCurve(double t, String raw) {
        String curve = raw == null ? "LINEAR" : raw.toUpperCase(Locale.ROOT);
        return switch (curve) {
            case "EASE_IN_QUAD" -> t * t;
            case "EASE_OUT_QUAD" -> 1.0 - (1.0 - t) * (1.0 - t);
            case "EASE_IN_OUT_QUAD" -> t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
            case "EASE_OUT_CUBIC" -> 1.0 - Math.pow(1.0 - t, 3.0);
            default -> t;
        };
    }

    private void syncItemDisplay(VisualKey key, Location loc, ConfigurationSection s) {
        ItemDisplay display = itemDisplays.get(key);
        if (display == null || !display.isValid() || display.getWorld() != loc.getWorld()) {
            removeItemVisual(key);
            ItemStack stack = buildDisplayItem(s);
            if (stack == null || stack.getType().isAir()) return;

            display = loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setGravity(false);
                d.setInvulnerable(true);
                d.setBillboard(parseBillboard(s.getString("billboard", "FIXED")));
                d.setItemDisplayTransform(parseItemTransform(s.getString("transform", "FIXED")));
                d.setItemStack(stack);
                d.setViewRange((float) Math.max(0.1, s.getDouble("view-range", 1.0)));
                d.setTeleportDuration(Math.max(0, Math.min(59, s.getInt("teleport-duration-ticks", 1))));
                d.setShadowRadius((float) Math.max(0.0, s.getDouble("shadow-radius", 0.0)));
                d.setShadowStrength((float) Math.max(0.0, s.getDouble("shadow-strength", 0.0)));
                d.addScoreboardTag("mdvcrates_visual");
                d.addScoreboardTag("mdvcrates_item_visual");
                applyDisplayScale(d, s);
            });
            itemDisplays.put(key, display);
        } else {
            display.setBillboard(parseBillboard(s.getString("billboard", "FIXED")));
            display.setViewRange((float) Math.max(0.1, s.getDouble("view-range", 1.0)));
            display.setTeleportDuration(Math.max(0, Math.min(59, s.getInt("teleport-duration-ticks", 1))));
            applyDisplayScale(display, s);
            if (display.getLocation().distanceSquared(loc) > 0.000001) display.teleport(loc);
        }
    }

    private ItemStack buildDisplayItem(ConfigurationSection s) {
        String mmoType = s.getString("mmoitems-type");
        String mmoId = s.getString("mmoitems-id");
        if (mmoType != null && !mmoType.isBlank() && mmoId != null && !mmoId.isBlank()) {
            ItemStack mmo = plugin.mmoItemsHook().getPreviewItem(mmoType, mmoId, 1);
            if (mmo != null && !mmo.getType().isAir()) return mmo;
        }

        String raw = s.getString("material", "AMETHYST_CLUSTER");
        Material material = Material.matchMaterial(raw == null ? "AMETHYST_CLUSTER" : raw);
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("ItemDisplay de crate con material inválido: " + raw);
            return null;
        }
        return new ItemStack(material, 1);
    }

    private void applyDisplayScale(ItemDisplay display, ConfigurationSection s) {
        double size = Math.max(0.001, s.getDouble("size", 1.0));
        float sx = (float) Math.max(0.001, s.contains("scale.x") ? s.getDouble("scale.x") : size);
        float sy = (float) Math.max(0.001, s.contains("scale.y") ? s.getDouble("scale.y") : size);
        float sz = (float) Math.max(0.001, s.contains("scale.z") ? s.getDouble("scale.z") : size);
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(sx, sy, sz), new AxisAngle4f()));
    }

    private ItemDisplay.ItemDisplayTransform parseItemTransform(String raw) {
        try { return ItemDisplay.ItemDisplayTransform.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return ItemDisplay.ItemDisplayTransform.FIXED; }
    }

    /**
     * Genera puntos aleatorios dentro de un volumen alrededor de la crate.
     * Cada efecto tiene su propio interval-ticks y points-per-spawn.
     */
    private void drawRandomPoints(World world, Location base, ConfigurationSection randomPoints, long previousTicks) {
        if (randomPoints == null) return;
        for (String id : randomPoints.getKeys(false)) {
            ConfigurationSection s = randomPoints.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true) || !shouldRun(s, previousTicks)) continue;

            ParticleSpec particle = ParticleSpec.of(s.getString("particle", "ENCHANT"), s.getInt("count", 1),
                    s.getDouble("spread", 0), ParticleSpec.configuredSpeed(s, "particle-speed", "extra", 0.0),
                    s.getConfigurationSection("data"));
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

    private void removeItemVisual(VisualKey key) {
        ItemDisplay display = itemDisplays.remove(key);
        removeDisplay(display);
    }

    private void removeDisplay(TextDisplay display) {
        if (display != null && display.isValid()) display.remove();
    }

    private void removeDisplay(ItemDisplay display) {
        if (display != null && display.isValid()) display.remove();
    }

    private record VisualKey(BlockKey crate, String group, int index) {}
}
