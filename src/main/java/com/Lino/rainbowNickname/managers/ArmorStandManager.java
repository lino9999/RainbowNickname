package com.Lino.rainbowNickname.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArmorStandManager {
    private final Set<UUID> pluginArmorStands = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConfigManager config;
    private final AnimationManager animationManager;

    public ArmorStandManager(ConfigManager config, AnimationManager animationManager) {
        this.config = config;
        this.animationManager = animationManager;
    }

    public ArmorStand createArmorStand(Player player) {
        Location loc = player.getLocation().clone();
        loc.setY(loc.getY() + config.getNametagHeight());

        ArmorStand armorStand = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);

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

        pluginArmorStands.add(armorStand.getUniqueId());
        return armorStand;
    }

    public void removeArmorStand(ArmorStand armorStand) {
        if (armorStand != null && !armorStand.isDead()) {
            pluginArmorStands.remove(armorStand.getUniqueId());
            armorStand.remove();
        }
    }

    public void cleanupOrphanedArmorStands() {
        int removed = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand) {
                    ArmorStand armorStand = (ArmorStand) entity;
                    if (armorStand.isMarker() && armorStand.isCustomNameVisible() &&
                            !armorStand.isVisible() && !pluginArmorStands.contains(armorStand.getUniqueId())) {

                        String customName = armorStand.getCustomName();
                        if (customName != null && animationManager.containsRainbowColors(customName)) {
                            armorStand.remove();
                            removed++;
                        }
                    }
                }
            }
        }

        if (removed > 0) {
            Bukkit.getLogger().info("Removed " + removed + " orphaned armor stands.");
        }
    }

    public void cleanupDuplicates(Collection<Location> knownLocations) {
        List<ArmorStand> toRemove = new ArrayList<>();

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

        for (ArmorStand as : toRemove) {
            as.remove();
        }
    }

    public boolean isPluginArmorStand(UUID uuid) {
        return pluginArmorStands.contains(uuid);
    }

    public void clearArmorStands() {
        pluginArmorStands.clear();
    }
}