package com.Lino.rainbowNickname;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
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

    public AnimationType getPlayerAnimation(UUID uuid) {
        String typeName = config.getString("players." + uuid.toString());
        if (typeName == null) return null;
        try {
            return AnimationType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setPlayerAnimation(UUID uuid, AnimationType type) {
        if (type == null) {
            config.set("players." + uuid.toString(), null);
        } else {
            config.set("players." + uuid.toString(), type.name());
        }
        saveData();
    }
}