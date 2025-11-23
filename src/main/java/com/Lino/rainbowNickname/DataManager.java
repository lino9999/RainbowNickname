package com.Lino.rainbowNickname;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataManager {

    private final RainbowNickname plugin;
    private File file;
    private FileConfiguration config;

    public DataManager(RainbowNickname plugin) {
        this.plugin = plugin;
        loadData();
    }

    public void loadData() {
        file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void saveData() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isNickEnabled(UUID uuid) {
        List<String> enabledPlayers = config.getStringList("enabled-players");
        return enabledPlayers.contains(uuid.toString());
    }

    public void setNickEnabled(UUID uuid, boolean enabled) {
        List<String> enabledPlayers = config.getStringList("enabled-players");
        String uuidStr = uuid.toString();

        if (enabled) {
            if (!enabledPlayers.contains(uuidStr)) {
                enabledPlayers.add(uuidStr);
            }
        } else {
            enabledPlayers.remove(uuidStr);
        }

        config.set("enabled-players", enabledPlayers);
        saveData();
    }
}