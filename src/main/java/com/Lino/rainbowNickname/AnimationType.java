package com.Lino.rainbowNickname;

import org.bukkit.Material;

public enum AnimationType {
    RAINBOW("<bold><gradient:red:gold:yellow:green:aqua:blue:light_purple:dark_purple:%phase%>", "Rainbow", Material.NAME_TAG),
    ICE("<bold><gradient:#a1c4fd:#c2e9fb:#00c6ff:#0072ff:#00c6ff:#c2e9fb:%phase%>", "Ice", Material.PACKED_ICE),
    FIRE("<bold><gradient:#8E0E00:#1F1C18:#f12711:#f5af19:#f12711:#8E0E00:%phase%>", "Fire", Material.FLINT_AND_STEEL),
    SUNSET("<bold><gradient:#355C7D:#6C5B7B:#C06C84:#F67280:#F8B195:%phase%>", "Sunset", Material.ORANGE_GLAZED_TERRACOTTA),
    PASTEL("<bold><gradient:#ff9a9e:#fad0c4:#fad0c4:#a18cd1:#fbc2eb:#8fd3f4:%phase%>", "Pastel", Material.PINK_DYE),
    ALIEN("<bold><gradient:#000000:#0f9b0f:#52c234:#0f9b0f:#000000:%phase%>", "Alien", Material.SLIME_BALL),
    DARK("<bold><gradient:#000000:#434343:#000000:#232526:%phase%>", "Dark", Material.COAL_BLOCK),
    ELECTRIC("<bold><gradient:#4776E6:#8E54E9:#4776E6:%phase%>", "Electric", Material.LIGHTNING_ROD);

    private final String pattern;
    private final String displayName;
    private final Material icon;

    AnimationType(String pattern, String displayName, Material icon) {
        this.pattern = pattern;
        this.displayName = displayName;
        this.icon = icon;
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
}