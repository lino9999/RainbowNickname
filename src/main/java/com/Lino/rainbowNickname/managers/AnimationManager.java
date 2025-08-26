package com.Lino.rainbowNickname.managers;

import org.bukkit.ChatColor;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnimationManager {
    public static final ChatColor[] RAINBOW_COLORS = {
            ChatColor.RED,
            ChatColor.GOLD,
            ChatColor.YELLOW,
            ChatColor.GREEN,
            ChatColor.AQUA,
            ChatColor.BLUE,
            ChatColor.LIGHT_PURPLE
    };

    private final Map<String, String[]> nameCache = new ConcurrentHashMap<>();
    private final ConfigManager config;

    private int headerColorIndex = 0;
    private int footerColorIndex = 0;

    public AnimationManager(ConfigManager config) {
        this.config = config;
    }

    public void precachePlayerNames(String playerName) {
        if (nameCache.size() >= config.getMaxCacheSize()) {
            Iterator<String> iterator = nameCache.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        String[] cachedNames = new String[RAINBOW_COLORS.length];

        for (int offset = 0; offset < RAINBOW_COLORS.length; offset++) {
            StringBuilder sb = new StringBuilder(playerName.length() * 4);

            for (int i = 0; i < playerName.length(); i++) {
                ChatColor color = RAINBOW_COLORS[(offset + i) % RAINBOW_COLORS.length];
                sb.append(color);
                if (config.isUseBold()) {
                    sb.append(ChatColor.BOLD);
                }
                sb.append(playerName.charAt(i));
            }

            cachedNames[offset] = sb.toString();
        }

        nameCache.put(playerName, cachedNames);
    }

    public String[] getCachedNames(String playerName) {
        return nameCache.get(playerName);
    }

    public boolean hasCachedNames(String playerName) {
        return nameCache.containsKey(playerName);
    }

    public String applyRainbowEffect(String text, int startIndex) {
        StringBuilder result = new StringBuilder();
        String cleanText = ChatColor.stripColor(text);

        for (int i = 0; i < cleanText.length(); i++) {
            ChatColor color = RAINBOW_COLORS[(startIndex + i) % RAINBOW_COLORS.length];
            result.append(color);
            if (config.isUseBold() && cleanText.charAt(i) != ' ') {
                result.append(ChatColor.BOLD);
            }
            result.append(cleanText.charAt(i));
        }

        return result.toString();
    }

    public boolean containsRainbowColors(String text) {
        for (ChatColor color : RAINBOW_COLORS) {
            if (text.contains(color.toString())) {
                return true;
            }
        }
        return false;
    }

    public void incrementHeaderColorIndex() {
        headerColorIndex = (headerColorIndex + 1) % RAINBOW_COLORS.length;
    }

    public void incrementFooterColorIndex() {
        footerColorIndex = (footerColorIndex + 1) % RAINBOW_COLORS.length;
    }

    public int getHeaderColorIndex() { return headerColorIndex; }
    public int getFooterColorIndex() { return footerColorIndex; }

    public void clearCache() {
        nameCache.clear();
    }
}