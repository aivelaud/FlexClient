package com.flex.client.module;

import java.util.*;

public class Module {
    private final String name;
    private final String category;
    private final String description;
    private boolean enabled;
    private int keybind = -1;

    private final Map<String, Boolean> boolSettings   = new LinkedHashMap<>();
    private final Map<String, Integer> intSettings    = new LinkedHashMap<>();
    private final Map<String, Float>   floatSettings  = new LinkedHashMap<>();
    private final Map<String, String>  stringSettings = new LinkedHashMap<>();

    public Module(String name, String category, String description) {
        this.name = name; this.category = category; this.description = description;
        initDefaultSettings();
    }
    public Module(String name, String category) { this(name, category, ""); }

    private void initDefaultSettings() {
        switch (name) {
            // ── COMBAT ──────────────────────────────────────────────
            case "KillAura":
                stringSettings.put("mode",       "Single");
                boolSettings.put("hitMonsters",  true);
                boolSettings.put("hitPlayers",   true);
                boolSettings.put("hitAnimals",   false);
                boolSettings.put("rotate",       true);
                boolSettings.put("swing",        true);
                intSettings.put("range",         5);
                intSettings.put("delay",         3);
                break;
            case "CrystalAura":
                stringSettings.put("mode",       "Smart");
                boolSettings.put("autoPlace",    true);
                boolSettings.put("autoAttack",   true);
                boolSettings.put("hitPlayers",   true);
                boolSettings.put("antiSuicide",  true);
                intSettings.put("range",         5);
                intSettings.put("delay",         3);
                floatSettings.put("minDamage",   6.0f);
                floatSettings.put("selfSafe",    8.0f);
                break;
            case "TriggerBot":
                boolSettings.put("hitPlayers",   true);
                boolSettings.put("hitMobs",      true);
                boolSettings.put("hitAnimals",   false);
                boolSettings.put("autoSprint",   true);
                intSettings.put("delay",         4);
                intSettings.put("randomDelay",   2);
                break;
            case "AimAssist":
                boolSettings.put("hitPlayers",   true);
                boolSettings.put("hitMobs",      true);
                boolSettings.put("fov",          false);
                intSettings.put("range",         5);
                floatSettings.put("speed",       5.0f);
                floatSettings.put("fovAngle",    90.0f);
                break;
            case "Velocity":
                stringSettings.put("mode",       "Reduce");
                floatSettings.put("horizontal",  0.15f);
                floatSettings.put("vertical",    1.0f);
                boolSettings.put("jump",         false);
                break;
            case "AntiKnockback":
                floatSettings.put("amount",      0.0f);
                break;
            case "Criticals":
                stringSettings.put("mode",       "Jump");
                break;
            case "Reach":
                boolSettings.put("blockReach",   true);
                boolSettings.put("entityReach",  true);
                floatSettings.put("range",       6.0f);
                break;
            case "AutoTotem":
                boolSettings.put("alert",        true);
                boolSettings.put("offhand",      true);
                intSettings.put("threshold",     8);
                break;
            case "AutoGap":
                boolSettings.put("gapple",       true);
                boolSettings.put("crystal",      false);
                floatSettings.put("threshold",   12.0f);
                break;
            case "AutoWeapon":
                boolSettings.put("preferSword",  true);
                boolSettings.put("preferAxe",    false);
                break;
            case "AutoArmor":
                boolSettings.put("override",     false);
                break;
            // ── MOVEMENT ────────────────────────────────────────────
            case "Fly":
                stringSettings.put("mode",       "Vanilla");
                boolSettings.put("noFall",       true);
                boolSettings.put("sprint",       false);
                floatSettings.put("speed",       0.25f);
                break;
            case "Speed":
                stringSettings.put("mode",       "Strafe");
                boolSettings.put("jump",         false);
                boolSettings.put("sprint",       true);
                floatSettings.put("speed",       0.35f);
                break;
            case "BunnyHop":
                floatSettings.put("boost",       0.08f);
                break;
            case "LongJump":
                floatSettings.put("boost",       0.8f);
                break;
            case "Step":
                boolSettings.put("combat",       false);
                floatSettings.put("height",      2.5f);
                break;
            case "Jesus":
                stringSettings.put("mode",       "Solid");
                break;
            case "Scaffold":
                boolSettings.put("tower",        false);
                boolSettings.put("safe",         true);
                boolSettings.put("sprint",       true);
                break;
            case "AntiVoid":
                boolSettings.put("slowFall",     true);
                floatSettings.put("safeY",       0.0f);
                break;
            case "Parkour":
                boolSettings.put("onlyOnEdge",   true);
                break;
            case "AntiAFK":
                boolSettings.put("rotate",       true);
                boolSettings.put("swing",        false);
                boolSettings.put("jump",         false);
                intSettings.put("interval",      60);
                break;
            case "ElytraFly":
                boolSettings.put("autoFeed",     false);
                floatSettings.put("speed",       1.8f);
                floatSettings.put("pitch",       -0.25f);
                break;
            case "Spider":
                floatSettings.put("speed",       0.3f);
                break;
            case "HighJump":
                floatSettings.put("boost",       0.5f);
                break;
            case "VClip":
                floatSettings.put("distance", 5.0f);
                boolSettings.put("up", true);
                break;
            case "FastLadder":
                floatSettings.put("speed",       0.3f);
                break;
            // ── PLAYER ──────────────────────────────────────────────
            case "AutoEat":
                boolSettings.put("eatGap",       false);
                boolSettings.put("pauseAction",  true);
                intSettings.put("threshold",     16);
                break;
            case "Regen":
                floatSettings.put("rate",        0.3f);
                break;
            case "NoSlow":
                boolSettings.put("items",        true);
                boolSettings.put("blocks",       true);
                break;
            case "FastPlace":
                boolSettings.put("limitDist",    false);
                intSettings.put("delay",         0);
                break;
            case "SpeedMine":
                boolSettings.put("packet",       false);
                floatSettings.put("modifier",    0.6f);
                break;
            case "AutoTool":
                boolSettings.put("silkTouch",    false);
                boolSettings.put("fortune",      true);
                break;
            case "VeinMiner":
                boolSettings.put("ores",         true);
                boolSettings.put("logs",         false);
                intSettings.put("maxBlocks",     32);
                break;
            case "AutoLog":
                boolSettings.put("alert",        true);
                floatSettings.put("threshold",   6.0f);
                break;
            case "AntiHunger":
                floatSettings.put("factor",      0.0f);
                break;
            case "InvWalk":
                boolSettings.put("sneak",        false);
                break;
            case "ChestStealer":
                boolSettings.put("closeGui",     true);
                intSettings.put("delay",         5);
                break;
            case "Nuker":
                stringSettings.put("mode",       "Sphere");
                boolSettings.put("noSwing",      false);
                boolSettings.put("instant",      false);
                intSettings.put("range",         3);
                break;
            case "Multitask":
                boolSettings.put("eat",          true);
                boolSettings.put("bow",          true);
                break;
            // ── RENDER ──────────────────────────────────────────────
            case "Xray":
                boolSettings.put("showDiamond",        true);
                boolSettings.put("showGold",           true);
                boolSettings.put("showIron",           true);
                boolSettings.put("showAncientDebris",  true);
                boolSettings.put("showChests",         true);
                boolSettings.put("showSpawner",        true);
                boolSettings.put("showCoal",           false);
                boolSettings.put("showEmerald",        true);
                boolSettings.put("showLapis",          false);
                boolSettings.put("showRedstone",       false);
                boolSettings.put("showCopper",         false);
                break;
            case "Fullbright":
                boolSettings.put("night",        false);
                floatSettings.put("gamma",       16.0f);
                break;
            case "ESP":
                stringSettings.put("mode",       "Box");
                boolSettings.put("showPlayers",  true);
                boolSettings.put("showMobs",     true);
                boolSettings.put("showAnimals",  false);
                boolSettings.put("showVehicles", false);
                boolSettings.put("health",       true);
                break;
            case "Tracers":
                stringSettings.put("origin",     "Eyes");
                boolSettings.put("players",      true);
                boolSettings.put("mobs",         false);
                boolSettings.put("chests",       true);
                break;
            case "StorageESP":
                boolSettings.put("chests",       true);
                boolSettings.put("barrels",      true);
                boolSettings.put("shulkers",     true);
                boolSettings.put("furnaces",     false);
                boolSettings.put("droppers",     false);
                break;
            case "HoleESP":
                boolSettings.put("obsidian",     true);
                boolSettings.put("bedrock",      true);
                boolSettings.put("partialHole",  false);
                break;
            case "NameTags":
                boolSettings.put("health",       true);
                boolSettings.put("armor",        true);
                boolSettings.put("distance",     true);
                floatSettings.put("scale",       1.5f);
                break;
            case "Chams":
                boolSettings.put("players",      true);
                boolSettings.put("mobs",         false);
                boolSettings.put("xray",         true);
                break;
            case "NoRender":
                boolSettings.put("fire",         true);
                boolSettings.put("weather",      false);
                boolSettings.put("overlays",     false);
                boolSettings.put("totemAnim",    true);
                break;
            case "Zoom":
                boolSettings.put("scroll",       true);
                floatSettings.put("fov",         10.0f);
                floatSettings.put("smooth",      0.1f);
                break;
            case "HandView":
                boolSettings.put("noSwing",      false);
                floatSettings.put("swingSpeed",  1.0f);
                break;
            case "FreeLook":
                boolSettings.put("center",       false);
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

    public void    setSetting(String key, boolean v)  { boolSettings.put(key, v); }
    public boolean getSetting(String key)             { return boolSettings.getOrDefault(key, false); }
    public Map<String, Boolean> getBoolSettings()     { return boolSettings; }

    public void    setIntSetting(String key, int v)   { intSettings.put(key, v); }
    public int     getIntSetting(String key, int def) { return intSettings.getOrDefault(key, def); }
    public Map<String, Integer> getIntSettings()      { return intSettings; }

    public void  setFloatSetting(String key, float v)     { floatSettings.put(key, v); }
    public float getFloatSetting(String key, float def)   { return floatSettings.getOrDefault(key, def); }
    public Map<String, Float> getFloatSettings()          { return floatSettings; }

    public void   setStringSetting(String key, String v)       { stringSettings.put(key, v); }
    public String getStringSetting(String key, String def)     { return stringSettings.getOrDefault(key, def); }
    public Map<String, String> getStringSettings()             { return stringSettings; }
    public String getMode()                                    { return stringSettings.getOrDefault("mode", ""); }

    // Shortcuts
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
