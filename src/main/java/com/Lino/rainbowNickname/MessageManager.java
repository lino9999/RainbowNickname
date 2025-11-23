package com.Lino.rainbowNickname;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public class MessageManager {

    private final RainbowNickname plugin;
    private FileConfiguration messagesConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .character('§')
            .build();

    public MessageManager(RainbowNickname plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void sendMessage(CommandSender sender, String key) {
        sendMessage(sender, key, null, null);
    }

    public void sendMessage(CommandSender sender, String key, String placeholder, String replacement) {
        String message = messagesConfig.getString(key);
        if (message == null) return;

        if (placeholder != null && replacement != null) {
            message = message.replace(placeholder, replacement);
        }

        String prefix = messagesConfig.getString("prefix", "");
        String fullMessage = prefix + message;

        Component component = miniMessage.deserialize(fullMessage);
        sender.sendMessage(legacySerializer.serialize(component));
    }

    public void sendRawMessage(CommandSender sender, String key) {
        String message = messagesConfig.getString(key);
        if (message == null) return;

        Component component = miniMessage.deserialize(message);
        sender.sendMessage(legacySerializer.serialize(component));
    }
}