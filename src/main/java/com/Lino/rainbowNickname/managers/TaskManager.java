package com.Lino.rainbowNickname.managers;

import com.Lino.rainbowNickname.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class TaskManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PlayerDataManager playerDataManager;
    private final TabListManager tabListManager;
    private final ArmorStandManager armorStandManager;

    private BukkitRunnable animationTask;
    private BukkitRunnable positionTask;
    private BukkitRunnable cleanupTask;
    private BukkitRunnable prefixUpdateTask;
    private BukkitRunnable nonRainbowUpdateTask;

    public TaskManager(JavaPlugin plugin, ConfigManager config, PlayerDataManager playerDataManager,
                       TabListManager tabListManager, ArmorStandManager armorStandManager) {
        this.plugin = plugin;
        this.config = config;
        this.playerDataManager = playerDataManager;
        this.tabListManager = tabListManager;
        this.armorStandManager = armorStandManager;
    }

    public void startAllTasks() {
        startAnimationTask();
        startPositionTask();
        startCleanupTask();
        if (config.isKeepPrefixSuffix()) {
            startPrefixUpdateTask();
            startNonRainbowUpdateTask();
        }
    }

    public void cancelAllTasks() {
        if (animationTask != null) animationTask.cancel();
        if (positionTask != null) positionTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        if (prefixUpdateTask != null) prefixUpdateTask.cancel();
        if (nonRainbowUpdateTask != null) nonRainbowUpdateTask.cancel();
    }

    private void startAnimationTask() {
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : playerDataManager.getPermittedPlayers()) {
                    playerDataManager.updatePlayerColor(uuid);
                }

                if (config.isUseTabList()) {
                    tabListManager.updateAllTabLists();
                }
            }
        };

        animationTask.runTaskTimer(plugin, 0L, config.getAnimationSpeed());
    }

    private void startPositionTask() {
        positionTask = new BukkitRunnable() {
            @Override
            public void run() {
                List<UUID> toRemove = new ArrayList<>();

                for (Map.Entry<UUID, PlayerData> entry : playerDataManager.getAllPlayerData().entrySet()) {
                    UUID uuid = entry.getKey();
                    PlayerData data = entry.getValue();
                    Player player = Bukkit.getPlayer(uuid);

                    if (player == null || !player.isOnline()) {
                        toRemove.add(uuid);
                        continue;
                    }

                    if (data.armorStand == null || data.armorStand.isDead()) {
                        toRemove.add(uuid);
                        continue;
                    }

                    Location playerLoc = player.getLocation();
                    if (playerDataManager.shouldUpdatePosition(playerLoc, data.lastLocation)) {
                        Location newLoc = playerLoc.clone();
                        newLoc.setY(newLoc.getY() + config.getNametagHeight());
                        data.armorStand.teleport(newLoc);
                        data.lastLocation = playerLoc;
                    }
                }

                toRemove.forEach(uuid -> {
                    PlayerData data = playerDataManager.removePlayerData(uuid);
                    if (data != null && data.armorStand != null && !data.armorStand.isDead()) {
                        armorStandManager.removeArmorStand(data.armorStand);
                    }
                });
            }
        };

        positionTask.runTaskTimer(plugin, 0L, 2L);
    }

    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                Set<Location> knownLocations = new HashSet<>();

                for (PlayerData data : playerDataManager.getAllPlayerData().values()) {
                    if (data.armorStand != null && !data.armorStand.isDead()) {
                        knownLocations.add(data.armorStand.getLocation());
                    }
                }

                armorStandManager.cleanupDuplicates(knownLocations);
            }
        };

        cleanupTask.runTaskTimer(plugin, 100L, 100L);
    }

    private void startPrefixUpdateTask() {
        prefixUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    if (playerDataManager.getPermittedPlayers().contains(uuid)) {
                        playerDataManager.updatePrefixSuffix(player);
                    } else if (config.isKeepPrefixSuffix()) {
                        tabListManager.forceUpdateNonRainbowPlayer(player, player.getName());
                    }
                }
            }
        };

        prefixUpdateTask.runTaskTimer(plugin, 60L, 40L);
    }

    private void startNonRainbowUpdateTask() {
        nonRainbowUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.hasPermission("rainbownick.use")) {
                        UUID uuid = player.getUniqueId();

                        Long lastCheck = tabListManager.getLastTabCheck().get(uuid);
                        if (lastCheck == null || currentTime - lastCheck > 2000) {

                            String expectedPrefix = "";
                            String expectedSuffix = "";

                            for (Team team : tabListManager.getScoreboard().getTeams()) {
                                if (!team.getName().startsWith("rn_") && team.hasEntry(player.getName())) {
                                    expectedPrefix = team.getPrefix();
                                    expectedSuffix = team.getSuffix();
                                    break;
                                }
                            }

                            if (!expectedPrefix.isEmpty() || !expectedSuffix.isEmpty()) {
                                String expectedTabName = expectedPrefix + player.getName() + expectedSuffix;
                                String cachedTabName = tabListManager.getNonRainbowTabCache().get(uuid);

                                if (!expectedTabName.equals(cachedTabName)) {
                                    String currentTabName = player.getPlayerListName();

                                    if (!expectedTabName.equals(currentTabName)) {
                                        player.setPlayerListName(expectedTabName);
                                    }

                                    tabListManager.getNonRainbowTabCache().put(uuid, expectedTabName);
                                }
                            }

                            tabListManager.getLastTabCheck().put(uuid, currentTime);
                        }
                    }
                }
            }
        };

        nonRainbowUpdateTask.runTaskTimer(plugin, 20L, 10L);
    }
}