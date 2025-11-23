package com.Lino.rainbowNickname.commands;

import com.Lino.rainbowNickname.RainbowNickname;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

            case "toggle":
            case "set":
                if (!sender.hasPermission("rainbownick.command.toggle")) {
                    plugin.getMessageManager().sendMessage(sender, "no-permission");
                    return true;
                }

                Player target;
                if (args.length > 1) {
                    if (!sender.hasPermission("rainbownick.admin")) {
                        plugin.getMessageManager().sendMessage(sender, "no-permission");
                        return true;
                    }
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        plugin.getMessageManager().sendMessage(sender, "player-not-found");
                        return true;
                    }
                } else if (sender instanceof Player) {
                    target = (Player) sender;
                } else {
                    plugin.getMessageManager().sendMessage(sender, "only-players");
                    return true;
                }

                // Logica aggiornata con DataManager
                boolean currentlyEnabled = plugin.getNickManager().hasNick(target);

                if (currentlyEnabled) {
                    // Disabilita visivamente
                    plugin.getNickManager().disableNick(target);
                    // Salva nel database che è disabilitato
                    plugin.getDataManager().setNickEnabled(target.getUniqueId(), false);

                    plugin.getMessageManager().sendMessage(sender, "nick-disabled", "%player%", target.getName());
                } else {
                    // Abilita visivamente
                    plugin.getNickManager().enableNick(target);
                    // Salva nel database che è abilitato
                    plugin.getDataManager().setNickEnabled(target.getUniqueId(), true);

                    plugin.getMessageManager().sendMessage(sender, "nick-enabled", "%player%", target.getName());
                }
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
        if (sender.hasPermission("rainbownick.command.toggle")) {
            plugin.getMessageManager().sendRawMessage(sender, "help-toggle");
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
            if (sender.hasPermission("rainbownick.command.toggle")) options.add("toggle");
            if (sender.hasPermission("rainbownick.admin")) options.add("reload");
            options.add("help");
            return options;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle") && sender.hasPermission("rainbownick.admin")) {
            return null;
        }
        return Collections.emptyList();
    }
}