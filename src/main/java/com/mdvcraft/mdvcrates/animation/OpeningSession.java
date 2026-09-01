package com.mdvcraft.mdvcrates.animation;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.model.BlockKey;
import com.mdvcraft.mdvcrates.model.CrateDefinition;
import com.mdvcraft.mdvcrates.model.PendingReward;
import com.mdvcraft.mdvcrates.model.Reward;
import com.mdvcraft.mdvcrates.service.OpeningManager;
import com.mdvcraft.mdvcrates.service.RewardService;
import com.mdvcraft.mdvcrates.util.ParticleSpec;
import com.mdvcraft.mdvcrates.util.Text;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lidded;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class OpeningSession {
    private final MDVCratesPlugin plugin;
    private final OpeningManager manager;
    private final UUID playerId;
    private final Block block;
    private final BlockKey blockKey;
    private final CrateDefinition crate;
    private final Reward winner;
    private final PendingReward pendingReward;
    private final RewardService rewards;
    private final ConfigurationSection opening;

    private final List<SuctionMote> motes = new ArrayList<>();
    private BukkitTask task;
    private ItemDisplay itemDisplay;
    private TextDisplay textDisplay;
    private long tick;
    private int rollIndex;
    private long nextRollTick;
    private Phase phase = Phase.ROLL;
    private long phaseTick;
    private boolean burstDone;
    private boolean rewardDelivered;
    private boolean finished;
    private float yaw;

    public OpeningSession(MDVCratesPlugin plugin, OpeningManager manager, Player player, Block block,
                          CrateDefinition crate, Reward winner, PendingReward pendingReward, RewardService rewards) {
        this.plugin = plugin;
        this.manager = manager;
        this.playerId = player.getUniqueId();
        this.block = block;
        this.blockKey = BlockKey.of(block);
        this.crate = crate;
        this.winner = winner;
        this.pendingReward = pendingReward;
        this.rewards = rewards;
        ConfigurationSection animations = crate.animationSection();
        this.opening = animations == null ? null : animations.getConfigurationSection("opening");
    }

    public UUID playerId() { return playerId; }
    public Player player() { return plugin.getServer().getPlayer(playerId); }
    public BlockKey blockKey() { return blockKey; }

    public void start() {
        block.getChunk().addPluginChunkTicket(plugin);
        openLid();
        spawnDisplays();
        int steps = Math.max(1, rollSteps());
        if (steps <= 1) {
            showReward(winner);
            phase = Phase.FINAL_PAUSE;
            phaseTick = 0;
        } else {
            Reward first = rewards.randomVisual(crate);
            showReward(first == null ? winner : first);
            rollIndex = 1;
            nextRollTick = rollDelayFor(0);
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        if (finished) return;
        Player player = player();
        if (player == null || !player.isOnline()) {
            interrupt(false);
            return;
        }
        if (!sameCrateStillExists()) {
            interrupt(true);
            return;
        }
        double maxDistance = plugin.getConfig().getDouble("settings.max-open-distance", 6.0);
        if (player.getWorld() != block.getWorld() || player.getLocation().distanceSquared(block.getLocation().add(.5, .5, .5)) > maxDistance * maxDistance) {
            interrupt(true);
            return;
        }

        tick++;
        spinAndBob();
        if (!burstDone) tickSuction();

        switch (phase) {
            case ROLL -> tickRoll();
            case FINAL_PAUSE -> tickFinalPause();
            case RISE -> tickRise();
            case CLOSE -> tickClose();
        }
    }

    private void tickRoll() {
        if (tick < nextRollTick) return;
        int steps = Math.max(1, rollSteps());

        // El último cambio muestra al ganador y entra inmediatamente en la
        // pausa final. De este modo pause-before-burst-ticks=8 son exactamente
        // 0.4 s con el premio detenido antes del burst, sin sumar otro delay.
        if (rollIndex >= steps - 1) {
            showReward(winner);
            playSound(path("roll.change-sound", "BLOCK_NOTE_BLOCK_HAT"),
                    (float) dbl("roll.change-sound-volume", .55), (float) dbl("roll.change-sound-pitch", 1.55));
            phase = Phase.FINAL_PAUSE;
            phaseTick = 0;
            return;
        }

        Reward visual = rewards.randomVisual(crate);
        showReward(visual == null ? winner : visual);
        playSound(path("roll.change-sound", "BLOCK_NOTE_BLOCK_HAT"),
                (float) dbl("roll.change-sound-volume", .55), (float) dbl("roll.change-sound-pitch", 1.55));
        long delay = rollDelayFor(rollIndex);
        nextRollTick = tick + delay;
        rollIndex++;
    }

    private void tickFinalPause() {
        phaseTick++;
        int pause = integer("finish.pause-before-burst-ticks", 8);
        if (phaseTick < pause) return;
        burstDone = true;
        motes.clear();
        spawnBurst();
        playSound(path("finish.reward-sound", "ENTITY_PLAYER_LEVELUP"),
                (float) dbl("finish.reward-sound-volume", 1.0), (float) dbl("finish.reward-sound-pitch", 1.15));
        phase = Phase.RISE;
        phaseTick = 0;
    }

    private void tickRise() {
        phaseTick++;
        int riseTicks = Math.max(1, integer("finish.rise-ticks", 12));
        double riseHeight = dbl("finish.rise-height", .90);
        double t = Math.min(1.0, phaseTick / (double) riseTicks);
        Location target = displayBase().add(0, riseHeight * easeOutCubic(t), 0);
        teleportDisplays(target);
        if (phaseTick < riseTicks) return;

        if (!rewardDelivered) {
            Player player = player();
            if (player != null && player.isOnline()) {
                boolean ok = rewards.deliver(player, pendingReward);
                if (ok) manager.completePending(playerId, pendingReward.entryId());
                else manager.queue(playerId, pendingReward);
                rewardDelivered = ok;
                if (ok) {
                    if (winner.type().name().equals("COMMAND")) {
                        plugin.messages().send(player, "reward-command", Map.of("reward", rewards.displayName(winner)));
                    } else {
                        plugin.messages().send(player, "reward-item", Map.of("reward", rewards.displayName(winner)));
                    }
                } else {
                    plugin.messages().send(player, "reward-pending", Map.of("reward", rewards.displayName(winner)));
                }
            } else {
                manager.queue(playerId, pendingReward);
            }
        }
        removeDisplays();
        phase = Phase.CLOSE;
        phaseTick = 0;
    }

    private void tickClose() {
        phaseTick++;
        if (phaseTick < integer("finish.close-delay-ticks", 4)) return;
        closeLid();
        finishCleanup();
    }

    public void interrupt(boolean deliverNowIfOnline) {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        if (!rewardDelivered) {
            Player player = player();
            if (deliverNowIfOnline && player != null && player.isOnline()) {
                boolean ok = rewards.deliver(player, pendingReward);
                if (ok) manager.completePending(playerId, pendingReward.entryId());
                else manager.queue(playerId, pendingReward);
                rewardDelivered = ok;
            } else {
                manager.queue(playerId, pendingReward);
            }
        }
        motes.clear();
        removeDisplays();
        closeLid();
        try { block.getChunk().removePluginChunkTicket(plugin); } catch (Throwable ignored) {}
        manager.completed(this);
    }

    private void finishCleanup() {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        try { block.getChunk().removePluginChunkTicket(plugin); } catch (Throwable ignored) {}
        manager.completed(this);
    }

    private void spawnDisplays() {
        World world = block.getWorld();
        Location loc = displayBase();
        itemDisplay = world.spawn(loc, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setBillboard(Display.Billboard.FIXED);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            display.addScoreboardTag("mdvcrates_visual");
            float scale = (float) dbl("roll.display-scale", .85);
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
        if (bool("roll.show-name", true)) {
            textDisplay = world.spawn(loc.clone().add(0, dbl("roll.name-offset-y", .55), 0), TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(false);
                display.setShadowed(true);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.addScoreboardTag("mdvcrates_visual");
            });
        }
    }

    private void showReward(Reward reward) {
        if (reward == null) return;
        ItemStack preview = rewards.preview(reward);
        if (preview != null && itemDisplay != null) itemDisplay.setItemStack(preview);
        if (textDisplay != null) textDisplay.setText(rewards.displayNameWithAmount(reward));
    }

    private void spinAndBob() {
        if (itemDisplay == null) return;
        yaw += (float) dbl("roll.spin-deg-per-tick", 10.0);
        Location loc = itemDisplay.getLocation();
        loc.setYaw(yaw);
        itemDisplay.teleport(loc);
    }

    private void teleportDisplays(Location itemLoc) {
        if (itemDisplay != null) {
            itemLoc.setYaw(yaw);
            itemDisplay.teleport(itemLoc);
        }
        if (textDisplay != null) textDisplay.teleport(itemLoc.clone().add(0, dbl("roll.name-offset-y", .55), 0));
    }

    private void removeDisplays() {
        if (itemDisplay != null && itemDisplay.isValid()) itemDisplay.remove();
        if (textDisplay != null && textDisplay.isValid()) textDisplay.remove();
        itemDisplay = null;
        textDisplay = null;
    }

    private void tickSuction() {
        if (!bool("suction.enabled", true)) return;
        int interval = Math.max(1, integer("suction.spawn-interval-ticks", 1));
        int perSpawn;
        if (opening != null && opening.contains("suction.motes-per-spawn")) {
            perSpawn = Math.max(0, integer("suction.motes-per-spawn", 1));
        } else {
            // Compatibilidad con 1.0.0.
            perSpawn = Math.max(0, integer("suction.motes-per-tick", 2));
        }
        double radius = dbl("suction.radius", 1.5);
        int minLife = Math.max(2, integer("suction.travel-ticks-min", 8));
        int maxLife = Math.max(minLife, integer("suction.travel-ticks-max", 14));
        if (tick % interval == 0) {
            for (int i = 0; i < perSpawn; i++) {
                double u = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
                double theta = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
                double rxy = Math.sqrt(1 - u * u);
                Vector start = new Vector(Math.cos(theta) * rxy, u, Math.sin(theta) * rxy).multiply(radius);
                int life = ThreadLocalRandom.current().nextInt(minLife, maxLife + 1);
                motes.add(new SuctionMote(start, life));
            }
        }

        ParticleSpec particle = particle("suction", "SOUL_FIRE_FLAME");
        Location center = block.getLocation().add(.5, dbl("suction.center-y-offset", .55), .5);
        Iterator<SuctionMote> it = motes.iterator();
        while (it.hasNext()) {
            SuctionMote mote = it.next();
            mote.age++;
            double t = Math.min(1.0, mote.age / (double) mote.life);
            double eased = t * t;
            Vector pos = mote.start.clone().multiply(1.0 - eased);
            particle.spawn(block.getWorld(), center.getX() + pos.getX(), center.getY() + pos.getY(), center.getZ() + pos.getZ());
            if (mote.age >= mote.life) it.remove();
        }
    }

    private void spawnBurst() {
        String name = path("finish.burst-particle", "EXPLOSION");
        int count = integer("finish.burst-count", 1);
        double spread = dbl("finish.burst-spread", 0.0);
        double extra = opening != null && opening.contains("finish.burst-particle-speed")
                ? dbl("finish.burst-particle-speed", 0.0)
                : dbl("finish.burst-extra", 0.0);
        ParticleSpec spec = ParticleSpec.of(name, count, spread, extra,
                opening == null ? null : opening.getConfigurationSection("finish.burst-data"));
        Location loc = itemDisplay == null ? displayBase() : itemDisplay.getLocation();
        spec.spawn(block.getWorld(), loc.getX(), loc.getY(), loc.getZ());
    }

    private ParticleSpec particle(String prefix, String fallback) {
        if (opening == null) return ParticleSpec.of(fallback, 1, 0, 0, null);
        ConfigurationSection sec = opening.getConfigurationSection(prefix);
        if (sec == null) return ParticleSpec.of(fallback, 1, 0, 0, null);
        return ParticleSpec.of(sec.getString("particle", fallback), sec.getInt("count", 1),
                sec.getDouble("spread", 0), ParticleSpec.configuredSpeed(sec, "particle-speed", "extra", 0.0),
                sec.getConfigurationSection("data"));
    }

    private long rollDelayFor(int index) {
        int steps = Math.max(1, rollSteps());
        double progress = steps <= 1 ? 1.0 : Math.max(0, Math.min(1, index / (double) (steps - 1)));
        String curve = path("roll.curve", "EASE_IN_QUAD").toUpperCase(Locale.ROOT);
        double curved = switch (curve) {
            case "LINEAR" -> progress;
            case "EASE_IN_CUBIC" -> progress * progress * progress;
            default -> progress * progress;
        };
        double first = dbl("roll.initial-delay-ticks", 2);
        double last = dbl("roll.final-delay-ticks", 12);
        return Math.max(1L, Math.round(first + (last - first) * curved));
    }

    private int rollSteps() { return Math.max(1, integer("roll.steps", 18)); }

    private Location displayBase() {
        return block.getLocation().add(
                dbl("roll.display-offset.x", .5),
                dbl("roll.display-offset.y", 1.45),
                dbl("roll.display-offset.z", .5));
    }

    private boolean sameCrateStillExists() {
        return plugin.crateManager().isRegisteredLocation(blockKey) && block.getType() == crate.blockMaterial();
    }

    private void openLid() {
        if (block.getState() instanceof Lidded lidded) lidded.open();
    }

    private void closeLid() {
        try {
            if (block.getState() instanceof Lidded lidded) lidded.close();
        } catch (Throwable ignored) {}
    }

    private void playSound(String raw, float volume, float pitch) {
        Player player = player();
        if (player == null || raw == null || raw.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(raw.toUpperCase(Locale.ROOT));
            player.playSound(block.getLocation(), sound, volume, pitch);
        } catch (Throwable ignored) {
            // Permite también namespaced sound ids configurados manualmente.
            player.playSound(block.getLocation(), raw.toLowerCase(Locale.ROOT), volume, pitch);
        }
    }

    private String path(String path, String def) { return opening == null ? def : opening.getString(path, def); }
    private boolean bool(String path, boolean def) { return opening == null ? def : opening.getBoolean(path, def); }
    private int integer(String path, int def) { return opening == null ? def : opening.getInt(path, def); }
    private double dbl(String path, double def) { return opening == null ? def : opening.getDouble(path, def); }
    private static double easeOutCubic(double t) { return 1.0 - Math.pow(1.0 - t, 3); }

    private enum Phase { ROLL, FINAL_PAUSE, RISE, CLOSE }

    private static final class SuctionMote {
        private final Vector start;
        private final int life;
        private int age;
        private SuctionMote(Vector start, int life) { this.start = start; this.life = life; }
    }
}
