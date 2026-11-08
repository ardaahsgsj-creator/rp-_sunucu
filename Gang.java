package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Çete sistemi (Mafya). /cete kur|davet|kabul|ayril|at|yatir|cek|bilgi|dagit  ve /cs (çete sohbeti)
 */
public class Gang implements Listener {

    private static final String PFX = RpPlugin.PREFIX;
    private static final int KURMA_UCRETI = 1000;

    private static class G {
        String name;
        UUID leader;
        Set<UUID> members = new LinkedHashSet<>();
        double bank;
    }

    private final RpPlugin plugin;
    private final File file;
    private final Map<String, G> gangs = new HashMap<>();      // küçük harf isim -> çete
    private final Map<UUID, G> byPlayer = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>(); // davetli -> çete anahtarı

    public Gang(RpPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "cetes.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------ KAYIT

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("gangs");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(key);
            if (c == null) continue;
            try {
                G g = new G();
                g.name = c.getString("name", key);
                g.leader = UUID.fromString(c.getString("leader", ""));
                g.bank = c.getDouble("bank", 0);
                for (String m : c.getStringList("members")) g.members.add(UUID.fromString(m));
                gangs.put(key, g);
                for (UUID u : g.members) byPlayer.put(u, g);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("cetes.yml okunamadı: " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, G> en : gangs.entrySet()) {
            G g = en.getValue();
            String p = "gangs." + en.getKey() + ".";
            y.set(p + "name", g.name);
            y.set(p + "leader", g.leader.toString());
            y.set(p + "bank", g.bank);
            List<String> ms = new ArrayList<>();
            for (UUID u : g.members) ms.add(u.toString());
            y.set(p + "members", ms);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("cetes.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    private static String name(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return op.getName() == null ? "?" : op.getName();
    }

    private void tell(G g, String msg) {
        for (UUID u : g.members) {
            Player m = Bukkit.getPlayer(u);
            if (m != null) m.sendMessage(msg);
        }
    }

    // ------------------------------------------------------------ KOMUT

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage().trim();
        String[] parts = raw.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        Player p = e.getPlayer();

        if (cmd.equals("/cete")) {
            e.setCancelled(true);
            if (plugin.get(p).loggedIn) cete(p, Arrays.copyOfRange(parts, 1, parts.length));
        } else if (cmd.equals("/cs")) {
            e.setCancelled(true);
            if (!plugin.get(p).loggedIn) return;
            String rest = parts.length > 1 ? raw.substring(raw.indexOf(' ') + 1).trim() : "";
            G g = byPlayer.get(p.getUniqueId());
            if (g == null) {
                p.sendMessage(PFX + "§cBir çetede değilsin.");
            } else if (rest.isEmpty()) {
                p.sendMessage(PFX + "§cKullanım: /cs <mesaj>");
            } else {
                tell(g, "§4§l[ÇETE] §c" + p.getName() + "§7: §f" + rest);
            }
        }
    }

    private void cete(Player p, String[] a) {
        if (a.length == 0) {
            p.sendMessage(PFX + "§7/cete kur <isim>  davet <oyuncu>  kabul  ayril  at <oyuncu>");
            p.sendMessage(PFX + "§7/cete yatir <miktar>  cek <miktar>  bilgi  dagit  §8| §7/cs <mesaj>");
            return;
        }
        UUID id = p.getUniqueId();
        PlayerData d = plugin.get(p);
        G g = byPlayer.get(id);

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "kur": {
                if (!Job.MAFYA.name().equals(d.job)) {
                    p.sendMessage(PFX + "§cÇeteyi sadece Mafya üyeleri kurabilir.");
                    return;
                }
                if (Job.MAFYA.rankIndex(d.xpOf(Job.MAFYA)) < 2) {
                    p.sendMessage(PFX + "§cÇete kurmak için en az §eKaptan §crütbesi gerekir.");
                    return;
                }
                if (g != null) {
                    p.sendMessage(PFX + "§cZaten bir çetedesin.");
                    return;
                }
                if (a.length != 2 || !a[1].matches("[A-Za-z0-9_]{3,12}")) {
                    p.sendMessage(PFX + "§cKullanım: /cete kur <isim>  (3-12 harf/rakam)");
                    return;
                }
                String key = a[1].toLowerCase(Locale.ROOT);
                if (gangs.containsKey(key)) {
                    p.sendMessage(PFX + "§cBu isimde bir çete zaten var.");
                    return;
                }
                if (d.money < KURMA_UCRETI) {
                    p.sendMessage(PFX + "§cÇete kurmak §e$" + KURMA_UCRETI + " §cgerektirir.");
                    return;
                }
                d.money -= KURMA_UCRETI;
                G ng = new G();
                ng.name = a[1];
                ng.leader = id;
                ng.members.add(id);
                gangs.put(key, ng);
                byPlayer.put(id, ng);
                save();
                Bukkit.broadcastMessage(PFX + "§4§l" + ng.name + " §cçetesi kuruldu! Lider: §f" + p.getName());
                break;
            }
            case "davet": {
                if (g == null || !g.leader.equals(id)) {
                    p.sendMessage(PFX + "§cSadece çete lideri davet edebilir.");
                    return;
                }
                if (a.length != 2) {
                    p.sendMessage(PFX + "§cKullanım: /cete davet <oyuncu>");
                    return;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null || !plugin.get(t).loggedIn) {
                    p.sendMessage(PFX + "§cOyuncu bulunamadı.");
                    return;
                }
                if (!Job.MAFYA.name().equals(plugin.get(t).job)) {
                    p.sendMessage(PFX + "§cSadece Mafya üyeleri çeteye katılabilir.");
                    return;
                }
                if (byPlayer.containsKey(t.getUniqueId())) {
                    p.sendMessage(PFX + "§cOyuncu zaten bir çetede.");
                    return;
                }
                final String key = g.name.toLowerCase(Locale.ROOT);
                final UUID tid = t.getUniqueId();
                invites.put(tid, key);
                Bukkit.getScheduler().runTaskLater(plugin, () -> invites.remove(tid, key), 1200L);
                p.sendMessage(PFX + "§a" + t.getName() + " kişisine çete daveti gönderildi.");
                t.sendMessage(PFX + "§c" + p.getName() + " seni §4" + g.name + " §cçetesine davet etti. §a/cete kabul §7(60 sn)");
                break;
            }
            case "kabul": {
                String key = invites.remove(id);
                G target = key == null ? null : gangs.get(key);
                if (target == null) {
                    p.sendMessage(PFX + "§cSana verilmiş bir davet yok.");
                    return;
                }
                if (g != null) {
                    p.sendMessage(PFX + "§cZaten bir çetedesin.");
                    return;
                }
                target.members.add(id);
                byPlayer.put(id, target);
                save();
                tell(target, PFX + "§c" + p.getName() + " çeteye katıldı!");
                break;
            }
            case "ayril": {
                if (g == null) {
                    p.sendMessage(PFX + "§cBir çetede değilsin.");
                    return;
                }
                if (g.leader.equals(id)) {
                    p.sendMessage(PFX + "§cLider ayrılamaz. Çeteyi dağıtmak için §f/cete dagit");
                    return;
                }
                g.members.remove(id);
                byPlayer.remove(id);
                save();
                p.sendMessage(PFX + "§7Çeteden ayrıldın.");
                tell(g, PFX + "§c" + p.getName() + " çeteden ayrıldı.");
                break;
            }
            case "at": {
                if (g == null || !g.leader.equals(id)) {
                    p.sendMessage(PFX + "§cSadece çete lideri üye atabilir.");
                    return;
                }
                if (a.length != 2) {
                    p.sendMessage(PFX + "§cKullanım: /cete at <oyuncu>");
                    return;
                }
                UUID hedef = null;
                for (UUID u : g.members) {
                    if (name(u).equalsIgnoreCase(a[1])) hedef = u;
                }
                if (hedef == null || hedef.equals(id)) {
                    p.sendMessage(PFX + "§cÇetede böyle bir üye yok.");
                    return;
                }
                g.members.remove(hedef);
                byPlayer.remove(hedef);
                save();
                p.sendMessage(PFX + "§a" + a[1] + " çeteden atıldı.");
                Player at = Bukkit.getPlayer(hedef);
                if (at != null) at.sendMessage(PFX + "§cÇeteden atıldın.");
                break;
            }
            case "yatir":
            case "cek": {
                if (g == null) {
                    p.sendMessage(PFX + "§cBir çetede değilsin.");
                    return;
                }
                boolean yatir = a[0].equalsIgnoreCase("yatir");
                if (!yatir && !g.leader.equals(id)) {
                    p.sendMessage(PFX + "§cKasadan sadece lider para çekebilir.");
                    return;
                }
                if (a.length != 2) {
                    p.sendMessage(PFX + "§cKullanım: /cete " + a[0] + " <miktar>");
                    return;
                }
                int miktar;
                try {
                    miktar = Integer.parseInt(a[1]);
                } catch (NumberFormatException ex) {
                    p.sendMessage(PFX + "§cGeçersiz miktar.");
                    return;
                }
                if (miktar <= 0) {
                    p.sendMessage(PFX + "§cGeçersiz miktar.");
                    return;
                }
                if (yatir) {
                    if (miktar > d.money) {
                        p.sendMessage(PFX + "§cYetersiz bakiye.");
                        return;
                    }
                    d.money -= miktar;
                    g.bank += miktar;
                    tell(g, PFX + "§c" + p.getName() + " kasaya §e$" + miktar + " §cyatırdı. Kasa: §e$" + (int) g.bank);
                } else {
                    if (miktar > g.bank) {
                        p.sendMessage(PFX + "§cKasada bu kadar para yok.");
                        return;
                    }
                    g.bank -= miktar;
                    d.money += miktar;
                    tell(g, PFX + "§cLider kasadan §e$" + miktar + " §cçekti. Kasa: §e$" + (int) g.bank);
                }
                save();
                break;
            }
            case "bilgi": {
                if (g == null) {
                    p.sendMessage(PFX + "§cBir çetede değilsin.");
                    return;
                }
                List<String> names = new ArrayList<>();
                for (UUID u : g.members) names.add((u.equals(g.leader) ? "§6★" : "§f") + name(u));
                p.sendMessage("§8§m                    ");
                p.sendMessage("§4§l" + g.name + " §7çetesi");
                p.sendMessage("§7Lider: §6" + name(g.leader) + " §8| §7Kasa: §e$" + (int) g.bank);
                p.sendMessage("§7Üyeler (" + g.members.size() + "): " + String.join("§7, ", names));
                p.sendMessage("§8§m                    ");
                break;
            }
            case "dagit": {
                if (g == null || !g.leader.equals(id)) {
                    p.sendMessage(PFX + "§cSadece çete lideri çeteyi dağıtabilir.");
                    return;
                }
                d.money += g.bank;
                for (UUID u : new ArrayList<>(g.members)) byPlayer.remove(u);
                gangs.remove(g.name.toLowerCase(Locale.ROOT));
                save();
                Bukkit.broadcastMessage(PFX + "§4§l" + g.name + " §cçetesi dağıldı.");
                p.sendMessage(PFX + "§7Kasadaki para sana verildi: §e$" + (int) g.bank);
                break;
            }
            default:
                p.sendMessage(PFX + "§cBilinmeyen alt komut. /cete yaz.");
                break;
        }
    }
}
