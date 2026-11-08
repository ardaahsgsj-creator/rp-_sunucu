package com.rp;

import org.bukkit.Material;

public enum Job {
    MAFYA("Mafya", Material.NETHERITE_SWORD, "§c",
            "Çırak", "Asker", "Kaptan", "Alt Patron", "Mafya Babası"),
    POLIS("Polis", Material.SHIELD, "§9",
            "Stajyer", "Memur", "Komiser", "Başkomiser", "Emniyet Müdürü"),
    KACAKCI("Kaçakçı", Material.CHEST, "§5",
            "Acemi", "Taşıyıcı", "Tedarikçi", "Baron", "Kartel Lideri"),
    MADENCI("Madenci", Material.IRON_PICKAXE, "§7",
            "Çırak", "Usta", "Ocak Başı", "Mühendis", "Maden Patronu"),
    ESNAF("Esnaf", Material.EMERALD, "§a",
            "Çırak", "Tezgahtar", "Dükkan Sahibi", "Tüccar", "Ticaret Baronu"),
    DOKTOR("Doktor", Material.GOLDEN_APPLE, "§b",
            "Stajyer Doktor", "Pratisyen", "Uzman Doktor", "Başhekim", "Profesör"),
    AVUKAT("Avukat", Material.WRITABLE_BOOK, "§6",
            "Stajyer Avukat", "Avukat", "Kıdemli Avukat", "Ortak Avukat", "Baro Başkanı"),
    EMLAKCI("Emlakçı", Material.OAK_DOOR, "§e",
            "Çırak", "Satış Danışmanı", "Gayrimenkul Uzmanı", "Emlak Müdürü", "Emlak Patronu");

    /** Rütbe için gereken XP eşikleri */
    public static final int[] THRESH = {0, 100, 300, 700, 1500};

    public final String display;
    public final Material icon;
    public final String color;
    public final String[] ranks;

    Job(String display, Material icon, String color, String... ranks) {
        this.display = display;
        this.icon = icon;
        this.color = color;
        this.ranks = ranks;
    }

    public int rankIndex(int xp) {
        int idx = 0;
        for (int i = 0; i < THRESH.length; i++) {
            if (xp >= THRESH[i]) idx = i;
        }
        return idx;
    }

    public String rankName(int xp) {
        return ranks[rankIndex(xp)];
    }

    public static Job from(String s) {
        if (s == null) return null;
        try {
            return Job.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
