package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sivil meslekler: Esnaf, Doktor, Avukat, Emlakçı.
 * Komutlar: /iyilestir /savun /teklif /kabul /reddet
 */
public class Roles implements Listener {

    private static final String PFX = RpPlugin.PREFIX;

    private static final double IYILESTIR_UCRET = 100;
    private static final long IYILESTIR_BEKLEME = 10_000L;
    private static final double SAVUN_UCRET = 150;
    private static final long SAVUN_BEKLEME = 5 * 60_000L;
    private static final long TEKLIF_SURE = 30_000L;

    private static class Offer {
        final UUID seller;
        final ItemStack item;
        final int price;
        final boolean tapu;
        final long expires;

        Offer(UUID seller, ItemStack item, int price, boolean tapu, long expires) {
            this.seller = seller;
            this.item = item;
            this.price = price;
            this.tapu = tapu;
            this.expires = expires;
        }
    }

    private final RpPlugin plugin;
    private final Map<UUID, Offer> offers = new HashMap<>();      // alıcı -> teklif
    private final Map<UUID, Long> healCooldown = new HashMap<>(); // doktor -> son tedavi
    private final Map<UUID, Long> defendCooldown = new HashMap<>(); // hedef -> son savunma

    public Roles(RpPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------ KOMUT YAKALAMA

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String[] parts = e.getMessage().trim().split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        Player p = e.getPlayer();

        switch (cmd) {
            case "/iyilestir":
                e.setCancelled(true);
                if (plugin.get(p).loggedIn) iyilestir(p, args);
                break;
            case "/savun":
                e.setCancelled(true);
                if (plugin.get(p).loggedIn) savun(p, args);
                break;
            case "/teklif":
                e.setCancelled(true);
                if (plugin.get(p).loggedIn) teklif(p, args);
                break;
            case "/kabul":
                e.setCancelled(true);
                if (plugin.get(p).loggedIn) kabul(p);
                break;
            case "/reddet":
                e.setCancelled(true);
                if (plugin.get(p).loggedIn) reddet(p);
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        offers.remove(id);
        healCooldown.remove(id);
        defendCooldown.remove(id);
    }

    // ------------------------------------------------------------ YARDIMCI

    private Player target(Player p, String name, double radius) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || !plugin.get(t).loggedIn) {
            p.sendMessage(PFX + "§cOyuncu bulunamadı.");
            return null;
        }
        if (t.equals(p)) {
            p.sendMessage(PFX + "§cKendini seçemezsin.");
            return null;
        }
        if (radius > 0 && (!t.getWorld().equals(p.getWorld())
                || t.getLocation().distance(p.getLocation()) > radius)) {
            p.sendMessage(PFX + "§cOyuncu yeterince yakın değil.");
            return null;
        }
        return t;
    }

    private boolean isJob(Player p, Job j, String hata) {
        if (!j.name().equals(plugin.get(p).job)) {
            p.sendMessage(PFX + "§c" + hata);
            return false;
        }
        return true;
    }

    private static String itemName(ItemStack it) {
        String name;
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            name = it.getItemMeta().getDisplayName();
        } else {
            name = it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        return name + " §7x" + it.getAmount();
    }

    // ------------------------------------------------------------ DOKTOR

    private void iyilestir(Player p, String[] args) {
        if (!isJob(p, Job.DOKTOR, "Bunu sadece Doktorlar yapabilir.")) return;
        if (args.length != 1) {
            p.sendMessage(PFX + "§cKullanım: /iyilestir <oyuncu>");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = healCooldown.get(p.getUniqueId());
        if (last != null && now - last < IYILESTIR_BEKLEME) {
            p.sendMessage(PFX + "§cBiraz bekle: " + ((IYILESTIR_BEKLEME - (now - last)) / 1000 + 1) + " sn");
            return;
        }
        Player t = target(p, args[0], 5);
        if (t == null) return;
        if (t.getHealth() >= 19.5 && t.getFoodLevel() >= 20) {
            p.sendMessage(PFX + "§7" + t.getName() + " zaten sağlıklı.");
            return;
        }

        t.setHealth(20.0);
        t.setFoodLevel(20);
        t.setFireTicks(0);
        healCooldown.put(p.getUniqueId(), now);

        PlayerData d = plugin.get(p);
        PlayerData td = plugin.get(t);
        double ucret = Math.min(IYILESTIR_UCRET, td.money);
        td.money -= ucret;
        d.money += ucret;

        p.sendMessage(PFX + "§a" + t.getName() + " tedavi edildi. Ücret: §e$" + (int) ucret);
        t.sendMessage(PFX + "§b" + p.getName() + " (Doktor) seni iyileştirdi. §7(-$" + (int) ucret + ")");
        plugin.addXp(p, Job.DOKTOR, 10);
    }

    // ------------------------------------------------------------ AVUKAT

    private void savun(Player p, String[] args) {
        if (!isJob(p, Job.AVUKAT, "Bunu sadece Avukatlar yapabilir.")) return;
        if (args.length != 1) {
            p.sendMessage(PFX + "§cKullanım: /savun <oyuncu>");
            return;
        }
        Player t = target(p, args[0], 0);
        if (t == null) return;

        PlayerData td = plugin.get(t);
        long now = System.currentTimeMillis();
        long kalan = td.jailUntil - now;
        if (kalan <= 0) {
            p.sendMessage(PFX + "§c" + t.getName() + " tutuklu değil.");
            return;
        }
        if (kalan < 30_000L) {
            p.sendMessage(PFX + "§7" + t.getName() + " zaten birazdan çıkacak.");
            return;
        }
        Long last = defendCooldown.get(t.getUniqueId());
        if (last != null && now - last < SAVUN_BEKLEME) {
            p.sendMessage(PFX + "§cBu müvekkil için yakın zamanda savunma yapıldı.");
            return;
        }

        PlayerData d = plugin.get(p);
        int rank = Job.AVUKAT.rankIndex(d.xpOf(Job.AVUKAT));
        double oran = Math.min(0.7, 0.3 + 0.1 * rank);
        long azalt = (long) (kalan * oran);
        td.jailUntil -= azalt;
        defendCooldown.put(t.getUniqueId(), now);

        double ucret = Math.min(SAVUN_UCRET, td.money);
        td.money -= ucret;
        d.money += ucret;

        p.sendMessage(PFX + "§a" + t.getName() + " için savunma yaptın. Ceza %" + (int) (oran * 100)
                + " kısaldı. Ücret: §e$" + (int) ucret);
        t.sendMessage(PFX + "§6" + p.getName() + " (Avukat) cezanı kısalttı. §7(-$" + (int) ucret + ")");
        plugin.addXp(p, Job.AVUKAT, 20);
    }

    // ------------------------------------------------------------ ESNAF / EMLAKÇI

    private ItemStack tapu(Player p, int price) {
        Location l = p.getLocation();
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§e§lTapu Senedi");
        List<String> lore = new ArrayList<>();
        lore.add("§7Satıcı: §f" + p.getName());
        lore.add("§7Konum: §f" + l.getWorld().getName() + " X" + l.getBlockX()
                + " Y" + l.getBlockY() + " Z" + l.getBlockZ());
        lore.add("§7Bedel: §a$" + price);
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private void teklif(Player p, String[] args) {
        PlayerData d = plugin.get(p);
        boolean esnaf = Job.ESNAF.name().equals(d.job);
        boolean emlak = Job.EMLAKCI.name().equals(d.job);
        if (!esnaf && !emlak) {
            p.sendMessage(PFX + "§cSadece Esnaf ve Emlakçılar teklif verebilir.");
            return;
        }
        if (args.length != 2) {
            p.sendMessage(PFX + "§cKullanım: /teklif <oyuncu> <fiyat>");
            return;
        }
        int price;
        try {
            price = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            p.sendMessage(PFX + "§cGeçersiz fiyat.");
            return;
        }
        if (price < 1 || price > 1_000_000) {
            p.sendMessage(PFX + "§cFiyat 1 ile 1.000.000 arasında olmalı.");
            return;
        }
        Player t = target(p, args[0], 6);
        if (t == null) return;

        ItemStack item;
        if (esnaf) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                p.sendMessage(PFX + "§cElinde satılacak bir eşya yok.");
                return;
            }
            if (Kit.isRp(hand)) {
                p.sendMessage(PFX + "§cMeslek eşyaları satılamaz.");
                return;
            }
            item = hand.clone();
        } else {
            item = tapu(p, price);
        }

        offers.put(t.getUniqueId(), new Offer(p.getUniqueId(), item, price, emlak,
                System.currentTimeMillis() + TEKLIF_SURE));
        p.sendMessage(PFX + "§a" + t.getName() + " kişisine teklif gönderildi: §f" + itemName(item)
                + " §7- §a$" + price);
        t.sendMessage(PFX + "§e" + p.getName() + " sana teklif veriyor: §f" + itemName(item)
                + " §7- §a$" + price);
        t.sendMessage(PFX + "§a/kabul §7ya da §c/reddet §7(30 saniye)");
    }

    private void kabul(Player p) {
        Offer o = offers.remove(p.getUniqueId());
        if (o == null) {
            p.sendMessage(PFX + "§cSana verilmiş bir teklif yok.");
            return;
        }
        if (System.currentTimeMillis() > o.expires) {
            p.sendMessage(PFX + "§cTeklifin süresi dolmuş.");
            return;
        }
        Player seller = Bukkit.getPlayer(o.seller);
        if (seller == null || !plugin.get(seller).loggedIn) {
            p.sendMessage(PFX + "§cSatıcı çevrimdışı.");
            return;
        }
        PlayerData bd = plugin.get(p);
        PlayerData sd = plugin.get(seller);
        if (bd.money < o.price) {
            p.sendMessage(PFX + "§cYetersiz bakiye! Gereken: §e$" + o.price);
            offers.put(p.getUniqueId(), o);
            return;
        }

        if (!o.tapu) {
            // Esnaf: eşya hâlâ satıcıda olmalı
            if (!seller.getInventory().containsAtLeast(o.item, o.item.getAmount())) {
                p.sendMessage(PFX + "§cSatıcının üzerinde artık bu eşya yok.");
                seller.sendMessage(PFX + "§cTeklifteki eşya envanterinde bulunamadı.");
                return;
            }
            Map<Integer, ItemStack> kalan = seller.getInventory().removeItem(o.item.clone());
            if (!kalan.isEmpty()) {
                p.sendMessage(PFX + "§cEşya satıcıdan alınamadı.");
                return;
            }
        }

        bd.money -= o.price;
        sd.money += o.price;

        Map<Integer, ItemStack> left = p.getInventory().addItem(o.item.clone());
        for (ItemStack l : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), l);
        }

        p.sendMessage(PFX + "§aSatın aldın: §f" + itemName(o.item) + " §7(-$" + o.price + ")");
        seller.sendMessage(PFX + "§a" + p.getName() + " teklifini kabul etti! §e+$" + o.price);

        int xp = Math.max(5, Math.min(50, o.price / 20));
        plugin.addXp(seller, o.tapu ? Job.EMLAKCI : Job.ESNAF, xp);
    }

    private void reddet(Player p) {
        Offer o = offers.remove(p.getUniqueId());
        if (o == null) {
            p.sendMessage(PFX + "§cSana verilmiş bir teklif yok.");
            return;
        }
        p.sendMessage(PFX + "§7Teklifi reddettin.");
        Player seller = Bukkit.getPlayer(o.seller);
        if (seller != null) {
            seller.sendMessage(PFX + "§c" + p.getName() + " teklifini reddetti.");
        }
    }
}
