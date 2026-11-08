package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /market : paranla eşya satın aldığın menü. Fiyatlar aşağıdaki listede. */
public class Market implements Listener {

    private static class Entry {
        final Material mat;
        final int amount;
        final int price;
        final String name;

        Entry(Material mat, int amount, int price, String name) {
            this.mat = mat;
            this.amount = amount;
            this.price = price;
            this.name = name;
        }
    }

    private static class MarketHolder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();

    private static void add(int slot, Material mat, int amount, int price, String name) {
        ENTRIES.put(slot, new Entry(mat, amount, price, name));
    }

    static {
        // Kılıçlar (satır 2)
        add(10, Material.WOODEN_SWORD, 1, 50, "§fTahta Kılıç");
        add(11, Material.STONE_SWORD, 1, 100, "§7Taş Kılıç");
        add(12, Material.IRON_SWORD, 1, 300, "§fDemir Kılıç");
        add(13, Material.DIAMOND_SWORD, 1, 1000, "§bElmas Kılıç");
        add(14, Material.NETHERITE_SWORD, 1, 3000, "§8Netherite Kılıç");

        // Yiyecek (satır 3)
        add(19, Material.BREAD, 8, 20, "§6Ekmek §7x8");
        add(20, Material.COOKED_BEEF, 8, 60, "§cPişmiş Et §7x8");
        add(21, Material.GOLDEN_APPLE, 1, 200, "§eAltın Elma");

        // Zırh (satır 4)
        add(28, Material.IRON_HELMET, 1, 150, "§fDemir Miğfer");
        add(29, Material.IRON_CHESTPLATE, 1, 300, "§fDemir Göğüslük");
        add(30, Material.IRON_LEGGINGS, 1, 250, "§fDemir Pantolon");
        add(31, Material.IRON_BOOTS, 1, 120, "§fDemir Bot");
        add(32, Material.DIAMOND_CHESTPLATE, 1, 1200, "§bElmas Göğüslük");

        // Aletler (satır 5)
        add(37, Material.IRON_PICKAXE, 1, 200, "§fDemir Kazma");
        add(38, Material.SHIELD, 1, 150, "§7Kalkan");
        add(39, Material.ENDER_PEARL, 2, 150, "§5Kaçış İncisi §7x2");
        add(40, Material.TORCH, 32, 30, "§eMeşale §7x32");
    }

    private final RpPlugin plugin;

    public Market(RpPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------ MENÜ

    private ItemStack info(Player p) {
        PlayerData d = plugin.get(p);
        ItemStack it = new ItemStack(Material.GOLD_INGOT);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§6§lBakiyen");
        List<String> lore = new ArrayList<>();
        lore.add("§a$" + (int) d.money);
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack display(Entry e) {
        ItemStack it = new ItemStack(e.mat, e.amount);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(e.name);
        List<String> lore = new ArrayList<>();
        lore.add("§7Fiyat: §a$" + e.price);
        lore.add("");
        lore.add("§e▶ Satın almak için tıkla!");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    public void open(Player p) {
        MarketHolder h = new MarketHolder();
        Inventory inv = Bukkit.createInventory(h, 54, "§6§l✦ §e§lMARKET §6§l✦");
        h.inv = inv;

        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bm = bg.getItemMeta();
        bm.setDisplayName(" ");
        bg.setItemMeta(bm);
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        for (Map.Entry<Integer, Entry> en : ENTRIES.entrySet()) {
            inv.setItem(en.getKey(), display(en.getValue()));
        }
        inv.setItem(4, info(p));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------ OLAYLAR

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (msg.equals("/market") || msg.equals("/magaza")) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            if (!plugin.get(p).loggedIn) return;
            open(p);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent ev) {
        if (!(ev.getInventory().getHolder() instanceof MarketHolder)) return;
        ev.setCancelled(true);
        if (!(ev.getWhoClicked() instanceof Player)) return;
        Player p = (Player) ev.getWhoClicked();
        if (ev.getClickedInventory() == null || ev.getClickedInventory() != ev.getInventory()) return;

        Entry en = ENTRIES.get(ev.getSlot());
        if (en == null) return;

        PlayerData d = plugin.get(p);
        if (!d.loggedIn) return;

        if (d.money < en.price) {
            p.sendMessage(RpPlugin.PREFIX + "§cYetersiz bakiye! Gereken: §e$" + en.price);
            return;
        }
        d.money -= en.price;
        HashMap<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(en.mat, en.amount));
        for (ItemStack l : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), l);
        }
        p.sendMessage(RpPlugin.PREFIX + "§aSatın aldın: " + en.name + " §7(-$" + en.price + ")");
        ev.getInventory().setItem(4, info(p));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent ev) {
        if (ev.getInventory().getHolder() instanceof MarketHolder) ev.setCancelled(true);
    }
}
