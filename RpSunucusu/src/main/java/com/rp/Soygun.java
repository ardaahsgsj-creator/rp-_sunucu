package com.rp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /soygun : soygun noktasında 30 saniye durarak banka/dükkan soyulur.
 * Polise anında haber gider. Polis yaklaşırsa soygun başarısız olur.
 */
public class Soygun implements Listener {

    private static final String PFX = RpPlugin.PREFIX;
    private static final long SURE = 30_000L;
    private static final long BEKLEME = 10 * 60_000L;

    private final RpPlugin plugin;
    private UUID active = null;
    private long lastStart = 0;

    public Soygun(RpPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private List<Player> cops() {
        List<Player> list = new ArrayList<>();
        for (Player c : Bukkit.getOnlinePlayers()) {
            PlayerData cd = plugin.get(c);
            if (cd.loggedIn && Job.POLIS.name().equals(cd.job)) list.add(c);
        }
        return list;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!msg.equals("/soygun")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (plugin.get(p).loggedIn) baslat(p);
    }

    private void baslat(Player p) {
        PlayerData d = plugin.get(p);
        if (Job.POLIS.name().equals(d.job)) {
            p.sendMessage(PFX + "§cPolisler soygun yapamaz.");
            return;
        }
        final Location pt = plugin.getConfig().getLocation("nokta.soygun");
        if (pt == null || pt.getWorld() == null) {
            p.sendMessage(PFX + "§cSoygun noktası ayarlanmamış. (/rpadmin setnokta soygun)");
            return;
        }
        if (!pt.getWorld().equals(p.getWorld()) || pt.distance(p.getLocation()) > 5) {
            p.sendMessage(PFX + "§cSoygun noktasında değilsin.");
            return;
        }
        long now = System.currentTimeMillis();
        if (active != null) {
            p.sendMessage(PFX + "§cŞu an zaten bir soygun sürüyor.");
            return;
        }
        if (now - lastStart < BEKLEME) {
            p.sendMessage(PFX + "§cYer az önce soyuldu. §7" + ((BEKLEME - (now - lastStart)) / 60000 + 1) + " dk sonra tekrar dene.");
            return;
        }
        List<Player> polisler = cops();
        if (polisler.isEmpty()) {
            p.sendMessage(PFX + "§cSoygun için en az 1 polis çevrimiçi olmalı.");
            return;
        }

        final UUID id = p.getUniqueId();
        active = id;
        lastStart = now;
        final long start = now;

        p.sendMessage(PFX + "§c§lSOYGUN BAŞLADI! §f30 saniye bu bölgeden ayrılma!");
        for (Player c : polisler) {
            c.sendMessage("§9§l[POLİS TELSİZİ] §c§lSOYGUN ALARMI!");
            c.sendMessage("§7Konum: §eX " + pt.getBlockX() + " Y " + pt.getBlockY() + " Z " + pt.getBlockZ());
            c.sendTitle("§c§lSOYGUN!", "§7Hemen olay yerine gidin", 5, 60, 10);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                Player r = Bukkit.getPlayer(id);
                long gecen = System.currentTimeMillis() - start;
                String fail = null;

                if (r == null || !r.isOnline()) {
                    fail = "Soyguncu oyundan ayrıldı";
                } else if (r.isDead()) {
                    fail = "Soyguncu öldü";
                } else if (!r.getWorld().equals(pt.getWorld()) || r.getLocation().distance(pt) > 7) {
                    fail = "Soyguncu bölgeden uzaklaştı";
                } else {
                    for (Player c : cops()) {
                        if (c.getWorld().equals(pt.getWorld()) && c.getLocation().distance(pt) <= 6) {
                            fail = "Polis olay yerine ulaştı";
                            break;
                        }
                    }
                }

                if (fail != null) {
                    bitir(r, false, fail, 0);
                    cancel();
                    return;
                }
                if (gecen >= SURE) {
                    int odul = ThreadLocalRandom.current().nextInt(400, 801);
                    plugin.addMoney(r, odul);
                    bitir(r, true, null, odul);
                    cancel();
                    return;
                }
                long kalan = (SURE - gecen) / 1000 + 1;
                r.sendActionBar(Component.text("§cSoygun sürüyor: " + kalan + " sn"));
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void bitir(Player r, boolean basarili, String sebep, int odul) {
        active = null;
        if (basarili) {
            if (r != null) r.sendMessage(PFX + "§a§lSOYGUN BAŞARILI! §e+$" + odul + " §7Hemen kaç!");
            for (Player c : cops()) {
                c.sendMessage("§9§l[POLİS TELSİZİ] §cSoygun tamamlandı, şüpheli kaçıyor!");
            }
        } else {
            if (r != null) r.sendMessage(PFX + "§cSoygun başarısız: " + sebep);
            for (Player c : cops()) {
                c.sendMessage("§9§l[POLİS TELSİZİ] §aSoygun durduruldu: " + sebep);
            }
        }
    }
}
