package com.mdvcraft.mdvcrates.service;

import com.mdvcraft.mdvcrates.MDVCratesPlugin;
import com.mdvcraft.mdvcrates.hook.MMOItemsHook;
import com.mdvcraft.mdvcrates.model.*;
import com.mdvcraft.mdvcrates.util.PlaceholderUtil;
import com.mdvcraft.mdvcrates.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RewardService {
    private final MDVCratesPlugin plugin;
    private final MMOItemsHook mmoItems;

    public RewardService(MDVCratesPlugin plugin, MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.mmoItems = mmoItems;
    }

    public List<Reward> validRewards(CrateDefinition crate) {
        List<Reward> valid = new ArrayList<>();
        for (Reward reward : crate.rewards()) {
            boolean selectable = reward.hasExplicitChance() ? reward.chance() > 0 : reward.weight() > 0;
            if (!selectable) continue;
            if (reward.type() == RewardType.COMMAND) {
                if (!reward.commands().isEmpty()) valid.add(reward);
            } else if (isRewardDefinitionValid(reward)) {
                valid.add(reward);
            }
        }
        return valid;
    }

    /**
     * Valida la definición sin construir previews dinámicos. En especial, un
     * MMOItem se comprueba consultando su template; no se genera un ItemStack y
     * por tanto no se tiran modifiers solo por calcular probabilidades.
     */
    private boolean isRewardDefinitionValid(Reward reward) {
        return switch (reward.type()) {
            case MMOITEM -> mmoItems.exists(reward.mmoItemsType(), reward.mmoItemsId());
            case VANILLA -> reward.vanillaMaterial() != null && !reward.vanillaMaterial().isAir();
            case ITEM -> {
                ItemStack item = reward.storedItem();
                yield item != null && !item.getType().isAir();
            }
            case COMMAND -> !reward.commands().isEmpty();
        };
    }

    /**
     * Calcula las probabilidades efectivas de la crate.
     *
     * Reglas:
     * - Sin chance explícito: comportamiento clásico por weight.
     * - chance explícito <= 100: reserva exactamente ese porcentaje; el porcentaje
     *   restante se reparte entre rewards sin chance usando weight.
     * - chance explícito > 100: se normalizan los chance a 100 y los rewards solo
     *   por weight quedan en 0.
     * - Si todos usan chance pero suman menos de 100, se normalizan entre ellos para
     *   garantizar que cada apertura siempre tenga una recompensa.
     */
    public Map<String, Double> probabilities(CrateDefinition crate) {
        List<Reward> valid = validRewards(crate);
        LinkedHashMap<String, Double> out = new LinkedHashMap<>();
        if (valid.isEmpty()) return out;

        List<Reward> explicit = valid.stream().filter(Reward::hasExplicitChance).toList();
        List<Reward> weighted = valid.stream().filter(r -> !r.hasExplicitChance()).toList();

        if (explicit.isEmpty()) {
            distributeByWeight(weighted, 100.0, out);
            return out;
        }

        double explicitTotal = explicit.stream().mapToDouble(r -> r.chance()).sum();
        if (explicitTotal >= 100.0) {
            if (explicitTotal <= 0) return out;
            for (Reward reward : explicit) out.put(reward.id(), reward.chance() * 100.0 / explicitTotal);
            for (Reward reward : weighted) out.put(reward.id(), 0.0);
            return out;
        }

        if (!weighted.isEmpty()) {
            for (Reward reward : explicit) out.put(reward.id(), reward.chance());
            distributeByWeight(weighted, 100.0 - explicitTotal, out);
            return out;
        }

        // Solo hay chance explícito y no suma 100. Se normaliza para evitar aperturas vacías.
        if (explicitTotal > 0) {
            for (Reward reward : explicit) out.put(reward.id(), reward.chance() * 100.0 / explicitTotal);
        }
        return out;
    }

    private void distributeByWeight(List<Reward> rewards, double percentPool, Map<String, Double> out) {
        double totalWeight = rewards.stream().mapToDouble(Reward::weight).sum();
        if (totalWeight <= 0) {
            for (Reward reward : rewards) out.put(reward.id(), 0.0);
            return;
        }
        for (Reward reward : rewards) out.put(reward.id(), percentPool * reward.weight() / totalWeight);
    }

    public double probabilityPercent(CrateDefinition crate, Reward reward) {
        if (crate == null || reward == null) return 0;
        return probabilities(crate).getOrDefault(reward.id(), 0.0);
    }

    public Reward select(CrateDefinition crate) {
        List<Reward> valid = validRewards(crate);
        if (valid.isEmpty()) return null;
        Map<String, Double> chances = probabilities(crate);
        double total = valid.stream().mapToDouble(r -> chances.getOrDefault(r.id(), 0.0)).sum();
        if (total <= 0) return null;

        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0;
        for (Reward reward : valid) {
            cursor += chances.getOrDefault(reward.id(), 0.0);
            if (roll < cursor) return reward;
        }
        return valid.get(valid.size() - 1);
    }

    public Reward randomVisual(CrateDefinition crate) {
        Map<String, Double> chances = probabilities(crate);
        List<Reward> valid = validRewards(crate).stream()
                .filter(r -> chances.getOrDefault(r.id(), 0.0) > 0.0000001)
                .toList();
        if (valid.isEmpty()) return null;
        return valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
    }

    /**
     * Preview limpio para menús/ruleta. Para MMOItems se intenta usar el build de
     * display de MMOItems, que evita arrastrar prefijos/sufijos/modificadores de una
     * copia usada como "Gastado ...". La entrega real sigue usando getItem normal.
     */
    public ItemStack preview(Reward reward) {
        if (reward == null) return null;
        return switch (reward.type()) {
            case MMOITEM -> mmoItems.getPreviewItem(reward.mmoItemsType(), reward.mmoItemsId(), Math.min(reward.amount(), 64));
            case VANILLA -> {
                if (reward.vanillaMaterial() == null || reward.vanillaMaterial().isAir()) yield null;
                ItemStack item = new ItemStack(reward.vanillaMaterial());
                item.setAmount(Math.min(reward.amount(), Math.max(1, item.getMaxStackSize())));
                yield item;
            }
            case ITEM -> {
                ItemStack item = reward.storedItem();
                if (item != null) item.setAmount(Math.min(reward.amount(), Math.max(1, item.getMaxStackSize())));
                yield item;
            }
            case COMMAND -> reward.commandPreview();
        };
    }

    /**
     * Preview exclusivo del visualizador de recompensas. Solo MMOItems usa una
     * ruta distinta: template base sin modifier group. El resto queda idéntico.
     */
    public ItemStack viewerPreview(Reward reward) {
        if (reward == null) return null;
        if (reward.type() == RewardType.MMOITEM) {
            return mmoItems.getViewerPreviewItem(
                    reward.mmoItemsType(), reward.mmoItemsId(), Math.min(reward.amount(), 64));
        }
        return preview(reward);
    }

    public String displayName(Reward reward) {
        if (reward.displayName() != null && !reward.displayName().isBlank()) return Text.color(reward.displayName());
        ItemStack preview = preview(reward);
        return preview == null ? reward.id() : Text.itemName(preview);
    }

    public String displayNameWithAmount(Reward reward) {
        String name = displayName(reward);
        return reward.amount() > 1 ? name + Text.color(" &ax" + reward.amount()) : name;
    }

    public PendingReward snapshot(String crateId, Reward reward, int reservedSlot) {
        ItemStack item = switch (reward.type()) {
            case ITEM -> reward.storedItem();
            case VANILLA -> reward.vanillaMaterial() == null ? null : new ItemStack(reward.vanillaMaterial());
            default -> null;
        };
        return new PendingReward(
                UUID.randomUUID().toString(), crateId, reward.type(), displayName(reward), reward.amount(),
                reward.mmoItemsType(), reward.mmoItemsId(), item, reward.commands(), reservedSlot);
    }

    public boolean deliver(Player player, PendingReward reward) {
        if (reward.type() == RewardType.COMMAND) {
            for (String command : reward.commands()) {
                String parsed = PlaceholderUtil.apply(player, command, Map.of("crate", reward.crateId()));
                if (parsed.startsWith("/")) parsed = parsed.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
            return true;
        }

        ItemStack base;
        if (reward.type() == RewardType.MMOITEM) {
            // IMPORTANTE: entrega normal, no preview. Conserva el sistema aleatorio
            // de modifiers/tier/calidad configurado en MMOItems.
            base = mmoItems.getItem(reward.mmoItemsType(), reward.mmoItemsId(), 1);
        } else {
            base = reward.item();
            if (base != null) base.setAmount(1);
        }
        if (base == null || base.getType() == Material.AIR) return false;

        int remaining = Math.max(1, reward.amount());
        int reserved = reward.reservedSlot();
        int max = Math.max(1, base.getMaxStackSize());

        if (reserved >= 0 && reserved < player.getInventory().getStorageContents().length) {
            ItemStack occupant = player.getInventory().getItem(reserved);
            if (occupant != null && !occupant.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), occupant.clone());
            }
            int firstAmount = Math.min(max, remaining);
            ItemStack first = base.clone();
            first.setAmount(firstAmount);
            player.getInventory().setItem(reserved, first);
            remaining -= firstAmount;
        }

        while (remaining > 0) {
            int amount = Math.min(max, remaining);
            ItemStack part = base.clone();
            part.setAmount(amount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(part);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= amount;
        }
        return true;
    }
}
