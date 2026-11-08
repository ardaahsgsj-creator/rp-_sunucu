package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Banka (/banka), polis para cezası (/ceza), ölünce para kaybı, hastanede doğma. */
public class Economy implements Listener {

    private static final String PFX = RpPlugin.PREFIX;
    private static final double OLUM_KAYBI = 0.10;      // ölünce cüzdanın %10'u gider
    private static final long CEZA_BEKLEME = 60_000L;

    private final RpPlugin plugin;
    private final Map<UUID, Long> fineCooldown = new HashMap<>();

    public Economy(RpPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String[] parts = e.getMessage().trim().split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        Player p = e.getPlayer();

        if (cmd.equals("/banka")) {
            e.setCancelled(true);
            if (plugin.get(p).loggedIn) banka(p, args);
        } else if (cmd.equals("/ceza")) {
            e.setCancelled(true);
            if (plugin.get(p).loggedIn) ceza(p, args);
        }
    }

    // ------------------------------------------------------------ BANKA

    private void banka(Player p, String[] args) {
        PlayerData d = plugin.get(p);
        if (args.length == 0) {
            p.sendMessage(PFX + "§7Cüzdan: §a$" + (int) d.money + " §8| §7Banka: §b$" + (int) d.bank);
            p.sendMessage(PFX + "§7/banka yatir <miktar|hepsi>  /banka cek <miktar|hepsi>");
            return;
        }
        Location loc = plugin.getConfig().getLocation("nokta.banka");
        if (loc != null && (loc.getWorld() == null || !loc.getWorld().equals(p.getWorld())
                || loc.distance(p.getLocation()) > 6)) {
            p.sendMessage(PFX + "§cBanka/ATM'ye yakın değilsin.");
            return;
        }
        if (args.length != 2) {
            p.sendMessage(PFX + "§cKullanım: /banka <yatir|cek> <miktar|hepsi>");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("yatir") && !sub.equals("cek")) {
            p.sendMessage(PFX + "§cKullanım: /banka <yatir|cek> <miktar|hepsi>");
            return;
        }
        double amount;
        if (args[1].equalsIgnoreCase("hepsi")) {
            amount = Math.floor(sub.equals("yatir") ? d.money : d.bank);
        } else {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                p.sendMessage(PFX + "§cGeçersiz miktar.");
                return;
            }
        }
        if (amount <= 0) {
            p.sendMessage(PFX + "§cGeçersiz miktar.");
            return;
        }
        if (sub.equals("yatir")) {
            if (amount > d.money) {
                p.sendMessage(PFX + "§cCüzdanında bu kadar para yok.");
                return;
            }
            d.money -= amount;
            d.bank += amount;
            p.sendMessage(PFX + "§a$" + (int) amount + " bankaya yatırıldı. §7Banka: §b$" + (int) d.bank);
        } else {
            if (amount > d.bank) {
                p.sendMessage(PFX + "§cBankada bu kadar para yok.");
                return;
            }
            d.bank -= amount;
            d.money += amount;
            p.sendMessage(PFX + "§a$" + (int) amount + " çekildi. §7Cüzdan: §a$" + (int) d.money);
        }
    }

    // ------------------------------------------------------------ POLİS CEZASI

    private void ceza(Player p, String[] args) {
        PlayerData d = plugin.get(p);
        if (!Job.POLIS.name().equals(d.job)) {
            p.sendMessage(PFX + "§cBunu sadece Polisler yapabilir.");
            return;
        }
        if (args.length != 2) {
            p.sendMessage(PFX + "§cKullanım: /ceza <oyuncu> <miktar>");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null || !plugin.get(t).loggedIn) {
            p.sendMessage(PFX + "§cOyuncu bulunamadı.");
            return;
        }
        if (t.equals(p)) {
            p.sendMessage(PFX + "§cKendine ceza kesemezsin.");
            return;
        }
        if (!t.getWorld().equals(p.getWorld()) || t.getLocation().distance(p.getLocation()) > 5) {
            p.sendMessage(PFX + "§cOyuncu yeterince yakın değil.");
            return;
        }
        int rank = Job.POLIS.rankIndex(d.xpOf(Job.POLIS));
        int max = 200 * (rank + 1);
        int miktar;
        try {
            miktar = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            p.sendMessage(PFX + "§cGeçersiz miktar.");
            return;
        }
        if (miktar < 10 || miktar > max) {
            p.sendMessage(PFX + "§cCeza 10 ile " + max + " arasında olmalı (rütbene göre).");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = fineCooldown.get(t.getUniqueId());
        if (last != null && now - last < CEZA_BEKLEME) {
            p.sendMessage(PFX + "§cBu oyuncuya yakın zamanda ceza kesildi.");
            return;
        }
        PlayerData td = plugin.get(t);
        double alinan = Math.min(miktar, td.money);
        td.money -= alinan;
        fineCooldown.put(t.getUniqueId(), now);
        p.sendMessage(PFX + "§a" + t.getName() + " kişisine §e$" + (int) alinan + " §apara cezası kesildi.");
        t.sendMessage(PFX + "§c" + p.getName() + " (Polis) sana $" + (int) alinan + " para cezası kesti!");
        plugin.addXp(p, Job.POLIS, 10);
    }

    // ------------------------------------------------------------ ÖLÜM / HASTANE

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PlayerData d = plugin.get(p);
        if (!d.loggedIn) return;
        double lose = Math.floor(d.money * OLUM_KAYBI);
        if (lose >= 1) {
            d.money -= lose;
            p.sendMessage(PFX + "§cÖlünce cüzdanından §e$" + (int) lose + " §ckaybettin. §7Parayı bankada tut!");
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Location h = plugin.getConfig().getLocation("nokta.hastane");
        if (h != null && h.getWorld() != null && !e.isBedSpawn()) {
            e.setRespawnLocation(h);
            Player p = e.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> p.sendMessage(PFX + "§bHastanede uyandın."), 5L);
        }
    }
}
