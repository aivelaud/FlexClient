package com.flex.client.module;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    public static final List<Module> modules = new ArrayList<>();

    // Cift init'i onle
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ── COMBAT ──────────────────────────────────────────────
        modules.add(new Module("KillAura",   "Combat",   "Yakin entitelere otomatik saldirir"));
        modules.add(new Module("AntiKnockback", "Combat","Geri itilmeyi azaltir"));

        // ── MOVEMENT ────────────────────────────────────────────
        modules.add(new Module("Fly",        "Movement", "Serbestce ucmani saglar"));
        modules.add(new Module("Speed",      "Movement", "Yurus hizini arttirir"));
        modules.add(new Module("NoFall",     "Movement", "Dusme hasarini engeller"));

        // ── RENDER ──────────────────────────────────────────────
        modules.add(new Module("Xray",       "Render",   "Degerli bloklari duvardan gosterir"));
        modules.add(new Module("Fullbright", "Render",   "Karanlikta tam parlaklik"));
    }

    // ============================================================
    //  Modül getir
    // ============================================================
    public static Module get(String name) {
        if (name == null) return null;
        for (Module m : modules) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    // ============================================================
    //  Aktif mi?
    // ============================================================
    public static boolean isEnabled(String name) {
        Module m = get(name);
        return m != null && m.isEnabled();
    }

    // ============================================================
    //  Kategori bazli liste
    // ============================================================
    public static List<Module> getByCategory(String category) {
        List<Module> result = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory().equals(category)) result.add(m);
        }
        return result;
    }

    // ============================================================
    //  Tum moduller
    // ============================================================
    public static void enableAll()  { for (Module m : modules) m.setEnabled(true); }
    public static void disableAll() { for (Module m : modules) m.setEnabled(false); }
}
