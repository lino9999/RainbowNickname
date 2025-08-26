package com.Lino.rainbowNickname.handlers;

import com.Lino.rainbowNickname.RainbowNickname;
import com.Lino.rainbowNickname.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.UUID;

public class EventListener implements Listener {
    private static final String PERMISSION = "rainbownick.use";
    private final RainbowNickname plugin;

    public EventListener(RainbowNickname plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                String immediateTabName = player.getPlayerListName();
                if (immediateTabName != null && !immediateTabName.equals(player.getName())) {
                    plugin.getTabListManager().saveOriginalTabName(player);
                }
            }
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                String currentTabName = player.getPlayerListName();
                if (currentTabName != null && !currentTabName.equals(player.getName())) {
                    plugin.getTabListManager().saveOriginalTabName(player);
                }

                plugin.getTabListManager().saveOriginalTeam(player);
            }
        }, plugin.getConfigManager().getLuckpermsJoinDelay() - 20);

        if (plugin.getConfigManager().isUseAsyncTasks()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (player.hasPermission(PERMISSION)) {
                    plugin.getAnimationManager().precachePlayerNames(player.getName());

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            plugin.setupPlayer(player);
                        }
                    }, plugin.getConfigManager().getLuckpermsJoinDelay());
                } else if (plugin.getConfigManager().isKeepPrefixSuffix()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            plugin.getTabListManager().forceUpdateNonRainbowPlayer(player, player.getName());
                        }
                    }, plugin.getConfigManager().getLuckpermsJoinDelay() + 10);
                }
            });
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    if (player.hasPermission(PERMISSION)) {
                        plugin.setupPlayer(player);
                    } else if (plugin.getConfigManager().isKeepPrefixSuffix()) {
                        plugin.getTabListManager().forceUpdateNonRainbowPlayer(player, player.getName());
                    }
                }
            }, plugin.getConfigManager().getLuckpermsJoinDelay());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.cleanupPlayer(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);

        if (data != null && data.armorStand != null && !data.armorStand.isDead()) {
            Location newLocation = event.getTo().clone();
            newLocation.setY(newLocation.getY() + plugin.getConfigManager().getNametagHeight());
            data.armorStand.teleport(newLocation);
            data.lastLocation = newLocation;
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof ArmorStand) {
                ArmorStand armorStand = (ArmorStand) entity;
                if (armorStand.isMarker() && armorStand.isCustomNameVisible() &&
                        !armorStand.isVisible() && !plugin.getArmorStandManager().isPluginArmorStand(armorStand.getUniqueId())) {

                    String customName = armorStand.getCustomName();
                    if (customName != null && plugin.getAnimationManager().containsRainbowColors(customName)) {
                        armorStand.remove();
                    }
                }
            }
        }
    }
}