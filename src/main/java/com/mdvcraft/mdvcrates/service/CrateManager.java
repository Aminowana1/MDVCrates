package com.mdvcraft.mdvcrates.service;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.config.CrateRepository;
import com.mdvcraft.mdvcrates.model.BlockKey;
import com.mdvcraft.mdvcrates.model.CrateDefinition;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
        for (Map.Entry<BlockKey, String> entry : locationIndex.entrySet()) {
            CrateDefinition crate = repository.get(entry.getValue());
            Block block = resolve(entry.getKey());
            if (crate == null || block == null || block.getType() != crate.blockMaterial()) continue;
            clearContainer(block);
        }
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
