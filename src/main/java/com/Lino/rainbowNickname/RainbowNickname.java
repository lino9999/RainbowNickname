package com.Lino.rainbowNickname;

import com.Lino.rainbowNickname.commands.MainCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class RainbowNickname extends JavaPlugin implements Listener {

    private NickManager nickManager;
    private MessageManager messageManager;
    private DataManager dataManager;
    private HookManager hookManager;
    private BukkitRunnable animationTask;
    private float rainbowPhase = 0.0f;

    @Override
    public void onEnable() {
        this.messageManager = new MessageManager(this);
        this.dataManager = new DataManager(this);
        this.hookManager = new HookManager();
        this.nickManager = new NickManager(this);

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("rainbownick").setExecutor(new MainCommand(this));

        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            getLogger().info("LuckPerms detected! Prefixes enabled.");
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreNick(player);
        }

        startAnimationLoop();
        getLogger().info("RainbowNickname v2.3 enabled (Smooth Mode)!");
    }

    @Override
    public void onDisable() {
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }
        nickManager.removeAll();
        getLogger().info("RainbowNickname disabled.");
    }

    public void reloadPlugin() {
        onDisable();
        onEnable();
        messageManager.loadMessages();
        dataManager.loadData();
    }

    private void restoreNick(Player player) {
        AnimationType type = dataManager.getPlayerAnimation(player.getUniqueId());
        if (type != null) {
            nickManager.setAnimation(player, type);
        }
    }

    private void startAnimationLoop() {
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                // MODIFICA QUI: Da 0.05f a 0.02f per maggiore fluidità
                rainbowPhase -= 0.02f;

                // Reset fase
                if (rainbowPhase < -1000.0f) rainbowPhase = 0.0f;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (nickManager.hasNick(player)) {
                        try {
                            nickManager.updateNick(player, rainbowPhase);
                        } catch (Exception ignored) {}
                    }
                }
            }
        };
        // Gira sempre a 1 tick per fluidità massima
        animationTask.runTaskTimer(this, 1L, 1L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                restoreNick(player);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nickManager.disableNick(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (nickManager.hasNick(player)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    nickManager.refreshPassenger(player);
                }
            }, 5L);
        }
    }

    public NickManager getNickManager() { return nickManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public DataManager getDataManager() { return dataManager; }
    public HookManager getHookManager() { return hookManager; }
}