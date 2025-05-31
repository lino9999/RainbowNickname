package com.Lino.rainbowNickname;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RainbowNickname extends JavaPlugin implements Listener {

    private static final String PERMISSION = "rainbownick.use";
    private ChatColor[] RAINBOW_COLORS;

    private final Map<UUID, Integer> playerColorIndex = new HashMap<>();
    private final Map<UUID, String> originalNames = new HashMap<>();
    private final Map<UUID, ArmorStand> playerArmorStands = new HashMap<>();

    // Sistema di cache per i nomi colorati
    private final Map<String, String[]> nameCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastArmorStandName = new HashMap<>();
    private final Map<UUID, String> lastTabName = new HashMap<>();

    private BukkitRunnable animationTask;
    private BukkitRunnable positionTask;
    private BukkitRunnable cacheCleanupTask;
    private Scoreboard scoreboard;
    private int animationSpeed;
    private boolean useBold;
    private double nametagHeight;
    private int cacheSize;
    private long cacheCleanupInterval;

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
            // Avvia le animazioni dopo aver configurato i giocatori
            startAnimation();
            startPositionUpdater();
            startCacheCleanup();
        }, 20L); // 1 secondo di delay

        getLogger().info("RainbowNickname abilitato con Armor Stands e Cache ottimizzata!");
    }

    private void loadConfiguration() {
        // Carica la velocità dell'animazione (in ticks)
        animationSpeed = getConfig().getInt("animation-speed", 10);

        // Carica l'opzione per il testo in grassetto
        useBold = getConfig().getBoolean("use-bold", true);

        // Carica l'altezza della nametag
        nametagHeight = getConfig().getDouble("nametag-height", 2.3);

        // Configurazioni per la cache
        cacheSize = getConfig().getInt("cache.max-size", 1000);
        cacheCleanupInterval = getConfig().getLong("cache.cleanup-interval", 300); // 5 minuti in secondi

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
        // Ferma le animazioni
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }
        if (positionTask != null && !positionTask.isCancelled()) {
            positionTask.cancel();
        }
        if (cacheCleanupTask != null && !cacheCleanupTask.isCancelled()) {
            cacheCleanupTask.cancel();
        }

        // Ripristina i nomi originali e rimuovi armor stands
        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayer(player);
        }

        // Pulisci i team e la cache
        cleanupOldTeams();
        clearCache();

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

        // Rimuovi l'armor stand
        ArmorStand armorStand = playerArmorStands.get(uuid);
        if (armorStand != null && !armorStand.isDead()) {
            armorStand.remove();
        }

        // Rimuovi dalle mappe
        playerColorIndex.remove(uuid);
        originalNames.remove(uuid);
        playerArmorStands.remove(uuid);
        lastArmorStandName.remove(uuid);
        lastTabName.remove(uuid);

        // Rimuovi dal team per nascondere la nametag originale
        String teamName = "rn_" + uuid.toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Aggiorna la posizione dell'armor stand quando il giocatore si muove
        // Questo viene gestito dal task di aggiornamento posizione per performance migliori
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Aggiorna immediatamente la posizione dell'armor stand dopo il teleport
        Bukkit.getScheduler().runTaskLater(this, () -> {
            ArmorStand armorStand = playerArmorStands.get(uuid);
            if (armorStand != null && !armorStand.isDead() && player.isOnline()) {
                Location newLocation = player.getLocation().clone();
                newLocation.setY(newLocation.getY() + nametagHeight);
                armorStand.teleport(newLocation);
            }
        }, 2L);
    }

    private void setupPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Salva il nome originale
        originalNames.put(uuid, player.getName());

        // Inizializza l'indice del colore casualmente per varietà
        playerColorIndex.put(uuid, (int) (Math.random() * RAINBOW_COLORS.length));

        // Pre-calcola e cachea i nomi per questo giocatore
        precachePlayerNames(player.getName());

        // Nascondi la nametag originale del giocatore usando un team
        hideOriginalNametag(player);

        // Crea l'armor stand per la nametag personalizzata
        createCustomNametag(player);

        // Aggiorna il colore iniziale
        updatePlayerColor(player);

        getLogger().info("Configurato giocatore: " + player.getName() + " con armor stand personalizzato");
    }

    private void precachePlayerNames(String playerName) {
        if (nameCache.containsKey(playerName)) {
            return; // Già cachato
        }

        String[] cachedNames = new String[RAINBOW_COLORS.length];

        // Pre-calcola tutti i possibili offset di colore per questo nome
        for (int offset = 0; offset < RAINBOW_COLORS.length; offset++) {
            StringBuilder rainbowName = new StringBuilder();
            for (int i = 0; i < playerName.length(); i++) {
                char c = playerName.charAt(i);
                int colorIndex = (offset + i) % RAINBOW_COLORS.length;

                rainbowName.append(RAINBOW_COLORS[colorIndex]);
                if (useBold) {
                    rainbowName.append(ChatColor.BOLD);
                }
                rainbowName.append(c);
            }
            cachedNames[offset] = rainbowName.toString();
        }

        nameCache.put(playerName, cachedNames);

        // Controlla se la cache è troppo grande
        if (nameCache.size() > cacheSize) {
            cleanupCache();
        }
    }

    private String getCachedRainbowName(String playerName, int colorOffset) {
        String[] cachedNames = nameCache.get(playerName);
        if (cachedNames == null) {
            // Se non è cachato, pre-calcolalo
            precachePlayerNames(playerName);
            cachedNames = nameCache.get(playerName);
        }

        int normalizedOffset = colorOffset % RAINBOW_COLORS.length;
        return cachedNames[normalizedOffset];
    }

    private void hideOriginalNametag(Player player) {
        UUID uuid = player.getUniqueId();
        String teamName = "rn_" + uuid.toString().substring(0, 8);

        // Rimuovi il vecchio team se esiste
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }

        // Crea un nuovo team per nascondere la nametag originale
        team = scoreboard.registerNewTeam(teamName);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.addEntry(player.getName());
    }

    private void createCustomNametag(Player player) {
        UUID uuid = player.getUniqueId();

        // Rimuovi il vecchio armor stand se esiste
        ArmorStand oldArmorStand = playerArmorStands.get(uuid);
        if (oldArmorStand != null && !oldArmorStand.isDead()) {
            oldArmorStand.remove();
        }

        // Calcola la posizione dell'armor stand (sopra il giocatore)
        Location armorStandLocation = player.getLocation().clone();
        armorStandLocation.setY(armorStandLocation.getY() + nametagHeight);

        // Crea l'armor stand
        ArmorStand armorStand = (ArmorStand) player.getWorld().spawnEntity(armorStandLocation, EntityType.ARMOR_STAND);

        // Configura l'armor stand
        armorStand.setVisible(false); // Invisibile
        armorStand.setGravity(false); // Non cade
        armorStand.setCanPickupItems(false); // Non raccoglie oggetti
        armorStand.setCustomNameVisible(true); // Nome personalizzato visibile
        armorStand.setMarker(true); // Marker (non ha hitbox)
        armorStand.setSilent(true); // Silenzioso
        armorStand.setInvulnerable(true); // Invulnerabile
        armorStand.setBasePlate(false); // Nessuna base
        armorStand.setArms(false); // Nessun braccio
        armorStand.setSmall(true); // Piccolo per meno impatto visivo

        // Salva l'armor stand nella mappa
        playerArmorStands.put(uuid, armorStand);
    }

    private void restorePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Rimuovi l'armor stand
        ArmorStand armorStand = playerArmorStands.get(uuid);
        if (armorStand != null && !armorStand.isDead()) {
            armorStand.remove();
        }

        // Rimuovi dal team per ripristinare la nametag originale
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

        // Rimuovi dalle mappe
        playerArmorStands.remove(uuid);
        lastArmorStandName.remove(uuid);
        lastTabName.remove(uuid);
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
        getLogger().info("Animazione arcobaleno avviata con velocità: " + animationSpeed + " ticks");
    }

    private void startPositionUpdater() {
        if (positionTask != null && !positionTask.isCancelled()) {
            positionTask.cancel();
        }

        positionTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, ArmorStand> entry : playerArmorStands.entrySet()) {
                    UUID uuid = entry.getKey();
                    ArmorStand armorStand = entry.getValue();

                    Player player = Bukkit.getPlayer(uuid);

                    if (player != null && player.isOnline() && armorStand != null && !armorStand.isDead()) {
                        // Aggiorna la posizione dell'armor stand
                        Location playerLocation = player.getLocation();
                        Location armorStandLocation = armorStand.getLocation();

                        // Solo se il giocatore si è mosso significativamente
                        if (playerLocation.distance(armorStandLocation) > 0.1) {
                            Location newLocation = playerLocation.clone();
                            newLocation.setY(newLocation.getY() + nametagHeight);
                            armorStand.teleport(newLocation);
                        }
                    } else if (armorStand != null && !armorStand.isDead()) {
                        // Rimuovi armor stand orfani
                        armorStand.remove();
                        playerArmorStands.remove(uuid);
                    }
                }
            }
        };

        // Aggiorna le posizioni ogni 2 ticks (più frequente per fluidità)
        positionTask.runTaskTimer(this, 0L, 2L);
        getLogger().info("Aggiornamento posizioni armor stand avviato");
    }

    private void startCacheCleanup() {
        if (cacheCleanupTask != null && !cacheCleanupTask.isCancelled()) {
            cacheCleanupTask.cancel();
        }

        cacheCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupCache();
                getLogger().info("Cache pulita. Dimensione attuale: " + nameCache.size() + " nomi");
            }
        };

        // Converte i secondi in ticks (20 ticks = 1 secondo)
        cacheCleanupTask.runTaskTimer(this, cacheCleanupInterval * 20L, cacheCleanupInterval * 20L);
        getLogger().info("Task di pulizia cache avviato (ogni " + cacheCleanupInterval + " secondi)");
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

        ArmorStand armorStand = playerArmorStands.get(uuid);
        if (armorStand == null || armorStand.isDead()) {
            return;
        }

        // Ottieni l'indice corrente del colore
        int colorIndex = playerColorIndex.get(uuid);

        // Usa la cache per ottenere il nome arcobaleno
        String rainbowName = getCachedRainbowName(originalName, colorIndex);

        // Aggiorna solo se il nome è cambiato (ottimizzazione)
        String lastArmorName = lastArmorStandName.get(uuid);
        if (!rainbowName.equals(lastArmorName)) {
            armorStand.setCustomName(rainbowName);
            lastArmorStandName.put(uuid, rainbowName);
        }

        // Mantieni il nome originale per la chat
        player.setDisplayName(originalNames.get(uuid));

        // Usa la cache anche per il nome della TAB
        String lastTab = lastTabName.get(uuid);
        if (!rainbowName.equals(lastTab)) {
            player.setPlayerListName(rainbowName);
            lastTabName.put(uuid, rainbowName);
        }

        // Incrementa l'indice del colore per il prossimo aggiornamento
        playerColorIndex.put(uuid, (colorIndex + 1) % RAINBOW_COLORS.length);
    }

    private void cleanupCache() {
        // Rimuovi i nomi dalla cache che non corrispondono a giocatori online
        nameCache.entrySet().removeIf(entry -> {
            String playerName = entry.getKey();
            return Bukkit.getPlayer(playerName) == null;
        });

        // Se la cache è ancora troppo grande, rimuovi le voci più vecchie
        if (nameCache.size() > cacheSize * 0.8) { // Mantieni all'80% della dimensione massima
            int toRemove = nameCache.size() - (int)(cacheSize * 0.8);
            nameCache.entrySet().stream()
                    .limit(toRemove)
                    .map(Map.Entry::getKey)
                    .forEach(nameCache::remove);
        }
    }

    private void clearCache() {
        nameCache.clear();
        lastArmorStandName.clear();
        lastTabName.clear();
        getLogger().info("Cache completamente pulita");
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

    // Metodi per statistiche della cache (utili per debug)
    public int getCacheSize() {
        return nameCache.size();
    }

    public void printCacheStats() {
        getLogger().info("=== STATISTICHE CACHE ===");
        getLogger().info("Nomi cachati: " + nameCache.size() + "/" + cacheSize);
        getLogger().info("Memoria utilizzata (approssimativa): " + (nameCache.size() * RAINBOW_COLORS.length * 50) + " bytes");
        getLogger().info("========================");
    }
}