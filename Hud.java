package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sağdaki bilgi paneli + TAB listesinde meslekler (en iyi meslekten en kötüye). */
public class Hud {

    private static final int LINES = 15;
    private static final ZoneId TR = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter SAAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TARIH = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String[] BASLIK = {
            "§6§l✦ ŞEHİR RP ✦", "§e§l✦ ŞEHİR RP ✦", "§f§l✦ ŞEHİR RP ✦", "§e§l✦ ŞEHİR RP ✦"
    };
    private int frame = 0;
    private final Map<UUID, Integer> titleIdx = new HashMap<>();

    private static final Statistic PLAY_STAT = findPlayStat();

    private static Statistic findPlayStat() {
        for (String n : new String[]{"PLAY_ONE_MINUTE", "PLAY_TIME"}) {
            try {
                return Statistic.valueOf(n);
            } catch (IllegalArgumentException ignored) {
                // bu surumde yok, siradakini dene
            }
        }
        return null;
    }

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
        frame++;
        boards.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        titleIdx.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
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

        ZonedDateTime now = ZonedDateTime.now(TR);
        String[] lines = {
                "§8§m                    ",
                "§e✦ Saat §8» §f" + now.format(SAAT),
                "§e✦ Tarih §8» §f" + now.format(TARIH),
                " ",
                "§6§l✦ HESAP",
                "§7 Oyuncu §8» §f" + p.getName(),
                "§7 Para §8» §a$" + String.format(Locale.US, "%,d", (long) d.money),
                "§7 Banka §8» §b$" + String.format(Locale.US, "%,d", (long) d.bank),
                "§6§l✦ MESLEK",
                "§7 Meslek §8» " + meslek,
                "§7 Rütbe §8» " + rutbe,
                "§7 XP §8» " + xp,
                "§d§l✦ SUNUCU",
                "§7 Oynama §8» §f" + playtime(p) + " §8| §7Online §8» §f" + Bukkit.getOnlinePlayers().size(),
                "§7 Durum §8» " + durum
        };
        Objective obj = sb.getObjective("rp");
        if (obj != null) {
            int idx = (frame / 2) % BASLIK.length;
            Integer last = titleIdx.get(p.getUniqueId());
            if (last == null || last != idx) {
                obj.setDisplayName(BASLIK[idx]);
                titleIdx.put(p.getUniqueId(), idx);
            }
        }
        for (int i = 0; i < LINES; i++) {
            sb.getTeam("l" + i).setPrefix(lines[i]);
        }

        updateTab(sb);
    }

    private static String playtime(Player p) {
        if (PLAY_STAT == null) return "-";
        long sec = p.getStatistic(PLAY_STAT) / 20L;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long sn = sec % 60;
        if (h > 0) return h + "sa " + m + "dk";
        return m + "dk " + sn + "sn";
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
