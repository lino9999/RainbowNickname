package com.example.rainbownickname;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RainbowNicknameEnhanced extends JavaPlugin implements Listener {

    private static final String PERMISSION_USE = "rainbownick.use";
    private static final String PERMISSION_ADMIN = "rainbownick.admin";
    private static final String PERMISSION_TOGGLE = "rainbownick.toggle";

    private static final ChatColor[] RAINBOW_COLORS = {
            ChatColor.RED,
            ChatColor.GOLD,
            ChatColor.YELLOW,
            ChatColor.GREEN,
            ChatColor.AQUA,
            ChatColor.BLUE,
            ChatColor.LIGHT_PURPLE
    };

    // Uso ConcurrentHashMap per thread safety
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    private BukkitRunnable animationTask;
    private Scoreboard scoreboard;
    private int animationSpeed = 4; // Tick tra aggiornamenti

    private static class PlayerData {
        final String originalName;
        int colorIndex;

        PlayerData(String originalName) {
            this.originalName = originalName;
            this.colorIndex = 0;
        }
    }

    @Override
    public void onEnable() {
        // Salva config default
        saveDefaultConfig();

        // Carica velocità animazione dalla config
        animationSpeed = getConfig().getInt("animation-speed", 4);

        // Registra eventi
        getServer().getPluginManager().registerEvents(this, this);

        // Inizializza scoreboard
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Pulisci vecchi team
        cleanupOldTeams();

        // Avvia l'animazione
        startAnimation();

        // Applica a tutti i giocatori online
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldHaveRainbow(player)) {
                setupPlayer(player);
            }
        }

        getLogger().info("RainbowNickname Enhanced abilitato!");
    }

    @Override
    public void onDisable() {
        // Ferma l'animazione
        if (animationTask != null) {
            animationTask.cancel();
        }

        // Ripristina tutti i giocatori
        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayer(player);
        }

        // Pulisci i team
        cleanupOldTeams();

        // Pulisci le mappe
        playerData.clear();
        disabledPlayers.clear();

        getLogger().info("RainbowNickname Enhanced disabilitato!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rainbownick")) {
            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "toggle":
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Questo comando può essere usato solo dai giocatori!");
                        return true;
                    }

                    Player player = (Player) sender;
                    if (!player.hasPermission(PERMISSION_TOGGLE)) {
                        player.sendMessage(ChatColor.RED + "Non hai il permesso per usare questo comando!");
                        return true;
                    }

                    toggleRainbow(player);
                    return true;

                case "reload":
                    if (!sender.hasPermission(PERMISSION_ADMIN)) {
                        sender.sendMessage(ChatColor.RED + "Non hai il permesso per usare questo comando!");
                        return true;
                    }

                    reloadPlugin();
                    sender.sendMessage(ChatColor.GREEN + "Plugin ricaricato con successo!");
                    return true;

                case "speed":
                    if (!sender.hasPermission(PERMISSION_ADMIN)) {
                        sender.sendMessage(ChatColor.RED + "Non hai il permesso per usare questo comando!");
                        return true;
                    }

                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Uso: /rainbownick speed <tick>");
                        return true;
                    }

                    try {
                        int speed = Integer.parseInt(args[1]);
                        if (speed < 1 || speed > 20) {
                            sender.sendMessage(ChatColor.RED + "La velocità deve essere tra 1 e 20 tick!");
                            return true;
                        }

                        setAnimationSpeed(speed);
                        sender.sendMessage(ChatColor.GREEN + "Velocità animazione impostata a " + speed + " tick!");
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Inserisci un numero valido!");
                    }
                    return true;

                default:
                    sendHelp(sender);
                    return true;
            }
        }

        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Rimuovi dal set dei disabilitati se presente
        disabledPlayers.remove(player.getUniqueId());

        if (shouldHaveRainbow(player)) {
            // Delay di 1 tick per assicurarsi che il giocatore sia completamente caricato
            Bukkit.getScheduler().runTaskLater(this, () -> setupPlayer(player), 1L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Pulisci i dati del giocatore
        playerData.remove(uuid);

        // Rimuovi il team
        removePlayerTeam(uuid);
    }

    private boolean shouldHaveRainbow(Player player) {
        return player.hasPermission(PERMISSION_USE) && !disabledPlayers.contains(player.getUniqueId());
    }

    private void setupPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Salva i dati del giocatore
        playerData.put(uuid, new PlayerData(player.getName()));

        // Crea o ottieni il team del giocatore
        String teamName = getTeamName(uuid);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        // Configura il team
        team.addEntry(player.getName());
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        // Applica il colore iniziale
        updatePlayerColor(player);
    }

    private void restorePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Rimuovi i dati
        playerData.remove(uuid);

        // Rimuovi dal team
        removePlayerTeam(uuid);

        // Ripristina il nome
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
    }

    private void removePlayerTeam(UUID uuid) {
        String teamName = getTeamName(uuid);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }

    private String getTeamName(UUID uuid) {
        // Usa solo i primi 16 caratteri per rispettare il limite di Minecraft
        return "rb_" + uuid.toString().replace("-", "").substring(0, 13);
    }

    private void startAnimation() {
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Usa un set temporaneo per evitare ConcurrentModificationException
                Set<Player> playersToUpdate = new HashSet<>();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (playerData.containsKey(player.getUniqueId())) {
                        playersToUpdate.add(player);
                    }
                }

                // Aggiorna i giocatori
                for (Player player : playersToUpdate) {
                    updatePlayerColor(player);
                }
            }
        };

        animationTask.runTaskTimer(this, 0L, animationSpeed);
    }

    private void updatePlayerColor(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerData.get(uuid);

        if (data == null) return;

        String originalName = data.originalName;
        int baseColorIndex = data.colorIndex;

        // Costruisci il nome colorato per chat e tab list
        StringBuilder coloredName = new StringBuilder();
        for (int i = 0; i < originalName.length(); i++) {
            char c = originalName.charAt(i);
            int colorIndex = (baseColorIndex + i) % RAINBOW_COLORS.length;
            coloredName.append(RAINBOW_COLORS[colorIndex]).append(c);
        }

        // Aggiorna il team per il nome sopra la testa
        String teamName = getTeamName(uuid);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            // Usa il colore del primo carattere per il team
            ChatColor firstColor = RAINBOW_COLORS[baseColorIndex];
            team.setPrefix(firstColor.toString());

            // Per versioni 1.13+
            try {
                team.setColor(firstColor);
            } catch (NoSuchMethodError ignored) {
                // Versione precedente
            }
        }

        // Aggiorna display name e tab list
        player.setDisplayName(coloredName.toString());
        player.setPlayerListName(coloredName.toString());

        // Incrementa l'indice per il prossimo frame
        data.colorIndex = (data.colorIndex + 1) % RAINBOW_COLORS.length;
    }

    private void toggleRainbow(Player player) {
        UUID uuid = player.getUniqueId();

        if (disabledPlayers.contains(uuid)) {
            // Riabilita
            disabledPlayers.remove(uuid);
            if (player.hasPermission(PERMISSION_USE)) {
                setupPlayer(player);
                player.sendMessage(ChatColor.GREEN + "Nickname arcobaleno abilitato!");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Non hai il permesso per il nickname arcobaleno!");
            }
        } else {
            // Disabilita
            disabledPlayers.add(uuid);
            restorePlayer(player);
            player.sendMessage(ChatColor.YELLOW + "Nickname arcobaleno disabilitato!");
        }
    }

    private void setAnimationSpeed(int speed) {
        animationSpeed = speed;
        getConfig().set("animation-speed", speed);
        saveConfig();

        // Riavvia l'animazione con la nuova velocità
        if (animationTask != null) {
            animationTask.cancel();
        }
        startAnimation();
    }

    private void reloadPlugin() {
        // Ricarica config
        reloadConfig();
        animationSpeed = getConfig().getInt("animation-speed", 4);

        // Riavvia animazione
        if (animationTask != null) {
            animationTask.cancel();
        }
        startAnimation();
    }

    private void cleanupOldTeams() {
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith("rb_") || team.getName().startsWith("rainbow_")) {
                team.unregister();
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== RainbowNickname Help ===");

        if (sender.hasPermission(PERMISSION_TOGGLE)) {
            sender.sendMessage(ChatColor.YELLOW + "/rainbownick toggle" + ChatColor.WHITE + " - Attiva/disattiva il tuo nickname arcobaleno");
        }

        if (sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(ChatColor.YELLOW + "/rainbownick reload" + ChatColor.WHITE + " - Ricarica il plugin");
            sender.sendMessage(ChatColor.YELLOW + "/rainbownick speed <tick>" + ChatColor.WHITE + " - Imposta la velocità dell'animazione (1-20)");
        }
    }
}