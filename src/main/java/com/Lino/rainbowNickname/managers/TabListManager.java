package com.Lino.rainbowNickname.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TabListManager {
    private final Map<UUID, String> originalTabNames = new ConcurrentHashMap<>();
    private final Map<UUID, Team> originalTeams = new ConcurrentHashMap<>();
    private final Map<UUID, String> nonRainbowTabCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTabCheck = new ConcurrentHashMap<>();

    private final ConfigManager config;
    private final AnimationManager animationManager;
    private final Scoreboard scoreboard;

    public TabListManager(ConfigManager config, AnimationManager animationManager) {
        this.config = config;
        this.animationManager = animationManager;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    public void saveOriginalTabName(Player player) {
        UUID uuid = player.getUniqueId();
        String tabName = player.getPlayerListName();
        if (tabName != null && !tabName.equals(player.getName())) {
            originalTabNames.put(uuid, tabName);
        }
    }

    public void saveOriginalTeam(Player player) {
        UUID uuid = player.getUniqueId();
        for (Team team : scoreboard.getTeams()) {
            if (!team.getName().startsWith("rn_") && team.hasEntry(player.getName())) {
                originalTeams.put(uuid, team);
                break;
            }
        }
    }

    public String getOriginalTabName(UUID uuid) {
        return originalTabNames.get(uuid);
    }

    public Team getOriginalTeam(UUID uuid) {
        return originalTeams.get(uuid);
    }

    public void hideOriginalNametag(Player player) {
        String teamName = "rn_" + player.getUniqueId().toString().substring(0, 8);

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        team.addEntry(player.getName());
    }

    public void removePlayerTeam(UUID uuid) {
        String teamName = "rn_" + uuid.toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }

    public String[] getPrefixSuffix(Player player) {
        String prefix = "";
        String suffix = "";

        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith("rn_")) {
                continue;
            }

            if (team.hasEntry(player.getName())) {
                prefix = team.getPrefix();
                suffix = team.getSuffix();
                break;
            }
        }

        if (prefix.isEmpty() && suffix.isEmpty() && player.getDisplayName() != null) {
            String displayName = player.getDisplayName();
            String playerName = player.getName();

            if (!displayName.equals(playerName)) {
                int nameIndex = displayName.indexOf(playerName);
                if (nameIndex > 0) {
                    prefix = displayName.substring(0, nameIndex);
                }
                if (nameIndex >= 0 && nameIndex + playerName.length() < displayName.length()) {
                    suffix = displayName.substring(nameIndex + playerName.length());
                }
            }
        }

        return new String[]{prefix, suffix};
    }

    public void updateTabList(Player player) {
        if (!config.isUseTabList()) return;

        String coloredHeader = animationManager.applyRainbowEffect(config.getTabHeader(), animationManager.getHeaderColorIndex());
        String coloredFooter = animationManager.applyRainbowEffect(config.getTabFooter(), animationManager.getFooterColorIndex());

        player.setPlayerListHeader(coloredHeader);
        player.setPlayerListFooter(coloredFooter);
    }

    public void updateAllTabLists() {
        animationManager.incrementHeaderColorIndex();
        animationManager.incrementFooterColorIndex();

        for (Player player : Bukkit.getOnlinePlayers()) {
            updateTabList(player);
        }
    }

    public void forceUpdateNonRainbowPlayer(Player player, String playerName) {
        if (!config.isKeepPrefixSuffix() || player.hasPermission("rainbownick.use")) return;

        UUID uuid = player.getUniqueId();

        Team savedTeam = originalTeams.get(uuid);
        String prefix = "";
        String suffix = "";

        if (savedTeam != null) {
            prefix = savedTeam.getPrefix();
            suffix = savedTeam.getSuffix();
        }

        if (prefix.isEmpty() && suffix.isEmpty()) {
            Team currentTeam = null;
            for (Team team : scoreboard.getTeams()) {
                if (!team.getName().startsWith("rn_") && team.hasEntry(playerName)) {
                    currentTeam = team;
                    prefix = team.getPrefix();
                    suffix = team.getSuffix();
                    originalTeams.put(uuid, team);
                    break;
                }
            }

            if (currentTeam == null) {
                for (Team team : scoreboard.getTeams()) {
                    if (team.hasEntry(playerName) && !team.getName().startsWith("rn_")) {
                        prefix = team.getPrefix();
                        suffix = team.getSuffix();

                        if (!prefix.isEmpty() || !suffix.isEmpty()) {
                            originalTeams.put(uuid, team);
                            break;
                        }
                    }
                }
            }
        }

        if (prefix.isEmpty() && suffix.isEmpty()) {
            String savedTabName = originalTabNames.get(uuid);
            if (savedTabName != null && !savedTabName.equals(playerName)) {
                String cleanTabName = ChatColor.stripColor(savedTabName);
                int nameIndex = cleanTabName.indexOf(playerName);

                if (nameIndex > 0) {
                    prefix = extractColoredPrefix(savedTabName, playerName);
                }

                if (nameIndex >= 0 && nameIndex + playerName.length() < cleanTabName.length()) {
                    suffix = extractColoredSuffix(savedTabName, playerName);
                }
            }
        }

        if (prefix.isEmpty() && suffix.isEmpty()) {
            String displayName = player.getDisplayName();
            if (!displayName.equals(playerName)) {
                int nameIndex = displayName.indexOf(playerName);

                if (nameIndex > 0) {
                    prefix = displayName.substring(0, nameIndex);
                }

                if (nameIndex >= 0 && nameIndex + playerName.length() < displayName.length()) {
                    suffix = displayName.substring(nameIndex + playerName.length());
                }
            }
        }

        String newTabName = prefix + playerName + suffix;

        try {
            player.setPlayerListName(newTabName);

            Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("RainbowNickname"), () -> {
                if (!player.getPlayerListName().equals(newTabName)) {
                    player.setPlayerListName(newTabName);
                }
            }, 1L);

        } catch (Exception e) {
        }
    }

    private String extractColoredPrefix(String fullName, String playerName) {
        int index = fullName.indexOf(playerName);
        if (index > 0) {
            return fullName.substring(0, index);
        }
        return "";
    }

    private String extractColoredSuffix(String fullName, String playerName) {
        int index = fullName.indexOf(playerName);
        if (index >= 0) {
            int endIndex = index + playerName.length();
            if (endIndex < fullName.length()) {
                return fullName.substring(endIndex);
            }
        }
        return "";
    }

    public void cleanupOldTeams() {
        scoreboard.getTeams().stream()
                .filter(team -> team.getName().startsWith("rn_") || team.getName().startsWith("rainbow_"))
                .forEach(Team::unregister);
    }

    public void clearCaches() {
        originalTabNames.clear();
        originalTeams.clear();
        nonRainbowTabCache.clear();
        lastTabCheck.clear();
    }

    public void removePlayerData(UUID uuid) {
        originalTabNames.remove(uuid);
        originalTeams.remove(uuid);
        nonRainbowTabCache.remove(uuid);
        lastTabCheck.remove(uuid);
    }

    public Map<UUID, String> getNonRainbowTabCache() { return nonRainbowTabCache; }
    public Map<UUID, Long> getLastTabCheck() { return lastTabCheck; }
    public Scoreboard getScoreboard() { return scoreboard; }
}