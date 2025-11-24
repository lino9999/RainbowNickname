package com.Lino.rainbowNickname;

import org.bukkit.Material;

public enum AnimationType {
    RAINBOW(
            "<bold><gradient:red:gold:yellow:green:aqua:blue:light_purple:dark_purple:%phase%>",
            "<gradient:red:gold:green:aqua:light_purple>Rainbow</gradient>",
            Material.NAME_TAG,
            "<gray>The classic </gray><bold><gradient:red:gold:green:aqua:light_purple>multicolor loop</gradient></bold><gray>!</gray>"
    ),
    ICE(
            "<bold><gradient:#a1c4fd:#c2e9fb:#00c6ff:#0072ff:#00c6ff:#c2e9fb:%phase%>",
            "<gradient:#a1c4fd:#0072ff>Ice</gradient>",
            Material.PACKED_ICE,
            "<gray>Stay frosty with </gray><bold><gradient:#a1c4fd:#0072ff>cold colors</gradient></bold><gray>.</gray>"
    ),
    FIRE(
            "<bold><gradient:#8E0E00:#1F1C18:#f12711:#f5af19:#f12711:#8E0E00:%phase%>",
            "<gradient:#f12711:#f5af19>Fire</gradient>",
            Material.FLINT_AND_STEEL,
            "<gray>Burn bright with </gray><bold><gradient:#f12711:#f5af19>fiery shades</gradient></bold><gray>.</gray>"
    ),
    SUNSET(
            "<bold><gradient:#355C7D:#6C5B7B:#C06C84:#F67280:#F8B195:%phase%>",
            "<gradient:#355C7D:#F8B195>Sunset</gradient>",
            Material.ORANGE_GLAZED_TERRACOTTA,
            "<gray>Relaxing </gray><bold><gradient:#355C7D:#F8B195>evening vibes</gradient></bold><gray>.</gray>"
    ),
    PASTEL(
            "<bold><gradient:#ff9a9e:#fad0c4:#fad0c4:#a18cd1:#fbc2eb:#8fd3f4:%phase%>",
            "<gradient:#ff9a9e:#a18cd1>Pastel</gradient>",
            Material.PINK_DYE,
            "<gray>Soft, sweet and </gray><bold><gradient:#ff9a9e:#a18cd1>cute colors</gradient></bold><gray>.</gray>"
    ),
    ALIEN(
            "<bold><gradient:#000000:#0f9b0f:#52c234:#0f9b0f:#000000:%phase%>",
            "<gradient:#0f9b0f:#52c234>Alien</gradient>",
            Material.SLIME_BALL,
            "<gray>Straight from </gray><bold><gradient:#000000:#52c234>another galaxy</gradient></bold><gray>.</gray>"
    ),
    DARK(
            "<bold><gradient:#000000:#434343:#000000:#232526:%phase%>",
            "<gradient:#000000:#434343>Dark</gradient>",
            Material.COAL_BLOCK,
            "<gray>Mysterious and </gray><bold><gradient:#000000:#434343>elegant style</gradient></bold><gray>.</gray>"
    ),
    ELECTRIC(
            "<bold><gradient:#4776E6:#8E54E9:#4776E6:%phase%>",
            "<gradient:#4776E6:#8E54E9>Electric</gradient>",
            Material.LIGHTNING_ROD,
            "<gray>High voltage </gray><bold><gradient:#4776E6:#8E54E9>energy flow</gradient></bold><gray>.</gray>"
    );

    private final String pattern;
    private final String displayName;
    private final Material icon;
    private final String description;

    AnimationType(String pattern, String displayName, Material icon, String description) {
        this.pattern = pattern;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getPattern() {
        return pattern;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }
}