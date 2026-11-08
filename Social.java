package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * Yerel sohbet (sadece yakındakiler duyar), /g global sohbet, /telsiz (polis),
 * /me, /do, /kimlik.
 */
public class Social implements Listener {

    private static final String PFX = RpPlugin.PREFIX;
    private static final double SOHBET_MESAFE = 40;
    private static final double RP_MESAFE = 30;

    private final RpPlugin plugin;

    public Social(RpPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------ YEREL SOHBET

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player s = e.getPlayer();
        org.bukkit.Location sl = s.getLocation();
        e.getRecipients().removeIf(r -> !r.equals(s)
                && (!r.getWorld().equals(sl.getWorld()) || r.getLocation().distance(sl) > SOHBET_MESAFE));
    }

    // ------------------------------------------------------------ KOMUTLAR

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage().trim();
        String[] parts = raw.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        String rest = parts.length > 1 ? raw.substring(raw.indexOf(' ') + 1).trim() : "";
        Player p = e.getPlayer();

        switch (cmd) {
            case "/g":
            case "/global":
                e.setCancelled(true);
                if (plugin.get(p).loggedIn) global(p, rest);
                break;
            case "/telsiz":
                e.setCancelled(true);
                if (plugin.get(p).loggedIn) telsiz(p, rest);
                break;
            case "/me":
                e.setCancelled(true);
                if (plugin.get(p).loggedIn) rp(p, rest, true);
                break;
            case "/do":
                e.setCancelled(true);
                if (plugin.get(p).loggedIn) rp(p, rest, false);
                break;
            case "/kimlik":
                e.setCancelled(true);
                if (plugin.get(p).loggedIn) kimlik(p, args);
                break;
            default:
                break;
        }
    }

    private void global(Player p, String msg) {
        if (msg.isEmpty()) {
            p.sendMessage(PFX + "§cKullanım: /g <mesaj>");
            return;
        }
        for (Player r : Bukkit.getOnlinePlayers()) {
            if (plugin.get(r).loggedIn) {
                r.sendMessage("§8[§6GLOBAL§8] §f" + p.getName() + "§7: §f" + msg);
            }
        }
    }

    private void telsiz(Player p, String msg) {
        PlayerData d = plugin.get(p);
        if (!Job.POLIS.name().equals(d.job)) {
            p.sendMessage(PFX + "§cTelsiz sadece Polislere özel.");
            return;
        }
        if (msg.isEmpty()) {
            p.sendMessage(PFX + "§cKullanım: /telsiz <mesaj>");
            return;
        }
        String rutbe = Job.POLIS.rankName(d.xpOf(Job.POLIS));
        for (Player r : Bukkit.getOnlinePlayers()) {
            PlayerData rd = plugin.get(r);
            if (rd.loggedIn && Job.POLIS.name().equals(rd.job)) {
                r.sendMessage("§9§l[TELSİZ] §b" + rutbe + " " + p.getName() + "§7: §f" + msg);
            }
        }
    }

    private void rp(Player p, String msg, boolean me) {
        if (msg.isEmpty()) {
            p.sendMessage(PFX + (me ? "§cKullanım: /me <eylem>" : "§cKullanım: /do <durum>"));
            return;
        }
        String line = me ? "§d* " + p.getName() + " " + msg : "§d* " + msg + " §7((" + p.getName() + "))";
        for (Player r : Bukkit.getOnlinePlayers()) {
            if (r.equals(p) || (r.getWorld().equals(p.getWorld())
                    && r.getLocation().distance(p.getLocation()) <= RP_MESAFE)) {
                r.sendMessage(line);
            }
        }
    }

    private String kimlikSatiri(Player p) {
        PlayerData d = plugin.get(p);
        Job j = Job.from(d.job);
        String meslek = j == null ? "§7Vatandaş" : j.color + j.display + " §7(" + j.color + j.rankName(d.xpOf(j)) + "§7)";
        return "§7Ad: §f" + p.getName() + " §8| §7Meslek: " + meslek;
    }

    private void kimlik(Player p, String[] args) {
        if (args.length == 0) {
            p.sendMessage("§8§m                    ");
            p.sendMessage("§e§lKİMLİK");
            p.sendMessage(kimlikSatiri(p));
            p.sendMessage("§8§m                    ");
            p.sendMessage(PFX + "§7Başkasına göstermek için: /kimlik goster <oyuncu>");
            return;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("goster")) {
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null || t.equals(p) || !t.getWorld().equals(p.getWorld())
                    || t.getLocation().distance(p.getLocation()) > 4) {
                p.sendMessage(PFX + "§cOyuncu bulunamadı ya da yeterince yakın değil.");
                return;
            }
            t.sendMessage("§e" + p.getName() + " sana kimliğini gösterdi:");
            t.sendMessage(kimlikSatiri(p));
            p.sendMessage(PFX + "§aKimliğini " + t.getName() + " kişisine gösterdin.");
            return;
        }
        p.sendMessage(PFX + "§cKullanım: /kimlik  ya da  /kimlik goster <oyuncu>");
    }
}
