package com.rp;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class GameListener implements Listener {

    private final RpPlugin plugin;

    public GameListener(RpPlugin plugin) {
        this.plugin = plugin;
    }

    /** Madenci: cevher kırınca para + XP */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.get(p);
        if (!d.loggedIn) return;
        if (!Job.MADENCI.name().equals(d.job)) return;

        Material m = e.getBlock().getType();
        if (!m.name().endsWith("_ORE") && m != Material.ANCIENT_DEBRIS) return;

        double para = plugin.getConfig().getDouble("madenci.cevher-para", 10);
        int xp = plugin.getConfig().getInt("madenci.cevher-xp", 2);
        plugin.addMoney(p, para);
        plugin.addXp(p, Job.MADENCI, xp);
        p.sendActionBar(net.kyori.adventure.text.Component.text("§a+$" + (int) para + " §7| §e+" + xp + " XP"));
    }

    /** Sohbet: [Meslek | Rütbe] İsim: mesaj */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.get(p);
        Job j = Job.from(d.job);

        String prefix;
        if (j == null) {
            prefix = "§7[Vatandaş]";
        } else {
            prefix = j.color + "[" + j.display + " | " + j.rankName(d.xpOf(j)) + "]";
        }
        // %1$s = oyuncu adı, %2$s = mesaj
        e.setFormat(prefix + " §f%1$s§7: §f%2$s");
    }
}
