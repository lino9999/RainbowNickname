package com.Lino.rainbowNickname.commands;

import com.Lino.rainbowNickname.AnimationType;
import com.Lino.rainbowNickname.RainbowNickname;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final RainbowNickname plugin;

    public MainCommand(RainbowNickname plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("rainbownick.admin")) {
                    plugin.getMessageManager().sendMessage(sender, "no-permission");
                    return true;
                }
                plugin.reloadPlugin();
                plugin.getMessageManager().sendMessage(sender, "reload-success");
                break;

            case "set":
                if (!sender.hasPermission("rainbownick.command.set")) {
                    plugin.getMessageManager().sendMessage(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    plugin.getMessageManager().sendMessage(sender, "usage-set");
                    return true;
                }

                String typeName = args[1].toUpperCase();
                AnimationType type;
                try {
                    type = AnimationType.valueOf(typeName);
                } catch (IllegalArgumentException e) {
                    plugin.getMessageManager().sendMessage(sender, "invalid-type");
                    return true;
                }

                Player targetSet;
                if (args.length > 2) {
                    if (!sender.hasPermission("rainbownick.admin")) {
                        plugin.getMessageManager().sendMessage(sender, "no-permission");
                        return true;
                    }
                    targetSet = Bukkit.getPlayer(args[2]);
                    if (targetSet == null) {
                        plugin.getMessageManager().sendMessage(sender, "player-not-found");
                        return true;
                    }
                } else if (sender instanceof Player) {
                    targetSet = (Player) sender;
                } else {
                    plugin.getMessageManager().sendMessage(sender, "only-players");
                    return true;
                }

                plugin.getNickManager().setAnimation(targetSet, type);
                plugin.getDataManager().setPlayerAnimation(targetSet.getUniqueId(), type);

                plugin.getMessageManager().sendMessage(sender, "nick-set", "%type%", type.getDisplayName());
                break;

            case "off":
            case "disable":
                if (!sender.hasPermission("rainbownick.command.set")) {
                    plugin.getMessageManager().sendMessage(sender, "no-permission");
                    return true;
                }

                Player targetOff;
                if (args.length > 1) {
                    if (!sender.hasPermission("rainbownick.admin")) {
                        plugin.getMessageManager().sendMessage(sender, "no-permission");
                        return true;
                    }
                    targetOff = Bukkit.getPlayer(args[1]);
                    if (targetOff == null) {
                        plugin.getMessageManager().sendMessage(sender, "player-not-found");
                        return true;
                    }
                } else if (sender instanceof Player) {
                    targetOff = (Player) sender;
                } else {
                    plugin.getMessageManager().sendMessage(sender, "only-players");
                    return true;
                }

                plugin.getNickManager().disableNick(targetOff);
                plugin.getDataManager().setPlayerAnimation(targetOff.getUniqueId(), null);
                plugin.getMessageManager().sendMessage(sender, "nick-disabled", "%player%", targetOff.getName());
                break;

            case "help":
                sendHelp(sender);
                break;

            default:
                plugin.getMessageManager().sendMessage(sender, "usage");
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageManager().sendRawMessage(sender, "help-header");
        if (sender.hasPermission("rainbownick.command.set")) {
            plugin.getMessageManager().sendRawMessage(sender, "help-set");
            plugin.getMessageManager().sendRawMessage(sender, "help-off");
        }
        if (sender.hasPermission("rainbownick.admin")) {
            plugin.getMessageManager().sendRawMessage(sender, "help-reload");
        }
        plugin.getMessageManager().sendRawMessage(sender, "help-help");
        plugin.getMessageManager().sendRawMessage(sender, "help-footer");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("rainbownick.command.set")) {
                options.add("set");
                options.add("off");
            }
            if (sender.hasPermission("rainbownick.admin")) options.add("reload");
            options.add("help");
            return options;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set") && sender.hasPermission("rainbownick.command.set")) {
            return Arrays.stream(AnimationType.values()).map(Enum::name).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}