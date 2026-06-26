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
                boolSettings.put("hitAnimals",  false);
                boolSettings.put("hitPlayers",  true);
                boolSettings.put("hitMonsters", true);
                boolSettings.put("rotate",      true);
                intSettings.put("range",  5);
                intSettings.put("delay",  3);
                break;
            case "AimAssist":
                intSettings.put("range",   5);
                floatSettings.put("speed", 5.0f);
                boolSettings.put("players", true);
                boolSettings.put("mobs",    true);
                break;
            case "Velocity":
                floatSettings.put("horizontal", 0.15f);
                floatSettings.put("vertical",   1.0f);
                break;
            case "Reach":
                floatSettings.put("range", 6.0f);
                break;
            case "Fly":
                floatSettings.put("speed",  0.25f);
                boolSettings.put("noFall",  true);
                break;
            case "Speed":
                floatSettings.put("speed",  0.35f);
                boolSettings.put("jump",    false);
                break;
            case "BunnyHop":
                floatSettings.put("boost",  0.08f);
                break;
            case "LongJump":
                floatSettings.put("boost",  0.8f);
                break;
            case "Step":
                floatSettings.put("height", 2.5f);
                break;
            case "Scaffold":
                boolSettings.put("tower",   false);
                boolSettings.put("safe",    true);
                break;
            case "Xray":
                boolSettings.put("showDiamond",      true);
                boolSettings.put("showGold",         true);
                boolSettings.put("showIron",         true);
                boolSettings.put("showAncientDebris",true);
                boolSettings.put("showChests",       true);
                boolSettings.put("showCoal",         false);
                boolSettings.put("showEmerald",      true);
                break;
            case "ESP":
                boolSettings.put("showPlayers",  true);
                boolSettings.put("showMobs",     true);
                boolSettings.put("showAnimals",  false);
                break;
            case "AutoEat":
                intSettings.put("threshold",  16);
                break;
            case "Regen":
                floatSettings.put("rate",  0.3f);
                break;
            case "Nuker":
                intSettings.put("range",  3);
                break;
            case "AutoTotem":
                intSettings.put("threshold", 8);
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

    public void setSetting(String key, boolean value)  { boolSettings.put(key, value); }
    public boolean getSetting(String key)              { return boolSettings.getOrDefault(key, false); }

    public void setIntSetting(String key, int value)   { intSettings.put(key, value); }
    public int getIntSetting(String key, int def)      { return intSettings.getOrDefault(key, def); }

    public void setFloatSetting(String key, float value) { floatSettings.put(key, value); }
    public float getFloatSetting(String key, float def)  { return floatSettings.getOrDefault(key, def); }

    // ── Shortcuts ─────────────────────────────────────────────
    public boolean isHitAnimals()   { return getSetting("hitAnimals"); }
    public boolean isHitPlayers()   { return getSetting("hitPlayers"); }
    public boolean isHitMonsters()  { return getSetting("hitMonsters"); }
    public boolean isRotate()       { return getSetting("rotate"); }
    public int   getKillAuraRange() { return getIntSetting("range", 5); }
    public int   getKillAuraDelay() { return getIntSetting("delay", 3); }
    public float getFlySpeed()      { return getFloatSetting("speed", 0.25f); }
    public boolean isNoFall()       { return getSetting("noFall"); }
    public float getWalkSpeed()     { return getFloatSetting("speed", 0.35f); }
}
