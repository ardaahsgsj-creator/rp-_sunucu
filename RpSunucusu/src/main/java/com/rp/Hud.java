package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sağdaki bilgi paneli + TAB listesinde meslekler (en iyi meslekten en kötüye). */
public class Hud {

    private static final int LINES = 10;

    private final RpPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public Hud(RpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 10L);
    }

    /** TAB sıralaması: küçük sayı = listenin üstünde. İstersen sırayı buradan değiştir. */
    private static int jobOrder(Job j) {
        if (j == null) return 9;
        switch (j) {
            case POLIS: return 1;
            case DOKTOR: return 2;
            case AVUKAT: return 3;
            case MAFYA: return 4;
            case EMLAKCI: return 5;
            case ESNAF: return 6;
            case KACAKCI: return 7;
            default: return 8;
        }
    }

    private static String entry(int i) {
        return "§" + Integer.toHexString(i) + "§r";
    }

    private Scoreboard create() {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("rp", "dummy", "§6§l✦ ŞEHİR RP ✦");
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < LINES; i++) {
            Team t = sb.registerNewTeam("l" + i);
            t.addEntry(entry(i));
            o.getScore(entry(i)).setScore(LINES - i);
        }
        return sb;
    }

    private void updateAll() {
        boards.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.get(p);
            if (!d.loggedIn) {
                if (boards.remove(p.getUniqueId()) != null) {
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                continue;
            }
            update(p, d);
        }
    }

    private void update(Player p, PlayerData d) {
        Scoreboard sb = boards.computeIfAbsent(p.getUniqueId(), k -> create());
        if (p.getScoreboard() != sb) p.setScoreboard(sb);

        Job j = Job.from(d.job);
        String meslek;
        String rutbe;
        String xp;
        if (j == null) {
            meslek = "§cYok";
            rutbe = "§7-";
            xp = "§7-";
        } else {
            int x = d.xpOf(j);
            int idx = j.rankIndex(x);
            meslek = j.color + j.display;
            rutbe = j.color + j.rankName(x);
            xp = idx + 1 < Job.THRESH.length
                    ? "§e" + x + "§7/§e" + Job.THRESH[idx + 1]
                    : "§6" + x + " §7(MAX)";
        }

        String durum;
        long kalan = d.jailUntil - System.currentTimeMillis();
        if (kalan > 0) {
            long sn = kalan / 1000;
            durum = "§cHapiste §f" + (sn / 60) + ":" + String.format("%02d", sn % 60);
        } else {
            durum = "§aÖzgür";
        }

        String[] lines = {
                "§8§m                    ",
                "§7Oyuncu: §f" + p.getName(),
                "§7Para: §a$" + String.format(Locale.US, "%,d", (long) d.money),
                "§7Banka: §b$" + String.format(Locale.US, "%,d", (long) d.bank),
                "§7Meslek: " + meslek,
                "§7Rütbe: " + rutbe,
                "§7XP: " + xp,
                "§7Durum: " + durum,
                " ",
                "§8§m                    §r"
        };
        for (int i = 0; i < LINES; i++) {
            sb.getTeam("l" + i).setPrefix(lines[i]);
        }

        updateTab(sb);
    }

    /** Her oyuncuya takım kurar: ekle ismi önünde meslek yazar, takım adı sıralamayı belirler. */
    private void updateTab(Scoreboard sb) {
        Set<String> keep = new HashSet<>();
        for (Player q : Bukkit.getOnlinePlayers()) {
            PlayerData qd = plugin.get(q);
            Job qj = qd.loggedIn ? Job.from(qd.job) : null;
            int rank = qj == null ? 0 : qj.rankIndex(qd.xpOf(qj));
            String key = jobOrder(qj) + "" + (4 - rank) + q.getUniqueId().toString().substring(0, 6);
            keep.add(key);

            Team t = sb.getTeam(key);
            if (t == null) t = sb.registerNewTeam(key);
            String prefix = qj == null ? "§7[Vatandaş] §r" : qj.color + "[" + qj.display + "] §r";
            t.setPrefix(prefix);
            if (!t.hasEntry(q.getName())) t.addEntry(q.getName());
        }
        for (Team t : new ArrayList<>(sb.getTeams())) {
            String n = t.getName();
            if (!n.isEmpty() && Character.isDigit(n.charAt(0)) && !keep.contains(n)) {
                t.unregister();
            }
        }
    }
}
