package com.Lino.rainbowNickname.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final JavaPlugin plugin;

    private int animationSpeed;
    private boolean useBold;
    private double nametagHeight;
    private double positionThreshold;
    private boolean useAsyncTasks;
    private int maxCacheSize;
    private boolean keepPrefixSuffix;
    private boolean animatePrefixSuffix;
    private int luckpermsJoinDelay;
    private boolean readFromTab;
    private boolean debugMode;
    private boolean useTabList;
    private String tabHeader;
    private String tabFooter;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        loadConfiguration();
    }

    public void reload() {
        plugin.reloadConfig();
        loadConfiguration();
    }

    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();

        animationSpeed = Math.max(1, config.getInt("animation-speed", 10));
        useBold = config.getBoolean("use-bold", true);
        nametagHeight = config.getDouble("nametag-height", 2.3);
        positionThreshold = config.getDouble("position-threshold", 0.1);
        useAsyncTasks = config.getBoolean("use-async-tasks", true);
        maxCacheSize = config.getInt("cache.max-size", 1000);
        keepPrefixSuffix = config.getBoolean("keep-prefix-suffix", true);
        animatePrefixSuffix = config.getBoolean("animate-prefix-suffix", false);

        luckpermsJoinDelay = config.getInt("luckperms.join-delay", 40);
        readFromTab = config.getBoolean("luckperms.read-from-tab", true);

        debugMode = config.getBoolean("performance.debug", false);

        useTabList = config.getBoolean("tab-list.enabled", true);
        tabHeader = ChatColor.translateAlternateColorCodes('&',
                config.getString("tab-list.header", "&lRainbow Server"));
        tabFooter = ChatColor.translateAlternateColorCodes('&',
                config.getString("tab-list.footer", "&7Powered by RainbowNickname"));
    }

    public int getAnimationSpeed() { return animationSpeed; }
    public boolean isUseBold() { return useBold; }
    public double getNametagHeight() { return nametagHeight; }
    public double getPositionThreshold() { return positionThreshold; }
    public boolean isUseAsyncTasks() { return useAsyncTasks; }
    public int getMaxCacheSize() { return maxCacheSize; }
    public boolean isKeepPrefixSuffix() { return keepPrefixSuffix; }
    public boolean isAnimatePrefixSuffix() { return animatePrefixSuffix; }
    public int getLuckpermsJoinDelay() { return luckpermsJoinDelay; }
    public boolean isReadFromTab() { return readFromTab; }
    public boolean isDebugMode() { return debugMode; }
    public boolean isUseTabList() { return useTabList; }
    public String getTabHeader() { return tabHeader; }
    public String getTabFooter() { return tabFooter; }
}