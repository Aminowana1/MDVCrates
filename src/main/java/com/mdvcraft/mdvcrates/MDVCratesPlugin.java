package com.mdvcraft.mdvcrates;

import com.mdvcraft.mdvcrates.animation.IdleAnimationManager;
import com.mdvcraft.mdvcrates.command.MDVCratesCommand;
import com.mdvcraft.mdvcrates.config.CrateRepository;
import com.mdvcraft.mdvcrates.config.MessageManager;
import com.mdvcraft.mdvcrates.editor.EditorManager;
import com.mdvcraft.mdvcrates.hook.MMOItemsHook;
import com.mdvcraft.mdvcrates.listener.*;
import com.mdvcraft.mdvcrates.service.*;
import com.mdvcraft.mdvcrates.viewer.RewardViewerManager;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class MDVCratesPlugin extends JavaPlugin {
    private MMOItemsHook mmoItemsHook;
    private MessageManager messages;
    private CrateRepository crateRepository;
    private RewardService rewardService;
    private PendingRewardService pendingRewardService;
    private CrateManager crateManager;
    private OpeningManager openingManager;
    private EditorManager editorManager;
    private IdleAnimationManager idleAnimationManager;
    private RewardViewerManager rewardViewerManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cleanupOldVisuals();

        mmoItemsHook = new MMOItemsHook(getLogger());
        messages = new MessageManager(this);
        crateRepository = new CrateRepository(this, mmoItemsHook);
        rewardService = new RewardService(this, mmoItemsHook);
        pendingRewardService = new PendingRewardService(this, rewardService);
        crateManager = new CrateManager(this, crateRepository);
        crateManager.refreshLoadedPhysicalCrates();
        openingManager = new OpeningManager(this, mmoItemsHook, rewardService, pendingRewardService, messages);
        editorManager = new EditorManager(this, mmoItemsHook);
        rewardViewerManager = new RewardViewerManager(this);
        idleAnimationManager = new IdleAnimationManager(this, crateManager, openingManager);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new CrateInteractionListener(this), this);
        pm.registerEvents(new CrateProtectionListener(this), this);
        pm.registerEvents(new EditorListener(this), this);
        pm.registerEvents(new PlayerSafetyListener(this), this);
        pm.registerEvents(new RewardViewerListener(this), this);
        pm.registerEvents(new CratePlacementSyncListener(this), this);

        MDVCratesCommand command = new MDVCratesCommand(this);
        Objects.requireNonNull(getCommand("mdvcrates")).setExecutor(command);
        Objects.requireNonNull(getCommand("mdvcrates")).setTabCompleter(command);

        idleAnimationManager.start();
        getLogger().info("MDVCrates 1.1.3 habilitado. Crates cargadas: " + crateRepository.all().size());
    }

    @Override
    public void onDisable() {
        if (idleAnimationManager != null) idleAnimationManager.stop();
        if (openingManager != null) openingManager.interruptAll(false);
        if (pendingRewardService != null) pendingRewardService.save();
        cleanupOldVisuals();
    }

    public void reloadEverything() {
        if (openingManager != null) openingManager.interruptAll(true);
        if (idleAnimationManager != null) idleAnimationManager.stop();
        reloadConfig();
        messages.reload();
        mmoItemsHook.reload();
        crateRepository.reload();
        crateManager.rebuildIndex();
        int refreshed = crateManager.refreshLoadedPhysicalCrates();
        idleAnimationManager.start();
        getLogger().info("Reload aplicado a " + refreshed + " crate(s) colocada(s) en chunks cargados. Las demás se sincronizarán al cargar su chunk.");
    }

    private void cleanupOldVisuals() {
        for (World world : getServer().getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getScoreboardTags().contains("mdvcrates_visual")) display.remove();
            }
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getScoreboardTags().contains("mdvcrates_visual")) display.remove();
            }
        }
    }

    public MMOItemsHook mmoItemsHook() { return mmoItemsHook; }
    public MessageManager messages() { return messages; }
    public CrateRepository crateRepository() { return crateRepository; }
    public RewardService rewardService() { return rewardService; }
    public PendingRewardService pendingRewardService() { return pendingRewardService; }
    public CrateManager crateManager() { return crateManager; }
    public OpeningManager openingManager() { return openingManager; }
    public EditorManager editorManager() { return editorManager; }
    public RewardViewerManager rewardViewerManager() { return rewardViewerManager; }
}
