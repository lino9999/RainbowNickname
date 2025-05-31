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
        // Salva il config.yml di default se non esiste
        saveDefaultConfig();

        // Carica le configurazioni
        loadConfiguration();

        // Registra eventi
        getServer().getPluginManager().registerEvents(this, this);

        // Inizializza scoreboard
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Pulisci vecchi team se esistono
        cleanupOldTeams();

        // Applica a tutti i giocatori online (con un piccolo delay)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(PERMISSION)) {
                    setupPlayer(player);
                }
            }
            // Avvia l'animazione dopo aver configurato i giocatori
            startAnimation();
        }, 20L); // 1 secondo di delay

        getLogger().info("RainbowNickname abilitato!");
    }

    private void loadConfiguration() {
        // Carica la velocità dell'animazione (in ticks)
        animationSpeed = getConfig().getInt("animation-speed", 10);

        // Carica l'opzione per il testo in grassetto
        useBold = getConfig().getBoolean("use-bold", true);

        // Carica i colori
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
        if (animationTask != null && !animationTask.isCancelled()) {
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
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Configura il giocatore con un piccolo delay per assicurarsi che sia completamente caricato
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && player.hasPermission(PERMISSION)) {
                setupPlayer(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Rimuovi dalla mappa
        playerColorIndex.remove(uuid);
        originalNames.remove(uuid);

        // Rimuovi dal team
        String teamName = "rn_" + uuid.toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }

    private void setupPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Salva il nome originale
        originalNames.put(uuid, player.getName());

        // Inizializza l'indice del colore casualmente per varietà
        playerColorIndex.put(uuid, (int) (Math.random() * RAINBOW_COLORS.length));

        // Crea un team personale per il giocatore (nome più corto per compatibilità)
        String teamName = "rn_" + uuid.toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister(); // Rimuovi il vecchio team se esiste
        }
        team = scoreboard.registerNewTeam(teamName);

        // IMPORTANTE: Imposta il colore del team come RESET per nascondere il nome originale
        team.setColor(ChatColor.RESET);

        // Aggiungi il giocatore al team
        team.addEntry(player.getName());

        // Imposta il prefisso iniziale
        updatePlayerColor(player);

        getLogger().info("Configurato giocatore: " + player.getName() + " con team: " + teamName);
    }

    private void restorePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Rimuovi dal team
        String teamName = "rn_" + uuid.toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.removeEntry(player.getName());
            team.unregister();
        }

        // Ripristina i nomi originali
        String originalName = originalNames.get(uuid);
        if (originalName != null) {
            player.setDisplayName(originalName);
            player.setPlayerListName(originalName);
        } else {
            // Fallback al nome corrente del giocatore
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
        }
    }

    private void startAnimation() {
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }

        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission(PERMISSION) && playerColorIndex.containsKey(player.getUniqueId())) {
                        updatePlayerColor(player);
                    }
                }
            }
        };

        // Usa la velocità configurata
        animationTask.runTaskTimer(this, 0L, animationSpeed);
        getLogger().info("Animazione avviata con velocità: " + animationSpeed + " ticks");
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

        // Aggiorna il team per il nome sopra la testa
        String teamName = "rn_" + uuid.toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            // Costruisci il nome completo arcobaleno per la nametag
            StringBuilder nametagName = new StringBuilder();
            for (int i = 0; i < originalName.length(); i++) {
                char c = originalName.charAt(i);
                int currentColorIndex = (colorIndex + i) % RAINBOW_COLORS.length;
                nametagName.append(RAINBOW_COLORS[currentColorIndex]);
                if (useBold) {
                    nametagName.append(ChatColor.BOLD);
                }
                nametagName.append(c);
            }

            String fullName = nametagName.toString();

            // Gestisce la distribuzione tra prefix e suffix
            if (fullName.length() <= 16) {
                team.setPrefix(fullName);
                team.setSuffix("");
            } else {
                // Per nomi lunghi, dividi intelligentemente
                String prefix = "";
                String suffix = "";

                int prefixLength = 0;
                int charCount = 0;

                // Calcola quanti caratteri effettivi possiamo mettere nel prefix
                for (int i = 0; i < originalName.length() && prefixLength < 14; i++) {
                    char c = originalName.charAt(i);
                    int currentColorIndex = (colorIndex + i) % RAINBOW_COLORS.length;

                    String colorCode = RAINBOW_COLORS[currentColorIndex].toString();
                    String boldCode = useBold ? ChatColor.BOLD.toString() : "";

                    int totalLength = colorCode.length() + boldCode.length() + 1; // +1 per il carattere

                    if (prefixLength + totalLength <= 16) {
                        prefix += colorCode + boldCode + c;
                        prefixLength += totalLength;
                        charCount++;
                    } else {
                        break;
                    }
                }

                // Il resto va nel suffix
                for (int i = charCount; i < originalName.length() && suffix.length() < 14; i++) {
                    char c = originalName.charAt(i);
                    int currentColorIndex = (colorIndex + i) % RAINBOW_COLORS.length;

                    String colorCode = RAINBOW_COLORS[currentColorIndex].toString();
                    String boldCode = useBold ? ChatColor.BOLD.toString() : "";

                    if (suffix.length() + colorCode.length() + boldCode.length() + 1 <= 16) {
                        suffix += colorCode + boldCode + c;
                    } else {
                        break;
                    }
                }

                team.setPrefix(prefix);
                team.setSuffix(suffix);
            }
        }

        // Mantieni il nome originale per la chat
        player.setDisplayName(originalNames.get(uuid));

        // Costruisci il nome arcobaleno per la TAB
        StringBuilder coloredTabName = new StringBuilder();
        for (int i = 0; i < originalName.length(); i++) {
            char c = originalName.charAt(i);
            int currentColorIndex = (colorIndex + i) % RAINBOW_COLORS.length;

            coloredTabName.append(RAINBOW_COLORS[currentColorIndex]);

            if (useBold) {
                coloredTabName.append(ChatColor.BOLD);
            }

            coloredTabName.append(c);
        }

        // Aggiorna il player list name (tab)
        player.setPlayerListName(coloredTabName.toString());

        // Incrementa l'indice del colore per il prossimo aggiornamento
        playerColorIndex.put(uuid, (colorIndex + 1) % RAINBOW_COLORS.length);
    }

    private void cleanupOldTeams() {
        try {
            // Rimuovi tutti i team rainbow esistenti
            for (Team team : scoreboard.getTeams()) {
                if (team.getName().startsWith("rainbow_") || team.getName().startsWith("rn_")) {
                    team.unregister();
                }
            }
        } catch (Exception e) {
            getLogger().warning("Errore durante la pulizia dei team: " + e.getMessage());
        }
    }
}