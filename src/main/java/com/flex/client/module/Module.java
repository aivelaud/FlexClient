package com.flex.client.module;

import java.util.HashMap;
import java.util.Map;

public class Module {
    private final String name;
    private final String category;
    private final String description;
    private boolean enabled;
    private int keybind = -1;

    private final Map<String, Boolean> boolSettings   = new HashMap<>();
    private final Map<String, Integer> intSettings    = new HashMap<>();
    private final Map<String, Float>   floatSettings  = new HashMap<>();
    private final Map<String, String>  stringSettings = new HashMap<>();

    public Module(String name, String category, String description) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.enabled = false;
        initDefaultSettings();
    }
    public Module(String name, String category) { this(name, category, ""); }

    private void initDefaultSettings() {
        switch (name) {
            // ── COMBAT ──────────────────────────────────────────
            case "KillAura":
                boolSettings.put("hitAnimals",  false);
                boolSettings.put("hitPlayers",  true);
                boolSettings.put("hitMonsters", true);
                boolSettings.put("rotate",      true);
                intSettings.put("range",  5);
                intSettings.put("delay",  3);
                stringSettings.put("mode", "Single"); // Single / Multi / Switch
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
                boolSettings.put("jump",        false);
                break;
            case "AntiKnockback":
                floatSettings.put("amount", 0.0f);
                break;
            case "Criticals":
                stringSettings.put("mode", "Jump"); // Jump / Packet / Always
                break;
            case "Reach":
                floatSettings.put("range",       6.0f);
                boolSettings.put("blockReach",   true);
                boolSettings.put("entityReach",  true);
                break;
            case "TriggerBot":
                intSettings.put("delay", 4);
                boolSettings.put("players", true);
                boolSettings.put("mobs",    true);
                break;
            case "AutoTotem":
                intSettings.put("threshold", 8);
                boolSettings.put("alert",    true);
                break;
            case "AutoArmor":
                boolSettings.put("override", false);
                break;
            case "AutoGap":
                floatSettings.put("threshold", 12.0f);
                boolSettings.put("gapple", true);
                boolSettings.put("crystal", false);
                break;
            case "AutoWeapon":
                boolSettings.put("preferSword", true);
                boolSettings.put("preferAxe",   false);
                break;

            // ── MOVEMENT ────────────────────────────────────────
            case "Fly":
                floatSettings.put("speed",   0.25f);
                boolSettings.put("noFall",   true);
                stringSettings.put("mode",   "Vanilla"); // Vanilla / Packet / Creative
                break;
            case "Speed":
                floatSettings.put("speed",   0.35f);
                boolSettings.put("jump",     false);
                stringSettings.put("mode",   "Strafe"); // Strafe / Ground / YPort
                break;
            case "BunnyHop":
                floatSettings.put("boost",   0.08f);
                break;
            case "LongJump":
                floatSettings.put("boost",   0.8f);
                break;
            case "Step":
                floatSettings.put("height",  2.5f);
                boolSettings.put("combat",   false);
                break;
            case "Jesus":
                stringSettings.put("mode",   "Solid"); // Solid / Bounce / Sneak
                break;
            case "Scaffold":
                boolSettings.put("tower",    false);
                boolSettings.put("safe",     true);
                boolSettings.put("sprint",   true);
                break;
            case "AntiVoid":
                floatSettings.put("safeY",   0.0f);
                boolSettings.put("slowFall", true);
                break;
            case "Parkour":
                boolSettings.put("onlyOnEdge", true);
                break;
            case "AntiAFK":
                boolSettings.put("rotate", true);
                boolSettings.put("swing",  false);
                intSettings.put("interval", 60);
                break;
            case "ElytraFly":
                floatSettings.put("speed",   1.8f);
                floatSettings.put("pitch",   -0.25f);
                boolSettings.put("autoFeed", false);
                break;
            case "Spider":
                floatSettings.put("speed",   0.3f);
                break;
            case "HighJump":
                floatSettings.put("boost",   0.5f);
                break;
            case "Strafe":
                floatSettings.put("strength", 0.2f);
                break;

            // ── PLAYER ──────────────────────────────────────────
            case "AutoEat":
                intSettings.put("threshold",   16);
                boolSettings.put("eatGap",     false);
                boolSettings.put("pauseAction",true);
                break;
            case "Regen":
                floatSettings.put("rate",      0.3f);
                break;
            case "NoSlow":
                boolSettings.put("items",      true);
                boolSettings.put("blocks",     true);
                break;
            case "FastPlace":
                intSettings.put("delay",       0);
                boolSettings.put("limitDist",  false);
                break;
            case "SpeedMine":
                floatSettings.put("modifier",  0.6f);
                boolSettings.put("packet",     false);
                break;
            case "AutoTool":
                boolSettings.put("silkTouch",  false);
                boolSettings.put("fortune",    true);
                break;
            case "VeinMiner":
                intSettings.put("maxBlocks",   32);
                boolSettings.put("ores",       true);
                boolSettings.put("logs",       false);
                break;
            case "AutoLog":
                floatSettings.put("threshold", 6.0f);
                boolSettings.put("alert",      true);
                break;
            case "AntiHunger":
                floatSettings.put("factor",    0.0f);
                break;
            case "InvWalk":
                boolSettings.put("sneak",      false);
                break;
            case "FastLadder":
                floatSettings.put("speed",     0.3f);
                break;
            case "ChestStealer":
                intSettings.put("delay",       5);
                boolSettings.put("closeGui",   true);
                break;
            case "Nuker":
                intSettings.put("range",       3);
                stringSettings.put("mode",     "Sphere"); // Sphere / Flat / ID
                boolSettings.put("noSwing",    false);
                break;
            case "Multitask":
                boolSettings.put("eat",        true);
                boolSettings.put("bow",        true);
                break;

            // ── RENDER ──────────────────────────────────────────
            case "Xray":
                boolSettings.put("showDiamond",       true);
                boolSettings.put("showGold",          true);
                boolSettings.put("showIron",          true);
                boolSettings.put("showAncientDebris", true);
                boolSettings.put("showChests",        true);
                boolSettings.put("showCoal",          false);
                boolSettings.put("showEmerald",       true);
                boolSettings.put("showLapis",         false);
                break;
            case "Fullbright":
                floatSettings.put("gamma",     16.0f);
                boolSettings.put("night",      false);
                break;
            case "ESP":
                boolSettings.put("showPlayers",   true);
                boolSettings.put("showMobs",      true);
                boolSettings.put("showAnimals",   false);
                boolSettings.put("showVehicles",  false);
                stringSettings.put("mode",        "Box"); // Box / Outline / Corner
                break;
            case "Tracers":
                boolSettings.put("players",    true);
                boolSettings.put("mobs",       false);
                boolSettings.put("chest",      true);
                stringSettings.put("origin",   "Eyes"); // Eyes / Crosshair
                break;
            case "StorageESP":
                boolSettings.put("chests",     true);
                boolSettings.put("barrels",    true);
                boolSettings.put("shulkers",   true);
                boolSettings.put("furnaces",   false);
                boolSettings.put("droppers",   false);
                break;
            case "HoleESP":
                boolSettings.put("obsidian",   true);
                boolSettings.put("bedrock",    true);
                boolSettings.put("partialHole",false);
                break;
            case "NameTags":
                floatSettings.put("scale",     1.5f);
                boolSettings.put("health",     true);
                boolSettings.put("armor",      true);
                boolSettings.put("distance",   true);
                break;
            case "NoHurtCam":
                break;
            case "Zoom":
                floatSettings.put("fov",       10.0f);
                floatSettings.put("smooth",    0.1f);
                boolSettings.put("scroll",     true);
                break;
            case "Chams":
                boolSettings.put("players",    true);
                boolSettings.put("mobs",       false);
                boolSettings.put("xray",       true);
                break;
            case "HandView":
                floatSettings.put("swingSpeed",1.0f);
                boolSettings.put("noSwing",    false);
                break;
            case "FreeLook":
                boolSettings.put("center",     false);
                break;
            case "NoRender":
                boolSettings.put("fire",       true);
                boolSettings.put("weather",    false);
                boolSettings.put("overlays",   false);
                boolSettings.put("totemAnim",  true);
                break;

            default: break;
        }
    }

    // ── Getters / Setters ────────────────────────────────────────
    public String getName()        { return name; }
    public String getCategory()    { return category; }
    public String getDescription() { return description; }
    public boolean isEnabled()     { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public int getKeybind()        { return keybind; }
    public void setKeybind(int k)  { this.keybind = k; }
    public void toggle()           { this.enabled = !this.enabled; }

    // Bool
    public void setSetting(String key, boolean value)  { boolSettings.put(key, value); }
    public boolean getSetting(String key)              { return boolSettings.getOrDefault(key, false); }
    public Map<String, Boolean> getBoolSettings()      { return boolSettings; }

    // Int
    public void setIntSetting(String key, int value)   { intSettings.put(key, value); }
    public int getIntSetting(String key, int def)      { return intSettings.getOrDefault(key, def); }
    public Map<String, Integer> getIntSettings()       { return intSettings; }

    // Float
    public void setFloatSetting(String key, float value) { floatSettings.put(key, value); }
    public float getFloatSetting(String key, float def)  { return floatSettings.getOrDefault(key, def); }
    public Map<String, Float> getFloatSettings()         { return floatSettings; }

    // String / Enum mode
    public void setStringSetting(String key, String value) { stringSettings.put(key, value); }
    public String getStringSetting(String key, String def) { return stringSettings.getOrDefault(key, def); }
    public Map<String, String> getStringSettings()         { return stringSettings; }
    public String getMode()  { return stringSettings.getOrDefault("mode", ""); }

    // ── Shortcuts ────────────────────────────────────────────────
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
