package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/** Kaçakçı /paket alınca online polislere telsizle haber verir. */
public class Alert implements Listener {

    /** true yaparsan polise şüphelinin adı da yazılır, false ise sadece konum. */
    private static final boolean SHOW_NAME = false;

    private final RpPlugin plugin;

    public Alert(RpPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!msg.equals("/paket")) return;

        Player p = e.getPlayer();
        PlayerData d = plugin.get(p);
        if (!d.loggedIn) return;

        // Paket gerçekten alındıysa lastPaket değişir (bekleme, yetki vb. hatalarda değişmez)
        final long old = d.lastPaket;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (d.lastPaket != old) alertPolice(p);
        });
    }

    private void alertPolice(Player p) {
        Location l = p.getLocation();
        int sent = 0;

        for (Player cop : Bukkit.getOnlinePlayers()) {
            if (cop.equals(p)) continue;
            PlayerData cd = plugin.get(cop);
            if (!cd.loggedIn || !Job.POLIS.name().equals(cd.job)) continue;

            String who = SHOW_NAME ? " §7Şüpheli: §f" + p.getName() : "";
            String dist = "";
            if (cop.getWorld().equals(l.getWorld())) {
                dist = " §7(" + (int) cop.getLocation().distance(l) + " blok uzakta)";
            }

            cop.sendMessage("§9§l[POLİS TELSİZİ] §fKaçak mal alımı bildirildi!" + who);
            cop.sendMessage("§7Konum: §eX " + l.getBlockX() + " Y " + l.getBlockY()
                    + " Z " + l.getBlockZ() + dist);
            cop.sendTitle("§9§lPOLİS TELSİZİ", "§cKaçak mal alımı!", 5, 40, 10);
            sent++;
        }

        if (sent > 0) {
            p.sendMessage(RpPlugin.PREFIX + "§7Birisi seni gördü, §9polise haber verildi§7!");
        }
    }
}
