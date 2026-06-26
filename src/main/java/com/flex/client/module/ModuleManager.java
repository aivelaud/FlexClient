package com.flex.client.module;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    public static final List<Module> modules = new ArrayList<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ── COMBAT ──────────────────────────────────────────────
        modules.add(new Module("KillAura",     "Combat",   "Yakin entitelere otomatik saldirir"));
        modules.add(new Module("AimAssist",    "Combat",   "Hedeflere dogru otomatik nisan alir"));
        modules.add(new Module("Velocity",     "Combat",   "Geri itilme hizini azaltir"));
        modules.add(new Module("AntiKnockback","Combat",   "Geri itilmeyi tamamen iptal eder"));
        modules.add(new Module("Criticals",    "Combat",   "Her vurusu kritik yapar"));
        modules.add(new Module("Reach",        "Combat",   "Saldiri menzilini arttirir"));
        modules.add(new Module("AutoArmor",    "Combat",   "En iyi zimbiyi otomatik giyer"));
        modules.add(new Module("TriggerBot",   "Combat",   "Hedefte otomatik saldiri"));
        modules.add(new Module("AutoTotem",    "Combat",   "Olum aninda totem otomatik takar"));

        // ── MOVEMENT ────────────────────────────────────────────
        modules.add(new Module("Fly",          "Movement", "Serbestce ucmani saglar"));
        modules.add(new Module("Speed",        "Movement", "Yurus hizini arttirir"));
        modules.add(new Module("Sprint",       "Movement", "Surekli kosmaya zorlar"));
        modules.add(new Module("NoFall",       "Movement", "Dusme hasarini engeller"));
        modules.add(new Module("BunnyHop",     "Movement", "Ziplaya ziplaya hiz kazanir"));
        modules.add(new Module("LongJump",     "Movement", "Ziplamada cok uzaga firlar"));
        modules.add(new Module("Step",         "Movement", "Yuksek bloklara direkt cikar"));
        modules.add(new Module("Jesus",        "Movement", "Su uzerinde yurumeyi saglar"));
        modules.add(new Module("Scaffold",     "Movement", "Otomatik zemin koyar"));
        modules.add(new Module("SafeWalk",     "Movement", "Kenarlardan dusmez"));
        modules.add(new Module("FastLadder",   "Movement", "Merdivende hizli tirmanis"));

        // ── RENDER ──────────────────────────────────────────────
        modules.add(new Module("Xray",         "Render",   "Degerli bloklari gosterir"));
        modules.add(new Module("Fullbright",   "Render",   "Karanlikta tam parlaklik"));
        modules.add(new Module("ESP",          "Render",   "Oyuncular etrafinda kutu cizer"));
        modules.add(new Module("Tracers",      "Render",   "Oyunculara cizgi ceker"));
        modules.add(new Module("NoHurtCam",    "Render",   "Hasar alinca ekran titremez"));
        modules.add(new Module("Zoom",         "Render",   "Z tusuyla yakinlastirir"));
        modules.add(new Module("NameTags",     "Render",   "Oyuncu isimlerini buyuk gosterir"));

        // ── PLAYER ──────────────────────────────────────────────
        modules.add(new Module("AutoEat",      "Player",   "Otomatik yemek yer"));
        modules.add(new Module("Regen",        "Player",   "Cani otomatik yeniler"));
        modules.add(new Module("NoSlow",       "Player",   "Yemek/ok sirasinda yavaslamaz"));
        modules.add(new Module("FastPlace",    "Player",   "Blok yerlesimi hizlanir"));
        modules.add(new Module("FastBreak",    "Player",   "Bloklari aninda kirar"));
        modules.add(new Module("ChestStealer", "Player",   "Sandiktan otomatik esya alir"));
        modules.add(new Module("InvWalk",      "Player",   "Envanter acikken yuruyebilir"));
        modules.add(new Module("Nuker",        "Player",   "Etraftaki bloklari otomatik kirar"));
    }

    public static Module get(String name) {
        if (name == null) return null;
        for (Module m : modules) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    public static boolean isEnabled(String name) {
        Module m = get(name);
        return m != null && m.isEnabled();
    }

    public static List<Module> getByCategory(String cat) {
        List<Module> res = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory().equals(cat)) res.add(m);
        }
        return res;
    }

    public static void enableAll()  { for (Module m : modules) m.setEnabled(true); }
    public static void disableAll() { for (Module m : modules) m.setEnabled(false); }
}
