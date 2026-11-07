package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

public class Commands implements CommandExecutor {

    private static final String P = RpPlugin.PREFIX;
    private static final String PAKET_AD = "§fKaçak Paket";

    private final RpPlugin plugin;

    public Commands(RpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase();

        if (!(s instanceof Player p)) {
            s.sendMessage("Bu komut sadece oyuncular içindir.");
            return true;
        }
        PlayerData d = plugin.get(p);

        if (n.equals("register")) return register(p, d, a);
        if (n.equals("login")) return login(p, d, a);

        if (!d.loggedIn) {
            p.sendMessage(P + "§cÖnce giriş yapmalısın!");
            return true;
        }

        switch (n) {
            case "meslek" -> JobMenu.open(p);
            case "rank" -> rank(p, d);
            case "para" -> para(p, d, a);
            case "paket" -> paket(p, d);
            case "sat" -> sat(p, d);
            case "ara" -> ara(p, d, a);
            case "tutukla" -> tutukla(p, d, a);
            case "serbest" -> serbest(p, d, a);
            case "harac" -> harac(p, d, a);
            case "rpadmin" -> admin(p, a);
            default -> { }
        }
        return true;
    }

    // ------------------------------------------------------------ KAYIT / GİRİŞ

    private boolean register(Player p, PlayerData d, String[] a) {
        if (d.loggedIn) {
            p.sendMessage(P + "§cZaten giriş yaptın.");
            return true;
        }
        if (d.hash != null) {
            p.sendMessage(P + "§cZaten kayıtlısın! §6/login <şifre>");
            return true;
        }
        if (a.length != 2) {
            p.sendMessage(P + "§cKullanım: /register <şifre> <şifre>");
            return true;
        }
        if (!a[0].equals(a[1])) {
            p.sendMessage(P + "§cŞifreler uyuşmuyor!");
            return true;
        }
        if (a[0].length() < 4) {
            p.sendMessage(P + "§cŞifre en az 4 karakter olmalı.");
            return true;
        }
        d.salt = RpPlugin.newSalt();
        d.hash = RpPlugin.hash(a[0], d.salt);
        plugin.finishLogin(p, d, true);
        return true;
    }

    private boolean login(Player p, PlayerData d, String[] a) {
        if (d.loggedIn) {
            p.sendMessage(P + "§cZaten giriş yaptın.");
            return true;
        }
        if (d.hash == null) {
            p.sendMessage(P + "§cÖnce kayıt ol: §6/register <şifre> <şifre>");
            return true;
        }
        if (a.length != 1) {
            p.sendMessage(P + "§cKullanım: /login <şifre>");
            return true;
        }
        if (!RpPlugin.hash(a[0], d.salt).equals(d.hash)) {
            p.sendMessage(P + "§cYanlış şifre!");
            return true;
        }
        plugin.finishLogin(p, d, false);
        return true;
    }

    // ------------------------------------------------------------ BİLGİ

    private void rank(Player p, PlayerData d) {
        Job j = Job.from(d.job);
        if (j == null) {
            p.sendMessage(P + "§7Henüz bir mesleğin yok. §6/meslek");
            return;
        }
        int xp = d.xpOf(j);
        int idx = j.rankIndex(xp);
        p.sendMessage("§8§m------------------------------");
        p.sendMessage("§7Meslek: " + j.color + j.display);
        p.sendMessage("§7Rütbe: " + j.color + j.rankName(xp) + " §8(" + (idx + 1) + "/" + j.ranks.length + ")");
        p.sendMessage("§7XP: §e" + xp
                + (idx + 1 < Job.THRESH.length ? " §8/ §e" + Job.THRESH[idx + 1] : " §6(MAX)"));
        p.sendMessage("§8§m------------------------------");
    }

    private void para(Player p, PlayerData d, String[] a) {
        if (a.length == 0) {
            p.sendMessage(P + "§7Bakiyen: §a$" + (int) d.money);
            return;
        }
        if (a.length == 3 && a[0].equalsIgnoreCase("gonder")) {
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t == null || !plugin.get(t).loggedIn) {
                p.sendMessage(P + "§cOyuncu bulunamadı.");
                return;
            }
            if (t.equals(p)) {
                p.sendMessage(P + "§cKendine para gönderemezsin.");
                return;
            }
            int miktar;
            try {
                miktar = Integer.parseInt(a[2]);
            } catch (NumberFormatException ex) {
                p.sendMessage(P + "§cGeçersiz miktar.");
                return;
            }
            if (miktar <= 0 || miktar > d.money) {
                p.sendMessage(P + "§cYetersiz bakiye veya geçersiz miktar.");
                return;
            }
            d.money -= miktar;
            plugin.get(t).money += miktar;
            p.sendMessage(P + "§a" + t.getName() + " kişisine $" + miktar + " gönderdin.");
            t.sendMessage(P + "§a" + p.getName() + " sana $" + miktar + " gönderdi.");
            return;
        }
        p.sendMessage(P + "§cKullanım: /para [gonder <oyuncu> <miktar>]");
    }

    // ------------------------------------------------------------ KAÇAKÇILIK

    private ItemStack paketItem(int n) {
        ItemStack it = new ItemStack(Material.SUGAR, n);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(PAKET_AD);
        m.setLore(List.of("§7Yasa dışı mal.", "§cPolis arayıp el koyabilir!"));
        it.setItemMeta(m);
        return it;
    }

    private boolean isPaket(ItemStack it) {
        return it != null && it.getType() == Material.SUGAR && it.hasItemMeta()
                && PAKET_AD.equals(it.getItemMeta().getDisplayName());
    }

    private int countPaket(Player p) {
        int c = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isPaket(it)) c += it.getAmount();
        }
        return c;
    }

    private int removePaket(Player p) {
        int c = 0;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isPaket(contents[i])) {
                c += contents[i].getAmount();
                p.getInventory().setItem(i, null);
            }
        }
        return c;
    }

    private boolean near(Player p, String key, double radius) {
        Location l = plugin.getConfig().getLocation("nokta." + key);
        return l != null && l.getWorld() != null && l.getWorld().equals(p.getWorld())
                && l.distance(p.getLocation()) <= radius;
    }

    private void paket(Player p, PlayerData d) {
        if (!Job.KACAKCI.name().equals(d.job)) {
            p.sendMessage(P + "§cBunu sadece Kaçakçılar yapabilir.");
            return;
        }
        if (!near(p, "tedarik", 5)) {
            p.sendMessage(P + "§cTedarik noktasında değilsin! (ya da ayarlanmamış)");
            return;
        }
        long bekleme = plugin.getConfig().getLong("paket.bekleme-saniye", 30) * 1000L;
        long kalan = d.lastPaket + bekleme - System.currentTimeMillis();
        if (kalan > 0) {
            p.sendMessage(P + "§cBiraz bekle: " + (kalan / 1000 + 1) + " sn");
            return;
        }
        d.lastPaket = System.currentTimeMillis();
        int adet = plugin.getConfig().getInt("paket.adet", 5);
        p.getInventory().addItem(paketItem(adet));
        p.sendMessage(P + "§5" + adet + " paket aldın. Polise yakalanma!");
    }

    private void sat(Player p, PlayerData d) {
        if (!near(p, "satis", 5)) {
            p.sendMessage(P + "§cSatış noktasında değilsin! (ya da ayarlanmamış)");
            return;
        }
        int adet = removePaket(p);
        if (adet == 0) {
            p.sendMessage(P + "§cÜstünde satılacak paket yok.");
            return;
        }
        double kazanc = adet * plugin.getConfig().getDouble("paket.satis-fiyati", 40);
        d.money += kazanc;
        p.sendMessage(P + "§a" + adet + " paket sattın: §e$" + (int) kazanc);
        if (Job.KACAKCI.name().equals(d.job)) {
            plugin.addXp(p, Job.KACAKCI, adet * plugin.getConfig().getInt("paket.satis-xp", 5));
        }
    }

    // ------------------------------------------------------------ POLİS

    private boolean isPolis(Player p, PlayerData d) {
        if (!Job.POLIS.name().equals(d.job)) {
            p.sendMessage(P + "§cBunu sadece Polisler yapabilir.");
            return false;
        }
        return true;
    }

    private Player target(Player p, String name, double radius) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || !plugin.get(t).loggedIn) {
            p.sendMessage(P + "§cOyuncu bulunamadı.");
            return null;
        }
        if (t.equals(p)) {
            p.sendMessage(P + "§cKendini seçemezsin.");
            return null;
        }
        if (radius > 0 && (!t.getWorld().equals(p.getWorld()) || t.getLocation().distance(p.getLocation()) > radius)) {
            p.sendMessage(P + "§cOyuncu yeterince yakın değil.");
            return null;
        }
        return t;
    }

    private void ara(Player p, PlayerData d, String[] a) {
        if (!isPolis(p, d)) return;
        if (a.length != 1) {
            p.sendMessage(P + "§cKullanım: /ara <oyuncu>");
            return;
        }
        Player t = target(p, a[0], 5);
        if (t == null) return;

        int adet = removePaket(t);
        if (adet == 0) {
            p.sendMessage(P + "§a" + t.getName() + " temiz çıktı.");
            t.sendMessage(P + "§7" + p.getName() + " (Polis) seni aradı, bir şey bulamadı.");
            return;
        }
        double odul = adet * plugin.getConfig().getDouble("polis.arama-odul", 100);
        d.money += odul;
        plugin.addXp(p, Job.POLIS, plugin.getConfig().getInt("polis.arama-xp", 10) * adet);
        p.sendMessage(P + "§a" + t.getName() + " üzerinde §c" + adet + " paket §abulundu ve el konuldu! Ödül: §e$" + (int) odul);
        t.sendMessage(P + "§c" + p.getName() + " (Polis) seni aradı ve " + adet + " paketine el koydu!");
        p.sendMessage(P + "§7Şimdi §6/tutukla " + t.getName() + " <dakika> §7ile tutuklayabilirsin.");
    }

    private void tutukla(Player p, PlayerData d, String[] a) {
        if (!isPolis(p, d)) return;
        if (a.length != 2) {
            p.sendMessage(P + "§cKullanım: /tutukla <oyuncu> <dakika>");
            return;
        }
        Location hapis = plugin.getConfig().getLocation("nokta.hapis");
        if (hapis == null) {
            p.sendMessage(P + "§cHapishane ayarlanmamış. (/rpadmin setnokta hapis)");
            return;
        }
        Player t = target(p, a[0], 5);
        if (t == null) return;
        int dk;
        try {
            dk = Integer.parseInt(a[1]);
        } catch (NumberFormatException ex) {
            p.sendMessage(P + "§cGeçersiz süre.");
            return;
        }
        if (dk < 1 || dk > 30) {
            p.sendMessage(P + "§cSüre 1 ile 30 dakika arasında olmalı.");
            return;
        }
        PlayerData td = plugin.get(t);
        td.jailUntil = System.currentTimeMillis() + dk * 60_000L;
        t.teleport(hapis);
        t.sendMessage(P + "§c" + p.getName() + " seni " + dk + " dakika tutukladı!");
        p.sendMessage(P + "§a" + t.getName() + " " + dk + " dakika tutuklandı.");
        plugin.addXp(p, Job.POLIS, plugin.getConfig().getInt("polis.tutuklama-xp", 20));
    }

    private void serbest(Player p, PlayerData d, String[] a) {
        if (!isPolis(p, d)) return;
        // Başkomiser (index 3) ve üstü
        if (Job.POLIS.rankIndex(d.xpOf(Job.POLIS)) < 3) {
            p.sendMessage(P + "§cBunun için en az Başkomiser olmalısın.");
            return;
        }
        if (a.length != 1) {
            p.sendMessage(P + "§cKullanım: /serbest <oyuncu>");
            return;
        }
        Player t = target(p, a[0], 0);
        if (t == null) return;
        PlayerData td = plugin.get(t);
        if (td.jailUntil <= 0) {
            p.sendMessage(P + "§cBu oyuncu tutuklu değil.");
            return;
        }
        td.jailUntil = 0;
        t.teleport(t.getWorld().getSpawnLocation());
        t.sendMessage(P + "§a" + p.getName() + " seni serbest bıraktı.");
        p.sendMessage(P + "§a" + t.getName() + " serbest bırakıldı.");
    }

    // ------------------------------------------------------------ MAFYA

    private void harac(Player p, PlayerData d, String[] a) {
        if (!Job.MAFYA.name().equals(d.job)) {
            p.sendMessage(P + "§cBunu sadece Mafya üyeleri yapabilir.");
            return;
        }
        if (a.length != 1) {
            p.sendMessage(P + "§cKullanım: /harac <oyuncu>");
            return;
        }
        long bekleme = plugin.getConfig().getLong("mafya.harac-bekleme-saniye", 120) * 1000L;
        long kalan = d.lastHarac + bekleme - System.currentTimeMillis();
        if (kalan > 0) {
            p.sendMessage(P + "§cBiraz bekle: " + (kalan / 1000 + 1) + " sn");
            return;
        }
        Player t = target(p, a[0], 5);
        if (t == null) return;
        PlayerData td = plugin.get(t);
        if (Job.MAFYA.name().equals(td.job)) {
            p.sendMessage(P + "§cAilene haraç kesemezsin!");
            return;
        }
        double yuzde = plugin.getConfig().getDouble("mafya.harac-yuzde", 10) / 100.0;
        double max = plugin.getConfig().getDouble("mafya.harac-max", 200);
        double miktar = Math.min(Math.floor(td.money * yuzde), max);
        if (miktar < 1) {
            p.sendMessage(P + "§7" + t.getName() + " kadar fakir birinden haraç alınmaz.");
            return;
        }
        d.lastHarac = System.currentTimeMillis();
        td.money -= miktar;
        d.money += miktar;
        plugin.addXp(p, Job.MAFYA, plugin.getConfig().getInt("mafya.harac-xp", 15));
        p.sendMessage(P + "§a" + t.getName() + " kişisinden §e$" + (int) miktar + " §aharaç aldın.");
        t.sendMessage(P + "§c" + p.getName() + " senden $" + (int) miktar + " haraç aldı! Polise haber ver.");
    }

    // ------------------------------------------------------------ ADMİN

    private void admin(Player p, String[] a) {
        if (!p.hasPermission("rp.admin")) {
            p.sendMessage(P + "§cYetkin yok.");
            return;
        }
        if (a.length == 0) {
            p.sendMessage(P + "§7/rpadmin setnokta <tedarik|satis|hapis>");
            p.sendMessage(P + "§7/rpadmin paraver <oyuncu> <miktar>");
            p.sendMessage(P + "§7/rpadmin xpver <oyuncu> <meslek> <miktar>");
            p.sendMessage(P + "§7/rpadmin sifirla <oyuncu>  §8(şifreyi sıfırlar)");
            return;
        }
        switch (a[0].toLowerCase()) {
            case "setnokta" -> {
                if (a.length != 2 || !List.of("tedarik", "satis", "hapis").contains(a[1].toLowerCase())) {
                    p.sendMessage(P + "§cKullanım: /rpadmin setnokta <tedarik|satis|hapis>");
                    return;
                }
                plugin.getConfig().set("nokta." + a[1].toLowerCase(), p.getLocation());
                plugin.saveConfig();
                p.sendMessage(P + "§a" + a[1] + " noktası bulunduğun yere ayarlandı.");
            }
            case "paraver" -> {
                if (a.length != 3) {
                    p.sendMessage(P + "§cKullanım: /rpadmin paraver <oyuncu> <miktar>");
                    return;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) {
                    p.sendMessage(P + "§cOyuncu çevrimdışı.");
                    return;
                }
                try {
                    int m = Integer.parseInt(a[2]);
                    plugin.get(t).money += m;
                    p.sendMessage(P + "§a" + t.getName() + " kişisine $" + m + " verildi.");
                } catch (NumberFormatException ex) {
                    p.sendMessage(P + "§cGeçersiz miktar.");
                }
            }
            case "xpver" -> {
                if (a.length != 4) {
                    p.sendMessage(P + "§cKullanım: /rpadmin xpver <oyuncu> <MAFYA|POLIS|KACAKCI|MADENCI> <miktar>");
                    return;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                Job j = Job.from(a[2].toUpperCase());
                if (t == null || j == null) {
                    p.sendMessage(P + "§cOyuncu veya meslek bulunamadı.");
                    return;
                }
                try {
                    plugin.addXp(t, j, Integer.parseInt(a[3]));
                    p.sendMessage(P + "§aXP verildi.");
                } catch (NumberFormatException ex) {
                    p.sendMessage(P + "§cGeçersiz miktar.");
                }
            }
            case "sifirla" -> {
                if (a.length != 2) {
                    p.sendMessage(P + "§cKullanım: /rpadmin sifirla <oyuncu>");
                    return;
                }
                @SuppressWarnings("deprecation")
                UUID id = Bukkit.getOfflinePlayer(a[1]).getUniqueId();
                plugin.wipePassword(id);
                p.sendMessage(P + "§a" + a[1] + " şifresi sıfırlandı. Girişte tekrar /register yapacak.");
            }
            default -> p.sendMessage(P + "§cBilinmeyen alt komut.");
        }
    }
}
