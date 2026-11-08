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

    // 6 satırlık menü: üstte 4, altta 4 meslek (her biri kendi kutucuğunda)
    private static final int[] SLOTS = {10, 12, 14, 16, 37, 39, 41, 43};
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
            case MADENCI: return Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            case ESNAF: return Material.GREEN_STAINED_GLASS_PANE;
            case DOKTOR: return Material.CYAN_STAINED_GLASS_PANE;
            case AVUKAT: return Material.ORANGE_STAINED_GLASS_PANE;
            default: return Material.YELLOW_STAINED_GLASS_PANE;
        }
    }

    private static String[] descOf(Job j) {
        switch (j) {
            case MAFYA:
                return new String[]{"§7Şehrin karanlık yüzü.", "§7Haraç topla: §f/harac <oyuncu>"};
            case POLIS:
                return new String[]{"§7Düzeni sağla.", "§f/ara  /tutukla  /serbest"};
            case KACAKCI:
                return new String[]{"§7Mal al, kaçır, sat.", "§f/paket  /sat  §7(polise yakalanma!)"};
            case MADENCI:
                return new String[]{"§7Cevher kır, para kazan.", "§7Güvenli ve sabit gelir."};
            case ESNAF:
                return new String[]{"§7Eşya al, satış yap.", "§7Elindekini teklif et:", "§f/teklif <oyuncu> <fiyat>"};
            case DOKTOR:
                return new String[]{"§7Yaralıları iyileştir.", "§f/iyilestir <oyuncu>"};
            case AVUKAT:
                return new String[]{"§7Tutuklu müvekkillerin", "§7cezasını kısalt.", "§f/savun <oyuncu>"};
            default:
                return new String[]{"§7Tapu sat, arsa ve ev ticareti.", "§f/teklif <oyuncu> <fiyat>"};
        }
    }

    private static String progressBar(Job j, int xp) {
        int idx = j.rankIndex(xp);
        if (idx >= Job.THRESH.length - 1) return "§6★ MAKSİMUM RÜTBE ★";
        int from = Job.THRESH[idx];
        int to = Job.THRESH[idx + 1];
        int filled = (int) Math.round(10.0 * (xp - from) / (to - from));
        if (filled < 0) filled = 0;
        if (filled > 10) filled = 10;
        return "§a" + "■".repeat(filled) + "§8" + "■".repeat(10 - filled) + " §f" + xp + "§7/§f" + to + " XP";
    }

    public static void open(Player p) {
        RpPlugin plugin = (RpPlugin) Bukkit.getPluginManager().getPlugin("RpSunucusu");
        if (plugin == null) return;
        JobMenu menu = new JobMenu(plugin);
        menu.inv = Bukkit.createInventory(menu, 54, "§6§l✦ §e§lMESLEK SEÇ §6§l✦");
        PlayerData d = plugin.get(p);

        // Arka plan
        ItemStack bg = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) menu.inv.setItem(i, bg);

        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length && i < SLOTS.length; i++) {
            Job j = jobs[i];
            boolean current = j.name().equals(d.job);
            int xp = d.xpOf(j);

            // Meslek ikonunun üstü ve altı renkli cam (seçili meslek yeşil)
            Material pm = current ? Material.LIME_STAINED_GLASS_PANE : paneOf(j);
            ItemStack colored = pane(pm, j.color + "§l" + j.display);
            menu.inv.setItem(SLOTS[i] - 9, colored);
            menu.inv.setItem(SLOTS[i] + 9, colored);

            ItemStack it = new ItemStack(j.icon);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(j.color + "§l" + j.display.toUpperCase() + (current ? " §a§l✔" : ""));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            List<String> lore = new ArrayList<>();
            lore.add("§8§m                          ");
            for (String s : descOf(j)) lore.add(s);
            lore.add("");
            lore.add("§6§lRÜTBELER");
            int cur = j.rankIndex(xp);
            for (int r = 0; r < j.ranks.length; r++) {
                String mark = r == cur ? " §a◀ Sen" : "";
                String color = r <= cur ? "§f" : "§8";
                lore.add(" " + color + "▪ " + j.ranks[r] + mark);
            }
            lore.add("");
            lore.add("§7Rütben: " + j.color + j.rankName(xp));
            lore.add(progressBar(j, xp));
            lore.add("§8§m                          ");
            lore.add(current ? "§a✔ Şu an bu meslektesin" : "§e▶ Seçmek için tıkla!");
            m.setLore(lore);
            it.setItemMeta(m);
            menu.inv.setItem(SLOTS[i], it);
        }

        // Alt ortada oyuncu bilgisi
        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§6§l" + p.getName());
        List<String> il = new ArrayList<>();
        il.add("§7Bakiye: §a$" + (int) d.money);
        Job cj = Job.from(d.job);
        il.add("§7Meslek: " + (cj == null ? "§cYok" : cj.color + cj.display));
        il.add("");
        il.add("§8Meslek değiştirince XP'n silinmez.");
        im.setLore(il);
        info.setItemMeta(im);
        menu.inv.setItem(49, info);

        // Üst ortada başlık
        ItemStack title = new ItemStack(Material.NETHER_STAR);
        ItemMeta tm = title.getItemMeta();
        tm.setDisplayName("§e§lŞehre Hoş Geldin!");
        List<String> tl = new ArrayList<>();
        tl.add("§7Bir meslek seç ve");
        tl.add("§7kendi hikayeni yaz.");
        tm.setLore(tl);
        title.setItemMeta(tm);
        menu.inv.setItem(4, title);

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
            if (!(e.getWhoClicked() instanceof Player)) return;
            Player p = (Player) e.getWhoClicked();
            if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;

            Job[] jobs = Job.values();
            for (int i = 0; i < jobs.length && i < SLOTS.length; i++) {
                if (e.getSlot() == SLOTS[i]) {
                    PlayerData d = plugin.get(p);
                    Job j = jobs[i];
                    d.job = j.name();
                    plugin.save(p.getUniqueId());
                    p.closeInventory();
                    p.sendTitle(j.color + "§l" + j.display.toUpperCase(), "§7" + j.rankName(d.xpOf(j)), 10, 50, 15);
                    p.sendMessage(RpPlugin.PREFIX + "§aArtık bir " + j.color + j.display
                            + " §a(" + j.color + j.rankName(d.xpOf(j)) + "§a) olarak çalışıyorsun!");
                    return;
                }
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent e) {
            if (!(e.getInventory().getHolder() instanceof JobMenu)) return;
            if (!(e.getPlayer() instanceof Player)) return;
            Player p = (Player) e.getPlayer();
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
