package com.rp;

import java.util.HashMap;
import java.util.Map;

public class PlayerData {
    public String hash;
    public String salt;
    public double money;
    public double bank = 0;
    public String job = "YOK";
    public Map<String, Integer> xp = new HashMap<>();
    public long jailUntil = 0;

    // Kaydedilmeyen (geçici) veriler
    public boolean loggedIn = false;
    public long lastPaket = 0;
    public long lastHarac = 0;
    public long lastJobChange = 0;

    public int xpOf(Job j) {
        return xp.getOrDefault(j.name(), 0);
    }
}
