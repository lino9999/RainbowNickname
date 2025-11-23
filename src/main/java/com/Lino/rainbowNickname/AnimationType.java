package com.Lino.rainbowNickname;

public enum AnimationType {
    // Arcobaleno classico: aggiunto viola scuro per chiudere il loop
    RAINBOW("<bold><gradient:red:gold:yellow:green:aqua:blue:light_purple:dark_purple:%phase%>", "Rainbow"),

    // Ghiaccio: Aggiunti bianchi e ciano per profondità
    ICE("<bold><gradient:#a1c4fd:#c2e9fb:#00c6ff:#0072ff:#00c6ff:#c2e9fb:%phase%>", "Ice"),

    // Fuoco: Aggiunti rosso scuro e giallo intenso
    FIRE("<bold><gradient:#8E0E00:#1F1C18:#f12711:#f5af19:#f12711:#8E0E00:%phase%>", "Fire"),

    // Tramonto: Viola -> Rosa -> Arancio -> Giallo
    SUNSET("<bold><gradient:#355C7D:#6C5B7B:#C06C84:#F67280:#F8B195:%phase%>", "Sunset"),

    // Pastello: Più sfumature morbide
    PASTEL("<bold><gradient:#ff9a9e:#fad0c4:#fad0c4:#a18cd1:#fbc2eb:#8fd3f4:%phase%>", "Pastel"),

    // Alieno: Verde neon, nero e lime
    ALIEN("<bold><gradient:#000000:#0f9b0f:#52c234:#0f9b0f:#000000:%phase%>", "Alien"),

    // Dark: Grigio scuro, nero e argento
    DARK("<bold><gradient:#000000:#434343:#000000:#232526:%phase%>", "Dark"),

    // Nuova animazione: Elettrico (Viola/Blu neon)
    ELECTRIC("<bold><gradient:#4776E6:#8E54E9:#4776E6:%phase%>", "Electric");

    private final String pattern;
    private final String displayName;

    AnimationType(String pattern, String displayName) {
        this.pattern = pattern;
        this.displayName = displayName;
    }

    public String getPattern() {
        return pattern;
    }

    public String getDisplayName() {
        return displayName;
    }
}