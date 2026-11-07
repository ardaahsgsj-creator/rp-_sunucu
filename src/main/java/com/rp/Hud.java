package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Ekranın sağında görünen bilgi paneli (HUD). */
public class Hud {

    private static final int LINES = 9;

    private final RpPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public Hud(RpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 10L);
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
    }
}
