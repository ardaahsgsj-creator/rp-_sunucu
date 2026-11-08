package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Meslek ekipmanları. Mesleğe göre silah/alet verir, rütbe yükselince
 * daha iyisiyle değiştirir, meslek değişince eskisini siler.
 */
public class Kit implements Listener {

    private static final String PREFIX = "§8[RP:";

    private final RpPlugin plugin;

    public Kit(RpPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 40L, 20L);
    }

    // ------------------------------------------------------------ KONTROL

    private void check() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.get(p);
            if (!d.loggedIn) continue;
            Job j = Job.from(d.job);
            if (j == null) continue;
            if (!hasKit(p, j, tier(j, d.xpOf(j)))) {
                clear(p);
                if (give(p, j, d)) {
                    p.sendMessage(RpPlugin.PREFIX + "§aMeslek ekipmanların envanterine eklendi.");
                }
            }
        }
    }

    private static int tier(Job j, int xp) {
        int r = j.rankIndex(xp);
        if (r <= 1) return 0;
        if (r <= 3) return 1;
        return 2;
    }

    private static String tag(Job j, int tier) {
        return PREFIX + j.name() + ":" + tier + ":v2]";
    }

    // ------------------------------------------------------------ EŞYA

    private static ItemStack make(Material m, int amt, String name, String tag, String... lines) {
        ItemStack it = new ItemStack(m, amt);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        for (String s : lines) lore.add(s);
        lore.add(tag);
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isRp(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        ItemMeta meta = it.getItemMeta();
        if (!meta.hasLore()) return false;
        for (String s : meta.getLore()) {
            if (s.startsWith(PREFIX)) return true;
        }
        return false;
    }

    private static boolean hasTag(ItemStack it, String tag) {
        if (!isRp(it)) return false;
        return it.getItemMeta().getLore().contains(tag);
    }

    private boolean hasKit(Player p, Job j, int tier) {
        String tag = tag(j, tier);
        for (ItemStack it : p.getInventory().getContents()) {
            if (hasTag(it, tag)) return true;
        }
        return false;
    }

    private void clear(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isRp(contents[i])) inv.setItem(i, null);
        }
    }

    private boolean give(Player p, Job j, PlayerData d) {
        int tier = tier(j, d.xpOf(j));
        String tag = tag(j, tier);
        List<ItemStack> items = new ArrayList<>();

        // Silahlar bu eklentiden gelmez, ayri bir silah eklentisiyle verilir.
        switch (j) {
            case POLIS: {
                items.add(make(Material.SHIELD, 1, "§9Polis Kalkanı", tag));
                items.add(make(Material.LEAD, 1, "§9Kelepçe", tag, "§7/ara ve /tutukla ile kullan."));
                break;
            }
            case MAFYA: {
                break;
            }
            case KACAKCI: {
                items.add(make(Material.ENDER_PEARL, 2, "§5Kaçış İncisi", tag, "§7Polisten kaçmak için."));
                break;
            }
            case MADENCI: {
                Material[] pick = {Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE};
                items.add(make(pick[tier], 1, "§7Maden Kazması", tag, "§7Cevher kır, para kazan."));
                items.add(make(Material.TORCH, 32, "§7Maden Feneri", tag));
                items.add(make(Material.BREAD, 8, "§7Maden Yemeği", tag));
                break;
            }
            default: {
                break;
            }
        }

        for (ItemStack it : items) {
            HashMap<Integer, ItemStack> left = p.getInventory().addItem(it);
            for (ItemStack l : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), l);
            }
        }
        return !items.isEmpty();
    }

    // ------------------------------------------------------------ OLAYLAR

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isRp(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(RpPlugin.PREFIX + "§cMeslek eşyalarını yere atamazsın.");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(Kit::isRp);
    }
}
