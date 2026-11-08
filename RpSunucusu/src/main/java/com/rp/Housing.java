package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ev / mülk sistemi. Emlakçı /ev olustur ile satılık alan açar, oyuncu /ev satinal ile alır.
 * Sahibi ve ortakları dışında kimse alanda blok kıramaz, koyamaz, sandık/kapı açamaz.
 */
public class Housing implements Listener {

    private static final String PFX = RpPlugin.PREFIX;
    private static final int MAX_EV = 3;

    private static class House {
        int id;
        String world;
        int cx;
        int cz;
        int r;
        int price;
        UUID creator;
        UUID owner;
        Set<UUID> members = new HashSet<>();
    }

    private final RpPlugin plugin;
    private final File file;
    private final Map<Integer, House> houses = new LinkedHashMap<>();
    private final Map<UUID, Long> warnCd = new HashMap<>();
    private int nextId = 1;

    public Housing(RpPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "houses.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------ KAYIT

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("houses");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            ConfigurationSection h = s.getConfigurationSection(key);
            if (h == null) continue;
            try {
                House ho = new House();
                ho.id = Integer.parseInt(key);
                ho.world = h.getString("world");
                ho.cx = h.getInt("cx");
                ho.cz = h.getInt("cz");
                ho.r = h.getInt("r");
                ho.price = h.getInt("price");
                String c = h.getString("creator", "");
                ho.creator = c.isEmpty() ? null : UUID.fromString(c);
                String o = h.getString("owner", "");
                ho.owner = o.isEmpty() ? null : UUID.fromString(o);
                for (String m : h.getStringList("members")) ho.members.add(UUID.fromString(m));
                houses.put(ho.id, ho);
                nextId = Math.max(nextId, ho.id + 1);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("houses.yml okunamadı: " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (House h : houses.values()) {
            String p = "houses." + h.id + ".";
            y.set(p + "world", h.world);
            y.set(p + "cx", h.cx);
            y.set(p + "cz", h.cz);
            y.set(p + "r", h.r);
            y.set(p + "price", h.price);
            y.set(p + "creator", h.creator == null ? "" : h.creator.toString());
            y.set(p + "owner", h.owner == null ? "" : h.owner.toString());
            List<String> ms = new ArrayList<>();
            for (UUID u : h.members) ms.add(u.toString());
            y.set(p + "members", ms);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("houses.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ YARDIMCI

    private House at(Location l) {
        if (l == null || l.getWorld() == null) return null;
        String w = l.getWorld().getName();
        for (House h : houses.values()) {
            if (h.world.equals(w) && Math.abs(l.getBlockX() - h.cx) <= h.r
                    && Math.abs(l.getBlockZ() - h.cz) <= h.r) {
                return h;
            }
        }
        return null;
    }

    private static String name(UUID u) {
        if (u == null) return "-";
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return op.getName() == null ? "?" : op.getName();
    }

    private int ownedCount(UUID u) {
        int c = 0;
        for (House h : houses.values()) {
            if (u.equals(h.owner)) c++;
        }
        return c;
    }

    private boolean allowed(Player p, House h) {
        if (p.hasPermission("rp.admin") && p.getGameMode() == GameMode.CREATIVE) return true;
        if (h.owner == null) return false;
        return h.owner.equals(p.getUniqueId()) || h.members.contains(p.getUniqueId());
    }

    private void warn(Player p, House h) {
        long now = System.currentTimeMillis();
        Long last = warnCd.get(p.getUniqueId());
        if (last != null && now - last < 2000) return;
        warnCd.put(p.getUniqueId(), now);
        if (h.owner == null) {
            p.sendMessage(PFX + "§cBu mülk satılık! §7Satın almak için: §f/ev satinal §7(§a$" + h.price + "§7)");
        } else {
            p.sendMessage(PFX + "§cBu mülk §e" + name(h.owner) + " §ctarafından korunuyor.");
        }
    }

    private void credit(UUID id, double amount, String mesaj) {
        if (id == null) return;
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            plugin.get(online).money += amount;
            online.sendMessage(mesaj);
            return;
        }
        PlayerData cd = plugin.load(id);
        cd.money += amount;
        plugin.unload(id);
    }

    // ------------------------------------------------------------ KORUMA

    private void check(Player p, Location l, Cancellable e) {
        House h = at(l);
        if (h == null || allowed(p, h)) return;
        e.setCancelled(true);
        warn(p, h);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        check(e.getPlayer(), e.getBlock().getLocation(), e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        check(e.getPlayer(), e.getBlock().getLocation(), e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        String n = e.getClickedBlock().getType().name();
        boolean korunan = n.contains("CHEST") || n.contains("DOOR") || n.contains("GATE")
                || n.contains("BUTTON") || n.equals("LEVER") || n.contains("FURNACE")
                || n.contains("BARREL") || n.contains("SHULKER") || n.contains("HOPPER")
                || n.contains("DROPPER") || n.contains("DISPENSER") || n.contains("ANVIL");
        if (!korunan) return;
        check(e.getPlayer(), e.getClickedBlock().getLocation(), e);
    }

    // ------------------------------------------------------------ KOMUT

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String[] parts = e.getMessage().trim().split("\\s+");
        if (!parts[0].equalsIgnoreCase("/ev")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!plugin.get(p).loggedIn) return;
        ev(p, Arrays.copyOfRange(parts, 1, parts.length));
    }

    private void ev(Player p, String[] a) {
        if (a.length == 0) {
            p.sendMessage(PFX + "§7/ev bilgi  /ev satinal  /ev birak  /ev liste");
            p.sendMessage(PFX + "§7/ev ortak <ekle|cikar> <oyuncu>  §8| §7Emlakçı: /ev olustur <fiyat> <yaricap>  /ev sil");
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "olustur": olustur(p, a); break;
            case "satinal": satinal(p); break;
            case "bilgi": bilgi(p); break;
            case "birak": birak(p); break;
            case "liste": liste(p); break;
            case "ortak": ortak(p, a); break;
            case "sil": sil(p); break;
            default: p.sendMessage(PFX + "§cBilinmeyen alt komut. /ev yaz."); break;
        }
    }

    private void olustur(Player p, String[] a) {
        PlayerData d = plugin.get(p);
        if (!Job.EMLAKCI.name().equals(d.job)) {
            p.sendMessage(PFX + "§cMülk oluşturmayı sadece Emlakçılar yapabilir.");
            return;
        }
        if (a.length != 3) {
            p.sendMessage(PFX + "§cKullanım: /ev olustur <fiyat> <yaricap>");
            return;
        }
        int price;
        int r;
        try {
            price = Integer.parseInt(a[1]);
            r = Integer.parseInt(a[2]);
        } catch (NumberFormatException ex) {
            p.sendMessage(PFX + "§cGeçersiz sayı.");
            return;
        }
        int rank = Job.EMLAKCI.rankIndex(d.xpOf(Job.EMLAKCI));
        int maxR = 6 + 3 * rank;
        if (price < 100 || price > 1_000_000) {
            p.sendMessage(PFX + "§cFiyat 100 ile 1.000.000 arasında olmalı.");
            return;
        }
        if (r < 3 || r > maxR) {
            p.sendMessage(PFX + "§cYarıçap 3 ile " + maxR + " arasında olmalı (rütbene göre).");
            return;
        }
        Location l = p.getLocation();
        String w = l.getWorld().getName();
        for (House h : houses.values()) {
            if (h.world.equals(w) && Math.abs(l.getBlockX() - h.cx) <= r + h.r
                    && Math.abs(l.getBlockZ() - h.cz) <= r + h.r) {
                p.sendMessage(PFX + "§cBu alan başka bir mülkle çakışıyor (mülk #" + h.id + ").");
                return;
            }
        }
        House h = new House();
        h.id = nextId++;
        h.world = w;
        h.cx = l.getBlockX();
        h.cz = l.getBlockZ();
        h.r = r;
        h.price = price;
        h.creator = p.getUniqueId();
        houses.put(h.id, h);
        save();
        p.sendMessage(PFX + "§aMülk #" + h.id + " oluşturuldu: §f" + (2 * r + 1) + "x" + (2 * r + 1)
                + " alan, fiyat §a$" + price + "§f. Alıcılar içinde dururken §e/ev satinal §fyazar.");
        plugin.addXp(p, Job.EMLAKCI, 10);
    }

    private void satinal(Player p) {
        House h = at(p.getLocation());
        if (h == null) {
            p.sendMessage(PFX + "§cBir mülkün içinde değilsin.");
            return;
        }
        if (h.owner != null) {
            p.sendMessage(PFX + "§cBu mülkün zaten sahibi var: §e" + name(h.owner));
            return;
        }
        if (p.getUniqueId().equals(h.creator)) {
            p.sendMessage(PFX + "§cKendi ilanını satın alamazsın.");
            return;
        }
        if (ownedCount(p.getUniqueId()) >= MAX_EV) {
            p.sendMessage(PFX + "§cEn fazla " + MAX_EV + " mülk sahibi olabilirsin.");
            return;
        }
        PlayerData d = plugin.get(p);
        if (d.money < h.price) {
            p.sendMessage(PFX + "§cYetersiz bakiye! Gereken: §e$" + h.price);
            return;
        }
        d.money -= h.price;
        h.owner = p.getUniqueId();
        h.members.clear();
        save();
        credit(h.creator, h.price, PFX + "§aMülk #" + h.id + " satıldı! §e+$" + h.price);
        p.sendMessage(PFX + "§aMülk #" + h.id + " artık senin! §7(-$" + h.price + ")");
        if (h.creator != null) {
            Player c = Bukkit.getPlayer(h.creator);
            if (c != null) plugin.addXp(c, Job.EMLAKCI, Math.max(10, Math.min(60, h.price / 20)));
        }
    }

    private void bilgi(Player p) {
        House h = at(p.getLocation());
        if (h == null) {
            p.sendMessage(PFX + "§7Bir mülkün içinde değilsin.");
            return;
        }
        p.sendMessage("§8§m                    ");
        p.sendMessage("§e§lMülk #" + h.id);
        p.sendMessage("§7Sahibi: §f" + (h.owner == null ? "§aSatılık" : name(h.owner)));
        p.sendMessage("§7Fiyat: §a$" + h.price + " §8| §7Boyut: §f" + (2 * h.r + 1) + "x" + (2 * h.r + 1));
        if (!h.members.isEmpty()) {
            List<String> n = new ArrayList<>();
            for (UUID u : h.members) n.add(name(u));
            p.sendMessage("§7Ortaklar: §f" + String.join(", ", n));
        }
        p.sendMessage("§8§m                    ");
    }

    private void birak(Player p) {
        House h = at(p.getLocation());
        if (h == null || !p.getUniqueId().equals(h.owner)) {
            p.sendMessage(PFX + "§cBu mülkün sahibi sen değilsin.");
            return;
        }
        double iade = Math.floor(h.price * 0.5);
        plugin.get(p).money += iade;
        h.owner = null;
        h.members.clear();
        save();
        p.sendMessage(PFX + "§aMülkü bıraktın. İade: §e$" + (int) iade + " §7(fiyatın %50'si). Mülk tekrar satılık.");
    }

    private void liste(Player p) {
        boolean var = false;
        for (House h : houses.values()) {
            if (p.getUniqueId().equals(h.owner)) {
                p.sendMessage(PFX + "§7Mülk #" + h.id + " §8- §f" + h.world + " X" + h.cx + " Z" + h.cz);
                var = true;
            }
        }
        if (!var) p.sendMessage(PFX + "§7Hiç mülkün yok.");
    }

    private void ortak(Player p, String[] a) {
        if (a.length != 3) {
            p.sendMessage(PFX + "§cKullanım: /ev ortak <ekle|cikar> <oyuncu>");
            return;
        }
        House h = at(p.getLocation());
        if (h == null || !p.getUniqueId().equals(h.owner)) {
            p.sendMessage(PFX + "§cBunu kendi mülkünün içinde yapmalısın.");
            return;
        }
        OfflinePlayer t = Bukkit.getOfflinePlayer(a[2]);
        if (t.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(PFX + "§cKendini ekleyemezsin.");
            return;
        }
        if (a[1].equalsIgnoreCase("ekle")) {
            if (!t.hasPlayedBefore() && !t.isOnline()) {
                p.sendMessage(PFX + "§cOyuncu bulunamadı.");
                return;
            }
            h.members.add(t.getUniqueId());
            save();
            p.sendMessage(PFX + "§a" + a[2] + " mülke ortak eklendi.");
        } else if (a[1].equalsIgnoreCase("cikar")) {
            h.members.remove(t.getUniqueId());
            save();
            p.sendMessage(PFX + "§a" + a[2] + " ortaklıktan çıkarıldı.");
        } else {
            p.sendMessage(PFX + "§cKullanım: /ev ortak <ekle|cikar> <oyuncu>");
        }
    }

    private void sil(Player p) {
        House h = at(p.getLocation());
        if (h == null) {
            p.sendMessage(PFX + "§cBir mülkün içinde değilsin.");
            return;
        }
        boolean admin = p.hasPermission("rp.admin");
        boolean yaratan = p.getUniqueId().equals(h.creator) && h.owner == null;
        if (!admin && !yaratan) {
            p.sendMessage(PFX + "§cSadece yönetici ya da (satılmamışsa) ilanı açan Emlakçı silebilir.");
            return;
        }
        houses.remove(h.id);
        save();
        p.sendMessage(PFX + "§aMülk #" + h.id + " silindi.");
    }
}
