package com.Lino.rainbowNickname;

import com.Lino.rainbowNickname.data.PlayerData;
import com.Lino.rainbowNickname.handlers.CommandHandler;
import com.Lino.rainbowNickname.handlers.EventListener;
import com.Lino.rainbowNickname.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

public class RainbowNickname extends JavaPlugin {
    private static final String PERMISSION = "rainbownick.use";

    private ConfigManager configManager;
    private AnimationManager animationManager;
    private ArmorStandManager armorStandManager;
    private TabListManager tabListManager;
    private PlayerDataManager playerDataManager;
    private TaskManager taskManager;
    private TPSMonitor tpsMonitor;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        animationManager = new AnimationManager(configManager);
        armorStandManager = new ArmorStandManager(configManager, animationManager);
        tabListManager = new TabListManager(configManager, animationManager);
        playerDataManager = new PlayerDataManager(configManager, animationManager, tabListManager);
        taskManager = new TaskManager(this, configManager, playerDataManager, tabListManager, armorStandManager);
        tpsMonitor = new TPSMonitor(this, configManager);

        taskManager.setTPSMonitor(tpsMonitor);

        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        getCommand("rainbownick").setExecutor(new CommandHandler(this));

        tabListManager.cleanupOldTeams();
        armorStandManager.cleanupOrphanedArmorStands();

        tpsMonitor.start();

        if (configManager.isUseAsyncTasks()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission(PERMISSION)) {
                        animationManager.precachePlayerNames(player.getName());
                    }
                }

                Bukkit.getScheduler().runTask(this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.hasPermission(PERMISSION)) {
                            setupPlayer(player);
                        } else if (configManager.isKeepPrefixSuffix()) {
                            tabListManager.forceUpdateNonRainbowPlayer(player, player.getName());
                        }
                    }
                    taskManager.startAllTasks();
                });
            });
        } else {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission(PERMISSION)) {
                        setupPlayer(player);
                    } else if (configManager.isKeepPrefixSuffix()) {
                        tabListManager.forceUpdateNonRainbowPlayer(player, player.getName());
                    }
                }
                taskManager.startAllTasks();
            }, 20L);
        }

        getLogger().info("RainbowNickname enabled - Version with prefix/suffix support!");
        getLogger().info("Prefix/Suffix: " + (configManager.isKeepPrefixSuffix() ? "ENABLED for ALL players" : "DISABLED"));
        getLogger().info("Read from tab: " + (configManager.isReadFromTab() ? "ENABLED" : "DISABLED"));
        getLogger().info("Delay LuckPerms: " + configManager.getLuckpermsJoinDelay() + " ticks");
        getLogger().info("TPS Auto-adjust: " + (configManager.isAutoAdjustSpeed() ? "ENABLED" : "DISABLED"));
    }

    @Override
    public void onDisable() {
        taskManager.cancelAllTasks();
        tpsMonitor.stop();

        playerDataManager.getAllPlayerData().values().parallelStream().forEach(data -> {
            if (data.armorStand != null && !data.armorStand.isDead()) {
                Bukkit.getScheduler().runTask(this, () -> data.armorStand.remove());
            }
        });

        armorStandManager.cleanupOrphanedArmorStands();

        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayer(player);
            if (configManager.isUseTabList()) {
                player.setPlayerListHeader("");
                player.setPlayerListFooter("");
            }
        }

        tabListManager.cleanupOldTeams();
        playerDataManager.clearAll();
        animationManager.clearCache();
        armorStandManager.clearArmorStands();
        tabListManager.clearCaches();

        getLogger().info("RainbowNickname disabled!");
    }

    public void setupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        PlayerData oldData = playerDataManager.getPlayerData(uuid);
        if (oldData != null && oldData.armorStand != null && !oldData.armorStand.isDead()) {
            armorStandManager.removeArmorStand(oldData.armorStand);
        }

        if (!animationManager.hasCachedNames(name)) {
            animationManager.precachePlayerNames(name);
        }

        Team luckPermsTeam = null;
        String savedPrefix = "";
        String savedSuffix = "";

        for (Team team : tabListManager.getScoreboard().getTeams()) {
            if (!team.getName().startsWith("rn_") && team.hasEntry(player.getName())) {
                luckPermsTeam = team;
                savedPrefix = team.getPrefix();
                savedSuffix = team.getSuffix();
                break;
            }
        }

        tabListManager.hideOriginalNametag(player);

        ArmorStand armorStand = armorStandManager.createArmorStand(player);

        PlayerData data = playerDataManager.createPlayerData(player, armorStand);
        data.cachedPrefix = savedPrefix;
        data.cachedSuffix = savedSuffix;
        data.luckPermsTeam = luckPermsTeam;

        playerDataManager.addPlayerData(uuid, data);

        playerDataManager.updatePlayerColor(uuid);

        if (configManager.isUseTabList()) {
            tabListManager.updateTabList(player);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        PlayerData data = playerDataManager.removePlayerData(uuid);

        if (data != null) {
            if (data.armorStand != null && !data.armorStand.isDead()) {
                armorStandManager.removeArmorStand(data.armorStand);
            }

            tabListManager.removePlayerTeam(uuid);
        }

        tabListManager.removePlayerData(uuid);
    }

    private void restorePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataManager.getPlayerData(uuid);

        if (data != null) {
            player.setDisplayName(data.originalName);
            if (data.originalListName != null) {
                player.setPlayerListName(data.originalListName);
            } else {
                player.setPlayerListName(data.originalName);
            }
        }
    }

    public void reloadPlugin(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading RainbowNickname...");

        taskManager.cancelAllTasks();
        tpsMonitor.stop();

        for (PlayerData data : playerDataManager.getAllPlayerData().values()) {
            if (data.armorStand != null && !data.armorStand.isDead()) {
                data.armorStand.remove();
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayer(player);
            if (configManager.isUseTabList()) {
                player.setPlayerListHeader("");
                player.setPlayerListFooter("");
            }
        }

        playerDataManager.clearAll();
        animationManager.clearCache();
        armorStandManager.clearArmorStands();
        tabListManager.clearCaches();

        configManager.reload();

        tpsMonitor = new TPSMonitor(this, configManager);
        taskManager.setTPSMonitor(tpsMonitor);
        tpsMonitor.start();

        armorStandManager.cleanupOrphanedArmorStands();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERMISSION)) {
                animationManager.precachePlayerNames(player.getName());
                setupPlayer(player);
            }
        }

        taskManager.startAllTasks();

        sender.sendMessage(ChatColor.GREEN + "RainbowNickname reloaded successfully!");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public AnimationManager getAnimationManager() { return animationManager; }
    public ArmorStandManager getArmorStandManager() { return armorStandManager; }
    public TabListManager getTabListManager() { return tabListManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public TPSMonitor getTPSMonitor() { return tpsMonitor; }
}