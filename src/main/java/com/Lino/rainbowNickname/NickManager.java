package com.Lino.rainbowNickname;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NickManager {

    private final RainbowNickname plugin;
    private final Map<UUID, TextDisplay> activeNicks = new HashMap<>();

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .character('§')
            .build();

    public NickManager(RainbowNickname plugin) {
        this.plugin = plugin;
    }

    public void enableNick(Player player) {
        if (activeNicks.containsKey(player.getUniqueId())) disableNick(player);

        hideVanillaNametag(player);

        TextDisplay display = (TextDisplay) player.getWorld().spawnEntity(player.getLocation(), EntityType.TEXT_DISPLAY);
        display.setBillboard(Display.Billboard.CENTER);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setShadowed(true);

        Transformation t = display.getTransformation();
        t.getTranslation().set(0.0f, 0.40f, 0.0f);
        display.setTransformation(t);

        player.addPassenger(display);
        activeNicks.put(player.getUniqueId(), display);
    }

    public void disableNick(Player player) {
        TextDisplay display = activeNicks.remove(player.getUniqueId());
        if (display != null && display.isValid()) display.remove();
        player.setPlayerListName(player.getName());
    }

    public void updateNick(Player player, float phase) {
        TextDisplay display = activeNicks.get(player.getUniqueId());
        if (display == null || !display.isValid()) {
            enableNick(player);
            return;
        }

        float safePhase = phase % 1.0f;
        String name = player.getName();
        String gradientTag = "<bold><gradient:red:gold:yellow:green:aqua:blue:light_purple:" + safePhase + ">" + name + "</gradient></bold>";

        try {
            Component component = miniMessage.deserialize(gradientTag);
            String rainbowName = legacySerializer.serialize(component);

            display.setText(rainbowName);

            String prefix = plugin.getHookManager().getPrefix(player);
            String suffix = plugin.getHookManager().getSuffix(player);

            player.setPlayerListName(prefix + rainbowName + suffix);

        } catch (Exception e) {
            display.setText(player.getName());
        }

        if (!player.getPassengers().contains(display)) {
            player.addPassenger(display);
        }
    }

    public void refreshPassenger(Player player) {
        TextDisplay display = activeNicks.get(player.getUniqueId());
        if (display != null && display.isValid()) player.addPassenger(display);
    }

    public boolean hasNick(Player player) { return activeNicks.containsKey(player.getUniqueId()); }
    public void removeAll() { activeNicks.values().forEach(TextDisplay::remove); activeNicks.clear(); }

    private void hideVanillaNametag(Player player) {
        org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team team = sb.getTeam("rn_hide");
        if (team == null) {
            team = sb.registerNewTeam("rn_hide");
            team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        }
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
    }
}