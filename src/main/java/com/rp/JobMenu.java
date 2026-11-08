package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class JobMenu implements InventoryHolder {

    // 5 satırlık menü: meslekler ortadaki satırda
    private static final int[] SLOTS = {19, 21, 23, 25};
    private final RpPlugin plugin;
    private Inventory inv;

    private JobMenu(RpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    private static ItemStack pane(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }

    private static Material paneOf(Job j) {
        switch (j) {
            case MAFYA: return Material.RED_STAINED_GLASS_PANE;
            case POLIS: return Material.BLUE_STAINED_GLASS_PANE;
            case KACAKCI: return Material.PURPLE_STAINED_GLASS_PANE;
            default: return Material.LIGHT_GRAY_STAINED_GLASS_PANE;
        }
    }

    private static String[] descOf(Job j) {
        switch
