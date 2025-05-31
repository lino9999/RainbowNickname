package com.Lino.rainbowNickname;

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

public class RainbowNicknameAdvanced extends JavaPlugin implements Listener {

    private static final String PERMISSION_USE = "rainbownick.use";
    private static final String PERMISSION_ADMIN = "rainbownick.admin";
    private static final String PERMISSION_TOGGLE = "rainbownick.toggle";

    private static final ChatColor[] RAINBOW_COLORS = {
            ChatColor.DARK_RED,
            ChatColor.RED,
            ChatColor.GOLD,
            ChatColor.YELLOW,
            ChatColor.GREEN,
            ChatColor.DARK_GREEN,
            ChatColor.AQUA,
            ChatColor.DARK_AQUA,
            ChatColor.BLUE,
            ChatColor.DARK_BLUE,
            ChatColor.LIGHT_PURPLE,
            ChatColor.DARK_PURPLE
    };

    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    private BukkitRunnable animationTask;
    private Scoreboard scoreboard;
    private int animationSpeed = 4;
    private boolean useBold = true;
    private boolean smoothTransition = true;

    private static class PlayerData {
        final String originalName;
        final String fakeName;
        int colorIndex;
        Team realTeam;
        Team fakeTeam;

        PlayerData(String originalName, String fakeName, Team realTeam, Team fakeTeam) {
            this.originalName = originalName;
            this.fakeName = fakeName;
            this.colorIndex = 0;
            this.realTeam = realTeam;
            this.fakeTeam = fakeTeam;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Carica configurazione
        animationSpeed = getConfig().getInt("animation-speed", 4);
        useBold = getConfig().getBoolean("use-bold", true);
        smoothTransition = getConfig().getBoolean("smooth-transition", true);

        getServer().getPluginManager().registerEvents(this, this);

        // Crea uno scoreboard personalizzato per gestire meglio i team
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        cleanupOldTeams();
        startAnimation();

        // Applica lo scoreboard e setup a tutti i giocatori online
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(scoreboard);
            if (shouldHaveRainbow(player)) {
                setupPlayer(player);
            }
        }

        getLogger().info("RainbowNickname Advanced abilitato!");
    }

    @Override
    public void onDisable() {
        if (animationTask != null) {
            animationTask.cancel();
        }

        // Ripristina lo scoreboard principale
        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayer(player);
            player.setScoreboard(mainScoreboard);
        }

        cleanupOldTeams();
        playerData.clear();
        disabledPlayers.clear();

        getLogger().info("RainbowNickname Advanced disabilitato!");
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

                case "bold":
                    if (!sender.hasPermission(PERMISSION_ADMIN)) {
                        sender.sendMessage(ChatColor.RED + "Non hai il permesso per usare questo comando!");
                        return true;
                    }

                    useBold = !useBold;
                    getConfig().set("use-bold", useBold);
                    saveConfig();

                    sender.sendMessage(ChatColor.GREEN + "Testo in grassetto " +
                            (useBold ? "abilitato" : "disabilitato") + "!");

                    updateAllPlayers();
                    return true;

                case "smooth":
                    if (!sender.hasPermission(PERMISSION_ADMIN)) {
                        sender.sendMessage(ChatColor.RED + "Non hai il permesso per usare questo comando!");
                        return true;
                    }

                    smoothTransition = !smoothTransition;
                    getConfig().set("smooth-transition", smoothTransition);
                    saveConfig();

                    sender.sendMessage(ChatColor.GREEN + "Transizione fluida " +
                            (smoothTransition ? "abilitata" : "disabilitata") + "!");
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

        // Imposta lo scoreboard personalizzato
        player.setScoreboard(scoreboard);

        disabledPlayers.remove(player.getUniqueId());

        if (shouldHaveRainbow(player)) {
            Bukkit.getScheduler().runTaskLater(this, () -> setupPlayer(player), 1L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        PlayerData data = playerData.remove(uuid);
        if (data != null) {
            if (data.realTeam != null) data.realTeam.unregister();
            if (data.fakeTeam != null) data.fakeTeam.unregister();
        }
    }

    private boolean shouldHaveRainbow(Player player) {
        return player.hasPermission(PERMISSION_USE) && !disabledPlayers.contains(player.getUniqueId());
    }

    private void setupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        String originalName = player.getName();

        // Genera un nome "falso" per superare il limite di 16 caratteri
        String fakeName = generateFakeName(uuid);

        // Crea due team: uno per il nome reale e uno per il nome falso
        String realTeamName = "rb_r_" + uuid.toString().substring(0, 10);
        String fakeTeamName = "rb_f_" + uuid.toString().substring(0, 10);

        Team realTeam = scoreboard.registerNewTeam(realTeamName);
        Team fakeTeam = scoreboard.registerNewTeam(fakeTeamName);

        // Configura i team
        realTeam.addEntry(originalName);
        fakeTeam.addEntry(fakeName);

        realTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        fakeTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        // Salva i dati
        playerData.put(uuid, new PlayerData(originalName, fakeName, realTeam, fakeTeam));

        // Applica il colore iniziale
        updatePlayerColor(player);
    }

    private String generateFakeName(UUID uuid) {
        // Genera un nome falso unico basato sull'UUID
        return "§" + uuid.toString().substring(0, 15);
    }

    private void restorePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        PlayerData data = playerData.remove(uuid);
        if (data != null) {
            if (data.realTeam != null) data.realTeam.unregister();
            if (data.fakeTeam != null) data.fakeTeam.unregister();
        }

        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
    }

    private void startAnimation() {
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                Set<Player> playersToUpdate = new HashSet<>();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (playerData.containsKey(player.getUniqueId())) {
                        playersToUpdate.add(player);
                    }
                }

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

        // Costruisci il nome colorato completo per chat e tab
        StringBuilder coloredName = new StringBuilder();
        StringBuilder nametagBuilder = new StringBuilder();

        for (int i = 0; i < originalName.length(); i++) {
            char c = originalName.charAt(i);
            int colorIndex;

            if (smoothTransition) {
                // Transizione fluida tra i colori
                colorIndex = (baseColorIndex + i * 2) % RAINBOW_COLORS.length;
            } else {
                // Cambio colore standard
                colorIndex = (baseColorIndex + i) % RAINBOW_COLORS.length;
            }

            ChatColor color = RAINBOW_COLORS[colorIndex];

            // Per chat e tab
            coloredName.append(color);
            if (useBold) {
                coloredName.append(ChatColor.BOLD);
            }
            coloredName.append(c);

            // Per nametag
            nametagBuilder.append(color);
            if (useBold) {
                nametagBuilder.append(ChatColor.BOLD);
            }
            nametagBuilder.append(c);
        }

        // Applica al team falso per il nametag sopra la testa
        applyAdvancedTeamFormatting(data.fakeTeam, originalName, baseColorIndex);

        // Aggiorna display name e tab list
        player.setDisplayName(coloredName.toString());
        player.setPlayerListName(coloredName.toString());

        // Incrementa l'indice per il prossimo frame
        if (smoothTransition) {
            data.colorIndex = (data.colorIndex + 1) % (RAINBOW_COLORS.length * 2);
        } else {
            data.colorIndex = (data.colorIndex + 1) % RAINBOW_COLORS.length;
        }
    }

    private void applyAdvancedTeamFormatting(Team team, String name, int baseColorIndex) {
        // Strategia avanzata: usa prefix, display name e suffix per massimizzare i colori

        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();

        int totalLength = name.length();
        int prefixLength = Math.min(totalLength, 8); // Massimo 8 caratteri nel prefix
        int suffixLength = Math.min(totalLength - prefixLength, 8); // Massimo 8 nel suffix

        // Costruisci il prefix
        for (int i = 0; i < prefixLength; i++) {
            char c = name.charAt(i);
            int colorIndex;

            if (smoothTransition) {
                colorIndex = (baseColorIndex + i * 2) % RAINBOW_COLORS.length;
            } else {
                colorIndex = (baseColorIndex + i) % RAINBOW_COLORS.length;
            }

            prefix.append(RAINBOW_COLORS[colorIndex]);
            if (useBold) {
                prefix.append(ChatColor.BOLD);
            }
            prefix.append(c);
        }

        // Costruisci il suffix se necessario
        if (suffixLength > 0) {
            for (int i = 0; i < suffixLength; i++) {
                int charIndex = prefixLength + i;
                char c = name.charAt(charIndex);
                int colorIndex;

                if (smoothTransition) {
                    colorIndex = (baseColorIndex + charIndex * 2) % RAINBOW_COLORS.length;
                } else {
                    colorIndex = (baseColorIndex + charIndex) % RAINBOW_COLORS.length;
                }

                suffix.append(RAINBOW_COLORS[colorIndex]);
                if (useBold) {
                    suffix.append(ChatColor.BOLD);
                }
                suffix.append(c);
            }
        }

        // Applica al team
        String prefixStr = prefix.toString();
        String suffixStr = suffix.toString();

        // Tronca se necessario (max 16 caratteri per prefix/suffix)
        if (prefixStr.length() > 64) { // 64 caratteri = circa 16 caratteri visibili con colori
            prefixStr = truncateColoredString(prefixStr, 16);
        }
        if (suffixStr.length() > 64) {
            suffixStr = truncateColoredString(suffixStr, 16);
        }

        team.setPrefix(prefixStr);
        team.setSuffix(suffixStr);

        // Imposta il colore principale del team
        try {
            team.setColor(RAINBOW_COLORS[baseColorIndex % RAINBOW_COLORS.length]);
        } catch (NoSuchMethodError ignored) {
            // Versione precedente
        }
    }

    private String truncateColoredString(String colored, int maxVisibleChars) {
        StringBuilder result = new StringBuilder();
        int visibleChars = 0;
        boolean inColorCode = false;

        for (int i = 0; i < colored.length(); i++) {
            char c = colored.charAt(i);

            if (c == '§' || c == ChatColor.COLOR_CHAR) {
                inColorCode = true;
                result.append(c);
            } else if (inColorCode) {
                result.append(c);
                inColorCode = false;
            } else {
                if (visibleChars >= maxVisibleChars) {
                    break;
                }
                result.append(c);
                visibleChars++;
            }
        }

        return result.toString();
    }

    private void toggleRainbow(Player player) {
        UUID uuid = player.getUniqueId();

        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            if (player.hasPermission(PERMISSION_USE)) {
                setupPlayer(player);
                player.sendMessage(ChatColor.GREEN + "Nickname arcobaleno abilitato!");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Non hai il permesso per il nickname arcobaleno!");
            }
        } else {
            disabledPlayers.add(uuid);
            restorePlayer(player);
            player.sendMessage(ChatColor.YELLOW + "Nickname arcobaleno disabilitato!");
        }
    }

    private void setAnimationSpeed(int speed) {
        animationSpeed = speed;
        getConfig().set("animation-speed", speed);
        saveConfig();

        if (animationTask != null) {
            animationTask.cancel();
        }
        startAnimation();
    }

    private void reloadPlugin() {
        reloadConfig();
        animationSpeed = getConfig().getInt("animation-speed", 4);
        useBold = getConfig().getBoolean("use-bold", true);
        smoothTransition = getConfig().getBoolean("smooth-transition", true);

        if (animationTask != null) {
            animationTask.cancel();
        }
        startAnimation();

        updateAllPlayers();
    }

    private void updateAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playerData.containsKey(player.getUniqueId())) {
                updatePlayerColor(player);
            }
        }
    }

    private void cleanupOldTeams() {
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith("rb_")) {
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
            sender.sendMessage(ChatColor.YELLOW + "/rainbownick bold" + ChatColor.WHITE + " - Attiva/disattiva testo in grassetto");
            sender.sendMessage(ChatColor.YELLOW + "/rainbownick smooth" + ChatColor.WHITE + " - Attiva/disattiva transizione fluida");
        }
    }
}