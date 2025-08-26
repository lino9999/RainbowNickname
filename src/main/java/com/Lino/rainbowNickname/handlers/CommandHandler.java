package com.Lino.rainbowNickname.handlers;

import com.Lino.rainbowNickname.RainbowNickname;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandHandler implements CommandExecutor {
    private static final String ADMIN_PERMISSION = "rainbownick.admin";
    private final RainbowNickname plugin;

    public CommandHandler(RainbowNickname plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rainbownick")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to reload the plugin!");
                    return true;
                }

                plugin.reloadPlugin(sender);
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "=== RainbowNickname Help ===");
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(ChatColor.YELLOW + "/rainbownick reload" + ChatColor.WHITE + " - Reload the plugin");
            }
            return true;
        }
        return false;
    }
}