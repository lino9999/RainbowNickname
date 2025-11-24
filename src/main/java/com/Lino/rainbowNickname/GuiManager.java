package com.Lino.rainbowNickname;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class GuiManager implements Listener {

    private final RainbowNickname plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .character('§')
            .build();
    private final String guiTitle = "§8RainbowNickname Menu";

    public GuiManager(RainbowNickname plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 18, guiTitle);

        for (AnimationType type : AnimationType.values()) {
            ItemStack item = new ItemStack(type.getIcon());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                Component component = miniMessage.deserialize("<!i>" + type.getDisplayName());
                meta.setDisplayName(legacySerializer.serialize(component));
                item.setItemMeta(meta);
            }
            gui.addItem(item);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§cDisable Nickname");
            close.setItemMeta(closeMeta);
        }
        gui.setItem(17, close);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem.getType() == Material.BARRIER) {
            plugin.getNickManager().disableNick(player);
            plugin.getDataManager().setPlayerAnimation(player.getUniqueId(), null);
            plugin.getMessageManager().sendMessage(player, "nick-disabled", "%player%", player.getName());
            player.closeInventory();
            return;
        }

        Arrays.stream(AnimationType.values())
                .filter(type -> type.getIcon() == clickedItem.getType())
                .findFirst()
                .ifPresent(type -> {
                    plugin.getNickManager().setAnimation(player, type);
                    plugin.getDataManager().setPlayerAnimation(player.getUniqueId(), type);
                    plugin.getMessageManager().sendMessage(player, "nick-set", "%type%", type.getDisplayName());
                    player.closeInventory();
                });
    }
}