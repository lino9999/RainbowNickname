package com.Lino.rainbowNickname.data;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

public class PlayerData {
    public final String originalName;
    public final ArmorStand armorStand;
    public int colorIndex;
    public String lastArmorStandName;
    public String lastTabName;
    public Location lastLocation;
    public final UUID armorStandUUID;
    public String lastPrefix = "";
    public String lastSuffix = "";
    public String originalListName;
    public String cachedPrefix = "";
    public String cachedSuffix = "";
    public Team luckPermsTeam = null;

    public PlayerData(String originalName, ArmorStand armorStand, int colorIndex) {
        this.originalName = originalName;
        this.armorStand = armorStand;
        this.colorIndex = colorIndex;
        this.lastLocation = armorStand.getLocation();
        this.armorStandUUID = armorStand.getUniqueId();
    }
}