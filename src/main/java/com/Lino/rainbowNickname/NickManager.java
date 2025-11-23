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
    private final Map<UUID, AnimationType> playerAnimations = new HashMap<>();

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .character('§')
            .build();

    public NickManager(RainbowNickname plugin) {
        this.plugin = plugin;
    }

    public void setAnimation(Player player, AnimationType type) {
        playerAnimations.put(player.getUniqueId(), type);
        enableNick(player);
    }

    public AnimationType getAnimation(Player player) {
        return playerAnimations.get(player.getUniqueId());
    }

    public void enableNick(Player player) {
        if (!playerAnimations.containsKey(player.getUniqueId())) return;

        if (activeNicks.containsKey(player.getUniqueId())) disableNickVisuals(player);

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
        disableNickVisuals(player);
        playerAnimations.remove(player.getUniqueId());
    }

    private void disableNickVisuals(Player player) {
        TextDisplay display = activeNicks.remove(player.getUniqueId());
        if (display != null && display.isValid()) display.remove();
        player.setPlayerListName(player.getName());
    }

    public void updateNick(Player player, float phase) {
        if (!playerAnimations.containsKey(player.getUniqueId())) return;

        TextDisplay display = activeNicks.get(player.getUniqueId());
        if (display == null || !display.isValid()) {
            enableNick(player);
            return;
        }

        AnimationType type = playerAnimations.get(player.getUniqueId());
        float safePhase = phase % 1.0f;
        String pattern = type.getPattern().replace("%phase%", String.valueOf(safePhase));

        try {
            String fullTag = pattern + player.getName() + "</gradient></bold>";
            Component component = miniMessage.deserialize(fullTag);
            String legacyName = legacySerializer.serialize(component);

            display.setText(legacyName);

            String prefix = plugin.getHookManager().getPrefix(player);
            String suffix = plugin.getHookManager().getSuffix(player);

            player.setPlayerListName(prefix + legacyName + suffix);

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