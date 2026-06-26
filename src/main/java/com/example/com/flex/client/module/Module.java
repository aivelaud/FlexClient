package com.flex.client.module;

import java.util.HashMap;
import java.util.Map;

public class Module {
    private final String name;
    private final String category;
    private final String description;
    private boolean enabled;

    // Boolean ayarlar (ornek: hitAnimals, noFall)
    private final Map<String, Boolean> boolSettings = new HashMap<>();

    // Integer ayarlar (ornek: range, speed miktari)
    private final Map<String, Integer> intSettings = new HashMap<>();

    // Float ayarlar (ornek: fly hizi)
    private final Map<String, Float> floatSettings = new HashMap<>();

    // ============================================================
    //  Constructor
    // ============================================================

    public Module(String name, String category) {
        this(name, category, "");
    }

    public Module(String name, String category, String description) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.enabled = false;
        initDefaultSettings();
    }

    // Her modul kendi varsayilan ayarlarini buraya yazar
    private void initDefaultSettings() {
        switch (name) {
            case "KillAura":
                boolSettings.put("hitAnimals", false);
                intSettings.put("range", 6);         // Menzil (blok)
                intSettings.put("delay", 8);          // Saldirir arasi tick
                break;
            case "Fly":
                floatSettings.put("speed", 0.1f);    // Ucus hizi
                boolSettings.put("noFall", true);     // Dusme hasari engelle
                break;
            case "Speed":
                floatSettings.put("speed", 0.2f);    // Yurus hizi
                break;
            case "Xray":
                boolSettings.put("showCoal", false);  // Komur goster/gizle
                boolSettings.put("showIron", true);
                boolSettings.put("showGold", true);
                boolSettings.put("showDiamond", true);
                boolSettings.put("showAncientDebris", true);
                boolSettings.put("showChests", true);
                boolSettings.put("showSpawners", true);
                break;
            default:
                break;
        }
    }

    // ============================================================
    //  Temel getter/setter
    // ============================================================

    public String getName()        { return name; }
    public String getCategory()    { return category; }
    public String getDescription() { return description; }
    public boolean isEnabled()     { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }

    public void toggle() {
        this.enabled = !this.enabled;
    }

    // ============================================================
    //  Boolean ayarlar
    // ============================================================

    public void setSetting(String key, boolean value) {
        boolSettings.put(key, value);
    }

    public boolean getSetting(String key) {
        return boolSettings.getOrDefault(key, false);
    }

    // ============================================================
    //  Integer ayarlar
    // ============================================================

    public void setIntSetting(String key, int value) {
        intSettings.put(key, value);
    }

    public int getIntSetting(String key, int defaultValue) {
        return intSettings.getOrDefault(key, defaultValue);
    }

    // ============================================================
    //  Float ayarlar
    // ============================================================

    public void setFloatSetting(String key, float value) {
        floatSettings.put(key, value);
    }

    public float getFloatSetting(String key, float defaultValue) {
        return floatSettings.getOrDefault(key, defaultValue);
    }

    // ============================================================
    //  Kullanisli kisayollar
    // ============================================================

    // KillAura
    public boolean isHitAnimals() { return getSetting("hitAnimals"); }
    public int getKillAuraRange() { return getIntSetting("range", 6); }
    public int getKillAuraDelay() { return getIntSetting("delay", 8); }

    // Fly
    public float getFlySpeed()  { return getFloatSetting("speed", 0.1f); }
    public boolean isNoFall()   { return getSetting("noFall"); }

    // Speed
    public float getWalkSpeed() { return getFloatSetting("speed", 0.2f); }

    // Xray
    public boolean xrayShowCoal()          { return getSetting("showCoal"); }
    public boolean xrayShowIron()          { return getSetting("showIron"); }
    public boolean xrayShowGold()          { return getSetting("showGold"); }
    public boolean xrayShowDiamond()       { return getSetting("showDiamond"); }
    public boolean xrayShowAncientDebris() { return getSetting("showAncientDebris"); }
    public boolean xrayShowChests()        { return getSetting("showChests"); }
    public boolean xrayShowSpawners()      { return getSetting("showSpawners"); }
}
