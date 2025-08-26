package com.Lino.rainbowNickname.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class TPSMonitor {
    private static final String ADMIN_PERMISSION = "rainbownick.admin";

    private final JavaPlugin plugin;
    private final ConfigManager config;

    private double currentTPS = 20.0;
    private boolean isLowTPS = false;
    private boolean wasLowTPS = false;
    private long lastTPSCheck = System.currentTimeMillis();
    private long[] tickTimes = new long[600];
    private int tickCount = 0;

    private BukkitRunnable tpsCheckTask;

    public TPSMonitor(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        tpsCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateTPS();
                checkTPSStatus();
            }
        };

        tpsCheckTask.runTaskTimer(plugin, 100L, 100L);

        Bukkit.getScheduler().runTaskTimer(plugin, this::recordTick, 1L, 1L);
    }

    public void stop() {
        if (tpsCheckTask != null) {
            tpsCheckTask.cancel();
        }
    }

    private void recordTick() {
        tickTimes[tickCount % tickTimes.length] = System.currentTimeMillis();
        tickCount++;
    }

    private void updateTPS() {
        if (tickCount < tickTimes.length) {
            currentTPS = 20.0;
            return;
        }

        int target = (tickCount - 1 - tickTimes.length) % tickTimes.length;
        long elapsed = System.currentTimeMillis() - tickTimes[target];

        if (elapsed > 0) {
            currentTPS = tickTimes.length / (elapsed / 1000.0);
            currentTPS = Math.min(20.0, currentTPS);
            currentTPS = Math.round(currentTPS * 100.0) / 100.0;
        }
    }

    private void checkTPSStatus() {
        wasLowTPS = isLowTPS;
        isLowTPS = currentTPS < config.getMinTPS();

        if (isLowTPS != wasLowTPS) {
            if (isLowTPS) {
                notifyAdminsLowTPS();
            } else {
                notifyAdminsNormalTPS();
            }
        }
    }

    private void notifyAdminsLowTPS() {
        String message = ChatColor.YELLOW + "[RainbowNickname] " + ChatColor.RED +
                "Animation slowed down due to low TPS (" +
                String.format("%.1f", currentTPS) + "/20.0)";

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(ADMIN_PERMISSION)) {
                player.sendMessage(message);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
        }

        plugin.getLogger().warning("Low TPS detected: " + String.format("%.1f", currentTPS) +
                " - Animation speed reduced");
    }

    private void notifyAdminsNormalTPS() {
        String message = ChatColor.YELLOW + "[RainbowNickname] " + ChatColor.GREEN +
                "TPS restored (" + String.format("%.1f", currentTPS) +
                "/20.0) - Animation speed normalized";

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(ADMIN_PERMISSION)) {
                player.sendMessage(message);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            }
        }

        plugin.getLogger().info("TPS normalized: " + String.format("%.1f", currentTPS));
    }

    public double getCurrentTPS() {
        return currentTPS;
    }

    public boolean isLowTPS() {
        return isLowTPS;
    }

    public int getAdjustedAnimationSpeed() {
        if (!config.isAutoAdjustSpeed()) {
            return config.getAnimationSpeed();
        }

        if (isLowTPS) {
            double tpsRatio = currentTPS / 20.0;
            double multiplier = config.getLagSpeedMultiplier();

            int baseSpeed = config.getAnimationSpeed();
            int adjustedSpeed = (int) Math.ceil(baseSpeed / (tpsRatio * multiplier));

            return Math.min(adjustedSpeed, config.getMaxLagSpeed());
        }

        return config.getAnimationSpeed();
    }
}