package com.mdvcraft.mdvcrates.service;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.config.CrateRepository;
import com.mdvcraft.mdvcrates.model.BlockKey;
import com.mdvcraft.mdvcrates.model.CrateDefinition;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import java.util.*;

public final class CrateManager {
    private final MDVCratesPlugin plugin;
    private final CrateRepository repository;
    private final Map<BlockKey, String> locationIndex = new HashMap<>();

    public CrateManager(MDVCratesPlugin plugin, CrateRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        rebuildIndex();
    }

    public void rebuildIndex() {
        locationIndex.clear();
        for (CrateDefinition crate : repository.all()) {
            for (BlockKey key : crate.locations()) locationIndex.put(key, crate.id());
        }
    }

    public CrateDefinition at(Block block) {
        String id = locationIndex.get(BlockKey.of(block));
        return id == null ? null : repository.get(id);
    }

    public boolean isCrate(Block block) {
        return locationIndex.containsKey(BlockKey.of(block));
    }

    public Collection<Map.Entry<BlockKey, String>> physicalCrates() {
        return Collections.unmodifiableCollection(locationIndex.entrySet());
    }

    public Block resolve(BlockKey key) {
        World world = Bukkit.getWorld(key.world());
        if (world == null) return null;
        int chunkX = key.x() >> 4;
        int chunkZ = key.z() >> 4;
        // Las animaciones idle no deben cargar chunks remotos únicamente para
        // dibujar partículas. Una crate vuelve a procesarse al cargar su chunk.
        if (!world.isChunkLoaded(chunkX, chunkZ)) return null;
        return world.getBlockAt(key.x(), key.y(), key.z());
    }

    public Block targetPlacementBlock(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null) return null;
        Block above = target.getRelative(BlockFace.UP);
        if (above.getType().isAir() || above.isReplaceable()) return above;
        return null;
    }

    public boolean place(Player player, String crateId, Block destination) {
        CrateDefinition crate = repository.get(crateId);
        if (crate == null || destination == null) return false;
        BlockKey key = BlockKey.of(destination);
        if (locationIndex.containsKey(key)) return false;
        if (plugin.getConfig().getBoolean("settings.prevent-double-chests", true) && wouldMakeDoubleChest(destination, crate.blockMaterial())) {
            return false;
        }
        destination.setType(crate.blockMaterial(), false);
        if (shouldFacePlayer(crate.blockMaterial()) && destination.getBlockData() instanceof Directional directional) {
            BlockFace facing = player.getFacing().getOppositeFace();
            if (directional.getFaces().contains(facing)) {
                directional.setFacing(facing);
                destination.setBlockData(directional, false);
            }
        }
        clearContainer(destination);
        List<BlockKey> locations = new ArrayList<>(crate.locations());
        locations.add(key);
        repository.setLocations(crate.id(), locations);
        rebuildIndex();
        return true;
    }

    public boolean remove(Block block, boolean removePhysical) {
        CrateDefinition crate = at(block);
        if (crate == null) return false;
        BlockKey key = BlockKey.of(block);
        List<BlockKey> locations = new ArrayList<>(crate.locations());
        locations.remove(key);
        repository.setLocations(crate.id(), locations);
        rebuildIndex();
        if (removePhysical) block.setType(Material.AIR, false);
        return true;
    }

    public boolean moveNearest(Player player, String crateId, Block destination) {
        CrateDefinition crate = repository.get(crateId);
        if (crate == null || destination == null || crate.locations().isEmpty()) return false;
        BlockKey nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockKey key : crate.locations()) {
            World w = Bukkit.getWorld(key.world());
            if (w == null || w != player.getWorld()) continue;
            Location loc = new Location(w, key.x() + .5, key.y() + .5, key.z() + .5);
            double d = loc.distanceSquared(player.getLocation());
            if (d < best) { best = d; nearest = key; }
        }
        if (nearest == null || best > 12 * 12) return false;
        if (locationIndex.containsKey(BlockKey.of(destination))) return false;
        if (plugin.getConfig().getBoolean("settings.prevent-double-chests", true) && wouldMakeDoubleChest(destination, crate.blockMaterial())) return false;

        Block old = resolve(nearest);
        if (old != null) old.setType(Material.AIR, false);
        destination.setType(crate.blockMaterial(), false);
        if (shouldFacePlayer(crate.blockMaterial()) && destination.getBlockData() instanceof Directional directional) {
            BlockFace facing = player.getFacing().getOppositeFace();
            if (directional.getFaces().contains(facing)) {
                directional.setFacing(facing);
                destination.setBlockData(directional, false);
            }
        }
        clearContainer(destination);
        List<BlockKey> locations = new ArrayList<>(crate.locations());
        locations.remove(nearest);
        locations.add(BlockKey.of(destination));
        repository.setLocations(crate.id(), locations);
        rebuildIndex();
        return true;
    }

    public boolean isRegisteredLocation(BlockKey key) {
        return locationIndex.containsKey(key);
    }

    public void sanitizePhysicalCrates() {
        refreshLoadedPhysicalCrates();
    }

    /**
     * Aplica la definición actual a todas las crates colocadas cuyos chunks ya
     * están cargados. No fuerza carga de chunks. Esto hace que /mdvcrates reload
     * actualice material/config visual sin tener que volver a colocarlas.
     */
    public int refreshLoadedPhysicalCrates() {
        int refreshed = 0;
        for (Map.Entry<BlockKey, String> entry : locationIndex.entrySet()) {
            CrateDefinition crate = repository.get(entry.getValue());
            Block block = resolve(entry.getKey());
            if (crate == null || block == null) continue;
            if (syncPhysicalBlock(block, crate)) refreshed++;
        }
        return refreshed;
    }

    /**
     * Cuando un chunk vuelve a cargar, sincroniza las crates registradas dentro
     * de él con la definición vigente, incluidas modificaciones hechas durante
     * un reload mientras ese chunk estaba descargado.
     */
    public int refreshChunk(Chunk chunk) {
        if (chunk == null) return 0;
        int refreshed = 0;
        String worldName = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        for (Map.Entry<BlockKey, String> entry : locationIndex.entrySet()) {
            BlockKey key = entry.getKey();
            if (!key.world().equals(worldName) || (key.x() >> 4) != cx || (key.z() >> 4) != cz) continue;
            CrateDefinition crate = repository.get(entry.getValue());
            if (crate == null) continue;
            Block block = chunk.getWorld().getBlockAt(key.x(), key.y(), key.z());
            if (syncPhysicalBlock(block, crate)) refreshed++;
        }
        return refreshed;
    }

    private boolean syncPhysicalBlock(Block block, CrateDefinition crate) {
        Material wanted = crate.blockMaterial();
        Material current = block.getType();

        // Nunca pisa un bloque arbitrario que no sea una crate soportada. AIR se
        // permite para recuperar una colocación registrada que haya quedado vacía.
        if (current != wanted && current != Material.AIR && !com.mdvcraft.mdvcrates.util.CrateBlocks.isSupported(current)) {
            plugin.getLogger().warning("No se pudo sincronizar crate '" + crate.id() + "' en "
                    + block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ()
                    + ": el bloque está ocupado por " + current + ".");
            return false;
        }

        if (current != wanted) {
            BlockData oldData = block.getBlockData();
            BlockFace oldFacing = oldData instanceof Directional oldDirectional ? oldDirectional.getFacing() : null;
            boolean preserveFacing = sameFacingFamily(current, wanted);

            block.setType(wanted, false);
            if (preserveFacing && oldFacing != null && block.getBlockData() instanceof Directional directional
                    && directional.getFaces().contains(oldFacing)) {
                directional.setFacing(oldFacing);
                block.setBlockData(directional, false);
            }
        }

        clearContainer(block);
        return true;
    }

    private boolean sameFacingFamily(Material a, Material b) {
        if (a == null || b == null) return false;
        boolean aChest = a == Material.CHEST || a == Material.TRAPPED_CHEST || a == Material.ENDER_CHEST;
        boolean bChest = b == Material.CHEST || b == Material.TRAPPED_CHEST || b == Material.ENDER_CHEST;
        boolean aShulker = a == Material.SHULKER_BOX || a.name().endsWith("_SHULKER_BOX");
        boolean bShulker = b == Material.SHULKER_BOX || b.name().endsWith("_SHULKER_BOX");
        return (aChest && bChest) || (aShulker && bShulker);
    }


    private boolean shouldFacePlayer(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.ENDER_CHEST;
    }

    private boolean wouldMakeDoubleChest(Block block, Material material) {
        if (material != Material.CHEST && material != Material.TRAPPED_CHEST) return false;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (block.getRelative(face).getType() == material) return true;
        }
        return false;
    }

    private void clearContainer(Block block) {
        if (block.getState() instanceof org.bukkit.block.Container container) container.getInventory().clear();
    }
}
