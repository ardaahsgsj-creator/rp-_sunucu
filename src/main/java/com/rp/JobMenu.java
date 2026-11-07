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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class JobMenu implements InventoryHolder {

    private static final int[] SLOTS = {10, 12, 14, 16};
    private final RpPlugin plugin;
    private Inventory inv;

    private JobMenu(RpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public static void open(Player p) {
        RpPlugin plugin = (RpPlugin) Bukkit.getPluginManager().getPlugin("RpSunucusu");
        if (plugin == null) return;
        JobMenu menu = new JobMenu(plugin);
        menu.inv = Bukkit.createInventory(menu, 27, "§8Bir Meslek Seç");
        PlayerData d = plugin.get(p);

        // Süs camları
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(" ");
        glass.setItemMeta(gm);
        for (int i = 0; i < 27; i++) menu.inv.setItem(i, glass);

        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length && i < SLOTS.length; i++) {
            Job j = jobs[i];
            ItemStack it = new ItemStack(j.icon);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(j.color + "§l" + j.display);
            List<String> lore = new ArrayList<>();
            lore.add("§7Rütbeler:");
            for (int r = 0; r < j.ranks.length; r++) {
                lore.add("§8 - §f" + j.ranks[r] + " §8(" + Job.THRESH[r] + " XP)");
            }
            lore.add("");
            lore.add("§7Mevcut XP'n: §e" + d.xpOf(j));
            lore.add("§7Rütben: " + j.color + j.rankName(d.xpOf(j)));
            lore.add("");
            lore.add(j.name().equals(d.job) ? "§aŞu an bu meslektesin" : "§eSeçmek için tıkla!");
            m.setLore(lore);
            it.setItemMeta(m);
            menu.inv.setItem(SLOTS[i], it);
        }
        p.openInventory(menu.inv);
    }

    public static class MenuListener implements Listener {
        private final RpPlugin plugin;

        public MenuListener(RpPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder() instanceof JobMenu)) return;
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;

            Job[] jobs = Job.values();
            for (int i = 0; i < jobs.length && i < SLOTS.length; i++) {
                if (e.getSlot() == SLOTS[i]) {
                    PlayerData d = plugin.get(p);
                    Job j = jobs[i];
                    d.job = j.name();
                    plugin.save(p.getUniqueId());
                    p.closeInventory();
                    p.sendMessage(RpPlugin.PREFIX + "§aArtık bir " + j.color + j.display
                            + " §a(" + j.color + j.rankName(d.xpOf(j)) + "§a) olarak çalışıyorsun!");
                    return;
                }
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent e) {
            if (!(e.getInventory().getHolder() instanceof JobMenu)) return;
            if (!(e.getPlayer() instanceof Player p)) return;
            PlayerData d = plugin.get(p);
            // Meslek seçmeden kapatırsa tekrar aç
            if (d.loggedIn && Job.from(d.job) == null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) JobMenu.open(p);
                }, 2L);
            }
        }
    }
}
