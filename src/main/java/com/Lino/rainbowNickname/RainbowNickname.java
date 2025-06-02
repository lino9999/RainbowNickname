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
import org.bukkit.event.server.TabCompleteEvent;
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
        String lastPrefix = "";
        String lastSuffix = "";
        String originalListName;
        String cachedPrefix = "";
        String cachedSuffix = "";
        Team luckPermsTeam = null;

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

    // Mappa per salvare i dati originali di TUTTI i giocatori
    private final Map<UUID, String> originalTabNames = new ConcurrentHashMap<>();
    private final Map<UUID, Team> originalTeams = new ConcurrentHashMap<>();

    // Cache per i tab name dei giocatori senza permesso
    private final Map<UUID, String> nonRainbowTabCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTabCheck = new ConcurrentHashMap<>();

    private BukkitRunnable animationTask;
    private BukkitRunnable positionTask;
    private BukkitRunnable cleanupTask;
    private BukkitRunnable prefixUpdateTask;
    private BukkitRunnable nonRainbowUpdateTask;
    private Scoreboard scoreboard;

    // Configurazioni
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
                        } else if (keepPrefixSuffix) {
                            // Forza l'aggiornamento del tab name per mostrare prefix/suffix anche senza permesso
                            forceUpdateNonRainbowPlayer(player);
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
                    } else if (keepPrefixSuffix) {
                        // Forza l'aggiornamento del tab name per mostrare prefix/suffix anche senza permesso
                        forceUpdateNonRainbowPlayer(player);
                    }
                }
                startTasks();
            }, 20L);
        }

        getLogger().info("RainbowNickname abilitato - Versione con supporto prefix/suffix!");
        getLogger().info("Prefix/Suffix: " + (keepPrefixSuffix ? "ABILITATO per TUTTI i giocatori" : "DISABILITATO"));
        getLogger().info("Lettura da tab: " + (readFromTab ? "ABILITATO" : "DISABILITATO"));
        getLogger().info("Delay LuckPerms: " + luckpermsJoinDelay + " ticks");
    }

    private void loadConfiguration() {
        animationSpeed = Math.max(1, getConfig().getInt("animation-speed", 10));
        useBold = getConfig().getBoolean("use-bold", true);
        nametagHeight = getConfig().getDouble("nametag-height", 2.3);
        positionThreshold = getConfig().getDouble("position-threshold", 0.1);
        useAsyncTasks = getConfig().getBoolean("use-async-tasks", true);
        maxCacheSize = getConfig().getInt("cache.max-size", 1000);
        keepPrefixSuffix = getConfig().getBoolean("keep-prefix-suffix", true);
        animatePrefixSuffix = getConfig().getBoolean("animate-prefix-suffix", false);

        // LuckPerms settings
        luckpermsJoinDelay = getConfig().getInt("luckperms.join-delay", 40);
        readFromTab = getConfig().getBoolean("luckperms.read-from-tab", true);

        // Debug
        debugMode = getConfig().getBoolean("performance.debug", false);

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
        if (prefixUpdateTask != null) prefixUpdateTask.cancel();
        if (nonRainbowUpdateTask != null) nonRainbowUpdateTask.cancel();

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
        originalTabNames.clear();
        originalTeams.clear();
        nonRainbowTabCache.clear();
        lastTabCheck.clear();

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
            } else if (args.length > 0 && args[0].equalsIgnoreCase("debug") && sender instanceof Player) {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    sender.sendMessage(ChatColor.RED + "Non hai il permesso per usare il debug!");
                    return true;
                }

                Player player = (Player) sender;
                sender.sendMessage(ChatColor.GOLD + "=== RainbowNickname Debug ===");
                sender.sendMessage(ChatColor.YELLOW + "PlayerListName: " + ChatColor.WHITE + player.getPlayerListName());

                // Mostra tutti i team
                sender.sendMessage(ChatColor.YELLOW + "Teams:");
                for (Team team : scoreboard.getTeams()) {
                    if (team.hasEntry(player.getName())) {
                        sender.sendMessage(ChatColor.AQUA + "  - " + team.getName() +
                                " Prefix: '" + team.getPrefix() +
                                "' Suffix: '" + team.getSuffix() + "'");
                    }
                }

                // Mostra i dati cached
                PlayerData data = playerData.get(player.getUniqueId());
                if (data != null) {
                    sender.sendMessage(ChatColor.YELLOW + "Cached data:");
                    sender.sendMessage(ChatColor.AQUA + "  - Original listName: " + data.originalListName);
                    sender.sendMessage(ChatColor.AQUA + "  - Cached prefix: '" + data.cachedPrefix + "'");
                    sender.sendMessage(ChatColor.AQUA + "  - Cached suffix: '" + data.cachedSuffix + "'");
                }

                // Test getPrefixSuffix
                String[] prefixSuffix = getPrefixSuffix(player);
                sender.sendMessage(ChatColor.YELLOW + "getPrefixSuffix result:");
                sender.sendMessage(ChatColor.AQUA + "  - Prefix: '" + prefixSuffix[0] + "'");
                sender.sendMessage(ChatColor.AQUA + "  - Suffix: '" + prefixSuffix[1] + "'");

                return true;
            }

            // Mostra help
            sender.sendMessage(ChatColor.GOLD + "=== RainbowNickname Help ===");
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(ChatColor.YELLOW + "/rainbownick reload" + ChatColor.WHITE + " - Ricarica il plugin");
                sender.sendMessage(ChatColor.YELLOW + "/rainbownick debug" + ChatColor.WHITE + " - Mostra info debug (solo player)");
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
        if (prefixUpdateTask != null) prefixUpdateTask.cancel();
        if (nonRainbowUpdateTask != null) nonRainbowUpdateTask.cancel();

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
        originalTabNames.clear();
        originalTeams.clear();
        nonRainbowTabCache.clear();
        lastTabCheck.clear();

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Task immediato per catturare il tab name APPENA possibile
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) {
                String immediateTabName = player.getPlayerListName();
                if (immediateTabName != null && !immediateTabName.equals(player.getName())) {
                    originalTabNames.put(uuid, immediateTabName);

                    if (debugMode) {
                        getLogger().info("[DEBUG] Captured immediate tab name for " + player.getName() + ": " + immediateTabName);
                    }
                }
            }
        });

        // Salva SEMPRE il tab name originale per TUTTI i giocatori
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                String currentTabName = player.getPlayerListName();
                // Sovrascrivi solo se è migliore del precedente
                if (currentTabName != null && !currentTabName.equals(player.getName())) {
                    originalTabNames.put(uuid, currentTabName);
                }

                // Salva anche il team originale
                for (Team team : scoreboard.getTeams()) {
                    if (!team.getName().startsWith("rn_") && team.hasEntry(player.getName())) {
                        originalTeams.put(uuid, team);

                        if (debugMode) {
                            getLogger().info("[DEBUG] Saved team for " + player.getName() + ": " + team.getName());
                            getLogger().info("[DEBUG] Team prefix: '" + team.getPrefix() + "' suffix: '" + team.getSuffix() + "'");
                        }
                        break;
                    }
                }

                if (debugMode) {
                    getLogger().info("[DEBUG] Saved original data for " + player.getName());
                    getLogger().info("[DEBUG] Original tab name: " + currentTabName);
                }
            }
        }, luckpermsJoinDelay - 20); // Un po' prima del setup normale

        // Check asincrono del permesso
        if (useAsyncTasks) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                if (player.hasPermission(PERMISSION)) {
                    // Pre-cache il nome
                    precachePlayerNames(player.getName());

                    // Setup sul thread principale con delay configurabile per LuckPerms
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (player.isOnline()) {
                            setupPlayer(player);
                        }
                    }, luckpermsJoinDelay);
                } else if (keepPrefixSuffix) {
                    // Forza l'update per i giocatori senza permesso
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (player.isOnline()) {
                            forceUpdateNonRainbowPlayer(player);
                        }
                    }, luckpermsJoinDelay + 10); // Poco dopo LuckPerms
                }
            });
        } else {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    if (player.hasPermission(PERMISSION)) {
                        setupPlayer(player);
                    } else if (keepPrefixSuffix) {
                        forceUpdateNonRainbowPlayer(player);
                    }
                }
            }, luckpermsJoinDelay);
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
        originalTabNames.remove(uuid);
        originalTeams.remove(uuid);
        nonRainbowTabCache.remove(uuid);
        lastTabCheck.remove(uuid);
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
        setupPlayer(player, null);
    }

    private void setupPlayer(Player player, String savedOriginalListName) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        if (debugMode) {
            getLogger().info("[DEBUG] Setting up player: " + name);
            getLogger().info("[DEBUG] Current playerListName: " + player.getPlayerListName());
        }

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

        // Prima di nascondere il nametag, salva il team di LuckPerms
        Team luckPermsTeam = null;
        String savedPrefix = "";
        String savedSuffix = "";

        for (Team team : scoreboard.getTeams()) {
            if (!team.getName().startsWith("rn_") && team.hasEntry(player.getName())) {
                luckPermsTeam = team;
                savedPrefix = team.getPrefix();
                savedSuffix = team.getSuffix();

                if (debugMode) {
                    getLogger().info("[DEBUG] Found LuckPerms team before hiding: " + team.getName());
                    getLogger().info("[DEBUG] Prefix: '" + savedPrefix + "' Suffix: '" + savedSuffix + "'");
                }
                break;
            }
        }

        // Nascondi nametag originale
        hideOriginalNametag(player);

        // Crea armor stand
        ArmorStand armorStand = createArmorStand(player);

        // Crea e salva PlayerData
        int startIndex = (int) (Math.random() * RAINBOW_COLORS.length);
        PlayerData data = new PlayerData(name, armorStand, startIndex);

        // Usa il tab name salvato se disponibile
        String tabNameToUse = originalTabNames.get(uuid);
        if (tabNameToUse == null) {
            tabNameToUse = player.getPlayerListName();
        }
        data.originalListName = tabNameToUse;

        // Salva prefix e suffix trovati
        data.cachedPrefix = savedPrefix;
        data.cachedSuffix = savedSuffix;
        data.luckPermsTeam = luckPermsTeam;

        if (debugMode && keepPrefixSuffix) {
            getLogger().info("[DEBUG] Cached prefix: '" + data.cachedPrefix + "'");
            getLogger().info("[DEBUG] Cached suffix: '" + data.cachedSuffix + "'");
        }

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
            // Ripristina il playerListName originale se salvato
            if (data.originalListName != null) {
                player.setPlayerListName(data.originalListName);
            } else {
                player.setPlayerListName(data.originalName);
            }
        }
    }

    private void startTasks() {
        startAnimationTask();
        startPositionTask();
        startCleanupTask();
        if (keepPrefixSuffix) {
            startPrefixUpdateTask();
            startNonRainbowUpdateTask();
        }
    }

    private void startAnimationTask() {
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Aggiorna SOLO i giocatori con permesso rainbow
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

        // Usa sempre animationSpeed per l'animazione
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

    private void startNonRainbowUpdateTask() {
        nonRainbowUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                // Controlla solo i giocatori senza permesso
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.hasPermission(PERMISSION)) {
                        UUID uuid = player.getUniqueId();

                        // Controlla solo se non l'abbiamo controllato di recente
                        Long lastCheck = lastTabCheck.get(uuid);
                        if (lastCheck == null || currentTime - lastCheck > 2000) { // Ogni 2 secondi massimo

                            // Trova il team del giocatore
                            String expectedPrefix = "";
                            String expectedSuffix = "";

                            for (Team team : scoreboard.getTeams()) {
                                if (!team.getName().startsWith("rn_") && team.hasEntry(player.getName())) {
                                    expectedPrefix = team.getPrefix();
                                    expectedSuffix = team.getSuffix();
                                    break;
                                }
                            }

                            // Se ha un prefix/suffix
                            if (!expectedPrefix.isEmpty() || !expectedSuffix.isEmpty()) {
                                String expectedTabName = expectedPrefix + player.getName() + expectedSuffix;
                                String cachedTabName = nonRainbowTabCache.get(uuid);

                                // Controlla solo se è cambiato rispetto alla cache
                                if (!expectedTabName.equals(cachedTabName)) {
                                    String currentTabName = player.getPlayerListName();

                                    // Se non corrisponde, aggiornalo
                                    if (!expectedTabName.equals(currentTabName)) {
                                        player.setPlayerListName(expectedTabName);

                                        if (debugMode) {
                                            getLogger().info("[DEBUG] Updated tab name for " + player.getName());
                                        }
                                    }

                                    // Aggiorna la cache
                                    nonRainbowTabCache.put(uuid, expectedTabName);
                                }
                            }

                            // Aggiorna il timestamp dell'ultimo controllo
                            lastTabCheck.put(uuid, currentTime);
                        }
                    }
                }
            }
        };

        // Esegui ogni 10 tick (0.5 secondi) invece che ogni tick
        nonRainbowUpdateTask.runTaskTimer(this, 20L, 10L);
    }

    private void startPrefixUpdateTask() {
        prefixUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Controlla e aggiorna i prefix/suffix per TUTTI i giocatori online
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    // Se il giocatore ha il permesso rainbow, gestiscilo normalmente
                    if (permittedPlayers.contains(uuid)) {
                        PlayerData data = playerData.get(uuid);
                        if (data != null) {
                            // Controlla se il team di LuckPerms è cambiato
                            boolean foundLuckPermsTeam = false;

                            for (Team team : scoreboard.getTeams()) {
                                if (!team.getName().startsWith("rn_") && team.hasEntry(player.getName())) {
                                    String currentPrefix = team.getPrefix();
                                    String currentSuffix = team.getSuffix();

                                    // Se sono cambiati, aggiorna la cache
                                    if (!currentPrefix.equals(data.cachedPrefix) || !currentSuffix.equals(data.cachedSuffix)) {
                                        data.cachedPrefix = currentPrefix;
                                        data.cachedSuffix = currentSuffix;
                                        data.lastTabName = ""; // Forza aggiornamento

                                        if (debugMode) {
                                            getLogger().info("[DEBUG] Prefix/suffix changed for " + player.getName());
                                            getLogger().info("[DEBUG] New prefix: '" + currentPrefix + "' suffix: '" + currentSuffix + "'");
                                        }
                                    }

                                    foundLuckPermsTeam = true;
                                    break;
                                }
                            }

                            // Se non trovato nessun team LuckPerms ma aveva prefix cached, resetta
                            if (!foundLuckPermsTeam && (!data.cachedPrefix.isEmpty() || !data.cachedSuffix.isEmpty())) {
                                if (debugMode) {
                                    getLogger().info("[DEBUG] Lost LuckPerms team for " + player.getName() + ", checking displayName");
                                }

                                // Prova a recuperare dal display name
                                String displayName = player.getDisplayName();
                                if (!displayName.equals(player.getName())) {
                                    String playerName = player.getName();
                                    int nameIndex = displayName.indexOf(playerName);

                                    if (nameIndex > 0) {
                                        data.cachedPrefix = displayName.substring(0, nameIndex);
                                    } else {
                                        data.cachedPrefix = "";
                                    }

                                    if (nameIndex >= 0 && nameIndex + playerName.length() < displayName.length()) {
                                        data.cachedSuffix = displayName.substring(nameIndex + playerName.length());
                                    } else {
                                        data.cachedSuffix = "";
                                    }

                                    data.lastTabName = ""; // Forza aggiornamento
                                }
                            }
                        }
                    } else if (keepPrefixSuffix) {
                        // Per i giocatori senza permesso rainbow, aggiorna comunque il loro tab name
                        forceUpdateNonRainbowPlayer(player);
                    }
                }
            }
        };

        // Esegui ogni 2 secondi per catturare i cambiamenti di LuckPerms
        prefixUpdateTask.runTaskTimer(this, 60L, 40L);
    }

    private boolean shouldUpdatePosition(Location current, Location last) {
        return last == null ||
                current.getWorld() != last.getWorld() ||
                current.distanceSquared(last) > positionThreshold * positionThreshold;
    }

    // Metodo per ottenere prefix e suffix dal team del giocatore
    private String[] getPrefixSuffix(Player player) {
        String prefix = "";
        String suffix = "";

        // Cerca nel team del giocatore (escludendo i nostri team rn_)
        for (Team team : scoreboard.getTeams()) {
            // Salta i team creati da questo plugin
            if (team.getName().startsWith("rn_")) {
                continue;
            }

            if (team.hasEntry(player.getName())) {
                prefix = team.getPrefix();
                suffix = team.getSuffix();

                if (debugMode) {
                    getLogger().info("[DEBUG] Found team: " + team.getName() + " with prefix: '" + prefix + "' suffix: '" + suffix + "'");
                }
                break;
            }
        }

        // Se non trovato, prova con il display name
        if (prefix.isEmpty() && suffix.isEmpty() && player.getDisplayName() != null) {
            String displayName = player.getDisplayName();
            String playerName = player.getName();

            if (!displayName.equals(playerName)) {
                // Trova il nome del giocatore nel display name
                int nameIndex = displayName.indexOf(playerName);
                if (nameIndex > 0) {
                    prefix = displayName.substring(0, nameIndex);
                }
                if (nameIndex >= 0 && nameIndex + playerName.length() < displayName.length()) {
                    suffix = displayName.substring(nameIndex + playerName.length());
                }

                if (debugMode && !prefix.isEmpty()) {
                    getLogger().info("[DEBUG] Found prefix from display name: '" + prefix + "'");
                }
            }
        }

        // Log debug se abilitato
        if (debugMode) {
            getLogger().info("[DEBUG] Display name: " + player.getDisplayName());
            getLogger().info("[DEBUG] List name: " + player.getPlayerListName());

            // Mostra tutti i team (per debug)
            for (Team team : scoreboard.getTeams()) {
                if (team.hasEntry(player.getName())) {
                    getLogger().info("[DEBUG] Team: " + team.getName());
                }
            }
        }

        return new String[]{prefix, suffix};
    }

    private void updatePlayerColor(UUID uuid, PlayerData data) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        String[] cachedNames = nameCache.get(data.originalName);
        if (cachedNames == null) return;

        // Usa sempre l'indice normale per l'animazione
        String rainbowName = cachedNames[data.colorIndex];

        // Aggiorna solo se cambiato
        if (!rainbowName.equals(data.lastArmorStandName)) {
            data.armorStand.setCustomName(rainbowName);
            data.lastArmorStandName = rainbowName;
        }

        // Gestione tab list
        String prefix = "";
        String suffix = "";

        // Se keepPrefixSuffix è abilitato, ottieni prefix e suffix
        if (keepPrefixSuffix) {
            // Usa prima i dati cached
            if (!data.cachedPrefix.isEmpty() || !data.cachedSuffix.isEmpty()) {
                prefix = data.cachedPrefix;
                suffix = data.cachedSuffix;
            } else {
                // Altrimenti prova a recuperarli
                String[] prefixSuffix = getPrefixSuffix(player);
                prefix = prefixSuffix[0];
                suffix = prefixSuffix[1];

                // Se trovati, salvali nella cache
                if (!prefix.isEmpty() || !suffix.isEmpty()) {
                    data.cachedPrefix = prefix;
                    data.cachedSuffix = suffix;
                }
            }

            // Se ancora vuoti, prova dal listName originale salvato
            if (prefix.isEmpty() && data.originalListName != null && !data.originalListName.equals(player.getName())) {
                String originalList = data.originalListName;
                String playerName = player.getName();

                // Cerca il nome senza colori
                String cleanList = ChatColor.stripColor(originalList);
                int namePos = cleanList.indexOf(playerName);

                if (namePos > 0) {
                    // Conta i caratteri fino alla posizione per trovare il prefix con i colori
                    int coloredPos = 0;
                    int cleanPos = 0;

                    while (cleanPos < namePos && coloredPos < originalList.length()) {
                        if (originalList.charAt(coloredPos) == '§' && coloredPos + 1 < originalList.length()) {
                            coloredPos += 2;
                        } else {
                            cleanPos++;
                            coloredPos++;
                        }
                    }

                    prefix = originalList.substring(0, coloredPos);
                    data.cachedPrefix = prefix;
                }

                if (debugMode && !prefix.isEmpty()) {
                    getLogger().info("[DEBUG] Extracted prefix from original listName: '" + prefix + "'");
                }
            }
        }

        // Costruisci il nome completo per la tab
        String fullTabName;

        if (animatePrefixSuffix && keepPrefixSuffix) {
            // Anima anche prefix e suffix
            String animatedPrefix = applyRainbowEffect(ChatColor.stripColor(prefix), data.colorIndex);
            String animatedSuffix = applyRainbowEffect(ChatColor.stripColor(suffix), data.colorIndex);
            fullTabName = animatedPrefix + rainbowName + animatedSuffix;
        } else if (keepPrefixSuffix) {
            // Mantieni prefix e suffix originali
            fullTabName = prefix + rainbowName + suffix;
        } else {
            // Solo nome arcobaleno senza prefix/suffix
            fullTabName = rainbowName;
        }

        // Aggiorna sempre il playerListName
        if (!fullTabName.equals(data.lastTabName)) {
            player.setPlayerListName(fullTabName);
            data.lastTabName = fullTabName;

            // Debug solo quando cambia
            if (debugMode && keepPrefixSuffix) {
                getLogger().info("[DEBUG] Updated tab name for " + player.getName() + " to: " + fullTabName);
            }
        }

        // Mantieni nome originale per chat
        player.setDisplayName(data.originalName);

        // Incrementa indice per la prossima animazione
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

    // Metodo più aggressivo per i giocatori senza permesso
    private void forceUpdateNonRainbowPlayer(Player player) {
        if (!keepPrefixSuffix || player.hasPermission(PERMISSION)) return;

        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        // Log per debug
        if (debugMode) {
            getLogger().info("[DEBUG] Force updating non-rainbow player: " + playerName);
            getLogger().info("[DEBUG] Current tab name: " + player.getPlayerListName());
        }

        // Prova prima dal team salvato
        Team savedTeam = originalTeams.get(uuid);
        String prefix = "";
        String suffix = "";

        if (savedTeam != null) {
            prefix = savedTeam.getPrefix();
            suffix = savedTeam.getSuffix();

            if (debugMode) {
                getLogger().info("[DEBUG] Using saved team for " + playerName + ": " + savedTeam.getName());
                getLogger().info("[DEBUG] Saved prefix: '" + prefix + "' suffix: '" + suffix + "'");
            }
        }

        // Se non c'è team salvato o è vuoto, cerca quello attuale
        if (prefix.isEmpty() && suffix.isEmpty()) {
            // Prima prova a cercare un team che non sia nostro
            Team currentTeam = null;
            for (Team team : scoreboard.getTeams()) {
                if (!team.getName().startsWith("rn_") && team.hasEntry(playerName)) {
                    currentTeam = team;
                    prefix = team.getPrefix();
                    suffix = team.getSuffix();
                    originalTeams.put(uuid, team); // Salva per dopo

                    if (debugMode) {
                        getLogger().info("[DEBUG] Found current team for " + playerName + ": " + team.getName());
                        getLogger().info("[DEBUG] Current prefix: '" + prefix + "' suffix: '" + suffix + "'");
                    }
                    break;
                }
            }

            // Se ancora niente, cerca QUALSIASI team (anche quelli strani di LuckPerms)
            if (currentTeam == null) {
                for (Team team : scoreboard.getTeams()) {
                    if (team.hasEntry(playerName) && !team.getName().startsWith("rn_")) {
                        prefix = team.getPrefix();
                        suffix = team.getSuffix();

                        if (debugMode) {
                            getLogger().info("[DEBUG] Found ANY team for " + playerName + ": " + team.getName());
                            getLogger().info("[DEBUG] Any prefix: '" + prefix + "' suffix: '" + suffix + "'");
                        }

                        if (!prefix.isEmpty() || !suffix.isEmpty()) {
                            originalTeams.put(uuid, team);
                            break;
                        }
                    }
                }
            }
        }

        // Se ancora niente, prova dal tab name salvato
        if (prefix.isEmpty() && suffix.isEmpty()) {
            String savedTabName = originalTabNames.get(uuid);
            if (savedTabName != null && !savedTabName.equals(playerName)) {
                if (debugMode) {
                    getLogger().info("[DEBUG] Trying to extract from saved tab name: " + savedTabName);
                }

                // Estrai prefix e suffix dal tab name salvato
                String cleanTabName = ChatColor.stripColor(savedTabName);
                int nameIndex = cleanTabName.indexOf(playerName);

                if (nameIndex > 0) {
                    // Estrai il prefix con i colori
                    prefix = extractColoredPrefix(savedTabName, playerName);

                    if (debugMode) {
                        getLogger().info("[DEBUG] Extracted prefix: '" + prefix + "'");
                    }
                }

                if (nameIndex >= 0 && nameIndex + playerName.length() < cleanTabName.length()) {
                    // Estrai il suffix con i colori
                    suffix = extractColoredSuffix(savedTabName, playerName);

                    if (debugMode) {
                        getLogger().info("[DEBUG] Extracted suffix: '" + suffix + "'");
                    }
                }
            }
        }

        // Se ANCORA non abbiamo trovato niente, proviamo con il display name
        if (prefix.isEmpty() && suffix.isEmpty()) {
            String displayName = player.getDisplayName();
            if (!displayName.equals(playerName)) {
                int nameIndex = displayName.indexOf(playerName);

                if (nameIndex > 0) {
                    prefix = displayName.substring(0, nameIndex);
                }

                if (nameIndex >= 0 && nameIndex + playerName.length() < displayName.length()) {
                    suffix = displayName.substring(nameIndex + playerName.length());
                }

                if (debugMode && (!prefix.isEmpty() || !suffix.isEmpty())) {
                    getLogger().info("[DEBUG] Extracted from display name - Prefix: '" + prefix + "' Suffix: '" + suffix + "'");
                }
            }
        }

        // Costruisci e imposta il nuovo tab name
        String newTabName = prefix + playerName + suffix;

        // FORZA l'aggiornamento usando reflection se necessario
        try {
            player.setPlayerListName(newTabName);

            // Doppio controllo dopo un tick
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.getPlayerListName().equals(newTabName)) {
                    player.setPlayerListName(newTabName);

                    if (debugMode) {
                        getLogger().info("[DEBUG] Had to force update again for " + playerName);
                    }
                }
            }, 1L);

        } catch (Exception e) {
            if (debugMode) {
                getLogger().warning("[DEBUG] Failed to update tab name for " + playerName + ": " + e.getMessage());
            }
        }

        if (debugMode) {
            getLogger().info("[DEBUG] Final tab name for " + playerName + ": " + newTabName);
            getLogger().info("[DEBUG] ========================================");
        }
    }

    // Helper per estrarre prefix con colori
    private String extractColoredPrefix(String fullName, String playerName) {
        int index = fullName.indexOf(playerName);
        if (index > 0) {
            return fullName.substring(0, index);
        }
        return "";
    }

    // Helper per estrarre suffix con colori
    private String extractColoredSuffix(String fullName, String playerName) {
        int index = fullName.indexOf(playerName);
        if (index >= 0) {
            int endIndex = index + playerName.length();
            if (endIndex < fullName.length()) {
                return fullName.substring(endIndex);
            }
        }
        return "";
    }
}