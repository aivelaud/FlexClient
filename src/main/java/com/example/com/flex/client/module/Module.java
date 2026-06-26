package com.flex.client.module;

import java.util.HashMap;
import java.util.Map;

public class Module {
    private final String name;
    private final String category;
    private final String description;
    private boolean enabled;
    private int keybind = -1;

    private final Map<String, Boolean> boolSettings  = new HashMap<>();
    private final Map<String, Integer> intSettings   = new HashMap<>();
    private final Map<String, Float>   floatSettings = new HashMap<>();

    public Module(String name, String category, String description) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.enabled = false;
        initDefaultSettings();
    }

    public Module(String name, String category) {
        this(name, category, "");
    }

    private void initDefaultSettings() {
        switch (name) {
            case "KillAura":
                boolSettings.put("hitAnimals", false);
                boolSettings.put("hitPlayers", true);
                intSettings.put("range", 4);
                intSettings.put("delay", 6);
                break;
            case "Reach":
                floatSettings.put("range", 5.0f);
                break;
            case "Fly":
                floatSettings.put("speed", 0.15f);
                boolSettings.put("noFall", true);
                break;
            case "Speed":
                floatSettings.put("speed", 0.25f);
                break;
            case "Step":
                floatSettings.put("height", 2.0f);
                break;
            case "Scaffold":
                boolSettings.put("tower", false);
                break;
            case "Xray":
                boolSettings.put("showDiamond", true);
                boolSettings.put("showGold", true);
                boolSettings.put("showIron", true);
                boolSettings.put("showAncientDebris", true);
                boolSettings.put("showChests", true);
                boolSettings.put("showCoal", false);
                break;
            case "ESP":
                boolSettings.put("showPlayers", true);
                boolSettings.put("showMobs", true);
                break;
            default:
                break;
        }
    }

    public String getName()        { return name; }
    public String getCategory()    { return category; }
    public String getDescription() { return description; }
    public boolean isEnabled()     { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public int getKeybind()        { return keybind; }
    public void setKeybind(int k)  { this.keybind = k; }

    public void toggle() { this.enabled = !this.enabled; }

    public void setSetting(String key, boolean value) { boolSettings.put(key, value); }
    public boolean getSetting(String key) { return boolSettings.getOrDefault(key, false); }

    public void setIntSetting(String key, int value) { intSettings.put(key, value); }
    public int getIntSetting(String key, int def) { return intSettings.getOrDefault(key, def); }

    public void setFloatSetting(String key, float value) { floatSettings.put(key, value); }
    public float getFloatSetting(String key, float def) { return floatSettings.getOrDefault(key, def); }

    // Shortcuts
    public boolean isHitAnimals()  { return getSetting("hitAnimals"); }
    public boolean isHitPlayers()  { return getSetting("hitPlayers"); }
    public int getKillAuraRange()  { return getIntSetting("range", 4); }
    public int getKillAuraDelay()  { return getIntSetting("delay", 6); }
    public float getFlySpeed()     { return getFloatSetting("speed", 0.15f); }
    public boolean isNoFall()      { return getSetting("noFall"); }
    public float getWalkSpeed()    { return getFloatSetting("speed", 0.25f); }
}
