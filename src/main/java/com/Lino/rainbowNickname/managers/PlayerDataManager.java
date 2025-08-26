package com.Lino.rainbowNickname.managers;

import com.Lino.rainbowNickname.data.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private final Set<UUID> permittedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ConfigManager config;
    private final AnimationManager animationManager;
    private final TabListManager tabListManager;

    public PlayerDataManager(ConfigManager config, AnimationManager animationManager, TabListManager tabListManager) {
        this.config = config;
        this.animationManager = animationManager;
        this.tabListManager = tabListManager;
    }

    public PlayerData createPlayerData(Player player, ArmorStand armorStand) {
        int startIndex = (int) (Math.random() * AnimationManager.RAINBOW_COLORS.length);
        PlayerData data = new PlayerData(player.getName(), armorStand, startIndex);

        String tabNameToUse = tabListManager.getOriginalTabName(player.getUniqueId());
        if (tabNameToUse == null) {
            tabNameToUse = player.getPlayerListName();
        }
        data.originalListName = tabNameToUse;

        return data;
    }

    public void addPlayerData(UUID uuid, PlayerData data) {
        playerData.put(uuid, data);
        permittedPlayers.add(uuid);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerData.get(uuid);
    }

    public PlayerData removePlayerData(UUID uuid) {
        permittedPlayers.remove(uuid);
        return playerData.remove(uuid);
    }

    public void updatePlayerColor(UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data == null) return;

        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        String[] cachedNames = animationManager.getCachedNames(data.originalName);
        if (cachedNames == null) return;

        String rainbowName = cachedNames[data.colorIndex];

        if (!rainbowName.equals(data.lastArmorStandName)) {
            data.armorStand.setCustomName(rainbowName);
            data.lastArmorStandName = rainbowName;
        }

        String prefix = "";
        String suffix = "";

        if (config.isKeepPrefixSuffix()) {
            if (!data.cachedPrefix.isEmpty() || !data.cachedSuffix.isEmpty()) {
                prefix = data.cachedPrefix;
                suffix = data.cachedSuffix;
            } else {
                String[] prefixSuffix = tabListManager.getPrefixSuffix(player);
                prefix = prefixSuffix[0];
                suffix = prefixSuffix[1];

                if (!prefix.isEmpty() || !suffix.isEmpty()) {
                    data.cachedPrefix = prefix;
                    data.cachedSuffix = suffix;
                }
            }

            if (prefix.isEmpty() && data.originalListName != null && !data.originalListName.equals(player.getName())) {
                String originalList = data.originalListName;
                String playerName = player.getName();

                String cleanList = ChatColor.stripColor(originalList);
                int namePos = cleanList.indexOf(playerName);

                if (namePos > 0) {
                    int coloredPos = 0;
                    int cleanPos = 0;

                    while (cleanPos < namePos && coloredPos < originalList.length()) {
                        if (originalList.charAt(coloredPos) == '§' && coloredPos + 1 < originalList.length()) {
                            coloredPos += 2;
                        } else {
                            cleanPos++;
                            coloredPos++;
                        }
                    }

                    prefix = originalList.substring(0, coloredPos);
                    data.cachedPrefix = prefix;
                }
            }
        }

        String fullTabName;

        if (config.isAnimatePrefixSuffix() && config.isKeepPrefixSuffix()) {
            String animatedPrefix = animationManager.applyRainbowEffect(ChatColor.stripColor(prefix), data.colorIndex);
            String animatedSuffix = animationManager.applyRainbowEffect(ChatColor.stripColor(suffix), data.colorIndex);
            fullTabName = animatedPrefix + rainbowName + animatedSuffix;
        } else if (config.isKeepPrefixSuffix()) {
            fullTabName = prefix + rainbowName + suffix;
        } else {
            fullTabName = rainbowName;
        }

        if (!fullTabName.equals(data.lastTabName)) {
            player.setPlayerListName(fullTabName);
            data.lastTabName = fullTabName;
        }

        player.setDisplayName(data.originalName);

        data.colorIndex = (data.colorIndex + 1) % AnimationManager.RAINBOW_COLORS.length;
    }

    public void updatePrefixSuffix(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerData.get(uuid);
        if (data == null) return;

        boolean foundLuckPermsTeam = false;

        for (Team team : tabListManager.getScoreboard().getTeams()) {
            if (!team.getName().startsWith("rn_") && team.hasEntry(player.getName())) {
                String currentPrefix = team.getPrefix();
                String currentSuffix = team.getSuffix();

                if (!currentPrefix.equals(data.cachedPrefix) || !currentSuffix.equals(data.cachedSuffix)) {
                    data.cachedPrefix = currentPrefix;
                    data.cachedSuffix = currentSuffix;
                    data.lastTabName = "";
                }

                foundLuckPermsTeam = true;
                break;
            }
        }

        if (!foundLuckPermsTeam && (!data.cachedPrefix.isEmpty() || !data.cachedSuffix.isEmpty())) {
            String displayName = player.getDisplayName();
            if (!displayName.equals(player.getName())) {
                String playerName = player.getName();
                int nameIndex = displayName.indexOf(playerName);

                if (nameIndex > 0) {
                    data.cachedPrefix = displayName.substring(0, nameIndex);
                } else {
                    data.cachedPrefix = "";
                }

                if (nameIndex >= 0 && nameIndex + playerName.length() < displayName.length()) {
                    data.cachedSuffix = displayName.substring(nameIndex + playerName.length());
                } else {
                    data.cachedSuffix = "";
                }

                data.lastTabName = "";
            }
        }
    }

    public boolean shouldUpdatePosition(Location current, Location last) {
        return last == null ||
                current.getWorld() != last.getWorld() ||
                current.distanceSquared(last) > config.getPositionThreshold() * config.getPositionThreshold();
    }

    public Map<UUID, PlayerData> getAllPlayerData() {
        return playerData;
    }

    public Set<UUID> getPermittedPlayers() {
        return permittedPlayers;
    }

    public void clearAll() {
        playerData.clear();
        permittedPlayers.clear();
    }
}