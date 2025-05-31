package com.Lino.rainbowNickname;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RainbowNickname extends JavaPlugin implements Listener {

    private static final String PERMISSION = "rainbownick.use";
    private ChatColor[] RAINBOW_COLORS;

    private final Map<UUID, Integer> playerColorIndex = new HashMap<>();
    private final Map<UUID, String> originalNames = new HashMap<>();
    private BukkitRunnable animationTask;
    private Scoreboard scoreboard;
    private int animationSpeed;
    private boolean useBold;

    @Override
    public void onEnable() {
        // IMPORTANTE: Salva il config.yml di default se non esiste
        saveDefaultConfig();

        // Carica le configurazioni
        loadConfiguration();

        // Registra eventi
        getServer().getPluginManager().registerEvents(this, this);

        // Inizializza scoreboard
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Pulisci vecchi team se esistono
        cleanupOldTeams();

        // Avvia l'animazione
        startAnimation();

        // Applica a tutti i giocatori online
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERMISSION)) {
                setupPlayer(player);
            }
        }

        getLogger().info("RainbowNickname abilitato!");
    }

    private void loadConfiguration() {
        // Carica la velocità dell'animazione
        animationSpeed = getConfig().getInt("animation-speed", 4);

        // Carica l'opzione per il testo in grassetto
        useBold = getConfig().getBoolean("use-bold", true);

        // Carica i colori (per ora usa quelli di default)
        RAINBOW_COLORS = new ChatColor[]{
                ChatColor.RED,
                ChatColor.GOLD,
                ChatColor.YELLOW,
                ChatColor.GREEN,
                ChatColor.AQUA,
                ChatColor.BLUE,
                ChatColor.LIGHT_PURPLE
        };


    }

    @Override
    public void onDisable() {
        // Ferma l'animazione
        if (animationTask != null) {
            animationTask.cancel();
        }

        // Ripristina i nomi originali
        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayer(player);
        }

        // Pulisci i team
        cleanupOldTeams();

        getLogger().info("RainbowNickname disabilitato!");
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Rimuovi dalla mappa
        playerColorIndex.remove(uuid);
        originalNames.remove(uuid);

        // Rimuovi dal team
        String teamName = "rainbow_" + uuid.toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }

    private void setupPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Salva il nome originale
        originalNames.put(uuid, player.getName());

        // Inizializza l'indice del colore
        playerColorIndex.put(uuid, 0);

        // Crea un team personale per il giocatore
        String teamName = "rainbow_" + uuid.toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        // Aggiungi il giocatore al team
        team.addEntry(player.getName());

        // Imposta il prefisso iniziale
        updatePlayerColor(player);
    }

    private void restorePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Rimuovi dal team
        String teamName = "rainbow_" + uuid.toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.removeEntry(player.getName());
            team.unregister();
        }

        // Ripristina il display name
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
    }

    private void startAnimation() {
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission(PERMISSION)) {
                        updatePlayerColor(player);
                    }
                }
            }
        };

        // Usa la velocità configurata
        animationTask.runTaskTimer(this, 0L, animationSpeed);
    }

    private void updatePlayerColor(Player player) {
        UUID uuid = player.getUniqueId();

        if (!playerColorIndex.containsKey(uuid)) {
            return;
        }

        String originalName = originalNames.get(uuid);
        if (originalName == null) {
            return;
        }

        // Ottieni l'indice corrente del colore
        int colorIndex = playerColorIndex.get(uuid);

        // Costruisci il nome colorato
        StringBuilder coloredName = new StringBuilder();
        for (int i = 0; i < originalName.length(); i++) {
            char c = originalName.charAt(i);
            int currentColorIndex = (colorIndex + i) % RAINBOW_COLORS.length;
            coloredName.append(RAINBOW_COLORS[currentColorIndex]);

            // Aggiungi grassetto se configurato
            if (useBold) {
                coloredName.append(ChatColor.BOLD);
            }

            coloredName.append(c);
        }

        // Aggiorna il team prefix (sopra la testa)
        String teamName = "rainbow_" + uuid.toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            // Imposta il prefisso con il primo colore
            team.setPrefix(RAINBOW_COLORS[colorIndex].toString());

            // Per Minecraft 1.13+, possiamo usare setColor per colorare l'intero nome
            try {
                team.setColor(RAINBOW_COLORS[colorIndex]);
            } catch (NoSuchMethodError e) {
                // Versione precedente, ignora
            }
        }

        // Aggiorna il display name (chat)
        player.setDisplayName(coloredName.toString());

        // Aggiorna il player list name (tab)
        player.setPlayerListName(coloredName.toString());

        // Incrementa l'indice del colore per il prossimo aggiornamento
        playerColorIndex.put(uuid, (colorIndex + 1) % RAINBOW_COLORS.length);
    }

    private void cleanupOldTeams() {
        // Rimuovi tutti i team rainbow esistenti
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith("rainbow_")) {
                team.unregister();
            }
        }
    }
}