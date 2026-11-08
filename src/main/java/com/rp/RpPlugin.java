package com.rp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RpPlugin extends JavaPlugin {

    public static final String PREFIX = "§8[§6RP§8] §f";

    private final Map<UUID, PlayerData> players = new HashMap<>();
    private File file;
    private YamlConfiguration yml;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        file = new File(getDataFolder(), "players.yml");
        yml = YamlConfiguration.loadConfiguration(file);

        Bukkit.getPluginManager().registerEvents(new AuthListener(this), this);
        Bukkit.getPluginManager().registerEvents(new JobMenu.MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);

        Commands cmds = new Commands(this);
        for (String name : getDescription().getCommands().keySet()) {
            if (getCommand(name) != null) getCommand(name).setExecutor(cmds);
        }

        // Maaş
        long maasTick = getConfig().getLong("maas-dakika", 10) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::payday, maasTick, maasTick);

        // Hapis kontrolü (her saniye)
        Bukkit.getScheduler().runTaskTimer(this, this::jailTick, 20L, 20L);

        new Hud(this).start(); new Kit(this); // Otomatik kayıt (5 dk)
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, 6000L, 6000L);

        // Reload durumunda online oyuncular
        for (Player p : Bukkit.getOnlinePlayers()) load(p.getUniqueId());

        getLogger().info("RpSunucusu aktif!");
    }

    @Override
    public void onDisable() {
        saveAll();
    }

    // ---------------------------------------------------------------- VERİ

    public PlayerData get(Player p) {
        PlayerData d = players.get(p.getUniqueId());
        return d != null ? d : load(p.getUniqueId());
    }

    public PlayerData load(UUID id) {
        PlayerData d = new PlayerData();
        String p = "p." + id + ".";
        d.hash = yml.getString(p + "hash");
        d.salt = yml.getString(p + "salt");
        d.money = yml.getDouble(p + "money", getConfig().getDouble("baslangic-parasi", 500));
        d.job = yml.getString(p + "job", "YOK");
        d.jailUntil = yml.getLong(p + "jail", 0);
        ConfigurationSection s = yml.getConfigurationSection(p + "xp");
        if (s != null) {
            for (String k : s.getKeys(false)) d.xp.put(k, s.getInt(k));
        }
        players.put(id, d);
        return d;
    }

    public void save(UUID id) {
        PlayerData d = players.get(id);
        if (d == null) return;
        String p = "p." + id + ".";
        yml.set(p + "hash", d.hash);
        yml.set(p + "salt", d.salt);
        yml.set(p + "money", d.money);
        yml.set(p + "job", d.job);
        yml.set(p + "jail", d.jailUntil);
        yml.set(p + "xp", null);
        for (Map.Entry<String, Integer> e : d.xp.entrySet()) {
            yml.set(p + "xp." + e.getKey(), e.getValue());
        }
    }

    public void flush() {
        try {
            yml.save(file);
        } catch (IOException e) {
            getLogger().warning("players.yml kaydedilemedi: " + e.getMessage());
        }
    }

    public void saveAll() {
        for (UUID id : players.keySet()) save(id);
        flush();
    }

    public void unload(UUID id) {
        save(id);
        flush();
        players.remove(id);
    }

    /** Çevrimdışı oyuncu için bile kaydı sil (admin /sifirla) */
    public void wipePassword(UUID id) {
        PlayerData d = players.get(id);
        if (d != null) {
            d.hash = null;
            d.salt = null;
            d.loggedIn = false;
        }
        yml.set("p." + id + ".hash", null);
        yml.set("p." + id + ".salt", null);
        flush();
    }

    // ---------------------------------------------------------------- ŞİFRE

    public static String newSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    public static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void finishLogin(Player p, PlayerData d, boolean firstTime) {
        d.loggedIn = true;
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        save(p.getUniqueId());
        flush();
        p.sendMessage(PREFIX + (firstTime ? "§aKayıt başarılı! Hoş geldin." : "§aGiriş başarılı! Hoş geldin."));
        if (Job.from(d.job) == null) {
            Bukkit.getScheduler().runTask(this, () -> JobMenu.open(p));
        }
    }

    // ---------------------------------------------------------------- PARA / XP

    public void addMoney(Player p, double amount) {
        get(p).money += amount;
    }

    public void addXp(Player p, Job j, int amount) {
        PlayerData d = get(p);
        int old = d.xpOf(j);
        int now = old + amount;
        d.xp.put(j.name(), now);
        int a = j.rankIndex(old);
        int b = j.rankIndex(now);
        if (b > a) {
            p.sendTitle("§6RÜTBE ATLADIN!", j.color + j.rankName(now), 10, 70, 20);
            p.sendMessage(PREFIX + "§6Yeni rütben: " + j.color + j.rankName(now));
            if (b == Job.THRESH.length - 1) {
                Bukkit.broadcastMessage(PREFIX + "§e" + p.getName() + " §f artık " + j.color
                        + j.display + " §f mesleğinde §6" + j.rankName(now) + "§f!");
            }
        }
    }

    // ---------------------------------------------------------------- DÖNGÜLER

    private void payday() {
        double mult = getConfig().getDouble("maas-carpan", 100);
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = players.get(p.getUniqueId());
            if (d == null || !d.loggedIn) continue;
            Job j = Job.from(d.job);
            if (j == null) continue;
            int rank = j.rankIndex(d.xpOf(j));
            double pay = mult * (rank + 1);
            d.money += pay;
            p.sendMessage(PREFIX + "§aMaaşın yattı: §e$" + (int) pay + " §7(" + j.color + j.rankName(d.xpOf(j)) + "§7)");
        }
    }

    private void jailTick() {
        long now = System.currentTimeMillis();
        Location hapis = getConfig().getLocation("nokta.hapis");
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = players.get(p.getUniqueId());
            if (d == null || d.jailUntil <= 0) continue;
            if (now >= d.jailUntil) {
                d.jailUntil = 0;
                p.teleport(p.getWorld().getSpawnLocation());
                p.sendMessage(PREFIX + "§aCezan bitti, serbestsin!");
            } else if (hapis != null && hapis.getWorld() != null) {
                if (!hapis.getWorld().equals(p.getWorld()) || hapis.distance(p.getLocation()) > 8) {
                    p.teleport(hapis);
                }
            }
        }
    }
}
