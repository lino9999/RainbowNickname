package com.Lino.rainbowNickname;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class HookManager {

    private LuckPerms luckPerms;
    private final boolean hasLuckPerms;

    public HookManager() {
        this.hasLuckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
        if (this.hasLuckPerms) {
            this.luckPerms = LuckPermsProvider.get();
        }
    }

    public String getPrefix(Player player) {
        if (!hasLuckPerms) return "";

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";

        String prefix = user.getCachedData().getMetaData().getPrefix();
        return prefix == null ? "" : ChatColor.translateAlternateColorCodes('&', prefix);
    }

    public String getSuffix(Player player) {
        if (!hasLuckPerms) return "";

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";

        String suffix = user.getCachedData().getMetaData().getSuffix();
        return suffix == null ? "" : ChatColor.translateAlternateColorCodes('&', suffix);
    }
}