package com.Lino.rainbowNickname;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RainbowNickname extends JavaPlugin implements Listener {

    private static final String PERMISSION = "rainbownick.use";
    private static final String ADMIN_PERMISSION = "rainbownick.admin";
    private static final ChatColor[] RAINBOW_COLORS = {
            ChatColor.RED,
            ChatColor.GOLD,
            ChatColor.YELLOW,
            ChatColor.GREEN,
            ChatColor.AQUA,
            ChatColor.BLUE,
            ChatColor.LIGHT_PURPLE
    };

    // Struttura dati ottimizzata per i dati del giocatore
    private static class PlayerData {
        final String originalName;
        final ArmorStand armorStand;
        int colorIndex;
        String lastArmorStandName;
        String lastTabName;
        Location lastLocation;
        final UUID armorStandUUID;

        PlayerData(String originalName, ArmorStand armorStand, int colorIndex) {
            this.originalName = originalName;
            this.armorStand = armorStand;
            this.colorIndex = colorIndex;
            this.lastLocation = armorStand.getLocation();
            this.armorStandUUID = armorStand.getUniqueId();
        }
    }

    // Mappa principale per i dati dei giocatori
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    // Cache ottimizzata con array pre-calcolati
    private final Map<String, String[]> nameCache = new ConcurrentHashMap<>();

    // Set per tracciare i giocatori con permesso (più veloce per i controlli)
    private final Set<UUID> permittedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Set per tracciare tutti gli armor stand creati dal plugin
    private final Set<UUID> pluginArmorStands = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private BukkitRunnable animationTask;
    private BukkitRunnable positionTask;
    private BukkitRunnable cleanupTask;
    private Scoreboard scoreboard;

    // Configurazioni
    private int animationSpeed;
    private boolean useBold;
    private double nametagHeight;
    private double positionThreshold;
    private boolean useAsyncTasks;
    private int maxCacheSize;

    // Tab list header e footer
    private boolean useTabList;
    private String tabHeader;
    private String tabFooter;
    private int headerColorIndex = 0;
    private int footerColorIndex = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();

        getServer().getPluginManager().registerEvents(this, this);
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        cleanupOldTeams();
        cleanupOrphanedArmorStands();

        // Inizializzazione asincrona per non bloccare il thread principale
        if (useAsyncTasks) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                // Pre-cache i nomi di tutti i giocatori online
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission(PERMISSION)) {
                        precachePlayerNames(player.getName());
                    }
                }

                // Torna sul thread principale per setup dei giocatori
                Bukkit.getScheduler().runTask(this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.hasPermission(PERMISSION)) {
                            setupPlayer(player);
                        }
                    }
                    startTasks();
                });
            });
        } else {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission(PERMISSION)) {
                        setupPlayer(player);
                    }
                }
                startTasks();
            }, 20L);
        }

        getLogger().info("RainbowNickname abilitato - Versione ottimizzata con tab list!");
    }

    private void loadConfiguration() {
        animationSpeed = Math.max(1, getConfig().getInt("animation-speed", 10));
        useBold = getConfig().getBoolean("use-bold", true);
        nametagHeight = getConfig().getDouble("nametag-height", 2.3);
        positionThreshold = getConfig().getDouble("position-threshold", 0.1);
        useAsyncTasks = getConfig().getBoolean("use-async-tasks", true);
        maxCacheSize = getConfig().getInt("cache.max-size", 1000);

        // Tab list configurazioni
        useTabList = getConfig().getBoolean("tab-list.enabled", true);
        tabHeader = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("tab-list.header", "&lRainbow Server"));
        tabFooter = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("tab-list.footer", "&7Powered by RainbowNickname"));
    }

    @Override
    public void onDisable() {
        // Cancella i task
        if (animationTask != null) animationTask.cancel();
        if (positionTask != null) positionTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();

        // Cleanup in batch
        playerData.values().parallelStream().forEach(data -> {
            if (data.armorStand != null && !data.armorStand.isDead()) {
                // Usa il thread principale per rimuovere entità
                Bukkit.getScheduler().runTask(this, () -> data.armorStand.remove());
            }
        });

        // Rimuovi tutti gli armor stand orfani
        cleanupOrphanedArmorStands();

        // Ripristina i nomi originali e rimuovi header/footer
        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayer(player);
            if (useTabList) {
                player.setPlayerListHeader("");
                player.setPlayerListFooter("");
            }
        }

        cleanupOldTeams();
        playerData.clear();
        nameCache.clear();
        permittedPlayers.clear();
        pluginArmorStands.clear();

        getLogger().info("RainbowNickname disabilitato!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rainbownick")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    sender.sendMessage(ChatColor.RED + "Non hai il permesso per ricaricare il plugin!");
                    return true;
                }

                reloadPlugin(sender);
                return true;
            }

            // Mostra help
            sender.sendMessage(ChatColor.GOLD + "=== RainbowNickname Help ===");
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(ChatColor.YELLOW + "/rainbownick reload" + ChatColor.WHITE + " - Ricarica il plugin");
            }
            return true;
        }
        return false;
    }

    private void reloadPlugin(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Ricaricamento RainbowNickname...");

        // Cancella tutti i task
        if (animationTask != null) animationTask.cancel();
        if (positionTask != null) positionTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();

        // Rimuovi tutti gli armor stand
        for (PlayerData data : playerData.values()) {
            if (data.armorStand != null && !data.armorStand.isDead()) {
                data.armorStand.remove();
            }
        }

        // Ripristina i giocatori
        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayer(player);
            if (useTabList) {
                player.setPlayerListHeader("");
                player.setPlayerListFooter("");
            }
        }

        // Pulisci le strutture dati
        playerData.clear();
        nameCache.clear();
        permittedPlayers.clear();
        pluginArmorStands.clear();

        // Ricarica configurazione
        reloadConfig();
        loadConfiguration();

        // Pulisci armor stand orfani
        cleanupOrphanedArmorStands();

        // Re-inizializza i giocatori
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERMISSION)) {
                precachePlayerNames(player.getName());
                setupPlayer(player);
            }
        }

        // Riavvia i task
        startTasks();

        sender.sendMessage(ChatColor.GREEN + "RainbowNickname ricaricato con successo!");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check asincrono del permesso
        if (useAsyncTasks) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                if (player.hasPermission(PERMISSION)) {
                    // Pre-cache il nome
                    precachePlayerNames(player.getName());

                    // Setup sul thread principale
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (player.isOnline()) {
                            setupPlayer(player);
                        }
                    }, 10L);
                }
            });
        } else {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline() && player.hasPermission(PERMISSION)) {
                    setupPlayer(player);
                }
            }, 10L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData data = playerData.remove(uuid);

        if (data != null) {
            // Rimuovi armor stand
            if (data.armorStand != null && !data.armorStand.isDead()) {
                data.armorStand.remove();
                pluginArmorStands.remove(data.armorStandUUID);
            }

            // Cleanup team
            String teamName = "rn_" + uuid.toString().substring(0, 8);
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        }

        permittedPlayers.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerData data = playerData.get(uuid);

        if (data != null && data.armorStand != null && !data.armorStand.isDead()) {
            // Teleport immediato dell'armor stand
            Location newLocation = event.getTo().clone();
            newLocation.setY(newLocation.getY() + nametagHeight);
            data.armorStand.teleport(newLocation);
            data.lastLocation = newLocation;
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Rimuovi armor stand orfani quando un chunk viene caricato
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof ArmorStand) {
                ArmorStand armorStand = (ArmorStand) entity;
                if (armorStand.isMarker() && armorStand.isCustomNameVisible() &&
                        !armorStand.isVisible() && !pluginArmorStands.contains(armorStand.getUniqueId())) {

                    // Controlla se è un armor stand del nostro plugin (ha i colori arcobaleno)
                    String customName = armorStand.getCustomName();
                    if (customName != null && containsRainbowColors(customName)) {
                        armorStand.remove();
                    }
                }
            }
        }
    }

    private boolean containsRainbowColors(String text) {
        for (ChatColor color : RAINBOW_COLORS) {
            if (text.contains(color.toString())) {
                return true;
            }
        }
        return false;
    }

    private void cleanupOrphanedArmorStands() {
        int removed = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand) {
                    ArmorStand armorStand = (ArmorStand) entity;
                    if (armorStand.isMarker() && armorStand.isCustomNameVisible() &&
                            !armorStand.isVisible() && !pluginArmorStands.contains(armorStand.getUniqueId())) {

                        String customName = armorStand.getCustomName();
                        if (customName != null && containsRainbowColors(customName)) {
                            armorStand.remove();
                            removed++;
                        }
                    }
                }
            }
        }

        if (removed > 0) {
            getLogger().info("Rimossi " + removed + " armor stand orfani.");
        }
    }

    private void setupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // Rimuovi eventuali dati precedenti
        PlayerData oldData = playerData.get(uuid);
        if (oldData != null && oldData.armorStand != null && !oldData.armorStand.isDead()) {
            oldData.armorStand.remove();
            pluginArmorStands.remove(oldData.armorStandUUID);
        }

        // Pre-cache i nomi se non già fatto
        if (!nameCache.containsKey(name)) {
            precachePlayerNames(name);
        }

        // Nascondi nametag originale
        hideOriginalNametag(player);

        // Crea armor stand
        ArmorStand armorStand = createArmorStand(player);

        // Crea e salva PlayerData
        int startIndex = (int) (Math.random() * RAINBOW_COLORS.length);
        PlayerData data = new PlayerData(name, armorStand, startIndex);
        playerData.put(uuid, data);
        permittedPlayers.add(uuid);
        pluginArmorStands.add(armorStand.getUniqueId());

        // Aggiorna colore iniziale
        updatePlayerColor(uuid, data);

        // Aggiorna tab list se abilitato
        if (useTabList) {
            updateTabList(player);
        }
    }

    private void precachePlayerNames(String playerName) {
        if (nameCache.size() >= maxCacheSize) {
            // Rimuovi le entry più vecchie (FIFO)
            Iterator<String> iterator = nameCache.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        String[] cachedNames = new String[RAINBOW_COLORS.length];

        // Pre-calcola tutti i nomi con StringBuilder ottimizzato
        for (int offset = 0; offset < RAINBOW_COLORS.length; offset++) {
            StringBuilder sb = new StringBuilder(playerName.length() * 4);

            for (int i = 0; i < playerName.length(); i++) {
                ChatColor color = RAINBOW_COLORS[(offset + i) % RAINBOW_COLORS.length];
                sb.append(color);
                if (useBold) {
                    sb.append(ChatColor.BOLD);
                }
                sb.append(playerName.charAt(i));
            }

            cachedNames[offset] = sb.toString();
        }

        nameCache.put(playerName, cachedNames);
    }

    private void hideOriginalNametag(Player player) {
        String teamName = "rn_" + player.getUniqueId().toString().substring(0, 8);

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        team.addEntry(player.getName());
    }

    private ArmorStand createArmorStand(Player player) {
        Location loc = player.getLocation().clone();
        loc.setY(loc.getY() + nametagHeight);

        ArmorStand armorStand = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);

        // Configurazione ottimizzata in un blocco
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setCanPickupItems(false);
        armorStand.setCustomNameVisible(true);
        armorStand.setMarker(true);
        armorStand.setSilent(true);
        armorStand.setInvulnerable(true);
        armorStand.setBasePlate(false);
        armorStand.setArms(false);
        armorStand.setSmall(true);

        return armorStand;
    }

    private void restorePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerData.get(uuid);

        if (data != null) {
            player.setDisplayName(data.originalName);
            player.setPlayerListName(data.originalName);
        }
    }

    private void startTasks() {
        startAnimationTask();
        startPositionTask();
        startCleanupTask();
    }

    private void startAnimationTask() {
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Aggiorna solo i giocatori con permesso
                for (UUID uuid : permittedPlayers) {
                    PlayerData data = playerData.get(uuid);
                    if (data != null) {
                        updatePlayerColor(uuid, data);
                    }
                }

                // Aggiorna tab list header/footer se abilitato
                if (useTabList) {
                    updateAllTabLists();
                }
            }
        };

        animationTask.runTaskTimer(this, 0L, animationSpeed);
    }

    private void startPositionTask() {
        positionTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Batch update delle posizioni
                List<UUID> toRemove = new ArrayList<>();

                for (Map.Entry<UUID, PlayerData> entry : playerData.entrySet()) {
                    UUID uuid = entry.getKey();
                    PlayerData data = entry.getValue();
                    Player player = Bukkit.getPlayer(uuid);

                    if (player == null || !player.isOnline()) {
                        toRemove.add(uuid);
                        continue;
                    }

                    if (data.armorStand == null || data.armorStand.isDead()) {
                        toRemove.add(uuid);
                        continue;
                    }

                    // Aggiorna solo se necessario
                    Location playerLoc = player.getLocation();
                    if (shouldUpdatePosition(playerLoc, data.lastLocation)) {
                        Location newLoc = playerLoc.clone();
                        newLoc.setY(newLoc.getY() + nametagHeight);
                        data.armorStand.teleport(newLoc);
                        data.lastLocation = playerLoc;
                    }
                }

                // Rimuovi giocatori non validi
                toRemove.forEach(uuid -> {
                    PlayerData data = playerData.remove(uuid);
                    if (data != null && data.armorStand != null && !data.armorStand.isDead()) {
                        data.armorStand.remove();
                        pluginArmorStands.remove(data.armorStandUUID);
                    }
                    permittedPlayers.remove(uuid);
                });
            }
        };

        positionTask.runTaskTimer(this, 0L, 2L);
    }

    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Rimuovi armor stand duplicati ogni 5 secondi
                Set<Location> knownLocations = new HashSet<>();
                List<ArmorStand> toRemove = new ArrayList<>();

                for (PlayerData data : playerData.values()) {
                    if (data.armorStand != null && !data.armorStand.isDead()) {
                        knownLocations.add(data.armorStand.getLocation());
                    }
                }

                // Cerca armor stand vicini alle posizioni note
                for (Location loc : knownLocations) {
                    Collection<Entity> nearbyEntities = loc.getWorld().getNearbyEntities(loc, 3, 3, 3);
                    int armorStandCount = 0;

                    for (Entity entity : nearbyEntities) {
                        if (entity instanceof ArmorStand) {
                            ArmorStand as = (ArmorStand) entity;
                            if (as.isMarker() && as.isCustomNameVisible() && !as.isVisible()) {
                                armorStandCount++;
                                if (armorStandCount > 1 && !pluginArmorStands.contains(as.getUniqueId())) {
                                    toRemove.add(as);
                                }
                            }
                        }
                    }
                }

                // Rimuovi duplicati
                for (ArmorStand as : toRemove) {
                    as.remove();
                }
            }
        };

        cleanupTask.runTaskTimer(this, 100L, 100L); // Ogni 5 secondi
    }

    private boolean shouldUpdatePosition(Location current, Location last) {
        return last == null ||
                current.getWorld() != last.getWorld() ||
                current.distanceSquared(last) > positionThreshold * positionThreshold;
    }

    private void updatePlayerColor(UUID uuid, PlayerData data) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        String[] cachedNames = nameCache.get(data.originalName);
        if (cachedNames == null) return;

        String rainbowName = cachedNames[data.colorIndex];

        // Aggiorna solo se cambiato
        if (!rainbowName.equals(data.lastArmorStandName)) {
            data.armorStand.setCustomName(rainbowName);
            data.lastArmorStandName = rainbowName;
        }

        if (!rainbowName.equals(data.lastTabName)) {
            player.setPlayerListName(rainbowName);
            data.lastTabName = rainbowName;
        }

        // Mantieni nome originale per chat
        player.setDisplayName(data.originalName);

        // Incrementa indice
        data.colorIndex = (data.colorIndex + 1) % RAINBOW_COLORS.length;
    }

    private void updateTabList(Player player) {
        if (!useTabList) return;

        String coloredHeader = applyRainbowEffect(tabHeader, headerColorIndex);
        String coloredFooter = applyRainbowEffect(tabFooter, footerColorIndex);

        player.setPlayerListHeader(coloredHeader);
        player.setPlayerListFooter(coloredFooter);
    }

    private void updateAllTabLists() {
        headerColorIndex = (headerColorIndex + 1) % RAINBOW_COLORS.length;
        footerColorIndex = (footerColorIndex + 1) % RAINBOW_COLORS.length;

        for (Player player : Bukkit.getOnlinePlayers()) {
            updateTabList(player);
        }
    }

    private String applyRainbowEffect(String text, int startIndex) {
        StringBuilder result = new StringBuilder();
        String cleanText = ChatColor.stripColor(text);

        for (int i = 0; i < cleanText.length(); i++) {
            ChatColor color = RAINBOW_COLORS[(startIndex + i) % RAINBOW_COLORS.length];
            result.append(color);
            if (useBold && cleanText.charAt(i) != ' ') {
                result.append(ChatColor.BOLD);
            }
            result.append(cleanText.charAt(i));
        }

        return result.toString();
    }

    private void cleanupOldTeams() {
        scoreboard.getTeams().stream()
                .filter(team -> team.getName().startsWith("rn_") || team.getName().startsWith("rainbow_"))
                .forEach(Team::unregister);
    }
}