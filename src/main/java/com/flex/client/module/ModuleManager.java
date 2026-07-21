package com.flex.client.module;

import java.util.*;

public class ModuleManager {
    public static final List<Module> modules = new ArrayList<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ── COMBAT ──────────────────────────────────────────────────
        reg("KillAura",      "Combat",   "Yakın entitelere otomatik saldırır (Single/Multi/Switch)");
        reg("CrystalAura",   "Combat",   "End Crystal otomatik yerleştirir ve patlatır (Smart/Suicide/Safe)");
        reg("AimAssist",     "Combat",   "Hedeflere doğru otomatik nişan alır");
        reg("TriggerBot",    "Combat",   "Crosshair'daki entiteye otomatik saldırır");
        reg("Velocity",      "Combat",   "Geri itilme hızını ayarlar");
        reg("AntiKnockback", "Combat",   "Geri itilmeyi tamamen iptal eder");
        reg("Criticals",     "Combat",   "Her vuruşu kritik yapar (Jump/Packet/Always)");
        reg("Reach",         "Combat",   "Blok/entity etkileşim menzilini artırır");
        reg("AutoTotem",     "Combat",   "Ölüm anında totemi otomatik offhand'e koyar");
        reg("AutoArmor",     "Combat",   "En iyi zırhı otomatik giyer");
        reg("AutoGap",       "Combat",   "Düşük sağlıkta golden apple otomatik yer");
        reg("AutoWeapon",    "Combat",   "En iyi silahı otomatik seçer (kılıç/balta)");

        // ── MOVEMENT ────────────────────────────────────────────────
        reg("Fly",           "Movement", "Serbestçe uçmanı sağlar (Vanilla/Packet/Creative)");
        reg("Speed",         "Movement", "Yürüş hızını artırır (Strafe/Ground/YPort)");
        reg("Sprint",        "Movement", "Sürekli koşmaya zorlar");
        reg("NoFall",        "Movement", "Düşme hasarını engeller");
        reg("BunnyHop",      "Movement", "Zıplayarak sürekli hız kazanır");
        reg("LongJump",      "Movement", "Zıplamada çok uzağa fırlar");
        reg("Step",          "Movement", "Yüksek bloklara direkt çıkar");
        reg("Jesus",         "Movement", "Su/lav üzerinde yürümeyi sağlar");
        reg("Scaffold",      "Movement", "Ayak altına otomatik blok yerleştirir");
        reg("SafeWalk",      "Movement", "Kenarlardan düşmez");
        reg("NoSlow",        "Movement", "Yemek/ok çekerken yavaşlamaz");
        reg("AntiVoid",      "Movement", "Void'e düşmeyi engeller");
        reg("Parkour",       "Movement", "Kenar algılamasıyla otomatik zıplar");
        reg("ElytraFly",     "Movement", "Elitra ile stabil ve hızlı uçuş");
        reg("Spider",        "Movement", "Duvarlara tırmanmayı sağlar");
        reg("HighJump",      "Movement", "Çok yüksek zıplamayı sağlar");
        reg("AntiAFK",       "Movement", "AFK atılmayı engeller");
        reg("FastLadder",    "Movement", "Merdivende çok hızlı tırmanma");
          reg("Clip",          "Movement", "Bloklardan ve kilitli kapılardan geçmeyi sağlar");
          reg("VClip",         "Movement", "Dikey eksen boyunca bloklardan geçerir");

        // ── PLAYER ──────────────────────────────────────────────────
        reg("AutoEat",       "Player",   "Açlık düşünce otomatik yemek yer");
        reg("Regen",         "Player",   "Canı otomatik yeniler");
        reg("FastPlace",     "Player",   "Blok yerleşimini hızlandırır");
        reg("SpeedMine",     "Player",   "Blok kırma hızını artırır");
        reg("AutoTool",      "Player",   "Kırılacak blok için en iyi aleti seçer");
        reg("VeinMiner",     "Player",   "Tüm bağlı cevher damarını kırar");
        reg("AutoLog",       "Player",   "Düşük sağlıkta otomatik çıkış yapar");
        reg("AntiHunger",    "Player",   "Açlık tüketimini sıfırlar");
        reg("InvWalk",       "Player",   "Envanter açıkken yürüyebilir");
        reg("Nuker",         "Player",   "Etraftaki blokları otomatik kırar (Sphere/Flat/ID)");
        reg("ChestStealer",  "Player",   "Sandıktan otomatik eşya alır");
        reg("Multitask",     "Player",   "Madencilik/savaş sırasında yemek yiyebilir");

        // ── RENDER ──────────────────────────────────────────────────
        reg("Xray",          "Render",   "Cevher/sandık/spawner gibi değerli blokları gösterir");
        reg("Fullbright",    "Render",   "Karanlıkta tam parlaklık sağlar");
        reg("ESP",           "Render",   "Oyuncular/moblar etrafında kutu çizer (Box/Outline/Corner)");
        reg("Tracers",       "Render",   "Oyunculara/sandıklara çizgi çeker");
        reg("StorageESP",    "Render",   "Sandık/barrel/shulker kutularını vurgular");
        reg("HoleESP",       "Render",   "Güvenli obsidyen deliklerini vurgular");
        reg("NameTags",      "Render",   "Oyuncu isimlerini büyük ve detaylı gösterir");
        reg("Chams",         "Render",   "Düşmanları duvar arkasından gösterir");
        reg("NoHurtCam",     "Render",   "Hasar alınca ekran titremesini engeller");
        reg("Zoom",          "Render",   "Z tuşuyla yakınlaştırır");
        reg("HandView",      "Render",   "El animasyonlarını özelleştirir");
        reg("FreeLook",      "Render",   "Bakış açısını serbestçe döndürür");
        reg("NoRender",      "Render",   "Ateş/hava durumu gibi gereksiz efektleri kaldırır");

        // ── YENİ MODÜLLER ────────────────────────────────────────────
        reg("Timer",         "World",    "Oyun hız çarpanını değiştirir (0.5x – 10x)");
        reg("BowAimbot",     "Combat",   "Yay çekerken düşmanlara otomatik nişan alır");
        reg("Surround",      "Combat",   "Etrafına obsidyen/blok yerleştirerek korur");
        reg("AutoReconnect", "Misc",     "Sunucudan atılınca belirli süre sonra yeniden bağlanır");
        reg("BetterSprint",  "Movement", "Sprint'i tüm yönlere genişletir, savaş kesilmesini azaltır");
        reg("Blink",         "Movement", "Paketleri biriktirir, toggle'da toplu gönderir");
        reg("PacketFly",     "Movement", "Sunucu tarafını atlatarak uçuş sağlar");
        reg("ClickAura",     "Combat",   "Tıklarken etraftaki hedefe otomatik saldırır");
    }

    private static void reg(String n, String c, String d) { modules.add(new Module(n, c, d)); }

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
        for (Module m : modules) if (!cats.contains(m.getCategory())) cats.add(m.getCategory());
        return cats;
    }

    public static Map<String, List<Module>> getGrouped() {
        Map<String, List<Module>> map = new LinkedHashMap<>();
        for (Module m : modules) map.computeIfAbsent(m.getCategory(), k -> new ArrayList<>()).add(m);
        return map;
    }

    public static void enableAll()  { for (Module m : modules) m.setEnabled(true); }
      public static void disableAll() { for (Module m : modules) m.setEnabled(false); }

      /**
       * CrashGuard tarafından çağrılır — modülü devre dışı bırakır.
       */
      public static void forceDisable(String name) {
          Module m = get(name);
          if (m != null) m.setEnabled(false);
      }

      /** Şu an aktif (enabled) modüllerin adlarını döndürür. */
      public static List<String> getEnabledNames() {
          List<String> res = new ArrayList<>();
          for (Module m : modules) if (m.isEnabled()) res.add(m.getName());
          return res;
      }
  }
