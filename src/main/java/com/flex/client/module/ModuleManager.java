package com.flex.client.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    public static final List<Module> modules = new ArrayList<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ── COMBAT ──────────────────────────────────────────────────
        reg("KillAura",      "Combat",   "Yakın entitelere otomatik saldırır (Single/Multi/Switch)");
        reg("AimAssist",     "Combat",   "Hedeflere doğru otomatik nişan alır");
        reg("Velocity",      "Combat",   "Geri itilme hızını azaltır");
        reg("AntiKnockback", "Combat",   "Geri itilmeyi tamamen iptal eder");
        reg("Criticals",     "Combat",   "Her vuruşu kritik yapar (Jump/Packet)");
        reg("Reach",         "Combat",   "Saldırı menzilini artırır");
        reg("TriggerBot",    "Combat",   "Hedefte crosshair olunca otomatik saldırır");
        reg("AutoTotem",     "Combat",   "Ölüm anında totemi otomatik takar");
        reg("AutoArmor",     "Combat",   "En iyi zırhı otomatik giyer");
        reg("AutoGap",       "Combat",   "Sağlık düşünce otomatik golden apple yer");
        reg("AutoWeapon",    "Combat",   "En iyi silahı otomatik seçer");

        // ── MOVEMENT ────────────────────────────────────────────────
        reg("Fly",           "Movement", "Serbestçe uçmanı sağlar");
        reg("Speed",         "Movement", "Yürüş hızını artırır (Strafe/Ground/YPort)");
        reg("Sprint",        "Movement", "Sürekli koşmaya zorlar");
        reg("NoFall",        "Movement", "Düşme hasarını engeller");
        reg("BunnyHop",      "Movement", "Zıplayarak hız kazanır");
        reg("LongJump",      "Movement", "Zıplamada çok uzağa fırlar");
        reg("Step",          "Movement", "Yüksek bloklara direkt çıkar");
        reg("Jesus",         "Movement", "Su/lav üzerinde yürümeyi sağlar");
        reg("Scaffold",      "Movement", "Otomatik zemin koyar");
        reg("SafeWalk",      "Movement", "Kenarlardan düşmez");
        reg("NoSlow",        "Movement", "Yemek/ok çekerken yavaşlamaz");
        reg("AntiVoid",      "Movement", "Void'e düşmeyi engeller");
        reg("Parkour",       "Movement", "Kenar algılamasıyla otomatik zıplar");
        reg("ElytraFly",     "Movement", "Elitra ile hızlı ve stabil uçuş");
        reg("Spider",        "Movement", "Duvarlardan tırmanmayı sağlar");
        reg("HighJump",      "Movement", "Daha yüksek zıplamayı sağlar");
        reg("AntiAFK",       "Movement", "AFK atmayı engeller");
        reg("FastLadder",    "Movement", "Merdivende hızlı tırmanış");

        // ── PLAYER ──────────────────────────────────────────────────
        reg("AutoEat",       "Player",   "Otomatik yemek yer");
        reg("Regen",         "Player",   "Canı otomatik yeniler");
        reg("FastPlace",     "Player",   "Blok yerleşimi hızlanır");
        reg("SpeedMine",     "Player",   "Blok kırma hızını artırır");
        reg("AutoTool",      "Player",   "En iyi aleti otomatik seçer");
        reg("VeinMiner",     "Player",   "Bağlı cevherlerin hepsini kırar");
        reg("AutoLog",       "Player",   "Düşük sağlıkta otomatik çıkış yapar");
        reg("AntiHunger",    "Player",   "Açlık tüketimini azaltır");
        reg("InvWalk",       "Player",   "Envanter açıkken yürüyebilir");
        reg("Nuker",         "Player",   "Etraftaki blokları otomatik kırar");
        reg("ChestStealer",  "Player",   "Sandıktan otomatik eşya alır");
        reg("Multitask",     "Player",   "Madencilik/savaş sırasında yemek yer");

        // ── RENDER ──────────────────────────────────────────────────
        reg("Xray",          "Render",   "Değerli blokları gösterir");
        reg("Fullbright",    "Render",   "Karanlıkta tam parlaklık");
        reg("ESP",           "Render",   "Oyuncular etrafında kutu çizer");
        reg("Tracers",       "Render",   "Oyunculara çizgi çeker");
        reg("StorageESP",    "Render",   "Sandıkları/barelleri vurgular");
        reg("HoleESP",       "Render",   "Güvenli delikleri vurgular");
        reg("NameTags",      "Render",   "Oyuncu isimlerini büyük gösterir");
        reg("Chams",         "Render",   "Düşmanları duvar arkasından gösterir");
        reg("NoHurtCam",     "Render",   "Hasar alınca ekran titremez");
        reg("Zoom",          "Render",   "Z tuşuyla yakınlaştırır");
        reg("HandView",      "Render",   "El animasyonlarını özelleştirir");
        reg("FreeLook",      "Render",   "Bakış açısını serbestçe döndürür");
        reg("NoRender",      "Render",   "Ateş/hava durumu efektlerini kaldırır");
    }

    private static void reg(String name, String cat, String desc) {
        modules.add(new Module(name, cat, desc));
    }

    public static Module get(String name) {
        if (name == null) return null;
        for (Module m : modules) if (m.getName().equals(name)) return m;
        return null;
    }

    public static boolean isEnabled(String name) {
        Module m = get(name);
        return m != null && m.isEnabled();
    }

    public static List<Module> getByCategory(String cat) {
        List<Module> res = new ArrayList<>();
        for (Module m : modules) if (m.getCategory().equals(cat)) res.add(m);
        return res;
    }

    public static List<String> getCategories() {
        List<String> cats = new ArrayList<>();
        for (Module m : modules) {
            if (!cats.contains(m.getCategory())) cats.add(m.getCategory());
        }
        return cats;
    }

    public static Map<String, List<Module>> getGrouped() {
        Map<String, List<Module>> map = new LinkedHashMap<>();
        for (Module m : modules) {
            map.computeIfAbsent(m.getCategory(), k -> new ArrayList<>()).add(m);
        }
        return map;
    }

    public static void enableAll()  { for (Module m : modules) m.setEnabled(true); }
    public static void disableAll() { for (Module m : modules) m.setEnabled(false); }
}
